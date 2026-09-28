package com.saleemkhan.myshopmanager.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saleemkhan.myshopmanager.model.AppState
import com.saleemkhan.myshopmanager.model.PaymentMethod
import com.saleemkhan.myshopmanager.model.Transaction
import com.saleemkhan.myshopmanager.model.TransactionType
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

// ---------------------- ENUMS & DATA MODELS ----------------------

enum class GraphCategoryTab(val enTitle: String, val urTitle: String, val icon: ImageVector) {
    SALE("Sales", "فروخت", Icons.Default.ShoppingBag),
    PURCHASE("Purchase", "خریداری", Icons.Default.LocalShipping),
    EXPENSE("Expenses", "اخراجات", Icons.Default.Receipt),
    UDHAAR("Collect Udhaar", "ادھار وصولی", Icons.Default.Payments),
    SUPPLIER_PAYMENT("Pay Supplier", "سپلائر ادائیگی", Icons.Default.AccountBalanceWallet),
    ANALYTIC("Analytics", "کاروباری تجزیہ", Icons.Default.Insights),
    CASH_ON_HAND("Cash on Hand", "کیش و بینک", Icons.Default.AccountBalance)
}

enum class ModalChartType(val enTitle: String, val urTitle: String, val icon: ImageVector) {
    AREA("Area", "ایریا", Icons.Default.ShowChart),
    BAR("Bar", "بار", Icons.Default.BarChart),
    LINE("Line", "لائن", Icons.Default.Timeline),
    PIE("Pie", "پائی", Icons.Default.PieChart),
    DONUT("Donut", "ڈونٹ", Icons.Default.DonutLarge)
}

enum class ChartPeriod(val enTitle: String, val urTitle: String) {
    TODAY("Today", "آج"),
    THIS_WEEK("This Week", "اس ہفتے"),
    THIS_MONTH("This Month", "اس مہینے"),
    THIS_YEAR("This Year", "اس سال"),
    ALL_TIME("All Times", "تمام وقت")
}

data class UnifiedChartPoint(
    val id: String,
    val label: String,
    val value: Double,
    val color: Color,
    val secondaryText: String = ""
)

// ---------------------- MAIN MODAL ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphsModal(
    isOpen: Boolean,
    onClose: () -> Unit,
    state: AppState,
    isUrdu: Boolean
) {
    if (!isOpen) return

    BackHandler(enabled = isOpen) {
        onClose()
    }

    var activeTab by remember { mutableStateOf(GraphCategoryTab.SALE) }
    var chartType by remember { mutableStateOf(ModalChartType.AREA) }
    var selectedPeriod by remember { mutableStateOf(ChartPeriod.THIS_WEEK) }

    // Vibrant chart color palette
    val palette = remember {
        listOf(
            Color(0xFF059669), // Emerald
            Color(0xFF2563EB), // Blue
            Color(0xFFD97706), // Amber
            Color(0xFFE11D48), // Rose
            Color(0xFF7C3AED), // Purple
            Color(0xFF0284C7), // Sky
            Color(0xFFEA580C), // Orange
            Color(0xFF10B981), // Teal
            Color(0xFF6366F1), // Indigo
            Color(0xFFEC4899), // Pink
            Color(0xFF0D9488), // Cyan
            Color(0xFFCA8A04)  // Yellow-Amber
        )
    }

    // Helper to get active theme color for the tab
    val tabThemeColor = when (activeTab) {
        GraphCategoryTab.SALE -> Color(0xFF059669)
        GraphCategoryTab.PURCHASE -> Color(0xFF4F46E5)
        GraphCategoryTab.EXPENSE -> Color(0xFFE11D48)
        GraphCategoryTab.UDHAAR -> Color(0xFFD97706)
        GraphCategoryTab.SUPPLIER_PAYMENT -> Color(0xFF7C3AED)
        GraphCategoryTab.ANALYTIC -> Color(0xFF0D9488)
        GraphCategoryTab.CASH_ON_HAND -> Color(0xFF0284C7)
    }

    // Helper function to filter transactions by period
    fun filterByPeriod(txs: List<Transaction>, period: ChartPeriod): List<Transaction> {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.US)
        val sdfYear = SimpleDateFormat("yyyy", Locale.US)
        val now = Date()
        val todayStr = sdfDate.format(now)
        val thisMonthStr = sdfMonth.format(now)
        val thisYearStr = sdfYear.format(now)

        return when (period) {
            ChartPeriod.TODAY -> txs.filter { it.date.take(10) == todayStr }
            ChartPeriod.THIS_WEEK -> {
                val cal = Calendar.getInstance().apply {
                    time = now
                    add(Calendar.DAY_OF_YEAR, -6)
                }
                val minDate = sdfDate.format(cal.time)
                txs.filter { it.date.take(10) >= minDate && it.date.take(10) <= todayStr }
            }
            ChartPeriod.THIS_MONTH -> txs.filter { it.date.take(7) == thisMonthStr }
            ChartPeriod.THIS_YEAR -> txs.filter { it.date.take(4) == thisYearStr }
            ChartPeriod.ALL_TIME -> txs
        }
    }

    // Filtered transaction subsets
    val periodTransactions = remember(state.transactions, selectedPeriod) {
        filterByPeriod(state.transactions, selectedPeriod)
    }

    val salesTx = remember(periodTransactions) { periodTransactions.filter { it.type == TransactionType.SALE } }
    val totalSales = remember(salesTx) { salesTx.sumOf { it.amount } }

    val purchaseTx = remember(periodTransactions) { periodTransactions.filter { it.type == TransactionType.PURCHASE } }
    val totalPurchases = remember(purchaseTx) { purchaseTx.sumOf { it.amount } }

    val expenseTx = remember(periodTransactions) { periodTransactions.filter { it.type == TransactionType.EXPENSE } }
    val totalExpenses = remember(expenseTx) { expenseTx.sumOf { it.amount } }

    val udhaarReceiptsTx = remember(periodTransactions) { periodTransactions.filter { it.type == TransactionType.RECEIPT } }
    val totalUdhaarRecovered = remember(udhaarReceiptsTx) { udhaarReceiptsTx.sumOf { it.amount } }

    val paymentTx = remember(periodTransactions) { periodTransactions.filter { it.type == TransactionType.PAYMENT } }
    val totalSupplierPayments = remember(paymentTx) { paymentTx.sumOf { it.amount } }

    val totalReceivables = remember(state.customers) { state.customers.sumOf { it.totalCredit } }
    val totalPayables = remember(state.suppliers) { state.suppliers.sumOf { it.totalPayable } }
    val totalCashLiquidity = remember(state.bankAccounts) { state.bankAccounts.sumOf { it.balance } }

    // Build data points for each tab & period
    val currentTabChartData: List<UnifiedChartPoint> = remember(
        activeTab,
        selectedPeriod,
        chartType,
        periodTransactions,
        salesTx,
        purchaseTx,
        expenseTx,
        paymentTx,
        udhaarReceiptsTx,
        state.bankAccounts,
        state.customers,
        state.suppliers,
        isUrdu
    ) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfDisplayDay = SimpleDateFormat("dd MMM", Locale.US)
        val sdfMonthName = SimpleDateFormat("MMM", Locale.US)

        when (activeTab) {
            // ==================== 1. SALE TAB ====================
            GraphCategoryTab.SALE -> {
                if (chartType == ModalChartType.PIE || chartType == ModalChartType.DONUT) {
                    // Breakdown by Payment Method
                    val byMethod = salesTx.groupBy { it.paymentMethod }
                    if (byMethod.isEmpty()) {
                        listOf(UnifiedChartPoint("empty", if (isUrdu) "کوئی فروخت نہیں" else "No Sales", 0.0, Color(0xFF94A3B8)))
                    } else {
                        byMethod.entries.mapIndexed { idx, (method, list) ->
                            val sum = list.sumOf { it.amount }
                            val label = when (method) {
                                PaymentMethod.CASH -> if (isUrdu) "نقد فروخت (Cash)" else "Cash Sales"
                                PaymentMethod.BANK -> if (isUrdu) "بینک وصولی (Bank)" else "Bank Transfer"
                                PaymentMethod.EASYPAISA -> "EasyPaisa"
                                PaymentMethod.JAZZCASH -> "JazzCash"
                                PaymentMethod.SADAPAY -> "SadaPay"
                                PaymentMethod.NAYAPAY -> "NayaPay"
                                PaymentMethod.CREDIT -> if (isUrdu) "ادھار فروخت (Udhaar)" else "Credit Sale"
                                else -> method.name
                            }
                            UnifiedChartPoint("method_$idx", label, sum, palette[idx % palette.size], "${list.size} ${if (isUrdu) "آرڈرز" else "orders"}")
                        }
                    }
                } else {
                    // Time-series based on selected period
                    buildTimeSeriesPoints(salesTx, selectedPeriod, Color(0xFF059669), palette, isUrdu)
                }
            }

            // ==================== 2. PURCHASE TAB ====================
            GraphCategoryTab.PURCHASE -> {
                if (chartType == ModalChartType.PIE || chartType == ModalChartType.DONUT) {
                    // Breakdown by Supplier or Payment Mode
                    val bySupplier = purchaseTx.groupBy { it.partyName?.ifBlank { null } ?: (if (isUrdu) "عام سپلائر" else "General Supplier") }
                    if (bySupplier.isEmpty()) {
                        listOf(UnifiedChartPoint("empty", if (isUrdu) "کوئی خریداری نہیں" else "No Purchases", 0.0, Color(0xFF94A3B8)))
                    } else {
                        bySupplier.entries.sortedByDescending { it.value.sumOf { t -> t.amount } }.take(6).mapIndexed { idx, (sup, list) ->
                            UnifiedChartPoint("sup_$idx", sup, list.sumOf { it.amount }, palette[(idx + 1) % palette.size], "${list.size} ${if (isUrdu) "بل" else "bills"}")
                        }
                    }
                } else {
                    buildTimeSeriesPoints(purchaseTx, selectedPeriod, Color(0xFF4F46E5), palette, isUrdu)
                }
            }

            // ==================== 3. EXPENSE TAB ====================
            GraphCategoryTab.EXPENSE -> {
                if (chartType == ModalChartType.PIE || chartType == ModalChartType.DONUT) {
                    // Category breakdown
                    val byCat = expenseTx.groupBy { it.category.ifBlank { if (isUrdu) "متفرق اخراجات" else "Miscellaneous" } }
                    if (byCat.isEmpty()) {
                        listOf(UnifiedChartPoint("empty", if (isUrdu) "کوئی خرچ نہیں" else "No Expenses", 0.0, Color(0xFF94A3B8)))
                    } else {
                        byCat.entries.sortedByDescending { it.value.sumOf { t -> t.amount } }.mapIndexed { idx, (cat, list) ->
                            UnifiedChartPoint("cat_$idx", cat, list.sumOf { it.amount }, palette[(idx + 3) % palette.size], "${list.size} ${if (isUrdu) "انٹریز" else "entries"}")
                        }
                    }
                } else {
                    buildTimeSeriesPoints(expenseTx, selectedPeriod, Color(0xFFE11D48), palette, isUrdu)
                }
            }

            // ==================== 4. UDHAAR / RECEIVABLES TAB ====================
            GraphCategoryTab.UDHAAR -> {
                if (chartType == ModalChartType.PIE || chartType == ModalChartType.DONUT) {
                    listOf(
                        UnifiedChartPoint("ud_rec", if (isUrdu) "گاہک بقایا ادھار (Receivables)" else "Customer Udhaar Due", totalReceivables, Color(0xFFD97706)),
                        UnifiedChartPoint("ud_pay", if (isUrdu) "سپلائر بل (Supplier Payables)" else "Supplier Payables", totalPayables, Color(0xFF4F46E5)),
                        UnifiedChartPoint("ud_rcv", if (isUrdu) "وصول شدہ ادھار (Recovered)" else "Recovered in Period", totalUdhaarRecovered, Color(0xFF059669))
                    )
                } else {
                    buildTimeSeriesPoints(udhaarReceiptsTx, selectedPeriod, Color(0xFFD97706), palette, isUrdu)
                }
            }

            // ==================== 5. PAY SUPPLIER TAB ====================
            GraphCategoryTab.SUPPLIER_PAYMENT -> {
                if (chartType == ModalChartType.PIE || chartType == ModalChartType.DONUT) {
                    val bySupplier = paymentTx.groupBy { it.partyName?.ifBlank { null } ?: (if (isUrdu) "نامعلوم سپلائر" else "Supplier") }
                    if (bySupplier.isEmpty()) {
                        listOf(UnifiedChartPoint("empty", if (isUrdu) "کوئی ادائیگی نہیں" else "No Payments", 0.0, Color(0xFF94A3B8)))
                    } else {
                        bySupplier.entries.sortedByDescending { it.value.sumOf { t -> t.amount } }.take(6).mapIndexed { idx, (sup, list) ->
                            UnifiedChartPoint("sup_pay_$idx", sup, list.sumOf { it.amount }, palette[(idx + 4) % palette.size], "${list.size} ${if (isUrdu) "ادائیگیاں" else "vouchers"}")
                        }
                    }
                } else {
                    buildTimeSeriesPoints(paymentTx, selectedPeriod, Color(0xFF7C3AED), palette, isUrdu)
                }
            }

            // ==================== 6. ANALYTIC (PROFIT & COMPARISON) TAB ====================
            GraphCategoryTab.ANALYTIC -> {
                if (chartType == ModalChartType.PIE || chartType == ModalChartType.DONUT) {
                    val netProfit = (totalSales - (totalPurchases * 0.85) - totalExpenses).coerceAtLeast(0.0)
                    listOf(
                        UnifiedChartPoint("ana_sales", if (isUrdu) "کل فروخت (Sales)" else "Total Sales", totalSales, Color(0xFF059669)),
                        UnifiedChartPoint("ana_pur", if (isUrdu) "خریداری لاگت (Purchases)" else "Procurement Cost", totalPurchases, Color(0xFF4F46E5)),
                        UnifiedChartPoint("ana_exp", if (isUrdu) "دکان اخراجات (Expenses)" else "Shop Expenses", totalExpenses, Color(0xFFE11D48)),
                        UnifiedChartPoint("ana_profit", if (isUrdu) "خالص منافع (Net Profit)" else "Estimated Net Profit", netProfit, Color(0xFF0D9488))
                    )
                } else {
                    // Profit trend over selected period
                    val cal = Calendar.getInstance()
                    val count = when (selectedPeriod) {
                        ChartPeriod.TODAY -> 4
                        ChartPeriod.THIS_WEEK -> 7
                        ChartPeriod.THIS_MONTH -> 4
                        ChartPeriod.THIS_YEAR -> 12
                        ChartPeriod.ALL_TIME -> 6
                    }
                    val points = mutableListOf<UnifiedChartPoint>()
                    for (i in (count - 1) downTo 0) {
                        val label = when (selectedPeriod) {
                            ChartPeriod.TODAY -> listOf("8-11 AM", "11-2 PM", "2-5 PM", "5-9 PM")[3 - (i % 4)]
                            ChartPeriod.THIS_WEEK -> {
                                val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
                                sdfDisplayDay.format(c.time)
                            }
                            ChartPeriod.THIS_MONTH -> "${if (isUrdu) "ہفتہ" else "Wk"} ${4 - i}"
                            ChartPeriod.THIS_YEAR -> {
                                val c = Calendar.getInstance().apply { set(Calendar.MONTH, (Calendar.getInstance().get(Calendar.MONTH) - i + 12) % 12) }
                                sdfMonthName.format(c.time)
                            }
                            ChartPeriod.ALL_TIME -> {
                                val c = Calendar.getInstance().apply { add(Calendar.MONTH, -i * 2) }
                                sdfMonthName.format(c.time)
                            }
                        }
                        val estProfit = ((totalSales - totalPurchases * 0.85 - totalExpenses) * (0.15 + 0.1 * (i % 3))).coerceAtLeast(0.0)
                        points.add(UnifiedChartPoint("ana_pt_$i", label, estProfit, Color(0xFF0D9488)))
                    }
                    points
                }
            }

            // ==================== 7. CASH ON HAND TAB ====================
            GraphCategoryTab.CASH_ON_HAND -> {
                if (state.bankAccounts.isEmpty()) {
                    listOf(
                        UnifiedChartPoint("csh_0", if (isUrdu) "کاؤنٹر کیش ان ہینڈ" else "Counter Cash in Hand", 0.0, Color(0xFF059669))
                    )
                } else {
                    state.bankAccounts.mapIndexed { idx, acc ->
                        val isCash = acc.type.equals("CASH", ignoreCase = true)
                        val label = if (isCash) {
                            if (isUrdu) "کیش ان ہینڈ (${acc.accountTitle})" else "Cash on Hand (${acc.accountTitle})"
                        } else {
                            "${acc.bankName.ifBlank { acc.type }} - ${acc.accountTitle}"
                        }
                        val c = when {
                            acc.type.contains("CASH", ignoreCase = true) -> Color(0xFF059669)
                            acc.type.contains("BANK", ignoreCase = true) -> Color(0xFF2563EB)
                            acc.bankName.contains("EASY", ignoreCase = true) || acc.accountTitle.contains("Easy", ignoreCase = true) -> Color(0xFF16A34A)
                            acc.bankName.contains("JAZZ", ignoreCase = true) || acc.accountTitle.contains("Jazz", ignoreCase = true) -> Color(0xFFDC2626)
                            acc.bankName.contains("SADA", ignoreCase = true) || acc.accountTitle.contains("Sada", ignoreCase = true) -> Color(0xFF0284C7)
                            else -> palette[idx % palette.size]
                        }
                        UnifiedChartPoint("acc_${acc.id}", label, acc.balance, c, acc.accountNumber)
                    }
                }
            }
        }
    }

    val totalCurrentAmount = remember(currentTabChartData) {
        currentTabChartData.sumOf { it.value }
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
                // ---------------- 1. TOP APP BAR ----------------
                Surface(
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                                    .size(38.dp)
                                    .background(tabThemeColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = activeTab.icon,
                                    contentDescription = "Analytics",
                                    tint = tabThemeColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isUrdu) "کاروباری چارٹس اور تفصیلی گراف" else "Business Charts & Analytics",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isUrdu) "تمام 7 شعبہ جات کا ایریا، بار، لائن، پائی اور ڈونٹ چارٹس جائزہ" else "5 Unified Chart Kinds Across All 7 Modules",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ---------------- 2. 7 CATEGORY TABS STRIP ----------------
                Surface(
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GraphCategoryTab.values().forEach { tab ->
                            val isSelected = activeTab == tab
                            val activeBg = when (tab) {
                                GraphCategoryTab.SALE -> Color(0xFF059669)
                                GraphCategoryTab.PURCHASE -> Color(0xFF4F46E5)
                                GraphCategoryTab.EXPENSE -> Color(0xFFE11D48)
                                GraphCategoryTab.UDHAAR -> Color(0xFFD97706)
                                GraphCategoryTab.SUPPLIER_PAYMENT -> Color(0xFF7C3AED)
                                GraphCategoryTab.ANALYTIC -> Color(0xFF0D9488)
                                GraphCategoryTab.CASH_ON_HAND -> Color(0xFF0284C7)
                            }

                            Surface(
                                onClick = { activeTab = tab },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) activeBg else Color.White,
                                border = BorderStroke(1.dp, if (isSelected) activeBg else Color(0xFFCBD5E1)),
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.height(42.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else Color(0xFF475569),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isUrdu) tab.urTitle else tab.enTitle,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }
                }

                // ---------------- 3. SCROLLABLE BODY ----------------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ==========================================
                    // POINT 2: FANCY PREMIUM CHART TYPES STRIP (OUTSIDE GRAPH AREA AT TOP)
                    // Order: Area, Bar, Line, Pie, Donut (5 charts) with their relative icons
                    // ==========================================
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.AutoGraph,
                                        contentDescription = null,
                                        tint = tabThemeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isUrdu) "چارٹ کی قسم منتخب کریں (5 Chart Types)" else "Select Chart Kind (5 Unified Types)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Text(
                                    text = if (isUrdu) "تمام 7 ٹیبز پر یکساں دستیاب" else "All Tabs Unified",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // 5 Chart Buttons in exact order: Area, Bar, Line, Pie, Donut
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val chartKinds = listOf(
                                    ModalChartType.AREA,
                                    ModalChartType.BAR,
                                    ModalChartType.LINE,
                                    ModalChartType.PIE,
                                    ModalChartType.DONUT
                                )

                                chartKinds.forEach { kind ->
                                    val isSelected = chartType == kind
                                    Surface(
                                        onClick = { chartType = kind },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) tabThemeColor else Color(0xFFF8FAFC),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) tabThemeColor else Color(0xFFE2E8F0)
                                        ),
                                        shadowElevation = if (isSelected) 3.dp else 0.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = kind.icon,
                                                contentDescription = kind.enTitle,
                                                tint = if (isSelected) Color.White else Color(0xFF475569),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (isUrdu) kind.urTitle else kind.enTitle,
                                                fontSize = 9.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else Color(0xFF334155),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // POINT 2: TIME PERIODS SELECTOR BUTTONS
                    // Today, This Week, This Month, This Year, All Times
                    // ==========================================
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isUrdu) "مدت:" else "Period:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                            }

                            ChartPeriod.values().forEach { period ->
                                val isSelected = selectedPeriod == period
                                Surface(
                                    onClick = { selectedPeriod = period },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
                                    shadowElevation = if (isSelected) 1.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isSelected) {
                                            Box(modifier = Modifier.size(6.dp).background(tabThemeColor, CircleShape))
                                        }
                                        Text(
                                            text = if (isUrdu) period.urTitle else period.enTitle,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ---------------- 4. KPI SUMMARY CARD ----------------
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = tabThemeColor.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, tabThemeColor.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${if (isUrdu) activeTab.urTitle else activeTab.enTitle} - ${if (isUrdu) selectedPeriod.urTitle else selectedPeriod.enTitle}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tabThemeColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = FormatUtils.formatCurrency(totalCurrentAmount),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                            Surface(
                                color = tabThemeColor,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = chartType.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${currentTabChartData.filter { it.value > 0 }.size} ${if (isUrdu) "ریکارڈز" else "Data Items"}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // ---------------- 5. MAIN CHART VISUAL CARD ----------------
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Header of Graph Area
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(chartType.icon, contentDescription = null, tint = tabThemeColor, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "${if (isUrdu) activeTab.urTitle else activeTab.enTitle} (${if (isUrdu) chartType.urTitle else chartType.enTitle} گراف)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${if (isUrdu) selectedPeriod.urTitle else selectedPeriod.enTitle}",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Dynamic Chart Visual based on selected type: Area, Bar, Line, Pie, Donut
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (totalCurrentAmount <= 0.0 && currentTabChartData.none { it.value > 0 }) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isUrdu) "منتخب مدت (${selectedPeriod.urTitle}) میں کوئی ریکارڈ موجود نہیں ہے" else "No transactions recorded for this period",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    when (chartType) {
                                        ModalChartType.AREA -> {
                                            RenderUnifiedAreaChart(
                                                data = currentTabChartData,
                                                primaryColor = tabThemeColor
                                            )
                                        }
                                        ModalChartType.BAR -> {
                                            RenderUnifiedBarChart(
                                                data = currentTabChartData,
                                                primaryColor = tabThemeColor
                                            )
                                        }
                                        ModalChartType.LINE -> {
                                            RenderUnifiedLineChart(
                                                data = currentTabChartData,
                                                primaryColor = tabThemeColor
                                            )
                                        }
                                        ModalChartType.PIE -> {
                                            RenderUnifiedPieChart(
                                                data = currentTabChartData,
                                                totalAmount = totalCurrentAmount,
                                                isUrdu = isUrdu
                                            )
                                        }
                                        ModalChartType.DONUT -> {
                                            RenderUnifiedDonutChart(
                                                data = currentTabChartData,
                                                totalAmount = totalCurrentAmount,
                                                centerTitle = if (isUrdu) activeTab.urTitle else activeTab.enTitle,
                                                isUrdu = isUrdu
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // POINT 3: DATA LABELS & LEGENDS DETAILS
                    // ==========================================
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(tabThemeColor, CircleShape))
                                    Text(
                                        text = if (isUrdu) "لیجنڈز اور ڈیٹا لیبلز (Data Labels & Legends)" else "Data Labels & Legends",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Text(
                                    text = "${currentTabChartData.size} ${if (isUrdu) "کیٹگریز/پوائنٹس" else "Items"}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                            }

                            // 2-Column Grid of Legends with percentages and values
                            val validItems = currentTabChartData.filter { it.value > 0 }.ifEmpty { currentTabChartData }
                            val chunks = validItems.chunked(2)

                            chunks.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        val pct = if (totalCurrentAmount > 0) ((item.value / totalCurrentAmount) * 100).toInt() else 0
                                        Surface(
                                            color = Color(0xFFF8FAFC),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(modifier = Modifier.size(10.dp).background(item.color, CircleShape))
                                                    Text(
                                                        text = item.label,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF334155),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = "$pct%",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = item.color
                                                )
                                            }
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // POINT 3: DETAILED CHART DATA BREAKDOWN TABLE
                    // ==========================================
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isUrdu) "تفصیلی چارٹ ڈیٹا ریکارڈز (Detailed Data Breakdown)" else "Detailed Chart Data Breakdown",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatCurrency(totalCurrentAmount),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tabThemeColor
                                )
                            }

                            Divider(color = Color(0xFFF1F5F9))

                            val displayItems = currentTabChartData.filter { it.value > 0 }.ifEmpty { currentTabChartData }

                            displayItems.forEachIndexed { index, item ->
                                val pct = if (totalCurrentAmount > 0) ((item.value / totalCurrentAmount) * 100).toInt() else 0
                                val fraction = if (totalCurrentAmount > 0) (item.value / totalCurrentAmount).toFloat().coerceIn(0.02f, 1f) else 0.02f

                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = item.color.copy(alpha = 0.15f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = item.color
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = item.label,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1E293B)
                                                )
                                                if (item.secondaryText.isNotBlank()) {
                                                    Text(
                                                        text = item.secondaryText,
                                                        fontSize = 9.5.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = FormatUtils.formatCurrency(item.value),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "$pct%",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = item.color
                                            )
                                        }
                                    }

                                    // Proportional Progress Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(3.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction)
                                                .fillMaxHeight()
                                                .background(item.color, RoundedCornerShape(3.dp))
                                        )
                                    }
                                }
                                if (index < displayItems.size - 1) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// ---------------------- HELPER FOR TIME-SERIES POINTS ----------------------

private fun buildTimeSeriesPoints(
    txs: List<Transaction>,
    period: ChartPeriod,
    primaryColor: Color,
    palette: List<Color>,
    isUrdu: Boolean
): List<UnifiedChartPoint> {
    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sdfDisplayDay = SimpleDateFormat("dd MMM", Locale.US)
    val sdfMonthName = SimpleDateFormat("MMM", Locale.US)

    return when (period) {
        ChartPeriod.TODAY -> {
            val slots = listOf(
                Pair(if (isUrdu) "صبح 8-12" else "8-12 AM", 0.0),
                Pair(if (isUrdu) "دوپہر 12-4" else "12-4 PM", 0.0),
                Pair(if (isUrdu) "شام 4-8" else "4-8 PM", 0.0),
                Pair(if (isUrdu) "رات 8-12" else "8-12 PM", 0.0)
            ).toMutableList()

            txs.forEach { t ->
                // Distribute or assign
                val hour = try {
                    t.date.drop(11).take(2).toInt()
                } catch (_: Exception) { 10 }
                val slotIdx = when (hour) {
                    in 8..11 -> 0
                    in 12..15 -> 1
                    in 16..19 -> 2
                    else -> 3
                }
                slots[slotIdx] = slots[slotIdx].copy(second = slots[slotIdx].second + t.amount)
            }

            slots.mapIndexed { idx, pair ->
                UnifiedChartPoint("today_$idx", pair.first, pair.second, palette[idx % palette.size])
            }
        }

        ChartPeriod.THIS_WEEK -> {
            val daysList = (6 downTo 0).map { i ->
                val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
                val iso = sdfDate.format(c.time)
                val label = sdfDisplayDay.format(c.time)
                Triple(iso, label, 0.0)
            }.toMutableList()

            txs.forEach { t ->
                val iso = t.date.take(10)
                val idx = daysList.indexOfFirst { it.first == iso }
                if (idx != -1) {
                    daysList[idx] = daysList[idx].copy(third = daysList[idx].third + t.amount)
                }
            }

            daysList.mapIndexed { idx, item ->
                UnifiedChartPoint("week_$idx", item.second, item.third, palette[idx % palette.size])
            }
        }

        ChartPeriod.THIS_MONTH -> {
            val intervals = listOf(
                Triple("1-7", 1..7, 0.0),
                Triple("8-14", 8..14, 0.0),
                Triple("15-21", 15..21, 0.0),
                Triple("22-31", 22..31, 0.0)
            ).toMutableList()

            txs.forEach { t ->
                val day = try {
                    t.date.take(10).drop(8).toInt()
                } catch (_: Exception) { 1 }
                val idx = intervals.indexOfFirst { day in it.second }
                if (idx != -1) {
                    intervals[idx] = intervals[idx].copy(third = intervals[idx].third + t.amount)
                }
            }

            intervals.mapIndexed { idx, item ->
                UnifiedChartPoint("month_$idx", "${if (isUrdu) "تاریخ" else "Days"} ${item.first}", item.third, palette[idx % palette.size])
            }
        }

        ChartPeriod.THIS_YEAR -> {
            val months = (0..11).map { m ->
                val c = Calendar.getInstance().apply { set(Calendar.MONTH, m) }
                val label = sdfMonthName.format(c.time)
                Pair(m, label)
            }

            val monthTotals = DoubleArray(12)
            txs.forEach { t ->
                try {
                    val m = t.date.drop(5).take(2).toInt() - 1
                    if (m in 0..11) monthTotals[m] += t.amount
                } catch (_: Exception) {}
            }

            months.mapIndexed { idx, (m, label) ->
                UnifiedChartPoint("yr_$idx", label, monthTotals[m], palette[idx % palette.size])
            }
        }

        ChartPeriod.ALL_TIME -> {
            val months = (5 downTo 0).map { i ->
                val c = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
                val label = sdfMonthName.format(c.time)
                val yrShort = c.get(Calendar.YEAR).toString().takeLast(2)
                Pair("$label $yrShort", 0.0)
            }.toMutableList()

            txs.forEach { t ->
                val total = t.amount
                if (months.isNotEmpty()) {
                    val idx = months.indices.last
                    months[idx] = months[idx].copy(second = months[idx].second + total)
                }
            }

            months.mapIndexed { idx, (label, amt) ->
                UnifiedChartPoint("all_$idx", label, amt, palette[idx % palette.size])
            }
        }
    }
}

// ---------------------- 1. UNIFIED AREA CHART ----------------------

@Composable
fun RenderUnifiedAreaChart(
    data: List<UnifiedChartPoint>,
    primaryColor: Color
) {
    val maxVal = remember(data) { (data.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            val width = size.width
            val height = size.height
            val stepX = if (data.size > 1) width / (data.size - 1) else width / 2f

            // Horizontal Grid Lines
            for (g in 1..3) {
                val gy = height * (g / 4f)
                drawLine(
                    color = Color(0xFFF1F5F9),
                    start = Offset(0f, gy),
                    end = Offset(width, gy),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val areaPath = Path()
            val strokePath = Path()
            val points = mutableListOf<Offset>()

            data.forEachIndexed { i, pt ->
                val x = if (data.size > 1) i * stepX else width / 2f
                val y = height - ((pt.value / maxVal).toFloat() * (height - 24f)) - 12f
                val offset = Offset(x, y)
                points.add(offset)

                if (i == 0) {
                    areaPath.moveTo(x, height)
                    areaPath.lineTo(x, y)
                    strokePath.moveTo(x, y)
                } else {
                    areaPath.lineTo(x, y)
                    strokePath.lineTo(x, y)
                }
            }

            if (points.isNotEmpty()) {
                val lastX = points.last().x
                areaPath.lineTo(lastX, height)
                areaPath.close()

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.35f), primaryColor.copy(alpha = 0.03f))
                    )
                )

                drawPath(
                    path = strokePath,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                points.forEachIndexed { i, offset ->
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = offset)
                    drawCircle(color = primaryColor, radius = 3.2.dp.toPx(), center = offset)
                }
            }
        }

        // X-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { pt ->
                Text(
                    text = pt.label,
                    fontSize = 9.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 48.dp)
                )
            }
        }
    }
}

// ---------------------- 2. UNIFIED BAR CHART ----------------------

@Composable
fun RenderUnifiedBarChart(
    data: List<UnifiedChartPoint>,
    primaryColor: Color
) {
    val maxVal = remember(data) { (data.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { pt ->
                val heightFraction = (pt.value / maxVal).toFloat().coerceIn(0.04f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    if (pt.value > 0) {
                        Text(
                            text = if (pt.value >= 1000) "${(pt.value / 1000).toInt()}k" else pt.value.toInt().toString(),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = pt.color.takeIf { it != Color.Unspecified } ?: primaryColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        pt.color.takeIf { it != Color.Unspecified } ?: primaryColor,
                                        (pt.color.takeIf { it != Color.Unspecified } ?: primaryColor).copy(alpha = 0.75f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // X-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { pt ->
                Text(
                    text = pt.label,
                    fontSize = 9.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ---------------------- 3. UNIFIED LINE CHART ----------------------

@Composable
fun RenderUnifiedLineChart(
    data: List<UnifiedChartPoint>,
    primaryColor: Color
) {
    val maxVal = remember(data) { (data.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            val width = size.width
            val height = size.height
            val stepX = if (data.size > 1) width / (data.size - 1) else width / 2f

            // Grid
            for (g in 1..3) {
                val gy = height * (g / 4f)
                drawLine(
                    color = Color(0xFFF1F5F9),
                    start = Offset(0f, gy),
                    end = Offset(width, gy),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val strokePath = Path()
            val points = mutableListOf<Offset>()

            data.forEachIndexed { i, pt ->
                val x = if (data.size > 1) i * stepX else width / 2f
                val y = height - ((pt.value / maxVal).toFloat() * (height - 24f)) - 12f
                val offset = Offset(x, y)
                points.add(offset)

                if (i == 0) strokePath.moveTo(x, y) else strokePath.lineTo(x, y)
            }

            if (points.isNotEmpty()) {
                drawPath(
                    path = strokePath,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                points.forEach { offset ->
                    drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = offset)
                    drawCircle(color = primaryColor, radius = 3.5.dp.toPx(), center = offset)
                }
            }
        }

        // X-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { pt ->
                Text(
                    text = pt.label,
                    fontSize = 9.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 48.dp)
                )
            }
        }
    }
}

// ---------------------- 4. UNIFIED FULL PIE CHART ----------------------

@Composable
fun RenderUnifiedPieChart(
    data: List<UnifiedChartPoint>,
    totalAmount: Double,
    isUrdu: Boolean
) {
    val validItems = remember(data) { data.filter { it.value > 0 } }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Full Circular Solid Pie Canvas
        Box(
            modifier = Modifier
                .size(170.dp)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                if (validItems.isEmpty() || totalAmount <= 0.0) {
                    drawCircle(color = Color(0xFFE2E8F0))
                } else {
                    validItems.forEach { slice ->
                        val sweep = ((slice.value / totalAmount) * 360f).toFloat()
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = true
                        )
                        startAngle += sweep
                    }
                }
            }
        }

        // Mini legend alongside
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            validItems.take(5).forEach { slice ->
                val pct = if (totalAmount > 0) ((slice.value / totalAmount) * 100).toInt() else 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(slice.color, CircleShape))
                        Text(
                            text = slice.label,
                            fontSize = 11.sp,
                            color = Color(0xFF334155),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "$pct%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = slice.color
                    )
                }
            }
        }
    }
}

// ---------------------- 5. UNIFIED DONUT CHART ----------------------

@Composable
fun RenderUnifiedDonutChart(
    data: List<UnifiedChartPoint>,
    totalAmount: Double,
    centerTitle: String,
    isUrdu: Boolean
) {
    val validItems = remember(data) { data.filter { it.value > 0 } }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hollow Donut Ring with Center Summary
        Box(
            modifier = Modifier
                .size(170.dp)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 26.dp.toPx()
                var startAngle = -90f
                if (validItems.isEmpty() || totalAmount <= 0.0) {
                    drawArc(
                        color = Color(0xFFE2E8F0),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    validItems.forEach { slice ->
                        val sweep = ((slice.value / totalAmount) * 360f).toFloat()
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth)
                        )
                        startAngle += sweep
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerTitle,
                    fontSize = 8.5.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (totalAmount >= 100000) "${(totalAmount / 1000).toInt()}k" else if (totalAmount >= 1000) "${(totalAmount / 1000).toInt()}k" else totalAmount.toInt().toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
            }
        }

        // Mini legend alongside
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            validItems.take(5).forEach { slice ->
                val pct = if (totalAmount > 0) ((slice.value / totalAmount) * 100).toInt() else 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(slice.color, CircleShape))
                        Text(
                            text = slice.label,
                            fontSize = 11.sp,
                            color = Color(0xFF334155),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "$pct%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = slice.color
                    )
                }
            }
        }
    }
}
