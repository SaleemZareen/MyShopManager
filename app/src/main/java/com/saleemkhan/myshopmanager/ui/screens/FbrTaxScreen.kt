package com.saleemkhan.myshopmanager.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.*

enum class FbrTaxTab(val enLabel: String, val urLabel: String) {
    SUMMARY("Return Summary", "ریٹرن کا خلاصہ"),
    IRIS_MAPPINGS("IRIS Portal Codes", "FBR پورٹل فیلڈ کوڈز"),
    WEALTH_RECON("Wealth Reconciliation", "ویلتھ اسٹیٹمنٹ مطابقت"),
    WITHHOLDING_TAX("Withholding Tax & PSID", "ودہولڈنگ ٹیکس و چالان")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FbrTaxScreen(
    state: AppState,
    isUrdu: Boolean,
    onUpdateTaxRecord: (TaxRecord) -> Unit = {},
    onUpdateHouseholdExpenses: ((HouseholdExpenses) -> Unit)? = null,
    onUpdateOtherIncome: ((OtherIncome) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val selectedYear = state.taxRecord.selectedTaxYear.ifBlank { "2026" }
    var activeTab by remember { mutableStateOf(FbrTaxTab.SUMMARY) }
    var irisFilterSection by remember { mutableStateOf("ALL") } // "ALL", "TRADING", "EXPENSES", "BALANCE_SHEET"
    var irisSearchQuery by remember { mutableStateOf("") }

    // Dialog States
    var showWithholdingDialog by remember { mutableStateOf(false) }
    var showOpeningEquityDialog by remember { mutableStateOf(false) }
    var showHouseholdEditDialog by remember { mutableStateOf(false) }

    // Computations from TaxEngine
    val summary = remember(state, selectedYear) { TaxEngine.calculateFbrSummary(state) }
    val irisMappings = remember(summary, state) { TaxEngine.getIrisMappings(summary, state) }
    val wealthRecon = remember(summary, state, selectedYear) { TaxEngine.getWealthReconciliation(summary, state) }
    val withholdingData = remember(summary, state) { TaxEngine.getWithholdingTaxData(summary, state) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // ==========================================
        // 1. TOP HEADER & TAX YEAR SELECTOR
        // ==========================================
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFECFDF5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "FBR IRIS",
                                    tint = DukanGreenPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isUrdu) "ایف بی آر IRIS ٹیکس تیاری" else "FBR IRIS Tax & Wealth",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = if (isUrdu) "انکم ٹیکس ریٹرن اور ویلتھ اسٹیٹمنٹ تیاری" else "Income Tax Return & Wealth Statement Prep",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }

                        // Portal Quick Link
                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri("https://iris.fbr.gov.pk")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Visit: https://iris.fbr.gov.pk", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DukanGreenPrimary),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "FBR Portal",
                                tint = DukanGreenPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "IRIS Portal",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DukanGreenPrimary
                            )
                        }
                    }

                    Divider(color = Slate100, thickness = 1.dp)

                    // Tax Year Selector & Profile info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isUrdu) "ٹیکس سال:" else "Tax Year:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            listOf("2024", "2025", "2026").forEach { yr ->
                                val isSelected = selectedYear == yr
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) DukanGreenPrimary else Slate100)
                                        .clickable {
                                            onUpdateTaxRecord(state.taxRecord.copy(selectedTaxYear = yr))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = yr,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Slate700
                                    )
                                }
                            }
                        }

                        // NTN or Shop Badge
                        val ntn = state.profile.ntn.ifBlank { state.profile.cnic.ifBlank { "Unregistered" } }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Text(
                                text = "NTN/CNIC: $ntn",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate600,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. SUB-TABS ROW (4 TABS)
        // ==========================================
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FbrTaxTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        val tabIcon = when (tab) {
                            FbrTaxTab.SUMMARY -> Icons.Default.Analytics
                            FbrTaxTab.IRIS_MAPPINGS -> Icons.Default.AccountBalance
                            FbrTaxTab.WEALTH_RECON -> Icons.Default.Balance
                            FbrTaxTab.WITHHOLDING_TAX -> Icons.Default.ReceiptLong
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) DukanGreenPrimary else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = tabIcon,
                                    contentDescription = tab.enLabel,
                                    tint = if (isSelected) Color.White else Slate600,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isUrdu) tab.urLabel else tab.enLabel,
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

        // ==========================================
        // 3. TAB SPECIFIC CONTENT
        // ==========================================
        when (activeTab) {
            FbrTaxTab.SUMMARY -> {
                // Top KPI Summary Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaxKpiCard(
                            title = if (isUrdu) "کل سیلز (Turnover)" else "Total Sales",
                            code = "Code 7000",
                            amount = summary.sales,
                            color = Color(0xFF2563EB),
                            modifier = Modifier.weight(1f)
                        )
                        TaxKpiCard(
                            title = if (isUrdu) "خالص منافع (Net Profit)" else "Net Profit",
                            code = "Code 7050",
                            amount = summary.netProfit ?: 0.0,
                            color = DukanGreenPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TaxKpiCard(
                            title = if (isUrdu) "خالص دولت (Net Worth)" else "Net Worth",
                            code = "Code 7020",
                            amount = summary.netWorth,
                            color = Color(0xFF7C3AED),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Estimated Tax Liability Banner
                item {
                    val estTax = summary.estimatedIncomeTax
                    val totalAdj = withholdingData.totalAdjustableTax
                    val netPayable = (estTax - totalAdj).coerceAtLeast(0.0)

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (netPayable > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)),
                        border = BorderStroke(1.dp, if (netPayable > 0) Color(0xFFFECACA) else Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (netPayable > 0) Icons.Default.Payment else Icons.Default.CheckCircle,
                                    contentDescription = "Tax",
                                    tint = if (netPayable > 0) Color(0xFFDC2626) else DukanGreenPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isUrdu) "متوقع قابلِ ادائیگی انکم ٹیکس (FBR Slab)" else "Estimated Tax Liability (FBR Slabs)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (netPayable > 0) Color(0xFF991B1B) else DukanGreenHover
                                    )
                                    Text(
                                        text = if (isUrdu)
                                            "مجموعی ٹیکس: Rs. ${FormatUtils.formatCurrency(estTax)} | ایڈوانس کٹوتی: Rs. ${FormatUtils.formatCurrency(totalAdj)}"
                                        else
                                            "Gross: Rs. ${FormatUtils.formatCurrency(estTax)} | Deducted: Rs. ${FormatUtils.formatCurrency(totalAdj)}",
                                        fontSize = 10.sp,
                                        color = Slate600
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isUrdu) "خالص واجب الادا" else "Net to Pay",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(netPayable),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (netPayable > 0) Color(0xFFDC2626) else DukanGreenPrimary
                                )
                            }
                        }
                    }
                }

                // Income Statement Card (P&L)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "آمدن و منافع کا گوشوارہ (Income Statement)" else "Business Profit & Loss Statement (P&L)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = "Section 114",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DukanGreenPrimary
                                )
                            }

                            Divider(color = Slate100, thickness = 1.dp)

                            TaxItemDetailRow(
                                code = "Code 7000",
                                title = if (isUrdu) "مجموعی فروخت / ٹرن اوور (Gross Turnover)" else "Gross Sales / Turnover",
                                amount = summary.sales,
                                isUrdu = isUrdu
                            )
                            TaxItemDetailRow(
                                code = "Code 7011",
                                title = if (isUrdu) "فروخت شدہ مال کی لاگت (Cost of Sales / COGS)" else "Cost of Goods Sold (COGS)",
                                amount = summary.cogs,
                                isUrdu = isUrdu
                            )
                            TaxItemDetailRow(
                                code = "Code 7020",
                                title = if (isUrdu) "مجموعی منافع (Gross Profit)" else "Gross Profit",
                                amount = summary.grossProfit ?: 0.0,
                                isUrdu = isUrdu,
                                isHighlight = true,
                                highlightColor = Color(0xFFF0FDF4)
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isUrdu) "دکان کے آپریشنل و دفتری اخراجات:" else "Operating Deductions Breakdown:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600
                            )

                            val exp = summary.expensesBreakdown
                            TaxItemDetailRow(code = "Code 7031", title = if (isUrdu) "دکان کا کرایہ (Rent)" else "Shop / Warehouse Rent", amount = exp.rent, isUrdu = isUrdu, isSub = true)
                            TaxItemDetailRow(code = "Code 7032", title = if (isUrdu) "تنخواہیں و اجرت (Salaries)" else "Salaries & Wages", amount = exp.salaries, isUrdu = isUrdu, isSub = true)
                            TaxItemDetailRow(code = "Code 7033", title = if (isUrdu) "بجلی اور گیس کے بل (Utilities)" else "Electricity & Utilities", amount = exp.utilityBills, isUrdu = isUrdu, isSub = true)
                            TaxItemDetailRow(code = "Code 7034", title = if (isUrdu) "سواری و سفری اخراجات (Travel & Fuel)" else "Travelling & Vehicle Fuel", amount = exp.travellingFuel, isUrdu = isUrdu, isSub = true)
                            TaxItemDetailRow(code = "Code 7035", title = if (isUrdu) "فون و انٹرنیٹ (Communication)" else "Telephone & Internet", amount = exp.communication, isUrdu = isUrdu, isSub = true)
                            TaxItemDetailRow(code = "Code 7039", title = if (isUrdu) "متفرق اخراجات و نقصان (Sundry)" else "Other General Expenses", amount = exp.otherExpenses, isUrdu = isUrdu, isSub = true)

                            TaxItemDetailRow(
                                code = "Code 7040",
                                title = if (isUrdu) "کل اخراجات (Total Deductions)" else "Total Deductions & Expenses",
                                amount = exp.totalExpenses,
                                isUrdu = isUrdu,
                                isBold = true
                            )

                            Divider(color = Slate200, thickness = 1.dp)

                            TaxItemDetailRow(
                                code = "Code 7050",
                                title = if (isUrdu) "خالص قابلِ ٹیکس منافع (Net Taxable Profit)" else "Net Taxable Business Profit",
                                amount = summary.netProfit ?: 0.0,
                                isUrdu = isUrdu,
                                isHighlight = true,
                                highlightColor = Color(0xFFEFF6FF)
                            )
                        }
                    }
                }

                // Balance Sheet Snapshot Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "کاروباری اثاثہ جات و واجبات (Balance Sheet)" else "Balance Sheet & Wealth Statement",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = "Section 116",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF7C3AED)
                                )
                            }

                            Divider(color = Slate100, thickness = 1.dp)

                            TaxItemDetailRow(code = "Code 7001", title = if (isUrdu) "نقدی رقم (گلہ) اور بینک بیلنس" else "Cash in Hand & Bank Balances", amount = summary.cashAndBank, isUrdu = isUrdu)
                            TaxItemDetailRow(code = "Code 7002", title = if (isUrdu) "اختتامی مال تجارت (Closing Stock)" else "Stock-in-Trade (Inventory)", amount = summary.closingStockValue, isUrdu = isUrdu)
                            TaxItemDetailRow(code = "Code 7003", title = if (isUrdu) "کسٹمرز کے ذمہ ادھار (Receivables)" else "Trade Debtors / Customer Credit", amount = summary.totalReceivables, isUrdu = isUrdu)
                            TaxItemDetailRow(code = "Code 7004", title = if (isUrdu) "دکان کے فکسڈ اثاثے (Fixed Assets)" else "Business Fixed Assets (Equipment/Racks)", amount = (summary.totalBusinessAssets - summary.cashAndBank - summary.closingStockValue - summary.totalReceivables).coerceAtLeast(0.0), isUrdu = isUrdu)

                            TaxItemDetailRow(
                                code = "Total",
                                title = if (isUrdu) "مجموعی دکان کے اثاثے (Total Business Assets)" else "Total Business Assets",
                                amount = summary.totalBusinessAssets,
                                isUrdu = isUrdu,
                                isBold = true
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isUrdu) "واجبات (Liabilities):" else "Liabilities & Borrowings:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600
                            )

                            TaxItemDetailRow(code = "Code 7005", title = if (isUrdu) "سپلائرز کے واجبات (Trade Creditors)" else "Trade Creditors / Supplier Payables", amount = summary.totalSupplierPayables, isUrdu = isUrdu, isSub = true)
                            TaxItemDetailRow(code = "Code 7006", title = if (isUrdu) "کاروباری قرضے (Loans Payable)" else "Outstanding Business Borrowings", amount = summary.outstandingLoans, isUrdu = isUrdu, isSub = true)
                            TaxItemDetailRow(code = "Total", title = if (isUrdu) "مجموعی واجبات (Total Liabilities)" else "Total Liabilities", amount = summary.totalLiabilities, isUrdu = isUrdu, isBold = true)

                            Divider(color = Slate200, thickness = 1.dp)

                            TaxItemDetailRow(
                                code = "Code 7009",
                                title = if (isUrdu) "دکان کا اصل سرمایہ (Owner's Net Capital)" else "Owner's Net Capital / Equity",
                                amount = summary.ownerCapital ?: 0.0,
                                isUrdu = isUrdu,
                                isHighlight = true,
                                highlightColor = Color(0xFFFAF5FF)
                            )
                        }
                    }
                }
            }

            FbrTaxTab.IRIS_MAPPINGS -> {
                // Search and Filter Bar
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Search TextField
                            OutlinedTextField(
                                value = irisSearchQuery,
                                onValueChange = { irisSearchQuery = it },
                                placeholder = {
                                    Text(
                                        text = if (isUrdu) "کوڈ یا تفصیل سے تلاش کریں (مثلاً 7000، سیلز، کرایہ)..." else "Search by code or field (e.g., 7001, Rent)...",
                                        fontSize = 12.sp,
                                        color = Slate400
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Slate400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (irisSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { irisSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Slate500, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DukanGreenPrimary,
                                    unfocusedBorderColor = Slate200,
                                    focusedContainerColor = Slate50,
                                    unfocusedContainerColor = Slate50
                                ),
                                singleLine = true
                            )

                            // Section Filters
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "ALL" to (if (isUrdu) "تمام کوڈز" else "All Codes"),
                                    "TRADING" to (if (isUrdu) "تجارت و سیلز (Sec 114)" else "Trading Account"),
                                    "EXPENSES" to (if (isUrdu) "اخراجات (Sec 114)" else "Deductions"),
                                    "BALANCE_SHEET" to (if (isUrdu) "اثاثے و دولت (Sec 116)" else "Balance Sheet & Wealth")
                                ).forEach { (key, label) ->
                                    val isSelected = irisFilterSection == key
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { irisFilterSection = key },
                                        label = { Text(text = label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DukanGreenPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Helper Card for One-click copying
                item {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = Slate500, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isUrdu)
                                    "رہنمائی: کسی بھی فیلڈ کے ساتھ موجود کاپی بٹن دبا کر رقم براہ راست FBR پورٹل میں پیسٹ کریں۔"
                                else
                                    "Tip: Tap the Copy icon next to any field to copy its amount directly to clipboard for IRIS portal.",
                                fontSize = 11.sp,
                                color = Slate600
                            )
                        }
                    }
                }

                // Filtered IRIS Items
                val filteredMappings = irisMappings.filter { item ->
                    (irisFilterSection == "ALL" || item.section == irisFilterSection) &&
                    (irisSearchQuery.isBlank() ||
                     item.code.contains(irisSearchQuery, ignoreCase = true) ||
                     item.titleEn.contains(irisSearchQuery, ignoreCase = true) ||
                     item.titleUr.contains(irisSearchQuery, ignoreCase = true) ||
                     item.description.contains(irisSearchQuery, ignoreCase = true))
                }

                if (filteredMappings.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(imageVector = Icons.Default.SearchOff, contentDescription = "None", tint = Slate400, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = if (isUrdu) "کوئی کوڈ نہیں ملا" else "No matching IRIS codes found", fontSize = 13.sp, color = Slate600)
                            }
                        }
                    }
                } else {
                    items(filteredMappings) { item ->
                        IrisFieldCard(
                            item = item,
                            isUrdu = isUrdu,
                            onCopy = {
                                val textToCopy = item.amount.toLong().toString()
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                Toast.makeText(context, "${item.code}: Rs. $textToCopy copied!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            FbrTaxTab.WEALTH_RECON -> {
                // Wealth Reconciliation Status Card
                item {
                    val isBalanced = wealthRecon.isBalanced
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isBalanced) Color(0xFFECFDF5) else Color(0xFFFEF2F2)),
                        border = BorderStroke(1.dp, if (isBalanced) Color(0xFFA7F3D0) else Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = "Status",
                                        tint = if (isBalanced) DukanGreenPrimary else Color(0xFFDC2626),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isBalanced)
                                            (if (isUrdu) "ویلتھ اسٹیٹمنٹ مطابقت متوازن ہے" else "Wealth Statement Balanced")
                                        else
                                            (if (isUrdu) "غیر واضح فرق موجود ہے (Warning)" else "Unexplained Difference Warning"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isBalanced) DukanGreenHover else Color(0xFF991B1B)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isBalanced) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                                ) {
                                    Text(
                                        text = if (isBalanced) (if (isUrdu) "محفوظ" else "Safe") else (if (isUrdu) "نوٹس رسک" else "Notice Risk"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBalanced) DukanGreenHover else Color(0xFFDC2626),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (isBalanced)
                                    (if (isUrdu)
                                        "ماشاءاللہ! آپ کی آمدن، گھریلو اخراجات اور اثاثوں کا اضافہ برابر ہے۔ FBR آڈٹ نوٹس کا کوئی خطرہ نہیں۔"
                                    else
                                        "Excellent! Total income minus living expenses aligns with the increase in wealth within acceptable threshold.")
                                else
                                    (if (isUrdu)
                                        "وارننگ: آپ کی آمدن اور اثاثوں کے اضافے میں 50,000 سے زائد کا فرق ہے۔ FBR نوٹس سے بچنے کے لیے گھریلو اخراجات یا کیپیٹل درست کریں۔"
                                    else
                                        "Notice Risk: Unexplained gap exceeds Rs. 50,000 threshold. Adjust living expenses or capital introduced before filing."),
                                fontSize = 11.sp,
                                color = if (isBalanced) Slate600 else Color(0xFF7F1D1D)
                            )

                            Divider(color = if (isBalanced) Color(0xFFA7F3D0) else Color(0xFFFECACA), thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "مطابقت کا فرق (Difference):" else "Unexplained Wealth Difference:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate700
                                )
                                Text(
                                    text = "Rs. ${FormatUtils.formatCurrency(wealthRecon.unexplainedDifference)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isBalanced) DukanGreenPrimary else Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }

                // Step-by-Step Reconciliation Equation Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "ویلتھ ری کنسیلیشن مساوات (FBR Equation)" else "Wealth Reconciliation Breakdown",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = "IRIS Sec 116(1)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate500
                                )
                            }

                            Divider(color = Slate100, thickness = 1.dp)

                            // 1. Opening Wealth
                            TaxReconStepRow(
                                stepNumber = "1",
                                title = if (isUrdu) "افتتاحی خالص دولت (Opening Wealth)" else "Opening Net Wealth (Previous Year)",
                                amount = wealthRecon.openingWealth,
                                sign = "",
                                isUrdu = isUrdu,
                                onActionClick = { showOpeningEquityDialog = true },
                                actionLabel = if (isUrdu) "تبدیل" else "Edit"
                            )

                            // 2. Net Profit
                            TaxReconStepRow(
                                stepNumber = "2",
                                title = if (isUrdu) "خالص کاروباری منافع (Net Business Profit)" else "Net Business Profit During Year",
                                amount = wealthRecon.netBusinessProfit,
                                sign = "(+)",
                                isUrdu = isUrdu
                            )

                            // 3. Other Incomes
                            TaxReconStepRow(
                                stepNumber = "3",
                                title = if (isUrdu) "دیگر ذرائع آمدن (Other Incomes: Rent/Agri)" else "Other Incomes (Rental, Agri, Bank)",
                                amount = wealthRecon.otherIncomeTotal,
                                sign = "(+)",
                                isUrdu = isUrdu
                            )

                            // 4. Capital Inflow
                            TaxReconStepRow(
                                stepNumber = "4",
                                title = if (isUrdu) "دکان میں شامل نیا سرمایہ (Capital Introduced)" else "Capital Introduced / Fresh Funds",
                                amount = wealthRecon.capitalInflow,
                                sign = "(+)",
                                isUrdu = isUrdu
                            )

                            // 5. Living Expenses
                            TaxReconStepRow(
                                stepNumber = "5",
                                title = if (isUrdu) "سالانہ گھریلو و ذاتی اخراجات (Household & Drawings)" else "Household Expenses & Personal Drawings",
                                amount = wealthRecon.householdExpensesTotal,
                                sign = "(-)",
                                isUrdu = isUrdu,
                                onActionClick = { showHouseholdEditDialog = true },
                                actionLabel = if (isUrdu) "تبدیل" else "Edit"
                            )

                            // 6. Taxes Paid
                            TaxReconStepRow(
                                stepNumber = "6",
                                title = if (isUrdu) "ادا شدہ انکم و ودہولڈنگ ٹیکس (Taxes Paid)" else "Taxes Paid / Withheld During Year",
                                amount = wealthRecon.taxesPaidTotal,
                                sign = "(-)",
                                isUrdu = isUrdu
                            )

                            Divider(color = Slate200, thickness = 1.dp)

                            // Expected Wealth
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isUrdu) "(=) حسابی متوقع دولت (Expected Wealth)" else "(=) Expected Closing Net Wealth",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Slate800
                                    )
                                    Text(
                                        text = if (isUrdu) "شروع کی دولت + کل آمدن - اخراجات" else "Opening + Incomes - Outflows",
                                        fontSize = 10.sp,
                                        color = Slate500
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatCurrency(wealthRecon.expectedNetWealth),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Slate900
                                )
                            }

                            // Actual Wealth
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isUrdu) "دستیاب حقیقی خالص دولت (Actual Net Worth)" else "Actual Closing Net Worth (Ledger)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Slate800
                                    )
                                    Text(
                                        text = if (isUrdu) "تمام اثاثہ جات منفی تمام واجبات" else "Total Assets - Total Liabilities",
                                        fontSize = 10.sp,
                                        color = Slate500
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatCurrency(wealthRecon.actualNetWealth),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF2563EB)
                                )
                            }
                        }
                    }
                }
            }

            FbrTaxTab.WITHHOLDING_TAX -> {
                // Withholding Overview Card
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
                                Column {
                                    Text(
                                        text = if (isUrdu) "ایڈوانس کٹوتی شدہ ٹیکس (Adjustable Tax)" else "Adjustable Advance Tax Deductions",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = if (isUrdu) "بجلی، فون، بینک اور PSID چالان کا ریکارڈ" else "Electricity, phone, banking & PSID records",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }

                                Button(
                                    onClick = { showWithholdingDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isUrdu) "ٹیکس درج کریں" else "Update Tax", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Divider(color = Slate100, thickness = 1.dp)

                            // KPIs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(text = if (isUrdu) "کل کٹوتی شدہ ٹیکس" else "Total Deducted", fontSize = 10.sp, color = Slate600)
                                        Text(
                                            text = "Rs. ${FormatUtils.formatCurrency(withholdingData.totalAdjustableTax)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = DukanGreenHover
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(text = if (isUrdu) "متوقع فائنل ٹیکس" else "Gross Liability", fontSize = 10.sp, color = Slate600)
                                        Text(
                                            text = "Rs. ${FormatUtils.formatCurrency(withholdingData.estimatedIncomeTax)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1D4ED8)
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (withholdingData.netTaxPayable > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                                    border = BorderStroke(1.dp, if (withholdingData.netTaxPayable > 0) Color(0xFFFECACA) else Color(0xFFA7F3D0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(text = if (isUrdu) "خالص ادائیگی" else "Net to Pay", fontSize = 10.sp, color = Slate600)
                                        Text(
                                            text = "Rs. ${FormatUtils.formatCurrency(withholdingData.netTaxPayable)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (withholdingData.netTaxPayable > 0) Color(0xFFDC2626) else DukanGreenHover
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Detailed Deductions List
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isUrdu) "ودہولڈنگ ٹیکس کی مدات (Sections Breakdown)" else "Advance Tax Deductions Breakdown",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Slate900
                            )

                            Divider(color = Slate100, thickness = 1.dp)

                            WithholdingItemRow(
                                section = "Section 235",
                                title = if (isUrdu) "بجلی کے کمرشل بلوں پر کٹا ہوا ٹیکس" else "Electricity Utility Bills Advance Tax",
                                amount = withholdingData.utilityWithholding,
                                isUrdu = isUrdu
                            )
                            WithholdingItemRow(
                                section = "Section 236",
                                title = if (isUrdu) "موبائل فون لوڈ اور انٹرنیٹ پر کٹا ہوا ٹیکس" else "Mobile Phone & Broadband Advance Tax",
                                amount = withholdingData.mobileWithholding,
                                isUrdu = isUrdu
                            )
                            WithholdingItemRow(
                                section = "Section 231AB / 236P",
                                title = if (isUrdu) "بینک کیش نکالنے اور بینکنگ ٹرانزیکشنز ٹیکس" else "Cash Withdrawal & Banking Transactions Tax",
                                amount = withholdingData.bankWithholding,
                                isUrdu = isUrdu
                            )
                            WithholdingItemRow(
                                section = "Section 234",
                                title = if (isUrdu) "موٹر گاڑی ٹوکن ٹیکس ایڈوانس کٹوتی" else "Motor Vehicle Annual Token Tax",
                                amount = withholdingData.vehicleTax,
                                isUrdu = isUrdu
                            )
                            WithholdingItemRow(
                                section = "Section 147",
                                title = if (isUrdu) "سہ ماہی ایڈوانس انکم ٹیکس چالان" else "Quarterly Advance Income Tax Paid",
                                amount = withholdingData.advanceTax,
                                isUrdu = isUrdu
                            )

                            // PSID Challan Details
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (isUrdu) "PSID / CPR چالان ادائیگیاں" else "PSID / CPR Challan Payments",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Slate800
                                        )
                                        if (withholdingData.psidChallanNumber.isNotBlank()) {
                                            Text(
                                                text = "CPR No: ${withholdingData.psidChallanNumber}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF2563EB)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Rs. ${FormatUtils.formatCurrency(withholdingData.psidPayments)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate900
                                    )
                                }
                            }

                            WithholdingItemRow(
                                section = "Other Sections",
                                title = if (isUrdu) "دیگر ودہولڈنگ سرٹیفکیٹس و جائیداد ٹیکس" else "Other Withholding Tax Certificates",
                                amount = withholdingData.otherWithholding,
                                isUrdu = isUrdu
                            )
                        }
                    }
                }

                // FBR ATL Instructions Card
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF1D4ED8), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isUrdu) "ایکٹو ٹیکس پیئر لسٹ (ATL) میں شمولیت کی تصدیق" else "FBR Active Taxpayer List (ATL) Verification",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF1E40AF)
                                )
                            }
                            Text(
                                text = if (isUrdu)
                                    "ریگولر ریٹرن جمع کروانے کے بعد اپنا شناختی کارڈ نمبر 9966 پر SMS کریں (مثلاً: ATL 1234567890123) یا FBR پورٹل پر اپنا سٹیٹس دیکھیں۔"
                                else
                                    "After filing your return, check your active filer status by sending 'ATL <CNIC>' to 9966 via SMS or visit the online FBR portal.",
                                fontSize = 11.sp,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 4. DIALOGS
    // ==========================================

    // Withholding Tax Entry Dialog
    if (showWithholdingDialog) {
        WithholdingTaxDialog(
            currentRecord = state.taxRecord,
            isUrdu = isUrdu,
            onDismiss = { showWithholdingDialog = false },
            onSave = { updatedRecord ->
                onUpdateTaxRecord(updatedRecord)
                showWithholdingDialog = false
                Toast.makeText(context, if (isUrdu) "ودہولڈنگ ٹیکس ریکارڈ محفوظ ہو گیا" else "Tax deductions saved successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Opening Equity Dialog
    if (showOpeningEquityDialog) {
        val currentEquity = state.taxRecord.openingOwnerEquity[selectedYear] ?: 120000.0
        var equityText by remember { mutableStateOf(currentEquity.toLong().toString()) }

        Dialog(onDismissRequest = { showOpeningEquityDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isUrdu) "افتتاحی سرمایہ / دولت سیٹ کریں ($selectedYear)" else "Set Opening Net Wealth ($selectedYear)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Slate900
                    )
                    Text(
                        text = if (isUrdu) "ٹیکس سال کے آغاز پر پچھلے سال کی اختتامی خالص دولت درج کریں۔" else "Enter previous year's closing wealth as opening wealth for this year.",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    OutlinedTextField(
                        value = equityText,
                        onValueChange = { equityText = it },
                        label = { Text(if (isUrdu) "افتتاحی رقم (Rs.)" else "Opening Amount (Rs.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showOpeningEquityDialog = false }) {
                            Text(if (isUrdu) "منسوخ" else "Cancel", color = Slate600)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val newAmount = equityText.toDoubleOrNull() ?: currentEquity
                                val updatedMap = state.taxRecord.openingOwnerEquity.toMutableMap()
                                updatedMap[selectedYear] = newAmount
                                onUpdateTaxRecord(state.taxRecord.copy(openingOwnerEquity = updatedMap))
                                showOpeningEquityDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isUrdu) "محفوظ کریں" else "Save")
                        }
                    }
                }
            }
        }
    }

    // Household Living Expenses Dialog
    if (showHouseholdEditDialog) {
        val hh = state.householdExpenses
        var monthlyExp by remember { mutableStateOf(hh.monthlyHouseholdExpense.toLong().toString()) }
        var drawings by remember { mutableStateOf(hh.personalDrawings.toLong().toString()) }

        Dialog(onDismissRequest = { showHouseholdEditDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isUrdu) "گھریلو اخراجات و ذاتی رقوم (Drawings)" else "Household Expenses & Personal Drawings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Slate900
                    )
                    Text(
                        text = if (isUrdu) "ویلتھ اسٹیٹمنٹ کو برابر رکھنے کے لیے ماہانہ گھریلو راشن اور گلے سے ذاتی ضرورت کے لیے نکالی گئی رقم درج کریں۔" else "Update monthly living expense and personal drawings to balance wealth reconciliation.",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    OutlinedTextField(
                        value = monthlyExp,
                        onValueChange = { monthlyExp = it },
                        label = { Text(if (isUrdu) "ماہانہ گھریلو خرچہ (Rs.)" else "Monthly Household Expense (Rs.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = drawings,
                        onValueChange = { drawings = it },
                        label = { Text(if (isUrdu) "سالانہ ذاتی نکلوائی گئی رقم (Drawings)" else "Annual Personal Drawings (Rs.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showHouseholdEditDialog = false }) {
                            Text(if (isUrdu) "منسوخ" else "Cancel", color = Slate600)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val mExp = monthlyExp.toDoubleOrNull() ?: hh.monthlyHouseholdExpense
                                val draw = drawings.toDoubleOrNull() ?: hh.personalDrawings
                                val updatedHh = hh.copy(
                                    monthlyHouseholdExpense = mExp,
                                    personalDrawings = draw
                                )
                                onUpdateHouseholdExpenses?.invoke(updatedHh)
                                showHouseholdEditDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isUrdu) "محفوظ کریں" else "Save")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPONENT HELPERS
// ==========================================

@Composable
fun TaxKpiCard(
    title: String,
    code: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = code, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = title, fontSize = 10.sp, color = Slate600, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = FormatUtils.formatCurrency(amount),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = Slate900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TaxItemDetailRow(
    code: String,
    title: String,
    amount: Double,
    isUrdu: Boolean,
    isBold: Boolean = false,
    isHighlight: Boolean = false,
    highlightColor: Color = Color(0xFFF8FAFC),
    isSub: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHighlight) highlightColor else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = if (isSub) 12.dp else 4.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isHighlight) Color.White else Slate100
            ) {
                Text(
                    text = code,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighlight) DukanGreenHover else Slate600,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = if (isBold || isHighlight) 12.sp else 11.sp,
                fontWeight = if (isBold || isHighlight) FontWeight.Bold else FontWeight.Normal,
                color = if (isHighlight) Slate900 else Slate700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = FormatUtils.formatCurrency(amount),
            fontSize = if (isBold || isHighlight) 13.sp else 12.sp,
            fontWeight = if (isBold || isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = if (isHighlight) DukanGreenHover else Slate900
        )
    }
}

@Composable
fun IrisFieldCard(
    item: IrisMappingItem,
    isUrdu: Boolean,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                    ) {
                        Text(
                            text = item.code,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DukanGreenHover,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isUrdu) item.titleUr else item.titleEn,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Slate900
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    fontSize = 10.sp,
                    color = Slate500,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Rs. ${FormatUtils.formatCurrency(item.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = Slate900
                )
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = DukanGreenPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TaxReconStepRow(
    stepNumber: String,
    title: String,
    amount: Double,
    sign: String,
    isUrdu: Boolean,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Slate100,
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = stepNumber, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sign.isNotBlank()) {
                        Text(text = "$sign ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (sign == "(+)") DukanGreenPrimary else Color(0xFFDC2626))
                    }
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        color = Slate800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = FormatUtils.formatCurrency(amount),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )

            if (onActionClick != null && actionLabel != null) {
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = onActionClick,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(text = actionLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WithholdingItemRow(
    section: String,
    title: String,
    amount: Double,
    isUrdu: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = section, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DukanGreenPrimary)
            Text(text = title, fontSize = 11.sp, color = Slate700, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = "Rs. ${FormatUtils.formatCurrency(amount)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Slate900
        )
    }
}

@Composable
fun WithholdingTaxDialog(
    currentRecord: TaxRecord,
    isUrdu: Boolean,
    onDismiss: () -> Unit,
    onSave: (TaxRecord) -> Unit
) {
    var utility by remember { mutableStateOf(currentRecord.utilityWithholding.toLong().toString()) }
    var mobile by remember { mutableStateOf(currentRecord.mobileWithholding.toLong().toString()) }
    var bank by remember { mutableStateOf(currentRecord.bankWithholding.toLong().toString()) }
    var vehicle by remember { mutableStateOf(currentRecord.vehicleTax.toLong().toString()) }
    var advance by remember { mutableStateOf(currentRecord.advanceTax.toLong().toString()) }
    var psidPay by remember { mutableStateOf(currentRecord.psidPayments.toLong().toString()) }
    var psidNo by remember { mutableStateOf(currentRecord.psidChallanNumber) }
    var otherTax by remember { mutableStateOf(currentRecord.otherTaxCertificates.toLong().toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isUrdu) "ایڈوانس کٹوتی شدہ ٹیکس کا اندراج" else "Record Withholding / Advance Taxes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Slate900
                )
                Text(
                    text = if (isUrdu) "بجلی کے بلوں، موبائل، بینک اور PSID چالان پر کٹی ہوئی رقوم درج کریں۔" else "Enter withholding tax deducted on utilities, telecom, bank and PSID challans.",
                    fontSize = 11.sp,
                    color = Slate500
                )

                Divider(color = Slate200, thickness = 1.dp)

                // Fields
                OutlinedTextField(
                    value = utility,
                    onValueChange = { utility = it },
                    label = { Text(if (isUrdu) "بجلی کے بلوں پر ٹیکس (Sec 235)" else "Electricity Bill Tax (Sec 235)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text(if (isUrdu) "موبائل و انٹرنیٹ ٹیکس (Sec 236)" else "Mobile & Telecom Tax (Sec 236)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = bank,
                    onValueChange = { bank = it },
                    label = { Text(if (isUrdu) "بینک کیش نکالنے کا ٹیکس (Sec 231AB)" else "Banking Withholding Tax (Sec 231AB)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = advance,
                    onValueChange = { advance = it },
                    label = { Text(if (isUrdu) "سہ ماہی ایڈوانس ٹیکس چالان (Sec 147)" else "Advance Tax Installments (Sec 147)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = psidPay,
                        onValueChange = { psidPay = it },
                        label = { Text(if (isUrdu) "PSID رقم (Rs.)" else "PSID Paid (Rs.)", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = psidNo,
                        onValueChange = { psidNo = it },
                        label = { Text("PSID / CPR No", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isUrdu) "منسوخ" else "Cancel", color = Slate600)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val u = utility.toDoubleOrNull() ?: 0.0
                            val m = mobile.toDoubleOrNull() ?: 0.0
                            val b = bank.toDoubleOrNull() ?: 0.0
                            val v = vehicle.toDoubleOrNull() ?: 0.0
                            val a = advance.toDoubleOrNull() ?: 0.0
                            val p = psidPay.toDoubleOrNull() ?: 0.0
                            val o = otherTax.toDoubleOrNull() ?: 0.0

                            val updated = currentRecord.copy(
                                utilityWithholding = u,
                                mobileWithholding = m,
                                bankWithholding = b,
                                vehicleTax = v,
                                advanceTax = a,
                                psidPayments = p,
                                psidChallanNumber = psidNo,
                                otherTaxCertificates = o,
                                adjustableTax = u + m + b + v + a + p + o
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isUrdu) "محفوظ کریں" else "Save Tax")
                    }
                }
            }
        }
    }
}
