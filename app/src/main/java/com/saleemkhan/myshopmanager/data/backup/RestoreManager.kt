package com.saleemkhan.myshopmanager.data.backup

import android.content.Context
import com.saleemkhan.myshopmanager.data.AppStateRepository
import com.saleemkhan.myshopmanager.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RestoreManager(
    private val localBackupProvider: LocalBackupProvider = LocalBackupProvider()
) {

    suspend fun restore(
        context: Context,
        backupState: AppState,
        mode: RestoreMode = RestoreMode.REPLACE_ALL,
        createSafetyBackup: Boolean = true
    ): RestoreResult = withContext(Dispatchers.IO) {
        val repository = AppStateRepository.getInstance(context)
        val currentState = repository.appState.value

        var safetyBackupPath: String? = null
        if (createSafetyBackup) {
            val safetyResult = localBackupProvider.createQuickDeviceBackup(context, currentState, isSafetyBackup = true)
            if (safetyResult.isSuccess) {
                safetyBackupPath = safetyResult.getOrNull()?.absolutePath
            }
        }

        try {
            val finalState = when (mode) {
                RestoreMode.REPLACE_ALL -> backupState
                RestoreMode.MERGE -> mergeStates(currentState, backupState)
            }

            repository.saveState(finalState)

            val totalItems = finalState.inventory.size + finalState.customers.size +
                    finalState.suppliers.size + finalState.transactions.size

            RestoreResult(
                success = true,
                message = if (mode == RestoreMode.REPLACE_ALL) {
                    "ڈیٹا کامیابی سے بحال ہو گیا (Full Restore Complete)"
                } else {
                    "ڈیٹا کامیابی سے ضم اور بحال ہو گیا (Merge Complete)"
                },
                safetyBackupPath = safetyBackupPath,
                itemsRestoredCount = totalItems
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(
                success = false,
                message = "بحالی میں خرابی: ${e.message ?: "Unknown error"}",
                safetyBackupPath = safetyBackupPath,
                itemsRestoredCount = 0
            )
        }
    }

    fun mergeStates(current: AppState, incoming: AppState): AppState {
        // 1. Transactions merge by unique ID
        val currentTxIds = current.transactions.map { it.id }.toSet()
        val newTx = incoming.transactions.filter { it.id !in currentTxIds }
        val mergedTx = current.transactions + newTx

        // 2. Inventory merge by ID or by name
        val currentItemIds = current.inventory.map { it.id }.toSet()
        val currentItemNames = current.inventory.map { it.name.trim().lowercase() }.toSet()
        val newItems = incoming.inventory.filter { it.id !in currentItemIds && it.name.trim().lowercase() !in currentItemNames }
        val mergedInventory = current.inventory + newItems

        // 3. Customers merge by ID or name
        val currentCustIds = current.customers.map { it.id }.toSet()
        val currentCustNames = current.customers.map { it.name.trim().lowercase() }.toSet()
        val newCust = incoming.customers.filter { it.id !in currentCustIds && it.name.trim().lowercase() !in currentCustNames }
        val mergedCust = current.customers + newCust

        // 4. Suppliers merge by ID or name
        val currentSuppIds = current.suppliers.map { it.id }.toSet()
        val currentSuppNames = current.suppliers.map { it.name.trim().lowercase() }.toSet()
        val newSupp = incoming.suppliers.filter { it.id !in currentSuppIds && it.name.trim().lowercase() !in currentSuppNames }
        val mergedSupp = current.suppliers + newSupp

        // 5. Bank Accounts merge by ID or number
        val currentAccIds = current.bankAccounts.map { it.id }.toSet()
        val currentAccNums = current.bankAccounts.map { it.accountNumber.trim() }.filter { it.isNotBlank() }.toSet()
        val newAcc = incoming.bankAccounts.filter { it.id !in currentAccIds && (it.accountNumber.isBlank() || it.accountNumber.trim() !in currentAccNums) }
        val mergedAcc = current.bankAccounts + newAcc

        // 6. Loans merge by ID
        val currentLoanIds = current.loans.map { it.id }.toSet()
        val newLoans = incoming.loans.filter { it.id !in currentLoanIds }
        val mergedLoans = current.loans + newLoans

        // 7. Assets merge by ID
        val currentBizIds = current.businessAssets.map { it.id }.toSet()
        val newBiz = incoming.businessAssets.filter { it.id !in currentBizIds }
        val mergedBiz = current.businessAssets + newBiz

        val currentPerIds = current.personalAssets.map { it.id }.toSet()
        val newPer = incoming.personalAssets.filter { it.id !in currentPerIds }
        val mergedPer = current.personalAssets + newPer

        // Profile: keep current shop profile, but fill blank owner/phone/address if present in incoming
        val currentProf = current.profile
        val incomingProf = incoming.profile
        val mergedProfile = currentProf.copy(
            ownerName = currentProf.ownerName.ifBlank { incomingProf.ownerName },
            phone = currentProf.phone.ifBlank { incomingProf.phone },
            address = currentProf.address.ifBlank { incomingProf.address },
            cnic = currentProf.cnic.ifBlank { incomingProf.cnic },
            ntn = currentProf.ntn.ifBlank { incomingProf.ntn }
        )

        val rawMerged = current.copy(
            profile = mergedProfile,
            transactions = mergedTx,
            inventory = mergedInventory,
            customers = mergedCust,
            suppliers = mergedSupp,
            bankAccounts = mergedAcc,
            loans = mergedLoans,
            businessAssets = mergedBiz,
            personalAssets = mergedPer
        )
        return com.saleemkhan.myshopmanager.data.accounting.AccountingEngine.rebuildBalances(rawMerged)
    }
}
