package com.saleemkhan.myshopmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.saleemkhan.myshopmanager.data.AppStateRepository
import com.saleemkhan.myshopmanager.data.accounting.AccountingEngine
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.components.*
import com.saleemkhan.myshopmanager.ui.screens.*
import com.saleemkhan.myshopmanager.ui.theme.MyShopManagerTheme
import com.saleemkhan.myshopmanager.utils.FormatUtils
import kotlinx.coroutines.launch

@Composable
fun MyShopManagerApp() {
    val context = LocalContext.current
    val repository = remember { AppStateRepository.getInstance(context) }
    val appState by repository.appState.collectAsState()
    val dismissedNotifications by repository.dismissedNotifications.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var screenHistory by remember { mutableStateOf(listOf(Screen.DASHBOARD)) }

    val isUrdu = appState.profile.activeLanguage == "ur"
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var quickEntryType by remember { mutableStateOf<TransactionType?>(null) }
    var isQuickEntryOpen by remember { mutableStateOf(false) }

    // Dialog & Overlay states
    var isLocked by remember { mutableStateOf(appState.profile.securityPin.isNotBlank() || !appState.profile.passwordCode.isNullOrBlank() || appState.profile.biometricsEnabled) }
    var isPermissionsSetupOpen by remember { mutableStateOf(!repository.isPermissionsSetupDone()) }
    var isVoiceEntryOpen by remember { mutableStateOf(false) }
    var isBarcodeScannerOpen by remember { mutableStateOf(false) }
    var pendingBarcodeForAdd by remember { mutableStateOf<String?>(null) }
    var isAddInventoryDialogOpen by remember { mutableStateOf(false) }
    var isCashAccountsOpen by remember { mutableStateOf(false) }
    var isGraphsModalOpen by remember { mutableStateOf(false) }
    var isCalendarOpen by remember { mutableStateOf(false) }
    var isAiOpen by remember { mutableStateOf(false) }
    var isNavPinned by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    fun navigateTo(screen: Screen) {
        if (screen != currentScreen) {
            screenHistory = screenHistory + screen
            currentScreen = screen
        }
    }

    fun handleBack() {
        if (screenHistory.size > 1) {
            screenHistory = screenHistory.dropLast(1)
            currentScreen = screenHistory.last()
        } else if (currentScreen != Screen.DASHBOARD) {
            currentScreen = Screen.DASHBOARD
            screenHistory = listOf(Screen.DASHBOARD)
        }
    }

    // System back button / back gesture
    BackHandler(
        enabled = !isLocked && (drawerState.isOpen || currentScreen != Screen.DASHBOARD || screenHistory.size > 1)
    ) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else {
            handleBack()
        }
    }

    MyShopManagerTheme(isUrdu = isUrdu) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // Reserved Mobile Status Bar Area across entire app so status bar icons never overlap content
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Color.White)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val hasLock = appState.profile.securityPin.isNotBlank() || !appState.profile.passwordCode.isNullOrBlank() || appState.profile.biometricsEnabled
                if (isLocked && hasLock) {
                    SecurityLockScreen(
                        expectedPin = appState.profile.securityPin.ifBlank { null },
                        expectedPassword = appState.profile.passwordCode,
                        isBiometricsEnabled = appState.profile.biometricsEnabled,
                        shopName = appState.profile.shopName,
                        isUrdu = isUrdu,
                        onUnlockSuccess = { isLocked = false }
                    )
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = !isNavPinned,
                        drawerContent = {
                            NavigationDrawerContent(
                                currentScreen = currentScreen,
                                onSelectScreen = { screen ->
                                    navigateTo(screen)
                                },
                                isUrdu = isUrdu,
                                onClose = {
                                    coroutineScope.launch { drawerState.close() }
                                },
                                isPinned = isNavPinned,
                                onTogglePin = {
                                    isNavPinned = !isNavPinned
                                }
                            )
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (isNavPinned) {
                                PinnedSideNavRail(
                                    currentScreen = currentScreen,
                                    onSelectScreen = { screen -> navigateTo(screen) },
                                    isPinned = isNavPinned,
                                    onTogglePin = { isNavPinned = !isNavPinned },
                                    isUrdu = isUrdu
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Scaffold(
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            topBar = {
                                if (currentScreen != Screen.DASHBOARD) {
                                    AppHeader(
                                        profile = appState.profile,
                                        otherShops = appState.otherShops,
                                        inventory = appState.inventory,
                                        customers = appState.customers,
                                        suppliers = appState.suppliers,
                                        loans = appState.loans,
                                        isUrdu = isUrdu,
                                        currentScreen = currentScreen,
                                        onBack = if (screenHistory.size > 1 || currentScreen != Screen.DASHBOARD) {
                                            { handleBack() }
                                        } else null,
                                        onToggleUrdu = {
                                            val newLang = if (isUrdu) "en" else "ur"
                                            repository.updateState { it.copy(profile = it.profile.copy(activeLanguage = newLang)) }
                                        },
                                        onSwitchShop = { shopId ->
                                            repository.updateState { it.copy(activeShopId = shopId) }
                                        },
                                        onLockApp = {
                                            val canLock = appState.profile.securityPin.isNotBlank() || !appState.profile.passwordCode.isNullOrBlank() || appState.profile.biometricsEnabled
                                            if (canLock) {
                                                isLocked = true
                                            }
                                        },
                                        onOpenScanner = { isBarcodeScannerOpen = true },
                                        onOpenVoiceEntry = { isVoiceEntryOpen = true },
                                        onOpenCalendar = { isCalendarOpen = true },
                                        onToggleStoreMode = {
                                            val nextMode = if (appState.profile.storeMode == StoreMode.SIMPLE) StoreMode.SPECIALIZED else StoreMode.SIMPLE
                                            repository.updateState { it.copy(profile = it.profile.copy(storeMode = nextMode)) }
                                        },
                                        onOpenQuickEntry = { type ->
                                            quickEntryType = type
                                            isQuickEntryOpen = true
                                        },
                                        onNavigateScreen = { screen ->
                                            navigateTo(screen)
                                        },
                                        onToggleMenu = {
                                            coroutineScope.launch {
                                                if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                            }
                                        },
                                        dismissedIds = dismissedNotifications,
                                        onDismissAllNotifs = { ids -> repository.dismissAllNotifications(ids) }
                                    )
                                }
                            }
                        ) { paddingValues ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            ) {
                                when (currentScreen) {
                                    Screen.DASHBOARD -> {
                                        DashboardScreen(
                                            state = appState,
                                            isUrdu = isUrdu,
                                            header = {
                                                AppHeader(
                                                    profile = appState.profile,
                                                    otherShops = appState.otherShops,
                                                    inventory = appState.inventory,
                                                    customers = appState.customers,
                                                    suppliers = appState.suppliers,
                                                    loans = appState.loans,
                                                    isUrdu = isUrdu,
                                                    currentScreen = currentScreen,
                                                    onBack = null,
                                                    onToggleUrdu = {
                                                        val newLang = if (isUrdu) "en" else "ur"
                                                        repository.updateState { it.copy(profile = it.profile.copy(activeLanguage = newLang)) }
                                                    },
                                                    onSwitchShop = { shopId ->
                                                        repository.updateState { it.copy(activeShopId = shopId) }
                                                    },
                                                    onLockApp = {
                                                        val canLock = appState.profile.securityPin.isNotBlank() || !appState.profile.passwordCode.isNullOrBlank() || appState.profile.biometricsEnabled
                                                        if (canLock) {
                                                            isLocked = true
                                                        }
                                                    },
                                                    onOpenScanner = { isBarcodeScannerOpen = true },
                                                    onOpenVoiceEntry = { isVoiceEntryOpen = true },
                                                    onOpenCalendar = { isCalendarOpen = true },
                                                    onToggleStoreMode = {
                                                        val nextMode = if (appState.profile.storeMode == StoreMode.SIMPLE) StoreMode.SPECIALIZED else StoreMode.SIMPLE
                                                        repository.updateState { it.copy(profile = it.profile.copy(storeMode = nextMode)) }
                                                    },
                                                    onOpenQuickEntry = { type ->
                                                        quickEntryType = type
                                                        isQuickEntryOpen = true
                                                    },
                                                    onNavigateScreen = { screen ->
                                                        navigateTo(screen)
                                                    },
                                                    onToggleMenu = {
                                                        coroutineScope.launch {
                                                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                                        }
                                                    },
                                                    dismissedIds = dismissedNotifications,
                                                    onDismissAllNotifs = { ids -> repository.dismissAllNotifications(ids) }
                                                )
                                            },
                                            onOpenQuickEntry = { type ->
                                                quickEntryType = type
                                                isQuickEntryOpen = true
                                            },
                                            onOpenVoiceEntry = { isVoiceEntryOpen = true },
                                            onNavigateScreen = { screen -> navigateTo(screen) },
                                            onOpenCalendar = { isCalendarOpen = true },
                                            onOpenGraphs = { isGraphsModalOpen = true },
                                            onOpenAi = { isAiOpen = true },
                                            onOpenCashAccounts = { isCashAccountsOpen = true }
                                        )
                                    }
                            Screen.TRANSACTIONS -> {
                                TransactionsScreen(
                                    state = appState,
                                    isUrdu = isUrdu,
                                    onOpenQuickEntry = { type ->
                                        editingTransaction = null
                                        quickEntryType = type
                                        isQuickEntryOpen = true
                                    },
                                    onEditTransaction = { tx ->
                                        editingTransaction = tx
                                        quickEntryType = tx.type
                                        isQuickEntryOpen = true
                                    },
                                    onDeleteTransaction = { id ->
                                        val targetTx = appState.transactions.find { it.id == id } ?: return@TransactionsScreen true
                                        // Stock protection check: if deleting a purchase, ensure stock won't drop below 0
                                        if (targetTx.type == TransactionType.PURCHASE && !targetTx.itemId.isNullOrBlank()) {
                                            val currentItem = appState.inventory.find { it.id == targetTx.itemId }
                                            if (currentItem != null) {
                                                val purchaseQty = targetTx.qtyChange ?: 0.0
                                                if (currentItem.quantity - purchaseQty < 0.0) {
                                                    return@TransactionsScreen false // Block deletion
                                                }
                                            }
                                        }

                                        repository.updateState { current ->
                                            AccountingEngine.deleteTransaction(current, id)
                                        }
                                        true
                                    },
                                    onRestoreTransaction = { id ->
                                        repository.updateState { current ->
                                            AccountingEngine.restoreTransaction(current, id)
                                        }
                                    },
                                    onUpdateTransaction = { id, partialData ->
                                        repository.updateState { current ->
                                            val updatedList = current.transactions.map { tx ->
                                                if (tx.id == id) {
                                                    tx.copy(
                                                        loanAccountId = partialData.loanAccountId ?: tx.loanAccountId,
                                                        partyName = partialData.partyName ?: tx.partyName,
                                                        notes = partialData.notes ?: tx.notes
                                                    )
                                                } else tx
                                            }
                                            current.copy(transactions = updatedList)
                                        }
                                    },
                                    onEmptyTrash = {
                                        repository.updateState { it.copy(deletedTransactions = emptyList()) }
                                    }
                                )
                            }
                            Screen.KHATA -> {
                                KhataScreen(
                                    state = appState,
                                    isUrdu = isUrdu,
                                    onAddCustomer = { cust ->
                                        repository.updateState { it.copy(customers = it.customers + cust) }
                                    },
                                    onAddSupplier = { sup ->
                                        repository.updateState { it.copy(suppliers = it.suppliers + sup) }
                                    },
                                    onRecordPayment = { txType, partyName, amt ->
                                        val matchingCust = appState.customers.find { it.name.equals(partyName, true) }
                                        val matchingSupp = appState.suppliers.find { it.name.equals(partyName, true) }
                                        val partyId = if (txType == TransactionType.RECEIPT) matchingCust?.id else matchingSupp?.id
                                        val newTx = Transaction(
                                            id = "tx_${System.currentTimeMillis()}",
                                            type = txType,
                                            amount = amt,
                                            category = if (txType == TransactionType.RECEIPT) "Customer Udhaar" else "Supplier Payment",
                                            paymentMethod = PaymentMethod.CASH,
                                            partyName = partyName,
                                            partyId = partyId,
                                            date = FormatUtils.getTodayIsoDate()
                                        )
                                        repository.updateState { current ->
                                            AccountingEngine.applyTransaction(current, newTx)
                                        }
                                    },
                                    onToggleArchiveCustomer = { cust ->
                                        val newStatus = if (cust.status == "ARCHIVED" || cust.status == "CLOSED") "ACTIVE" else "ARCHIVED"
                                        repository.updateState { current ->
                                            current.copy(customers = current.customers.map { c -> if (c.id == cust.id) c.copy(status = newStatus) else c })
                                        }
                                    },
                                    onToggleArchiveSupplier = { sup ->
                                        val newStatus = if (sup.status == "ARCHIVED" || sup.status == "CLOSED") "ACTIVE" else "ARCHIVED"
                                        repository.updateState { current ->
                                            current.copy(suppliers = current.suppliers.map { s -> if (s.id == sup.id) s.copy(status = newStatus) else s })
                                        }
                                    }
                                )
                            }
                            Screen.INVENTORY -> {
                                InventoryScreen(
                                    state = appState,
                                    isUrdu = isUrdu,
                                    onAddItem = { item ->
                                        repository.updateState { it.copy(inventory = it.inventory + item) }
                                    },
                                    onUpdateQty = { id, qty ->
                                        repository.updateState { it.copy(inventory = it.inventory.map { item -> if (item.id == id) item.copy(quantity = qty) else item }) }
                                    },
                                    onDeleteItem = { id ->
                                        repository.updateState { it.copy(inventory = it.inventory.filter { item -> item.id != id }) }
                                    },
                                    onOpenScanner = { isBarcodeScannerOpen = true },
                                    onSaleItem = { item, qty, unitPrice, discount, netAmount, customer, newCustomerName, newCustomerPhone, paymentMethod, accountId, notes ->
                                        repository.updateState { current ->
                                            val party = customer?.name ?: newCustomerName ?: (if (isUrdu) "کاؤنٹر نقد کسٹمر" else "Counter Cash Customer")
                                            var updatedCustomers = current.customers
                                            var targetPartyId = customer?.id

                                            if (newCustomerName != null && newCustomerName.isNotBlank()) {
                                                val newCust = CustomerParty(
                                                    id = "cust_${System.currentTimeMillis()}",
                                                    name = newCustomerName,
                                                    phone = newCustomerPhone ?: "",
                                                    totalCredit = 0.0,
                                                    openingBalance = 0.0,
                                                    lastTransactionDate = FormatUtils.getTodayIsoDate()
                                                )
                                                updatedCustomers = updatedCustomers + newCust
                                                targetPartyId = newCust.id
                                            }

                                            val newTx = Transaction(
                                                id = "tx_${System.currentTimeMillis()}",
                                                type = TransactionType.SALE,
                                                amount = netAmount,
                                                category = item.category,
                                                paymentMethod = paymentMethod,
                                                partyName = party,
                                                partyId = targetPartyId,
                                                date = FormatUtils.getTodayIsoDate(),
                                                notes = notes ?: "Sale ${item.name} x $qty",
                                                itemId = item.id,
                                                qtyChange = qty,
                                                unitCost = item.purchasePrice,
                                                totalValueChange = qty * item.purchasePrice,
                                                accountId = accountId
                                            )

                                            val baseState = current.copy(customers = updatedCustomers)
                                            AccountingEngine.applyTransaction(baseState, newTx)
                                        }
                                    }
                                )
                            }
                            Screen.ASSETS_LOANS -> {
                                AssetsAndLoansScreen(
                                    state = appState,
                                    isUrdu = isUrdu,
                                    onAddBusinessAsset = { ba ->
                                        repository.updateState { it.copy(businessAssets = it.businessAssets + ba) }
                                    },
                                    onAddPersonalAsset = { pa ->
                                        repository.updateState { it.copy(personalAssets = it.personalAssets + pa) }
                                    },
                                    onAddLoan = { loan ->
                                        repository.updateState { it.copy(loans = it.loans + loan) }
                                    },
                                    onDeleteBusinessAsset = { id ->
                                        repository.updateState { it.copy(businessAssets = it.businessAssets.filter { it.id != id }) }
                                    },
                                    onDeletePersonalAsset = { id ->
                                        repository.updateState { it.copy(personalAssets = it.personalAssets.filter { it.id != id }) }
                                    },
                                    onDeleteLoan = { id ->
                                        repository.updateState { it.copy(loans = it.loans.filter { it.id != id }) }
                                    },
                                    onUpdateHouseholdExpenses = { exp ->
                                        repository.updateState { it.copy(householdExpenses = exp) }
                                    },
                                    onUpdateOtherIncome = { inc ->
                                        repository.updateState { it.copy(otherIncome = inc) }
                                    },
                                    onAddTransaction = { newTx ->
                                        repository.updateState { current ->
                                            AccountingEngine.applyTransaction(current, newTx)
                                        }
                                    }
                                )
                            }
                            Screen.FBR_TAX -> {
                                FbrTaxScreen(
                                    state = appState,
                                    isUrdu = isUrdu,
                                    onUpdateTaxRecord = { newTax ->
                                        repository.updateState { it.copy(taxRecord = newTax) }
                                    },
                                    onUpdateHouseholdExpenses = { newExp ->
                                        repository.updateState { it.copy(householdExpenses = newExp) }
                                    },
                                    onUpdateOtherIncome = { newInc ->
                                        repository.updateState { it.copy(otherIncome = newInc) }
                                    }
                                )
                            }
                            Screen.REPORTS -> {
                                ReportsScreen(state = appState, isUrdu = isUrdu, onBack = { handleBack() })
                            }
                            Screen.ANALYTICS -> {
                                AnalyticsScreen(state = appState, isUrdu = isUrdu)
                            }
                            Screen.NOTIFICATIONS -> {
                                NotificationsScreen(
                                    state = appState,
                                    isUrdu = isUrdu,
                                    dismissedIds = dismissedNotifications,
                                    onDismissNotification = { id -> repository.dismissNotification(id) },
                                    onClearAllNotifications = { ids -> repository.dismissAllNotifications(ids) },
                                    onRestoreHistory = { repository.clearDismissedNotifications() },
                                    onBack = { handleBack() },
                                    onNavigateScreen = { screen -> navigateTo(screen) }
                                )
                            }
                            Screen.BACKUP_SYNC -> {
                                BackupSyncScreen(
                                    state = appState,
                                    isUrdu = isUrdu,
                                    onBack = { handleBack() },
                                    onRestoreState = { restoredState ->
                                        repository.saveState(restoredState)
                                    }
                                )
                            }
                            Screen.SETTINGS -> {
                                SettingsScreen(
                                    state = appState,
                                    isUrdu = isUrdu,
                                    onUpdateProfile = { newProfile ->
                                        repository.updateState { it.copy(profile = newProfile) }
                                    },
                                    onResetData = {
                                        repository.resetState()
                                    },
                                    onNavigateToBackup = {
                                        navigateTo(Screen.BACKUP_SYNC)
                                    },
                                    onLockApp = {
                                        isLocked = true
                                    },
                                    onBack = { handleBack() }
                                )
                            }
                        }

                        // Quick Entry Dialog
                        if (isQuickEntryOpen && quickEntryType != null) {
                            QuickEntryDialog(
                                initialType = quickEntryType!!,
                                isOpen = isQuickEntryOpen,
                                onClose = {
                                    isQuickEntryOpen = false
                                    editingTransaction = null
                                },
                                editingTransaction = editingTransaction,
                                onAddNewInventoryItem = { newItem ->
                                    repository.updateState { it.copy(inventory = it.inventory + newItem) }
                                },
                                onSubmit = { newTx, itemId, qtyChange, custId, custBalChange, suppId, suppBalChange, targetAccId ->
                                    repository.updateState { current ->
                                        val finalTx = newTx.copy(
                                            itemId = newTx.itemId ?: itemId,
                                            qtyChange = newTx.qtyChange ?: (if (qtyChange != 0.0) kotlin.math.abs(qtyChange) else null),
                                            partyId = newTx.partyId ?: custId ?: suppId,
                                            accountId = newTx.accountId ?: targetAccId
                                        )

                                        if (editingTransaction != null) {
                                            AccountingEngine.editTransaction(current, editingTransaction!!, finalTx)
                                        } else {
                                            AccountingEngine.applyTransaction(current, finalTx)
                                        }
                                    }
                                    editingTransaction = null
                                },
                                onPosSaleSubmit = { newTx, cartDeductions, custId, custBalChange, targetAccId ->
                                    repository.updateState { current ->
                                        val finalTx = newTx.copy(
                                            partyId = newTx.partyId ?: custId,
                                            accountId = newTx.accountId ?: targetAccId
                                        )

                                        if (editingTransaction != null) {
                                            val stateAfterReverse = AccountingEngine.reverseTransaction(current, editingTransaction!!)
                                            val updatedInv = stateAfterReverse.inventory.map { item ->
                                                val deduction = cartDeductions.find { it.first == item.id }?.second ?: 0.0
                                                if (deduction > 0.0) item.copy(
                                                    quantity = AccountingEngine.roundQuantity(item.quantity - deduction),
                                                    soldQty = AccountingEngine.roundQuantity(item.soldQty + deduction)
                                                ) else item
                                            }
                                            val baseState = stateAfterReverse.copy(inventory = updatedInv)
                                            val stateWithPosting = AccountingEngine.applyTransaction(baseState, finalTx.copy(itemId = null, qtyChange = null))
                                            val updatedList = stateWithPosting.transactions.map { if (it.id == editingTransaction!!.id) finalTx else it }
                                            stateWithPosting.copy(transactions = updatedList)
                                        } else {
                                            val updatedInv = current.inventory.map { item ->
                                                val deduction = cartDeductions.find { it.first == item.id }?.second ?: 0.0
                                                if (deduction > 0.0) item.copy(
                                                    quantity = AccountingEngine.roundQuantity(item.quantity - deduction),
                                                    soldQty = AccountingEngine.roundQuantity(item.soldQty + deduction)
                                                ) else item
                                            }
                                            val baseState = current.copy(inventory = updatedInv)
                                            val stateWithPosting = AccountingEngine.applyTransaction(baseState, finalTx.copy(itemId = null, qtyChange = null))
                                            val updatedList = listOf(finalTx) + stateWithPosting.transactions.filter { it.id != finalTx.id }
                                            stateWithPosting.copy(transactions = updatedList)
                                        }
                                    }
                                    editingTransaction = null
                                },
                                isUrdu = isUrdu,
                                state = appState
                            )
                        }

                        // Voice Entry Dialog
                        if (isVoiceEntryOpen) {
                            VoiceEntryDialog(
                                isOpen = isVoiceEntryOpen,
                                onClose = { isVoiceEntryOpen = false },
                                onSubmit = { newTx ->
                                    repository.updateState { current ->
                                        AccountingEngine.applyTransaction(current, newTx)
                                    }
                                },
                                isUrdu = isUrdu
                            )
                        }

                        // Barcode Scanner Dialog
                        if (isBarcodeScannerOpen) {
                            BarcodeScannerDialog(
                                isOpen = isBarcodeScannerOpen,
                                onClose = { isBarcodeScannerOpen = false },
                                inventory = appState.inventory,
                                onItemScanned = { /* Displayed in scanner */ },
                                onQuickSale = { item ->
                                    val newTx = Transaction(
                                        id = "tx_${System.currentTimeMillis()}",
                                        type = TransactionType.SALE,
                                        amount = item.salePrice,
                                        category = item.category,
                                        paymentMethod = PaymentMethod.CASH,
                                        partyName = if (isUrdu) "کاؤنٹر نقد فروخت" else "Counter Cash Sale",
                                        date = FormatUtils.getTodayIsoDate(),
                                        notes = "Barcode Sale: ${item.name}",
                                        itemId = item.id,
                                        qtyChange = 1.0,
                                        unitCost = item.purchasePrice,
                                        totalValueChange = item.purchasePrice
                                    )
                                    repository.updateState { current ->
                                        AccountingEngine.applyTransaction(current, newTx)
                                    }
                                },
                                onQuickPurchase = { item ->
                                    val newTx = Transaction(
                                        id = "tx_${System.currentTimeMillis()}",
                                        type = TransactionType.PURCHASE,
                                        amount = item.purchasePrice,
                                        category = item.category,
                                        paymentMethod = PaymentMethod.CASH,
                                        partyName = if (isUrdu) "اسٹاک خریداری" else "Inventory Restock",
                                        date = FormatUtils.getTodayIsoDate(),
                                        notes = "Barcode Purchase: ${item.name}",
                                        itemId = item.id,
                                        qtyChange = 1.0,
                                        unitCost = item.purchasePrice,
                                        totalValueChange = item.purchasePrice
                                    )
                                    repository.updateState { current ->
                                        AccountingEngine.applyTransaction(current, newTx)
                                    }
                                },
                                onAddNewItemWithBarcode = { code ->
                                    pendingBarcodeForAdd = code
                                    isBarcodeScannerOpen = false
                                    isAddInventoryDialogOpen = true
                                },
                                onManualCodeEntered = { /* Handled */ },
                                isUrdu = isUrdu
                            )
                        }

                        // Add Inventory Item Dialog (Opened either from Scanner or elsewhere)
                        if (isAddInventoryDialogOpen) {
                            AddInventoryItemDialog(
                                isOpen = isAddInventoryDialogOpen,
                                onClose = {
                                    isAddInventoryDialogOpen = false
                                    pendingBarcodeForAdd = null
                                },
                                onAddItem = { newItem ->
                                    repository.updateState { it.copy(inventory = it.inventory + newItem) }
                                    isAddInventoryDialogOpen = false
                                    pendingBarcodeForAdd = null
                                },
                                isUrdu = isUrdu,
                                initialBarcode = pendingBarcodeForAdd ?: "",
                                inventory = appState.inventory
                            )
                        }

                        // Cash & Bank Accounts Dialog
                        if (isCashAccountsOpen) {
                            CashAccountsDialog(
                                isOpen = isCashAccountsOpen,
                                onClose = { isCashAccountsOpen = false },
                                accounts = appState.bankAccounts,
                                transactions = appState.transactions,
                                onAddAccount = { acc ->
                                    repository.updateState { it.copy(bankAccounts = it.bankAccounts + acc) }
                                },
                                onAddTransaction = { newTx, targetAccId, amountDelta ->
                                    repository.updateState { current ->
                                        val finalTx = newTx.copy(accountId = targetAccId ?: newTx.accountId)
                                        AccountingEngine.applyTransaction(current, finalTx)
                                    }
                                },
                                onTransferFunds = { fromId, toId, amt, notes ->
                                    repository.updateState { current ->
                                        val fromAcc = current.bankAccounts.find { it.id == fromId }
                                        val toAcc = current.bankAccounts.find { it.id == toId }

                                        val method = when (fromAcc?.type) {
                                            "BANK" -> PaymentMethod.BANK
                                            "WALLET" -> {
                                                when (fromAcc.bankName.uppercase()) {
                                                    "EASYPAISA" -> PaymentMethod.EASYPAISA
                                                    "JAZZCASH" -> PaymentMethod.JAZZCASH
                                                    "NAYAPAY" -> PaymentMethod.NAYAPAY
                                                    "SADAPAY" -> PaymentMethod.SADAPAY
                                                    else -> PaymentMethod.BANK
                                                }
                                            }
                                            else -> PaymentMethod.CASH
                                        }

                                        val transferTx = Transaction(
                                            id = "tx_${System.currentTimeMillis()}",
                                            type = TransactionType.TRANSFER,
                                            amount = amt,
                                            category = "Fund Transfer",
                                            paymentMethod = method,
                                            partyName = "${fromAcc?.accountTitle ?: fromId} → ${toAcc?.accountTitle ?: toId}",
                                            date = FormatUtils.getTodayIsoDate(),
                                            notes = notes.ifBlank { if (isUrdu) "رقم منتقلی (اندرونی فنڈ ٹرانسفر)" else "Internal Fund Transfer" },
                                            accountId = fromId,
                                            toAccountId = toId
                                        )

                                        AccountingEngine.applyTransaction(current, transferTx)
                                    }
                                },
                                onAdjustBalance = { accId, newBal, diff, reason ->
                                    repository.updateState { current ->
                                        val targetAcc = current.bankAccounts.find { it.id == accId }
                                        val updated = current.bankAccounts.map { acc ->
                                            if (acc.id == accId) acc.copy(balance = newBal) else acc
                                        }

                                        val method = when (targetAcc?.type) {
                                            "BANK" -> PaymentMethod.BANK
                                            "WALLET" -> {
                                                when (targetAcc.bankName.uppercase()) {
                                                    "EASYPAISA" -> PaymentMethod.EASYPAISA
                                                    "JAZZCASH" -> PaymentMethod.JAZZCASH
                                                    "NAYAPAY" -> PaymentMethod.NAYAPAY
                                                    "SADAPAY" -> PaymentMethod.SADAPAY
                                                    else -> PaymentMethod.BANK
                                                }
                                            }
                                            else -> PaymentMethod.CASH
                                        }

                                        val adjTx = Transaction(
                                            id = "tx_${System.currentTimeMillis()}",
                                            type = if (diff > 0) TransactionType.SALE else TransactionType.EXPENSE,
                                            amount = kotlin.math.abs(diff),
                                            category = "Balance Reconciliation",
                                            paymentMethod = method,
                                            partyName = targetAcc?.accountTitle ?: "Account Reconciliation",
                                            date = FormatUtils.getTodayIsoDate(),
                                            accountId = targetAcc?.id,
                                            notes = reason.ifBlank {
                                                if (isUrdu) "اکاؤنٹ بیلنس ایڈجسٹمنٹ (فرق: ${FormatUtils.formatCurrency(diff)})"
                                                else "Reconciliation adjustment (Diff: ${FormatUtils.formatCurrency(diff)})"
                                            }
                                        )

                                        current.copy(
                                            bankAccounts = updated,
                                            transactions = listOf(adjTx) + current.transactions
                                        )
                                    }
                                },
                                isUrdu = isUrdu
                            )
                        }

                        // Graphs & Analytics Modal
                        if (isGraphsModalOpen) {
                            GraphsModal(
                                isOpen = isGraphsModalOpen,
                                onClose = { isGraphsModalOpen = false },
                                state = appState,
                                isUrdu = isUrdu
                            )
                        }

                        // Calendar & Reminders Dialog
                        if (isCalendarOpen) {
                            CalendarRemindersDialog(
                                isOpen = isCalendarOpen,
                                onClose = { isCalendarOpen = false },
                                suppliers = appState.suppliers,
                                loans = appState.loans,
                                isUrdu = isUrdu
                            )
                        }

                        // Permissions Setup Dialog (First launch or ungranted setup)
                        if (isPermissionsSetupOpen) {
                            PermissionsSetupDialog(
                                isOpen = isPermissionsSetupOpen,
                                onClose = { isPermissionsSetupOpen = false },
                                onFinish = {
                                    repository.setPermissionsSetupDone(true)
                                    isPermissionsSetupOpen = false
                                },
                                isUrdu = isUrdu
                            )
                        }
                    }
                }
            }
        }
    }
}

            // Fullscreen AI Assistant Overlay (Guaranteed stable header & zero-gap keyboard layout)
            if (isAiOpen) {
                AiAssistantDialog(
                    isOpen = isAiOpen,
                    onClose = { isAiOpen = false },
                    onOpenVoiceEntry = {
                        isAiOpen = false
                        isVoiceEntryOpen = true
                    },
                    state = appState,
                    isUrdu = isUrdu
                )
            }
        }
    }
}
}

