package com.saleemkhan.myshopmanager.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saleemkhan.myshopmanager.R
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.components.AutoScrollText
import com.saleemkhan.myshopmanager.ui.components.OnboardingSetupCard
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils
import com.saleemkhan.myshopmanager.utils.TaxEngine
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    state: AppState,
    isUrdu: Boolean,
    header: (@Composable () -> Unit)? = null,
    isOnboardingCardVisible: Boolean = false,
    onOpenQuickEntry: (TransactionType) -> Unit,
    onOpenVoiceEntry: () -> Unit,
    onNavigateScreen: (Screen) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenGraphs: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenCashAccounts: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenAddInventory: () -> Unit = {},
    onOpenKhata: () -> Unit = {},
    onDismissOnboardingCard: () -> Unit = {}
) {
    val taxSummary = remember(state) { TaxEngine.calculateFbrSummary(state) }

    // Today's metrics
    val todayIso = remember { FormatUtils.getTodayIsoDate() }
    val todayTransactions = remember(state.transactions, todayIso) {
        state.transactions.filter { t ->
            t.date.startsWith(todayIso)
        }
    }

    val todaySales = remember(todayTransactions) {
        todayTransactions.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
    }
    val todayPurchases = remember(todayTransactions) {
        todayTransactions.filter { it.type == TransactionType.PURCHASE }.sumOf { it.amount }
    }
    val todayExpenses = remember(todayTransactions) {
        todayTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    val cashBalance = remember(state.bankAccounts) {
        state.bankAccounts.find { it.type == "CASH" }?.balance ?: 0.0
    }

    val dateDisplay = remember {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)
        sdf.format(Date())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 0.dp, bottom = 32.dp)
    ) {
        if (header != null) {
            item {
                header()
            }
        }

        if (isOnboardingCardVisible) {
            item {
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    OnboardingSetupCard(
                        state = state,
                        isUrdu = isUrdu,
                        onOpenSettings = onOpenSettings,
                        onOpenCashAccounts = onOpenCashAccounts,
                        onOpenAddInventory = onOpenAddInventory,
                        onOpenKhata = onOpenKhata,
                        onDismiss = onDismissOnboardingCard
                    )
                }
            }
        }

        // 1. HERO WELCOME BANNER CARD
        item {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color(0xFFD1FAE5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(205.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = if (isUrdu) {
                                        listOf(Color(0xFFD1FAE5), Color(0xFFF0FDF4), Color.White)
                                    } else {
                                        listOf(Color.White, Color(0xFFF0FDF4), Color(0xFFD1FAE5))
                                    }
                                )
                            )
                    ) {
                        // 3D Store Asset anchored at corner (Bottom-Left in Urdu flipped, Bottom-Right in English)
                        Image(
                            painter = painterResource(id = R.drawable.shop_logo),
                            contentDescription = "Store Illustration",
                            contentScale = ContentScale.Fit,
                            alignment = if (isUrdu) AbsoluteAlignment.BottomLeft else AbsoluteAlignment.BottomRight,
                            modifier = Modifier
                                .align(if (isUrdu) AbsoluteAlignment.BottomLeft else AbsoluteAlignment.BottomRight)
                                .fillMaxWidth(0.48f)
                                .fillMaxHeight(0.85f)
                                .graphicsLayer {
                                    if (isUrdu) {
                                        scaleX = -2.2f
                                        scaleY = 2.2f
                                        transformOrigin = TransformOrigin(0f, 1f)
                                        translationX = 2.2f * size.width - 220f
                                        translationY = 100f
                                    } else {
                                        scaleX = 2.2f
                                        scaleY = 2.2f
                                        transformOrigin = TransformOrigin(1f, 1f)
                                        translationX = 220f
                                        translationY = 100f
                                    }
                                }
                        )

                        // Date Pill (Top Right in LTR, Top Left in RTL)
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            modifier = Modifier
                                .align(if (isUrdu) AbsoluteAlignment.TopLeft else AbsoluteAlignment.TopRight)
                                .absolutePadding(
                                    top = 12.dp,
                                    left = if (isUrdu) 14.dp else 0.dp,
                                    right = if (isUrdu) 0.dp else 14.dp
                                )
                                .clickable { onOpenCalendar() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(DukanGreenPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Date",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = dateDisplay,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DukanGreenHover
                                )
                            }
                        }

                        // Welcome Text (Assalam o Alaikum + Owner Name + Tagline)
                        val ownerName = (state.profile.ownerName.ifBlank { "Muhammad Saleem" }) + "!"
                        val nameFontSize = when {
                            ownerName.length > 30 -> 18.sp
                            ownerName.length > 24 -> 21.sp
                            ownerName.length > 18 -> 24.sp
                            ownerName.length > 14 -> 26.sp
                            else -> 28.sp
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .absolutePadding(
                                    left = if (isUrdu) 14.dp else 16.dp,
                                    right = if (isUrdu) 16.dp else 14.dp,
                                    top = 14.dp,
                                    bottom = 44.dp
                                ),
                            horizontalAlignment = if (isUrdu) AbsoluteAlignment.Right else AbsoluteAlignment.Left,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Text(
                                text = if (isUrdu) "السلام علیکم،" else "Assalam o Alaikum,",
                                fontSize = if (isUrdu) 24.sp else 21.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = if (isUrdu) TextAlign.Right else TextAlign.Left,
                                color = Slate800,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ownerName,
                                fontSize = nameFontSize,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = if (isUrdu) TextAlign.Right else TextAlign.Left,
                                color = DukanGreenPrimary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isUrdu) "یہاں آپ کے بزنس کا پورا کنٹرول،\nایک جگہ، آسان اور اسمارٹ طریقے سے۔" else "Complete control of your business here,\nin one place, simple and smart.",
                                modifier = Modifier.fillMaxWidth(if (isUrdu) 0.60f else 0.58f),
                                textAlign = if (isUrdu) TextAlign.Right else TextAlign.Left,
                                fontSize = if (isUrdu) 14.5.sp else 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate800,
                                softWrap = true,
                                lineHeight = if (isUrdu) 20.sp else 18.sp
                            )
                        }

                        // Bottom Pill: "Get Help from AI Assistant" (Shadow removed cleanly)
                        Surface(
                            onClick = onOpenAi,
                            color = Color.Transparent,
                            shape = RoundedCornerShape(14.dp),
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(DukanGreenPrimary, DukanGreenHover)
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = "AI",
                                        tint = Color(0xFFA7F3D0),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isUrdu) "اے آئی اسسٹنٹ سے مدد لیں" else "Get Help from AI Assistant",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Stars",
                                        tint = Color(0xFFFDE047),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. TOP QUICK ACTION BUTTONS ROW
        item {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickActionButton(
                            title = if (isUrdu) "فروخت" else "Sales",
                            subtitle = if (isUrdu) "سیلز کا جائزہ" else "Track sales",
                            icon = Icons.Default.ShoppingBag,
                            iconTint = DukanGreenPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenQuickEntry(TransactionType.SALE) }
                        )
                        QuickActionButton(
                            title = if (isUrdu) "خریداری" else "Purchases",
                            subtitle = if (isUrdu) "مال کا جائزہ" else "Track purchases",
                            icon = Icons.Default.LocalShipping,
                            iconTint = Color(0xFF2563EB),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenQuickEntry(TransactionType.PURCHASE) }
                        )
                        QuickActionButton(
                            title = if (isUrdu) "اخراجات" else "Expenses",
                            subtitle = if (isUrdu) "اخراجات کا حساب" else "Track expenses",
                            icon = Icons.Default.Receipt,
                            iconTint = Color(0xFFDC2626),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenQuickEntry(TransactionType.EXPENSE) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickActionButton(
                            title = if (isUrdu) "ادھار وصولی" else "Collect Udhaar",
                            subtitle = if (isUrdu) "وصولی کا ریکارڈ" else "Collect dues",
                            icon = Icons.Default.Paid,
                            iconTint = Color(0xFFD97706),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenQuickEntry(TransactionType.RECEIPT) }
                        )
                        QuickActionButton(
                            title = if (isUrdu) "سپلائر ادائیگی" else "Pay Supplier",
                            subtitle = if (isUrdu) "سپلائرز کو ادائیگی" else "Pay suppliers",
                            icon = Icons.Default.Send,
                            iconTint = Color(0xFF7C3AED),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenQuickEntry(TransactionType.PAYMENT) }
                        )
                        QuickActionButton(
                            title = if (isUrdu) "گراف و تجزیہ" else "Analytics",
                            subtitle = if (isUrdu) "کاروباری رپورٹ" else "View analytics",
                            icon = Icons.Default.BarChart,
                            iconTint = Color(0xFF4F46E5),
                            modifier = Modifier.weight(1f),
                            onClick = onOpenGraphs
                        )
                    }

                    // Cash on Hand Button
                    Surface(
                        onClick = onOpenCashAccounts,
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Cash",
                                    tint = DukanGreenHover,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isUrdu) "نقدی ہاتھ میں (Cash in Hand)" else "Cash on Hand",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Slate800
                                )
                            }
                            Text(
                                text = FormatUtils.formatCurrency(cashBalance),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DukanGreenHover
                            )
                        }
                    }
                }
            }
        }

        // 3. TODAY'S OVERVIEW SECTION (Sales & Purchases Cards)
        item {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUrdu) "آج کا جائزہ" else "Today's Overview",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Slate900
                        )
                        Text(
                            text = if (isUrdu) "تمام دیکھیں" else "View All",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = DukanGreenPrimary,
                            modifier = Modifier.clickable { onNavigateScreen(Screen.REPORTS) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Total Sales Card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isUrdu) "کل فروخت" else "Total Sales",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate500
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color(0xFFDCFCE7), RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NorthEast,
                                            contentDescription = "Up",
                                            tint = DukanGreenHover,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = FormatUtils.formatCurrency(todaySales),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DukanGreenPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isUrdu) "▲ 12٪ بمقابلہ کل" else "▲ 12% vs yesterday",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Total Purchases Card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isUrdu) "کل خریداری" else "Total Purchases",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate500
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color(0xFFEEF2FF), RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SouthWest,
                                            contentDescription = "Down",
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = FormatUtils.formatCurrency(todayPurchases),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF312E81)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = Slate100,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isUrdu) "— 0٪ بمقابلہ کل" else "— 0% vs yesterday",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate600,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. FINANCIAL SUMMARY CARDS GRID (Net Profit, Total Capital, Total Expenses, Total Liabilities)
        item {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricMiniCard(
                            title = if (isUrdu) "کل منافع" else "Net Profit",
                            value = if (taxSummary.isStockUnavailable) (if (isUrdu) "نامکمل" else "Incomplete") else FormatUtils.formatCurrency(taxSummary.netProfit ?: 0.0),
                            icon = Icons.Default.MonetizationOn,
                            iconBg = Color(0xFFDCFCE7),
                            iconTint = DukanGreenPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniCard(
                            title = if (isUrdu) "مجموعہ اصل" else "Total Capital",
                            value = if (taxSummary.ownerCapital == null) (if (isUrdu) "نامکمل" else "Incomplete") else FormatUtils.formatCurrency(taxSummary.ownerCapital),
                            icon = Icons.Default.AccountBalanceWallet,
                            iconBg = Color(0xFFDBEAFE),
                            iconTint = Color(0xFF2563EB),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricMiniCard(
                            title = if (isUrdu) "کل خرچہ" else "Total Expenses",
                            value = FormatUtils.formatCurrency(taxSummary.directExpenses),
                            icon = Icons.Default.ReceiptLong,
                            iconBg = Color(0xFFFEE2E2),
                            iconTint = Color(0xFFDC2626),
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniCard(
                            title = if (isUrdu) "مجموعہ قرضہ" else "Total Liabilities",
                            value = FormatUtils.formatCurrency(taxSummary.totalLiabilities),
                            icon = Icons.Default.Group,
                            iconBg = Color(0xFFF3E8FF),
                            iconTint = Color(0xFF7C3AED),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. RECEIVABLES (TOTAL UDHAAR) DARK AMBER CARD
        item {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF451A03), Color(0xFF0F172A))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MonetizationOn,
                                        contentDescription = "Udhaar",
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isUrdu) "کل بقایا ادھار (Total Udhaar)" else "Total Receivables (Udhaar)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFDE68A)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = FormatUtils.formatCurrency(taxSummary.totalReceivables),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFFBEB)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isUrdu) "کسٹمرز کے ذمہ واجب الادا رقم" else "Pending Customer Dues",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFDE68A).copy(alpha = 0.8f)
                                )
                            }

                            Button(
                                onClick = { onNavigateScreen(Screen.KHATA) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Slate900
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) "تفصیلات دیکھیں" else "View Details",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "View",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.25f)),
        shadowElevation = 0.5.dp,
        modifier = modifier.height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(iconTint.copy(alpha = 0.04f))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(iconTint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Slate900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontWeight = FontWeight.Normal,
                    fontSize = 8.5.sp,
                    color = Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MetricMiniCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500
                )
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
