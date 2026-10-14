package com.saleemkhan.myshopmanager.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saleemkhan.myshopmanager.data.accounting.AccountingEngine
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils

enum class TransactionDateFilter {
    ALL,
    TODAY,
    YESTERDAY,
    THIS_MONTH,
    LAST_MONTH,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: AppState,
    isUrdu: Boolean,
    initialTypeFilter: TransactionType? = null,
    onOpenQuickEntry: (TransactionType) -> Unit,
    onDeleteTransaction: (String) -> Boolean, // returns false if blocked by stock safety
    onRestoreTransaction: (String) -> Unit,
    onUpdateTransaction: (String, PartialTransactionData) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var filterType by remember(initialTypeFilter) { mutableStateOf<TransactionType?>(initialTypeFilter) }
    var flowFilter by remember(initialTypeFilter) { mutableStateOf<String?>(null) } // "INFLOW" or "OUTFLOW" or null
    var filterDateRange by remember { mutableStateOf(TransactionDateFilter.ALL) }
    var customStartDate by remember { mutableStateOf("") }
    var customEndDate by remember { mutableStateOf("") }

    var collapsedGroups by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var selectedReceiptUrl by remember { mutableStateOf<String?>(null) }
    var isTrashOpen by remember { mutableStateOf(false) }
    var stockBlockAlertMessage by remember { mutableStateOf<String?>(null) }

    // Base transactions matching the date filter
    val dateFilteredTransactions = remember(
        state.transactions,
        filterDateRange,
        customStartDate,
        customEndDate
    ) {
        state.transactions.filter { tx ->
            AccountingEngine.matchesTransactionDateFilter(
                txDateStr = tx.date,
                filter = filterDateRange,
                customStart = customStartDate,
                customEnd = customEndDate
            )
        }
    }

    // Totals for Inflow & Outflow tabs based on current date range (Excludes credit sales/purchases)
    val cashFlowMetrics = remember(dateFilteredTransactions) {
        AccountingEngine.calculateCashFlow(dateFilteredTransactions)
    }
    val totalInflow = cashFlowMetrics.cashInflow
    val totalOutflow = cashFlowMetrics.cashOutflow

    val inflowCount = remember(dateFilteredTransactions) {
        dateFilteredTransactions.count { (it.type == TransactionType.SALE && it.paymentMethod != PaymentMethod.CREDIT) || it.type == TransactionType.RECEIPT || it.type == TransactionType.LOAN_TAKEN }
    }

    val outflowCount = remember(dateFilteredTransactions) {
        dateFilteredTransactions.count { (it.type == TransactionType.PURCHASE && it.paymentMethod != PaymentMethod.CREDIT) || it.type == TransactionType.EXPENSE || it.type == TransactionType.PAYMENT || it.type == TransactionType.LOAN_GIVEN }
    }

    // Filtered transactions incorporating flow filter, type filter and search
    val filteredTransactions = remember(
        dateFilteredTransactions,
        search,
        filterType,
        flowFilter
    ) {
        dateFilteredTransactions.filter { tx ->
            val matchesFlow = when (flowFilter) {
                "INFLOW" -> (tx.type == TransactionType.SALE && tx.paymentMethod != PaymentMethod.CREDIT) || tx.type == TransactionType.RECEIPT || tx.type == TransactionType.LOAN_TAKEN
                "OUTFLOW" -> (tx.type == TransactionType.PURCHASE && tx.paymentMethod != PaymentMethod.CREDIT) || tx.type == TransactionType.EXPENSE || tx.type == TransactionType.PAYMENT || tx.type == TransactionType.LOAN_GIVEN
                else -> true
            }

            val matchesType = filterType == null || tx.type == filterType

            val matchesSearch = search.isBlank() ||
                    tx.category.contains(search, ignoreCase = true) ||
                    (tx.partyName?.contains(search, ignoreCase = true) == true) ||
                    (tx.notes?.contains(search, ignoreCase = true) == true) ||
                    tx.paymentMethod.name.contains(search, ignoreCase = true) ||
                    tx.amount.toString().contains(search)

            matchesFlow && matchesType && matchesSearch
        }
    }

    // Group transactions by date
    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { tx ->
            FormatUtils.formatDate(tx.date)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. TOP HEADER (Centered like React app with + New Entry button)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = "Journal",
                        tint = DukanGreenHover,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isUrdu) "روزنامچہ / لین دین (Journal Entries)" else "Daily Journal & Transactions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Slate900
                    )
                }

                Text(
                    text = if (isUrdu) "تمام فروخت، خریداری، اور دکان کے اخراجات کا تاریخ وار ریکارڈ" else "Complete ledger of all shop sales, purchases & expenses",
                    fontSize = 11.sp,
                    color = Slate500,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = { onOpenQuickEntry(TransactionType.SALE) },
                    colors = ButtonDefaults.buttonColors(containerColor = DukanGreenHover),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isUrdu) "نیا اندراج (New Entry)" else "New Entry",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. TOTAL INFLOW & TOTAL OUTFLOW TABS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isInflowActive = flowFilter == "INFLOW"
            val isOutflowActive = flowFilter == "OUTFLOW"

            // Total Inflow Tab
            Card(
                onClick = {
                    flowFilter = if (isInflowActive) null else "INFLOW"
                    if (flowFilter != null) filterType = null
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isInflowActive) Color(0xFFECFDF5) else Color.White
                ),
                border = BorderStroke(
                    width = if (isInflowActive) 2.dp else 1.dp,
                    color = if (isInflowActive) DukanGreenHover else Color(0xFFD1FAE5)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD1FAE5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "Inflow",
                                    tint = DukanGreenHover,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (isUrdu) "ٹوٹل آمدن" else "Total Inflow",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700
                            )
                        }
                        if (isInflowActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DukanGreenHover
                            ) {
                                Text(
                                    text = if (isUrdu) "فعال" else "Active",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = FormatUtils.formatMoney(totalInflow),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DukanGreenHover
                    )

                    Text(
                        text = if (isUrdu) "$inflowCount اندراجات (فروخت و وصولی)" else "$inflowCount entries (Sales & Receipts)",
                        fontSize = 10.sp,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Total Outflow Tab
            Card(
                onClick = {
                    flowFilter = if (isOutflowActive) null else "OUTFLOW"
                    if (flowFilter != null) filterType = null
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOutflowActive) Color(0xFFFFF1F2) else Color.White
                ),
                border = BorderStroke(
                    width = if (isOutflowActive) 2.dp else 1.dp,
                    color = if (isOutflowActive) RedDanger else Color(0xFFFFE4E6)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFE4E6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = "Outflow",
                                    tint = RedDanger,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (isUrdu) "ٹوٹل خرچ" else "Total Outflow",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700
                            )
                        }
                        if (isOutflowActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = RedDanger
                            ) {
                                Text(
                                    text = if (isUrdu) "فعال" else "Active",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = FormatUtils.formatMoney(totalOutflow),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RedDanger
                    )

                    Text(
                        text = if (isUrdu) "$outflowCount اندراجات (خریداری و اخراجات)" else "$outflowCount entries (Purchases & Expenses)",
                        fontSize = 10.sp,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. SEARCH TRANSACTIONS BOX
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = {
                Text(
                    text = if (isUrdu) "ٹرانزیکشنز تلاش کریں (پارٹی، رقم یا تفصیل)..." else "Search transactions (party, amount, details)...",
                    fontSize = 12.sp,
                    color = Slate400
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = DukanGreenHover,
                    modifier = Modifier.size(19.dp)
                )
            },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = DukanGreenPrimary,
                unfocusedBorderColor = Slate200
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 4. TYPE FILTER PILLS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filterOptions = listOf(
                null to (if (isUrdu) "تمام" else "All"),
                TransactionType.SALE to (if (isUrdu) "فروخت" else "Sales"),
                TransactionType.PURCHASE to (if (isUrdu) "خریداری" else "Purchases"),
                TransactionType.EXPENSE to (if (isUrdu) "اخراجات" else "Expenses"),
                TransactionType.RECEIPT to (if (isUrdu) "وصولی (آمدن)" else "Receipts"),
                TransactionType.PAYMENT to (if (isUrdu) "ادائیگی (خرچ)" else "Payments")
            )

            filterOptions.forEach { (type, label) ->
                val isSelected = (filterType == type) && (type != null || flowFilter == null)
                Surface(
                    onClick = {
                        filterType = type
                        flowFilter = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) DukanGreenHover else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) DukanGreenHover else Slate200)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Slate700,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 3. SUB-CONTROLS STRIP (Date Filter, Collapse All, Trash Bin)
        Surface(
            color = Color(0xFFF1F5F9),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date Filter Selector
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (isUrdu) "تاریخ:" else "Date:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)

                        var isDateDropdownOpen by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                onClick = { isDateDropdownOpen = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate200)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = when (filterDateRange) {
                                            TransactionDateFilter.ALL -> if (isUrdu) "تمام وقت" else "All Time"
                                            TransactionDateFilter.TODAY -> if (isUrdu) "آج" else "Today"
                                            TransactionDateFilter.YESTERDAY -> if (isUrdu) "کل" else "Yesterday"
                                            TransactionDateFilter.THIS_MONTH -> if (isUrdu) "موجودہ مہینہ" else "This Month"
                                            TransactionDateFilter.LAST_MONTH -> if (isUrdu) "پچھلا مہینہ" else "Last Month"
                                            TransactionDateFilter.CUSTOM -> if (isUrdu) "مخصوص تاریخ" else "Custom Range"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate800
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }

                            DropdownMenu(
                                expanded = isDateDropdownOpen,
                                onDismissRequest = { isDateDropdownOpen = false }
                            ) {
                                TransactionDateFilter.values().forEach { dFilter ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (dFilter) {
                                                    TransactionDateFilter.ALL -> if (isUrdu) "تمام وقت" else "All Time"
                                                    TransactionDateFilter.TODAY -> if (isUrdu) "آج" else "Today"
                                                    TransactionDateFilter.YESTERDAY -> if (isUrdu) "کل" else "Yesterday"
                                                    TransactionDateFilter.THIS_MONTH -> if (isUrdu) "موجودہ مہینہ" else "This Month"
                                                    TransactionDateFilter.LAST_MONTH -> if (isUrdu) "پچھلا مہینہ" else "Last Month"
                                                    TransactionDateFilter.CUSTOM -> if (isUrdu) "مخصوص تاریخ..." else "Custom Range..."
                                                },
                                                fontSize = 11.sp
                                            )
                                        },
                                        onClick = {
                                            filterDateRange = dFilter
                                            isDateDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Right Side: Expand/Collapse All and Trash Bin
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Collapse / Expand All toggle
                        val allKeys = groupedTransactions.keys
                        val isAllCollapsed = allKeys.isNotEmpty() && allKeys.all { collapsedGroups[it] == true }

                        Surface(
                            onClick = {
                                val nextMap = if (isAllCollapsed) {
                                    emptyMap()
                                } else {
                                    allKeys.associateWith { true }
                                }
                                collapsedGroups = nextMap
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Text(
                                text = if (isAllCollapsed) (if (isUrdu) "سب کھولیں" else "Expand All") else (if (isUrdu) "سب بند کریں" else "Collapse All"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Trash Bin Button
                        Surface(
                            onClick = { isTrashOpen = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Trash", tint = RedDanger, modifier = Modifier.size(13.dp))
                                Text(
                                    text = if (isUrdu) "حذف شدہ" else "Trash",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDanger
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = RedDanger
                                ) {
                                    Text(
                                        text = "${state.deletedTransactions.size}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // If Custom Date Range selected:
                if (filterDateRange == TransactionDateFilter.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = customStartDate,
                            onValueChange = { customStartDate = it },
                            placeholder = { Text("YYYY-MM-DD", fontSize = 10.sp) },
                            label = { Text(if (isUrdu) "آغاز تاریخ" else "Start Date", fontSize = 10.sp) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        FormatUtils.showDatePickerDialog(context, customStartDate) { selected ->
                                            customStartDate = selected
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = "Start Date",
                                        tint = Slate600,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customEndDate,
                            onValueChange = { customEndDate = it },
                            placeholder = { Text("YYYY-MM-DD", fontSize = 10.sp) },
                            label = { Text(if (isUrdu) "اختتام تاریخ" else "End Date", fontSize = 10.sp) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        FormatUtils.showDatePickerDialog(context, customEndDate) { selected ->
                                            customEndDate = selected
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = "End Date",
                                        tint = Slate600,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4. GROUPED DAILY TRANSACTIONS LIST
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Empty",
                        tint = Slate300,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (isUrdu) "کوئی اینٹری نہیں ملی" else "No matching transactions found",
                        fontSize = 13.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                groupedTransactions.forEach { (dateLabel, txList) ->
                    val isCollapsed = collapsedGroups[dateLabel] == true

                    item(key = "header_$dateLabel") {
                        // Date Group Collapsible Header
                        Surface(
                            onClick = {
                                collapsedGroups = collapsedGroups.toMutableMap().apply {
                                    put(dateLabel, !isCollapsed)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = DukanGreenHover,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = DukanGreenHover,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = dateLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Slate900
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Slate200)
                                    ) {
                                        Text(
                                            text = "${txList.size} ${if (isUrdu) "اینٹریز" else "entries"}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate600,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = if (isCollapsed) (if (isUrdu) "دیکھیں" else "Expand") else (if (isUrdu) "بند کریں" else "Collapse"),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DukanGreenHover
                                )
                            }
                        }
                    }

                    if (!isCollapsed) {
                        items(txList, key = { it.id }) { tx ->
                            val isGreen = tx.type == TransactionType.SALE || tx.type == TransactionType.RECEIPT
                            val isIndigo = tx.type == TransactionType.PURCHASE || tx.type == TransactionType.PAYMENT
                            val isLoanTx = tx.type == TransactionType.LOAN_TAKEN || (tx.type == TransactionType.PAYMENT && tx.category == "LOAN_REPAYMENT")
                            val needsClassification = isLoanTx && tx.loanAccountId.isNullOrBlank()

                            val typeTitle = when (tx.type) {
                                TransactionType.SALE -> if (isUrdu) "فروخت" else "Sale"
                                TransactionType.PURCHASE -> if (isUrdu) "خریداری" else "Purchase"
                                TransactionType.EXPENSE -> if (isUrdu) "اخراجات" else "Expense"
                                TransactionType.RECEIPT -> if (isUrdu) "وصولی" else "Receipt"
                                TransactionType.PAYMENT -> if (isUrdu) "ادائیگی" else "Payment"
                                else -> tx.type.name
                            }

                            val typeColor = when {
                                isGreen -> DukanGreenHover
                                isIndigo -> Color(0xFF1D4ED8)
                                else -> RedDanger
                            }

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // IN-CARD DELETE CONFIRMATION BOX (Exact to React TransactionsModule)
                                    if (transactionToDelete?.id == tx.id) {
                                        Surface(
                                            color = Color(0xFFFEF2F2),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = RedDanger, modifier = Modifier.size(16.dp))
                                                    Text(
                                                        text = if (isUrdu) "اینٹری حذف کریں؟" else "Delete Entry?",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF991B1B)
                                                    )
                                                }
                                                Text(
                                                    text = if (isUrdu) "کیا آپ واقعی اس اندراج کو کوڑا دان میں منتقل کرنا چاہتے ہیں؟" else "Are you sure you want to move this transaction to trash?",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFB91C1C)
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = { transactionToDelete = null },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Slate700),
                                                        border = BorderStroke(1.dp, Slate300),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text(if (isUrdu) "کینسل" else "Cancel", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            val success = onDeleteTransaction(tx.id)
                                                            if (!success) {
                                                                stockBlockAlertMessage = if (isUrdu) {
                                                                    "خرابی: یہ خریداری ڈیلیٹ نہیں کی جا سکتی کیونکہ اس سے اسٹاک منفی (Zero سے کم) ہو جائے گا۔ پہلے فروخت کو ریورس کریں۔"
                                                                } else {
                                                                    "Error: Cannot delete purchase. Doing so would result in negative stock. Please reverse sales first."
                                                                }
                                                            }
                                                            transactionToDelete = null
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text(if (isUrdu) "حذف کریں" else "Delete", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Line 1: Type : Sales
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isUrdu) "قسم" else "Type",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate500,
                                                modifier = Modifier.width(64.dp)
                                            )
                                            Text(text = ":  ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400)
                                            Text(
                                                text = typeTitle,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = typeColor
                                            )
                                        }

                                        if (needsClassification) {
                                            Surface(
                                                color = Color(0xFFFEF2F2),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color(0xFFFECACA))
                                            ) {
                                                Text(
                                                    text = if (isUrdu) "درجہ بندی درکار ہے" else "Needs loan classification",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RedDanger,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Line 2: Party/Details : Party Name
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = if (isUrdu) "پارٹی / تفصیل" else "Party/Details",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate500,
                                            modifier = Modifier.width(64.dp)
                                        )
                                        Text(text = ":  ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = tx.partyName ?: tx.category,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Slate900
                                                )
                                                if (!tx.invoiceNo.isNullOrBlank()) {
                                                    Surface(color = Slate100, shape = RoundedCornerShape(4.dp)) {
                                                        Text("#${tx.invoiceNo}", fontSize = 9.sp, color = Slate600, modifier = Modifier.padding(horizontal = 3.dp))
                                                    }
                                                }
                                            }
                                            if (!tx.notes.isNullOrBlank()) {
                                                Text(
                                                    text = tx.notes,
                                                    fontSize = 10.sp,
                                                    color = Slate600,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // Line 3: Payment/Type : Cash
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isUrdu) "طریقہ ادائیگی" else "Payment/Type",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate500,
                                            modifier = Modifier.width(64.dp)
                                        )
                                        Text(text = ":  ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400)
                                        Text(
                                            text = when (tx.paymentMethod) {
                                                PaymentMethod.CASH -> if (isUrdu) "کیش (نقد)" else "Cash"
                                                PaymentMethod.BANK -> if (isUrdu) "بینک ٹرانسفر" else "Bank Transfer"
                                                PaymentMethod.CREDIT -> if (isUrdu) "ادھار (کھاتہ)" else "Credit / Khata"
                                                PaymentMethod.EASYPAISA -> "EasyPaisa"
                                                PaymentMethod.JAZZCASH -> "JazzCash"
                                                else -> tx.paymentMethod.name
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Slate700
                                        )
                                    }

                                    // Line 4: Date & Amount : 25-Sep-26 04:30 PM : +Rs. 3000 + Action Buttons
                                    Divider(color = Slate100, modifier = Modifier.padding(vertical = 2.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isUrdu) "تاریخ اور رقم" else "Date & Amount",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate500,
                                                modifier = Modifier.width(64.dp)
                                            )
                                            Text(text = ":  ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400)
                                            Text(
                                                text = FormatUtils.formatFullDateTime(tx.date),
                                                fontSize = 10.sp,
                                                color = Slate500
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${if (isGreen) "+" else "-"}${FormatUtils.formatCurrency(tx.amount)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = typeColor
                                            )
                                        }

                                        // Action Icons (Receipt Image, Edit, Delete)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            // Receipt Image Viewer button
                                            if (!tx.receiptImageUrl.isNullOrBlank()) {
                                                IconButton(
                                                    onClick = { selectedReceiptUrl = tx.receiptImageUrl },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.CameraAlt, contentDescription = "Receipt", tint = DukanGreenHover, modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            // Edit button
                                            IconButton(
                                                onClick = { onEditTransaction(tx) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                            }

                                            // Delete button
                                            IconButton(
                                                onClick = { transactionToDelete = tx },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = RedDanger, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    // Needs Loan Classification quick dropdown
                                    if (needsClassification && state.loans.isNotEmpty()) {
                                        var isLoanMenuOpen by remember { mutableStateOf(false) }
                                        Surface(
                                            color = Color(0xFFFFFBEB),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { isLoanMenuOpen = true }
                                                    .padding(6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isUrdu) "قرضہ اکاؤنٹ سے منسلک کریں..." else "Link to a loan account...",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF92400E)
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF92400E), modifier = Modifier.size(16.dp))
                                            }

                                            DropdownMenu(
                                                expanded = isLoanMenuOpen,
                                                onDismissRequest = { isLoanMenuOpen = false }
                                            ) {
                                                state.loans.forEach { loan ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text("${loan.lenderOrBorrower} (${loan.type})", fontSize = 11.sp)
                                                        },
                                                        onClick = {
                                                            onUpdateTransaction(
                                                                tx.id,
                                                                PartialTransactionData(
                                                                    loanAccountId = loan.id,
                                                                    partyName = loan.lenderOrBorrower
                                                                )
                                                            )
                                                            isLoanMenuOpen = false
                                                        }
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
            }
        }
    }

    // 5. RECEIPT IMAGE VIEWER DIALOG
    if (selectedReceiptUrl != null) {
        Dialog(onDismissRequest = { selectedReceiptUrl = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isUrdu) "منسلکہ رسید" else "Attached Receipt", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { selectedReceiptUrl = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Slate100, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = "Receipt Image", tint = Slate400, modifier = Modifier.size(64.dp))
                    }
                }
            }
        }
    }

    // 6. STOCK PROTECTION BLOCK ALERT DIALOG
    if (stockBlockAlertMessage != null) {
        AlertDialog(
            onDismissRequest = { stockBlockAlertMessage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RedDanger)
                    Text(if (isUrdu) "اسٹاک پروٹیکشن الرٹ" else "Stock Protection Alert")
                }
            },
            text = {
                Text(stockBlockAlertMessage ?: "", fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = { stockBlockAlertMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDanger)
                ) {
                    Text(if (isUrdu) "سمجھ گیا" else "OK")
                }
            }
        )
    }

    // 7. FULLSCREEN TRASH BIN MODAL
    if (isTrashOpen) {
        Dialog(
            onDismissRequest = { isTrashOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = Color(0xFFF8FAFC)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Trash Header
                    Surface(
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { isTrashOpen = false }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DukanGreenHover)
                                }
                                Text(
                                    text = if (isUrdu) "حذف شدہ اندراجات (Trash Bin)" else "Deleted Entries (Trash Bin)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Slate900
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (state.deletedTransactions.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            onEmptyTrash()
                                            Toast.makeText(context, if (isUrdu) "کوڑا دان خالی کر دیا گیا" else "Trash bin cleared", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF2F2), contentColor = RedDanger),
                                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isUrdu) "کوڑا دان خالی کریں" else "Empty Trash", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                IconButton(onClick = { isTrashOpen = false }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                                }
                            }
                        }
                    }

                    // Trash List
                    if (state.deletedTransactions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Empty", tint = Slate300, modifier = Modifier.size(64.dp))
                                Text(
                                    text = if (isUrdu) "کوئی حذف شدہ اینٹری دستیاب نہیں" else "No deleted entries in trash bin",
                                    fontSize = 13.sp,
                                    color = Slate500,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.deletedTransactions, key = { it.id }) { dt ->
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
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(color = Slate100, shape = RoundedCornerShape(6.dp)) {
                                                    Text(
                                                        text = dt.type.name,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Slate700,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = dt.partyName ?: dt.category,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Slate900
                                                )
                                            }
                                            Text(
                                                text = "${FormatUtils.formatFullDateTime(dt.date)} • ${FormatUtils.formatCurrency(dt.amount)} (${dt.paymentMethod})",
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                            if (!dt.notes.isNullOrBlank()) {
                                                Text(text = dt.notes, fontSize = 10.sp, color = Slate400, maxLines = 1)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                onRestoreTransaction(dt.id)
                                                Toast.makeText(context, if (isUrdu) "اینٹری بحال کر دی گئی" else "Transaction restored", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenLight, contentColor = DukanGreenHover),
                                            border = BorderStroke(1.dp, GreenSuccessBorder),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isUrdu) "بحال کریں" else "Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
}

data class PartialTransactionData(
    val amount: Double? = null,
    val category: String? = null,
    val notes: String? = null,
    val paymentMethod: PaymentMethod? = null,
    val partyName: String? = null,
    val date: String? = null,
    val loanAccountId: String? = null
)
