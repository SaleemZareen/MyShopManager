package com.saleemkhan.myshopmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saleemkhan.myshopmanager.data.accounting.AccountingEngine
import com.saleemkhan.myshopmanager.model.AppState
import com.saleemkhan.myshopmanager.model.Transaction
import com.saleemkhan.myshopmanager.model.TransactionType
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils
import java.text.SimpleDateFormat
import java.util.*

data class MonthlyTrendItem(
    val monthLabel: String,
    val sales: Double,
    val purchases: Double,
    val profit: Double
)

data class WalletSlice(
    val title: String,
    val balance: Double,
    val color: Color
)

@Composable
fun AnalyticsScreen(
    state: AppState,
    isUrdu: Boolean
) {
    // 1. Process Real Monthly Trend Data for the past 6 months
    val monthlyTrendData = remember(state.transactions) {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val now = Calendar.getInstance()
        val monthBuckets = mutableListOf<Triple<Int, Int, String>>() // [year, month0Indexed, label]

        for (i in 5 downTo 0) {
            val c = Calendar.getInstance().apply {
                time = now.time
                add(Calendar.MONTH, -i)
            }
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH)
            val yrShort = y.toString().takeLast(2)
            val label = "${months[m]} $yrShort"
            monthBuckets.add(Triple(y, m, label))
        }

        val monthlyTxMap = mutableMapOf<String, MutableList<Transaction>>()
        monthBuckets.forEach { (_, _, label) ->
            monthlyTxMap[label] = mutableListOf()
        }

        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        state.transactions.forEach { t ->
            try {
                val d = parser.parse(t.date.take(10))
                if (d != null) {
                    val c = Calendar.getInstance().apply { time = d }
                    val y = c.get(Calendar.YEAR)
                    val m = c.get(Calendar.MONTH)
                    val yrShort = y.toString().takeLast(2)
                    val label = "${months[m]} $yrShort"

                    monthlyTxMap[label]?.add(t)
                }
            } catch (_: Exception) {}
        }

        monthBuckets.map { (_, _, label) ->
            val txList = monthlyTxMap[label] ?: emptyList()
            val sales = txList.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
            val purchases = txList.filter { it.type == TransactionType.PURCHASE }.sumOf { it.amount }
            val profitResult = AccountingEngine.calculatePeriodProfit(txList)
            MonthlyTrendItem(
                monthLabel = label,
                sales = sales,
                purchases = purchases,
                profit = profitResult.netProfit
            )
        }
    }

    // 2. Process Real Bank/Wallet Distribution
    val totalLiquidity = remember(state.bankAccounts) { state.bankAccounts.sumOf { it.balance } }
    val paymentMethodData = remember(state.bankAccounts, isUrdu) {
        val palette = listOf(
            Color(0xFF0F8A5F), // Emerald / Primary
            Color(0xFF2563EB), // Blue
            Color(0xFF16A34A), // Green
            Color(0xFFDC2626), // Red
            Color(0xFF7C3AED), // Purple
            Color(0xFFD97706), // Amber
            Color(0xFF0284C7)  // Sky
        )

        if (state.bankAccounts.isEmpty()) {
            listOf(
                WalletSlice(if (isUrdu) "کاؤنٹر کیش (خالی)" else "Cash Drawer (Empty)", 1.0, Color(0xFF94A3B8))
            )
        } else {
            state.bankAccounts.mapIndexed { index, acc ->
                WalletSlice(
                    title = if (isUrdu && acc.id == "acc_cash") "کاؤنٹر کیش (نقدی)" else acc.accountTitle,
                    balance = acc.balance,
                    color = palette[index % palette.size]
                )
            }
        }
    }

    // Summary calculations via AccountingEngine
    val lifetimeProfitResult = remember(state.transactions) {
        AccountingEngine.calculatePeriodProfit(state.transactions)
    }
    val totalSales = lifetimeProfitResult.netRevenue
    val totalPurchases = remember(state.transactions) {
        state.transactions.filter { it.type == TransactionType.PURCHASE }.sumOf { it.amount }
    }
    val totalExpenses = lifetimeProfitResult.operatingExpenses
    val overallProfit = lifetimeProfitResult.netProfit

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Header Card (Matching React AnalyticsModule Header)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFECFDF5), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Analytics",
                            tint = Color(0xFF0F8A5F),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isUrdu) "کاروباری تجزیہ اور گرافکس" else "Business Analytics & Visual Charts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (isUrdu) "ماہانہ فروخت، اخراجات اور کیش فلو کے گرافک چارٹس" else "Visual breakdown of monthly sales, expenses, net profits & wallet flows.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }

        // Summary Metric Cards Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isUrdu) "کل فروخت (Sales)" else "Total Sales",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FormatUtils.formatCurrency(totalSales),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isUrdu) "خالص منافع (Net Profit)" else "Estimated Profit",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FormatUtils.formatCurrency(overallProfit),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            }
        }

        // 1. MONTHLY SALES VS PROFIT TREND (Last 6 Months Bar Chart)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header with Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF0F8A5F), modifier = Modifier.size(18.dp))
                            Text(
                                text = if (isUrdu) "ماہانہ فروخت بمقابلہ منافع (6 ماہ)" else "Monthly Sales vs Profit Trend",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF0F172A)
                            )
                        }

                        // Legend
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(10.dp).background(Color(0xFF0F8A5F), RoundedCornerShape(2.dp)))
                                Text(if (isUrdu) "فروخت" else "Sales", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(10.dp).background(Color(0xFF10B981), RoundedCornerShape(2.dp)))
                                Text(if (isUrdu) "منافع" else "Profit", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                    }

                    // Grouped Bar Chart
                    val maxMonthVal = remember(monthlyTrendData) {
                        monthlyTrendData.maxOfOrNull { maxOf(it.sales, it.profit) }?.coerceAtLeast(1.0) ?: 1.0
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            monthlyTrendData.forEach { item ->
                                val salesFrac = (item.sales / maxMonthVal).toFloat().coerceIn(0.04f, 1f)
                                val profitFrac = (item.profit / maxMonthVal).toFloat().coerceIn(0.04f, 1f)

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Pair of bars
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.Bottom,
                                        modifier = Modifier.fillMaxHeight()
                                    ) {
                                        // Sales Bar
                                        Box(
                                            modifier = Modifier
                                                .width(14.dp)
                                                .fillMaxHeight(salesFrac)
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(Color(0xFF0F8A5F))
                                        )
                                        // Profit Bar
                                        Box(
                                            modifier = Modifier
                                                .width(14.dp)
                                                .fillMaxHeight(profitFrac)
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(Color(0xFF10B981))
                                        )
                                    }
                                }
                            }
                        }

                        // X-Axis month labels
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            monthlyTrendData.forEach { item ->
                                Text(
                                    text = item.monthLabel,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. LIQUIDITY & WALLET DISTRIBUTION (Donut Pie Chart)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PieChart, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
                        Text(
                            text = if (isUrdu) "رقوم کی تقسیم (کیش بمقابلہ ڈیجیٹل والٹ)" else "Liquidity & Wallet Distribution",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Donut Graphic
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val activeTotal = if (totalLiquidity > 0) totalLiquidity else 1.0

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 22.dp.toPx()
                                var startAngle = -90f

                                paymentMethodData.forEach { slice ->
                                    val sweep = ((slice.balance / activeTotal) * 360f).toFloat()
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

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isUrdu) "کل کیش و بینک" else "Funds",
                                    fontSize = 9.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = if (totalLiquidity >= 1000) "${(totalLiquidity / 1000).toInt()}k" else totalLiquidity.toInt().toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        // Legend Listing
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val activeTotal = if (totalLiquidity > 0) totalLiquidity else 1.0
                            paymentMethodData.forEach { slice ->
                                val pct = if (totalLiquidity > 0) ((slice.balance / activeTotal) * 100).toInt() else 0
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
                                        Box(modifier = Modifier.size(9.dp).background(slice.color, CircleShape))
                                        Text(
                                            text = slice.title,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF334155),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${FormatUtils.formatCurrency(slice.balance)} ($pct%)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
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
