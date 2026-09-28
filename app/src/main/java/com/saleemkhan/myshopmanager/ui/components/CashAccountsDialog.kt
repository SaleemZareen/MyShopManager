package com.saleemkhan.myshopmanager.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashAccountsDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    accounts: List<BankAccount>,
    transactions: List<Transaction>,
    onAddAccount: (BankAccount) -> Unit,
    onAddTransaction: (Transaction, targetAccId: String?, amountDelta: Double) -> Unit,
    onTransferFunds: (fromId: String, toId: String, amount: Double, notes: String) -> Unit,
    onAdjustBalance: (accountId: String, newBalance: Double, diff: Double, reason: String) -> Unit,
    isUrdu: Boolean
) {
    if (!isOpen) return

    BackHandler(enabled = isOpen) {
        onClose()
    }

    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0: VIEW, 1: ENTRY, 2: TRANSFER, 3: ADJUST, 4: ADD

    // Direct Entry State
    var entryAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var entryType by remember { mutableStateOf("RECEIPT") } // RECEIPT vs PAYMENT
    var entryAmount by remember { mutableStateOf("") }
    var entryDate by remember { mutableStateOf(FormatUtils.getTodayIsoDate()) }
    var entryCategory by remember { mutableStateOf("Capital Contribution") }
    var entryPartyName by remember { mutableStateOf("") }
    var entryNotes by remember { mutableStateOf("") }

    // Transfer State
    var fromAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var toAccountId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: "") }
    var transferAmount by remember { mutableStateOf("") }
    var transferNotes by remember { mutableStateOf("") }

    // Adjustment State
    var selectedAdjAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var newBalance by remember { mutableStateOf("") }
    var adjNotes by remember { mutableStateOf("") }

    // Add Account State
    var newBankName by remember { mutableStateOf("") }
    var newAccountTitle by remember { mutableStateOf("") }
    var newAccountNumber by remember { mutableStateOf("") }
    var newInitialBalance by remember { mutableStateOf("") }
    var newAccountType by remember { mutableStateOf("BANK") }

    val totalLiquidity = remember(accounts) { accounts.sumOf { it.balance } }

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
                // Top Header (Fullscreen App Bar)
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
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0xFFECFDF5), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = "Cash Accounts",
                                        tint = DukanGreenHover,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = if (isUrdu) "کیش اور بینک اکاؤنٹس" else "Cash & Bank Accounts",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "${if (isUrdu) "کل کاروبار کے فنڈز" else "Total Business Funds"}: ${FormatUtils.formatCurrency(totalLiquidity)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DukanGreenHover
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
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
                        }

                        // Scrollable Tab Bar (Matching React's VIEW, ENTRY, TRANSFER, ADJUST, + ADD)
                        val tabs = listOf(
                            Pair(0, if (isUrdu) "اکاؤنٹ سمری" else "Balances"),
                            Pair(1, if (isUrdu) "کیش / بینک انٹری" else "Direct Entry"),
                            Pair(2, if (isUrdu) "رقم منتقلی" else "Transfer"),
                            Pair(3, if (isUrdu) "بیلنس درستگی" else "Adjust"),
                            Pair(4, if (isUrdu) "نیا اکاؤنٹ" else "+ Account")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tabs.forEach { (index, title) ->
                                val isSelected = activeTab == index
                                Surface(
                                    onClick = { activeTab = index },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) DukanGreenPrimary else Color.White,
                                    border = BorderStroke(1.dp, if (isSelected) DukanGreenHover else Slate200),
                                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Slate700
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Scrollable Body Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (activeTab) {
                        // ==================== 0: VIEW (BALANCES & LEDGER) ====================
                        0 -> {
                            // Total Net Liquid Funds Card
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFFECFDF5), Color(0xFFF0FDF4), Color(0xFFD1FAE5))
                                            )
                                        )
                                        .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = if (isUrdu) "تمام اکاؤنٹس میں کل نقد رقم" else "Total Net Liquid Funds",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF065F46)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = FormatUtils.formatCurrency(totalLiquidity),
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DukanGreenHover
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.Default.AccountBalanceWallet,
                                                contentDescription = "Wallet",
                                                tint = DukanGreenHover,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                            }

                            // Accounts Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "دستیاب کھاتوں کی تفصیل (${accounts.size})" else "Available Accounts & Wallets (${accounts.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Slate800
                                )

                                Surface(
                                    onClick = { activeTab = 4 },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Slate100,
                                    border = BorderStroke(1.dp, Slate300)
                                ) {
                                    Text(
                                        text = if (isUrdu) "+ نیا اکاؤنٹ" else "+ Add",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate700,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // List of Account Cards
                            accounts.forEach { acc ->
                                val isCash = acc.type == "CASH"
                                val isWallet = acc.type == "WALLET"
                                val cardBg = if (isCash) Color(0xFFF0FDF4) else if (isWallet) Color(0xFFFAF5FF) else Color(0xFFEFF6FF)
                                val borderCol = if (isCash) Color(0xFFBBF7D0) else if (isWallet) Color(0xFFE9D5FF) else Color(0xFFBFDBFE)

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                    border = BorderStroke(1.dp, borderCol),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .background(
                                                        if (isCash) DukanGreenLight else if (isWallet) Color(0xFFF3E8FF) else Color(0xFFDBEAFE),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = when {
                                                        isCash -> Icons.Default.Wallet
                                                        isWallet -> Icons.Default.Smartphone
                                                        else -> Icons.Default.AccountBalance
                                                    },
                                                    contentDescription = acc.bankName,
                                                    tint = when {
                                                        isCash -> DukanGreenHover
                                                        isWallet -> Color(0xFF7C3AED)
                                                        else -> Color(0xFF2563EB)
                                                    },
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }

                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (isUrdu && acc.id == "acc_cash") "کیش کاؤنٹر (نقدی)" else acc.accountTitle,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.5.sp,
                                                        color = Slate900,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isCash) {
                                                        Surface(
                                                            color = DukanGreenHover,
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isUrdu) "دکان گلہ" else "Cash Galla",
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "${acc.bankName} ${if (acc.accountNumber.isNotBlank() && acc.accountNumber != "N/A") "• ${acc.accountNumber}" else ""}",
                                                    fontSize = 11.sp,
                                                    color = Slate500
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = FormatUtils.formatCurrency(acc.balance),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = if (acc.balance >= 0) Slate900 else RedDanger
                                            )
                                            Text(
                                                text = if (isUrdu) "موجودہ رقم" else "Available",
                                                fontSize = 10.sp,
                                                color = Slate400
                                            )
                                        }
                                    }
                                }
                            }

                            // Recent Cash & Bank Ledger Section
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null, tint = Slate500, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = if (isUrdu) "حالیہ کیش و بینک کھاتا ہسٹری" else "Recent Cash & Bank Ledger",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Slate800
                                        )
                                    }

                                    Surface(
                                        color = Slate100,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${transactions.size} ${if (isUrdu) "ٹوٹل اندراج" else "total"}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate600,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (transactions.isEmpty()) {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isUrdu) "کوئی لین دین درج نہیں ملا" else "No transactions recorded yet.",
                                                fontSize = 12.sp,
                                                color = Slate400,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                } else {
                                    val recentList = transactions.take(30)
                                    recentList.forEach { tx ->
                                        val isIncoming = tx.type == TransactionType.SALE || tx.type == TransactionType.RECEIPT || tx.type == TransactionType.LOAN_TAKEN

                                        val displayCategory = if (isUrdu) {
                                            when (tx.category) {
                                                "Capital Contribution" -> "ذاتی سرمایہ"
                                                "Personal Drawings" -> "ذاتی استعمال / ڈرائنگز"
                                                "Balance Reconciliation" -> "بیلنس درستگی"
                                                "Fund Transfer" -> "رقم منتقلی"
                                                "Shop Expense" -> "دکان کا خرچہ"
                                                "Supplier Payment" -> "سپلائر کی ادائیگی"
                                                "Cash Received from Customer" -> "گاہک کی وصولی"
                                                "Other Income" -> "دیگر آمدنی"
                                                "Miscellaneous Inflow" -> "متفرق آمدن"
                                                "Miscellaneous Outflow" -> "متفرق خرچہ"
                                                "Udhaar Collection" -> "ادھار وصولی"
                                                "Supplier Bill Payment" -> "سپلائر ادائیگی"
                                                else -> tx.category.ifBlank { "عام لین دین" }
                                            }
                                        } else {
                                            tx.category.ifBlank { "General Transaction" }
                                        }

                                        Card(
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    // Inflow / Outflow Icon Pill
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .background(
                                                                if (isIncoming) Color(0xFFECFDF5) else Color(0xFFFFF1F2),
                                                                RoundedCornerShape(10.dp)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                            contentDescription = null,
                                                            tint = if (isIncoming) DukanGreenHover else RedDanger,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = displayCategory,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.5.sp,
                                                                color = Slate900
                                                            )

                                                            if (!tx.partyName.isNullOrBlank()) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = Slate100
                                                                ) {
                                                                    Text(
                                                                        text = tx.partyName,
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = Slate600,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }

                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = Color(0xFFF1F5F9)
                                                            ) {
                                                                Text(
                                                                    text = tx.paymentMethod.name,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Slate500,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }

                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = FormatUtils.formatDate(tx.date),
                                                                fontSize = 10.5.sp,
                                                                color = Slate400
                                                            )
                                                            if (!tx.notes.isNullOrBlank()) {
                                                                Text(
                                                                    text = "• ${tx.notes}",
                                                                    fontSize = 10.5.sp,
                                                                    color = Slate500,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = "${if (isIncoming) "+" else "-"} ${FormatUtils.formatCurrency(tx.amount)}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    color = if (isIncoming) DukanGreenHover else Slate800
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ==================== 1: ENTRY (DIRECT CASH / BANK ENTRY) ====================
                        1 -> {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Slate200),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = if (isUrdu) "براہ راست کیش / بینک اندراج" else "Direct Manual Account Entry",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DukanGreenHover
                                    )

                                    // Account Selection Dropdown
                                    var isAccMenuOpen by remember { mutableStateOf(false) }
                                    val currentAcc = accounts.find { it.id == entryAccountId } ?: accounts.firstOrNull()

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isUrdu) "کون سا اکاؤنٹ منتخب کریں؟" else "Select Account",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Slate700
                                        )

                                        ExposedDropdownMenuBox(
                                            expanded = isAccMenuOpen,
                                            onExpandedChange = { isAccMenuOpen = !isAccMenuOpen }
                                        ) {
                                            OutlinedTextField(
                                                value = if (currentAcc != null) {
                                                    "${if (isUrdu && currentAcc.id == "acc_cash") "کیش کاؤنٹر (نقدی)" else currentAcc.accountTitle} (${FormatUtils.formatCurrency(currentAcc.balance)})"
                                                } else "",
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccMenuOpen) },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = Slate200,
                                                    focusedBorderColor = DukanGreenHover
                                                ),
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth()
                                            )

                                            ExposedDropdownMenu(
                                                expanded = isAccMenuOpen,
                                                onDismissRequest = { isAccMenuOpen = false }
                                            ) {
                                                accounts.forEach { acc ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = if (isUrdu && acc.id == "acc_cash") "کیش کاؤنٹر (نقدی)" else acc.accountTitle,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 12.sp
                                                                )
                                                                Text(
                                                                    text = FormatUtils.formatCurrency(acc.balance),
                                                                    fontSize = 11.sp,
                                                                    color = Slate500
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            entryAccountId = acc.id
                                                            isAccMenuOpen = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Transaction Type Toggle (Inflow vs Outflow)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isUrdu) "اندراج کی قسم (Inflow / Outflow)" else "Transaction Type",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Slate700
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Slate100, RoundedCornerShape(12.dp))
                                                .padding(3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Receipt Button
                                            Surface(
                                                onClick = {
                                                    entryType = "RECEIPT"
                                                    entryCategory = "Capital Contribution"
                                                },
                                                shape = RoundedCornerShape(9.dp),
                                                color = if (entryType == "RECEIPT") DukanGreenHover else Color.Transparent,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.ArrowDownward,
                                                        contentDescription = null,
                                                        tint = if (entryType == "RECEIPT") Color.White else Slate600,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isUrdu) "آمد / کیش آیا (Receipt)" else "Cash In (Receipt)",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (entryType == "RECEIPT") Color.White else Slate700
                                                    )
                                                }
                                            }

                                            // Payment Button
                                            Surface(
                                                onClick = {
                                                    entryType = "PAYMENT"
                                                    entryCategory = "Personal Drawings"
                                                },
                                                shape = RoundedCornerShape(9.dp),
                                                color = if (entryType == "PAYMENT") RedDanger else Color.Transparent,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.ArrowUpward,
                                                        contentDescription = null,
                                                        tint = if (entryType == "PAYMENT") Color.White else Slate600,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isUrdu) "خرچ / کیش گیا (Payment)" else "Cash Out (Payment)",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (entryType == "PAYMENT") Color.White else Slate700
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Amount and Date Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Amount
                                        Column(modifier = Modifier.weight(1.1f)) {
                                            Text(
                                                text = if (isUrdu) "رقم درج کریں (روپے):" else "Amount (Rs.):",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = Slate700,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            OutlinedTextField(
                                                value = entryAmount,
                                                onValueChange = { entryAmount = it },
                                                leadingIcon = {
                                                    Text(
                                                        text = "Rs.",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = Slate400,
                                                        modifier = Modifier.padding(start = 10.dp, end = 2.dp)
                                                    )
                                                },
                                                placeholder = { Text("0.00", fontSize = 15.sp, color = Slate400) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = Slate200,
                                                    focusedBorderColor = if (entryType == "RECEIPT") DukanGreenHover else RedDanger
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        // Date with Calendar Picker
                                        Column(modifier = Modifier.weight(0.9f)) {
                                            Text(
                                                text = if (isUrdu) "تاریخ منتخب کریں:" else "Date:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = Slate700,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            OutlinedTextField(
                                                value = entryDate,
                                                onValueChange = { entryDate = it },
                                                trailingIcon = {
                                                    IconButton(
                                                        onClick = {
                                                            FormatUtils.showDatePickerDialog(context, entryDate) { selected ->
                                                                entryDate = selected
                                                            }
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.CalendarToday,
                                                            contentDescription = "Select Date",
                                                            tint = if (entryType == "RECEIPT") DukanGreenHover else RedDanger,
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                    }
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = Slate200,
                                                    focusedBorderColor = if (entryType == "RECEIPT") DukanGreenHover else RedDanger
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    // Category Dropdown
                                    var isCatMenuOpen by remember { mutableStateOf(false) }
                                    val categories = if (entryType == "RECEIPT") {
                                        listOf(
                                            Pair("Capital Contribution", if (isUrdu) "Owner Capital / ذاتی سرمایہ یا سرمایہ کاری" else "Capital Contribution / Owner Investment"),
                                            Pair("Other Income", if (isUrdu) "Other Income / دیگر غیر کاروباری آمدنی" else "Other Income"),
                                            Pair("Cash Received from Customer", if (isUrdu) "Cash Received from Customer / گاہک سے نقدی وصولی" else "Cash Received from Customer"),
                                            Pair("Miscellaneous Inflow", if (isUrdu) "Miscellaneous / متفرق آمدن" else "Miscellaneous Inflow")
                                        )
                                    } else {
                                        listOf(
                                            Pair("Personal Drawings", if (isUrdu) "Personal Drawings / مالک کا ذاتی استعمال کے لیے گلے سے کیش نکالنا" else "Personal Drawings / Owner Usage"),
                                            Pair("Shop Expense", if (isUrdu) "Shop Expense / دکان کا خرچہ (مثلاً چائے، صفائی)" else "Shop Expense / Direct General Expense"),
                                            Pair("Supplier Payment", if (isUrdu) "Supplier Payment / سپلائر یا ہول سیلر کو کیش ادائیگی" else "Supplier Payment"),
                                            Pair("Miscellaneous Outflow", if (isUrdu) "Miscellaneous / متفرق خرچہ" else "Miscellaneous Outflow")
                                        )
                                    }

                                    val currentCatLabel = categories.find { it.first == entryCategory }?.second ?: categories.first().second

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isUrdu) "زمرہ (Category)" else "Category",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Slate700
                                        )

                                        ExposedDropdownMenuBox(
                                            expanded = isCatMenuOpen,
                                            onExpandedChange = { isCatMenuOpen = !isCatMenuOpen }
                                        ) {
                                            OutlinedTextField(
                                                value = currentCatLabel,
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCatMenuOpen) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth()
                                            )

                                            ExposedDropdownMenu(
                                                expanded = isCatMenuOpen,
                                                onDismissRequest = { isCatMenuOpen = false }
                                            ) {
                                                categories.forEach { (catKey, catLabel) ->
                                                    DropdownMenuItem(
                                                        text = { Text(catLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                                                        onClick = {
                                                            entryCategory = catKey
                                                            isCatMenuOpen = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Party Name (Optional)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isUrdu) "فرد / فریق کا نام (اختیاری)" else "Party Name (Optional)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Slate700
                                        )
                                        OutlinedTextField(
                                            value = entryPartyName,
                                            onValueChange = { entryPartyName = it },
                                            placeholder = { Text(if (isUrdu) "مثلاً: محمد سلیم، یا پارٹنر کا نام" else "e.g., Partner Name, Customer, Supplier", fontSize = 12.sp, color = Slate400) },
                                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Notes / Remarks (Optional)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isUrdu) "تفصیل / نوٹس (اختیاری)" else "Notes / Description (Optional)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Slate700
                                        )
                                        OutlinedTextField(
                                            value = entryNotes,
                                            onValueChange = { entryNotes = it },
                                            placeholder = { Text(if (isUrdu) "مثلاً: Rs. 50k ذاتی سرمایہ گلے میں ڈالا" else "e.g., Added Rs. 50k owner capital cash injection", fontSize = 12.sp, color = Slate400) },
                                            leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Submit Button
                                    Button(
                                        onClick = {
                                            val amt = entryAmount.toDoubleOrNull() ?: 0.0
                                            if (amt <= 0) {
                                                Toast.makeText(context, if (isUrdu) "براہ کرم درست رقم درج کریں" else "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            val targetAcc = accounts.find { it.id == entryAccountId } ?: accounts.firstOrNull()
                                            if (targetAcc == null) {
                                                Toast.makeText(context, if (isUrdu) "منتخب کردہ اکاؤنٹ نہیں ملا" else "Selected account not found", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            if (entryType == "PAYMENT" && targetAcc.balance < amt) {
                                                Toast.makeText(
                                                    context,
                                                    if (isUrdu) "اکاؤنٹ میں ناکافی بیلنس ہے۔ دستیاب رقم: ${FormatUtils.formatCurrency(targetAcc.balance)}"
                                                    else "Insufficient balance in account. Available: ${FormatUtils.formatCurrency(targetAcc.balance)}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@Button
                                            }

                                            val method = when (targetAcc.type) {
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

                                            val txType = if (entryType == "RECEIPT") TransactionType.RECEIPT else TransactionType.PAYMENT
                                            val delta = if (entryType == "RECEIPT") amt else -amt

                                            val tx = Transaction(
                                                id = "tx_${System.currentTimeMillis()}",
                                                type = txType,
                                                amount = amt,
                                                category = entryCategory,
                                                paymentMethod = method,
                                                partyName = entryPartyName.ifBlank { null },
                                                date = entryDate.ifBlank { FormatUtils.getTodayIsoDate() },
                                                notes = entryNotes.ifBlank { null }
                                            )

                                            onAddTransaction(tx, targetAcc.id, delta)

                                            Toast.makeText(
                                                context,
                                                if (isUrdu) "نیا کھاتا اندراج کامیابی سے درج کر دیا گیا ہے!" else "Direct ledger entry recorded successfully!",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            entryAmount = ""
                                            entryPartyName = ""
                                            entryNotes = ""
                                            activeTab = 0
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (entryType == "RECEIPT") DukanGreenHover else RedDanger
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isUrdu) "اندراج درج کریں" else "Record Direct Entry",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // ==================== 2: TRANSFER (INTER-ACCOUNT FUND TRANSFER) ====================
                        2 -> {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Slate200),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = if (isUrdu) "اکاؤنٹس کے درمیان رقم منتقل کریں" else "Transfer Funds Between Accounts",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DukanGreenHover
                                    )

                                    // From Account Picker
                                    var isFromMenuOpen by remember { mutableStateOf(false) }
                                    val fromAcc = accounts.find { it.id == fromAccountId } ?: accounts.firstOrNull()

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (isUrdu) "کہاں سے (منتقل کریں) - From" else "Transfer From", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                        ExposedDropdownMenuBox(
                                            expanded = isFromMenuOpen,
                                            onExpandedChange = { isFromMenuOpen = !isFromMenuOpen }
                                        ) {
                                            OutlinedTextField(
                                                value = "${fromAcc?.accountTitle ?: ""} (${FormatUtils.formatCurrency(fromAcc?.balance ?: 0.0)})",
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isFromMenuOpen) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = isFromMenuOpen,
                                                onDismissRequest = { isFromMenuOpen = false }
                                            ) {
                                                accounts.forEach { a ->
                                                    DropdownMenuItem(
                                                        text = { Text("${a.accountTitle} (${FormatUtils.formatCurrency(a.balance)})", fontSize = 12.sp) },
                                                        onClick = {
                                                            fromAccountId = a.id
                                                            isFromMenuOpen = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Direction Indicator Icon
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color(0xFFF1F5F9), CircleShape)
                                                .border(1.dp, Slate300, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = Slate600, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    // To Account Picker
                                    var isToMenuOpen by remember { mutableStateOf(false) }
                                    val toAcc = accounts.find { it.id == toAccountId } ?: accounts.getOrNull(1)

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (isUrdu) "کہاں کو (وصول کریں) - To" else "Transfer To", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                        ExposedDropdownMenuBox(
                                            expanded = isToMenuOpen,
                                            onExpandedChange = { isToMenuOpen = !isToMenuOpen }
                                        ) {
                                            OutlinedTextField(
                                                value = "${toAcc?.accountTitle ?: ""} (${FormatUtils.formatCurrency(toAcc?.balance ?: 0.0)})",
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isToMenuOpen) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = isToMenuOpen,
                                                onDismissRequest = { isToMenuOpen = false }
                                            ) {
                                                accounts.filter { it.id != fromAccountId }.forEach { a ->
                                                    DropdownMenuItem(
                                                        text = { Text("${a.accountTitle} (${FormatUtils.formatCurrency(a.balance)})", fontSize = 12.sp) },
                                                        onClick = {
                                                            toAccountId = a.id
                                                            isToMenuOpen = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Amount
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (isUrdu) "منتقل کرنے کی رقم (روپے)" else "Amount to Transfer", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                        OutlinedTextField(
                                            value = transferAmount,
                                            onValueChange = { transferAmount = it },
                                            leadingIcon = {
                                                Text(
                                                    text = "Rs.",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = Slate400,
                                                    modifier = Modifier.padding(start = 10.dp, end = 2.dp)
                                                )
                                            },
                                            placeholder = { Text("0.00", fontSize = 15.sp, color = Slate400) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Quick Amount Pills
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(500, 1000, 5000, 10000).forEach { pAmt ->
                                            Surface(
                                                onClick = { transferAmount = pAmt.toString() },
                                                shape = RoundedCornerShape(8.dp),
                                                color = Slate100,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                    Text("Rs. $pAmt", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                                }
                                            }
                                        }
                                    }

                                    // Notes
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (isUrdu) "اضافی نوٹس (تفصیل)" else "Optional Notes", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                        OutlinedTextField(
                                            value = transferNotes,
                                            onValueChange = { transferNotes = it },
                                            placeholder = { Text(if (isUrdu) "مثلاً: کیش کو بینک میں جمع کروایا" else "e.g., Deposited cash in bank", fontSize = 12.sp, color = Slate400) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Button
                                    Button(
                                        onClick = {
                                            val amt = transferAmount.toDoubleOrNull() ?: 0.0
                                            if (amt <= 0) {
                                                Toast.makeText(context, if (isUrdu) "براہ کرم درست رقم درج کریں" else "Please enter a valid transfer amount", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (fromAccountId == toAccountId) {
                                                Toast.makeText(context, if (isUrdu) "ارسال کنندہ اور وصول کنندہ اکاؤنٹ ایک نہیں ہو سکتے" else "Sender and receiver accounts cannot be the same", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val avail = fromAcc?.balance ?: 0.0
                                            if (amt > avail) {
                                                Toast.makeText(
                                                    context,
                                                    if (isUrdu) "ارسال کنندہ اکاؤنٹ میں ناکافی بیلنس ہے۔ دستیاب رقم: ${FormatUtils.formatCurrency(avail)}"
                                                    else "Insufficient balance in sender account. Available: ${FormatUtils.formatCurrency(avail)}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@Button
                                            }

                                            onTransferFunds(fromAccountId, toAccountId, amt, transferNotes)
                                            Toast.makeText(context, if (isUrdu) "رقم کامیابی سے منتقل ہو گئی ہے!" else "Funds transferred successfully!", Toast.LENGTH_SHORT).show()
                                            transferAmount = ""
                                            transferNotes = ""
                                            activeTab = 0
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isUrdu) "رقم منتقل کریں" else "Confirm Transfer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        // ==================== 3: ADJUST (RECONCILIATION / بیلنس درستگی) ====================
                        3 -> {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Slate200),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Important Note Banner
                                    Surface(
                                        color = Color(0xFFFFFBEB),
                                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = if (isUrdu) "اہم نوٹ (Important Note):" else "Important Note:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF92400E)
                                            )
                                            Text(
                                                text = if (isUrdu) {
                                                    "دکان کا ذاتی سرمایہ (Owner Capital) داخل کرنے کے لیے یا کیش رجسٹر کا بیلنس ایڈجسٹ کرنے کے لیے یہاں نیا بیلنس درج کریں۔ مثال کے طور پر، اگر آپ کے پاس 0 روپے ہیں اور آپ 50,000 روپے ذاتی سرمایہ داخل کرنا چاہتے ہیں، تو یہاں 50000 درج کریں اور نوٹس میں 'ذاتی سرمایہ / Owner Capital' لکھیں۔"
                                                } else {
                                                    "To introduce Owner Capital or adjust your cash drawer balance, specify the target balance here. For example, if current cash is 0 and you want to invest Rs. 50,000, enter 50000 and write 'Owner Capital' in notes."
                                                },
                                                fontSize = 11.sp,
                                                color = Color(0xFF78350F),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }

                                    // Account selection
                                    var isAdjMenuOpen by remember { mutableStateOf(false) }
                                    val targetAdjAcc = accounts.find { it.id == selectedAdjAccountId } ?: accounts.firstOrNull()

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isUrdu) "کون سے اکاؤنٹ کا بیلنس درست کرنا ہے؟" else "Account to Reconcile",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Slate700
                                        )

                                        ExposedDropdownMenuBox(
                                            expanded = isAdjMenuOpen,
                                            onExpandedChange = { isAdjMenuOpen = !isAdjMenuOpen }
                                        ) {
                                            OutlinedTextField(
                                                value = if (targetAdjAcc != null) {
                                                    "${if (isUrdu && targetAdjAcc.id == "acc_cash") "کیش کاؤنٹر (نقدی)" else targetAdjAcc.accountTitle} (${FormatUtils.formatCurrency(targetAdjAcc.balance)})"
                                                } else "",
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAdjMenuOpen) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth()
                                            )

                                            ExposedDropdownMenu(
                                                expanded = isAdjMenuOpen,
                                                onDismissRequest = { isAdjMenuOpen = false }
                                            ) {
                                                accounts.forEach { acc ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = if (isUrdu && acc.id == "acc_cash") "کیش کاؤنٹر (نقدی)" else acc.accountTitle,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 12.sp
                                                                )
                                                                Text(
                                                                    text = FormatUtils.formatCurrency(acc.balance),
                                                                    fontSize = 11.sp,
                                                                    color = Slate500
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            selectedAdjAccountId = acc.id
                                                            isAdjMenuOpen = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // New Balance input
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isUrdu) "نیا درست بیلنس درج کریں (روپے)" else "Actual Current Balance",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Slate700
                                        )
                                        OutlinedTextField(
                                            value = newBalance,
                                            onValueChange = { newBalance = it },
                                            leadingIcon = {
                                                Text(
                                                    text = "Rs.",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = Slate400,
                                                    modifier = Modifier.padding(start = 10.dp, end = 2.dp)
                                                )
                                            },
                                            placeholder = { Text("0.00", fontSize = 15.sp, color = Slate400) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Slate200,
                                                focusedBorderColor = AmberWarning
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Adjustment Notes
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isUrdu) "درستگی کی وجہ / نوٹس" else "Reconciliation Reason",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Slate700
                                        )
                                        OutlinedTextField(
                                            value = adjNotes,
                                            onValueChange = { adjNotes = it },
                                            placeholder = { Text(if (isUrdu) "مثلاً: روزانہ کیش کی گنتی کے بعد درستگی" else "e.g., End of day physical cash count", fontSize = 12.sp, color = Slate400) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Action Button
                                    Button(
                                        onClick = {
                                            val targetBal = newBalance.toDoubleOrNull()
                                            if (targetBal == null || targetBal < 0) {
                                                Toast.makeText(context, if (isUrdu) "برائے مہربانی درست بیلنس درج کریں" else "Please enter a valid balance", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            val targetAcc = accounts.find { it.id == selectedAdjAccountId } ?: accounts.firstOrNull()
                                            if (targetAcc == null) {
                                                Toast.makeText(context, if (isUrdu) "منتخب کردہ اکاؤنٹ نہیں ملا" else "Selected account not found", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            val diff = targetBal - targetAcc.balance
                                            if (diff == 0.0) {
                                                Toast.makeText(context, if (isUrdu) "نیا بیلنس موجودہ بیلنس کے برابر ہے" else "New balance matches current balance", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            onAdjustBalance(targetAcc.id, targetBal, diff, adjNotes)

                                            Toast.makeText(context, if (isUrdu) "بیلنس کامیابی سے اپ ڈیٹ کر دیا گیا ہے!" else "Balance adjusted successfully!", Toast.LENGTH_SHORT).show()
                                            newBalance = ""
                                            adjNotes = ""
                                            activeTab = 0
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Adjust")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isUrdu) "بیلنس درست کریں" else "Apply Adjustment",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // ==================== 4: + ADD (ADD NEW ACCOUNT) ====================
                        4 -> {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Slate200),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = if (isUrdu) "نیا کیش، بینک یا والٹ اکاؤنٹ شامل کریں" else "Add New Account",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DukanGreenHover
                                    )

                                    // Account Type Selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val types = listOf(
                                            Pair("BANK", if (isUrdu) "بینک اکاؤنٹ" else "Bank"),
                                            Pair("WALLET", if (isUrdu) "جاز کیش / ایزی پیسہ" else "Wallet"),
                                            Pair("CASH", if (isUrdu) "کیش بکس / گلہ" else "Cash Box")
                                        )

                                        types.forEach { (tKey, tLabel) ->
                                            val isSelected = newAccountType == tKey
                                            Surface(
                                                onClick = { newAccountType = tKey },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) DukanGreenLight else Slate50,
                                                border = BorderStroke(1.dp, if (isSelected) GreenSuccessBorder else Slate200),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = tLabel,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) DukanGreenHover else Slate700
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = newBankName,
                                        onValueChange = { newBankName = it },
                                        label = { Text(if (isUrdu) "بینک یا سروس کا نام (مثلاً میزان بینک، ایزی پیسہ)" else "Bank / Provider Name") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = newAccountTitle,
                                        onValueChange = { newAccountTitle = it },
                                        label = { Text(if (isUrdu) "اکاؤنٹ ٹائٹل (مثلاً دکان اکاؤنٹ / محمد سلیم)" else "Account Title") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = newAccountNumber,
                                        onValueChange = { newAccountNumber = it },
                                        label = { Text(if (isUrdu) "اکاؤنٹ یا موبائل نمبر" else "Account / Phone Number") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = newInitialBalance,
                                        onValueChange = { newInitialBalance = it },
                                        label = { Text(if (isUrdu) "ابتدائی بیلنس (روپے)" else "Opening Balance (Rs.)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Button(
                                        onClick = {
                                            if (newAccountTitle.isBlank() || newBankName.isBlank()) {
                                                Toast.makeText(context, if (isUrdu) "براہ کرم تمام خانے پُر کریں" else "Please fill required fields", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val openBal = newInitialBalance.toDoubleOrNull() ?: 0.0
                                            val newAcc = BankAccount(
                                                id = "acc_${System.currentTimeMillis()}",
                                                accountTitle = newAccountTitle,
                                                bankName = newBankName,
                                                accountNumber = newAccountNumber.ifBlank { "N/A" },
                                                type = newAccountType,
                                                balance = openBal
                                            )
                                            onAddAccount(newAcc)
                                            Toast.makeText(context, if (isUrdu) "نیا اکاؤنٹ شامل کر دیا گیا" else "Account added successfully", Toast.LENGTH_SHORT).show()
                                            newBankName = ""
                                            newAccountTitle = ""
                                            newAccountNumber = ""
                                            newInitialBalance = ""
                                            activeTab = 0
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircle, contentDescription = "Add")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isUrdu) "اکاؤنٹ محفوظ کریں" else "Save Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
