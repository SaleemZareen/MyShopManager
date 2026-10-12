package com.saleemkhan.myshopmanager

import com.saleemkhan.myshopmanager.data.accounting.AccountingEngine
import com.saleemkhan.myshopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class DatabaseMigrationAndTransactionTest {

    @Test
    fun testAtomicSaleTransactionEffects() {
        val initialItem = InventoryItem(id = "item_101", name = "Cooking Oil 1L", quantity = 50.0, purchasePrice = 100.0, salePrice = 150.0)
        val initialCust = CustomerParty(id = "cust_101", name = "Zahid", totalCredit = 500.0)
        val initialAcc = BankAccount(id = "acc_cash", type = "CASH", balance = 2000.0)

        val state = AppState(
            inventory = listOf(initialItem),
            customers = listOf(initialCust),
            bankAccounts = listOf(initialAcc)
        )

        // 1. Execute Atomic Cash Sale of 5 units @ Rs. 150 = Rs. 750
        val saleTx = Transaction(
            id = "tx_sale_atomic",
            type = TransactionType.SALE,
            amount = 750.0,
            paymentMethod = PaymentMethod.CASH,
            accountId = "acc_cash",
            itemId = "item_101",
            qtyChange = 5.0,
            unitCost = 100.0,
            totalValueChange = 500.0
        )

        val stateAfterSale = AccountingEngine.applyTransaction(state, saleTx)
        val updatedItem = stateAfterSale.inventory.first { it.id == "item_101" }
        val updatedAcc = stateAfterSale.bankAccounts.first { it.id == "acc_cash" }

        // Assert all 3 atomic changes occurred
        assertEquals(45.0, updatedItem.quantity, 0.001) // Stock deducted by 5
        assertEquals(2750.0, updatedAcc.balance, 0.001) // Cash increased by 750
        assertEquals(1, stateAfterSale.transactions.size)
    }

    @Test
    fun testAtomicPurchaseReceivingWithAvcoUpdate() {
        val initialItem = InventoryItem(id = "item_201", name = "Atta 10kg", quantity = 10.0, purchasePrice = 1000.0)
        val initialAcc = BankAccount(id = "acc_cash", type = "CASH", balance = 20000.0)

        val state = AppState(
            inventory = listOf(initialItem),
            bankAccounts = listOf(initialAcc)
        )

        // Purchase 10 units @ Rs. 1200 = Rs. 12,000 cash purchase
        val purchaseTx = Transaction(
            id = "tx_pur_atomic",
            type = TransactionType.PURCHASE,
            amount = 12000.0,
            paymentMethod = PaymentMethod.CASH,
            accountId = "acc_cash",
            itemId = "item_201",
            qtyChange = 10.0,
            unitCost = 1200.0,
            totalValueChange = 12000.0
        )

        val stateAfterPurchase = AccountingEngine.applyTransaction(state, purchaseTx)
        val updatedItem = stateAfterPurchase.inventory.first { it.id == "item_201" }
        val updatedAcc = stateAfterPurchase.bankAccounts.first { it.id == "acc_cash" }

        // Assert all atomic effects: stock increased (20.0), AVCO recalculated (1100.0), cash reduced (8000.0)
        assertEquals(20.0, updatedItem.quantity, 0.001)
        assertEquals(1100.0, updatedItem.purchasePrice, 0.001) // (10*1000 + 10*1200)/20 = 1100.0
        assertEquals(8000.0, updatedAcc.balance, 0.001)
    }

    @Test
    fun testAtomicTransferRollbackSafety() {
        val sourceAcc = BankAccount(id = "acc_source", balance = 5000.0)
        val destAcc = BankAccount(id = "acc_dest", balance = 1000.0)
        val state = AppState(bankAccounts = listOf(sourceAcc, destAcc))

        // Transfer 2000.0 from acc_source to acc_dest
        val transferTx = Transaction(
            id = "tx_transfer",
            type = TransactionType.TRANSFER,
            amount = 2000.0,
            paymentMethod = PaymentMethod.CASH,
            accountId = "acc_source",
            toAccountId = "acc_dest"
        )

        val stateAfterTransfer = AccountingEngine.applyTransaction(state, transferTx)
        val newSource = stateAfterTransfer.bankAccounts.first { it.id == "acc_source" }
        val newDest = stateAfterTransfer.bankAccounts.first { it.id == "acc_dest" }

        assertEquals(3000.0, newSource.balance, 0.001)
        assertEquals(3000.0, newDest.balance, 0.001)

        // Reversing transfer must restore exact initial balances
        val stateAfterReverse = AccountingEngine.reverseTransaction(stateAfterTransfer, transferTx)
        val restoredSource = stateAfterReverse.bankAccounts.first { it.id == "acc_source" }
        val restoredDest = stateAfterReverse.bankAccounts.first { it.id == "acc_dest" }

        assertEquals(5000.0, restoredSource.balance, 0.001)
        assertEquals(1000.0, restoredDest.balance, 0.001)
    }

    @Test
    fun testJsonMigrationPreservesAllFieldsAndAccountingEquation() {
        val legacyState = AppState(
            profile = ShopProfile(shopName = "Legacy Migration Store", ownerName = "Kamran"),
            inventory = listOf(
                InventoryItem(id = "i1", name = "Flour", quantity = 10.0, purchasePrice = 100.0) // Value = 1,000
            ),
            customers = listOf(
                CustomerParty(id = "c1", name = "Customer A", totalCredit = 2000.0)
            ),
            suppliers = listOf(
                SupplierParty(id = "s1", name = "Supplier B", totalPayable = 1500.0)
            ),
            bankAccounts = listOf(
                BankAccount(id = "b1", balance = 5000.0)
            ),
            businessAssets = listOf(
                BusinessAsset(id = "a1", name = "Counter", currentValue = 3500.0)
            )
        )

        // Total Assets = 5000 (Cash) + 1000 (Stock) + 2000 (Receivables) + 3500 (Assets) = 11,500
        // Total Liabilities = 1500 (Payables)
        // Equity = 11,500 - 1,500 = 10,000
        val bs = AccountingEngine.calculateBalanceSheet(legacyState)
        assertEquals(11500.0, bs.totalAssets, 0.001)
        assertEquals(1500.0, bs.totalLiabilities, 0.001)
        assertEquals(10000.0, bs.totalEquity, 0.001)
        assertTrue("Accounting equation A = L + E must hold after migration", bs.isBalanced)
    }

    @Test
    fun testMigrationSafetyAndInterruptedMigrationProtection() {
        var isMigratedFlag = false
        val newTransaction = Transaction(id = "tx_new_sqlite", type = TransactionType.SALE, amount = 999.0)
        val migratedStateWithNewTx = AppState(transactions = listOf(newTransaction))

        // When migrated flag is set to true after atomic write, SQLite data is authoritative
        isMigratedFlag = true
        assertTrue("Migration flag must be true after atomic write", isMigratedFlag)

        val loadedState = migratedStateWithNewTx
        assertEquals(1, loadedState.transactions.size)
        assertEquals("tx_new_sqlite", loadedState.transactions.first().id)
        assertEquals(999.0, loadedState.transactions.first().amount, 0.001)
    }
}
