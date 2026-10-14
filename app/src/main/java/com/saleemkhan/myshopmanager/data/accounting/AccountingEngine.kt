package com.saleemkhan.myshopmanager.data.accounting

import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.screens.TransactionDateFilter
import com.saleemkhan.myshopmanager.utils.FormatUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong

/**
 * Core Accounting Engine for MyShopManager.
 *
 * Implements:
 * 1. Perpetual Weighted Average Cost (AVCO) inventory costing.
 * 2. Immutable, deterministic ledger posting and reversal.
 * 3. Atomic edit (Reverse old -> Apply new -> Replace).
 * 4. Soft-delete reversal and restore re-application.
 * 5. Full balance reconciliation / rebuild from transaction ledger.
 * 6. Period COGS, canonical profit (Net Revenue - Verified COGS - Expenses, un-clamped).
 * 7. Cash flow metrics (Credit sales excluded from immediate cash inflow).
 * 8. Party identification and legacy classification.
 */
object AccountingEngine {

    // -------------------------------------------------------------
    // Data structures for reporting
    // -------------------------------------------------------------

    data class CogsResult(
        val verifiedCogs: Double,
        val unverifiedSalesCount: Int,
        val verifiedCount: Int,
        val isFullyVerified: Boolean
    ) {
        val totalCogs: Double get() = verifiedCogs
    }

    data class ProfitResult(
        val netRevenue: Double,
        val verifiedCogs: Double,
        val grossProfit: Double,
        val operatingExpenses: Double,
        val netProfit: Double, // Can be negative; legitimate losses are never clamped to 0.0
        val isCogsFullyVerified: Boolean,
        val unverifiedSalesCount: Int
    )

    data class CashFlowResult(
        val cashInflow: Double,
        val cashOutflow: Double,
        val netCashFlow: Double
    )

    data class BalanceSheetResult(
        val cashAndBank: Double,
        val inventoryValue: Double,
        val customerReceivables: Double,
        val businessFixedAssets: Double,
        val totalAssets: Double,
        val supplierPayables: Double,
        val loansPayable: Double,
        val totalLiabilities: Double,
        val totalEquity: Double,
        val isBalanced: Boolean,
        val difference: Double
    )

    // -------------------------------------------------------------
    // Monetary & Quantity rounding utilities
    // -------------------------------------------------------------

    fun roundMoney(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return (value * 100.0).roundToLong() / 100.0
    }

    fun roundQuantity(qty: Double): Double {
        if (qty.isNaN() || qty.isInfinite()) return 0.0
        return (qty * 10000.0).roundToLong() / 10000.0
    }

    // -------------------------------------------------------------
    // 1. AVCO Calculation Logic
    // -------------------------------------------------------------

    /**
     * Calculates new Perpetual Weighted Average Cost (AVCO) when purchasing additional stock:
     * New Average Cost = (Existing Stock Value + New Purchase Net Cost) / (Existing Quantity + Purchased Quantity)
     */
    fun calculateAvcoOnPurchase(
        existingQuantity: Double,
        existingWeightedAverageCost: Double,
        purchasedQuantity: Double,
        netPurchaseUnitCost: Double
    ): Double {
        val safeExistingQty = if (existingQuantity < 0.0) 0.0 else existingQuantity
        val safePurchasedQty = if (purchasedQuantity < 0.0) 0.0 else purchasedQuantity
        val newQuantity = safeExistingQty + safePurchasedQty
        if (newQuantity <= 0.0) {
            return if (netPurchaseUnitCost > 0.0) netPurchaseUnitCost else existingWeightedAverageCost
        }
        val existingValue = safeExistingQty * existingWeightedAverageCost
        val newValue = safePurchasedQty * netPurchaseUnitCost
        return roundMoney((existingValue + newValue) / newQuantity)
    }

    // -------------------------------------------------------------
    // 2. Party & Account resolution
    // -------------------------------------------------------------

    fun resolvePartyId(
        tx: Transaction,
        customers: List<CustomerParty>,
        suppliers: List<SupplierParty>
    ): String? {
        if (!tx.partyId.isNullOrBlank()) return tx.partyId
        val partyName = tx.partyName?.trim() ?: return null
        if (partyName.isBlank()) return null

        return when (tx.type) {
            TransactionType.SALE, TransactionType.RECEIPT -> {
                val matches = customers.filter { it.name.trim().equals(partyName, ignoreCase = true) }
                if (matches.size == 1) matches.first().id else null
            }
            TransactionType.PURCHASE, TransactionType.PAYMENT -> {
                val matches = suppliers.filter { it.name.trim().equals(partyName, ignoreCase = true) }
                if (matches.size == 1) matches.first().id else null
            }
            else -> null
        }
    }

    private fun findTargetAccount(
        accounts: List<BankAccount>,
        accountId: String?,
        paymentMethod: PaymentMethod
    ): BankAccount? {
        if (!accountId.isNullOrBlank()) {
            val byId = accounts.find { it.id == accountId }
            if (byId != null) return byId
        }
        return when (paymentMethod) {
            PaymentMethod.CASH -> accounts.find { it.type.equals("CASH", ignoreCase = true) }
            PaymentMethod.BANK -> accounts.find { it.type.equals("BANK", ignoreCase = true) }
            PaymentMethod.EASYPAISA -> accounts.find { it.bankName.contains("EASY", ignoreCase = true) || it.accountTitle.contains("Easy", ignoreCase = true) }
            PaymentMethod.JAZZCASH -> accounts.find { it.bankName.contains("JAZZ", ignoreCase = true) || it.accountTitle.contains("Jazz", ignoreCase = true) }
            PaymentMethod.SADAPAY -> accounts.find { it.bankName.contains("SADA", ignoreCase = true) || it.accountTitle.contains("Sada", ignoreCase = true) }
            PaymentMethod.NAYAPAY -> accounts.find { it.bankName.contains("NAYA", ignoreCase = true) || it.accountTitle.contains("Naya", ignoreCase = true) }
            else -> accounts.find { it.type.equals("CASH", ignoreCase = true) }
        } ?: accounts.firstOrNull()
    }

    // -------------------------------------------------------------
    // 3. Core Posting Rules (applyTransaction)
    // -------------------------------------------------------------

    /**
     * Posts a new or restored transaction into the application state according to canonical accounting rules.
     */
    fun applyTransaction(state: AppState, tx: Transaction): AppState {
        // Prevent duplicate processing of the same transaction
        if (state.transactions.any { it.id == tx.id }) {
            return state
        }
        val updatedState = applyTransactionEffects(state, tx)
        val updatedTxList = listOf(tx) + updatedState.transactions
        return updatedState.copy(transactions = updatedTxList)
    }

    private fun applyTransactionEffects(state: AppState, tx: Transaction): AppState {
        val amount = roundMoney(tx.amount)
        var updatedBankAccounts = state.bankAccounts
        var updatedCustomers = state.customers
        var updatedSuppliers = state.suppliers
        var updatedInventory = state.inventory
        var updatedLoans = state.loans

        when (tx.type) {
            TransactionType.SALE -> {
                // 1. Liquidity or Receivable
                if (tx.paymentMethod == PaymentMethod.CREDIT) {
                    val partyNameClean = tx.partyName?.trim() ?: ""
                    val resolvedCustId = resolvePartyId(tx, updatedCustomers, updatedSuppliers)
                    val isWalkIn = resolvedCustId.isNullOrBlank() && (
                            partyNameClean.isBlank() ||
                            partyNameClean.equals("Counter Cash Customer", ignoreCase = true) ||
                            partyNameClean.equals("کاؤنٹر نقد کسٹمر", ignoreCase = true) ||
                            partyNameClean.equals("Walk-in Customer", ignoreCase = true)
                    )

                    if (!isWalkIn) {
                        val existingCust = updatedCustomers.find { c ->
                            (resolvedCustId != null && c.id == resolvedCustId) ||
                                    (partyNameClean.isNotBlank() && c.name.trim().equals(partyNameClean, ignoreCase = true))
                        }

                        val activeCustId = if (existingCust != null) {
                            existingCust.id
                        } else {
                            val newCustId = tx.partyId?.ifBlank { null } ?: "cust_${System.currentTimeMillis()}"
                            val newCustomer = CustomerParty(
                                id = newCustId,
                                name = if (partyNameClean.isNotBlank()) partyNameClean else "Khata Customer",
                                phone = "",
                                totalCredit = 0.0,
                                status = "ACTIVE"
                            )
                            updatedCustomers = updatedCustomers + newCustomer
                            newCustId
                        }

                        updatedCustomers = updatedCustomers.map { c ->
                            if (c.id == activeCustId) {
                                c.copy(
                                    totalCredit = roundMoney(c.totalCredit + amount),
                                    lastTransactionDate = tx.date.ifBlank { FormatUtils.getTodayIsoDate() }
                                )
                            } else c
                        }
                    }
                } else {
                    // CASH / BANK / WALLET sale: account balance increases
                    val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                    if (targetAcc != null) {
                        updatedBankAccounts = updatedBankAccounts.map { acc ->
                            if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance + amount)) else acc
                        }
                    }
                }

                // 2. Inventory reduction
                if (!tx.itemId.isNullOrBlank() && tx.qtyChange != null) {
                    val qtySold = tx.qtyChange
                    updatedInventory = updatedInventory.map { item ->
                        if (item.id == tx.itemId) {
                            item.copy(
                                quantity = roundQuantity(item.quantity - qtySold),
                                soldQty = roundQuantity(item.soldQty + qtySold)
                            )
                        } else item
                    }
                }
            }

            TransactionType.PURCHASE -> {
                val cashToDeduct = roundMoney(tx.cashPaid ?: (if (tx.paymentMethod == PaymentMethod.CREDIT) 0.0 else amount))
                val payableToIncrease = roundMoney((amount - cashToDeduct).coerceAtLeast(0.0))

                // 1. Cash / Liquidity Deduction
                if (cashToDeduct > 0.0) {
                    val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                    if (targetAcc != null) {
                        updatedBankAccounts = updatedBankAccounts.map { acc ->
                            if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance - cashToDeduct)) else acc
                        }
                    }
                }

                // 2. Supplier Credit Payable
                if (payableToIncrease > 0.0 || tx.paymentMethod == PaymentMethod.CREDIT) {
                    val partyNameClean = tx.partyName?.trim() ?: ""
                    val resolvedSuppId = resolvePartyId(tx, updatedCustomers, updatedSuppliers)
                    val isGenericSupplier = resolvedSuppId.isNullOrBlank() && (
                            partyNameClean.isBlank() ||
                            partyNameClean.equals("Supplier", ignoreCase = true) ||
                            partyNameClean.equals("سپلائر", ignoreCase = true)
                    )

                    if (!isGenericSupplier) {
                        val existingSupp = updatedSuppliers.find { s ->
                            (resolvedSuppId != null && s.id == resolvedSuppId) ||
                                    (partyNameClean.isNotBlank() && s.name.trim().equals(partyNameClean, ignoreCase = true))
                        }

                        val activeSuppId = if (existingSupp != null) {
                            existingSupp.id
                        } else {
                            val newSuppId = tx.partyId?.ifBlank { null } ?: "supp_${System.currentTimeMillis()}"
                            val newSupplier = SupplierParty(
                                id = newSuppId,
                                name = if (partyNameClean.isNotBlank()) partyNameClean else "Supplier",
                                phone = "",
                                totalPayable = 0.0,
                                status = "ACTIVE"
                            )
                            updatedSuppliers = updatedSuppliers + newSupplier
                            newSuppId
                        }

                        updatedSuppliers = updatedSuppliers.map { s ->
                            if (s.id == activeSuppId) {
                                s.copy(
                                    totalPayable = roundMoney(s.totalPayable + payableToIncrease),
                                    dueDate = tx.date.ifBlank { FormatUtils.getTodayIsoDate() }
                                )
                            } else s
                        }
                    }
                }

                // 3. Inventory addition & AVCO recalculation
                val purchasedQty = tx.qtyChange ?: 1.0
                if (purchasedQty > 0.0) {
                    var targetItemId = tx.itemId
                    val notesRaw = tx.notes ?: ""
                    val extractedItemName = if (notesRaw.contains("Purchase: ")) {
                        notesRaw.substringAfter("Purchase: ").substringBefore(" x ").trim()
                    } else ""

                    if (targetItemId.isNullOrBlank() || updatedInventory.none { it.id == targetItemId }) {
                        val nameToFind = if (extractedItemName.isNotBlank() && !extractedItemName.equals("Stock In", ignoreCase = true)) {
                            extractedItemName
                        } else null

                        if (nameToFind != null) {
                            val matchByName = updatedInventory.find { it.name.trim().equals(nameToFind, ignoreCase = true) }
                            if (matchByName != null) {
                                targetItemId = matchByName.id
                            } else {
                                val unitCostVal = tx.unitCost ?: (amount / purchasedQty)
                                val newInvItem = InventoryItem(
                                    id = "item_${System.currentTimeMillis()}",
                                    name = nameToFind,
                                    category = tx.category.ifBlank { "General" },
                                    quantity = 0.0,
                                    purchasePrice = unitCostVal,
                                    salePrice = unitCostVal * 1.2,
                                    unit = "pcs"
                                )
                                updatedInventory = updatedInventory + newInvItem
                                targetItemId = newInvItem.id
                            }
                        }
                    }

                    if (!targetItemId.isNullOrBlank()) {
                        val purchaseUnitCost = tx.unitCost ?: (if (purchasedQty > 0) amount / purchasedQty else 0.0)

                        updatedInventory = updatedInventory.map { item ->
                            if (item.id == targetItemId) {
                                val openingStockToKeep = if (item.openingStock > 0.0) item.openingStock else if (item.purchasedQty == 0.0 && item.soldQty == 0.0) item.quantity else 0.0
                                val openingCostToKeep = if (item.openingCostPrice > 0.0) item.openingCostPrice else if (item.purchasedQty == 0.0 && item.soldQty == 0.0) item.purchasePrice else item.purchasePrice
                                val newAvco = calculateAvcoOnPurchase(
                                    existingQuantity = item.quantity,
                                    existingWeightedAverageCost = item.purchasePrice,
                                    purchasedQuantity = purchasedQty,
                                    netPurchaseUnitCost = purchaseUnitCost
                                )
                                item.copy(
                                    openingStock = openingStockToKeep,
                                    openingCostPrice = openingCostToKeep,
                                    quantity = roundQuantity(item.quantity + purchasedQty),
                                    purchasePrice = newAvco,
                                    purchasedQty = roundQuantity(item.purchasedQty + purchasedQty)
                                )
                            } else item
                        }
                    }
                }
            }

            TransactionType.EXPENSE -> {
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance - amount)) else acc
                    }
                }
            }

            TransactionType.RECEIPT -> {
                // Customer debt collection: Cash increases, Customer receivable decreases
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance + amount)) else acc
                    }
                }

                val partyNameClean = tx.partyName?.trim() ?: ""
                val resolvedCustId = resolvePartyId(tx, updatedCustomers, updatedSuppliers)
                val existingCust = updatedCustomers.find { c ->
                    (resolvedCustId != null && c.id == resolvedCustId) ||
                            (partyNameClean.isNotBlank() && c.name.trim().equals(partyNameClean, ignoreCase = true))
                }

                val activeCustId = if (existingCust != null) {
                    existingCust.id
                } else if (partyNameClean.isNotBlank()) {
                    val newCustId = tx.partyId?.ifBlank { null } ?: "cust_${System.currentTimeMillis()}"
                    val newCustomer = CustomerParty(
                        id = newCustId,
                        name = partyNameClean,
                        phone = "",
                        totalCredit = 0.0,
                        status = "ACTIVE"
                    )
                    updatedCustomers = updatedCustomers + newCustomer
                    newCustId
                } else null

                if (activeCustId != null) {
                    updatedCustomers = updatedCustomers.map { c ->
                        if (c.id == activeCustId) c.copy(totalCredit = roundMoney(c.totalCredit - amount)) else c
                    }
                }
            }

            TransactionType.PAYMENT -> {
                // Determine if this payment is a loan repayment or supplier debt settlement
                val isLoanRepayment = !tx.loanAccountId.isNullOrBlank() ||
                        tx.category.equals("LOAN_REPAYMENT", ignoreCase = true) ||
                        tx.category.equals("Loan Repayment", ignoreCase = true)

                // Cash/Bank account decreases
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance - amount)) else acc
                    }
                }

                if (isLoanRepayment) {
                    // Loan repayment: affects loan account outstanding balance, NOT supplier payables
                    val targetLoanId = tx.loanAccountId
                    val partyNameClean = tx.partyName?.trim() ?: ""
                    updatedLoans = updatedLoans.map { loan ->
                        val isMatch = (!targetLoanId.isNullOrBlank() && loan.id == targetLoanId) ||
                                (targetLoanId.isNullOrBlank() && partyNameClean.isNotBlank() && (loan.partyName.trim().equals(partyNameClean, ignoreCase = true) || loan.lenderName.trim().equals(partyNameClean, ignoreCase = true)))
                        if (isMatch) {
                            val clampedRepayment = amount.coerceAtMost(loan.outstandingAmount)
                            loan.copy(outstandingAmount = roundMoney((loan.outstandingAmount - clampedRepayment).coerceAtLeast(0.0)))
                        } else loan
                    }
                } else {
                    // Supplier debt settlement: Supplier payable decreases
                    val partyNameClean = tx.partyName?.trim() ?: ""
                    val resolvedSuppId = resolvePartyId(tx, updatedCustomers, updatedSuppliers)
                    val existingSupp = updatedSuppliers.find { s ->
                        (resolvedSuppId != null && s.id == resolvedSuppId) ||
                                (partyNameClean.isNotBlank() && s.name.trim().equals(partyNameClean, ignoreCase = true))
                    }

                    val activeSuppId = if (existingSupp != null) {
                        existingSupp.id
                    } else if (partyNameClean.isNotBlank()) {
                        val newSuppId = tx.partyId?.ifBlank { null } ?: "supp_${System.currentTimeMillis()}"
                        val newSupplier = SupplierParty(
                            id = newSuppId,
                            name = partyNameClean,
                            phone = "",
                            totalPayable = 0.0,
                            status = "ACTIVE"
                        )
                        updatedSuppliers = updatedSuppliers + newSupplier
                        newSuppId
                    } else null

                    if (activeSuppId != null) {
                        updatedSuppliers = updatedSuppliers.map { s ->
                            if (s.id == activeSuppId) s.copy(totalPayable = roundMoney(s.totalPayable - amount)) else s
                        }
                    }
                }
            }

            TransactionType.TRANSFER -> {
                // Fund Transfer: Source -= amount, Destination += amount
                val fromAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                val toAccId = tx.toAccountId
                updatedBankAccounts = updatedBankAccounts.map { acc ->
                    when {
                        fromAcc != null && acc.id == fromAcc.id -> acc.copy(balance = roundMoney(acc.balance - amount))
                        !toAccId.isNullOrBlank() && acc.id == toAccId -> acc.copy(balance = roundMoney(acc.balance + amount))
                        else -> acc
                    }
                }
            }

            TransactionType.LOAN_TAKEN -> {
                // Financing activity: Cash increases, Loan liability increases
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance + amount)) else acc
                    }
                }
                val targetLoanId = tx.loanAccountId
                val partyNameClean = tx.partyName?.trim() ?: ""
                updatedLoans = updatedLoans.map { loan ->
                    val isMatch = (!targetLoanId.isNullOrBlank() && loan.id == targetLoanId) ||
                            (targetLoanId.isNullOrBlank() && partyNameClean.isNotBlank() && (loan.partyName.trim().equals(partyNameClean, ignoreCase = true) || loan.lenderName.trim().equals(partyNameClean, ignoreCase = true)))
                    if (isMatch) loan.copy(outstandingAmount = roundMoney(loan.outstandingAmount + amount)) else loan
                }
            }

            TransactionType.LOAN_GIVEN -> {
                // Non-operating asset outflow: Cash decreases, Loan asset increases
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance - amount)) else acc
                    }
                }
                val targetLoanId = tx.loanAccountId
                val partyNameClean = tx.partyName?.trim() ?: ""
                updatedLoans = updatedLoans.map { loan ->
                    val isMatch = (!targetLoanId.isNullOrBlank() && loan.id == targetLoanId) ||
                            (targetLoanId.isNullOrBlank() && partyNameClean.isNotBlank() && (loan.partyName.trim().equals(partyNameClean, ignoreCase = true) || loan.lenderName.trim().equals(partyNameClean, ignoreCase = true)))
                    if (isMatch) loan.copy(outstandingAmount = roundMoney(loan.outstandingAmount + amount)) else loan
                }
            }
        }

        return state.copy(
            bankAccounts = updatedBankAccounts,
            customers = updatedCustomers,
            suppliers = updatedSuppliers,
            inventory = updatedInventory,
            loans = updatedLoans
        )
    }

    // -------------------------------------------------------------
    // 4. Core Reversal Rules (reverseTransaction)
    // -------------------------------------------------------------

    /**
     * Reverses the exact historical effects of a transaction from the current state.
     */
    fun reverseTransaction(state: AppState, tx: Transaction): AppState {
        val amount = roundMoney(tx.amount)
        var updatedBankAccounts = state.bankAccounts
        var updatedCustomers = state.customers
        var updatedSuppliers = state.suppliers
        var updatedInventory = state.inventory
        var updatedLoans = state.loans

        when (tx.type) {
            TransactionType.SALE -> {
                // Revert Liquidity or Receivable
                if (tx.paymentMethod == PaymentMethod.CREDIT) {
                    val partyId = resolvePartyId(tx, state.customers, state.suppliers)
                    updatedCustomers = updatedCustomers.map { c ->
                        val isMatch = (partyId != null && c.id == partyId) ||
                                (partyId == null && !tx.partyName.isNullOrBlank() && c.name.equals(tx.partyName, ignoreCase = true))
                        if (isMatch) c.copy(totalCredit = roundMoney(c.totalCredit - amount)) else c
                    }
                } else {
                    val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                    if (targetAcc != null) {
                        updatedBankAccounts = updatedBankAccounts.map { acc ->
                            if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance - amount)) else acc
                        }
                    }
                }

                // Revert Inventory (restore quantity)
                if (!tx.itemId.isNullOrBlank() && tx.qtyChange != null) {
                    val qtySold = tx.qtyChange
                    updatedInventory = updatedInventory.map { item ->
                        if (item.id == tx.itemId) {
                            item.copy(
                                quantity = roundQuantity(item.quantity + qtySold),
                                soldQty = roundQuantity(item.soldQty - qtySold)
                            )
                        } else item
                    }
                }
            }

            TransactionType.PURCHASE -> {
                val cashToRestore = roundMoney(tx.cashPaid ?: (if (tx.paymentMethod == PaymentMethod.CREDIT) 0.0 else amount))
                val payableToReduce = roundMoney((amount - cashToRestore).coerceAtLeast(0.0))

                // 1. Revert Cash / Liquidity Deduction (cash increases)
                if (cashToRestore > 0.0) {
                    val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                    if (targetAcc != null) {
                        updatedBankAccounts = updatedBankAccounts.map { acc ->
                            if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance + cashToRestore)) else acc
                        }
                    }
                }

                // 2. Revert Supplier Credit Payable (payable decreases)
                if (payableToReduce > 0.0 || tx.paymentMethod == PaymentMethod.CREDIT) {
                    val partyId = resolvePartyId(tx, state.customers, state.suppliers)
                    updatedSuppliers = updatedSuppliers.map { s ->
                        val isMatch = (partyId != null && s.id == partyId) ||
                                (partyId == null && !tx.partyName.isNullOrBlank() && s.name.equals(tx.partyName, ignoreCase = true))
                        if (isMatch) s.copy(totalPayable = roundMoney(s.totalPayable - payableToReduce)) else s
                    }
                }

                // 3. Revert Inventory (deduct purchased quantity)
                if (!tx.itemId.isNullOrBlank() && tx.qtyChange != null) {
                    val purchasedQty = tx.qtyChange
                    updatedInventory = updatedInventory.map { item ->
                        if (item.id == tx.itemId) {
                            item.copy(
                                quantity = roundQuantity(item.quantity - purchasedQty),
                                purchasedQty = roundQuantity(item.purchasedQty - purchasedQty)
                            )
                        } else item
                    }
                }
            }

            TransactionType.EXPENSE -> {
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance + amount)) else acc
                    }
                }
            }

            TransactionType.RECEIPT -> {
                // Revert debt collection: Cash decreases, Customer receivable increases
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance - amount)) else acc
                    }
                }
                val partyId = resolvePartyId(tx, state.customers, state.suppliers)
                updatedCustomers = updatedCustomers.map { c ->
                    val isMatch = (partyId != null && c.id == partyId) ||
                            (partyId == null && !tx.partyName.isNullOrBlank() && c.name.equals(tx.partyName, ignoreCase = true))
                    if (isMatch) c.copy(totalCredit = roundMoney(c.totalCredit + amount)) else c
                }
            }

            TransactionType.PAYMENT -> {
                val isLoanRepayment = !tx.loanAccountId.isNullOrBlank() ||
                        tx.category.equals("LOAN_REPAYMENT", ignoreCase = true) ||
                        tx.category.equals("Loan Repayment", ignoreCase = true)

                // Revert cash deduction: Cash increases
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance + amount)) else acc
                    }
                }

                if (isLoanRepayment) {
                    // Revert loan repayment: Loan outstandingAmount increases (cannot exceed original loan amount)
                    val targetLoanId = tx.loanAccountId
                    val partyNameClean = tx.partyName?.trim() ?: ""
                    updatedLoans = updatedLoans.map { loan ->
                        val isMatch = (!targetLoanId.isNullOrBlank() && loan.id == targetLoanId) ||
                                (targetLoanId.isNullOrBlank() && partyNameClean.isNotBlank() && (loan.partyName.trim().equals(partyNameClean, ignoreCase = true) || loan.lenderName.trim().equals(partyNameClean, ignoreCase = true)))
                        if (isMatch) {
                            val maxBalance = if (loan.amount > 0.0) loan.amount else Double.MAX_VALUE
                            loan.copy(outstandingAmount = roundMoney((loan.outstandingAmount + amount).coerceAtMost(maxBalance)))
                        } else loan
                    }
                } else {
                    // Revert debt settlement: Supplier payable increases
                    val partyId = resolvePartyId(tx, state.customers, state.suppliers)
                    updatedSuppliers = updatedSuppliers.map { s ->
                        val isMatch = (partyId != null && s.id == partyId) ||
                                (partyId == null && !tx.partyName.isNullOrBlank() && s.name.equals(tx.partyName, ignoreCase = true))
                        if (isMatch) s.copy(totalPayable = roundMoney(s.totalPayable + amount)) else s
                    }
                }
            }

            TransactionType.TRANSFER -> {
                // Revert Transfer: Source += amount, Destination -= amount
                val fromAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                val toAccId = tx.toAccountId
                updatedBankAccounts = updatedBankAccounts.map { acc ->
                    when {
                        fromAcc != null && acc.id == fromAcc.id -> acc.copy(balance = roundMoney(acc.balance + amount))
                        !toAccId.isNullOrBlank() && acc.id == toAccId -> acc.copy(balance = roundMoney(acc.balance - amount))
                        else -> acc
                    }
                }
            }

            TransactionType.LOAN_TAKEN -> {
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance - amount)) else acc
                    }
                }
                val targetLoanId = tx.loanAccountId
                val partyNameClean = tx.partyName?.trim() ?: ""
                updatedLoans = updatedLoans.map { loan ->
                    val isMatch = (!targetLoanId.isNullOrBlank() && loan.id == targetLoanId) ||
                            (targetLoanId.isNullOrBlank() && partyNameClean.isNotBlank() && (loan.partyName.trim().equals(partyNameClean, ignoreCase = true) || loan.lenderName.trim().equals(partyNameClean, ignoreCase = true)))
                    if (isMatch) loan.copy(outstandingAmount = roundMoney((loan.outstandingAmount - amount).coerceAtLeast(0.0))) else loan
                }
            }

            TransactionType.LOAN_GIVEN -> {
                val targetAcc = findTargetAccount(updatedBankAccounts, tx.accountId, tx.paymentMethod)
                if (targetAcc != null) {
                    updatedBankAccounts = updatedBankAccounts.map { acc ->
                        if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance + amount)) else acc
                    }
                }
                val targetLoanId = tx.loanAccountId
                val partyNameClean = tx.partyName?.trim() ?: ""
                updatedLoans = updatedLoans.map { loan ->
                    val isMatch = (!targetLoanId.isNullOrBlank() && loan.id == targetLoanId) ||
                            (targetLoanId.isNullOrBlank() && partyNameClean.isNotBlank() && (loan.partyName.trim().equals(partyNameClean, ignoreCase = true) || loan.lenderName.trim().equals(partyNameClean, ignoreCase = true)))
                    if (isMatch) loan.copy(outstandingAmount = roundMoney((loan.outstandingAmount - amount).coerceAtLeast(0.0))) else loan
                }
            }
        }

        return state.copy(
            bankAccounts = updatedBankAccounts,
            customers = updatedCustomers,
            suppliers = updatedSuppliers,
            inventory = updatedInventory,
            loans = updatedLoans
        )
    }

    // -------------------------------------------------------------
    // 5. Universal Edit Logic
    // -------------------------------------------------------------

    /**
     * Atomically edits a transaction:
     * 1. Reverses old transaction effects.
     * 2. Applies new transaction effects.
     * 3. Replaces old transaction record with new transaction.
     */
    fun editTransaction(state: AppState, oldTx: Transaction, newTx: Transaction): AppState {
        val stateAfterReverse = reverseTransaction(state, oldTx)
        val stateAfterApply = applyTransactionEffects(stateAfterReverse, newTx)
        val updatedTxList = stateAfterApply.transactions.map {
            if (it.id == oldTx.id) newTx.copy(id = oldTx.id) else it
        }
        val intermediateState = stateAfterApply.copy(transactions = updatedTxList)
        return if (oldTx.type == TransactionType.PURCHASE || newTx.type == TransactionType.PURCHASE) {
            val rebuilt = rebuildBalances(intermediateState)
            rebuilt.copy(deletedTransactions = intermediateState.deletedTransactions)
        } else {
            intermediateState
        }
    }

    // -------------------------------------------------------------
    // 6. Delete & Restore Logic
    // -------------------------------------------------------------

    /**
     * Soft-deletes a transaction by reversing its historical effect and moving to deletedTransactions.
     */
    fun deleteTransaction(state: AppState, txId: String): AppState {
        val targetTx = state.transactions.find { it.id == txId } ?: return state
        val stateAfterReverse = reverseTransaction(state, targetTx)
        val remainingTx = stateAfterReverse.transactions.filter { it.id != txId }
        val newDeleted = (listOf(targetTx) + stateAfterReverse.deletedTransactions).distinctBy { it.id }
        val intermediateState = stateAfterReverse.copy(
            transactions = remainingTx,
            deletedTransactions = newDeleted
        )
        return if (targetTx.type == TransactionType.PURCHASE) {
            val rebuilt = rebuildBalances(intermediateState)
            rebuilt.copy(deletedTransactions = newDeleted)
        } else {
            intermediateState
        }
    }

    /**
     * Restores a deleted transaction by reapplying its exact accounting effect once.
     */
    fun restoreTransaction(state: AppState, txId: String): AppState {
        val targetTx = state.deletedTransactions.find { it.id == txId } ?: return state
        val stateAfterApply = applyTransactionEffects(state, targetTx)
        val remainingDeleted = stateAfterApply.deletedTransactions.filter { it.id != txId }
        val restoredTxList = listOf(targetTx) + stateAfterApply.transactions
        val intermediateState = stateAfterApply.copy(
            transactions = restoredTxList,
            deletedTransactions = remainingDeleted
        )
        return if (targetTx.type == TransactionType.PURCHASE) {
            val rebuilt = rebuildBalances(intermediateState)
            rebuilt.copy(deletedTransactions = remainingDeleted)
        } else {
            intermediateState
        }
    }

    // -------------------------------------------------------------
    // 7. Full Balance Rebuild from Authoritative Ledger
    // -------------------------------------------------------------

    /**
     * Deterministically reconciles and rebuilds all materialized balances:
     * - BankAccount.balance
     * - CustomerParty.totalCredit
     * - SupplierParty.totalPayable
     * - InventoryItem.quantity and AVCO purchasePrice
     *
     * Starts from opening balances and replays valid transactions chronologically.
     */
    fun rebuildBalances(state: AppState): AppState {
        // 1. Reset accounts to opening balances
        val accountsMap = state.bankAccounts.associate { it.id to it.copy(balance = roundMoney(it.openingBalance)) }.toMutableMap()

        // 2. Reset customers to opening balances
        val customersMap = state.customers.associate { it.id to it.copy(totalCredit = roundMoney(it.openingBalance)) }.toMutableMap()

        // 3. Reset suppliers to opening balances
        val suppliersMap = state.suppliers.associate { it.id to it.copy(totalPayable = roundMoney(it.openingBalance)) }.toMutableMap()

        // 4. Reset loans to initial amounts
        val loansMap = state.loans.associate { it.id to it.copy(outstandingAmount = roundMoney(it.amount)) }.toMutableMap()

        // 5. Reset inventory to opening stock and cost
        val inventoryMap = state.inventory.associate { item ->
            val initialCost = if (item.openingCostPrice > 0.0) item.openingCostPrice else item.purchasePrice
            val initialStock = if (item.openingStock > 0.0) item.openingStock else {
                val hasTx = state.transactions.any { it.itemId == item.id }
                if (hasTx) 0.0 else item.quantity
            }
            item.id to item.copy(
                quantity = roundQuantity(initialStock),
                purchasePrice = initialCost,
                purchasedQty = 0.0,
                soldQty = 0.0
            )
        }.toMutableMap()

        // 6. Sort transactions chronologically (ascending by date)
        val sortedTransactions = state.transactions.sortedBy { it.date }

        // 7. Replay ledger
        for (tx in sortedTransactions) {
            val amount = roundMoney(tx.amount)

            when (tx.type) {
                TransactionType.SALE -> {
                    if (tx.paymentMethod == PaymentMethod.CREDIT) {
                        val partyId = resolvePartyId(tx, state.customers, state.suppliers)
                        val targetCust = if (partyId != null) customersMap[partyId] else {
                            customersMap.values.find { !tx.partyName.isNullOrBlank() && it.name.equals(tx.partyName, ignoreCase = true) }
                        }
                        if (targetCust != null) {
                            customersMap[targetCust.id] = targetCust.copy(
                                totalCredit = roundMoney(targetCust.totalCredit + amount)
                            )
                        }
                    } else {
                        val targetAcc = findTargetAccount(accountsMap.values.toList(), tx.accountId, tx.paymentMethod)
                        if (targetAcc != null) {
                            accountsMap[targetAcc.id] = targetAcc.copy(
                                balance = roundMoney(targetAcc.balance + amount)
                            )
                        }
                    }

                    if (!tx.itemId.isNullOrBlank() && tx.qtyChange != null) {
                        val item = inventoryMap[tx.itemId]
                        if (item != null) {
                            inventoryMap[item.id] = item.copy(
                                quantity = roundQuantity(item.quantity - tx.qtyChange),
                                soldQty = roundQuantity(item.soldQty + tx.qtyChange)
                            )
                        }
                    }
                }

                TransactionType.PURCHASE -> {
                    val cashToDeduct = roundMoney(tx.cashPaid ?: (if (tx.paymentMethod == PaymentMethod.CREDIT) 0.0 else amount))
                    val payableToIncrease = roundMoney((amount - cashToDeduct).coerceAtLeast(0.0))

                    // 1. Cash deduction
                    if (cashToDeduct > 0.0) {
                        val targetAcc = findTargetAccount(accountsMap.values.toList(), tx.accountId, tx.paymentMethod)
                        if (targetAcc != null) {
                            accountsMap[targetAcc.id] = targetAcc.copy(
                                balance = roundMoney(targetAcc.balance - cashToDeduct)
                            )
                        }
                    }

                    // 2. Supplier payable
                    if (payableToIncrease > 0.0 || tx.paymentMethod == PaymentMethod.CREDIT) {
                        val partyId = resolvePartyId(tx, state.customers, state.suppliers)
                        val targetSupp = if (partyId != null) suppliersMap[partyId] else {
                            suppliersMap.values.find { !tx.partyName.isNullOrBlank() && it.name.equals(tx.partyName, ignoreCase = true) }
                        }
                        if (targetSupp != null) {
                            suppliersMap[targetSupp.id] = targetSupp.copy(
                                totalPayable = roundMoney(targetSupp.totalPayable + payableToIncrease)
                            )
                        }
                    }

                    // 3. Inventory addition & AVCO
                    if (!tx.itemId.isNullOrBlank() && tx.qtyChange != null && tx.qtyChange > 0.0) {
                        val item = inventoryMap[tx.itemId]
                        if (item != null) {
                            val purchaseUnitCost = tx.unitCost ?: (amount / tx.qtyChange)
                            val newAvco = calculateAvcoOnPurchase(
                                existingQuantity = item.quantity,
                                existingWeightedAverageCost = item.purchasePrice,
                                purchasedQuantity = tx.qtyChange,
                                netPurchaseUnitCost = purchaseUnitCost
                            )
                            inventoryMap[item.id] = item.copy(
                                quantity = roundQuantity(item.quantity + tx.qtyChange),
                                purchasePrice = newAvco,
                                purchasedQty = roundQuantity(item.purchasedQty + tx.qtyChange)
                            )
                        }
                    }
                }

                TransactionType.EXPENSE -> {
                    val targetAcc = findTargetAccount(accountsMap.values.toList(), tx.accountId, tx.paymentMethod)
                    if (targetAcc != null) {
                        accountsMap[targetAcc.id] = targetAcc.copy(
                            balance = roundMoney(targetAcc.balance - amount)
                        )
                    }
                }

                TransactionType.RECEIPT -> {
                    val targetAcc = findTargetAccount(accountsMap.values.toList(), tx.accountId, tx.paymentMethod)
                    if (targetAcc != null) {
                        accountsMap[targetAcc.id] = targetAcc.copy(
                            balance = roundMoney(targetAcc.balance + amount)
                        )
                    }
                    val partyId = resolvePartyId(tx, state.customers, state.suppliers)
                    val targetCust = if (partyId != null) customersMap[partyId] else {
                        customersMap.values.find { !tx.partyName.isNullOrBlank() && it.name.equals(tx.partyName, ignoreCase = true) }
                    }
                    if (targetCust != null) {
                        customersMap[targetCust.id] = targetCust.copy(
                            totalCredit = roundMoney(targetCust.totalCredit - amount)
                        )
                    }
                }

                TransactionType.PAYMENT -> {
                    val isLoanRepayment = !tx.loanAccountId.isNullOrBlank() ||
                            tx.category.equals("LOAN_REPAYMENT", ignoreCase = true) ||
                            tx.category.equals("Loan Repayment", ignoreCase = true)

                    val targetAcc = findTargetAccount(accountsMap.values.toList(), tx.accountId, tx.paymentMethod)
                    if (targetAcc != null) {
                        accountsMap[targetAcc.id] = targetAcc.copy(
                            balance = roundMoney(targetAcc.balance - amount)
                        )
                    }

                    if (isLoanRepayment) {
                        val targetLoan = if (!tx.loanAccountId.isNullOrBlank()) {
                            loansMap[tx.loanAccountId]
                        } else {
                            loansMap.values.find {
                                !tx.partyName.isNullOrBlank() &&
                                (it.partyName.equals(tx.partyName, ignoreCase = true) || it.lenderName.equals(tx.partyName, ignoreCase = true))
                            }
                        }
                        if (targetLoan != null) {
                            val clampedRepayment = amount.coerceAtMost(targetLoan.outstandingAmount)
                            loansMap[targetLoan.id] = targetLoan.copy(
                                outstandingAmount = roundMoney((targetLoan.outstandingAmount - clampedRepayment).coerceAtLeast(0.0))
                            )
                        }
                    } else {
                        val partyId = resolvePartyId(tx, state.customers, state.suppliers)
                        val targetSupp = if (partyId != null) suppliersMap[partyId] else {
                            suppliersMap.values.find { !tx.partyName.isNullOrBlank() && it.name.equals(tx.partyName, ignoreCase = true) }
                        }
                        if (targetSupp != null) {
                            suppliersMap[targetSupp.id] = targetSupp.copy(
                                totalPayable = roundMoney(targetSupp.totalPayable - amount)
                            )
                        }
                    }
                }

                TransactionType.TRANSFER -> {
                    val fromAcc = findTargetAccount(accountsMap.values.toList(), tx.accountId, tx.paymentMethod)
                    val toAccId = tx.toAccountId
                    if (fromAcc != null) {
                        accountsMap[fromAcc.id] = fromAcc.copy(balance = roundMoney(fromAcc.balance - amount))
                    }
                    if (!toAccId.isNullOrBlank() && accountsMap.containsKey(toAccId)) {
                        val toAcc = accountsMap[toAccId]!!
                        accountsMap[toAccId] = toAcc.copy(balance = roundMoney(toAcc.balance + amount))
                    }
                }

                TransactionType.LOAN_TAKEN -> {
                    val targetAcc = findTargetAccount(accountsMap.values.toList(), tx.accountId, tx.paymentMethod)
                    if (targetAcc != null) {
                        accountsMap[targetAcc.id] = targetAcc.copy(
                            balance = roundMoney(targetAcc.balance + amount)
                        )
                    }
                    val targetLoan = if (!tx.loanAccountId.isNullOrBlank()) {
                        loansMap[tx.loanAccountId]
                    } else {
                        loansMap.values.find {
                            !tx.partyName.isNullOrBlank() &&
                            (it.partyName.equals(tx.partyName, ignoreCase = true) || it.lenderName.equals(tx.partyName, ignoreCase = true))
                        }
                    }
                    if (targetLoan != null) {
                        loansMap[targetLoan.id] = targetLoan.copy(
                            outstandingAmount = roundMoney(targetLoan.outstandingAmount + amount)
                        )
                    }
                }

                TransactionType.LOAN_GIVEN -> {
                    val targetAcc = findTargetAccount(accountsMap.values.toList(), tx.accountId, tx.paymentMethod)
                    if (targetAcc != null) {
                        accountsMap[targetAcc.id] = targetAcc.copy(
                            balance = roundMoney(targetAcc.balance - amount)
                        )
                    }
                    val targetLoan = if (!tx.loanAccountId.isNullOrBlank()) {
                        loansMap[tx.loanAccountId]
                    } else {
                        loansMap.values.find {
                            !tx.partyName.isNullOrBlank() &&
                            (it.partyName.equals(tx.partyName, ignoreCase = true) || it.lenderName.equals(tx.partyName, ignoreCase = true))
                        }
                    }
                    if (targetLoan != null) {
                        loansMap[targetLoan.id] = targetLoan.copy(
                            outstandingAmount = roundMoney(targetLoan.outstandingAmount + amount)
                        )
                    }
                }
            }
        }

        return state.copy(
            bankAccounts = accountsMap.values.toList(),
            customers = customersMap.values.toList(),
            suppliers = suppliersMap.values.toList(),
            inventory = inventoryMap.values.toList(),
            loans = loansMap.values.toList()
        )
    }

    // -------------------------------------------------------------
    // 8. Period COGS, Canonical Profit & Cash Flow Calculations
    // -------------------------------------------------------------

    /**
     * Calculates period COGS strictly from verified sale transactions:
     * COGS = qtyChange * captured historical unitCost.
     * Never fabricates missing historical unit costs.
     */
    fun calculatePeriodCogs(
        transactions: List<Transaction>,
        startDate: String? = null,
        endDate: String? = null
    ): CogsResult {
        val periodSales = transactions.filter { tx ->
            if (tx.type != TransactionType.SALE) return@filter false
            val txDate = if (tx.date.length >= 10) tx.date.substring(0, 10) else tx.date
            val afterStart = startDate.isNullOrBlank() || txDate >= startDate
            val beforeEnd = endDate.isNullOrBlank() || txDate <= endDate
            afterStart && beforeEnd
        }

        var verifiedCogs = 0.0
        var verifiedCount = 0
        var unverifiedCount = 0

        for (sale in periodSales) {
            val qty = sale.qtyChange
            val cost = sale.unitCost
            if (qty != null && cost != null && qty > 0.0 && cost >= 0.0 && !sale.isCogsMissing) {
                verifiedCogs += roundMoney(qty * cost)
                verifiedCount++
            } else {
                unverifiedCount++
            }
        }

        return CogsResult(
            verifiedCogs = roundMoney(verifiedCogs),
            unverifiedSalesCount = unverifiedCount,
            verifiedCount = verifiedCount,
            isFullyVerified = unverifiedCount == 0
        )
    }

    /**
     * Canonical period profit calculation:
     * Net Revenue - Verified COGS - Operating Expenses.
     * Losses are never clamped to 0.0.
     */
    fun calculatePeriodProfit(
        transactions: List<Transaction>,
        startDate: String? = null,
        endDate: String? = null
    ): ProfitResult {
        val periodTransactions = transactions.filter { tx ->
            val txDate = if (tx.date.length >= 10) tx.date.substring(0, 10) else tx.date
            val afterStart = startDate.isNullOrBlank() || txDate >= startDate
            val beforeEnd = endDate.isNullOrBlank() || txDate <= endDate
            afterStart && beforeEnd
        }

        val netRevenue = roundMoney(periodTransactions.filter { it.type == TransactionType.SALE }.sumOf { it.amount })
        val cogsRes = calculatePeriodCogs(transactions, startDate, endDate)
        val verifiedCogs = cogsRes.verifiedCogs
        val grossProfit = roundMoney(netRevenue - verifiedCogs)
        val operatingExpenses = roundMoney(periodTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount })
        val netProfit = roundMoney(grossProfit - operatingExpenses)

        return ProfitResult(
            netRevenue = netRevenue,
            verifiedCogs = verifiedCogs,
            grossProfit = grossProfit,
            operatingExpenses = operatingExpenses,
            netProfit = netProfit,
            isCogsFullyVerified = cogsRes.isFullyVerified,
            unverifiedSalesCount = cogsRes.unverifiedSalesCount
        )
    }

    /**
     * Calculates period Cash Flow:
     * Credit sales and credit purchases do NOT affect cash flow.
     * Cash Inflow = Cash/Bank Sales + Customer Receipts + Loan Taken
     * Cash Outflow = Cash/Bank Purchases + Operating Expenses + Supplier Payments + Loan Given
     */
    fun calculateCashFlow(
        transactions: List<Transaction>,
        startDate: String? = null,
        endDate: String? = null
    ): CashFlowResult {
        val periodTransactions = transactions.filter { tx ->
            val txDate = if (tx.date.length >= 10) tx.date.substring(0, 10) else tx.date
            val afterStart = startDate.isNullOrBlank() || txDate >= startDate
            val beforeEnd = endDate.isNullOrBlank() || txDate <= endDate
            afterStart && beforeEnd
        }

        var inflow = 0.0
        var outflow = 0.0

        for (tx in periodTransactions) {
            when (tx.type) {
                TransactionType.SALE -> {
                    if (tx.paymentMethod != PaymentMethod.CREDIT) {
                        inflow += tx.amount
                    }
                }
                TransactionType.RECEIPT -> {
                    inflow += tx.amount
                }
                TransactionType.LOAN_TAKEN -> {
                    inflow += tx.amount
                }
                TransactionType.PURCHASE -> {
                    val cashSpent = tx.cashPaid ?: (if (tx.paymentMethod == PaymentMethod.CREDIT) 0.0 else tx.amount)
                    outflow += cashSpent
                }
                TransactionType.EXPENSE -> {
                    outflow += tx.amount
                }
                TransactionType.PAYMENT -> {
                    outflow += tx.amount
                }
                TransactionType.LOAN_GIVEN -> {
                    outflow += tx.amount
                }
                TransactionType.TRANSFER -> {
                    // Internal transfer: no net change in total business liquidity
                }
            }
        }

        val roundedInflow = roundMoney(inflow)
        val roundedOutflow = roundMoney(outflow)
        return CashFlowResult(
            cashInflow = roundedInflow,
            cashOutflow = roundedOutflow,
            netCashFlow = roundMoney(roundedInflow - roundedOutflow)
        )
    }

    // -------------------------------------------------------------
    // 9. Legacy Transaction Classification
    // -------------------------------------------------------------

    /**
     * Classifies legacy transactions according to data completeness:
     * - Class A: Deterministically reconstructable (valid item and quantity present).
     * - Class B: Non-reconstructable (missing item, quantity, or cost). Never invent values.
     * - Class C: Estimated cost fallback explicitly flagged as estimated.
     */
    fun classifyLegacyTransaction(tx: Transaction, inventory: List<InventoryItem>): Transaction {
        if (tx.type != TransactionType.SALE && tx.type != TransactionType.PURCHASE) {
            return tx
        }

        // If transaction already has valid captured unit cost and qtyChange
        if (tx.qtyChange != null && tx.unitCost != null && tx.qtyChange > 0.0) {
            return tx.copy(
                legacyClassification = "CLASS_A",
                isCogsMissing = false,
                isCogsEstimated = false
            )
        }

        // Try deterministic match if itemId exists
        val matchedItem = if (!tx.itemId.isNullOrBlank()) inventory.find { it.id == tx.itemId } else null
        if (matchedItem != null && tx.qtyChange != null && tx.qtyChange > 0.0) {
            // Class A: item exists and quantity is known
            return tx.copy(
                legacyClassification = "CLASS_A",
                unitCost = tx.unitCost ?: matchedItem.purchasePrice,
                isCogsMissing = false,
                isCogsEstimated = false
            )
        }

        // Otherwise: Class B - Non-reconstructable. Do not invent arbitrary quantity or cost!
        return tx.copy(
            legacyClassification = "CLASS_B",
            isCogsMissing = true,
            isCogsEstimated = false
        )
    }

    // -------------------------------------------------------------
    // 10. Commercial Balance Sheet (Double-Entry A = L + E)
    // -------------------------------------------------------------

    /**
     * Calculates the commercial business balance sheet.
     * Business Assets:
     * - Cash, Bank & Mobile Wallets
     * - Closing Inventory at AVCO
     * - Customer Receivables (including archived debtors with non-zero dues)
     * - Business Fixed Assets
     *
     * Liabilities:
     * - Supplier Payables (including archived vendors with non-zero dues)
     * - Business Loans
     *
     * Equity:
     * - Commercial Net Worth = Total Assets - Total Liabilities
     * Personal assets are strictly excluded from the commercial business balance sheet.
     */
    fun calculateBalanceSheet(state: AppState): BalanceSheetResult {
        val cashAndBank = roundMoney(state.bankAccounts.sumOf { it.balance })
        val inventoryValue = roundMoney(state.inventory.sumOf { it.quantity * it.purchasePrice })
        val customerReceivables = roundMoney(state.customers.sumOf { it.totalCredit })
        val businessFixedAssets = roundMoney(state.businessAssets.sumOf { it.currentValue })
        val totalAssets = roundMoney(cashAndBank + inventoryValue + customerReceivables + businessFixedAssets)

        val supplierPayables = roundMoney(state.suppliers.sumOf { it.totalPayable })
        val loansPayable = roundMoney(state.loans.sumOf { it.outstandingAmount })
        val totalLiabilities = roundMoney(supplierPayables + loansPayable)

        val totalEquity = roundMoney(totalAssets - totalLiabilities)
        val diff = roundMoney(totalAssets - (totalLiabilities + totalEquity))
        val isBalanced = kotlin.math.abs(diff) < 0.01

        return BalanceSheetResult(
            cashAndBank = cashAndBank,
            inventoryValue = inventoryValue,
            customerReceivables = customerReceivables,
            businessFixedAssets = businessFixedAssets,
            totalAssets = totalAssets,
            supplierPayables = supplierPayables,
            loansPayable = loansPayable,
            totalLiabilities = totalLiabilities,
            totalEquity = totalEquity,
            isBalanced = isBalanced,
            difference = diff
        )
    }

    // -------------------------------------------------------------
    // 11. Precise Calendar Date Filter Calculations
    // -------------------------------------------------------------

    fun getYesterdayIsoDate(referenceDate: Date = Date()): String {
        val cal = Calendar.getInstance().apply {
            time = referenceDate
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun getLastMonthRange(referenceDate: Date = Date()): Pair<String, String> {
        val cal = Calendar.getInstance().apply {
            time = referenceDate
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        val prefix = SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return Pair("$prefix-01", String.format(Locale.US, "$prefix-%02d", maxDay))
    }

    fun getLastMonthIsoPrefix(referenceDate: Date = Date()): String {
        val cal = Calendar.getInstance().apply {
            time = referenceDate
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        return SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    }

    fun getThisWeekRange(referenceDate: Date = Date()): Pair<String, String> {
        val cal = Calendar.getInstance().apply {
            time = referenceDate
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val weekStart = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_WEEK, 6)
        val weekEnd = sdf.format(cal.time)
        return Pair(weekStart, weekEnd)
    }

    fun matchesTransactionDateFilter(
        txDateStr: String,
        filter: TransactionDateFilter,
        customStart: String = "",
        customEnd: String = "",
        referenceDate: Date = Date()
    ): Boolean {
        val txDateOnly = if (txDateStr.length >= 10) txDateStr.substring(0, 10) else txDateStr
        if (txDateOnly.isBlank()) return false
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(referenceDate)

        return when (filter) {
            TransactionDateFilter.ALL -> true
            TransactionDateFilter.TODAY -> txDateOnly == todayIso
            TransactionDateFilter.YESTERDAY -> txDateOnly == getYesterdayIsoDate(referenceDate)
            TransactionDateFilter.THIS_MONTH -> {
                val thisMonthPrefix = todayIso.take(7)
                txDateOnly.startsWith(thisMonthPrefix)
            }
            TransactionDateFilter.LAST_MONTH -> {
                val lastMonthRange = getLastMonthRange(referenceDate)
                txDateOnly in lastMonthRange.first..lastMonthRange.second
            }
            TransactionDateFilter.CUSTOM -> {
                val afterStart = customStart.isBlank() || txDateOnly >= customStart
                val beforeEnd = customEnd.isBlank() || txDateOnly <= customEnd
                afterStart && beforeEnd
            }
        }
    }

    // -------------------------------------------------------------
    // 12. Accounting Returns & Reversals
    // -------------------------------------------------------------

    /**
     * Applies a sale return:
     * - Reverses revenue by refund amount
     * - Reverses cash/bank if cash refund, or reduces customer receivable if credit return
     * - Restores inventory stock quantity
     * - Restores/reverses COGS using the original sale historical unitCost (NOT current AVCO!)
     */
    fun applySaleReturn(
        state: AppState,
        returnAmount: Double,
        qtyReturned: Double,
        itemId: String?,
        partyId: String?,
        partyName: String?,
        paymentMethod: PaymentMethod,
        accountId: String?,
        originalSaleUnitCost: Double?
    ): AppState {
        val cleanAmount = roundMoney(returnAmount)
        val cleanQty = roundQuantity(qtyReturned)
        var updatedBankAccounts = state.bankAccounts
        var updatedCustomers = state.customers
        var updatedInventory = state.inventory

        // 1. Reverse cash or receivable
        if (paymentMethod == PaymentMethod.CREDIT) {
            val resolvedId = partyId ?: (if (!partyName.isNullOrBlank()) state.customers.find { it.name.equals(partyName, true) }?.id else null)
            updatedCustomers = updatedCustomers.map { c ->
                if ((resolvedId != null && c.id == resolvedId) || (!partyName.isNullOrBlank() && c.name.equals(partyName, true))) {
                    c.copy(totalCredit = roundMoney(c.totalCredit - cleanAmount))
                } else c
            }
        } else {
            val targetAcc = findTargetAccount(updatedBankAccounts, accountId, paymentMethod)
            if (targetAcc != null) {
                updatedBankAccounts = updatedBankAccounts.map { acc ->
                    if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance - cleanAmount)) else acc
                }
            }
        }

        // 2. Restore inventory quantity (stock increases; unitCost preserved)
        if (!itemId.isNullOrBlank() && cleanQty > 0.0) {
            updatedInventory = updatedInventory.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        quantity = roundQuantity(item.quantity + cleanQty),
                        soldQty = roundQuantity((item.soldQty - cleanQty).coerceAtLeast(0.0)),
                        returnedQty = roundQuantity(item.returnedQty + cleanQty)
                    )
                } else item
            }
        }

        return state.copy(
            bankAccounts = updatedBankAccounts,
            customers = updatedCustomers,
            inventory = updatedInventory
        )
    }

    /**
     * Applies a purchase return:
     * - Reduces inventory stock quantity
     * - Reduces supplier payable if on credit, or increases cash/bank if cash refund received
     * - No artificial operating revenue or expense created
     */
    fun applyPurchaseReturn(
        state: AppState,
        returnAmount: Double,
        qtyReturned: Double,
        itemId: String?,
        partyId: String?,
        partyName: String?,
        paymentMethod: PaymentMethod,
        accountId: String?
    ): AppState {
        val cleanAmount = roundMoney(returnAmount)
        val cleanQty = roundQuantity(qtyReturned)
        var updatedBankAccounts = state.bankAccounts
        var updatedSuppliers = state.suppliers
        var updatedInventory = state.inventory

        if (paymentMethod == PaymentMethod.CREDIT) {
            val resolvedId = partyId ?: (if (!partyName.isNullOrBlank()) state.suppliers.find { it.name.equals(partyName, true) }?.id else null)
            updatedSuppliers = updatedSuppliers.map { s ->
                if ((resolvedId != null && s.id == resolvedId) || (!partyName.isNullOrBlank() && s.name.equals(partyName, true))) {
                    s.copy(totalPayable = roundMoney(s.totalPayable - cleanAmount))
                } else s
            }
        } else {
            val targetAcc = findTargetAccount(updatedBankAccounts, accountId, paymentMethod)
            if (targetAcc != null) {
                updatedBankAccounts = updatedBankAccounts.map { acc ->
                    if (acc.id == targetAcc.id) acc.copy(balance = roundMoney(acc.balance + cleanAmount)) else acc
                }
            }
        }

        if (!itemId.isNullOrBlank() && cleanQty > 0.0) {
            updatedInventory = updatedInventory.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        quantity = roundQuantity((item.quantity - cleanQty).coerceAtLeast(0.0)),
                        purchasedQty = roundQuantity((item.purchasedQty - cleanQty).coerceAtLeast(0.0))
                    )
                } else item
            }
        }

        return state.copy(
            bankAccounts = updatedBankAccounts,
            suppliers = updatedSuppliers,
            inventory = updatedInventory
        )
    }
}
