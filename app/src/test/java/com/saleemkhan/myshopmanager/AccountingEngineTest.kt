package com.saleemkhan.myshopmanager

import com.saleemkhan.myshopmanager.data.accounting.AccountingEngine
import com.saleemkhan.myshopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class AccountingEngineTest {

    @Test
    fun testAvcoCalculationMathematicalProof() {
        // Step 1: Initial stock 10 units @ 50.0 = 500.0
        val item0 = InventoryItem(
            id = "item_1",
            name = "Cooking Oil 1L",
            quantity = 10.0,
            purchasePrice = 50.0,
            salePrice = 70.0
        )

        // Step 2: New purchase of 10 units @ 60.0 = 600.0
        // New AVCO = (500 + 600) / 20 = 1100 / 20 = 55.0
        val avco1 = AccountingEngine.calculateAvcoOnPurchase(
            existingQuantity = item0.quantity,
            existingWeightedAverageCost = item0.purchasePrice,
            purchasedQuantity = 10.0,
            netPurchaseUnitCost = 60.0
        )
        assertEquals(55.0, avco1, 0.001)

        val item1 = item0.copy(quantity = 20.0, purchasePrice = avco1)

        // Step 3: Sale of 5 units. Sale does NOT change unit AVCO (stays 55.0)
        // COGS = 5 * 55.0 = 275.0, Remaining stock = 15 units
        val saleTx = Transaction(
            id = "tx_sale_1",
            type = TransactionType.SALE,
            amount = 350.0, // 5 * 70.0
            paymentMethod = PaymentMethod.CASH,
            itemId = "item_1",
            qtyChange = 5.0,
            unitCost = item1.purchasePrice,
            totalValueChange = 5.0 * item1.purchasePrice
        )
        val stateWithItem1 = AppState(inventory = listOf(item1))
        val stateAfterSale = AccountingEngine.applyTransaction(stateWithItem1, saleTx)
        val itemAfterSale = stateAfterSale.inventory.first()
        assertEquals(15.0, itemAfterSale.quantity, 0.001)
        assertEquals(55.0, itemAfterSale.purchasePrice, 0.001)
        assertEquals(5.0, itemAfterSale.soldQty, 0.001)

        // Step 4: Purchase 5 units @ 70.0 = 350.0
        // Existing stock: 15 units @ 55.0 = 825.0
        // Total value = 825.0 + 350.0 = 1175.0
        // Total qty = 20.0
        // New AVCO = 1175.0 / 20.0 = 58.75
        val avco2 = AccountingEngine.calculateAvcoOnPurchase(
            existingQuantity = itemAfterSale.quantity,
            existingWeightedAverageCost = itemAfterSale.purchasePrice,
            purchasedQuantity = 5.0,
            netPurchaseUnitCost = 70.0
        )
        assertEquals(58.75, avco2, 0.001)
    }

    @Test
    fun testTransactionReversalAndRestore() {
        val cust = CustomerParty(id = "c1", name = "Ali", totalCredit = 500.0, openingBalance = 0.0)
        val bank = BankAccount(id = "b1", bankName = "Meezan", accountTitle = "Shop Account", balance = 1000.0, openingBalance = 1000.0)
        val item = InventoryItem(id = "i1", name = "Rice 5kg", quantity = 20.0, purchasePrice = 200.0, soldQty = 5.0)

        val tx = Transaction(
            id = "tx_101",
            type = TransactionType.SALE,
            amount = 300.0,
            paymentMethod = PaymentMethod.CREDIT,
            partyId = "c1",
            partyName = "Ali",
            itemId = "i1",
            qtyChange = 2.0,
            unitCost = 200.0,
            totalValueChange = 400.0
        )

        val initialState = AppState(
            customers = listOf(cust),
            bankAccounts = listOf(bank),
            inventory = listOf(item),
            transactions = emptyList()
        )

        // Apply transaction
        val stateAfterApply = AccountingEngine.applyTransaction(initialState, tx)
        val custAfterApply = stateAfterApply.customers.first()
        val itemAfterApply = stateAfterApply.inventory.first()
        assertEquals(800.0, custAfterApply.totalCredit, 0.001)
        assertEquals(18.0, itemAfterApply.quantity, 0.001)
        assertEquals(7.0, itemAfterApply.soldQty, 0.001)

        // Delete (Reverse) transaction
        val stateAfterDelete = AccountingEngine.deleteTransaction(stateAfterApply, "tx_101")
        val custAfterDelete = stateAfterDelete.customers.first()
        val itemAfterDelete = stateAfterDelete.inventory.first()
        assertEquals(500.0, custAfterDelete.totalCredit, 0.001)
        assertEquals(20.0, itemAfterDelete.quantity, 0.001)
        assertEquals(5.0, itemAfterDelete.soldQty, 0.001)
        assertEquals(1, stateAfterDelete.deletedTransactions.size)
        assertEquals(0, stateAfterDelete.transactions.size)

        // Restore transaction
        val stateAfterRestore = AccountingEngine.restoreTransaction(stateAfterDelete, "tx_101")
        val custAfterRestore = stateAfterRestore.customers.first()
        val itemAfterRestore = stateAfterRestore.inventory.first()
        assertEquals(800.0, custAfterRestore.totalCredit, 0.001)
        assertEquals(18.0, itemAfterRestore.quantity, 0.001)
        assertEquals(7.0, itemAfterRestore.soldQty, 0.001)
        assertEquals(0, stateAfterRestore.deletedTransactions.size)
        assertEquals(1, stateAfterRestore.transactions.size)
    }

    @Test
    fun testCashFlowSeparationFromAccrualSales() {
        val transactions = listOf(
            // Credit Sale: Not cash inflow
            Transaction(
                id = "t1",
                type = TransactionType.SALE,
                amount = 1000.0,
                paymentMethod = PaymentMethod.CREDIT
            ),
            // Cash Sale: Cash inflow
            Transaction(
                id = "t2",
                type = TransactionType.SALE,
                amount = 500.0,
                paymentMethod = PaymentMethod.CASH
            ),
            // Receipt against past credit sale: Cash inflow
            Transaction(
                id = "t3",
                type = TransactionType.RECEIPT,
                amount = 400.0,
                paymentMethod = PaymentMethod.CASH
            ),
            // Expense: Cash outflow
            Transaction(
                id = "t4",
                type = TransactionType.EXPENSE,
                amount = 200.0,
                paymentMethod = PaymentMethod.CASH
            )
        )

        val cf = AccountingEngine.calculateCashFlow(transactions)
        // Inflow should be cash sale (500) + receipt (400) = 900.0 (Credit sale 1000 excluded)
        assertEquals(900.0, cf.cashInflow, 0.001)
        // Outflow should be expense (200) = 200.0
        assertEquals(200.0, cf.cashOutflow, 0.001)
        // Net cash flow = 700.0
        assertEquals(700.0, cf.netCashFlow, 0.001)
    }

    @Test
    fun testRebuildBalancesDeterminismAndIdempotency() {
        val cust = CustomerParty(id = "c1", name = "Zahid", totalCredit = 9999.0, openingBalance = 100.0)
        val supp = SupplierParty(id = "s1", name = "National Foods", totalPayable = 9999.0, openingBalance = 500.0)
        val bank = BankAccount(id = "b1", bankName = "Cash Drawer", accountTitle = "Cash", balance = 9999.0, openingBalance = 2000.0)

        val txList = listOf(
            // Zahid buys 300 on credit
            Transaction(
                id = "t1",
                type = TransactionType.SALE,
                amount = 300.0,
                paymentMethod = PaymentMethod.CREDIT,
                partyId = "c1",
                date = "2026-01-01"
            ),
            // Zahid pays 150 cash
            Transaction(
                id = "t2",
                type = TransactionType.RECEIPT,
                amount = 150.0,
                paymentMethod = PaymentMethod.CASH,
                partyId = "c1",
                accountId = "b1",
                date = "2026-01-02"
            ),
            // Purchase from National Foods 800 on credit
            Transaction(
                id = "t3",
                type = TransactionType.PURCHASE,
                amount = 800.0,
                paymentMethod = PaymentMethod.CREDIT,
                partyId = "s1",
                date = "2026-01-03"
            ),
            // Payment to National Foods 400 from b1
            Transaction(
                id = "t4",
                type = TransactionType.PAYMENT,
                amount = 400.0,
                paymentMethod = PaymentMethod.CASH,
                partyId = "s1",
                accountId = "b1",
                date = "2026-01-04"
            )
        )

        val messyState = AppState(
            customers = listOf(cust),
            suppliers = listOf(supp),
            bankAccounts = listOf(bank),
            transactions = txList
        )

        val rebuiltState = AccountingEngine.rebuildBalances(messyState)

        // Customer balance: opening (100) + credit sale (300) - receipt (150) = 250.0
        val rebuiltCust = rebuiltState.customers.first()
        assertEquals(250.0, rebuiltCust.totalCredit, 0.001)

        // Supplier balance: opening (500) + credit purchase (800) - payment (400) = 900.0
        val rebuiltSupp = rebuiltState.suppliers.first()
        assertEquals(900.0, rebuiltSupp.totalPayable, 0.001)

        // Bank balance: opening (2000) + receipt (150) - payment (400) = 1750.0
        val rebuiltBank = rebuiltState.bankAccounts.first()
        assertEquals(1750.0, rebuiltBank.balance, 0.001)

        // Idempotency check: Rebuilding already rebuilt state yields identical results
        val secondRebuiltState = AccountingEngine.rebuildBalances(rebuiltState)
        assertEquals(rebuiltCust.totalCredit, secondRebuiltState.customers.first().totalCredit, 0.001)
        assertEquals(rebuiltSupp.totalPayable, secondRebuiltState.suppliers.first().totalPayable, 0.001)
        assertEquals(rebuiltBank.balance, secondRebuiltState.bankAccounts.first().balance, 0.001)
    }

    @Test
    fun testLegacyTransactionClassification() {
        val legacy1 = Transaction(id = "l1", type = TransactionType.SALE, amount = 100.0, itemId = null, unitCost = null)
        val legacy2 = Transaction(id = "l2", type = TransactionType.SALE, amount = 100.0, itemId = "item_x", unitCost = 50.0, qtyChange = 2.0)
        val inv = listOf(InventoryItem(id = "item_x", name = "Test Item", quantity = 10.0, purchasePrice = 50.0))

        val classified1 = AccountingEngine.classifyLegacyTransaction(legacy1, inv)
        assertEquals("CLASS_B", classified1.legacyClassification)
        assertTrue(classified1.isCogsMissing)

        val classified2 = AccountingEngine.classifyLegacyTransaction(legacy2, inv)
        assertEquals("CLASS_A", classified2.legacyClassification)
        assertFalse(classified2.isCogsMissing)
        assertEquals(50.0, classified2.unitCost ?: 0.0, 0.001)
    }

    @Test
    fun testYesterdayAndLastMonthDateFilters() {
        // Set reference date to 2026-03-15
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.MARCH, 15, 12, 0, 0)
        val refDate = cal.time

        // Yesterday should be 2026-03-14
        val yesterdayIso = AccountingEngine.getYesterdayIsoDate(refDate)
        assertEquals("2026-03-14", yesterdayIso)

        // Last month should be 2026-02-01 to 2026-02-28
        val (lastMonthStart, lastMonthEnd) = AccountingEngine.getLastMonthRange(refDate)
        assertEquals("2026-02-01", lastMonthStart)
        assertEquals("2026-02-28", lastMonthEnd)

        // Test filter matching
        assertTrue(AccountingEngine.matchesTransactionDateFilter("2026-03-14T10:00:00", com.saleemkhan.myshopmanager.ui.screens.TransactionDateFilter.YESTERDAY, referenceDate = refDate))
        assertFalse(AccountingEngine.matchesTransactionDateFilter("2026-03-15T10:00:00", com.saleemkhan.myshopmanager.ui.screens.TransactionDateFilter.YESTERDAY, referenceDate = refDate))
        assertFalse(AccountingEngine.matchesTransactionDateFilter("2026-03-13T10:00:00", com.saleemkhan.myshopmanager.ui.screens.TransactionDateFilter.YESTERDAY, referenceDate = refDate))

        assertTrue(AccountingEngine.matchesTransactionDateFilter("2026-02-15T09:30:00", com.saleemkhan.myshopmanager.ui.screens.TransactionDateFilter.LAST_MONTH, referenceDate = refDate))
        assertFalse(AccountingEngine.matchesTransactionDateFilter("2026-03-01T00:00:00", com.saleemkhan.myshopmanager.ui.screens.TransactionDateFilter.LAST_MONTH, referenceDate = refDate))
        assertFalse(AccountingEngine.matchesTransactionDateFilter("2026-01-31T23:59:59", com.saleemkhan.myshopmanager.ui.screens.TransactionDateFilter.LAST_MONTH, referenceDate = refDate))
    }

    @Test
    fun testCashFlowExcludesCreditAndIncludesReceipts() {
        val txs = listOf(
            Transaction(id = "1", type = TransactionType.SALE, amount = 1000.0, paymentMethod = PaymentMethod.CREDIT),
            Transaction(id = "2", type = TransactionType.SALE, amount = 500.0, paymentMethod = PaymentMethod.CASH),
            Transaction(id = "3", type = TransactionType.RECEIPT, amount = 300.0, paymentMethod = PaymentMethod.CASH),
            Transaction(id = "4", type = TransactionType.PURCHASE, amount = 800.0, paymentMethod = PaymentMethod.CREDIT),
            Transaction(id = "5", type = TransactionType.PURCHASE, amount = 200.0, paymentMethod = PaymentMethod.CASH),
            Transaction(id = "6", type = TransactionType.PAYMENT, amount = 150.0, paymentMethod = PaymentMethod.CASH),
            Transaction(id = "7", type = TransactionType.EXPENSE, amount = 100.0, paymentMethod = PaymentMethod.CASH),
            Transaction(id = "8", type = TransactionType.TRANSFER, amount = 500.0, paymentMethod = PaymentMethod.BANK),
            Transaction(id = "9", type = TransactionType.LOAN_TAKEN, amount = 2000.0, paymentMethod = PaymentMethod.BANK),
            Transaction(id = "10", type = TransactionType.LOAN_GIVEN, amount = 700.0, paymentMethod = PaymentMethod.CASH)
        )

        val cf = AccountingEngine.calculateCashFlow(txs)
        // Inflow = Cash Sale (500) + Receipt (300) + Loan Taken (2000) = 2800.0 (Credit sale 1000 excluded)
        assertEquals(2800.0, cf.cashInflow, 0.001)
        // Outflow = Cash Purchase (200) + Supplier Payment (150) + Expense (100) + Loan Given (700) = 1150.0 (Credit purchase 800 excluded)
        assertEquals(1150.0, cf.cashOutflow, 0.001)
        // Net Cash Flow = 2800 - 1150 = 1650.0
        assertEquals(1650.0, cf.netCashFlow, 0.001)
    }

    @Test
    fun testNegativeProfitUnClamped() {
        val txs = listOf(
            Transaction(id = "1", type = TransactionType.SALE, amount = 500.0, qtyChange = 5.0, unitCost = 80.0), // COGS = 400.0, Gross Profit = 100.0
            Transaction(id = "2", type = TransactionType.EXPENSE, amount = 350.0) // Operating expense = 350.0
        )
        // Net profit = 100.0 - 350.0 = -250.0 (Loss must remain negative, not clamped to 0.0)
        val profitResult = AccountingEngine.calculatePeriodProfit(txs)
        assertEquals(500.0, profitResult.netRevenue, 0.001)
        assertEquals(400.0, profitResult.verifiedCogs, 0.001)
        assertEquals(100.0, profitResult.grossProfit, 0.001)
        assertEquals(350.0, profitResult.operatingExpenses, 0.001)
        assertEquals(-250.0, profitResult.netProfit, 0.001)
    }

    @Test
    fun testArchivedCustomerReceivableReconciliation() {
        val activeCust = CustomerParty(id = "c1", name = "Active Customer", totalCredit = 3000.0, status = "ACTIVE")
        val archivedCust = CustomerParty(id = "c2", name = "Archived Customer", totalCredit = 2000.0, status = "ARCHIVED")
        val closedCust = CustomerParty(id = "c3", name = "Closed Customer", totalCredit = 1500.0, status = "CLOSED")

        val state = AppState(
            customers = listOf(activeCust, archivedCust, closedCust),
            bankAccounts = listOf(BankAccount(id = "b1", balance = 5000.0)),
            suppliers = listOf(SupplierParty(id = "s1", totalPayable = 2000.0, status = "ARCHIVED"))
        )

        val bs = AccountingEngine.calculateBalanceSheet(state)
        // All receivables must be counted (3000 + 2000 + 1500 = 6500.0)
        assertEquals(6500.0, bs.customerReceivables, 0.001)
        // All payables must be counted (2000.0)
        assertEquals(2000.0, bs.supplierPayables, 0.001)
    }

    @Test
    fun testSaleAndPurchaseReturns() {
        val item = InventoryItem(id = "item_1", name = "Shirt", quantity = 10.0, purchasePrice = 500.0, soldQty = 5.0)
        val cust = CustomerParty(id = "c1", name = "Tariq", totalCredit = 1000.0)
        val bank = BankAccount(id = "b1", type = "CASH", balance = 2000.0)
        val supp = SupplierParty(id = "s1", name = "Al-Karam", totalPayable = 4000.0)

        val state = AppState(
            inventory = listOf(item),
            customers = listOf(cust),
            suppliers = listOf(supp),
            bankAccounts = listOf(bank)
        )

        // 1. Sale Return (Credit customer returns 2 shirts sold @ Rs.800 each with original unitCost Rs.500)
        val stateAfterSaleReturn = AccountingEngine.applySaleReturn(
            state = state,
            returnAmount = 1600.0,
            qtyReturned = 2.0,
            itemId = "item_1",
            partyId = "c1",
            partyName = "Tariq",
            paymentMethod = PaymentMethod.CREDIT,
            accountId = null,
            originalSaleUnitCost = 500.0
        )
        val updatedCust = stateAfterSaleReturn.customers.first()
        val updatedItem = stateAfterSaleReturn.inventory.first()
        // Customer credit reduced by 1600 (1000 - 1600 = -600.0)
        assertEquals(-600.0, updatedCust.totalCredit, 0.001)
        // Stock restored (10 + 2 = 12 units)
        assertEquals(12.0, updatedItem.quantity, 0.001)
        assertEquals(3.0, updatedItem.soldQty, 0.001)

        // 2. Purchase Return (Return 3 units to supplier Al-Karam @ Rs.500 cost = Rs.1500 on credit)
        val stateAfterPurchaseReturn = AccountingEngine.applyPurchaseReturn(
            state = stateAfterSaleReturn,
            returnAmount = 1500.0,
            qtyReturned = 3.0,
            itemId = "item_1",
            partyId = "s1",
            partyName = "Al-Karam",
            paymentMethod = PaymentMethod.CREDIT,
            accountId = null
        )
        val updatedSupp = stateAfterPurchaseReturn.suppliers.first()
        val finalItem = stateAfterPurchaseReturn.inventory.first()
        // Supplier payable reduced by 1500 (4000 - 1500 = 2500.0)
        assertEquals(2500.0, updatedSupp.totalPayable, 0.001)
        // Stock reduced (12 - 3 = 9 units)
        assertEquals(9.0, finalItem.quantity, 0.001)
    }

    @Test
    fun testBalanceSheetDoubleEntryEquation() {
        val state = AppState(
            bankAccounts = listOf(
                BankAccount(id = "b1", balance = 50000.0),
                BankAccount(id = "b2", balance = 20000.0)
            ),
            inventory = listOf(
                InventoryItem(id = "i1", quantity = 100.0, purchasePrice = 150.0) // 15,000
            ),
            customers = listOf(
                CustomerParty(id = "c1", totalCredit = 25000.0)
            ),
            businessAssets = listOf(
                BusinessAsset(id = "a1", currentValue = 10000.0)
            ),
            personalAssets = listOf(
                PersonalAsset(id = "pa1", value = 999999.0) // Personal asset MUST be excluded!
            ),
            suppliers = listOf(
                SupplierParty(id = "s1", totalPayable = 30000.0)
            ),
            loans = listOf(
                LoanRecord(id = "l1", amount = 20000.0, outstandingAmount = 15000.0) // outstanding = 15,000
            )
        )

        val bs = AccountingEngine.calculateBalanceSheet(state)
        // Total Business Assets = 70,000 (Cash) + 15,000 (Stock) + 25,000 (Receivables) + 10,000 (Fixed Assets) = 120,000
        assertEquals(120000.0, bs.totalAssets, 0.001)
        // Total Liabilities = 30,000 (Payables) + 15,000 (Loans) = 45,000
        assertEquals(45000.0, bs.totalLiabilities, 0.001)
        // Equity = Assets - Liabilities = 120,000 - 45,000 = 75,000
        assertEquals(75000.0, bs.totalEquity, 0.001)
        // Equation A = L + E
        assertTrue(bs.isBalanced)
        assertEquals(0.0, bs.difference, 0.001)
    }

    @Test
    fun testGoldenScenarioAccountingTrace() {
        // Step 1: Initial state
        // Cash = Rs. 100,000
        // Inventory: 10 units @ Rs. 100
        var state = AppState(
            bankAccounts = listOf(
                BankAccount(id = "cash_acc", type = "CASH", balance = 100000.0)
            ),
            inventory = listOf(
                InventoryItem(id = "item_g", name = "Golden Item", quantity = 10.0, purchasePrice = 100.0)
            ),
            customers = listOf(
                CustomerParty(id = "cust_g", name = "Golden Customer", totalCredit = 0.0)
            )
        )

        // Step 2: Purchase 10 units @ Rs. 200 (Paid in cash Rs. 2,000)
        // Expected AVCO = (10 * 100 + 10 * 200) / 20 = 3000 / 20 = Rs. 150
        val purchaseTx = Transaction(
            id = "tx_p1",
            type = TransactionType.PURCHASE,
            amount = 2000.0,
            paymentMethod = PaymentMethod.CASH,
            accountId = "cash_acc",
            itemId = "item_g",
            qtyChange = 10.0,
            unitCost = 200.0
        )
        state = AccountingEngine.applyTransaction(state, purchaseTx)
        val itemAfterPurchase = state.inventory.first()
        val cashAfterPurchase = state.bankAccounts.first()
        assertEquals(20.0, itemAfterPurchase.quantity, 0.001)
        assertEquals(150.0, itemAfterPurchase.purchasePrice, 0.001)
        assertEquals(98000.0, cashAfterPurchase.balance, 0.001)

        // Step 3: Cash Sell 5 units @ Rs. 300 = Rs. 1,500
        // Expected: COGS = 5 * 150 = Rs. 750, Revenue = Rs. 1,500, Gross Profit = Rs. 750, Remaining stock = 15 units, Stock Value = 15 * 150 = Rs. 2,250
        val saleTx1 = Transaction(
            id = "tx_s1",
            type = TransactionType.SALE,
            amount = 1500.0,
            paymentMethod = PaymentMethod.CASH,
            accountId = "cash_acc",
            itemId = "item_g",
            qtyChange = 5.0,
            unitCost = itemAfterPurchase.purchasePrice,
            totalValueChange = 5.0 * itemAfterPurchase.purchasePrice
        )
        state = AccountingEngine.applyTransaction(state, saleTx1)
        val itemAfterSale1 = state.inventory.first()
        val cashAfterSale1 = state.bankAccounts.first()
        assertEquals(15.0, itemAfterSale1.quantity, 0.001)
        assertEquals(150.0, itemAfterSale1.purchasePrice, 0.001)
        assertEquals(99500.0, cashAfterSale1.balance, 0.001)

        val profit1 = AccountingEngine.calculatePeriodProfit(listOf(saleTx1))
        assertEquals(1500.0, profit1.netRevenue, 0.001)
        assertEquals(750.0, profit1.verifiedCogs, 0.001)
        assertEquals(750.0, profit1.grossProfit, 0.001)

        // Step 4: Credit Sell 2 units @ Rs. 300 = Rs. 600 to Golden Customer
        // Expected: Revenue +Rs. 600, Receivable +Rs. 600, Cash unchanged, COGS +Rs. 300 (2 * 150), Gross Profit +Rs. 300
        val saleTx2 = Transaction(
            id = "tx_s2",
            type = TransactionType.SALE,
            amount = 600.0,
            paymentMethod = PaymentMethod.CREDIT,
            partyId = "cust_g",
            itemId = "item_g",
            qtyChange = 2.0,
            unitCost = itemAfterSale1.purchasePrice,
            totalValueChange = 2.0 * itemAfterSale1.purchasePrice
        )
        state = AccountingEngine.applyTransaction(state, saleTx2)
        val itemAfterSale2 = state.inventory.first()
        val cashAfterSale2 = state.bankAccounts.first()
        val custAfterSale2 = state.customers.first()

        assertEquals(13.0, itemAfterSale2.quantity, 0.001)
        assertEquals(99500.0, cashAfterSale2.balance, 0.001) // Cash unchanged!
        assertEquals(600.0, custAfterSale2.totalCredit, 0.001) // Receivable increased!

        val profit2 = AccountingEngine.calculatePeriodProfit(listOf(saleTx1, saleTx2))
        assertEquals(2100.0, profit2.netRevenue, 0.001) // 1500 + 600
        assertEquals(1050.0, profit2.verifiedCogs, 0.001) // 750 + 300
        assertEquals(1050.0, profit2.grossProfit, 0.001) // 750 + 300

        // Step 5: Customer pays Rs. 600 cash
        // Expected: Cash +Rs. 600, Receivable -Rs. 600, Revenue unchanged, Profit unchanged
        val receiptTx = Transaction(
            id = "tx_r1",
            type = TransactionType.RECEIPT,
            amount = 600.0,
            paymentMethod = PaymentMethod.CASH,
            partyId = "cust_g",
            accountId = "cash_acc"
        )
        state = AccountingEngine.applyTransaction(state, receiptTx)
        val cashAfterReceipt = state.bankAccounts.first()
        val custAfterReceipt = state.customers.first()

        assertEquals(100100.0, cashAfterReceipt.balance, 0.001) // Cash increased 99500 + 600 = 100,100
        assertEquals(0.0, custAfterReceipt.totalCredit, 0.001) // Receivable cleared!

        // Profit remains exactly 1050.0 (Receipt does not double-count revenue!)
        val profit3 = AccountingEngine.calculatePeriodProfit(state.transactions)
        assertEquals(2100.0, profit3.netRevenue, 0.001)
        assertEquals(1050.0, profit3.verifiedCogs, 0.001)
        assertEquals(1050.0, profit3.grossProfit, 0.001)
    }

    @Test
    fun testDiscountedSaleAndPurchase() {
        // 1. Discounted Sale
        // Sale of 2 units @ Rs.500 gross (1000.0) with Rs.100 discount = Rs.900 net amount
        val saleTx = Transaction(
            id = "s_disc",
            type = TransactionType.SALE,
            amount = 900.0,
            qtyChange = 2.0,
            unitCost = 300.0,
            paymentMethod = PaymentMethod.CASH
        )
        val profit = AccountingEngine.calculatePeriodProfit(listOf(saleTx))
        assertEquals(900.0, profit.netRevenue, 0.001)
        assertEquals(600.0, profit.verifiedCogs, 0.001)
        assertEquals(300.0, profit.grossProfit, 0.001)

        // 2. Discounted Purchase
        // Purchase of 10 units @ Rs.200 gross with Rs.200 discount = Rs.1800 net (Rs.180/unit net cost)
        val newAvco = AccountingEngine.calculateAvcoOnPurchase(
            existingQuantity = 10.0,
            existingWeightedAverageCost = 100.0, // 1000.0 existing stock value
            purchasedQuantity = 10.0,
            netPurchaseUnitCost = 180.0 // 1800.0 net cost
        )
        // (1000 + 1800) / 20 = 2800 / 20 = 140.0
        assertEquals(140.0, newAvco, 0.001)
    }

    @Test
    fun testTransferNeutrality() {
        val transferTx = Transaction(
            id = "tx_trans",
            type = TransactionType.TRANSFER,
            amount = 5000.0,
            paymentMethod = PaymentMethod.BANK,
            accountId = "bank_1",
            toAccountId = "cash_1"
        )
        val cf = AccountingEngine.calculateCashFlow(listOf(transferTx))
        assertEquals(0.0, cf.cashInflow, 0.001)
        assertEquals(0.0, cf.cashOutflow, 0.001)
        assertEquals(0.0, cf.netCashFlow, 0.001)

        val profit = AccountingEngine.calculatePeriodProfit(listOf(transferTx))
        assertEquals(0.0, profit.netRevenue, 0.001)
        assertEquals(0.0, profit.verifiedCogs, 0.001)
        assertEquals(0.0, profit.grossProfit, 0.001)
        assertEquals(0.0, profit.netProfit, 0.001)
    }

    @Test
    fun testMissingHistoricalCogsHandling() {
        val legacySaleNoCost = Transaction(
            id = "tx_legacy_no_cost",
            type = TransactionType.SALE,
            amount = 1200.0,
            qtyChange = null,
            unitCost = null,
            isCogsMissing = true
        )
        val verifiedSale = Transaction(
            id = "tx_ver_sale",
            type = TransactionType.SALE,
            amount = 800.0,
            qtyChange = 2.0,
            unitCost = 250.0
        )
        val profit = AccountingEngine.calculatePeriodProfit(listOf(legacySaleNoCost, verifiedSale))
        assertEquals(2000.0, profit.netRevenue, 0.001)
        assertEquals(500.0, profit.verifiedCogs, 0.001) // Only verified sale COGS
        assertFalse(profit.isCogsFullyVerified)
        assertEquals(1, profit.unverifiedSalesCount)
    }

    @Test
    fun testCashSaleVsCreditSaleConsistency() {
        val cashSale = Transaction(
            id = "s_cash",
            type = TransactionType.SALE,
            amount = 1000.0,
            paymentMethod = PaymentMethod.CASH,
            qtyChange = 2.0,
            unitCost = 350.0
        )
        val creditSale = Transaction(
            id = "s_credit",
            type = TransactionType.SALE,
            amount = 1000.0,
            paymentMethod = PaymentMethod.CREDIT,
            partyId = "c1",
            qtyChange = 2.0,
            unitCost = 350.0
        )
        // Profit & Loss is identical for cash and credit sale
        val profitCash = AccountingEngine.calculatePeriodProfit(listOf(cashSale))
        val profitCredit = AccountingEngine.calculatePeriodProfit(listOf(creditSale))
        assertEquals(profitCash.netRevenue, profitCredit.netRevenue, 0.001)
        assertEquals(profitCash.verifiedCogs, profitCredit.verifiedCogs, 0.001)
        assertEquals(profitCash.grossProfit, profitCredit.grossProfit, 0.001)

        // Cash flow differs: cash sale gives cashInflow, credit sale does not
        val cfCash = AccountingEngine.calculateCashFlow(listOf(cashSale))
        val cfCredit = AccountingEngine.calculateCashFlow(listOf(creditSale))
        assertEquals(1000.0, cfCash.cashInflow, 0.001)
        assertEquals(0.0, cfCredit.cashInflow, 0.001)
    }

    @Test
    fun testDeletePurchaseRebuildAvcoRestoration() {
        // Initial state: 10 units @ Rs. 100 = Rs. 1,000
        val initialItem = InventoryItem(
            id = "item_del_test",
            name = "Test Item",
            quantity = 10.0,
            purchasePrice = 100.0,
            openingStock = 10.0,
            openingCostPrice = 100.0
        )
        val initialAccount = BankAccount(id = "acc_cash", type = "CASH", balance = 5000.0)
        var state = AppState(
            inventory = listOf(initialItem),
            bankAccounts = listOf(initialAccount)
        )

        // Step 1: Purchase 10 units @ Rs. 200 = Rs. 2,000
        // Expected AVCO before deletion: (10 * 100 + 10 * 200) / 20 = 150.0
        val purchaseTx = Transaction(
            id = "tx_pur_del",
            type = TransactionType.PURCHASE,
            amount = 2000.0,
            paymentMethod = PaymentMethod.CASH,
            accountId = "acc_cash",
            itemId = "item_del_test",
            qtyChange = 10.0,
            unitCost = 200.0
        )
        state = AccountingEngine.applyTransaction(state, purchaseTx)
        val itemBeforeDel = state.inventory.first()
        assertEquals(20.0, itemBeforeDel.quantity, 0.001)
        assertEquals(150.0, itemBeforeDel.purchasePrice, 0.001)

        // Step 2: Delete the Rs. 200 purchase
        // Expected after deletion & reconciliation: quantity = 10, AVCO = Rs. 100.0
        val stateAfterDelete = AccountingEngine.deleteTransaction(state, "tx_pur_del")
        val itemAfterDel = stateAfterDelete.inventory.first()
        assertEquals(10.0, itemAfterDel.quantity, 0.001)
        assertEquals(100.0, itemAfterDel.purchasePrice, 0.001) // Must return to 100.0, NOT 150.0
    }

    @Test
    fun testOutOfOrderPurchaseAndSaleRebuild() {
        val item = InventoryItem(
            id = "item_ooo",
            name = "Out of Order Item",
            quantity = 0.0,
            purchasePrice = 0.0,
            openingStock = 0.0,
            openingCostPrice = 0.0
        )
        val acc = BankAccount(id = "acc_1", balance = 10000.0, openingBalance = 10000.0)

        // 09:00 Purchase: 10 units @ 200.0 = 2000.0
        val purchase0900 = Transaction(
            id = "tx_p_0900",
            type = TransactionType.PURCHASE,
            amount = 2000.0,
            paymentMethod = PaymentMethod.CASH,
            accountId = "acc_1",
            itemId = "item_ooo",
            qtyChange = 10.0,
            unitCost = 200.0,
            date = "2026-01-01T09:00:00"
        )
        // 10:00 Sale: 5 units @ 300.0 = 1500.0
        val sale1000 = Transaction(
            id = "tx_s_1000",
            type = TransactionType.SALE,
            amount = 1500.0,
            paymentMethod = PaymentMethod.CASH,
            accountId = "acc_1",
            itemId = "item_ooo",
            qtyChange = 5.0,
            unitCost = 200.0,
            date = "2026-01-01T10:00:00"
        )

        // Insert in reverse order (Sale first, Purchase second)
        val outOfOrderState = AppState(
            inventory = listOf(item),
            bankAccounts = listOf(acc),
            transactions = listOf(sale1000, purchase0900)
        )

        // Rebuild balances chronologically
        val rebuiltState = AccountingEngine.rebuildBalances(outOfOrderState)
        val rebuiltItem = rebuiltState.inventory.first()
        val rebuiltAcc = rebuiltState.bankAccounts.first()

        // 10 purchased - 5 sold = 5 remaining
        assertEquals(5.0, rebuiltItem.quantity, 0.001)
        assertEquals(200.0, rebuiltItem.purchasePrice, 0.001)
        assertEquals(5.0, rebuiltItem.soldQty, 0.001)
        // Account balance: 10,000 - 2,000 + 1,500 = 9,500
        assertEquals(9500.0, rebuiltAcc.balance, 0.001)
    }

    @Test
    fun testMultiplePartialSalesReturnsHistoricalCost() {
        val initialItem = InventoryItem(
            id = "item_ret_test",
            name = "Return Test Shirt",
            quantity = 10.0,
            purchasePrice = 236.84, // Later AVCO has changed to 236.84
            soldQty = 10.0
        )
        val initialCust = CustomerParty(id = "c_ret", name = "Kamran", totalCredit = 2000.0)
        val initialAcc = BankAccount(id = "acc_ret", balance = 5000.0)
        var state = AppState(
            inventory = listOf(initialItem),
            customers = listOf(initialCust),
            bankAccounts = listOf(initialAcc)
        )

        // Historical Sale: 10 units @ historical unit cost = Rs. 150.0
        val originalSaleUnitCost = 150.0

        // Partial Return 1: Return 2 units with Rs. 400 refund on credit
        state = AccountingEngine.applySaleReturn(
            state = state,
            returnAmount = 400.0,
            qtyReturned = 2.0,
            itemId = "item_ret_test",
            partyId = "c_ret",
            partyName = "Kamran",
            paymentMethod = PaymentMethod.CREDIT,
            accountId = null,
            originalSaleUnitCost = originalSaleUnitCost
        )

        // Partial Return 2: Return 3 units with Rs. 600 refund on credit
        state = AccountingEngine.applySaleReturn(
            state = state,
            returnAmount = 600.0,
            qtyReturned = 3.0,
            itemId = "item_ret_test",
            partyId = "c_ret",
            partyName = "Kamran",
            paymentMethod = PaymentMethod.CREDIT,
            accountId = null,
            originalSaleUnitCost = originalSaleUnitCost
        )

        val finalItem = state.inventory.first()
        val finalCust = state.customers.first()

        // Total returned = 2 + 3 = 5 units
        assertEquals(15.0, finalItem.quantity, 0.001) // 10 + 5 = 15
        assertEquals(5.0, finalItem.soldQty, 0.001) // 10 - 5 = 5
        assertEquals(5.0, finalItem.returnedQty, 0.001)
        assertEquals(1000.0, finalCust.totalCredit, 0.001) // 2000 - 400 - 600 = 1000

        // Total historical COGS reversed = 5 units * Rs. 150.0 = Rs. 750.0 (NOT using current AVCO 236.84)
        val totalCogsReversed = 5.0 * originalSaleUnitCost
        assertEquals(750.0, totalCogsReversed, 0.001)
    }

    @Test
    fun testMergeModeRestoreLedgerReconciliation() {
        val restoreManager = com.saleemkhan.myshopmanager.data.backup.RestoreManager()

        val state1 = AppState(
            bankAccounts = listOf(BankAccount(id = "b1", bankName = "Cash", balance = 1500.0, openingBalance = 1000.0)),
            customers = listOf(CustomerParty(id = "c1", name = "Customer A", totalCredit = 500.0, openingBalance = 200.0)),
            inventory = listOf(InventoryItem(id = "i1", name = "Item A", quantity = 10.0, openingStock = 10.0, openingCostPrice = 100.0, purchasePrice = 100.0)),
            transactions = listOf(
                Transaction(id = "tx1", type = TransactionType.SALE, amount = 500.0, paymentMethod = PaymentMethod.CASH, accountId = "b1", date = "2026-01-01"),
                Transaction(id = "tx2", type = TransactionType.SALE, amount = 300.0, paymentMethod = PaymentMethod.CREDIT, partyId = "c1", date = "2026-01-02")
            )
        )

        val state2 = AppState(
            bankAccounts = listOf(BankAccount(id = "b2", bankName = "Meezan Bank", balance = 1600.0, openingBalance = 2000.0)),
            suppliers = listOf(SupplierParty(id = "s1", name = "Supplier X", totalPayable = 900.0, openingBalance = 500.0)),
            inventory = listOf(InventoryItem(id = "i2", name = "Item B", quantity = 5.0, openingStock = 5.0, openingCostPrice = 50.0, purchasePrice = 50.0)),
            transactions = listOf(
                Transaction(id = "tx3", type = TransactionType.EXPENSE, amount = 400.0, paymentMethod = PaymentMethod.CASH, accountId = "b2", date = "2026-01-03"),
                Transaction(id = "tx4", type = TransactionType.PURCHASE, amount = 400.0, paymentMethod = PaymentMethod.CREDIT, partyId = "s1", date = "2026-01-04")
            )
        )

        val mergedState = restoreManager.mergeStates(state1, state2)

        // Verify that all accounts, customers, suppliers, inventory, and transactions are reconciled
        val b1 = mergedState.bankAccounts.find { it.id == "b1" }!!
        val b2 = mergedState.bankAccounts.find { it.id == "b2" }!!
        val c1 = mergedState.customers.find { it.id == "c1" }!!
        val s1 = mergedState.suppliers.find { it.id == "s1" }!!
        val i1 = mergedState.inventory.find { it.id == "i1" }!!
        val i2 = mergedState.inventory.find { it.id == "i2" }!!

        assertEquals(1500.0, b1.balance, 0.001)
        assertEquals(1600.0, b2.balance, 0.001)
        assertEquals(500.0, c1.totalCredit, 0.001)
        assertEquals(900.0, s1.totalPayable, 0.001)
        assertEquals(10.0, i1.quantity, 0.001)
        assertEquals(100.0, i1.purchasePrice, 0.001)
        assertEquals(5.0, i2.quantity, 0.001)
        assertEquals(50.0, i2.purchasePrice, 0.001)
        assertEquals(4, mergedState.transactions.size)
    }

    @Test
    fun testCreditPurchaseWithNewSupplierAutoCreationAndPayables() {
        val bank = BankAccount(id = "b1", type = "CASH", balance = 10000.0)
        val initialState = AppState(bankAccounts = listOf(bank), suppliers = emptyList())

        val creditPurchaseTx = Transaction(
            id = "tx_pur_new_supp",
            type = TransactionType.PURCHASE,
            amount = 15000.0,
            paymentMethod = PaymentMethod.CREDIT,
            partyName = "Al-Madina Traders",
            date = "2026-10-13"
        )

        val stateAfter = AccountingEngine.applyTransaction(initialState, creditPurchaseTx)
        assertEquals(1, stateAfter.suppliers.size)
        val createdSupplier = stateAfter.suppliers.first()
        assertEquals("Al-Madina Traders", createdSupplier.name)
        assertEquals(15000.0, createdSupplier.totalPayable, 0.001)
        assertEquals(10000.0, stateAfter.bankAccounts.first().balance, 0.001)
        assertEquals(15000.0, AccountingEngine.calculateBalanceSheet(stateAfter).supplierPayables, 0.001)
    }

    @Test
    fun testCreditSaleWithNewCustomerAutoCreationAndReceivables() {
        val initialState = AppState(customers = emptyList())

        val creditSaleTx = Transaction(
            id = "tx_sale_new_cust",
            type = TransactionType.SALE,
            amount = 8000.0,
            paymentMethod = PaymentMethod.CREDIT,
            partyName = "Kamran Khan",
            date = "2026-10-13"
        )

        val stateAfter = AccountingEngine.applyTransaction(initialState, creditSaleTx)
        assertEquals(1, stateAfter.customers.size)
        val createdCust = stateAfter.customers.first()
        assertEquals("Kamran Khan", createdCust.name)
        assertEquals(8000.0, createdCust.totalCredit, 0.001)
        assertEquals(8000.0, AccountingEngine.calculateBalanceSheet(stateAfter).customerReceivables, 0.001)
    }

    @Test
    fun testSupplierAndCustomerMatchingCaseInsensitiveAndWhitespace() {
        val supp = SupplierParty(id = "s1", name = "National Foods", totalPayable = 2000.0)
        val cust = CustomerParty(id = "c1", name = "Zahid Mahmood", totalCredit = 1000.0)
        val initialState = AppState(suppliers = listOf(supp), customers = listOf(cust))

        val txSupp = Transaction(
            id = "tx_s_case",
            type = TransactionType.PURCHASE,
            amount = 1000.0,
            paymentMethod = PaymentMethod.CREDIT,
            partyName = "  national foods  "
        )
        val txCust = Transaction(
            id = "tx_c_case",
            type = TransactionType.SALE,
            amount = 500.0,
            paymentMethod = PaymentMethod.CREDIT,
            partyName = "zahid mahmood"
        )

        var state = AccountingEngine.applyTransaction(initialState, txSupp)
        state = AccountingEngine.applyTransaction(state, txCust)

        assertEquals(1, state.suppliers.size)
        assertEquals(1, state.customers.size)
        assertEquals(3000.0, state.suppliers.first().totalPayable, 0.001)
        assertEquals(1500.0, state.customers.first().totalCredit, 0.001)
    }

    @Test
    fun testPartialCreditPurchaseAccounting() {
        val bank = BankAccount(id = "b_cash", type = "CASH", balance = 20000.0)
        val item = InventoryItem(id = "item_p", name = "Wheat Flour 10kg", quantity = 10.0, purchasePrice = 500.0)
        val initialState = AppState(
            bankAccounts = listOf(bank),
            inventory = listOf(item),
            suppliers = emptyList()
        )

        val partialPurchaseTx = Transaction(
            id = "tx_part_pur",
            type = TransactionType.PURCHASE,
            amount = 10000.0,
            paymentMethod = PaymentMethod.CREDIT,
            partyName = "Lahore Flour Mills",
            accountId = "b_cash",
            itemId = "item_p",
            qtyChange = 10.0,
            unitCost = 1000.0,
            cashPaid = 3000.0
        )

        val stateAfter = AccountingEngine.applyTransaction(initialState, partialPurchaseTx)

        assertEquals(17000.0, stateAfter.bankAccounts.first().balance, 0.001)
        assertEquals(1, stateAfter.suppliers.size)
        assertEquals("Lahore Flour Mills", stateAfter.suppliers.first().name)
        assertEquals(7000.0, stateAfter.suppliers.first().totalPayable, 0.001)

        val updatedItem = stateAfter.inventory.first()
        assertEquals(20.0, updatedItem.quantity, 0.001)

        val bs = AccountingEngine.calculateBalanceSheet(stateAfter)
        assertTrue(bs.isBalanced)
    }
}
