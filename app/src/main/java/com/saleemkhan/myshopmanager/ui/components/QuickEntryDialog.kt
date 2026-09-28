package com.saleemkhan.myshopmanager.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils

data class PosCartItem(
    val item: InventoryItem,
    val quantity: Double,
    val unitPrice: Double
) {
    val lineTotal: Double get() = quantity * unitPrice
}

data class QuickEntryTabItem(
    val id: String,
    val type: TransactionType?,
    val labelUrdu: String,
    val labelEng: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryDialog(
    initialType: TransactionType,
    isOpen: Boolean,
    onClose: () -> Unit,
    onSubmit: (
        transaction: Transaction,
        itemId: String?,
        quantityChange: Double,
        customerId: String?,
        customerBalanceChange: Double,
        supplierId: String?,
        supplierBalanceChange: Double,
        targetAccountId: String?
    ) -> Unit,
    isUrdu: Boolean,
    state: AppState,
    editingTransaction: Transaction? = null,
    onPosSaleSubmit: ((
        transaction: Transaction,
        cartDeductions: List<Pair<String, Double>>,
        customerId: String?,
        customerBalanceChange: Double,
        targetAccountId: String?
    ) -> Unit)? = null,
    onAddNewInventoryItem: ((InventoryItem) -> Unit)? = null
) {
    if (!isOpen) return

    val context = LocalContext.current
    var activeTabId by remember(editingTransaction, initialType) {
        mutableStateOf(editingTransaction?.type?.name ?: initialType.name)
    }
    var activeType by remember(editingTransaction, initialType) {
        mutableStateOf(editingTransaction?.type ?: initialType)
    }

    var showQuickAddItemDialog by remember { mutableStateOf(false) }

    // SALE states (Supports Mode 1: Inventory POS & Mode 2: Quick Direct Sale)
    var saleMode by remember { mutableStateOf("POS") } // "POS" or "DIRECT"
    var cartItems by remember { mutableStateOf<List<PosCartItem>>(emptyList()) }
    var directSaleAmount by remember(editingTransaction) {
        mutableStateOf(if (editingTransaction != null && editingTransaction.type == TransactionType.SALE) editingTransaction.amount.toInt().toString() else "")
    }
    var selectedCustomerId by remember(editingTransaction) {
        mutableStateOf(state.customers.find { it.name.equals(editingTransaction?.partyName, ignoreCase = true) }?.id)
    }
    var customCustomerName by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.partyName ?: "")
    }
    var customerType by remember(selectedCustomerId, customCustomerName) {
        mutableStateOf(if (selectedCustomerId != null) "KHATA" else "WALKIN")
    }
    var saleDiscount by remember { mutableStateOf("0") }
    var salePaymentMethod by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.paymentMethod ?: PaymentMethod.CASH)
    }
    var saleAccountId by remember { mutableStateOf(state.bankAccounts.firstOrNull { it.type == "CASH" }?.id ?: state.bankAccounts.firstOrNull()?.id ?: "") }
    var saleNotes by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.notes ?: "")
    }
    var saleDate by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.date?.ifBlank { FormatUtils.getTodayIsoDate() } ?: FormatUtils.getTodayIsoDate())
    }

    val posSubtotal = if (saleMode == "POS") cartItems.sumOf { it.lineTotal } else (directSaleAmount.toDoubleOrNull() ?: 0.0)
    val parsedSaleDiscount = saleDiscount.toDoubleOrNull() ?: 0.0
    val netSaleTotal = (posSubtotal - parsedSaleDiscount).coerceAtLeast(0.0)

    // PURCHASE states
    var selectedSupplierId by remember(editingTransaction) {
        mutableStateOf(state.suppliers.find { it.name.equals(editingTransaction?.partyName, ignoreCase = true) }?.id)
    }
    var customSupplierName by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.partyName ?: "")
    }
    var purchaseInvoiceNo by remember { mutableStateOf("") }
    var purchaseDate by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.date?.ifBlank { FormatUtils.getTodayIsoDate() } ?: FormatUtils.getTodayIsoDate())
    }
    var purchaseItemId by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.itemId)
    }
    var customPurchaseItemName by remember { mutableStateOf("") }
    var purchaseQty by remember { mutableStateOf("1") }
    var purchaseUnit by remember { mutableStateOf("Piece") }
    var purchaseCostPrice by remember(editingTransaction) {
        mutableStateOf(if (editingTransaction != null && editingTransaction.type == TransactionType.PURCHASE) editingTransaction.amount.toInt().toString() else "")
    }
    var purchaseItemCode by remember { mutableStateOf("") }
    var purchaseBarcode by remember { mutableStateOf("") }
    var purchaseLandingCost by remember { mutableStateOf("") }
    var purchasePaymentTerm by remember(editingTransaction) {
        mutableStateOf(
            when (editingTransaction?.paymentMethod) {
                PaymentMethod.CREDIT -> "FULL_CREDIT"
                PaymentMethod.BANK -> "PAID_BANK"
                else -> "PAID_CASH"
            }
        )
    }
    var purchasePaidAmount by remember { mutableStateOf("") }
    var purchasePaymentMethod by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.paymentMethod ?: PaymentMethod.CASH)
    }
    var purchaseAccountId by remember { mutableStateOf(state.bankAccounts.firstOrNull { it.type == "CASH" }?.id ?: state.bankAccounts.firstOrNull()?.id ?: "") }
    var purchaseNotes by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.notes ?: "")
    }

    // EXPENSE states
    var expenseCategory by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.category ?: "ELECTRICITY")
    }
    var expenseAmount by remember(editingTransaction) {
        mutableStateOf(if (editingTransaction != null && editingTransaction.type == TransactionType.EXPENSE) editingTransaction.amount.toInt().toString() else "")
    }
    var expensePaidTo by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.partyName ?: "")
    }
    var expenseAccountId by remember { mutableStateOf(state.bankAccounts.firstOrNull { it.type == "CASH" }?.id ?: state.bankAccounts.firstOrNull()?.id ?: "") }
    var expenseDate by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.date?.ifBlank { FormatUtils.getTodayIsoDate() } ?: FormatUtils.getTodayIsoDate())
    }
    var expenseNotes by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.notes ?: "")
    }

    // RECEIPT (Collect Udhaar) states
    var receiptCustomerId by remember(editingTransaction) {
        mutableStateOf(state.customers.find { it.name.equals(editingTransaction?.partyName, ignoreCase = true) }?.id)
    }
    var receiptAmount by remember(editingTransaction) {
        mutableStateOf(if (editingTransaction != null && editingTransaction.type == TransactionType.RECEIPT) editingTransaction.amount.toInt().toString() else "")
    }
    var receiptAccountId by remember { mutableStateOf(state.bankAccounts.firstOrNull { it.type == "CASH" }?.id ?: state.bankAccounts.firstOrNull()?.id ?: "") }
    var receiptDate by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.date?.ifBlank { FormatUtils.getTodayIsoDate() } ?: FormatUtils.getTodayIsoDate())
    }
    var receiptNotes by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.notes ?: "")
    }

    // PAYMENT (Pay Supplier) states
    var paymentSupplierId by remember(editingTransaction) {
        mutableStateOf(state.suppliers.find { it.name.equals(editingTransaction?.partyName, ignoreCase = true) }?.id)
    }
    var paymentAmount by remember(editingTransaction) {
        mutableStateOf(if (editingTransaction != null && editingTransaction.type == TransactionType.PAYMENT) editingTransaction.amount.toInt().toString() else "")
    }
    var paymentAccountId by remember { mutableStateOf(state.bankAccounts.firstOrNull { it.type == "CASH" }?.id ?: state.bankAccounts.firstOrNull()?.id ?: "") }
    var paymentDate by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.date?.ifBlank { FormatUtils.getTodayIsoDate() } ?: FormatUtils.getTodayIsoDate())
    }
    var paymentNotes by remember(editingTransaction) {
        mutableStateOf(editingTransaction?.notes ?: "")
    }

    // TRANSFER states (Tab 6: Bank & Cash Transfer)
    var transferFromAccountId by remember { mutableStateOf(state.bankAccounts.firstOrNull { it.type == "CASH" }?.id ?: state.bankAccounts.firstOrNull()?.id ?: "") }
    var transferToAccountId by remember { mutableStateOf(state.bankAccounts.firstOrNull { it.id != transferFromAccountId }?.id ?: "") }
    var transferAmount by remember { mutableStateOf("") }
    var transferDate by remember { mutableStateOf(FormatUtils.getTodayIsoDate()) }
    var transferNotes by remember { mutableStateOf("") }

    // LOAN & ASSET states (Tab 7: Loans & Assets Desk)
    var loanPartyName by remember { mutableStateOf("") }
    var loanAmount by remember { mutableStateOf("") }
    var loanDirection by remember { mutableStateOf("GIVEN") } // "GIVEN" or "TAKEN"
    var loanAccountId by remember { mutableStateOf(state.bankAccounts.firstOrNull { it.type == "CASH" }?.id ?: state.bankAccounts.firstOrNull()?.id ?: "") }
    var loanDate by remember { mutableStateOf(FormatUtils.getTodayIsoDate()) }
    var loanNotes by remember { mutableStateOf("") }

    // Barcode scanner trigger
    var isInnerBarcodeScannerOpen by remember { mutableStateOf(false) }

    val submitReceiptAction: () -> Unit = {
        val amt = receiptAmount.toDoubleOrNull() ?: 0.0
        if (amt <= 0) {
            Toast.makeText(context, if (isUrdu) "براہ کرم درست وصول شدہ رقم درج کریں" else "Please enter valid received amount", Toast.LENGTH_SHORT).show()
        } else {
            val customer = state.customers.find { it.id == receiptCustomerId }
            val party = customer?.name ?: (if (isUrdu) "گاہک وصولی" else "Customer Recovery")
            val desc = "Udhaar Recovery from $party"
            val newTx = Transaction(
                id = "tx_${System.currentTimeMillis()}",
                type = TransactionType.RECEIPT,
                amount = amt,
                category = "Udhaar Recovery",
                paymentMethod = PaymentMethod.CASH,
                partyName = party,
                date = receiptDate.ifBlank { FormatUtils.getTodayIsoDate() },
                notes = if (receiptNotes.isNotBlank()) "$desc - $receiptNotes" else desc
            )

            onSubmit(
                newTx,
                null,
                0.0,
                customer?.id,
                -amt,
                null,
                0.0,
                receiptAccountId
            )
            onClose()
        }
    }

    fun shareWhatsAppReceipt(
        title: String,
        partyName: String,
        amount: Double,
        details: String,
        remainingBalance: Double? = null
    ) {
        val shopName = if (isUrdu && !state.profile.shopNameUrdu.isNullOrBlank()) state.profile.shopNameUrdu else state.profile.shopName
        val sb = StringBuilder()
        sb.appendLine("🧾 *${shopName}*")
        sb.appendLine("==========================")
        sb.appendLine("${if (isUrdu) "قسم" else "Type"}: $title")
        sb.appendLine("${if (isUrdu) "تاریخ" else "Date"}: ${FormatUtils.getTodayIsoDate()}")
        sb.appendLine("${if (isUrdu) "گاہک / فریق" else "Party"}: $partyName")
        sb.appendLine("--------------------------")
        sb.appendLine(details)
        sb.appendLine("--------------------------")
        sb.appendLine("💰 *${if (isUrdu) "کل رقم" else "Total Amount"}: ${FormatUtils.formatCurrency(amount)}*")
        if (remainingBalance != null) {
            sb.appendLine("${if (isUrdu) "باقایہ حساب" else "Current Balance"}: ${FormatUtils.formatCurrency(remainingBalance)}")
        }
        sb.appendLine("==========================")
        sb.appendLine(if (isUrdu) "شکریہ! دوبارہ تشریف لائیں۔" else "Thank you for your business!")
        sb.appendLine("📱 *MyShop Manager*")

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        try {
            val shareIntent = Intent.createChooser(sendIntent, if (isUrdu) "رسید شیئر کریں" else "Share Receipt")
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open share menu", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. TOP APP BAR (Header)
                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val headerColor = if (activeTabId == "ANALYTICS") {
                                    Color(0xFF8B5CF6)
                                } else {
                                    when (activeType) {
                                        TransactionType.SALE -> DukanGreenPrimary
                                        TransactionType.PURCHASE -> Color(0xFF2563EB)
                                        TransactionType.EXPENSE -> Color(0xFFDC2626)
                                        TransactionType.RECEIPT -> Color(0xFFD97706)
                                        TransactionType.PAYMENT -> Color(0xFF7C3AED)
                                        TransactionType.TRANSFER -> Color(0xFF0284C7)
                                        TransactionType.LOAN_GIVEN, TransactionType.LOAN_TAKEN -> Color(0xFF0D9488)
                                        else -> DukanGreenPrimary
                                    }
                                }

                                val headerIcon = if (activeTabId == "ANALYTICS") {
                                    Icons.Default.BarChart
                                } else {
                                    when (activeType) {
                                        TransactionType.SALE -> Icons.Default.ShoppingCart
                                        TransactionType.PURCHASE -> Icons.Default.Inventory2
                                        TransactionType.RECEIPT -> Icons.Default.Person
                                        TransactionType.PAYMENT -> Icons.Default.LocalShipping
                                        TransactionType.EXPENSE -> Icons.Default.ReceiptLong
                                        TransactionType.TRANSFER -> Icons.Default.AccountBalance
                                        TransactionType.LOAN_GIVEN, TransactionType.LOAN_TAKEN -> Icons.Default.AccountBalanceWallet
                                        else -> Icons.Default.EditNote
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(headerColor.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = headerIcon,
                                        contentDescription = "Quick Entry",
                                        tint = headerColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = if (activeTabId == "ANALYTICS") {
                                            if (isUrdu) "کاروباری تجزیہ اور منافع" else "Business Analytics & Insights"
                                        } else {
                                            when (activeType) {
                                                TransactionType.SALE -> if (isUrdu) "سیلز کاؤنٹر اور POS" else "Sales Counter & POS"
                                                TransactionType.PURCHASE -> if (isUrdu) "خریداری کاؤنٹر" else "Purchase & Inward"
                                                TransactionType.RECEIPT -> if (isUrdu) "کسٹمر کھاتہ (وصولی)" else "Customer Udhaar Recovery"
                                                TransactionType.PAYMENT -> if (isUrdu) "سپلائر کھاتہ (ادائیگی)" else "Supplier Khata Payment"
                                                TransactionType.EXPENSE -> if (isUrdu) "روزمرہ دکان اخراجات" else "Daily Shop Expenses"
                                                TransactionType.TRANSFER -> if (isUrdu) "بینک و کیش ٹرانسفر" else "Bank & Cash Transfer"
                                                TransactionType.LOAN_GIVEN, TransactionType.LOAN_TAKEN -> if (isUrdu) "قرضہ و دکان کے اثاثہ جات" else "Loans & Assets Desk"
                                                else -> if (isUrdu) "فوری اندراج (Quick Entry)" else "Quick Entry Desk"
                                            }
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = if (activeTabId == "ANALYTICS") {
                                            if (isUrdu) "سیلز، اخراجات، منافع اور مالیاتی تجزیہ" else "Real-time Sales, Expenses, Profit & Financial Overview"
                                        } else {
                                            when (activeType) {
                                                TransactionType.SALE -> if (isUrdu) "نقد و ادھار فروخت، بارکوڈ اور لائیو کیٹلاگ" else "Live Product POS, Barcode & Fast Direct Sales"
                                                TransactionType.PURCHASE -> if (isUrdu) "سپلائر سے خریداری، بل، بلٹی اور ٹرانسپورٹ لاگت کا اندراج" else "Record Purchases & Inwards from Supplier with Bill, Transport, Landing and other Costs"
                                                TransactionType.EXPENSE -> if (isUrdu) "دکان کے اخراجات، یوٹیلٹی بلز، ملازمین کی تنخواہیں اور متفرق اخراجات" else "Categorize your Shop Expenses, Utility Bills, Staff Wages and Other Expenses"
                                                TransactionType.RECEIPT -> if (isUrdu) "گاہکوں کے بقایا جات دیکھیں اور فوری وصولی درج کریں" else "View Customer Outstanding Debts and Record 1-Click Collection"
                                                TransactionType.PAYMENT -> if (isUrdu) "سپلائرز کو ادھار بل کی ادائیگی کریں" else "Record Bill Payment to Suppliers"
                                                TransactionType.TRANSFER -> if (isUrdu) "بینک اور کیش دراز کے درمیان رقم منتقلی" else "Transfer between Bank, Cash Drawer & Wallets"
                                                TransactionType.LOAN_GIVEN, TransactionType.LOAN_TAKEN -> if (isUrdu) "قرضہ لین دین اور دکان کے اثاثے" else "Shop Assets & Loan Management"
                                                else -> ""
                                            }
                                        },
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }
                            }

                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Slate100, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Slate700,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 2. HORIZONTAL SEGMENTED 8 TABS STRIP IN SEQUENCE:
                        // Sales -> Purchase -> Expenses -> Collect Udhaar -> Pay Supplier -> Analytics -> Cash/Bank -> Loans/Assets
                        val tabs = listOf(
                            QuickEntryTabItem("SALE", TransactionType.SALE, "فروخت (POS)", "Sales", DukanGreenPrimary, Icons.Default.ShoppingCart),
                            QuickEntryTabItem("PURCHASE", TransactionType.PURCHASE, "خریداری", "Purchase", Color(0xFF2563EB), Icons.Default.Inventory2),
                            QuickEntryTabItem("EXPENSE", TransactionType.EXPENSE, "اخراجات", "Expenses", Color(0xFFDC2626), Icons.Default.ReceiptLong),
                            QuickEntryTabItem("RECEIPT", TransactionType.RECEIPT, "ادھار وصولی", "Collect Udhaar", Color(0xFFD97706), Icons.Default.Person),
                            QuickEntryTabItem("PAYMENT", TransactionType.PAYMENT, "سپلائر ادائیگی", "Pay Supplier", Color(0xFF7C3AED), Icons.Default.LocalShipping),
                            QuickEntryTabItem("ANALYTICS", null, "تجزیہ و رپورٹس", "Analytics", Color(0xFF8B5CF6), Icons.Default.BarChart),
                            QuickEntryTabItem("TRANSFER", TransactionType.TRANSFER, "بینک و کیش", "Cash/Bank", Color(0xFF0284C7), Icons.Default.AccountBalance),
                            QuickEntryTabItem("LOAN_GIVEN", TransactionType.LOAN_GIVEN, "قرضہ و اثاثہ", "Loans/Assets", Color(0xFF0D9488), Icons.Default.AccountBalanceWallet)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tabs.forEach { tab ->
                                val isSelected = activeTabId == tab.id
                                Surface(
                                    onClick = {
                                        activeTabId = tab.id
                                        if (tab.type != null) {
                                            activeType = tab.type
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) tab.color else Slate100,
                                    border = BorderStroke(1.dp, if (isSelected) tab.color else Slate200),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.labelEng,
                                            tint = if (isSelected) Color.White else tab.color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isUrdu) tab.labelUrdu else tab.labelEng,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Slate700
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. TAB SPECIFIC FULLSCREEN FORM BODY
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (activeTabId == "ANALYTICS") {
                            AnalyticsQuickView(state = state, isUrdu = isUrdu)
                        } else {
                            when (activeType) {
                                TransactionType.SALE -> {
                                    SaleForm(
                                    state = state,
                                    isUrdu = isUrdu,
                                    saleMode = saleMode,
                                    onSaleModeChange = { saleMode = it },
                                    cartItems = cartItems,
                                    onCartItemsChange = { cartItems = it },
                                    directSaleAmount = directSaleAmount,
                                    onDirectSaleAmountChange = { directSaleAmount = it },
                                    customerType = customerType,
                                    onCustomerTypeChange = { customerType = it },
                                    selectedCustomerId = selectedCustomerId,
                                    onSelectCustomer = { selectedCustomerId = it },
                                    customCustomerName = customCustomerName,
                                    onCustomCustomerNameChange = { customCustomerName = it },
                                    saleDiscount = saleDiscount,
                                    onSaleDiscountChange = { saleDiscount = it },
                                    salePaymentMethod = salePaymentMethod,
                                    onSalePaymentMethodChange = { salePaymentMethod = it },
                                    saleAccountId = saleAccountId,
                                    onSaleAccountIdChange = { saleAccountId = it },
                                    saleNotes = saleNotes,
                                    onSaleNotesChange = { saleNotes = it },
                                    saleDate = saleDate,
                                    onSaleDateChange = { saleDate = it },
                                    onOpenScanner = { isInnerBarcodeScannerOpen = true },
                                    onOpenAddItemDialog = { showQuickAddItemDialog = true },
                                    onShareWhatsApp = {
                                        val party = if (customerType == "KHATA") {
                                            state.customers.find { it.id == selectedCustomerId }?.name ?: (if (isUrdu) "کھاتہ دار" else "Khata Customer")
                                        } else {
                                            customCustomerName.ifBlank { if (isUrdu) "نقد کاؤنٹر گاہک" else "Counter Cash Customer" }
                                        }
                                        val cust = if (customerType == "KHATA") state.customers.find { it.id == selectedCustomerId } else null
                                        val remaining = if (cust != null && salePaymentMethod == PaymentMethod.CREDIT) cust.totalCredit + netSaleTotal else null
                                        val details = if (saleMode == "POS") {
                                            cartItems.joinToString("\n") { "• ${it.item.name}: ${it.quantity.toInt()} x ${FormatUtils.formatCurrency(it.unitPrice)} = ${FormatUtils.formatCurrency(it.lineTotal)}" } +
                                                    (if (parsedSaleDiscount > 0) "\nDiscount: -${FormatUtils.formatCurrency(parsedSaleDiscount)}" else "")
                                        } else {
                                            "Direct Sale: ${FormatUtils.formatCurrency(posSubtotal)}" + (if (parsedSaleDiscount > 0) "\nDiscount: -${FormatUtils.formatCurrency(parsedSaleDiscount)}" else "")
                                        }
                                        shareWhatsAppReceipt(
                                            title = if (isUrdu) "فروخت رسیـد" else "Sale Receipt",
                                            partyName = party,
                                            amount = netSaleTotal,
                                            details = details,
                                            remainingBalance = remaining
                                        )
                                    }
                                )
                            }
                            TransactionType.PURCHASE -> {
                                PurchaseForm(
                                    state = state,
                                    isUrdu = isUrdu,
                                    selectedSupplierId = selectedSupplierId,
                                    onSelectSupplier = { selectedSupplierId = it },
                                    customSupplierName = customSupplierName,
                                    onCustomSupplierNameChange = { customSupplierName = it },
                                    purchaseInvoiceNo = purchaseInvoiceNo,
                                    onPurchaseInvoiceNoChange = { purchaseInvoiceNo = it },
                                    purchaseDate = purchaseDate,
                                    onPurchaseDateChange = { purchaseDate = it },
                                    purchaseItemId = purchaseItemId,
                                    onSelectItem = { item ->
                                        purchaseItemId = item.id
                                        customPurchaseItemName = item.name
                                        purchaseCostPrice = item.purchasePrice.toInt().toString()
                                        purchaseUnit = item.unit
                                        purchaseBarcode = item.barcode ?: ""
                                    },
                                    customPurchaseItemName = customPurchaseItemName,
                                    onCustomItemNameChange = { customPurchaseItemName = it },
                                    purchaseQty = purchaseQty,
                                    onPurchaseQtyChange = { purchaseQty = it },
                                    purchaseUnit = purchaseUnit,
                                    onPurchaseUnitChange = { purchaseUnit = it },
                                    purchaseCostPrice = purchaseCostPrice,
                                    onPurchaseCostPriceChange = { purchaseCostPrice = it },
                                    purchaseItemCode = purchaseItemCode,
                                    onPurchaseItemCodeChange = { purchaseItemCode = it },
                                    purchaseBarcode = purchaseBarcode,
                                    onPurchaseBarcodeChange = { purchaseBarcode = it },
                                    purchaseLandingCost = purchaseLandingCost,
                                    onPurchaseLandingCostChange = { purchaseLandingCost = it },
                                    purchasePaymentTerm = purchasePaymentTerm,
                                    onPurchasePaymentTermChange = { purchasePaymentTerm = it },
                                    purchasePaidAmount = purchasePaidAmount,
                                    onPurchasePaidAmountChange = { purchasePaidAmount = it },
                                    purchaseAccountId = purchaseAccountId,
                                    onPurchaseAccountIdChange = { purchaseAccountId = it },
                                    onOpenScanner = { isInnerBarcodeScannerOpen = true },
                                    onOpenAddItemDialog = { showQuickAddItemDialog = true }
                                )
                            }
                            TransactionType.EXPENSE -> {
                                ExpenseForm(
                                    state = state,
                                    isUrdu = isUrdu,
                                    expenseCategory = expenseCategory,
                                    onSelectCategory = { expenseCategory = it },
                                    expenseAmount = expenseAmount,
                                    onExpenseAmountChange = { expenseAmount = it },
                                    expensePaidTo = expensePaidTo,
                                    onExpensePaidToChange = { expensePaidTo = it },
                                    expenseAccountId = expenseAccountId,
                                    onExpenseAccountIdChange = { expenseAccountId = it },
                                    expenseDate = expenseDate,
                                    onExpenseDateChange = { expenseDate = it },
                                    expenseNotes = expenseNotes,
                                    onExpenseNotesChange = { expenseNotes = it }
                                )
                            }
                            TransactionType.RECEIPT -> {
                                ReceiptForm(
                                    state = state,
                                    isUrdu = isUrdu,
                                    receiptCustomerId = receiptCustomerId,
                                    onSelectCustomer = { cust ->
                                        receiptCustomerId = cust.id
                                        receiptAmount = cust.totalCredit.coerceAtLeast(0.0).toInt().toString()
                                    },
                                    receiptAmount = receiptAmount,
                                    onReceiptAmountChange = { receiptAmount = it },
                                    receiptAccountId = receiptAccountId,
                                    onReceiptAccountIdChange = { receiptAccountId = it },
                                    receiptDate = receiptDate,
                                    onReceiptDateChange = { receiptDate = it },
                                    receiptNotes = receiptNotes,
                                    onReceiptNotesChange = { receiptNotes = it }
                                )
                            }
                            TransactionType.PAYMENT -> {
                                PaymentForm(
                                    state = state,
                                    isUrdu = isUrdu,
                                    paymentSupplierId = paymentSupplierId,
                                    onSelectSupplier = { supp ->
                                        paymentSupplierId = supp.id
                                        paymentAmount = supp.totalPayable.coerceAtLeast(0.0).toInt().toString()
                                    },
                                    paymentAmount = paymentAmount,
                                    onPaymentAmountChange = { paymentAmount = it },
                                    paymentAccountId = paymentAccountId,
                                    onPaymentAccountIdChange = { paymentAccountId = it },
                                    paymentDate = paymentDate,
                                    onPaymentDateChange = { paymentDate = it },
                                    paymentNotes = paymentNotes,
                                    onPaymentNotesChange = { paymentNotes = it }
                                )
                            }
                            TransactionType.TRANSFER -> {
                                TransferQuickForm(
                                    state = state,
                                    isUrdu = isUrdu,
                                    fromAccountId = transferFromAccountId,
                                    onFromAccountIdChange = { transferFromAccountId = it },
                                    toAccountId = transferToAccountId,
                                    onToAccountIdChange = { transferToAccountId = it },
                                    amount = transferAmount,
                                    onAmountChange = { transferAmount = it },
                                    transferDate = transferDate,
                                    onTransferDateChange = { transferDate = it },
                                    notes = transferNotes,
                                    onNotesChange = { transferNotes = it }
                                )
                            }
                            TransactionType.LOAN_GIVEN, TransactionType.LOAN_TAKEN -> {
                                LoanAssetQuickForm(
                                    state = state,
                                    isUrdu = isUrdu,
                                    partyName = loanPartyName,
                                    onPartyNameChange = { loanPartyName = it },
                                    amount = loanAmount,
                                    onAmountChange = { loanAmount = it },
                                    direction = loanDirection,
                                    onDirectionChange = { loanDirection = it },
                                    accountId = loanAccountId,
                                    onAccountIdChange = { loanAccountId = it },
                                    loanDate = loanDate,
                                    onLoanDateChange = { loanDate = it },
                                    notes = loanNotes,
                                    onNotesChange = { loanNotes = it }
                                )
                            }
                            else -> {}
                        }
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }

                // 4. STICKY BOTTOM SUBMIT BAR (Only for transaction entry forms, not analytics overview)
                if (activeTabId != "ANALYTICS") {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        color = Color.White,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            when (activeType) {
                                TransactionType.SALE -> {
                                    Button(
                                        onClick = {
                                            if (saleMode == "POS") {
                                                if (cartItems.isEmpty()) {
                                                    Toast.makeText(context, if (isUrdu) "براہ کرم بل میں اشیاء شامل کریں" else "Please add items to cart", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                val customer = if (customerType == "KHATA") state.customers.find { it.id == selectedCustomerId } else null
                                                val party = if (customerType == "KHATA") {
                                                    customer?.name ?: (if (isUrdu) "کھاتہ دار" else "Khata Customer")
                                                } else {
                                                    customCustomerName.ifBlank { if (isUrdu) "کاؤنٹر نقد کسٹمر" else "Counter Cash Customer" }
                                                }
                                                val itemsSummary = cartItems.joinToString(", ") { "${it.item.name} x ${it.quantity.toInt()}" }
                                                val desc = "POS Sale: $itemsSummary"

                                                val newTx = Transaction(
                                                    id = editingTransaction?.id ?: "tx_${System.currentTimeMillis()}",
                                                    type = TransactionType.SALE,
                                                    amount = netSaleTotal,
                                                    category = cartItems.firstOrNull()?.item?.category ?: "Sale",
                                                    paymentMethod = salePaymentMethod,
                                                    partyName = party,
                                                    date = saleDate,
                                                    notes = if (saleNotes.isNotBlank()) "$desc - $saleNotes" else desc,
                                                    itemId = cartItems.firstOrNull()?.item?.id
                                                )

                                                val custChange = if (salePaymentMethod == PaymentMethod.CREDIT && customer != null) netSaleTotal else 0.0

                                                if (onPosSaleSubmit != null) {
                                                    onPosSaleSubmit(
                                                        newTx,
                                                        cartItems.map { it.item.id to it.quantity },
                                                        customer?.id,
                                                        custChange,
                                                        if (salePaymentMethod != PaymentMethod.CREDIT) saleAccountId else null
                                                    )
                                                } else {
                                                    onSubmit(
                                                        newTx,
                                                        cartItems.firstOrNull()?.item?.id,
                                                        -(cartItems.firstOrNull()?.quantity ?: 1.0),
                                                        customer?.id,
                                                        custChange,
                                                        null,
                                                        0.0,
                                                        if (salePaymentMethod != PaymentMethod.CREDIT) saleAccountId else null
                                                    )
                                                }
                                                onClose()
                                            } else {
                                                val amt = directSaleAmount.toDoubleOrNull() ?: 0.0
                                                if (amt <= 0) {
                                                    Toast.makeText(context, if (isUrdu) "براہ کرم فروخت کی رقم درج کریں" else "Please enter sale amount", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                val customer = if (customerType == "KHATA") state.customers.find { it.id == selectedCustomerId } else null
                                                val party = if (customerType == "KHATA") {
                                                    customer?.name ?: (if (isUrdu) "کھاتہ دار" else "Khata Customer")
                                                } else {
                                                    customCustomerName.ifBlank { if (isUrdu) "کاؤنٹر نقد کسٹمر" else "Counter Cash Customer" }
                                                }
                                                val desc = "Quick Direct Sale"

                                                val newTx = Transaction(
                                                    id = editingTransaction?.id ?: "tx_${System.currentTimeMillis()}",
                                                    type = TransactionType.SALE,
                                                    amount = netSaleTotal,
                                                    category = "Direct Sale",
                                                    paymentMethod = salePaymentMethod,
                                                    partyName = party,
                                                    date = saleDate,
                                                    notes = if (saleNotes.isNotBlank()) "$desc - $saleNotes" else desc
                                                )
                                                val custChange = if (salePaymentMethod == PaymentMethod.CREDIT && customer != null) netSaleTotal else 0.0

                                                onSubmit(
                                                    newTx,
                                                    null,
                                                    0.0,
                                                    customer?.id,
                                                    custChange,
                                                    null,
                                                    0.0,
                                                    if (salePaymentMethod != PaymentMethod.CREDIT) saleAccountId else null
                                                )
                                                onClose()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Save", modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isUrdu) "فروخت محفوظ کریں" else "Record Sale",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = FormatUtils.formatCurrency(netSaleTotal),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }

                                TransactionType.PURCHASE -> {
                                    val qty = purchaseQty.toDoubleOrNull() ?: 1.0
                                    val cost = purchaseCostPrice.toDoubleOrNull() ?: 0.0
                                    val landingCost = purchaseLandingCost.toDoubleOrNull() ?: 0.0
                                    val totalCost = (qty * cost) + landingCost
                                    val paidAmt = purchasePaidAmount.toDoubleOrNull() ?: 0.0

                                    Button(
                                        onClick = {
                                            if (totalCost <= 0) {
                                                Toast.makeText(context, if (isUrdu) "براہ کرم درست لاگت درج کریں" else "Please enter valid cost", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val supplier = state.suppliers.find { it.id == selectedSupplierId }
                                            val party = supplier?.name ?: customSupplierName.ifBlank { if (isUrdu) "سپلائر" else "Supplier" }
                                            val item = state.inventory.find { it.id == purchaseItemId }
                                            val itemName = item?.name ?: customPurchaseItemName.ifBlank { "Stock In" }
                                            val invInfo = if (purchaseInvoiceNo.isNotBlank()) "Invoice #${purchaseInvoiceNo}" else ""
                                            val landingNote = if (landingCost > 0) " + Bilty: Rs. ${landingCost.toInt()}" else ""
                                            val desc = "Purchase: $itemName x ${qty.toInt()} $purchaseUnit$landingNote${if (invInfo.isNotBlank()) " ($invInfo)" else ""}"

                                            val actualPaymentMethod = when (purchasePaymentTerm) {
                                                "PAID_CASH" -> PaymentMethod.CASH
                                                "PAID_BANK" -> PaymentMethod.BANK
                                                "FULL_CREDIT" -> PaymentMethod.CREDIT
                                                "PARTIAL_CREDIT" -> if (paidAmt > 0) PaymentMethod.CASH else PaymentMethod.CREDIT
                                                else -> PaymentMethod.CASH
                                            }

                                            val suppChange = when (purchasePaymentTerm) {
                                                "FULL_CREDIT" -> totalCost
                                                "PARTIAL_CREDIT" -> (totalCost - paidAmt).coerceAtLeast(0.0)
                                                else -> 0.0
                                            }

                                            val finalTargetAccId = when (purchasePaymentTerm) {
                                                "FULL_CREDIT" -> null
                                                "PARTIAL_CREDIT" -> if (paidAmt > 0) purchaseAccountId else null
                                                else -> purchaseAccountId
                                            }

                                            val newTx = Transaction(
                                                id = "tx_${System.currentTimeMillis()}",
                                                type = TransactionType.PURCHASE,
                                                amount = if (purchasePaymentTerm == "PARTIAL_CREDIT" && paidAmt > 0) paidAmt else totalCost,
                                                category = item?.category ?: "Purchase",
                                                paymentMethod = actualPaymentMethod,
                                                partyName = party,
                                                date = purchaseDate,
                                                notes = if (purchaseNotes.isNotBlank()) "$desc - $purchaseNotes" else desc,
                                                itemId = item?.id
                                            )

                                            onSubmit(
                                                newTx,
                                                item?.id,
                                                qty,
                                                null,
                                                0.0,
                                                supplier?.id,
                                                suppChange,
                                                finalTargetAccId
                                            )
                                            onClose()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.LocalShipping, contentDescription = "Purchase", modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isUrdu) "خریداری محفوظ کریں" else "Record Purchase",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = FormatUtils.formatCurrency(totalCost),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }

                                TransactionType.EXPENSE -> {
                                    val amt = expenseAmount.toDoubleOrNull() ?: 0.0
                                    Button(
                                        onClick = {
                                            if (amt <= 0) {
                                                Toast.makeText(context, if (isUrdu) "براہ کرم خرچے کی رقم درج کریں" else "Please enter expense amount", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val desc = "Expense: $expenseCategory${if (expensePaidTo.isNotBlank()) " to $expensePaidTo" else ""}"
                                            val selectedAcc = state.bankAccounts.find { it.id == expenseAccountId }
                                            val payMethod = if (selectedAcc?.type == "BANK") PaymentMethod.BANK else PaymentMethod.CASH
                                            val newTx = Transaction(
                                                id = "tx_${System.currentTimeMillis()}",
                                                type = TransactionType.EXPENSE,
                                                amount = amt,
                                                category = expenseCategory,
                                                paymentMethod = payMethod,
                                                partyName = expensePaidTo.ifBlank { expenseCategory },
                                                date = expenseDate.ifBlank { FormatUtils.getTodayIsoDate() },
                                                notes = if (expenseNotes.isNotBlank()) "$desc - $expenseNotes" else desc
                                            )

                                            onSubmit(
                                                newTx,
                                                null,
                                                0.0,
                                                null,
                                                0.0,
                                                null,
                                                0.0,
                                                expenseAccountId
                                            )
                                            onClose()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.ReceiptLong, contentDescription = "Expense", modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isUrdu) "خرچہ محفوظ کریں" else "Record Expense",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = FormatUtils.formatCurrency(amt),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }

                                TransactionType.RECEIPT -> {
                                    val amt = receiptAmount.toDoubleOrNull() ?: 0.0
                                    Button(
                                        onClick = submitReceiptAction,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Paid, contentDescription = "Receipt", modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isUrdu) "ادھار وصولی محفوظ کریں" else "Record Udhaar Recovery",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = FormatUtils.formatCurrency(amt),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }

                                TransactionType.PAYMENT -> {
                                    val amt = paymentAmount.toDoubleOrNull() ?: 0.0
                                    val supplier = state.suppliers.find { it.id == paymentSupplierId }

                                    Button(
                                        onClick = {
                                            if (amt <= 0) {
                                                Toast.makeText(context, if (isUrdu) "براہ کرم ادائیگی کی رقم درج کریں" else "Please enter payment amount", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val party = supplier?.name ?: (if (isUrdu) "سپلائر ادائیگی" else "Supplier Payment")
                                            val desc = "Payment to $party"
                                            val newTx = Transaction(
                                                id = "tx_${System.currentTimeMillis()}",
                                                type = TransactionType.PAYMENT,
                                                amount = amt,
                                                category = "Supplier Payment",
                                                paymentMethod = PaymentMethod.CASH,
                                                partyName = party,
                                                date = paymentDate.ifBlank { FormatUtils.getTodayIsoDate() },
                                                notes = if (paymentNotes.isNotBlank()) "$desc - $paymentNotes" else desc
                                            )

                                            onSubmit(
                                                newTx,
                                                null,
                                                0.0,
                                                null,
                                                0.0,
                                                supplier?.id,
                                                -amt,
                                                paymentAccountId
                                            )
                                            onClose()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Send, contentDescription = "Payment", modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isUrdu) "سپلائر ادائیگی محفوظ کریں" else "Record Supplier Payment",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = FormatUtils.formatCurrency(amt),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }

                                TransactionType.TRANSFER -> {
                                    val amt = transferAmount.toDoubleOrNull() ?: 0.0
                                    Button(
                                        onClick = {
                                            if (amt <= 0) {
                                                Toast.makeText(context, if (isUrdu) "براہ کرم درست رقم درج کریں" else "Please enter transfer amount", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val fromAcc = state.bankAccounts.find { it.id == transferFromAccountId }
                                            val toAcc = state.bankAccounts.find { it.id == transferToAccountId }
                                            val desc = "Transfer from ${fromAcc?.accountTitle ?: "Account"} to ${toAcc?.accountTitle ?: "Account"}"
                                            val newTx = Transaction(
                                                id = "tx_${System.currentTimeMillis()}",
                                                type = TransactionType.TRANSFER,
                                                amount = amt,
                                                category = "Fund Transfer",
                                                paymentMethod = PaymentMethod.BANK,
                                                partyName = toAcc?.accountTitle ?: "Transfer",
                                                date = transferDate.ifBlank { FormatUtils.getTodayIsoDate() },
                                                notes = if (transferNotes.isNotBlank()) "$desc - $transferNotes" else desc
                                            )
                                            onSubmit(newTx, null, 0.0, null, 0.0, null, 0.0, transferFromAccountId)
                                            onClose()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AccountBalance, contentDescription = "Transfer", modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isUrdu) "رقم ٹرانسفر محفوظ کریں" else "Record Transfer",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = FormatUtils.formatCurrency(amt),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }

                                TransactionType.LOAN_GIVEN, TransactionType.LOAN_TAKEN -> {
                                    val amt = loanAmount.toDoubleOrNull() ?: 0.0
                                    val isGiven = loanDirection == "GIVEN"
                                    Button(
                                        onClick = {
                                            if (amt <= 0 || loanPartyName.isBlank()) {
                                                Toast.makeText(context, if (isUrdu) "براہ کرم فریق کا نام اور رقم درج کریں" else "Please enter party name and amount", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val desc = "${if (isGiven) "Loan Given to" else "Loan Taken from"} $loanPartyName"
                                            val newTx = Transaction(
                                                id = "tx_${System.currentTimeMillis()}",
                                                type = if (isGiven) TransactionType.LOAN_GIVEN else TransactionType.LOAN_TAKEN,
                                                amount = amt,
                                                category = if (isGiven) "Loan Given" else "Loan Taken",
                                                paymentMethod = PaymentMethod.CASH,
                                                partyName = loanPartyName,
                                                date = loanDate.ifBlank { FormatUtils.getTodayIsoDate() },
                                                notes = if (loanNotes.isNotBlank()) "$desc - $loanNotes" else desc
                                            )
                                            onSubmit(newTx, null, 0.0, null, 0.0, null, 0.0, loanAccountId)
                                            onClose()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Loan", modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isUrdu) "قرضہ اندراج محفوظ کریں" else "Record Loan Entry",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = FormatUtils.formatCurrency(amt),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }
                }
            }
        }
    }

    // Quick Add Item Dialog (+ Add Item from Sales Counter POS)
    if (showQuickAddItemDialog) {
        QuickAddItemDialog(
            isOpen = showQuickAddItemDialog,
            onClose = { showQuickAddItemDialog = false },
            isUrdu = isUrdu,
            existingCategories = remember(state.inventory) {
                (listOf("General", "Beverages", "Grocery", "Dairy", "Spices", "Snacks") +
                        state.inventory.map { it.category }.filter { it.isNotBlank() }).distinct()
            },
            onSaveItem = { newItem ->
                onAddNewInventoryItem?.invoke(newItem)
                if (activeType == TransactionType.SALE) {
                    cartItems = cartItems + PosCartItem(newItem, 1.0, newItem.salePrice)
                } else if (activeType == TransactionType.PURCHASE) {
                    purchaseItemId = newItem.id
                    customPurchaseItemName = newItem.name
                    purchaseCostPrice = newItem.purchasePrice.toInt().toString()
                    purchaseUnit = newItem.unit
                    purchaseBarcode = newItem.barcode ?: ""
                }
                showQuickAddItemDialog = false
                Toast.makeText(context, if (isUrdu) "${newItem.name} کیٹلاگ میں شامل ہو گیا" else "${newItem.name} added to catalog!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Barcode scanner integration for Quick Entry
    if (isInnerBarcodeScannerOpen) {
        BarcodeScannerDialog(
            isOpen = isInnerBarcodeScannerOpen,
            onClose = { isInnerBarcodeScannerOpen = false },
            inventory = state.inventory,
            onItemScanned = { item ->
                if (activeType == TransactionType.SALE) {
                    val existingIndex = cartItems.indexOfFirst { it.item.id == item.id }
                    if (existingIndex >= 0) {
                        val updated = cartItems.toMutableList()
                        updated[existingIndex] = updated[existingIndex].copy(quantity = updated[existingIndex].quantity + 1.0)
                        cartItems = updated
                    } else {
                        cartItems = cartItems + PosCartItem(item, 1.0, item.salePrice)
                    }
                    Toast.makeText(context, "${item.name} +1", Toast.LENGTH_SHORT).show()
                } else if (activeType == TransactionType.PURCHASE) {
                    purchaseItemId = item.id
                    customPurchaseItemName = item.name
                    purchaseCostPrice = item.purchasePrice.toInt().toString()
                    purchaseUnit = item.unit
                    purchaseBarcode = item.barcode ?: ""
                }
                isInnerBarcodeScannerOpen = false
            },
            onQuickSale = { item ->
                val existingIndex = cartItems.indexOfFirst { it.item.id == item.id }
                if (existingIndex >= 0) {
                    val updated = cartItems.toMutableList()
                    updated[existingIndex] = updated[existingIndex].copy(quantity = updated[existingIndex].quantity + 1.0)
                    cartItems = updated
                } else {
                    cartItems = cartItems + PosCartItem(item, 1.0, item.salePrice)
                }
                Toast.makeText(context, "${item.name} +1", Toast.LENGTH_SHORT).show()
                isInnerBarcodeScannerOpen = false
            },
            onQuickPurchase = { item ->
                purchaseItemId = item.id
                customPurchaseItemName = item.name
                purchaseCostPrice = item.purchasePrice.toInt().toString()
                purchaseUnit = item.unit
                purchaseBarcode = item.barcode ?: ""
                isInnerBarcodeScannerOpen = false
            },
            onAddNewItemWithBarcode = { barcode ->
                customPurchaseItemName = "Item $barcode"
                purchaseBarcode = barcode
                isInnerBarcodeScannerOpen = false
            },
            isUrdu = isUrdu
        )
    }
}

// ---------------------- SUB-FORMS ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleForm(
    state: AppState,
    isUrdu: Boolean,
    saleMode: String,
    onSaleModeChange: (String) -> Unit,
    cartItems: List<PosCartItem>,
    onCartItemsChange: (List<PosCartItem>) -> Unit,
    directSaleAmount: String,
    onDirectSaleAmountChange: (String) -> Unit,
    customerType: String,
    onCustomerTypeChange: (String) -> Unit,
    selectedCustomerId: String?,
    onSelectCustomer: (String?) -> Unit,
    customCustomerName: String,
    onCustomCustomerNameChange: (String) -> Unit,
    saleDiscount: String,
    onSaleDiscountChange: (String) -> Unit,
    salePaymentMethod: PaymentMethod,
    onSalePaymentMethodChange: (PaymentMethod) -> Unit,
    saleAccountId: String,
    onSaleAccountIdChange: (String) -> Unit,
    saleNotes: String,
    onSaleNotesChange: (String) -> Unit,
    saleDate: String,
    onSaleDateChange: (String) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenAddItemDialog: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val context = LocalContext.current
    var isCustomerMenuOpen by remember { mutableStateOf(false) }
    var posSearch by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val selectedCustomer = state.customers.find { it.id == selectedCustomerId }

    val posSubtotal = if (saleMode == "POS") cartItems.sumOf { it.lineTotal } else (directSaleAmount.toDoubleOrNull() ?: 0.0)
    val parsedDiscount = saleDiscount.toDoubleOrNull() ?: 0.0
    val netTotal = (posSubtotal - parsedDiscount).coerceAtLeast(0.0)

    val categories = remember(state.inventory) {
        listOf("All") + state.inventory.map { it.category }.filter { it.isNotBlank() }.distinct()
    }

    val filteredInventory = remember(state.inventory, posSearch, selectedCategory) {
        state.inventory.filter { item ->
            val matchCat = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val matchSearch = posSearch.isBlank() ||
                    item.name.contains(posSearch, ignoreCase = true) ||
                    (item.barcode?.contains(posSearch, ignoreCase = true) == true) ||
                    item.category.contains(posSearch, ignoreCase = true)
            matchCat && matchSearch
        }
    }

    // 1. TOP SUB-MODES SWITCHER: POS vs DIRECT (Premium Segmented Design)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isUrdu) "سیل موڈ منتخب کریں:" else "Select Sales Mode:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                }

                Surface(
                    color = DukanGreenLight,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = if (saleMode == "POS") (if (isUrdu) "اشیاء کاؤنٹر فعال" else "POS Active") else (if (isUrdu) "ڈائریکٹ نقد فعال" else "Direct Active"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DukanGreenHover,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate100, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Mode 1: Inventory POS
                val isPos = saleMode == "POS"
                Surface(
                    onClick = { onSaleModeChange("POS") },
                    shape = RoundedCornerShape(11.dp),
                    color = if (isPos) DukanGreenPrimary else Color.Transparent,
                    shadowElevation = if (isPos) 2.dp else 0.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "POS",
                            tint = if (isPos) Color.White else Slate700,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUrdu) "اشیاء کاؤنٹر (POS)" else "Inventory POS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isPos) Color.White else Slate700
                        )
                    }
                }

                // Mode 2: Quick Direct Sale
                val isDirect = saleMode == "DIRECT"
                Surface(
                    onClick = { onSaleModeChange("DIRECT") },
                    shape = RoundedCornerShape(11.dp),
                    color = if (isDirect) DukanGreenPrimary else Color.Transparent,
                    shadowElevation = if (isDirect) 2.dp else 0.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Direct",
                            tint = if (isDirect) Color.White else Slate700,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUrdu) "فوری نقد سیل" else "Quick Direct",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isDirect) Color.White else Slate700
                        )
                    }
                }
            }
        }
    }

    // 2. SUB-MODE BODY
    if (saleMode == "POS") {
        // MODE 1: INVENTORY POS (Premium Catalog Block)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header with Live Product Catalog Title & "+ Add Item" Button (single +)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = DukanGreenLight,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.GridView, contentDescription = null, tint = DukanGreenHover, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = if (isUrdu) "اشیاء کی لائیو فہرست" else "Live Product Catalog",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate800
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate100,
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "${filteredInventory.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // + Add Item Button (without duplicate small + icon)
                    Button(
                        onClick = onOpenAddItemDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(if (isUrdu) "+ نیا آئٹم" else "+ Add Item", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Search Box and Barcode Scanner Button (Sleek borderless circular scan action)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = posSearch,
                        onValueChange = { posSearch = it },
                        placeholder = { Text(if (isUrdu) "نام یا بارکوڈ تلاش کریں..." else "Search name or barcode...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Slate400, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (posSearch.isNotBlank()) {
                                IconButton(onClick = { posSearch = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = DukanGreenPrimary,
                            unfocusedContainerColor = Slate50,
                            focusedContainerColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Premium borderless circular barcode button (box/border removed)
                    Surface(
                        onClick = onOpenScanner,
                        shape = CircleShape,
                        color = DukanGreenLight,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Barcode",
                                tint = DukanGreenHover,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Category Filter Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isCatSelected = selectedCategory == cat
                        FilterChip(
                            selected = isCatSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp, fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DukanGreenPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Product Cards Grid
                if (filteredInventory.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredInventory.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { invItem ->
                                    val inCart = cartItems.find { it.item.id == invItem.id }
                                    val isLow = invItem.quantity <= invItem.minStockAlert

                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (inCart != null) Color(0xFFF0FDF4) else Slate50
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (inCart != null) Color(0xFF86EFAC) else Slate200
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (inCart != null) {
                                                    val updated = cartItems.map {
                                                        if (it.item.id == invItem.id) it.copy(quantity = it.quantity + 1.0) else it
                                                    }
                                                    onCartItemsChange(updated)
                                                } else {
                                                    onCartItemsChange(cartItems + PosCartItem(invItem, 1.0, invItem.salePrice))
                                                }
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = invItem.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Slate900,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (inCart != null) {
                                                    Surface(
                                                        color = DukanGreenPrimary,
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "+${inCart.quantity.toInt()}",
                                                                color = Color.White,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = FormatUtils.formatCurrency(invItem.salePrice),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DukanGreenHover
                                                )
                                                Text(
                                                    text = "${invItem.quantity.toInt()} ${invItem.unit}",
                                                    fontSize = 10.sp,
                                                    color = if (isLow) RedDanger else Slate500,
                                                    fontWeight = if (isLow) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Slate50,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isUrdu) "کوئی پروڈکٹ نہیں ملی" else "No matching products found",
                            fontSize = 12.sp,
                            color = Slate500,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    } else {
        // MODE 2: QUICK DIRECT SALE
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isUrdu) "فوری نقد رقم درج کریں (Direct Amount)" else "Direct Cash Amount",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate800
                )

                OutlinedTextField(
                    value = directSaleAmount,
                    onValueChange = onDirectSaleAmountChange,
                    label = { Text(if (isUrdu) "فروخت کی رقم (Rs.)" else "Sale Amount (Rs.)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (directSaleAmount.isNotBlank()) {
                            IconButton(onClick = { onDirectSaleAmountChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Currency Preset Shortcut Buttons
                Text(
                    text = if (isUrdu) "تیز رفتار شارٹ کٹس (Presets):" else "Quick Currency Shortcuts:",
                    fontSize = 11.sp,
                    color = Slate600
                )

                val presets = listOf(100, 200, 500, 1000, 2000, 5000)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPresets.forEach { amount ->
                                Surface(
                                    onClick = {
                                        val cur = directSaleAmount.toDoubleOrNull() ?: 0.0
                                        onDirectSaleAmountChange((cur + amount).toInt().toString())
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Slate100,
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "+Rs. $amount",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Slate800
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. DEDICATED BILL ITEMS BLOCK (Requirement 4: Always visible on this page)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (cartItems.isNotEmpty()) Color(0xFF86EFAC) else Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (cartItems.isNotEmpty()) DukanGreenLight else Slate100,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = "Bill Items",
                                tint = if (cartItems.isNotEmpty()) DukanGreenHover else Slate600,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isUrdu) "بل میں شامل اشیاء (${cartItems.size})" else "Bill Items (${cartItems.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate900
                    )
                }

                if (cartItems.isNotEmpty()) {
                    TextButton(
                        onClick = { onCartItemsChange(emptyList()) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = RedDanger, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isUrdu) "تمام صاف کریں" else "Clear All", color = RedDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (cartItems.isEmpty()) {
                Surface(
                    color = Slate50,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCartCheckout,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (isUrdu) "بل میں فی الحال کوئی آئٹم شامل نہیں ہے۔" else "No items added to bill yet.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isUrdu) "اوپر کیٹلاگ سے اشیاء منتخب کریں یا بارکوڈ اسکین کریں۔" else "Select products from catalog above or scan barcode to add.",
                            fontSize = 10.sp,
                            color = Slate400,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cartItems.forEach { ci ->
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ci.item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate900)
                                    Text(
                                        "${FormatUtils.formatCurrency(ci.unitPrice)} / ${ci.item.unit}",
                                        fontSize = 10.sp,
                                        color = Slate500
                                    )
                                }

                                // Stepper Controls
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            if (ci.quantity > 1.0) {
                                                val updated = cartItems.map {
                                                    if (it.item.id == ci.item.id) it.copy(quantity = it.quantity - 1.0) else it
                                                }
                                                onCartItemsChange(updated)
                                            } else {
                                                onCartItemsChange(cartItems.filter { it.item.id != ci.item.id })
                                            }
                                        },
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Slate300),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(14.dp), tint = Slate700)
                                        }
                                    }

                                    Text(
                                        text = "${ci.quantity.toInt()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate900
                                    )

                                    Surface(
                                        onClick = {
                                            val updated = cartItems.map {
                                                if (it.item.id == ci.item.id) it.copy(quantity = it.quantity + 1.0) else it
                                            }
                                            onCartItemsChange(updated)
                                        },
                                        shape = CircleShape,
                                        color = DukanGreenLight,
                                        border = BorderStroke(1.dp, GreenSuccessBorder),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(14.dp), tint = DukanGreenHover)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = FormatUtils.formatCurrency(ci.lineTotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = DukanGreenHover
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. CUSTOMER SELECTION CARD (Customer icon + uniform-sized buttons)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Customer Type label with customer icon and two uniform-sized buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = DukanGreenLight,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Customer",
                                tint = DukanGreenHover,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isUrdu) "گاہک کی قسم:" else "Customer Type:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate800
                    )
                }

                Row(
                    modifier = Modifier
                        .background(Slate100, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Pair("WALKIN", if (isUrdu) "نقدی گاہک" else "Walk-in"),
                        Pair("KHATA", if (isUrdu) "کھاتہ دار" else "Khata")
                    ).forEach { (type, label) ->
                        val isSel = customerType == type
                        Box(
                            modifier = Modifier
                                .width(94.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) DukanGreenPrimary else Color.Transparent)
                                .clickable { onCustomerTypeChange(type) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else Slate700,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (customerType == "WALKIN") {
                OutlinedTextField(
                    value = customCustomerName,
                    onValueChange = onCustomCustomerNameChange,
                    label = { Text(if (isUrdu) "گاہک کا نام (اختیاری)" else "Customer Name (Optional)") },
                    placeholder = { Text(if (isUrdu) "نقد کاؤنٹر کسٹمر" else "Counter Cash Customer") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = DukanGreenPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Khata Customer Search / Dropdown
                ExposedDropdownMenuBox(
                    expanded = isCustomerMenuOpen,
                    onExpandedChange = { isCustomerMenuOpen = !isCustomerMenuOpen }
                ) {
                    OutlinedTextField(
                        value = selectedCustomer?.name ?: (if (isUrdu) "کھاتہ دار منتخب کریں..." else "Select Khata Customer..."),
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null, tint = DukanGreenPrimary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCustomerMenuOpen) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = DukanGreenPrimary
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = isCustomerMenuOpen,
                        onDismissRequest = { isCustomerMenuOpen = false }
                    ) {
                        state.customers.forEach { cust ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(cust.name, fontWeight = FontWeight.Bold)
                                        if (cust.totalCredit > 0) {
                                            Text(
                                                FormatUtils.formatCurrency(cust.totalCredit),
                                                color = RedDanger,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectCustomer(cust.id)
                                    onCustomCustomerNameChange(cust.name)
                                    isCustomerMenuOpen = false
                                }
                            )
                        }
                    }
                }

                // Customer Previous Udhaar Alert
                if (selectedCustomer != null && selectedCustomer.totalCredit > 0) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = "Alert", tint = RedDanger, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isUrdu) "سابقہ واجب الادا ادھار:" else "Previous Due Udhaar:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDanger
                                )
                            }
                            Text(
                                text = FormatUtils.formatCurrency(selectedCustomer.totalCredit),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = RedDanger
                            )
                        }
                    }
                }
            }
        }
    }

    // 5. PAYMENT & SETTLEMENT CARD (Premium Styling)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = DukanGreenLight,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = DukanGreenHover,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isUrdu) "ادائیگی اور تصفیہ" else "Payment & Settlement",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate900
                    )
                }

                Surface(
                    color = if (salePaymentMethod == PaymentMethod.CREDIT) RedDangerLight else GreenSuccessLight,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (salePaymentMethod == PaymentMethod.CREDIT) (if (isUrdu) "ادھار کھاتہ" else "Credit") else (if (isUrdu) "وصولی" else "Paid"),
                        color = if (salePaymentMethod == PaymentMethod.CREDIT) RedDanger else GreenSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Payment Method Selector as Dropdown Window
            var isPaymentMethodMenuOpen by remember { mutableStateOf(false) }
            val paymentMethodOptions = listOf(
                PaymentMethod.CASH to (if (isUrdu) "نقد (Cash)" else "Cash"),
                PaymentMethod.EASYPAISA to "EasyPaisa",
                PaymentMethod.JAZZCASH to "JazzCash",
                PaymentMethod.BANK to (if (isUrdu) "بینک اکاؤنٹ (Bank Account)" else "Bank Account"),
                PaymentMethod.CREDIT to (if (isUrdu) "مکمل ادھار کھاتہ (Credit / Udhaar)" else "Credit / Khata (Udhaar)")
            )

            val currentMethodLabel = paymentMethodOptions.find { it.first == salePaymentMethod }?.second
                ?: (if (isUrdu) "نقد (Cash)" else "Cash")

            ExposedDropdownMenuBox(
                expanded = isPaymentMethodMenuOpen,
                onExpandedChange = { isPaymentMethodMenuOpen = !isPaymentMethodMenuOpen }
            ) {
                OutlinedTextField(
                    value = currentMethodLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isUrdu) "طریقہ ادائیگی (Payment Method)" else "Payment Method") },
                    leadingIcon = {
                        Icon(
                            imageVector = when (salePaymentMethod) {
                                PaymentMethod.CASH -> Icons.Default.Payments
                                PaymentMethod.CREDIT -> Icons.Default.ReceiptLong
                                PaymentMethod.BANK -> Icons.Default.AccountBalance
                                else -> Icons.Default.PhoneAndroid
                            },
                            contentDescription = null,
                            tint = if (salePaymentMethod == PaymentMethod.CREDIT) RedDanger else DukanGreenPrimary
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPaymentMethodMenuOpen) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = DukanGreenPrimary
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = isPaymentMethodMenuOpen,
                    onDismissRequest = { isPaymentMethodMenuOpen = false }
                ) {
                    paymentMethodOptions.forEach { (method, label) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (method) {
                                            PaymentMethod.CASH -> Icons.Default.Payments
                                            PaymentMethod.CREDIT -> Icons.Default.ReceiptLong
                                            PaymentMethod.BANK -> Icons.Default.AccountBalance
                                            else -> Icons.Default.PhoneAndroid
                                        },
                                        contentDescription = null,
                                        tint = if (method == PaymentMethod.CREDIT) RedDanger else DukanGreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = label,
                                        fontWeight = if (method == salePaymentMethod) FontWeight.Bold else FontWeight.Normal,
                                        color = if (method == PaymentMethod.CREDIT) RedDanger else Slate800
                                    )
                                }
                            },
                            onClick = {
                                onSalePaymentMethodChange(method)
                                isPaymentMethodMenuOpen = false
                            }
                        )
                    }
                }
            }

            // Receiving Account Dropdown (if not Credit)
            if (salePaymentMethod != PaymentMethod.CREDIT && state.bankAccounts.isNotEmpty()) {
                var isAccountMenuOpen by remember { mutableStateOf(false) }
                val selectedAcc = state.bankAccounts.find { it.id == saleAccountId } ?: state.bankAccounts.firstOrNull()

                ExposedDropdownMenuBox(
                    expanded = isAccountMenuOpen,
                    onExpandedChange = { isAccountMenuOpen = !isAccountMenuOpen }
                ) {
                    OutlinedTextField(
                        value = if (selectedAcc != null) "${selectedAcc.accountTitle} (${FormatUtils.formatCurrency(selectedAcc.balance)})" else (if (isUrdu) "اکاؤنٹ منتخب کریں" else "Select Account"),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isUrdu) "وصول شدہ اکاؤنٹ (Deposit Account)" else "Deposit Account") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Slate500) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountMenuOpen) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = DukanGreenPrimary
                        ),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isAccountMenuOpen,
                        onDismissRequest = { isAccountMenuOpen = false }
                    ) {
                        state.bankAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(acc.accountTitle, fontWeight = FontWeight.Bold)
                                        Text(FormatUtils.formatCurrency(acc.balance), color = DukanGreenHover)
                                    }
                                },
                                onClick = {
                                    onSaleAccountIdChange(acc.id)
                                    isAccountMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // Discount & Sale Date INSIDE this Payment Method Block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Discount (Rs.)
                OutlinedTextField(
                    value = saleDiscount,
                    onValueChange = onSaleDiscountChange,
                    label = { Text(if (isUrdu) "رعایت (Rs.)" else "Discount (Rs.)") },
                    placeholder = { Text("0") },
                    leadingIcon = { Icon(Icons.Default.LocalOffer, contentDescription = "Discount", tint = AmberWarning, modifier = Modifier.size(16.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = DukanGreenPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Sale Date
                OutlinedTextField(
                    value = saleDate,
                    onValueChange = onSaleDateChange,
                    label = { Text(if (isUrdu) "فروخت تاریخ" else "Sale Date") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                FormatUtils.showDatePickerDialog(context, saleDate) { selected ->
                                    onSaleDateChange(selected)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = if (isUrdu) "تاریخ منتخب کریں" else "Select Date",
                                modifier = Modifier.size(18.dp),
                                tint = DukanGreenPrimary
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = DukanGreenPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Invoice Remarks / Notes
            OutlinedTextField(
                value = saleNotes,
                onValueChange = onSaleNotesChange,
                label = { Text(if (isUrdu) "نوٹس / اضافی تفصیل" else "Notes / Invoice Remarks") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Slate200,
                    focusedBorderColor = DukanGreenPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 6. NET CALCULATION BOX
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (isUrdu) "سب ٹوٹل (Subtotal)" else "Subtotal", fontSize = 12.sp, color = Slate600)
                Text(FormatUtils.formatCurrency(posSubtotal), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
            }

            if (parsedDiscount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (isUrdu) "رعایت (Discount)" else "Discount", fontSize = 12.sp, color = RedDanger)
                    Text("-${FormatUtils.formatCurrency(parsedDiscount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedDanger)
                }
            }

            Divider(color = Color(0xFFBBF7D0))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isUrdu) "کل واجب الادا رقم (Net Payable)" else "Net Payable", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DukanGreenHover)
                Text(FormatUtils.formatCurrency(netTotal), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DukanGreenHover)
            }

            if (salePaymentMethod == PaymentMethod.CREDIT && selectedCustomer != null) {
                val newDue = selectedCustomer.totalCredit + netTotal
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = if (isUrdu) "نیا کل کھاتہ بقایا:" else "New Khata Balance:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedDanger)
                        Text(text = FormatUtils.formatCurrency(newDue), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedDanger)
                    }
                }
            }

            // WhatsApp Share Pill Button
            Button(
                onClick = onShareWhatsApp,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isUrdu) "واٹس ایپ پر اردو رسید بنائیں" else "Generate WhatsApp Invoice", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseForm(
    state: AppState,
    isUrdu: Boolean,
    selectedSupplierId: String?,
    onSelectSupplier: (String?) -> Unit,
    customSupplierName: String,
    onCustomSupplierNameChange: (String) -> Unit,
    purchaseInvoiceNo: String,
    onPurchaseInvoiceNoChange: (String) -> Unit,
    purchaseDate: String,
    onPurchaseDateChange: (String) -> Unit,
    purchaseItemId: String?,
    onSelectItem: (InventoryItem) -> Unit,
    customPurchaseItemName: String,
    onCustomItemNameChange: (String) -> Unit,
    purchaseQty: String,
    onPurchaseQtyChange: (String) -> Unit,
    purchaseUnit: String,
    onPurchaseUnitChange: (String) -> Unit,
    purchaseCostPrice: String,
    onPurchaseCostPriceChange: (String) -> Unit,
    purchaseItemCode: String,
    onPurchaseItemCodeChange: (String) -> Unit,
    purchaseBarcode: String,
    onPurchaseBarcodeChange: (String) -> Unit,
    purchaseLandingCost: String,
    onPurchaseLandingCostChange: (String) -> Unit,
    purchasePaymentTerm: String,
    onPurchasePaymentTermChange: (String) -> Unit,
    purchasePaidAmount: String,
    onPurchasePaidAmountChange: (String) -> Unit,
    purchaseAccountId: String,
    onPurchaseAccountIdChange: (String) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenAddItemDialog: () -> Unit
) {
    val context = LocalContext.current
    var isSupplierMenuOpen by remember { mutableStateOf(false) }
    var isItemMenuOpen by remember { mutableStateOf(false) }
    var isUnitMenuOpen by remember { mutableStateOf(false) }

    val selectedSupplier = state.suppliers.find { it.id == selectedSupplierId }
    val selectedItem = state.inventory.find { it.id == purchaseItemId }

    val qty = purchaseQty.toDoubleOrNull() ?: 1.0
    val cost = purchaseCostPrice.toDoubleOrNull() ?: 0.0
    val landingCost = purchaseLandingCost.toDoubleOrNull() ?: 0.0
    val itemsCost = qty * cost
    val totalCost = itemsCost + landingCost
    val paidAmt = purchasePaidAmount.toDoubleOrNull() ?: 0.0
    val remainingDue = (totalCost - paidAmt).coerceAtLeast(0.0)

    val unitsList = listOf(
        "Piece" to (if (isUrdu) "عدد / پیس" else "Piece"),
        "Packet" to (if (isUrdu) "پیکٹ" else "Packet"),
        "Box" to (if (isUrdu) "باکس / ڈبہ" else "Box"),
        "Kg" to (if (isUrdu) "کلوگرام (Kg)" else "Kg"),
        "Gram" to (if (isUrdu) "گرام" else "Gram"),
        "Litre" to (if (isUrdu) "لیٹر" else "Litre"),
        "Dozen" to (if (isUrdu) "درجن" else "Dozen"),
        "Carton" to (if (isUrdu) "کارٹن" else "Carton"),
        "Bag" to (if (isUrdu) "بوری / بیگ" else "Bag"),
        "Bottle" to (if (isUrdu) "بوتل" else "Bottle"),
        "Bundle" to (if (isUrdu) "بنڈل" else "Bundle"),
        "Meter" to (if (isUrdu) "میٹر" else "Meter")
    )

    // BLOCK 1: SUPPLIER / DISTRIBUTOR SELECTION
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header with icon, title, payable badge and "+ New Item" link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isUrdu) "سپلائر / ڈسٹری بیوٹر کا انتخاب" else "Supplier/Distributor Selection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate900
                    )
                }

                // "+ New Item" button/link (Single + icon)
                Button(
                    onClick = onOpenAddItemDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Item", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isUrdu) "نیا سامان" else "New Item", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Supplier Dropdown & Payable alert
            ExposedDropdownMenuBox(
                expanded = isSupplierMenuOpen,
                onExpandedChange = { isSupplierMenuOpen = !isSupplierMenuOpen }
            ) {
                OutlinedTextField(
                    value = selectedSupplier?.name ?: (if (customSupplierName.isNotBlank()) customSupplierName else (if (isUrdu) "سپلائر چنیں یا نام درج کریں..." else "Select or enter supplier name...")),
                    onValueChange = { onCustomSupplierNameChange(it) },
                    readOnly = false,
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSupplierMenuOpen) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFF2563EB)
                    )
                )

                ExposedDropdownMenu(
                    expanded = isSupplierMenuOpen,
                    onDismissRequest = { isSupplierMenuOpen = false }
                ) {
                    state.suppliers.forEach { supp ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(supp.name, fontWeight = FontWeight.Bold)
                                    if (supp.totalPayable > 0) {
                                        Text(
                                            FormatUtils.formatCurrency(supp.totalPayable),
                                            color = RedDanger,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectSupplier(supp.id)
                                onCustomSupplierNameChange(supp.name)
                                isSupplierMenuOpen = false
                            }
                        )
                    }
                }
            }

            if (selectedSupplier != null && selectedSupplier.totalPayable > 0) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUrdu) "سابقہ واجب الادا بقایا:" else "Previous Due Payable:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedDanger
                        )
                        Text(
                            text = FormatUtils.formatCurrency(selectedSupplier.totalPayable),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedDanger
                        )
                    }
                }
            }

            // Invoice / Bill # & Purchase Date Row (Heading above boxes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUrdu) "انوائس / بل نمبر:" else "Invoice / Bill #:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = purchaseInvoiceNo,
                        onValueChange = onPurchaseInvoiceNoChange,
                        placeholder = { Text("Inv-xxxx", fontSize = 12.sp, color = Slate400) },
                        leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUrdu) "خریداری تاریخ:" else "Purchase Date:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = purchaseDate,
                        onValueChange = onPurchaseDateChange,
                        placeholder = { Text("YYYY-MM-DD", fontSize = 12.sp, color = Slate400) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    FormatUtils.showDatePickerDialog(context, purchaseDate) { selected ->
                                        onPurchaseDateChange(selected)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = if (isUrdu) "تاریخ منتخب کریں" else "Select Date",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // BLOCK 2: STOCK ITEMS & COSTING
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header with Title & Barcode Scanner Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isUrdu) "اسٹاک اشیاء اور خریداری لاگت" else "Stock Items & Costing",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate900
                    )
                }

                Surface(
                    onClick = onOpenScanner,
                    shape = CircleShape,
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Barcode",
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Product Name Dropdown / Input
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isUrdu) "پروڈکٹ کا نام:" else "Product Name:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = isItemMenuOpen,
                    onExpandedChange = { isItemMenuOpen = !isItemMenuOpen }
                ) {
                    OutlinedTextField(
                        value = selectedItem?.name ?: (if (customPurchaseItemName.isNotBlank()) customPurchaseItemName else (if (isUrdu) "انوینٹری سے آئٹم منتخب کریں یا نام درج کریں..." else "Select product or type name...")),
                        onValueChange = { onCustomItemNameChange(it) },
                        readOnly = false,
                        leadingIcon = { Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isItemMenuOpen) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = Color(0xFF2563EB)
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = isItemMenuOpen,
                        onDismissRequest = { isItemMenuOpen = false }
                    ) {
                        state.inventory.forEach { invItem ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(invItem.name, fontWeight = FontWeight.Bold)
                                            Text("${invItem.quantity.toInt()} ${invItem.unit} in stock", fontSize = 10.sp, color = Slate500)
                                        }
                                        Text(
                                            "Cost: ${FormatUtils.formatCurrency(invItem.purchasePrice)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectItem(invItem)
                                    onCustomItemNameChange(invItem.name)
                                    onPurchaseUnitChange(invItem.unit)
                                    isItemMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // Quantity, Unit, Cost / Unit Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quantity
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUrdu) "تعداد:" else "Qty:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = purchaseQty,
                        onValueChange = onPurchaseQtyChange,
                        placeholder = { Text("1", fontSize = 12.sp, color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Unit Dropdown
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = if (isUrdu) "یونٹ:" else "Unit:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = isUnitMenuOpen,
                        onExpandedChange = { isUnitMenuOpen = !isUnitMenuOpen },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = unitsList.find { it.first.equals(purchaseUnit, ignoreCase = true) }?.second ?: purchaseUnit,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isUnitMenuOpen) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Slate200,
                                focusedBorderColor = Color(0xFF2563EB)
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = isUnitMenuOpen,
                            onDismissRequest = { isUnitMenuOpen = false }
                        ) {
                            unitsList.forEach { (unitKey, unitLabel) ->
                                DropdownMenuItem(
                                    text = { Text(unitLabel, fontSize = 12.sp, fontWeight = if (purchaseUnit == unitKey) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        onPurchaseUnitChange(unitKey)
                                        isUnitMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Cost / Unit
                Column(modifier = Modifier.weight(1.3f)) {
                    Text(
                        text = if (isUrdu) "لاگت فی یونٹ:" else "Cost / Unit:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = purchaseCostPrice,
                        onValueChange = onPurchaseCostPriceChange,
                        leadingIcon = {
                            Text(
                                "Rs.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate500
                            )
                        },
                        placeholder = { Text("0.00", fontSize = 12.sp, color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Bilty / Transport / Landing Cost (Rs.)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isUrdu) "بلٹی / ٹرانسپورٹ / لینڈنگ لاگت (Rs.):" else "Bilty / Transport / Landing Cost (Rs.):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = purchaseLandingCost,
                    onValueChange = onPurchaseLandingCostChange,
                    leadingIcon = {
                        Text(
                            "Rs.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500
                        )
                    },
                    placeholder = { Text("0.00", fontSize = 12.sp, color = Slate400) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFF2563EB)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Item Code & Barcode Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUrdu) "آئٹم کوڈ / SKU:" else "Item Code / SKU:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = purchaseItemCode,
                        onValueChange = onPurchaseItemCodeChange,
                        placeholder = { Text("ITM-01", fontSize = 12.sp, color = Slate400) },
                        leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = if (isUrdu) "بارکوڈ:" else "Barcode:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = purchaseBarcode,
                        onValueChange = onPurchaseBarcodeChange,
                        placeholder = { Text("896...", fontSize = 12.sp, color = Slate400) },
                        trailingIcon = {
                            IconButton(onClick = onOpenScanner) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // BLOCK 3: PAYMENT TERMS & BILL TOTAL
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isUrdu) "ادائیگی کی شرائط اور بل کا کل" else "Payment Terms & Bill Total",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate900
                    )
                }

                Surface(
                    color = when (purchasePaymentTerm) {
                        "FULL_CREDIT" -> RedDangerLight
                        "PARTIAL_CREDIT" -> AmberWarningLight
                        else -> Color(0xFFEFF6FF)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = when (purchasePaymentTerm) {
                            "PAID_CASH" -> if (isUrdu) "نقد" else "Cash"
                            "PAID_BANK" -> if (isUrdu) "بینک" else "Bank"
                            "FULL_CREDIT" -> if (isUrdu) "مکمل ادھار" else "Full Credit"
                            "PARTIAL_CREDIT" -> if (isUrdu) "جزوی ادھار" else "Partial"
                            else -> ""
                        },
                        color = when (purchasePaymentTerm) {
                            "FULL_CREDIT" -> RedDanger
                            "PARTIAL_CREDIT" -> AmberWarning
                            else -> Color(0xFF2563EB)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // 4 Tabs for Payment Terms: Paid Cash, Paid Bank, Full Credit, Partial Credit
            val paymentTabs = listOf(
                Pair("PAID_CASH", if (isUrdu) "نقد ادا کیا" else "Paid Cash"),
                Pair("PAID_BANK", if (isUrdu) "بینک ادا کیا" else "Paid Bank"),
                Pair("FULL_CREDIT", if (isUrdu) "مکمل ادھار" else "Full Credit"),
                Pair("PARTIAL_CREDIT", if (isUrdu) "جزوی ادھار" else "Partial Credit")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate100, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                paymentTabs.forEach { (termKey, termLabel) ->
                    val isSelected = purchasePaymentTerm == termKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) Color(0xFF2563EB) else Color.Transparent)
                            .clickable { onPurchasePaymentTermChange(termKey) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = termLabel,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Slate700,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Paid From Account section (if not full credit)
            if (purchasePaymentTerm != "FULL_CREDIT" && state.bankAccounts.isNotEmpty()) {
                Text(
                    text = if (isUrdu) "رقم کس اکاؤنٹ سے ادا ہوئی؟" else "Paid From Account:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate600
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.bankAccounts.forEach { acc ->
                        val isSelected = purchaseAccountId == acc.id
                        Surface(
                            onClick = { onPurchaseAccountIdChange(acc.id) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFEFF6FF) else Slate50,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFBFDBFE) else Slate200),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = acc.accountTitle,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF1D4ED8) else Slate700
                                )
                                Text(
                                    text = "(${FormatUtils.formatCurrency(acc.balance)})",
                                    fontSize = 9.sp,
                                    color = if (isSelected) Color(0xFF1D4ED8) else Slate500
                                )
                            }
                        }
                    }
                }
            }

            // If Partial Credit, input for Paid Amount and Remaining Due alert
            if (purchasePaymentTerm == "PARTIAL_CREDIT") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = purchasePaidAmount,
                        onValueChange = onPurchasePaidAmountChange,
                        label = { Text(if (isUrdu) "ابھی ادا شدہ نقد (Rs.)" else "Paid Now (Rs.)", fontSize = 11.sp) },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(if (isUrdu) "باقی واجب الادا ادھار" else "Remaining Due", fontSize = 10.sp, color = RedDanger, fontWeight = FontWeight.Bold)
                            Text(FormatUtils.formatCurrency(remainingDue), fontSize = 13.sp, color = RedDanger, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Calculation and Bill Total Summary Card (Total Items, Items Cost, Total Purchase Amount)
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Total Items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isUrdu) "کل اشیاء (Total Items):" else "Total Items:", fontSize = 11.sp, color = Slate600)
                        Text("${qty.toInt()} $purchaseUnit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    }

                    // Items Cost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isUrdu) "لاگت اشیاء (Items Cost):" else "Items Cost:", fontSize = 11.sp, color = Slate600)
                        Text(FormatUtils.formatCurrency(itemsCost), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    }

                    if (landingCost > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isUrdu) "بلٹی / ٹرانسپورٹ لاگت:" else "Bilty / Landing Cost:", fontSize = 11.sp, color = Slate600)
                            Text(FormatUtils.formatCurrency(landingCost), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                    }

                    Divider(color = Color(0xFFBFDBFE))

                    // Total Purchase Amount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUrdu) "کل خریداری رقم (Total Purchase Amount):" else "Total Purchase Amount:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF1D4ED8)
                        )
                        Text(
                            text = FormatUtils.formatCurrency(totalCost),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1D4ED8)
                        )
                    }

                    if (purchasePaymentTerm == "FULL_CREDIT" && selectedSupplier != null) {
                        val newSupplierPayable = selectedSupplier.totalPayable + totalCost
                        Text(
                            text = "${if (isUrdu) "سپلائر کا نیا کل واجب الادا بقایا" else "New Total Payable"}: ${FormatUtils.formatCurrency(newSupplierPayable)}",
                            fontSize = 10.sp,
                            color = RedDanger,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseForm(
    state: AppState,
    isUrdu: Boolean,
    expenseCategory: String,
    onSelectCategory: (String) -> Unit,
    expenseAmount: String,
    onExpenseAmountChange: (String) -> Unit,
    expensePaidTo: String,
    onExpensePaidToChange: (String) -> Unit,
    expenseAccountId: String,
    onExpenseAccountIdChange: (String) -> Unit,
    expenseDate: String,
    onExpenseDateChange: (String) -> Unit,
    expenseNotes: String,
    onExpenseNotesChange: (String) -> Unit
) {
    val context = LocalContext.current
    var isAccountMenuOpen by remember { mutableStateOf(false) }
    val selectedAccount = state.bankAccounts.find { it.id == expenseAccountId }
        ?: state.bankAccounts.firstOrNull()

    val categories = listOf(
        Pair("ELECTRICITY", if (isUrdu) "⚡ بجلی کا بل" else "⚡ Electricity"),
        Pair("RENT", if (isUrdu) "🏠 دکان کرایہ" else "🏠 Shop Rent"),
        Pair("TEA_FOOD", if (isUrdu) "☕ چائے و کھانا" else "☕ Tea & Food"),
        Pair("SALARY", if (isUrdu) "👥 ملازمین تنخواہ" else "👥 Salaries"),
        Pair("MAINTENANCE", if (isUrdu) "🔧 دکان مرمت" else "🔧 Maintenance"),
        Pair("TRANSPORT", if (isUrdu) "🚚 کرایہ مال" else "🚚 Transport"),
        Pair("PACKAGING", if (isUrdu) "🛍️ شاپر و پیکنگ" else "🛍️ Bags & Packing"),
        Pair("OTHER", if (isUrdu) "📝 دیگر خرچے" else "📝 Other Expense")
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isUrdu) "خرچے کی قسم منتخب کریں" else "Select Expense Category",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Slate800
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.chunked(2).forEach { rowCats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowCats.forEach { (catKey, catLabel) ->
                            val isSelected = expenseCategory == catKey
                            Surface(
                                onClick = { onSelectCategory(catKey) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFFEF2F2) else Slate50,
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFFFECACA) else Slate200),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = catLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) RedDanger else Slate700
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (isUrdu) "خرچے کی رقم" else "Expense Amount",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Slate800
            )

            OutlinedTextField(
                value = expenseAmount,
                onValueChange = onExpenseAmountChange,
                label = { Text(if (isUrdu) "رقم (روپے)" else "Amount (Rs.)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(100, 500, 1000, 5000).forEach { addAmt ->
                    Surface(
                        onClick = {
                            val current = expenseAmount.toDoubleOrNull() ?: 0.0
                            onExpenseAmountChange((current + addAmt).toInt().toString())
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Slate100,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text("+$addAmt", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                    }
                }
            }
        }
    }

    // Payment Account & Date (Side by side) + Paid To + Description Details Card
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Payment Account & Date (Side by side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Payment Account
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = if (isUrdu) "ادائیگی کا اکاؤنٹ:" else "Payment Account:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = isAccountMenuOpen,
                        onExpandedChange = { isAccountMenuOpen = !isAccountMenuOpen },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedAccount?.let { "${it.accountTitle} (${FormatUtils.formatCurrency(it.balance)})" }
                                ?: (if (isUrdu) "اکاؤنٹ منتخب کریں" else "Select Account"),
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (selectedAccount?.type == "BANK") Icons.Default.AccountBalance else Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = RedDanger,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountMenuOpen) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Slate200,
                                focusedBorderColor = RedDanger
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = isAccountMenuOpen,
                            onDismissRequest = { isAccountMenuOpen = false }
                        ) {
                            state.bankAccounts.forEach { acc ->
                                val isAccSelected = expenseAccountId == acc.id
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (acc.type == "BANK") Icons.Default.AccountBalance else Icons.Default.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint = if (isAccSelected) RedDanger else Slate500,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = acc.accountTitle,
                                                    fontWeight = if (isAccSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp,
                                                    color = if (isAccSelected) RedDanger else Slate900
                                                )
                                            }
                                            Text(
                                                text = FormatUtils.formatCurrency(acc.balance),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAccSelected) RedDanger else Slate600
                                            )
                                        }
                                    },
                                    onClick = {
                                        onExpenseAccountIdChange(acc.id)
                                        isAccountMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Date
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUrdu) "تاریخ:" else "Date:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = expenseDate,
                        onValueChange = onExpenseDateChange,
                        placeholder = { Text("YYYY-MM-DD", fontSize = 12.sp, color = Slate400) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    FormatUtils.showDatePickerDialog(context, expenseDate) { selected ->
                                        onExpenseDateChange(selected)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = if (isUrdu) "تاریخ منتخب کریں" else "Select Date",
                                    tint = RedDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = RedDanger
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Paid To (Recipient / Vendor)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isUrdu) "رقم کس کو ادا کی؟ (وصول کنندہ / وینڈر):" else "Paid To (Recipient / Vendor):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = expensePaidTo,
                    onValueChange = onExpensePaidToChange,
                    placeholder = { Text("e.g. Electrician, Landlord, Staff", fontSize = 12.sp, color = Slate400) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = RedDanger, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = RedDanger
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Expense Notes / Description
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isUrdu) "اخراجات کی تفصیل / نوٹس:" else "Expense Notes / Description:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = expenseNotes,
                    onValueChange = onExpenseNotesChange,
                    placeholder = { Text(if (isUrdu) "اضافی نوٹس..." else "Additional Notes", fontSize = 12.sp, color = Slate400) },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = RedDanger
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptForm(
    state: AppState,
    isUrdu: Boolean,
    receiptCustomerId: String?,
    onSelectCustomer: (CustomerParty) -> Unit,
    receiptAmount: String,
    onReceiptAmountChange: (String) -> Unit,
    receiptAccountId: String,
    onReceiptAccountIdChange: (String) -> Unit,
    receiptDate: String,
    onReceiptDateChange: (String) -> Unit,
    receiptNotes: String,
    onReceiptNotesChange: (String) -> Unit
) {
    val context = LocalContext.current
    var isCustomerMenuOpen by remember { mutableStateOf(false) }
    var isAccountMenuOpen by remember { mutableStateOf(false) }
    var customerSearchQuery by remember { mutableStateOf("") }

    val selectedCustomer = state.customers.find { it.id == receiptCustomerId }
    val debtCustomers = remember(state.customers) {
        state.customers.filter { it.totalCredit > 0 }.sortedByDescending { it.totalCredit }
    }

    val filteredCustomers = remember(state.customers, customerSearchQuery, debtCustomers) {
        val base = if (debtCustomers.isNotEmpty()) debtCustomers else state.customers
        if (customerSearchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.name.contains(customerSearchQuery, ignoreCase = true) ||
                it.phone.contains(customerSearchQuery, ignoreCase = true)
            }
        }
    }

    val selectedAccount = state.bankAccounts.find { it.id == receiptAccountId }
        ?: state.bankAccounts.firstOrNull { it.type == "CASH" }
        ?: state.bankAccounts.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ==================== CARD 1: SELECT CUSTOMER FROM KHATA ====================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Top Row: Collection Icon + Heading + Customer Counter Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isUrdu) "کھاتہ سے گاہک منتخب کریں" else "Select Customer from Khata",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Slate900
                        )
                    }

                    // Counter Badge Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Text(
                            text = "${debtCustomers.size} ${if (isUrdu) "ادھار گاہک" else "Customers with Due"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Search Box
                OutlinedTextField(
                    value = customerSearchQuery,
                    onValueChange = {
                        customerSearchQuery = it
                        if (it.isNotBlank()) isCustomerMenuOpen = true
                    },
                    placeholder = {
                        Text(
                            if (isUrdu) "گاہک کا نام یا فون تلاش کریں..." else "Search customer name or phone...",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFFD97706)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Subheading & Customer Dropdown
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isUrdu) "ادھار والے گاہک کی فہرست:" else "Customer with Due Balance",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = Slate700
                    )

                    ExposedDropdownMenuBox(
                        expanded = isCustomerMenuOpen,
                        onExpandedChange = { isCustomerMenuOpen = !isCustomerMenuOpen }
                    ) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: (if (isUrdu) "گاہک منتخب کریں..." else "Select Customer..."),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCustomerMenuOpen) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Slate200,
                                focusedBorderColor = Color(0xFFD97706)
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = isCustomerMenuOpen,
                            onDismissRequest = { isCustomerMenuOpen = false }
                        ) {
                            if (filteredCustomers.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isUrdu) "کوئی گاہک نہیں ملا" else "No customer found",
                                            fontSize = 12.sp,
                                            color = Slate400
                                        )
                                    },
                                    onClick = {}
                                )
                            } else {
                                filteredCustomers.forEach { cust ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    if (cust.phone.isNotBlank()) {
                                                        Text(cust.phone, fontSize = 10.sp, color = Slate400)
                                                    }
                                                }
                                                Text(
                                                    "${if (isUrdu) "باقایہ" else "Due"}: ${FormatUtils.formatCurrency(cust.totalCredit)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (cust.totalCredit > 0) RedDanger else Slate500,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSelectCustomer(cust)
                                            isCustomerMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Due balance banner & Quick full/half payment actions if customer selected
                if (selectedCustomer != null) {
                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isUrdu) "کل واجب الادا ادھار:" else "Total Udhaar Due:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(selectedCustomer.totalCredit),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDanger
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onReceiptAmountChange(selectedCustomer.totalCredit.toInt().toString()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                ) {
                                    Text(if (isUrdu) "مکمل وصولی (Full)" else "Full Payment", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onReceiptAmountChange((selectedCustomer.totalCredit / 2).toInt().toString()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate100, contentColor = Slate800),
                                    border = BorderStroke(1.dp, Slate300),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                ) {
                                    Text(if (isUrdu) "آدھی وصولی (Half)" else "Half Payment", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== CARD 2: COLLECTION AMOUNT & SETTLEMENT ====================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Card 2 Heading
                Text(
                    text = if (isUrdu) "وصولی رقم اور کھاتہ سیٹلمنٹ" else "Collection Amount & Settlement",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Slate900
                )

                // Subheading: Received Amount (Rs.):
                Text(
                    text = if (isUrdu) "وصول شدہ رقم (روپے):" else "Received Amount (Rs.):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = Slate700
                )

                // Received Amount Box with Rs. 0.00 style
                OutlinedTextField(
                    value = receiptAmount,
                    onValueChange = onReceiptAmountChange,
                    leadingIcon = {
                        Text(
                            text = "Rs.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Slate400,
                            modifier = Modifier.padding(start = 12.dp, end = 2.dp)
                        )
                    },
                    placeholder = {
                        Text(
                            text = "0.00",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate400
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFFD97706)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Row: Receiving Account & Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Receiving Account Column (Dropdown)
                    Column(modifier = Modifier.weight(1.15f)) {
                        Text(
                            text = if (isUrdu) "وصولی اکاؤنٹ:" else "Receiving Account:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Slate700,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = isAccountMenuOpen,
                            onExpandedChange = { isAccountMenuOpen = !isAccountMenuOpen }
                        ) {
                            OutlinedTextField(
                                value = selectedAccount?.accountTitle ?: (if (isUrdu) "کاؤنٹر کیش" else "Shop Cash Drawer"),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountMenuOpen) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Slate200,
                                    focusedBorderColor = Color(0xFFD97706)
                                ),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = isAccountMenuOpen,
                                onDismissRequest = { isAccountMenuOpen = false }
                            ) {
                                state.bankAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(acc.accountTitle, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                                Text(FormatUtils.formatCurrency(acc.balance), fontSize = 10.sp, color = Slate500)
                                            }
                                        },
                                        onClick = {
                                            onReceiptAccountIdChange(acc.id)
                                            isAccountMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Date Column (with Calendar)
                    Column(modifier = Modifier.weight(0.95f)) {
                        Text(
                            text = if (isUrdu) "تاریخ:" else "Date:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Slate700,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        OutlinedTextField(
                            value = receiptDate,
                            onValueChange = onReceiptDateChange,
                            placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp, color = Slate400) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        FormatUtils.showDatePickerDialog(context, receiptDate) { selected ->
                                            onReceiptDateChange(selected)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = if (isUrdu) "تاریخ منتخب کریں" else "Select Date",
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Slate200,
                                focusedBorderColor = Color(0xFFD97706)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Remaining Udhaar display if customer selected
                if (selectedCustomer != null) {
                    val amt = receiptAmount.toDoubleOrNull() ?: 0.0
                    val remaining = (selectedCustomer.totalCredit - amt).coerceAtLeast(0.0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isUrdu) "وصولی کے بعد باقی ادھار:" else "Remaining Udhaar:", fontSize = 11.sp, color = Slate600)
                        Text(FormatUtils.formatCurrency(remaining), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (remaining > 0) AmberWarning else DukanGreenHover)
                    }
                }

                // Optional Notes / Remarks
                OutlinedTextField(
                    value = receiptNotes,
                    onValueChange = onReceiptNotesChange,
                    label = { Text(if (isUrdu) "رسید نمبر / اضافی تفصیل (اختیاری)" else "Receipt Remarks / Notes (Optional)", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFFD97706)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentForm(
    state: AppState,
    isUrdu: Boolean,
    paymentSupplierId: String?,
    onSelectSupplier: (SupplierParty) -> Unit,
    paymentAmount: String,
    onPaymentAmountChange: (String) -> Unit,
    paymentAccountId: String,
    onPaymentAccountIdChange: (String) -> Unit,
    paymentDate: String,
    onPaymentDateChange: (String) -> Unit,
    paymentNotes: String,
    onPaymentNotesChange: (String) -> Unit
) {
    val context = LocalContext.current
    var isSupplierMenuOpen by remember { mutableStateOf(false) }
    var isAccountMenuOpen by remember { mutableStateOf(false) }
    var supplierSearchQuery by remember { mutableStateOf("") }

    val selectedSupplier = state.suppliers.find { it.id == paymentSupplierId }

    val debtSuppliers = remember(state.suppliers) {
        state.suppliers.filter { it.totalPayable > 0 }.sortedByDescending { it.totalPayable }
    }

    val filteredSuppliers = remember(state.suppliers, supplierSearchQuery, debtSuppliers) {
        val base = if (debtSuppliers.isNotEmpty()) debtSuppliers else state.suppliers
        if (supplierSearchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.name.contains(supplierSearchQuery, ignoreCase = true) ||
                it.phone.contains(supplierSearchQuery, ignoreCase = true)
            }
        }
    }

    val selectedAccount = state.bankAccounts.find { it.id == paymentAccountId }
        ?: state.bankAccounts.firstOrNull { it.type == "CASH" }
        ?: state.bankAccounts.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ==================== CARD 1: SELECT SUPPLIER ====================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Top Row: Icon + Heading + Supplier Counter Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isUrdu) "سپلائر منتخب کریں" else "Select Supplier",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Slate900
                        )
                    }

                    // Supplier Counter Pill: "0 Suppliers" (or count of added suppliers)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF5F3FF),
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                    ) {
                        Text(
                            text = "${state.suppliers.size} ${if (isUrdu) "سپلائرز" else "Suppliers"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Search Box: "search supplier with name or phone number"
                OutlinedTextField(
                    value = supplierSearchQuery,
                    onValueChange = {
                        supplierSearchQuery = it
                        if (it.isNotBlank()) isSupplierMenuOpen = true
                    },
                    placeholder = {
                        Text(
                            if (isUrdu) "سپلائر کا نام یا فون تلاش کریں..." else "Search supplier with name or phone number",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFF7C3AED)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Subheading & Supplier to Pay Dropdown (retained)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isUrdu) "سپلائر جس کو ادائیگی کرنی ہے:" else "Supplier to Pay",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = Slate700
                    )

                    ExposedDropdownMenuBox(
                        expanded = isSupplierMenuOpen,
                        onExpandedChange = { isSupplierMenuOpen = !isSupplierMenuOpen }
                    ) {
                        OutlinedTextField(
                            value = selectedSupplier?.name ?: (if (isUrdu) "سپلائر چنیں..." else "Select Supplier..."),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSupplierMenuOpen) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Slate200,
                                focusedBorderColor = Color(0xFF7C3AED)
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = isSupplierMenuOpen,
                            onDismissRequest = { isSupplierMenuOpen = false }
                        ) {
                            if (filteredSuppliers.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isUrdu) "کوئی سپلائر نہیں ملا" else "No supplier found",
                                            fontSize = 12.sp,
                                            color = Slate400
                                        )
                                    },
                                    onClick = {}
                                )
                            } else {
                                filteredSuppliers.forEach { supp ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(supp.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    if (supp.phone.isNotBlank()) {
                                                        Text(supp.phone, fontSize = 10.sp, color = Slate400)
                                                    }
                                                }
                                                Text(
                                                    "${if (isUrdu) "واجب الادا" else "Owed"}: ${FormatUtils.formatCurrency(supp.totalPayable)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (supp.totalPayable > 0) Color(0xFF7C3AED) else Slate500,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSelectSupplier(supp)
                                            isSupplierMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Due balance banner & Quick full/half payment actions if supplier selected
                if (selectedSupplier != null) {
                    Surface(
                        color = Color(0xFFF5F3FF),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isUrdu) "سپلائر کو واجب الادا رقم:" else "Total Payable Due:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5B21B6)
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(selectedSupplier.totalPayable),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7C3AED)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onPaymentAmountChange(selectedSupplier.totalPayable.toInt().toString()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                ) {
                                    Text(if (isUrdu) "مکمل ادائیگی (Full)" else "Full Payment", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onPaymentAmountChange((selectedSupplier.totalPayable / 2).toInt().toString()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate100, contentColor = Slate800),
                                    border = BorderStroke(1.dp, Slate300),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                ) {
                                    Text(if (isUrdu) "آدھی ادائیگی (Half)" else "Half Payment", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== CARD 2: PAYMENT VOUCHER DETAILS ====================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Card 2 Heading: Payment Voucher Details
                Text(
                    text = if (isUrdu) "ادائیگی واؤچر کی تفصیلات" else "Payment Voucher Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Slate900
                )

                // Subheading: Payment Amount (Rs.):
                Text(
                    text = if (isUrdu) "ادائیگی رقم (روپے):" else "Payment Amount (Rs.):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = Slate700
                )

                // Payment Amount Box with text Rs. 0.00
                OutlinedTextField(
                    value = paymentAmount,
                    onValueChange = onPaymentAmountChange,
                    leadingIcon = {
                        Text(
                            text = "Rs.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Slate400,
                            modifier = Modifier.padding(start = 12.dp, end = 2.dp)
                        )
                    },
                    placeholder = {
                        Text(
                            text = "0.00",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate400
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFF7C3AED)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Row: Payment Mode (Dropdown) & Date (with Calendar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Payment Mode Column (Dropdown)
                    Column(modifier = Modifier.weight(1.15f)) {
                        Text(
                            text = if (isUrdu) "ادائیگی موڈ / اکاؤنٹ:" else "Payment Mode:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Slate700,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        ExposedDropdownMenuBox(
                            expanded = isAccountMenuOpen,
                            onExpandedChange = { isAccountMenuOpen = !isAccountMenuOpen }
                        ) {
                            OutlinedTextField(
                                value = selectedAccount?.accountTitle ?: (if (isUrdu) "کاؤنٹر کیش" else "Shop Cash Drawer"),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountMenuOpen) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Slate200,
                                    focusedBorderColor = Color(0xFF7C3AED)
                                ),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = isAccountMenuOpen,
                                onDismissRequest = { isAccountMenuOpen = false }
                            ) {
                                state.bankAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(acc.accountTitle, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                                Text(FormatUtils.formatCurrency(acc.balance), fontSize = 10.sp, color = Slate500)
                                            }
                                        },
                                        onClick = {
                                            onPaymentAccountIdChange(acc.id)
                                            isAccountMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Date Column (with Calendar Icon & DatePicker)
                    Column(modifier = Modifier.weight(0.95f)) {
                        Text(
                            text = if (isUrdu) "تاریخ:" else "Date:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Slate700,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        OutlinedTextField(
                            value = paymentDate,
                            onValueChange = onPaymentDateChange,
                            placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp, color = Slate400) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        FormatUtils.showDatePickerDialog(context, paymentDate) { selected ->
                                            onPaymentDateChange(selected)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = if (isUrdu) "تاریخ منتخب کریں" else "Select Date",
                                        tint = Color(0xFF7C3AED),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Slate200,
                                focusedBorderColor = Color(0xFF7C3AED)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Remaining Payable display if supplier selected
                if (selectedSupplier != null) {
                    val amt = paymentAmount.toDoubleOrNull() ?: 0.0
                    val remaining = (selectedSupplier.totalPayable - amt).coerceAtLeast(0.0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isUrdu) "ادائیگی کے بعد باقی واجب الادا:" else "Remaining Payable:", fontSize = 11.sp, color = Slate600)
                        Text(FormatUtils.formatCurrency(remaining), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (remaining > 0) Color(0xFF7C3AED) else DukanGreenHover)
                    }
                }

                // Optional Notes / Description
                OutlinedTextField(
                    value = paymentNotes,
                    onValueChange = onPaymentNotesChange,
                    placeholder = { Text(if (isUrdu) "چیک نمبر / حوالہ / اضافی تفصیل..." else "Cheque / Reference / Notes (Optional)", fontSize = 12.sp, color = Slate400) },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFF7C3AED)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ---------------------- TAB 6: TRANSFER FORM ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferQuickForm(
    state: AppState,
    isUrdu: Boolean,
    fromAccountId: String,
    onFromAccountIdChange: (String) -> Unit,
    toAccountId: String,
    onToAccountIdChange: (String) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    transferDate: String,
    onTransferDateChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit
) {
    val context = LocalContext.current
    var isFromMenuOpen by remember { mutableStateOf(false) }
    var isToMenuOpen by remember { mutableStateOf(false) }

    val fromAcc = state.bankAccounts.find { it.id == fromAccountId } ?: state.bankAccounts.firstOrNull()
    val toAcc = state.bankAccounts.find { it.id == toAccountId } ?: state.bankAccounts.getOrNull(1)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (isUrdu) "بینک اور کیش ٹرانسفر (Account Transfer)" else "Bank & Cash Transfer",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Slate800
            )

            // From Account
            ExposedDropdownMenuBox(
                expanded = isFromMenuOpen,
                onExpandedChange = { isFromMenuOpen = !isFromMenuOpen }
            ) {
                OutlinedTextField(
                    value = if (fromAcc != null) "${fromAcc.accountTitle} (${FormatUtils.formatCurrency(fromAcc.balance)})" else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isUrdu) "رقم بھیجنے والا اکاؤنٹ (From Account)" else "From Account") },
                    leadingIcon = { Icon(Icons.Default.Output, contentDescription = null, tint = RedDanger) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isFromMenuOpen) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = isFromMenuOpen, onDismissRequest = { isFromMenuOpen = false }) {
                    state.bankAccounts.forEach { acc ->
                        DropdownMenuItem(
                            text = {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(acc.accountTitle, fontWeight = FontWeight.Bold)
                                    Text(FormatUtils.formatCurrency(acc.balance), color = Slate600)
                                }
                            },
                            onClick = {
                                onFromAccountIdChange(acc.id)
                                isFromMenuOpen = false
                            }
                        )
                    }
                }
            }

            // To Account
            ExposedDropdownMenuBox(
                expanded = isToMenuOpen,
                onExpandedChange = { isToMenuOpen = !isToMenuOpen }
            ) {
                OutlinedTextField(
                    value = if (toAcc != null) "${toAcc.accountTitle} (${FormatUtils.formatCurrency(toAcc.balance)})" else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isUrdu) "وصول کنندہ اکاؤنٹ (To Account)" else "To Account") },
                    leadingIcon = { Icon(Icons.Default.Input, contentDescription = null, tint = DukanGreenHover) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isToMenuOpen) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = isToMenuOpen, onDismissRequest = { isToMenuOpen = false }) {
                    state.bankAccounts.forEach { acc ->
                        DropdownMenuItem(
                            text = {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(acc.accountTitle, fontWeight = FontWeight.Bold)
                                    Text(FormatUtils.formatCurrency(acc.balance), color = Slate600)
                                }
                            },
                            onClick = {
                                onToAccountIdChange(acc.id)
                                isToMenuOpen = false
                            }
                        )
                    }
                }
            }

            // Amount & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text(if (isUrdu) "منتقلی رقم (Rs.)" else "Transfer Amount (Rs.)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFF0284C7)
                    ),
                    modifier = Modifier.weight(1.1f)
                )

                OutlinedTextField(
                    value = transferDate,
                    onValueChange = onTransferDateChange,
                    label = { Text(if (isUrdu) "منتقلی تاریخ" else "Transfer Date") },
                    placeholder = { Text("YYYY-MM-DD", fontSize = 12.sp, color = Slate400) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                FormatUtils.showDatePickerDialog(context, transferDate) { selected ->
                                    onTransferDateChange(selected)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = if (isUrdu) "تاریخ منتخب کریں" else "Select Date",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFF0284C7)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text(if (isUrdu) "ٹرانسفر تفصیل / حوالہ" else "Reference / Transfer Remarks") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------- TAB 7: LOAN & ASSET QUICK FORM ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanAssetQuickForm(
    state: AppState,
    isUrdu: Boolean,
    partyName: String,
    onPartyNameChange: (String) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    direction: String,
    onDirectionChange: (String) -> Unit,
    accountId: String,
    onAccountIdChange: (String) -> Unit,
    loanDate: String,
    onLoanDateChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit
) {
    val context = LocalContext.current
    var isAccountMenuOpen by remember { mutableStateOf(false) }
    val selectedAcc = state.bankAccounts.find { it.id == accountId } ?: state.bankAccounts.firstOrNull()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (isUrdu) "قرضہ و اثاثہ جات اندراج" else "Loans & Assets Entry",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Slate800
            )

            // Direction Switcher: Given vs Taken
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate100, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Pair("GIVEN", if (isUrdu) "قرض دیا (Loan Given)" else "Loan Given"),
                    Pair("TAKEN", if (isUrdu) "قرض لیا (Loan Taken)" else "Loan Taken")
                ).forEach { (dir, label) ->
                    val isSel = direction == dir
                    val selColor = if (dir == "GIVEN") Color(0xFFD97706) else Color(0xFF0D9488)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) selColor else Color.Transparent)
                            .clickable { onDirectionChange(dir) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else Slate700
                        )
                    }
                }
            }

            // Party Name
            OutlinedTextField(
                value = partyName,
                onValueChange = onPartyNameChange,
                label = { Text(if (isUrdu) "فریق / شخص کا نام *" else "Person / Party Name *") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Amount & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text(if (isUrdu) "قرض کی رقم (Rs.) *" else "Loan Amount (Rs.) *") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFF0D9488)
                    ),
                    modifier = Modifier.weight(1.1f)
                )

                OutlinedTextField(
                    value = loanDate,
                    onValueChange = onLoanDateChange,
                    label = { Text(if (isUrdu) "قرض تاریخ" else "Loan Date") },
                    placeholder = { Text("YYYY-MM-DD", fontSize = 12.sp, color = Slate400) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                FormatUtils.showDatePickerDialog(context, loanDate) { selected ->
                                    onLoanDateChange(selected)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = if (isUrdu) "تاریخ منتخب کریں" else "Select Date",
                                tint = Color(0xFF0D9488),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Slate200,
                        focusedBorderColor = Color(0xFF0D9488)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Account Dropdown
            ExposedDropdownMenuBox(
                expanded = isAccountMenuOpen,
                onExpandedChange = { isAccountMenuOpen = !isAccountMenuOpen }
            ) {
                OutlinedTextField(
                    value = if (selectedAcc != null) "${selectedAcc.accountTitle} (${FormatUtils.formatCurrency(selectedAcc.balance)})" else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isUrdu) "متعلقہ اکاؤنٹ" else "Payment Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountMenuOpen) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = isAccountMenuOpen, onDismissRequest = { isAccountMenuOpen = false }) {
                    state.bankAccounts.forEach { acc ->
                        DropdownMenuItem(
                            text = {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(acc.accountTitle, fontWeight = FontWeight.Bold)
                                    Text(FormatUtils.formatCurrency(acc.balance), color = Slate600)
                                }
                            },
                            onClick = {
                                onAccountIdChange(acc.id)
                                isAccountMenuOpen = false
                            }
                        )
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text(if (isUrdu) "اضافی تفصیل / واپسی کی تاریخ" else "Remarks / Return Date") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------- QUICK ADD ITEM DIALOG ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddItemDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    isUrdu: Boolean,
    existingCategories: List<String>,
    onSaveItem: (InventoryItem) -> Unit
) {
    if (!isOpen) return

    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(existingCategories.firstOrNull() ?: "General") }
    var salePrice by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var stockQty by remember { mutableStateOf("10") }
    var unit by remember { mutableStateOf("Piece") }
    var barcode by remember { mutableStateOf("") }
    var isCatMenuOpen by remember { mutableStateOf(false) }
    var isUnitMenuOpen by remember { mutableStateOf(false) }

    val units = listOf("Piece", "Kg", "Gram", "Litre", "Packet", "Box", "Dozen", "Meter")

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(DukanGreenLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AddBusiness, contentDescription = null, tint = DukanGreenHover, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = if (isUrdu) "نیا پروڈکٹ شامل کریں" else "Add New Item",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Slate900
                        )
                    }

                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                    }
                }

                // Item Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isUrdu) "پروڈکٹ کا نام *" else "Product Name *") },
                    placeholder = { Text(if (isUrdu) "مثلاً باسمتی چاول یا پیپسی" else "e.g. Basmati Rice or Pepsi") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category & Unit Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Category Dropdown
                    ExposedDropdownMenuBox(
                        expanded = isCatMenuOpen,
                        onExpandedChange = { isCatMenuOpen = !isCatMenuOpen },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text(if (isUrdu) "کیٹیگری" else "Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCatMenuOpen) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = isCatMenuOpen, onDismissRequest = { isCatMenuOpen = false }) {
                            existingCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = { category = cat; isCatMenuOpen = false }
                                )
                            }
                        }
                    }

                    // Unit Dropdown
                    ExposedDropdownMenuBox(
                        expanded = isUnitMenuOpen,
                        onExpandedChange = { isUnitMenuOpen = !isUnitMenuOpen },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text(if (isUrdu) "اکائی" else "Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isUnitMenuOpen) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = isUnitMenuOpen, onDismissRequest = { isUnitMenuOpen = false }) {
                            units.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = { unit = u; isUnitMenuOpen = false }
                                )
                            }
                        }
                    }
                }

                // Prices: Sale Price & Cost Price
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = salePrice,
                        onValueChange = { salePrice = it },
                        label = { Text(if (isUrdu) "قیمت فروخت (Rs.) *" else "Sale Price *") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = costPrice,
                        onValueChange = { costPrice = it },
                        label = { Text(if (isUrdu) "قیمت خرید" else "Cost Price") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Stock Qty & Barcode
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockQty,
                        onValueChange = { stockQty = it },
                        label = { Text(if (isUrdu) "ابتدائی اسٹاک" else "Stock Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text(if (isUrdu) "بارکوڈ (اختیاری)" else "Barcode (Opt)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text(if (isUrdu) "منسوخ" else "Cancel", color = Slate600)
                    }

                    Button(
                        onClick = {
                            val sp = salePrice.toDoubleOrNull() ?: 0.0
                            val cp = costPrice.toDoubleOrNull() ?: (sp * 0.8)
                            val sq = stockQty.toDoubleOrNull() ?: 0.0
                            if (name.isBlank() || sp <= 0.0) {
                                return@Button
                            }
                            val newItem = InventoryItem(
                                id = "inv_${System.currentTimeMillis()}",
                                name = name.trim(),
                                category = category.ifBlank { "General" },
                                purchasePrice = cp,
                                salePrice = sp,
                                quantity = sq,
                                unit = unit.ifBlank { "Piece" },
                                barcode = barcode.ifBlank { null },
                                minStockAlert = 5.0
                            )
                            onSaveItem(newItem)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isUrdu) "محفوظ کریں" else "Save Item", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsQuickView(
    state: AppState,
    isUrdu: Boolean
) {
    val totalSales = remember(state.transactions) {
        state.transactions.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
    }
    val totalPurchases = remember(state.transactions) {
        state.transactions.filter { it.type == TransactionType.PURCHASE }.sumOf { it.amount }
    }
    val totalExpenses = remember(state.transactions) {
        state.transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }
    val grossProfit = totalSales - totalPurchases
    val netProfit = grossProfit - totalExpenses

    val totalCustomerCredit = remember(state.customers) {
        state.customers.sumOf { it.totalCredit }
    }
    val totalSupplierPayable = remember(state.suppliers) {
        state.suppliers.sumOf { it.totalPayable }
    }
    val totalCashBankBalance = remember(state.bankAccounts) {
        state.bankAccounts.sumOf { it.balance }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Executive Summary Cards (Profit, Sales)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = if (netProfit >= 0) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, if (netProfit >= 0) Color(0xFFBBF7D0) else Color(0xFFFECACA))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(if (isUrdu) "خالص منافع / نقصان" else "Net Profit / Loss", fontSize = 11.sp, color = Slate600)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        FormatUtils.formatCurrency(netProfit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (netProfit >= 0) DukanGreenHover else RedDanger
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(if (isUrdu) "کل سیلز" else "Total Sales", fontSize = 11.sp, color = Slate600)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        FormatUtils.formatCurrency(totalSales),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8)
                    )
                }
            }
        }

        // Purchases & Expenses
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(if (isUrdu) "کل خریداری" else "Total Purchases", fontSize = 11.sp, color = Slate600)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(FormatUtils.formatCurrency(totalPurchases), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(if (isUrdu) "کل اخراجات" else "Total Expenses", fontSize = 11.sp, color = Slate600)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(FormatUtils.formatCurrency(totalExpenses), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RedDanger)
                }
            }
        }

        // 2. Financial Position & Udhaar Snapshot
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                    Text(
                        text = if (isUrdu) "مالیاتی پوزیشن و ادھار کھاتے" else "Financial Health & Khata Balances",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate900
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isUrdu) "موجودہ کیش و بینک بیلنس:" else "Total Cash & Bank Balance:", fontSize = 11.sp, color = Slate600)
                    Text(FormatUtils.formatCurrency(totalCashBankBalance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isUrdu) "مارکیٹ ادھار وصولی (گاہکوں سے):" else "Receivable from Customers:", fontSize = 11.sp, color = Slate600)
                    Text(FormatUtils.formatCurrency(totalCustomerCredit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isUrdu) "سپلائرز کو واجب الادا رقم:" else "Payable to Suppliers:", fontSize = 11.sp, color = Slate600)
                    Text(FormatUtils.formatCurrency(totalSupplierPayable), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedDanger)
                }
            }
        }

        // 3. Proportional Breakdown Bar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PieChart, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                    Text(
                        text = if (isUrdu) "آمدن و اخراجات کا تقابل" else "Revenue & Expense Ratio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate900
                    )
                }

                val totalVol = (totalSales + totalPurchases + totalExpenses).coerceAtLeast(1.0)
                val salesPct = ((totalSales / totalVol) * 100).toInt()
                val purchasesPct = ((totalPurchases / totalVol) * 100).toInt()
                val expPct = ((totalExpenses / totalVol) * 100).toInt()

                QuickAnalyticsBarRow(if (isUrdu) "سیلز (Sales)" else "Sales", totalSales, salesPct, DukanGreenPrimary)
                QuickAnalyticsBarRow(if (isUrdu) "خریداری (Purchases)" else "Purchases", totalPurchases, purchasesPct, Color(0xFF2563EB))
                QuickAnalyticsBarRow(if (isUrdu) "اخراجات (Expenses)" else "Expenses", totalExpenses, expPct, RedDanger)
            }
        }
    }
}

@Composable
fun QuickAnalyticsBarRow(
    label: String,
    amount: Double,
    percent: Int,
    barColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
            Text(text = "${FormatUtils.formatCurrency(amount)} ($percent%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = barColor)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Slate100, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (percent / 100f).coerceIn(0.02f, 1f))
                    .background(barColor, RoundedCornerShape(4.dp))
            )
        }
    }
}
