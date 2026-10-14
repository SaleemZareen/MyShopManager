package com.saleemkhan.myshopmanager.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saleemkhan.myshopmanager.data.accounting.AccountingEngine
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.*
import java.text.SimpleDateFormat
import java.util.*

enum class ReportType(
    val titleEn: String,
    val titleUr: String,
    val subtitleEn: String,
    val subtitleUr: String,
    val icon: ImageVector,
    val color: Color
) {
    BALANCE_SHEET(
        "Balance Sheet",
        "بیلنس شیٹ (توازنِ مال)",
        "Assets, Liabilities & Net Capital",
        "اثاثہ جات، واجبات اور خالص مالیت",
        Icons.Default.AccountBalance,
        BlueInfo
    ),
    IRIS_TAX(
        "FBR IRIS Tax",
        "ایف بی آر انکم ٹیکس خلاصہ",
        "IRIS Codes, Minimum Tax & Wealth Recon",
        "ٹیکس کوڈز، ٹرن اوور اور ویلتھ مطابقت",
        Icons.Default.ReceiptLong,
        PurpleAccent
    ),
    SALES(
        "Sales Ledger",
        "تفصیلی فروخت کی رپورٹ",
        "Invoices, Cash vs Credit & Revenue",
        "تاریخ وار فروخت، کیش و ادھار بل",
        Icons.Default.ShoppingCart,
        DukanGreenPrimary
    ),
    PURCHASES(
        "Purchases Ledger",
        "تفصیلی خریداری کی رپورٹ",
        "Vendor Bills & Cost Tracking",
        "سپلائرز کے خریداری بل اور تاریخ",
        Icons.Default.ShoppingBag,
        AmberWarning
    ),
    COGS(
        "Cost of Goods Sold (COGS)",
        "اسٹاک لاگت رپورٹ (COGS)",
        "Opening, Purchases, Closing & Margins",
        "بیچے گئے مال کی لاگت اور مجموعی منافع",
        Icons.Default.Inventory2,
        DukanGreenHover
    ),
    NET_PROFIT(
        "Profit & Loss (P&L)",
        "نفع و نقصان کا گوشوارہ (P&L)",
        "Gross Profit, Overheads & Net Earnings",
        "آمدن، دکان کے اخراجات اور خالص منافع",
        Icons.Default.TrendingUp,
        GreenSuccess
    ),
    EXPENSES(
        "Expenses Ledger",
        "اخراجات کی تفصیلی رپورٹ",
        "Shop Overheads & Category Breakdown",
        "کرایہ، بل، تنخواہیں اور متفرق اخراجات",
        Icons.Default.MoneyOff,
        RedDanger
    ),
    CUSTOMER_LEDGER(
        "Customer Khata (Receivables)",
        "کسٹمر ادھار کھاتہ و وصولیاں",
        "Udhaar Balances, Aging & Contacts",
        "کسٹمرز کے بقایا جات اور وصول طلب ادھار",
        Icons.Default.People,
        AmberWarning
    ),
    SUPPLIER_LEDGER(
        "Supplier Payables Ledger",
        "سپلائر واجبات اور ادائیگیاں",
        "Vendor Balances & Payment Schedules",
        "سپلائرز کو واجب الادا بل اور آخری تاریخ",
        Icons.Default.LocalShipping,
        RedDanger
    ),
    STOCK_INVENTORY(
        "Stock Valuation & Inventory",
        "اسٹاک ویلیوایشن و انوینٹری",
        "Inventory Cost, Retail Value & Alerts",
        "موجودہ اسٹاک کی مالیت، لاگت اور لو اسٹاک",
        Icons.Default.Assessment,
        BlueInfo
    ),
    CASH_FLOW(
        "Cash & Bank Flow (Roznamcha)",
        "کیش بک اور بینک فلو (روزنامچہ)",
        "Daily Inflows, Outflows & Balances",
        "یومیہ نقد رقوم کی آمد، اخراج اور بیلنس",
        Icons.Default.AccountBalanceWallet,
        DukanGreenPrimary
    )
}

enum class ReportPeriod(val titleEn: String, val titleUr: String) {
    TODAY("Today", "آج"),
    YESTERDAY("Yesterday", "گزشتہ کل"),
    THIS_WEEK("This Week", "اس ہفتے"),
    FORTNIGHT("15 Days (Fortnight)", "پندرہ روزہ (15 دن)"),
    THIS_MONTH("This Month", "اس ماہ"),
    THIS_YEAR("This Year", "اس سال"),
    ALL_TIME("All Time", "مکمل ریکارڈ"),
    CUSTOM("Custom", "مخصوص تاریخ")
}

@Composable
fun ReportsScreen(
    state: AppState,
    isUrdu: Boolean,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        BackHandler(enabled = true) {
            onBack()
        }
    }
    val context = LocalContext.current
    var selectedReport by remember { mutableStateOf(ReportType.BALANCE_SHEET) }
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.THIS_MONTH) }
    var searchQuery by remember { mutableStateOf("") }
    var customStartDate by remember { mutableStateOf(FormatUtils.getTodayIsoDate()) }
    var customEndDate by remember { mutableStateOf(FormatUtils.getTodayIsoDate()) }
    var isReportDropdownOpen by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var selectedSupplierId by remember { mutableStateOf<String?>(null) }
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var isCustomerFilterMenuOpen by remember { mutableStateOf(false) }
    var isSupplierFilterMenuOpen by remember { mutableStateOf(false) }
    var isItemFilterMenuOpen by remember { mutableStateOf(false) }

    // State filtering helper
    val periodTransactions = remember(state.transactions, selectedPeriod, customStartDate, customEndDate) {
        filterTransactionsByPeriod(state.transactions, selectedPeriod, customStartDate, customEndDate)
    }

    val filteredTransactions = remember(periodTransactions, selectedCustomerId, selectedSupplierId, selectedItemId) {
        val selectedCustName = state.customers.find { it.id == selectedCustomerId }?.name
        val selectedSuppName = state.suppliers.find { it.id == selectedSupplierId }?.name
        periodTransactions.filter { tx ->
            val matchCust = selectedCustomerId == null || tx.partyId == selectedCustomerId ||
                    (!tx.partyName.isNullOrBlank() && selectedCustName != null && tx.partyName.equals(selectedCustName, ignoreCase = true))
            val matchSupp = selectedSupplierId == null || tx.partyId == selectedSupplierId ||
                    (!tx.partyName.isNullOrBlank() && selectedSuppName != null && tx.partyName.equals(selectedSuppName, ignoreCase = true))
            val matchItem = selectedItemId == null || tx.itemId == selectedItemId
            matchCust && matchSupp && matchItem
        }
    }

    // Comprehensive summary calculations
    val fbrSummary = remember(state) { TaxEngine.calculateFbrSummary(state) }

    if (showExportDialog) {
        ExportReportDialog(
            reportType = selectedReport,
            period = selectedPeriod,
            state = state,
            filteredTx = filteredTransactions,
            fbrSummary = fbrSummary,
            isUrdu = isUrdu,
            onDismiss = { showExportDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
    ) {
        // --- 1. HEADER & REPORT SELECTOR DROPDOWN ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(selectedReport.color.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = selectedReport.icon,
                                    contentDescription = null,
                                    tint = selectedReport.color,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isUrdu) "اکاؤنٹنٹ رپورٹس اور ایکسپورٹ پورٹل" else "Accountant Reports & Exports",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = if (isUrdu) selectedReport.subtitleUr else selectedReport.subtitleEn,
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }

                        // Quick Share / Export Action Bar with PDF, Excel and Dialog Options
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick PDF Button
                            FilledTonalButton(
                                onClick = {
                                    ReportExporter.printOrSavePdf(
                                        context = context,
                                        reportType = selectedReport,
                                        period = selectedPeriod,
                                        state = state,
                                        filteredTx = filteredTransactions,
                                        fbrSummary = fbrSummary,
                                        isUrdu = isUrdu
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = RedDanger.copy(alpha = 0.12f),
                                    contentColor = RedDanger
                                ),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Quick Excel Button
                            FilledTonalButton(
                                onClick = {
                                    ReportExporter.exportExcelSpreadsheet(
                                        context = context,
                                        reportType = selectedReport,
                                        period = selectedPeriod,
                                        state = state,
                                        filteredTx = filteredTransactions,
                                        fbrSummary = fbrSummary,
                                        isUrdu = isUrdu
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = DukanGreenPrimary.copy(alpha = 0.12f),
                                    contentColor = DukanGreenPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = "Excel", modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Excel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Full Format Selector Dialog Button
                            IconButton(
                                onClick = { showExportDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export Format Options", tint = Slate700, modifier = Modifier.size(20.dp))
                            }

                            // WhatsApp Share Button
                            IconButton(
                                onClick = {
                                    val summaryText = generateReportShareSummary(
                                        reportType = selectedReport,
                                        period = selectedPeriod,
                                        state = state,
                                        filteredTx = filteredTransactions,
                                        fbrSummary = fbrSummary,
                                        isUrdu = isUrdu
                                    )
                                    shareReportText(context, summaryText, if (isUrdu) selectedReport.titleUr else selectedReport.titleEn)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = AmberWarning, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Report Selector Trigger Button (Dropdown)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { isReportDropdownOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, selectedReport.color.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = selectedReport.color.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(selectedReport.icon, contentDescription = null, tint = selectedReport.color, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (isUrdu) selectedReport.titleUr else selectedReport.titleEn,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate900
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Slate600)
                            }
                        }

                        DropdownMenu(
                            expanded = isReportDropdownOpen,
                            onDismissRequest = { isReportDropdownOpen = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(Color.White)
                        ) {
                            ReportType.values().forEach { rType ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(rType.icon, contentDescription = null, tint = rType.color, modifier = Modifier.size(18.dp))
                                            Column {
                                                Text(
                                                    text = if (isUrdu) rType.titleUr else rType.titleEn,
                                                    fontWeight = if (selectedReport == rType) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    color = if (selectedReport == rType) rType.color else Slate800
                                                )
                                                Text(
                                                    text = if (isUrdu) rType.subtitleUr else rType.subtitleEn,
                                                    fontSize = 10.sp,
                                                    color = Slate500
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedReport = rType
                                        isReportDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Fast scrollable chips for 11 reports
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReportType.values().forEach { rType ->
                            val isSelected = selectedReport == rType
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedReport = rType },
                                label = {
                                    Text(
                                        text = if (isUrdu) rType.titleUr.split(" ")[0] else rType.titleEn.split(" ")[0],
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = rType.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isSelected) rType.color else Slate500
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = rType.color.copy(alpha = 0.12f),
                                    selectedLabelColor = rType.color
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Slate200,
                                    selectedBorderColor = rType.color
                                )
                            )
                        }
                    }

                    // Export Format Quick Output Strip (PDF vs Excel)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate100,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = Slate600, modifier = Modifier.size(15.dp))
                                Text(
                                    text = if (isUrdu) "رپورٹ آؤٹ پٹ:" else "Report Output:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SuggestionChip(
                                    onClick = {
                                        ReportExporter.printOrSavePdf(
                                            context = context,
                                            reportType = selectedReport,
                                            period = selectedPeriod,
                                            state = state,
                                            filteredTx = filteredTransactions,
                                            fbrSummary = fbrSummary,
                                            isUrdu = isUrdu
                                        )
                                    },
                                    label = { Text("PDF پرنٹ / سیو", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                                    icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(13.dp), tint = RedDanger) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.4f)),
                                    modifier = Modifier.height(28.dp)
                                )
                                SuggestionChip(
                                    onClick = {
                                        ReportExporter.exportExcelSpreadsheet(
                                            context = context,
                                            reportType = selectedReport,
                                            period = selectedPeriod,
                                            state = state,
                                            filteredTx = filteredTransactions,
                                            fbrSummary = fbrSummary,
                                            isUrdu = isUrdu
                                        )
                                    },
                                    label = { Text("Excel شیٹ", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold) },
                                    icon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(13.dp), tint = DukanGreenPrimary) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, DukanGreenPrimary.copy(alpha = 0.4f)),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. PERIOD FILTER & SEARCH BAR (Applicable for transaction / ledger reports) ---
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Period Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReportPeriod.values().forEach { period ->
                            val isSelected = selectedPeriod == period
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) DukanGreenPrimary else Slate100,
                                modifier = Modifier
                                    .clickable { selectedPeriod = period }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) period.titleUr else period.titleEn,
                                    color = if (isSelected) Color.White else Slate700,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Custom Date Pickers
                    if (selectedPeriod == ReportPeriod.CUSTOM) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    FormatUtils.showDatePickerDialog(context, customStartDate) { customStartDate = it }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(customStartDate, fontSize = 11.sp)
                            }
                            Text(if (isUrdu) "تا" else "to", fontSize = 12.sp, color = Slate600)
                            OutlinedButton(
                                onClick = {
                                    FormatUtils.showDatePickerDialog(context, customEndDate) { customEndDate = it }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(customEndDate, fontSize = 11.sp)
                            }
                        }
                    }

                    // Dedicated Entity Selectors (Customer, Supplier, Inventory Item)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Customer Selector
                        Box {
                            val activeCust = state.customers.find { it.id == selectedCustomerId }
                            FilterChip(
                                selected = selectedCustomerId != null,
                                onClick = { isCustomerFilterMenuOpen = true },
                                label = {
                                    Text(
                                        text = if (activeCust != null) "${if (isUrdu) "گاہک:" else "Cust:"} ${activeCust.name}"
                                        else if (isUrdu) "تمام گاہک" else "All Customers",
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedCustomerId != null) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                trailingIcon = {
                                    if (selectedCustomerId != null) {
                                        IconButton(
                                            onClick = { selectedCustomerId = null },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                                        }
                                    } else {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = isCustomerFilterMenuOpen,
                                onDismissRequest = { isCustomerFilterMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isUrdu) "تمام گاہک (بغیر فلٹر)" else "All Customers", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    onClick = {
                                        selectedCustomerId = null
                                        isCustomerFilterMenuOpen = false
                                    }
                                )
                                state.customers.forEach { c ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(c.name, fontSize = 12.sp)
                                                if (c.totalCredit > 0) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(FormatUtils.formatCurrency(c.totalCredit), fontSize = 11.sp, color = AmberWarning)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedCustomerId = c.id
                                            isCustomerFilterMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        // Supplier Selector
                        Box {
                            val activeSupp = state.suppliers.find { it.id == selectedSupplierId }
                            FilterChip(
                                selected = selectedSupplierId != null,
                                onClick = { isSupplierFilterMenuOpen = true },
                                label = {
                                    Text(
                                        text = if (activeSupp != null) "${if (isUrdu) "سپلائر:" else "Supp:"} ${activeSupp.name}"
                                        else if (isUrdu) "تمام سپلائر" else "All Suppliers",
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedSupplierId != null) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                trailingIcon = {
                                    if (selectedSupplierId != null) {
                                        IconButton(
                                            onClick = { selectedSupplierId = null },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                                        }
                                    } else {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = isSupplierFilterMenuOpen,
                                onDismissRequest = { isSupplierFilterMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isUrdu) "تمام سپلائرز (بغیر فلٹر)" else "All Suppliers", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    onClick = {
                                        selectedSupplierId = null
                                        isSupplierFilterMenuOpen = false
                                    }
                                )
                                state.suppliers.forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(s.name, fontSize = 12.sp)
                                                if (s.totalPayable > 0) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(FormatUtils.formatCurrency(s.totalPayable), fontSize = 11.sp, color = RedDanger)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedSupplierId = s.id
                                            isSupplierFilterMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        // Inventory Item Selector
                        Box {
                            val activeItem = state.inventory.find { it.id == selectedItemId }
                            FilterChip(
                                selected = selectedItemId != null,
                                onClick = { isItemFilterMenuOpen = true },
                                label = {
                                    Text(
                                        text = if (activeItem != null) "${if (isUrdu) "آئٹم:" else "Item:"} ${activeItem.name}"
                                        else if (isUrdu) "تمام اشیاء" else "All Items",
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedItemId != null) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                trailingIcon = {
                                    if (selectedItemId != null) {
                                        IconButton(
                                            onClick = { selectedItemId = null },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                                        }
                                    } else {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = isItemFilterMenuOpen,
                                onDismissRequest = { isItemFilterMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isUrdu) "تمام اشیاء (بغیر فلٹر)" else "All Items", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    onClick = {
                                        selectedItemId = null
                                        isItemFilterMenuOpen = false
                                    }
                                )
                                state.inventory.forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(item.name, fontSize = 12.sp)
                                                Spacer(Modifier.width(8.dp))
                                                Text("${item.quantity} ${item.unit}", fontSize = 11.sp, color = Slate500)
                                            }
                                        },
                                        onClick = {
                                            selectedItemId = item.id
                                            isItemFilterMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (isUrdu) "تلاش کریں (پارٹی، انوائس، آئٹم، کیٹیگری)..." else "Search party, invoice, item, notes...",
                                fontSize = 12.sp,
                                color = Slate400
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Slate200,
                            focusedBorderColor = DukanGreenPrimary,
                            unfocusedContainerColor = Slate50,
                            focusedContainerColor = Color.White
                        )
                    )
                }
            }
        }

        // --- 3. DEDICATED REPORT VIEWS (11 REPORTS) ---
        when (selectedReport) {
            ReportType.BALANCE_SHEET -> {
                item {
                    BalanceSheetReportView(
                        state = state,
                        summary = fbrSummary,
                        isUrdu = isUrdu,
                        searchQuery = searchQuery
                    )
                }
            }
            ReportType.IRIS_TAX -> {
                item {
                    IrisTaxReportView(
                        state = state,
                        summary = fbrSummary,
                        isUrdu = isUrdu,
                        searchQuery = searchQuery
                    )
                }
            }
            ReportType.SALES -> {
                item {
                    SalesReportView(
                        transactions = filteredTransactions.filter { it.type == TransactionType.SALE },
                        searchQuery = searchQuery,
                        isUrdu = isUrdu
                    )
                }
            }
            ReportType.PURCHASES -> {
                item {
                    PurchasesReportView(
                        transactions = filteredTransactions.filter { it.type == TransactionType.PURCHASE },
                        searchQuery = searchQuery,
                        isUrdu = isUrdu
                    )
                }
            }
            ReportType.COGS -> {
                item {
                    CogsReportView(
                        state = state,
                        summary = fbrSummary,
                        filteredTransactions = filteredTransactions,
                        searchQuery = searchQuery,
                        isUrdu = isUrdu
                    )
                }
            }
            ReportType.NET_PROFIT -> {
                item {
                    NetProfitReportView(
                        state = state,
                        summary = fbrSummary,
                        filteredTransactions = filteredTransactions,
                        isUrdu = isUrdu
                    )
                }
            }
            ReportType.EXPENSES -> {
                item {
                    ExpensesReportView(
                        transactions = filteredTransactions.filter { it.type == TransactionType.EXPENSE },
                        searchQuery = searchQuery,
                        isUrdu = isUrdu
                    )
                }
            }
            ReportType.CUSTOMER_LEDGER -> {
                item {
                    val displayCustomers = if (selectedCustomerId != null) {
                        state.customers.filter { it.id == selectedCustomerId }
                    } else state.customers
                    CustomerLedgerReportView(
                        customers = displayCustomers,
                        searchQuery = searchQuery,
                        isUrdu = isUrdu
                    )
                }
            }
            ReportType.SUPPLIER_LEDGER -> {
                item {
                    val displaySuppliers = if (selectedSupplierId != null) {
                        state.suppliers.filter { it.id == selectedSupplierId }
                    } else state.suppliers
                    SupplierLedgerReportView(
                        suppliers = displaySuppliers,
                        searchQuery = searchQuery,
                        isUrdu = isUrdu
                    )
                }
            }
            ReportType.STOCK_INVENTORY -> {
                item {
                    val displayInventory = if (selectedItemId != null) {
                        state.inventory.filter { it.id == selectedItemId }
                    } else state.inventory
                    StockValuationReportView(
                        inventory = displayInventory,
                        searchQuery = searchQuery,
                        isUrdu = isUrdu
                    )
                }
            }
            ReportType.CASH_FLOW -> {
                item {
                    CashFlowReportView(
                        state = state,
                        filteredTransactions = filteredTransactions,
                        searchQuery = searchQuery,
                        isUrdu = isUrdu
                    )
                }
            }
        }

        // --- 4. BOTTOM DOWNLOAD & EXPORT ACTION CARD ---
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isUrdu) "رپورٹ ڈاؤن لوڈ / ایکسپورٹ کریں" else "Download / Export Report",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )
                            Text(
                                text = if (isUrdu) "پی ڈی ایف یا ایکسل فارمیٹ میں باآسانی حاصل کریں" else "Get report output in PDF or Excel format",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                        OutlinedButton(
                            onClick = { showExportDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(if (isUrdu) "تمام آپشنز" else "Options", fontSize = 10.5.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                ReportExporter.printOrSavePdf(
                                    context = context,
                                    reportType = selectedReport,
                                    period = selectedPeriod,
                                    state = state,
                                    filteredTx = filteredTransactions,
                                    fbrSummary = fbrSummary,
                                    isUrdu = isUrdu
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isUrdu) "PDF رپورٹ" else "PDF Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                ReportExporter.exportExcelSpreadsheet(
                                    context = context,
                                    reportType = selectedReport,
                                    period = selectedPeriod,
                                    state = state,
                                    filteredTx = filteredTransactions,
                                    fbrSummary = fbrSummary,
                                    isUrdu = isUrdu
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isUrdu) "Excel شیٹ" else "Excel Sheet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 1. BALANCE SHEET REPORT VIEW (توازنِ مال - بیلنس شیٹ)
// =========================================================================
@Composable
fun BalanceSheetReportView(
    state: AppState,
    summary: FbrSummary,
    isUrdu: Boolean,
    searchQuery: String
) {
    val bs = remember(state) { AccountingEngine.calculateBalanceSheet(state) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // KPI Summary
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "مجموعی اثاثہ جات" else "Total Assets",
                value = bs.totalAssets,
                color = DukanGreenHover,
                icon = Icons.Default.AccountBalance,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "مجموعی واجبات" else "Total Liabilities",
                value = bs.totalLiabilities,
                color = RedDanger,
                icon = Icons.Default.MoneyOff,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "خالص مالیت (Capital)" else "Net Capital / Worth",
                value = bs.totalEquity,
                color = BlueInfo,
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f)
            )
        }

        // Current Assets Section
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "1. کاروباری اثاثہ جات (Current & Fixed Assets)" else "1. Business Assets",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                ReportDetailRow(
                    label = if (isUrdu) "نقدی کیش و بینک بیلنس" else "Cash & Bank Balance",
                    subtext = "${state.bankAccounts.size} accounts",
                    amount = bs.cashAndBank,
                    amountColor = Slate800
                )
                ReportDetailRow(
                    label = if (isUrdu) "اسٹاک کی مالیت (Inventory at AVCO)" else "Closing Inventory Value (AVCO)",
                    subtext = "${state.inventory.size} items",
                    amount = bs.inventoryValue,
                    amountColor = Slate800
                )
                ReportDetailRow(
                    label = if (isUrdu) "کسٹمرز سے وصول طلب ادھار (Receivables)" else "Customer Receivables (Debtors)",
                    subtext = "${state.customers.count { it.totalCredit > 0 }} debtors",
                    amount = bs.customerReceivables,
                    amountColor = AmberWarning
                )
                ReportDetailRow(
                    label = if (isUrdu) "دکان کے فکسڈ اثاثے (فرنیچر، مشینری)" else "Shop Fixed Assets",
                    subtext = "${state.businessAssets.size} assets registered",
                    amount = bs.businessFixedAssets,
                    amountColor = Slate800
                )

                Divider(color = Slate200)
                ReportDetailRow(
                    label = if (isUrdu) "کل اثاثے (Total Assets)" else "Total Assets",
                    subtext = null,
                    amount = bs.totalAssets,
                    amountColor = DukanGreenHover,
                    isBold = true,
                    isHighlight = true
                )
            }
        }

        // Liabilities & Equity Section
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "2. واجبات اور قرضہ جات (Liabilities & Debt)" else "2. Liabilities & Debt",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                ReportDetailRow(
                    label = if (isUrdu) "سپلائرز کے واجب الادا بل (Payables)" else "Supplier Payables (Creditors)",
                    subtext = "${state.suppliers.count { it.totalPayable > 0 }} creditors",
                    amount = bs.supplierPayables,
                    amountColor = RedDanger
                )
                ReportDetailRow(
                    label = if (isUrdu) "واجب الادا قرضے (Loans Payable)" else "Outstanding Loans",
                    subtext = "${state.loans.size} loan accounts",
                    amount = bs.loansPayable,
                    amountColor = RedDanger
                )

                Divider(color = Slate200)
                ReportDetailRow(
                    label = if (isUrdu) "کل واجبات (Total Liabilities)" else "Total Liabilities",
                    subtext = null,
                    amount = bs.totalLiabilities,
                    amountColor = RedDanger,
                    isBold = true,
                    isHighlight = true
                )
            }
        }

        // Net Worth / Equity Card (Satisfying A = L + E)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
            border = BorderStroke(1.5.dp, GreenSuccessBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isUrdu) "خالص کاروباری مالیت / ایکویٹی (Owner's Equity)" else "Owner's Equity (Capital)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DukanGreenHover
                    )
                    Text(
                        text = if (isUrdu) "کل اثاثے منہا کل واجبات (A = L + E)" else "Total Assets - Total Liabilities (A = L + E)",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }
                Text(
                    text = FormatUtils.formatCurrency(bs.totalEquity),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = DukanGreenHover
                )
            }
        }
    }
}

// =========================================================================
// 2. IRIS TAX REPORT VIEW (ایف بی آر ٹیکس خلاصہ)
// =========================================================================
@Composable
fun IrisTaxReportView(
    state: AppState,
    summary: FbrSummary,
    isUrdu: Boolean,
    searchQuery: String
) {
    val irisMappings = remember(summary, state) { TaxEngine.getIrisMappings(summary, state) }
    val wealthRecon = remember(summary, state) { TaxEngine.getWealthReconciliation(summary, state) }
    val withholding = remember(summary, state) { TaxEngine.getWithholdingTaxData(summary, state) }

    val filteredMappings = remember(irisMappings, searchQuery) {
        if (searchQuery.isBlank()) irisMappings
        else irisMappings.filter {
            it.code.contains(searchQuery, true) ||
            it.titleEn.contains(searchQuery, true) ||
            it.titleUr.contains(searchQuery, true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // KPI Summary Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "سالانہ ٹرن اوور (0001)" else "Gross Turnover",
                value = summary.sales,
                color = PurpleAccent,
                icon = Icons.Default.ReceiptLong,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "قابلِ ٹیکس منافع (0070)" else "Taxable Net Profit",
                value = summary.netProfit ?: 0.0,
                color = DukanGreenHover,
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "ایڈجسٹ ایبل ودہولڈنگ" else "Adjustable Tax",
                value = withholding.totalAdjustableTax,
                color = AmberWarning,
                icon = Icons.Default.VerifiedUser,
                modifier = Modifier.weight(1f)
            )
        }

        // Wealth Statement Health Banner
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (wealthRecon.isBalanced) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
            ),
            border = BorderStroke(1.dp, if (wealthRecon.isBalanced) GreenSuccessBorder else RedDangerBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (wealthRecon.isBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (wealthRecon.isBalanced) GreenSuccess else RedDanger,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (wealthRecon.isBalanced) {
                            if (isUrdu) "ویلتھ اسٹیٹمنٹ مطابقت درست ہے (Balanced)" else "Wealth Statement Balanced & Safe"
                        } else {
                            if (isUrdu) "انتباہ: مطابقت کا غیر وضاحتی فرق (Notice Risk)" else "Audit Risk: Unexplained Wealth Difference"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (wealthRecon.isBalanced) DukanGreenHover else RedDanger
                    )
                    Text(
                        text = if (isUrdu)
                            "مطابقت کا فرق: ${FormatUtils.formatCurrency(wealthRecon.unexplainedDifference)}"
                        else
                            "Unexplained Variance: ${FormatUtils.formatCurrency(wealthRecon.unexplainedDifference)}",
                        fontSize = 11.sp,
                        color = Slate700
                    )
                }
            }
        }

        // IRIS Field Codes Table
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "FBR IRIS پورٹل فیلڈ کوڈز (Official Mappings)" else "FBR IRIS Portal Field Codes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                filteredMappings.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PurpleAccent.copy(alpha = 0.1f),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Text(
                                        text = item.code,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PurpleAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = if (isUrdu) item.titleUr else item.titleEn,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate800
                                )
                            }
                            Text(text = item.description, fontSize = 10.sp, color = Slate500)
                        }

                        Text(
                            text = FormatUtils.formatCurrency(item.amount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                    }
                    Divider(color = Slate50)
                }
            }
        }
    }
}

// =========================================================================
// 3. SALES LEDGER REPORT VIEW (تفصیلی فروخت کی رپورٹ)
// =========================================================================
@Composable
fun SalesReportView(
    transactions: List<Transaction>,
    searchQuery: String,
    isUrdu: Boolean
) {
    val filteredSales = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            (it.partyName ?: "").contains(searchQuery, true) ||
            (it.invoiceNo ?: "").contains(searchQuery, true) ||
            (it.notes ?: "").contains(searchQuery, true) ||
            it.category.contains(searchQuery, true)
        }
    }

    val totalSales = filteredSales.sumOf { it.amount }
    val cashSales = filteredSales.filter { it.paymentMethod != PaymentMethod.CREDIT }.sumOf { it.amount }
    val creditSales = filteredSales.filter { it.paymentMethod == PaymentMethod.CREDIT }.sumOf { it.amount }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // KPI Summary
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "مجموعی فروخت" else "Total Sales",
                value = totalSales,
                color = DukanGreenPrimary,
                icon = Icons.Default.ShoppingCart,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "نقد فروخت" else "Cash Sales",
                value = cashSales,
                color = GreenSuccess,
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "ادھار فروخت" else "Credit (Udhaar)",
                value = creditSales,
                color = AmberWarning,
                icon = Icons.Default.Schedule,
                modifier = Modifier.weight(1f)
            )
        }

        // Ledger Table
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUrdu) "فروخت کا اندراج (${filteredSales.size} بلز)" else "Sales Invoices (${filteredSales.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate900
                    )
                    Text(
                        text = "Total: ${FormatUtils.formatCurrency(totalSales)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = DukanGreenHover
                    )
                }

                Divider(color = Slate100)

                if (filteredSales.isEmpty()) {
                    EmptyReportNotice(if (isUrdu) "اس مدت میں کوئی فروخت درج نہیں ہے۔" else "No sales recorded for this period.")
                } else {
                    filteredSales.forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = tx.partyName?.ifBlank { if (isUrdu) "عام گاہک" else "Walk-in Customer" } ?: if (isUrdu) "عام گاہک" else "Walk-in Customer",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = Slate900
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (tx.paymentMethod == PaymentMethod.CREDIT) AmberWarning.copy(alpha = 0.15f) else GreenSuccess.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = tx.paymentMethod.name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tx.paymentMethod == PaymentMethod.CREDIT) AmberWarning else GreenSuccess,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${FormatUtils.formatDate(tx.date)} ${if (!tx.invoiceNo.isNullOrBlank()) "• Inv #${tx.invoiceNo}" else ""} ${if (!tx.notes.isNullOrBlank()) "• ${tx.notes}" else ""}",
                                    fontSize = 10.sp,
                                    color = Slate500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = FormatUtils.formatCurrency(tx.amount),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DukanGreenHover
                            )
                        }
                        Divider(color = Slate50)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 4. PURCHASES LEDGER REPORT VIEW (تفصیلی خریداری کی رپورٹ)
// =========================================================================
@Composable
fun PurchasesReportView(
    transactions: List<Transaction>,
    searchQuery: String,
    isUrdu: Boolean
) {
    val filteredPurchases = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            (it.partyName ?: "").contains(searchQuery, true) ||
            (it.invoiceNo ?: "").contains(searchQuery, true) ||
            (it.notes ?: "").contains(searchQuery, true) ||
            it.category.contains(searchQuery, true)
        }
    }

    val totalPurchases = filteredPurchases.sumOf { it.amount }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "مجموعی خریداری" else "Total Purchases",
                value = totalPurchases,
                color = AmberWarning,
                icon = Icons.Default.ShoppingBag,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "خریداری کے بلز" else "Purchase Invoices",
                value = filteredPurchases.size.toDouble(),
                isCurrency = false,
                color = BlueInfo,
                icon = Icons.Default.Receipt,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "خریداری ریکارڈ (${filteredPurchases.size} اندراجات)" else "Purchases Ledger (${filteredPurchases.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                if (filteredPurchases.isEmpty()) {
                    EmptyReportNotice(if (isUrdu) "کوئی خریداری ریکارڈ موجود نہیں ہے۔" else "No purchase records found.")
                } else {
                    filteredPurchases.forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.partyName ?: if (isUrdu) "سپلائر بل" else "Supplier Purchase",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = "${FormatUtils.formatDate(tx.date)} • Method: ${tx.paymentMethod.name} ${if (!tx.notes.isNullOrBlank()) "• ${tx.notes}" else ""}",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                            Text(
                                text = FormatUtils.formatCurrency(tx.amount),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AmberWarning
                            )
                        }
                        Divider(color = Slate50)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. COGS REPORT VIEW (اسٹاک لاگت رپورٹ - Cost of Goods Sold)
// =========================================================================
@Composable
fun CogsReportView(
    state: AppState,
    summary: FbrSummary,
    filteredTransactions: List<Transaction>,
    searchQuery: String,
    isUrdu: Boolean
) {
    val cogsResult = remember(filteredTransactions) {
        AccountingEngine.calculatePeriodCogs(filteredTransactions)
    }
    val periodSales = filteredTransactions.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
    val verifiedCogs = cogsResult.verifiedCogs
    val grossProfit = periodSales - verifiedCogs
    val grossMarginPct = if (periodSales > 0) (grossProfit / periodSales) * 100 else 0.0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "بیچے گئے مال کی لاگت (AVCO)" else "Verified COGS (AVCO)",
                value = verifiedCogs,
                color = Slate800,
                icon = Icons.Default.Inventory2,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "مجموعی منافع (Gross)" else "Gross Profit",
                value = grossProfit,
                color = DukanGreenHover,
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "منافع کی شرح" else "Gross Margin %",
                value = grossMarginPct,
                isCurrency = false,
                color = PurpleAccent,
                icon = Icons.Default.Percent,
                modifier = Modifier.weight(1f)
            )
        }

        if (cogsResult.unverifiedSalesCount > 0) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Alert", tint = AmberWarning, modifier = Modifier.size(18.dp))
                    Text(
                        text = if (isUrdu) "نوٹ: ${cogsResult.unverifiedSalesCount} فروخت کے اندراجات کی تاریخی لاگت معلوم نہیں ہے۔"
                        else "Notice: ${cogsResult.unverifiedSalesCount} sales transaction(s) have unverified/missing historical unit cost.",
                        fontSize = 11.sp,
                        color = Slate800,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "اسٹاک لاگت کا گوشوارہ (Perpetual AVCO Costing)" else "Perpetual AVCO Cost Calculation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                ReportDetailRow(
                    label = if (isUrdu) "مجموعی فروخت (Total Revenue)" else "Total Sales Revenue",
                    subtext = "${filteredTransactions.count { it.type == TransactionType.SALE }} sales",
                    amount = periodSales,
                    amountColor = DukanGreenHover
                )
                ReportDetailRow(
                    label = if (isUrdu) "تصدیق شدہ بیچے گئے مال کی لاگت (Verified COGS)" else "Verified COGS (AVCO Costing)",
                    subtext = "${cogsResult.verifiedCount} verified sales",
                    amount = verifiedCogs,
                    amountColor = Slate800,
                    isBold = true
                )

                Divider(color = Slate200)
                ReportDetailRow(
                    label = if (isUrdu) "خام منافع (Gross Profit)" else "Gross Profit",
                    subtext = "Margin: ${String.format(Locale.US, "%.1f", grossMarginPct)}%",
                    amount = grossProfit,
                    amountColor = if (grossProfit >= 0) DukanGreenHover else RedDanger,
                    isBold = true,
                    isHighlight = true
                )
            }
        }
    }
}

// =========================================================================
// 6. NET PROFIT REPORT VIEW (نفع و نقصان کا گوشوارہ - P&L)
// =========================================================================
@Composable
fun NetProfitReportView(
    state: AppState,
    summary: FbrSummary,
    filteredTransactions: List<Transaction>,
    isUrdu: Boolean
) {
    val profitResult = remember(filteredTransactions) {
        AccountingEngine.calculatePeriodProfit(filteredTransactions)
    }
    val sales = profitResult.netRevenue
    val verifiedCogs = profitResult.verifiedCogs
    val grossProfit = profitResult.grossProfit
    val directExpenses = profitResult.operatingExpenses
    val otherIncomeTotal = state.otherIncome.let {
        it.rentalIncome + it.bankProfit + it.agriculturalIncome + it.capitalGain + it.freelanceIncome + it.otherIncome
    }
    val netProfit = grossProfit - directExpenses + otherIncomeTotal
    val estimatedTax = Math.max(0.0, (netProfit - 600000.0) * 0.15)
    val netProfitAfterTax = netProfit - estimatedTax

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "مجموعی سیلز" else "Total Revenue",
                value = sales,
                color = DukanGreenPrimary,
                icon = Icons.Default.ShoppingCart,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "دکان کے اخراجات" else "Total Expenses",
                value = directExpenses,
                color = RedDanger,
                icon = Icons.Default.MoneyOff,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "خالص منافع (Net)" else "Net Business Profit",
                value = netProfit,
                color = if (netProfit >= 0) GreenSuccess else RedDanger,
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "نفع و نقصان کی مکمل تفصیل (P&L Income Statement)" else "Multi-Step Income Statement",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                ReportDetailRow(if (isUrdu) "1. مجموعی آمدن / فروخت (Sales Revenue)" else "1. Gross Revenue from Sales", null, sales, DukanGreenHover)
                ReportDetailRow(if (isUrdu) "2. بیچے گئے مال کی لاگت (Verified COGS)" else "2. Cost of Goods Sold (Verified COGS)", null, verifiedCogs, Slate700)
                ReportDetailRow(if (isUrdu) "3. مجموعی منافع (Gross Profit)" else "3. Gross Profit", null, grossProfit, if (grossProfit >= 0) DukanGreenHover else RedDanger, isBold = true)

                Divider(color = Slate100)
                ReportDetailRow(if (isUrdu) "4. دکان کے آپریشنل اخراجات (Operating Expenses)" else "4. Operating Overheads & Expenses", null, directExpenses, RedDanger)
                ReportDetailRow(if (isUrdu) "5. دیگر آمدنی (Other Income)" else "5. Other Income (Rent, Profit, etc.)", null, otherIncomeTotal, BlueInfo)

                Divider(color = Slate200)
                ReportDetailRow(
                    label = if (isUrdu) "خالص منافع قبل از ٹیکس (Net Profit Before Tax)" else "Net Profit Before Tax",
                    subtext = null,
                    amount = netProfit,
                    amountColor = if (netProfit >= 0) DukanGreenHover else RedDanger,
                    isBold = true,
                    isHighlight = true
                )
                ReportDetailRow(
                    label = if (isUrdu) "متوقع انکم ٹیکس (Estimated Income Tax)" else "Estimated Income Tax",
                    subtext = "Based on FBR Slab",
                    amount = estimatedTax,
                    amountColor = RedDanger
                )
                ReportDetailRow(
                    label = if (isUrdu) "خالص منافع بعد از ٹیکس (Net Profit After Tax)" else "Net Profit After Tax",
                    subtext = null,
                    amount = netProfitAfterTax,
                    amountColor = if (netProfitAfterTax >= 0) GreenSuccess else RedDanger,
                    isBold = true,
                    isHighlight = true
                )
            }
        }
    }
}

// =========================================================================
// 7. EXPENSES LEDGER REPORT VIEW (اخراجات کی تفصیلی رپورٹ)
// =========================================================================
@Composable
fun ExpensesReportView(
    transactions: List<Transaction>,
    searchQuery: String,
    isUrdu: Boolean
) {
    val filteredExp = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            it.category.contains(searchQuery, true) ||
            (it.notes ?: "").contains(searchQuery, true) ||
            (it.partyName ?: "").contains(searchQuery, true)
        }
    }

    val totalExpenses = filteredExp.sumOf { it.amount }
    val categoryMap = filteredExp.groupBy { it.category }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "مجموعی اخراجات" else "Total Expenses",
                value = totalExpenses,
                color = RedDanger,
                icon = Icons.Default.MoneyOff,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "اخراجات کے واؤچرز" else "Expense Vouchers",
                value = filteredExp.size.toDouble(),
                isCurrency = false,
                color = Slate800,
                icon = Icons.Default.Receipt,
                modifier = Modifier.weight(1f)
            )
        }

        // Category Breakdown Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "کیٹیگری وائز خلاصہ (Category Breakdown)" else "Category Breakdown",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                categoryMap.forEach { (cat, list) ->
                    val catTotal = list.sumOf { it.amount }
                    val catPct = if (totalExpenses > 0) (catTotal / totalExpenses) * 100 else 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = cat.replace("_", " "), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Slate800)
                            Text(text = "${String.format(Locale.US, "%.1f", catPct)}% of total", fontSize = 10.sp, color = Slate500)
                        }
                        Text(text = FormatUtils.formatCurrency(catTotal), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RedDanger)
                    }
                    Divider(color = Slate50)
                }
            }
        }

        // Itemized Vouchers List
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "تفصیلی واؤچرز (${filteredExp.size})" else "Itemized Expense Vouchers (${filteredExp.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                if (filteredExp.isEmpty()) {
                    EmptyReportNotice(if (isUrdu) "کوئی خرچ درج نہیں ہے۔" else "No expense records found.")
                } else {
                    filteredExp.forEach { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = tx.category.replace("_", " "), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Slate900)
                                Text(
                                    text = "${FormatUtils.formatDate(tx.date)} ${if (!tx.notes.isNullOrBlank()) "• ${tx.notes}" else ""}",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                            Text(text = FormatUtils.formatCurrency(tx.amount), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RedDanger)
                        }
                        Divider(color = Slate50)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 8. CUSTOMER LEDGER REPORT VIEW (کسٹمر ادھار کھاتہ و وصولیاں)
// =========================================================================
@Composable
fun CustomerLedgerReportView(
    customers: List<CustomerParty>,
    searchQuery: String,
    isUrdu: Boolean
) {
    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers
        else customers.filter {
            it.name.contains(searchQuery, true) ||
            it.phone.contains(searchQuery, true) ||
            (it.notes ?: "").contains(searchQuery, true)
        }
    }

    val totalUdhaar = filteredCustomers.sumOf { it.totalCredit }
    val debtorsCount = filteredCustomers.count { it.totalCredit > 0 }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "کل وصول طلب ادھار" else "Total Udhaar Receivables",
                value = totalUdhaar,
                color = AmberWarning,
                icon = Icons.Default.People,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "ادھار والے گاہک" else "Active Debtors",
                value = debtorsCount.toDouble(),
                isCurrency = false,
                color = BlueInfo,
                icon = Icons.Default.Person,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "کسٹمر کھاتہ رجسٹر (${filteredCustomers.size})" else "Customer Khata Register (${filteredCustomers.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                if (filteredCustomers.isEmpty()) {
                    EmptyReportNotice(if (isUrdu) "کوئی کسٹمر کھاتہ نہیں ملا۔" else "No customer accounts found.")
                } else {
                    filteredCustomers.sortedByDescending { it.totalCredit }.forEach { cust ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = cust.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Slate900)
                                    if (cust.status == "ARCHIVED") {
                                        Surface(shape = RoundedCornerShape(4.dp), color = Slate200) {
                                            Text("Archived", fontSize = 9.sp, color = Slate600, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text(
                                    text = "${if (cust.phone.isNotBlank()) cust.phone else "No Phone"} • Last: ${FormatUtils.formatDate(cust.lastTransactionDate)}",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                            Text(
                                text = FormatUtils.formatCurrency(cust.totalCredit),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (cust.totalCredit > 0) AmberWarning else GreenSuccess
                            )
                        }
                        Divider(color = Slate50)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 9. SUPPLIER LEDGER REPORT VIEW (سپلائر واجبات اور ادائیگیاں)
// =========================================================================
@Composable
fun SupplierLedgerReportView(
    suppliers: List<SupplierParty>,
    searchQuery: String,
    isUrdu: Boolean
) {
    val filteredSuppliers = remember(suppliers, searchQuery) {
        if (searchQuery.isBlank()) suppliers
        else suppliers.filter {
            it.name.contains(searchQuery, true) ||
            it.phone.contains(searchQuery, true) ||
            (it.notes ?: "").contains(searchQuery, true)
        }
    }

    val totalPayable = filteredSuppliers.sumOf { it.totalPayable }
    val payableSuppliersCount = filteredSuppliers.count { it.totalPayable > 0 }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "کل واجب الادا بل" else "Total Supplier Payables",
                value = totalPayable,
                color = RedDanger,
                icon = Icons.Default.LocalShipping,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "سپلائرز تعداد" else "Creditors Count",
                value = payableSuppliersCount.toDouble(),
                isCurrency = false,
                color = Slate800,
                icon = Icons.Default.Business,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "سپلائر کھاتہ رجسٹر (${filteredSuppliers.size})" else "Supplier Payables Register (${filteredSuppliers.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                if (filteredSuppliers.isEmpty()) {
                    EmptyReportNotice(if (isUrdu) "کوئی سپلائر کھاتہ موجود نہیں ہے۔" else "No supplier accounts found.")
                } else {
                    filteredSuppliers.sortedByDescending { it.totalPayable }.forEach { sup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = sup.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Slate900)
                                Text(
                                    text = "${if (sup.phone.isNotBlank()) sup.phone else "No Phone"} ${if (!sup.dueDate.isNullOrBlank()) "• Due: ${sup.dueDate}" else ""}",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                            Text(
                                text = FormatUtils.formatCurrency(sup.totalPayable),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (sup.totalPayable > 0) RedDanger else GreenSuccess
                            )
                        }
                        Divider(color = Slate50)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 10. STOCK VALUATION & INVENTORY REPORT (اسٹاک ویلیوایشن و انوینٹری)
// =========================================================================
@Composable
fun StockValuationReportView(
    inventory: List<InventoryItem>,
    searchQuery: String,
    isUrdu: Boolean
) {
    val filteredItems = remember(inventory, searchQuery) {
        if (searchQuery.isBlank()) inventory
        else inventory.filter {
            it.name.contains(searchQuery, true) ||
            it.category.contains(searchQuery, true) ||
            (it.barcode ?: "").contains(searchQuery, true)
        }
    }

    val totalCostValue = filteredItems.sumOf { it.quantity * it.purchasePrice }
    val totalRetailValue = filteredItems.sumOf { it.quantity * it.salePrice }
    val lowStockCount = filteredItems.count { it.quantity <= it.minStockAlert }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "اسٹاک لاگت ویلیو" else "Total Cost Value",
                value = totalCostValue,
                color = BlueInfo,
                icon = Icons.Default.Assessment,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "متوقع فروخت ویلیو" else "Expected Retail Value",
                value = totalRetailValue,
                color = DukanGreenHover,
                icon = Icons.Default.MonetizationOn,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "لو اسٹاک آئٹمز" else "Low Stock Alerts",
                value = lowStockCount.toDouble(),
                isCurrency = false,
                color = RedDanger,
                icon = Icons.Default.Warning,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "آئٹم وائز انوینٹری ویلیوایشن (${filteredItems.size})" else "Inventory Item Valuation (${filteredItems.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                if (filteredItems.isEmpty()) {
                    EmptyReportNotice(if (isUrdu) "کوئی آئٹم نہیں ملا۔" else "No inventory items found.")
                } else {
                    filteredItems.forEach { item ->
                        val itemCostVal = item.quantity * item.purchasePrice
                        val itemRetailVal = item.quantity * item.salePrice
                        val isLowStock = item.quantity <= item.minStockAlert

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = item.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Slate900)
                                    if (isLowStock) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = RedDangerLight) {
                                            Text("Low Stock", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = RedDanger, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text(
                                    text = "Qty: ${FormatUtils.formatNumber(item.quantity)} ${item.unit} • Cost: ${FormatUtils.formatCurrency(item.purchasePrice)} • Retail: ${FormatUtils.formatCurrency(item.salePrice)}",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = FormatUtils.formatCurrency(itemCostVal), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate900)
                                Text(text = "Retail: ${FormatUtils.formatCurrency(itemRetailVal)}", fontSize = 10.sp, color = DukanGreenHover)
                            }
                        }
                        Divider(color = Slate50)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 11. CASH FLOW REPORT VIEW (کیش بک اور بینک فلو - روزنامچہ)
// =========================================================================
@Composable
fun CashFlowReportView(
    state: AppState,
    filteredTransactions: List<Transaction>,
    searchQuery: String,
    isUrdu: Boolean
) {
    val cashFlowResult = remember(filteredTransactions) {
        AccountingEngine.calculateCashFlow(filteredTransactions)
    }
    val inflows = cashFlowResult.cashInflow
    val outflows = cashFlowResult.cashOutflow
    val netCashFlow = cashFlowResult.netCashFlow
    val totalCashAndBank = state.bankAccounts.sumOf { it.balance }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportKpiCard(
                title = if (isUrdu) "کل کیش آمد (Inflow)" else "Cash Inflows",
                value = inflows,
                color = GreenSuccess,
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "کل کیش اخراج (Outflow)" else "Cash Outflows",
                value = outflows,
                color = RedDanger,
                icon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1f)
            )
            ReportKpiCard(
                title = if (isUrdu) "خالص تبدیلی" else "Net Cash Flow",
                value = netCashFlow,
                color = if (netCashFlow >= 0) DukanGreenHover else RedDanger,
                icon = Icons.Default.AccountBalanceWallet,
                modifier = Modifier.weight(1f)
            )
        }

        // Bank / Cash Accounts Live Balances
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isUrdu) "دکان کے کیش و بینک اکاؤنٹس (Current Balances)" else "Cash & Bank Accounts",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                Divider(color = Slate100)

                state.bankAccounts.forEach { acc ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = acc.accountTitle, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Slate800)
                            Text(text = "${acc.type} • ${acc.bankName}", fontSize = 10.sp, color = Slate500)
                        }
                        Text(text = FormatUtils.formatCurrency(acc.balance), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DukanGreenHover)
                    }
                    Divider(color = Slate50)
                }

                Divider(color = Slate200)
                ReportDetailRow(
                    label = if (isUrdu) "مجموعی نقد رقم (Total Available Cash)" else "Total Cash & Bank Balance",
                    subtext = null,
                    amount = totalCashAndBank,
                    amountColor = DukanGreenHover,
                    isBold = true,
                    isHighlight = true
                )
            }
        }
    }
}

// =========================================================================
// HELPER COMPOSABLES & UTILITIES
// =========================================================================

@Composable
fun ReportKpiCard(
    title: String,
    value: Double,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isCurrency: Boolean = true
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            }
            Text(text = title, fontSize = 10.sp, color = Slate600, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (isCurrency) FormatUtils.formatCurrency(value) else FormatUtils.formatNumber(value),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ReportDetailRow(
    label: String,
    subtext: String?,
    amount: Double,
    amountColor: Color,
    isBold: Boolean = false,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHighlight) Color(0xFFF0FDF4) else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                color = Slate800
            )
            if (!subtext.isNullOrBlank()) {
                Text(text = subtext, fontSize = 10.sp, color = Slate500)
            }
        }
        Text(
            text = FormatUtils.formatCurrency(amount),
            fontSize = 13.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = amountColor
        )
    }
}

@Composable
fun EmptyReportNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, fontSize = 12.sp, color = Slate500, textAlign = TextAlign.Center)
    }
}

// Date Range Filtering
fun filterTransactionsByPeriod(
    transactions: List<Transaction>,
    period: ReportPeriod,
    customStart: String,
    customEnd: String
): List<Transaction> {
    if (period == ReportPeriod.ALL_TIME) return transactions

    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayStr = sdf.format(cal.time)

    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStr = sdf.format(cal.time)

    val currentYear = todayStr.take(4)
    val currentMonth = todayStr.take(7)

    val calWeek = Calendar.getInstance()
    calWeek.firstDayOfWeek = Calendar.MONDAY
    calWeek.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    val weekStartStr = sdf.format(calWeek.time)

    val calFortnight = Calendar.getInstance()
    calFortnight.add(Calendar.DAY_OF_YEAR, -14)
    val fortnightStartStr = sdf.format(calFortnight.time)

    return transactions.filter { tx ->
        val txDate = tx.date.take(10)
        if (txDate.isBlank()) return@filter false

        when (period) {
            ReportPeriod.TODAY -> txDate == todayStr
            ReportPeriod.YESTERDAY -> txDate == yesterdayStr
            ReportPeriod.THIS_WEEK -> txDate >= weekStartStr && txDate <= todayStr
            ReportPeriod.FORTNIGHT -> txDate in fortnightStartStr..todayStr
            ReportPeriod.THIS_MONTH -> txDate.startsWith(currentMonth)
            ReportPeriod.THIS_YEAR -> txDate.startsWith(currentYear)
            ReportPeriod.CUSTOM -> {
                val start = if (customStart.isNotBlank()) customStart else "2000-01-01"
                val end = if (customEnd.isNotBlank()) customEnd else "2099-12-31"
                txDate in start..end
            }
            ReportPeriod.ALL_TIME -> true
        }
    }
}

// Generate shareable text summary
fun generateReportShareSummary(
    reportType: ReportType,
    period: ReportPeriod,
    state: AppState,
    filteredTx: List<Transaction>,
    fbrSummary: FbrSummary,
    isUrdu: Boolean
): String {
    val shopName = state.profile.shopName
    val dateNow = FormatUtils.getTodayIsoDate()
    val periodStr = if (isUrdu) period.titleUr else period.titleEn

    val sb = StringBuilder()
    sb.appendLine("📊 $shopName - ${if (isUrdu) reportType.titleUr else reportType.titleEn}")
    sb.appendLine("📅 ${if (isUrdu) "مدت:" else "Period:"} $periodStr ($dateNow)")
    sb.appendLine("----------------------------------------")

    when (reportType) {
        ReportType.BALANCE_SHEET -> {
            val bs = AccountingEngine.calculateBalanceSheet(state)
            sb.appendLine("${if (isUrdu) "کل کاروباری اثاثہ جات:" else "Total Business Assets:"} ${FormatUtils.formatCurrency(bs.totalAssets)}")
            sb.appendLine("${if (isUrdu) "کل واجبات:" else "Total Liabilities:"} ${FormatUtils.formatCurrency(bs.totalLiabilities)}")
            sb.appendLine("${if (isUrdu) "خالص مالیت / ایکویٹی:" else "Total Equity:"} ${FormatUtils.formatCurrency(bs.totalEquity)}")
        }
        ReportType.SALES -> {
            val totalSales = filteredTx.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
            sb.appendLine("${if (isUrdu) "مجموعی فروخت:" else "Total Sales:"} ${FormatUtils.formatCurrency(totalSales)}")
            sb.appendLine("${if (isUrdu) "کل بلز:" else "Total Invoices:"} ${filteredTx.size}")
        }
        ReportType.NET_PROFIT -> {
            val profitResult = AccountingEngine.calculatePeriodProfit(filteredTx)
            sb.appendLine("${if (isUrdu) "فروخت:" else "Sales Revenue:"} ${FormatUtils.formatCurrency(profitResult.netRevenue)}")
            sb.appendLine("${if (isUrdu) "بیچے گئے مال کی لاگت (COGS):" else "Verified COGS:"} ${FormatUtils.formatCurrency(profitResult.verifiedCogs)}")
            sb.appendLine("${if (isUrdu) "مجموعی منافع:" else "Gross Profit:"} ${FormatUtils.formatCurrency(profitResult.grossProfit)}")
            sb.appendLine("${if (isUrdu) "اخراجات:" else "Operating Expenses:"} ${FormatUtils.formatCurrency(profitResult.operatingExpenses)}")
            sb.appendLine("${if (isUrdu) "خالص منافع:" else "Net Profit:"} ${FormatUtils.formatCurrency(profitResult.netProfit)}")
        }
        ReportType.COGS -> {
            val cogsRes = AccountingEngine.calculatePeriodCogs(filteredTx)
            val sales = filteredTx.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
            sb.appendLine("${if (isUrdu) "فروخت:" else "Sales Revenue:"} ${FormatUtils.formatCurrency(sales)}")
            sb.appendLine("${if (isUrdu) "بیچے گئے مال کی لاگت (COGS):" else "Verified COGS:"} ${FormatUtils.formatCurrency(cogsRes.verifiedCogs)}")
            sb.appendLine("${if (isUrdu) "مجموعی منافع:" else "Gross Profit:"} ${FormatUtils.formatCurrency(sales - cogsRes.verifiedCogs)}")
        }
        ReportType.CASH_FLOW -> {
            val cf = AccountingEngine.calculateCashFlow(filteredTx)
            sb.appendLine("${if (isUrdu) "کیش آمد (Inflow):" else "Cash Inflow:"} ${FormatUtils.formatCurrency(cf.cashInflow)}")
            sb.appendLine("${if (isUrdu) "کیش اخراج (Outflow):" else "Cash Outflow:"} ${FormatUtils.formatCurrency(cf.cashOutflow)}")
            sb.appendLine("${if (isUrdu) "خالص کیش فلو:" else "Net Cash Flow:"} ${FormatUtils.formatCurrency(cf.netCashFlow)}")
        }
        ReportType.CUSTOMER_LEDGER -> {
            val totalUdhaar = state.customers.sumOf { it.totalCredit }
            sb.appendLine("${if (isUrdu) "کل وصول طلب ادھار:" else "Total Udhaar Receivables:"} ${FormatUtils.formatCurrency(totalUdhaar)}")
            sb.appendLine("${if (isUrdu) "ادھار والے گاہک:" else "Debtors Count:"} ${state.customers.count { it.totalCredit > 0 }}")
        }
        ReportType.STOCK_INVENTORY -> {
            val totalStockVal = state.inventory.sumOf { it.quantity * it.purchasePrice }
            sb.appendLine("${if (isUrdu) "اسٹاک لاگت ویلیو:" else "Total Stock Valuation:"} ${FormatUtils.formatCurrency(totalStockVal)}")
            sb.appendLine("${if (isUrdu) "کل آئٹمز:" else "Total Items:"} ${state.inventory.size}")
        }
        else -> {
            sb.appendLine("${if (isUrdu) "خلاصہ:" else "Summary:"} Generated successfully.")
        }
    }

    sb.appendLine("----------------------------------------")
    sb.appendLine(if (isUrdu) "جنریٹڈ بذریعہ دکان مینیجر ایپ" else "Generated via Dukan Manager")
    return sb.toString()
}

// Generate CSV string
fun generateReportCsv(
    reportType: ReportType,
    state: AppState,
    filteredTx: List<Transaction>,
    fbrSummary: FbrSummary,
    isUrdu: Boolean
): String {
    val sb = StringBuilder()
    // UTF-8 BOM for Urdu support in Excel
    sb.append('\uFEFF')

    when (reportType) {
        ReportType.SALES, ReportType.PURCHASES, ReportType.EXPENSES -> {
            sb.appendLine("ID,Date,Party/Customer,Category,PaymentMethod,Amount,Notes")
            filteredTx.forEach { tx ->
                sb.appendLine("\"${tx.id}\",\"${tx.date}\",\"${tx.partyName ?: ""}\",\"${tx.category}\",\"${tx.paymentMethod.name}\",${tx.amount},\"${tx.notes ?: ""}\"")
            }
        }
        ReportType.CUSTOMER_LEDGER -> {
            sb.appendLine("ID,Name,Phone,TotalCredit,LastDate,Status")
            state.customers.forEach { c ->
                sb.appendLine("\"${c.id}\",\"${c.name}\",\"${c.phone}\",${c.totalCredit},\"${c.lastTransactionDate}\",\"${c.status ?: "ACTIVE"}\"")
            }
        }
        ReportType.SUPPLIER_LEDGER -> {
            sb.appendLine("ID,Name,Phone,TotalPayable,DueDate,Notes")
            state.suppliers.forEach { s ->
                sb.appendLine("\"${s.id}\",\"${s.name}\",\"${s.phone}\",${s.totalPayable},\"${s.dueDate ?: ""}\",\"${s.notes ?: ""}\"")
            }
        }
        ReportType.STOCK_INVENTORY -> {
            sb.appendLine("ID,Name,Category,Qty,Unit,PurchasePrice,SalePrice,TotalCostValue,TotalRetailValue")
            state.inventory.forEach { i ->
                val costVal = i.quantity * i.purchasePrice
                val retVal = i.quantity * i.salePrice
                sb.appendLine("\"${i.id}\",\"${i.name}\",\"${i.category}\",${i.quantity},\"${i.unit}\",${i.purchasePrice},${i.salePrice},$costVal,$retVal")
            }
        }
        else -> {
            sb.appendLine("Metric,Amount")
            sb.appendLine("\"Total Assets\",${fbrSummary.totalAssets}")
            sb.appendLine("\"Total Liabilities\",${fbrSummary.totalLiabilities}")
            sb.appendLine("\"Net Worth\",${fbrSummary.netWorth}")
        }
    }
    return sb.toString()
}

// Android native share intent
fun shareReportText(context: Context, text: String, title: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title)
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// Export CSV via share intent
fun exportReportCsv(context: Context, fileName: String, csvContent: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvContent)
            putExtra(Intent.EXTRA_TITLE, fileName)
            type = "text/csv"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export $fileName")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ExportReportDialog(
    reportType: ReportType,
    period: ReportPeriod,
    state: AppState,
    filteredTx: List<Transaction>,
    fbrSummary: FbrSummary,
    isUrdu: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(reportType.color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = reportType.color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (isUrdu) "رپورٹ ایکسپورٹ فارمیٹ منتخب کریں" else "Select Report Export Format",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = "${if (isUrdu) reportType.titleUr else reportType.titleEn} (${if (isUrdu) period.titleUr else period.titleEn})",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isUrdu) "رپورٹ کے لیے مطلوبہ فارمیٹ منتخب کریں:" else "Choose output format:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700
                )

                // Option 1: PDF Card
                val isPdf = selectedFormat == ExportFormat.PDF
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPdf) RedDanger.copy(alpha = 0.08f) else Slate50,
                    border = BorderStroke(if (isPdf) 2.dp else 1.dp, if (isPdf) RedDanger else Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFormat = ExportFormat.PDF }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = isPdf,
                            onClick = { selectedFormat = ExportFormat.PDF },
                            colors = RadioButtonDefaults.colors(selectedColor = RedDanger)
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(RedDanger.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = RedDanger, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUrdu) "PDF رپورٹ (.pdf)" else "PDF Report (.pdf)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = if (isUrdu) "پرنٹ یا سیو کریں - موبائل و پرنٹر کے لیے موزوں" else "Formatted A4 document for print & save",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }
                }

                // Option 2: Excel Card
                val isExcel = selectedFormat == ExportFormat.EXCEL
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isExcel) DukanGreenPrimary.copy(alpha = 0.08f) else Slate50,
                    border = BorderStroke(if (isExcel) 2.dp else 1.dp, if (isExcel) DukanGreenPrimary else Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFormat = ExportFormat.EXCEL }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = isExcel,
                            onClick = { selectedFormat = ExportFormat.EXCEL },
                            colors = RadioButtonDefaults.colors(selectedColor = DukanGreenPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(DukanGreenPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = DukanGreenPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isUrdu) "Excel شیٹ (.csv / ایکسل)" else "Excel Spreadsheet (.csv)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = if (isUrdu) "تمام کالمز اور ٹوٹل مائیکروسافٹ ایکسل و گوگل شیٹس کے لیے" else "Full tabular data compatible with MS Excel",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }
                }

                // Action buttons based on format
                Spacer(Modifier.height(4.dp))
                if (selectedFormat == ExportFormat.PDF) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                ReportExporter.printOrSavePdf(
                                    context = context,
                                    reportType = reportType,
                                    period = period,
                                    state = state,
                                    filteredTx = filteredTx,
                                    fbrSummary = fbrSummary,
                                    isUrdu = isUrdu
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isUrdu) "پرنٹ / Save PDF" else "Print / Save PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                ReportExporter.shareGeneratedPdfFile(
                                    context = context,
                                    reportType = reportType,
                                    period = period,
                                    state = state,
                                    filteredTx = filteredTx,
                                    fbrSummary = fbrSummary,
                                    isUrdu = isUrdu
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isUrdu) "پی ڈی ایف شیئر" else "Share PDF", fontSize = 11.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            onDismiss()
                            ReportExporter.exportExcelSpreadsheet(
                                context = context,
                                reportType = reportType,
                                period = period,
                                state = state,
                                filteredTx = filteredTx,
                                fbrSummary = fbrSummary,
                                isUrdu = isUrdu
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isUrdu) "Excel شیٹ جنریٹ کریں" else "Generate Excel Spreadsheet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isUrdu) "بند کریں" else "Cancel", color = Slate600)
            }
        }
    )
}

