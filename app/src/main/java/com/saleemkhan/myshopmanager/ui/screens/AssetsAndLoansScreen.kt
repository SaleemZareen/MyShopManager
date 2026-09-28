package com.saleemkhan.myshopmanager.ui.screens

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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils
import com.saleemkhan.myshopmanager.utils.TaxEngine

enum class AssetLoanTab(val enLabel: String, val urLabel: String) {
    BALANCE_SHEET("Balance Sheet", "بیلنس شیٹ (توازنِ مال)"),
    BUSINESS_ASSETS("Business Assets", "دکان کے اثاثے"),
    PERSONAL_ASSETS("Personal Assets", "ذاتی اثاثے"),
    HOUSEHOLD_DRAWINGS("Household & Expenses", "گھریلو اور کاروباری اخراجات"),
    LOANS("Loans & Borrowings", "قرضے")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsAndLoansScreen(
    state: AppState,
    isUrdu: Boolean,
    onAddBusinessAsset: (BusinessAsset) -> Unit,
    onAddPersonalAsset: (PersonalAsset) -> Unit,
    onAddLoan: (LoanRecord) -> Unit,
    onDeleteBusinessAsset: (String) -> Unit,
    onDeletePersonalAsset: ((String) -> Unit)? = null,
    onDeleteLoan: (String) -> Unit,
    onUpdateHouseholdExpenses: ((HouseholdExpenses) -> Unit)? = null,
    onUpdateOtherIncome: ((OtherIncome) -> Unit)? = null,
    onAddTransaction: ((Transaction) -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(AssetLoanTab.BALANCE_SHEET) }
    var balanceSheetMode by remember { mutableStateOf("COMBINED") } // "COMBINED" or "BUSINESS_ONLY"
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    // Inline Loan Transaction Entry State
    var selectedLoanId by remember { mutableStateOf<String?>(null) }
    var movementType by remember { mutableStateOf("REPAYMENT") } // "TAKEN" or "REPAYMENT"
    var movementAmount by remember { mutableStateOf("") }
    var movementPaymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var movementNotes by remember { mutableStateOf("") }

    // Handle Back Press when inline loan logger is expanded
    BackHandler(enabled = selectedLoanId != null || deleteConfirmId != null) {
        if (selectedLoanId != null) selectedLoanId = null
        if (deleteConfirmId != null) deleteConfirmId = null
    }

    // Business Asset State
    var bName by remember { mutableStateOf("") }
    var bPrice by remember { mutableStateOf("") }
    var bCategory by remember { mutableStateOf("Furniture & Fixtures") }

    // Personal Asset State
    var pName by remember { mutableStateOf("") }
    var pValue by remember { mutableStateOf("") }
    var pCategory by remember { mutableStateOf("Property / Gold") }

    // Household Expenses local draft
    var hhExpenses by remember(state.householdExpenses) { mutableStateOf(state.householdExpenses) }

    // Helper for computing loan values dynamically
    fun getLoanOutstanding(loan: LoanRecord): Double {
        val initialAmount = loan.amount
        val taken = state.transactions
            .filter { it.type == TransactionType.LOAN_TAKEN && it.loanAccountId == loan.id }
            .sumOf { it.amount }
        val repaid = state.transactions
            .filter { it.type == TransactionType.PAYMENT && (it.category.equals("LOAN_REPAYMENT", ignoreCase = true) || it.category.equals("Loan Repayment", ignoreCase = true)) && it.loanAccountId == loan.id }
            .sumOf { it.amount }
        return (initialAmount + taken - repaid).coerceAtLeast(0.0)
    }

    // Valuation calculations
    val totalBusinessAssetVal = remember(state.businessAssets) {
        state.businessAssets.sumOf { it.currentValue }
    }
    val totalPersonalAssetVal = remember(state.personalAssets) {
        state.personalAssets.sumOf { it.value }
    }

    val cashBalance = remember(state.bankAccounts) {
        state.bankAccounts.sumOf { it.balance }
    }
    val stockValue = remember(state.inventory) {
        state.inventory.sumOf { it.quantity * it.purchasePrice }
    }
    val customerReceivables = remember(state.customers) {
        state.customers.sumOf { it.totalCredit }
    }
    val supplierPayables = remember(state.suppliers) {
        state.suppliers.sumOf { it.totalPayable }
    }

    val businessLoansValue = remember(state.loans, state.transactions) {
        state.loans
            .filter { it.classification == "BUSINESS_LOAN" || (it.classification != "PERSONAL_LOAN" && it.type != "PERSONAL_LOAN") }
            .sumOf { getLoanOutstanding(it) }
    }
    val personalLoansValue = remember(state.loans, state.transactions) {
        state.loans
            .filter { it.classification == "PERSONAL_LOAN" || it.type == "PERSONAL_LOAN" }
            .sumOf { getLoanOutstanding(it) }
    }

    val totalAssets = if (balanceSheetMode == "COMBINED") {
        cashBalance + stockValue + customerReceivables + totalBusinessAssetVal + totalPersonalAssetVal
    } else {
        cashBalance + stockValue + customerReceivables + totalBusinessAssetVal
    }

    val totalLiabilities = if (balanceSheetMode == "COMBINED") {
        supplierPayables + businessLoansValue + personalLoansValue
    } else {
        supplierPayables + businessLoansValue
    }

    val netWorth = totalAssets - totalLiabilities

    // Tax Engine & Ledger Business Expenses Summary
    val fbrSummary = remember(state) {
        TaxEngine.calculateFbrSummary(state)
    }
    val selectedYear = state.taxRecord.selectedTaxYear.ifBlank { "2026" }

    val expenseTransactions = remember(state.transactions, selectedYear) {
        state.transactions.filter { t ->
            t.type == TransactionType.EXPENSE &&
                    t.date.take(4) == selectedYear &&
                    !t.category.equals("DRAWINGS", ignoreCase = true) &&
                    !t.category.equals("PERSONAL_DRAWINGS", ignoreCase = true) &&
                    !t.category.equals("LOAN_REPAYMENT", ignoreCase = true)
        }
    }

    val expenseBreakdown = remember(expenseTransactions) {
        val map = mutableMapOf<String, Double>()
        expenseTransactions.forEach { t ->
            val cat = t.category.ifBlank { "MISCELLANEOUS" }
            map[cat] = (map[cat] ?: 0.0) + t.amount
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ==========================================
        // 1. MAIN HEADER CARD
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(DukanGreenLight, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Assets & Loans",
                        tint = DukanGreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUrdu) "اثاثے، گھریلو اخراجات اور ویلتھ" else "Assets, Loans & Wealth Statement",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Slate900
                    )
                    Text(
                        text = if (isUrdu) "کاروباری و ذاتی اثاثے، گھریلو اخراجات اور ایف بی آر مطابقت کے لیے دیگر آمدن" else "Track business & personal net worth required for FBR Wealth Reconciliation",
                        fontSize = 10.5.sp,
                        color = Slate500
                    )
                }
            }
        }

        // ==========================================
        // 2. 5 TABS NAVIGATION STRIP
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssetLoanTab.values().forEach { t ->
                val isSelected = selectedTab == t
                Surface(
                    onClick = { selectedTab = t },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) DukanGreenPrimary else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) DukanGreenPrimary else Slate200),
                    shadowElevation = if (isSelected) 1.dp else 0.dp
                ) {
                    Text(
                        text = if (isUrdu) t.urLabel else t.enLabel,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Slate700,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // ==========================================
        // 3. TAB CONTENT
        // ==========================================
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                // ---------------- TAB 1: BALANCE SHEET ----------------
                AssetLoanTab.BALANCE_SHEET -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Mode Switcher Card
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isUrdu) "دکان کی بیلنس شیٹ (توازنِ مال)" else "Shopkeeper's Balance Sheet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = if (isUrdu) "اثاثے، واجبات اور خالص سرمایہ کا موازنہ" else "Statement of Assets, Liabilities & Capital",
                                        fontSize = 10.sp,
                                        color = Slate500
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .background(Slate100, RoundedCornerShape(8.dp))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Surface(
                                        onClick = { balanceSheetMode = "BUSINESS_ONLY" },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (balanceSheetMode == "BUSINESS_ONLY") DukanGreenPrimary else Color.Transparent
                                    ) {
                                        Text(
                                            text = if (isUrdu) "صرف دکان" else "Business Only",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (balanceSheetMode == "BUSINESS_ONLY") Color.White else Slate700,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = { balanceSheetMode = "COMBINED" },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (balanceSheetMode == "COMBINED") DukanGreenPrimary else Color.Transparent
                                    ) {
                                        Text(
                                            text = if (isUrdu) "مجموعی (ذاتی + دکان)" else "Combined",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (balanceSheetMode == "COMBINED") Color.White else Slate700,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Top 3 KPI Stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Total Assets
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (isUrdu) "کل اثاثے" else "Total Assets",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF065F46)
                                    )
                                    Text(
                                        text = FormatUtils.formatCurrency(totalAssets),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = DukanGreenHover
                                    )
                                }
                            }

                            // Total Liabilities
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
                                border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (isUrdu) "کل واجبات" else "Liabilities",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9F1239)
                                    )
                                    Text(
                                        text = FormatUtils.formatCurrency(totalLiabilities),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = RedDanger
                                    )
                                }
                            }

                            // Net Capital / Equity
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (isUrdu) "خالص سرمایہ" else "Net Worth",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E40AF)
                                    )
                                    Text(
                                        text = FormatUtils.formatCurrency(netWorth),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                            }
                        }

                        // Detailed Ledger: Assets Section
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = DukanGreenPrimary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (isUrdu) "اثاثہ جات (ASSETS)" else "Assets Ledger",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF065F46)
                                    )
                                }

                                Divider(color = Slate100)

                                // Current Assets
                                Text(
                                    text = if (isUrdu) "موجودہ کاروباری اثاثے (Current Assets)" else "Current Assets",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate400
                                )

                                // Cash & Bank Balance Card with 4 sub-accounts
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Main Header Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isUrdu) "نقد اور بینک بیلنس" else "Cash & Bank Balance",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                            Text(
                                                text = FormatUtils.formatCurrency(cashBalance),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                        }

                                        // Sub Accounts Breakdown with Left Vertical Line Indicator
                                        val accountsList = if (state.bankAccounts.isNotEmpty()) {
                                            state.bankAccounts
                                        } else {
                                            listOf(
                                                BankAccount(id = "acc_cash", bankName = "Cash Counter", accountTitle = "Cash in Dukan Counter", balance = 0.0),
                                                BankAccount(id = "acc_bank", bankName = "Bank", accountTitle = "Bank Account", balance = 0.0),
                                                BankAccount(id = "acc_ep", bankName = "EasyPaisa", accountTitle = "EasyPaisa Wallet", balance = 0.0),
                                                BankAccount(id = "acc_jc", bankName = "JazzCash", accountTitle = "JazzCash Wallet", balance = 0.0)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Vertical Left Border Line
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height((accountsList.size * 22).dp)
                                                    .background(Color(0xFFCBD5E1), RoundedCornerShape(1.dp))
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                accountsList.forEach { acc ->
                                                    val accLabel = if (acc.accountTitle.isNotBlank() && acc.bankName.isNotBlank() && !acc.accountTitle.equals(acc.bankName, ignoreCase = true)) {
                                                        "${acc.bankName} (${acc.accountTitle})"
                                                    } else if (acc.bankName.isNotBlank()) {
                                                        acc.bankName
                                                    } else {
                                                        acc.accountTitle.ifBlank { acc.type }
                                                    }

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = accLabel,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Color(0xFF64748B),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        Text(
                                                            text = FormatUtils.formatCurrency(acc.balance),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Inventory
                                BalanceSheetItem(
                                    title = if (isUrdu) "دکان کا مال (انوینٹری اسٹاک مالیت)" else "Stock-in-Trade (Inventory)",
                                    subtitle = "${state.inventory.size} ${if (isUrdu) "آئٹمز" else "items"}",
                                    amount = stockValue,
                                    color = Slate900
                                )

                                // Receivables
                                BalanceSheetItem(
                                    title = if (isUrdu) "کھاتا وصولیاں (ادھار صارفین)" else "Accounts Receivable (Customer Udhaar)",
                                    subtitle = "${state.customers.count { it.totalCredit > 0 }} ${if (isUrdu) "فعال کھاتے" else "active debtor accounts"}",
                                    amount = customerReceivables,
                                    color = Slate900
                                )

                                Divider(color = Slate100)

                                // Fixed Business Assets
                                Text(
                                    text = if (isUrdu) "مستقل کاروباری اثاثے (Fixed Assets)" else "Fixed Assets",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate400
                                )
                                BalanceSheetItem(
                                    title = if (isUrdu) "کاروباری سامان (فرنیچر، سولر، آلات وغیرہ)" else "Business Equipment & Fixtures",
                                    subtitle = "${state.businessAssets.size} ${if (isUrdu) "اثاثے" else "assets registered"}",
                                    amount = totalBusinessAssetVal,
                                    color = Slate900
                                )

                                if (balanceSheetMode == "COMBINED") {
                                    Divider(color = Slate100)
                                    Text(
                                        text = if (isUrdu) "ذاتی و گھریلو اثاثے (Personal Assets)" else "Personal Wealth & Property",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate400
                                    )
                                    BalanceSheetItem(
                                        title = if (isUrdu) "ذاتی جائیداد، سونا، نقدی وغیرہ" else "Personal Property & Gold",
                                        subtitle = "${state.personalAssets.size} ${if (isUrdu) "اثاثے" else "properties"}",
                                        amount = totalPersonalAssetVal,
                                        color = Slate900
                                    )
                                }

                                // Total Assets with Accounting Double Border
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.5.dp, Color(0xFF0F172A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isUrdu) "کل اثاثے (TOTAL ASSETS)" else "TOTAL ASSETS",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(totalAssets),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF065F46)
                                        )
                                    }
                                }
                            }
                        }

                        // Detailed Ledger: Liabilities & Capital Section
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = RedDanger, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (isUrdu) "واجبات اور سرمایہ (LIABILITIES & EQUITY)" else "Liabilities & Capital Ledger",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                }

                                Divider(color = Slate100)

                                // Current Liabilities
                                Text(
                                    text = if (isUrdu) "موجودہ کاروباری واجبات (Current Liabilities)" else "Current Liabilities",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate400
                                )

                                // Supplier Payables
                                BalanceSheetItem(
                                    title = if (isUrdu) "سپلائر کھاتا ادائیگیاں" else "Accounts Payable (Suppliers)",
                                    subtitle = "${state.suppliers.count { it.totalPayable > 0 }} ${if (isUrdu) "فعال سپلائر بل" else "active supplier debts"}",
                                    amount = supplierPayables,
                                    color = RedDanger
                                )

                                // Business Loans
                                BalanceSheetItem(
                                    title = if (isUrdu) "کاروباری قرضے (Business Loans)" else "Business Loans & Borrowings",
                                    subtitle = "${state.loans.count { it.classification == "BUSINESS_LOAN" }} ${if (isUrdu) "قرضے" else "loans"}",
                                    amount = businessLoansValue,
                                    color = RedDanger
                                )

                                if (balanceSheetMode == "COMBINED") {
                                    Divider(color = Slate100)
                                    Text(
                                        text = if (isUrdu) "ذاتی قرضہ جات (Personal Borrowings)" else "Personal Borrowings",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate400
                                    )
                                    BalanceSheetItem(
                                        title = if (isUrdu) "ذاتی قرضے و ادھاریاں" else "Personal Loans",
                                        subtitle = "${state.loans.count { it.classification == "PERSONAL_LOAN" }} ${if (isUrdu) "ذاتی قرضے" else "loans"}",
                                        amount = personalLoansValue,
                                        color = RedDanger
                                    )
                                }

                                Divider(color = Slate100)

                                // Owner Capital
                                Text(
                                    text = if (isUrdu) "خالص سرمایہ (Capital / Equity)" else "Owner's Net Capital",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate400
                                )
                                BalanceSheetItem(
                                    title = if (isUrdu) "مالک کا خالص سرمایہ / نیٹ ورتھ" else "Owner's Net Capital / Equity",
                                    subtitle = if (isUrdu) "اثاثے منفی کل واجبات" else "Assets minus Liabilities",
                                    amount = netWorth,
                                    color = Color(0xFF2563EB)
                                )

                                // Total Liabilities & Equity with Accounting Double Border
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.5.dp, Color(0xFF0F172A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isUrdu) "کل واجبات اور سرمایہ" else "TOTAL LIAB & EQUITY",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(totalLiabilities + netWorth),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------- TAB 2: BUSINESS ASSETS ----------------
                AssetLoanTab.BUSINESS_ASSETS -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Add Business Asset Form Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isUrdu) "+ نیا دکان کا اثاثہ شامل کریں" else "+ Add New Business Asset",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Slate700
                                )

                                OutlinedTextField(
                                    value = bName,
                                    onValueChange = { bName = it },
                                    placeholder = { Text(if (isUrdu) "اثاثہ کا نام (مثلاً: فرنیچر/سولر/ڈیپ فریزر)" else "Asset Name (e.g. Solar System, Deep Freezer)", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = bPrice,
                                        onValueChange = { bPrice = it },
                                        placeholder = { Text(if (isUrdu) "خرید قیمت (روپے)" else "Purchase Price (Rs.)", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    Button(
                                        onClick = {
                                            val pr = bPrice.toDoubleOrNull() ?: 0.0
                                            if (bName.isNotBlank() && pr > 0) {
                                                onAddBusinessAsset(
                                                    BusinessAsset(
                                                        id = "ba_${System.currentTimeMillis()}",
                                                        name = bName.trim(),
                                                        category = bCategory,
                                                        purchasePrice = pr,
                                                        currentValue = pr,
                                                        purchaseDate = FormatUtils.getTodayIsoDate(),
                                                        usefulLifeYears = 10
                                                    )
                                                )
                                                bName = ""
                                                bPrice = ""
                                                Toast.makeText(context, if (isUrdu) "اثاثہ محفوظ ہو گیا" else "Asset Saved", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Text(text = if (isUrdu) "حفظ کریں" else "Save Asset", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Business Assets List
                        if (state.businessAssets.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isUrdu) "کوئی کاروباری اثاثہ ریکارڈ نہیں" else "No business assets found",
                                    color = Slate400,
                                    fontSize = 12.5.sp
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.businessAssets, key = { it.id }) { asset ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(
                                                    modifier = Modifier.size(38.dp).background(Color(0xFFDBEAFE), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                                }
                                                Column {
                                                    Text(text = asset.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                                    Text(
                                                        text = "${asset.category} • ${if (isUrdu) "تاریخ:" else "Date:"} ${asset.purchaseDate}",
                                                        fontSize = 10.sp,
                                                        color = Slate500
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = FormatUtils.formatCurrency(asset.currentValue),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF2563EB)
                                                )

                                                if (deleteConfirmId == asset.id) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Button(
                                                            onClick = {
                                                                onDeleteBusinessAsset(asset.id)
                                                                deleteConfirmId = null
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(if (isUrdu) "ہاں" else "Yes", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        OutlinedButton(
                                                            onClick = { deleteConfirmId = null },
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(if (isUrdu) "نہیں" else "No", fontSize = 9.sp)
                                                        }
                                                    }
                                                } else {
                                                    IconButton(onClick = { deleteConfirmId = asset.id }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Slate400, modifier = Modifier.size(16.dp))
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

                // ---------------- TAB 3: PERSONAL ASSETS ----------------
                AssetLoanTab.PERSONAL_ASSETS -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Add Personal Asset Form Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isUrdu) "+ نیا ذاتی اثاثہ درج کریں (گھر/پلاٹ/گاڑی/سونا)" else "+ Add Personal Property (House/Plot/Gold)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Slate700
                                )

                                OutlinedTextField(
                                    value = pName,
                                    onValueChange = { pName = it },
                                    placeholder = { Text(if (isUrdu) "اثاثہ کی تفصیل (مثلاً: رہائشی مکان/سونا/پلاٹ)" else "Property Title (e.g. Family House, Gold)", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = pValue,
                                        onValueChange = { pValue = it },
                                        placeholder = { Text(if (isUrdu) "اندازاً مالیت (روپے)" else "Estimated Value (Rs.)", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    Button(
                                        onClick = {
                                            val vl = pValue.toDoubleOrNull() ?: 0.0
                                            if (pName.isNotBlank() && vl > 0) {
                                                onAddPersonalAsset(
                                                    PersonalAsset(
                                                        id = "pa_${System.currentTimeMillis()}",
                                                        name = pName.trim(),
                                                        category = pCategory,
                                                        value = vl
                                                    )
                                                )
                                                pName = ""
                                                pValue = ""
                                                Toast.makeText(context, if (isUrdu) "ذاتی اثاثہ محفوظ ہو گیا" else "Property Saved", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Text(text = if (isUrdu) "حفظ کریں" else "Save Property", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Personal Assets List
                        if (state.personalAssets.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isUrdu) "کوئی ذاتی اثاثہ ریکارڈ نہیں" else "No personal assets found",
                                    color = Slate400,
                                    fontSize = 12.5.sp
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.personalAssets, key = { it.id }) { pa ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(
                                                    modifier = Modifier.size(38.dp).background(Color(0xFFFEF3C7), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                                                }
                                                Column {
                                                    Text(text = pa.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                                    Text(text = pa.category, fontSize = 10.sp, color = Slate500)
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = FormatUtils.formatCurrency(pa.value),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = DukanGreenHover
                                                )

                                                if (deleteConfirmId == pa.id) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Button(
                                                            onClick = {
                                                                onDeletePersonalAsset?.invoke(pa.id)
                                                                deleteConfirmId = null
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(if (isUrdu) "ہاں" else "Yes", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        OutlinedButton(
                                                            onClick = { deleteConfirmId = null },
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text(if (isUrdu) "نہیں" else "No", fontSize = 9.sp)
                                                        }
                                                    }
                                                } else {
                                                    IconButton(onClick = { deleteConfirmId = pa.id }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Slate400, modifier = Modifier.size(16.dp))
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

                // ---------------- TAB 4: HOUSEHOLD EXPENSES & DRAWINGS ----------------
                AssetLoanTab.HOUSEHOLD_DRAWINGS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Notice Card
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                            border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isUrdu) "ضروری اطلاع: FBR انکم ٹیکس ریٹرن اور ویلتھ اسٹیٹمنٹ کو برابر کرنے کے لیے سالانہ گھریلو خرچ درج کرنا ضروری ہے۔ کاروباری اخراجات دکان کے لیجر سے حاصل کیے جاتے ہیں۔" else "Notice: Accurate household expense logging is required by FBR to balance annual Wealth Reconciliation. Business expenses are automatically sourced from your shop ledger.",
                                fontSize = 10.5.sp,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        // Actions Bar: Clear All & Save Changes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    hhExpenses = HouseholdExpenses()
                                    onUpdateHouseholdExpenses?.invoke(hhExpenses)
                                    Toast.makeText(context, if (isUrdu) "گھریلو اخراجات صاف کر دیے گئے" else "Cleared All", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDanger),
                                border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isUrdu) "صاف کریں" else "Clear All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onUpdateHouseholdExpenses?.invoke(hhExpenses)
                                    Toast.makeText(context, if (isUrdu) "تبدیلیاں محفوظ ہو گئیں" else "Saved Successfully", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isUrdu) "تبدیلیاں محفوظ کریں" else "Save Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Left Section: Manual Household Expenses
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = DukanGreenPrimary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (isUrdu) "گھریلو اخراجات اور ڈرائنگز (دستی اندراج)" else "Household Expenses & Drawings (Manual)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Slate900
                                    )
                                }

                                Divider(color = Slate100)

                                HouseholdInputField(
                                    label = if (isUrdu) "ماہانہ گھریلو راشن و باورچی خانہ خرچ:" else "Monthly Kitchen & Ration (Rs.):",
                                    value = if (hhExpenses.monthlyHouseholdExpense > 0) hhExpenses.monthlyHouseholdExpense.toInt().toString() else "",
                                    onValueChange = { hhExpenses = hhExpenses.copy(monthlyHouseholdExpense = it.toDoubleOrNull() ?: 0.0) }
                                )

                                HouseholdInputField(
                                    label = if (isUrdu) "بچوں کی سالانہ اسکول فیس:" else "Children School & College Fees (Rs.):",
                                    value = if (hhExpenses.schoolFee > 0) hhExpenses.schoolFee.toInt().toString() else "",
                                    onValueChange = { hhExpenses = hhExpenses.copy(schoolFee = it.toDoubleOrNull() ?: 0.0) }
                                )

                                HouseholdInputField(
                                    label = if (isUrdu) "گھر کا سالانہ بجلی، گیس، پانی بل:" else "Annual Home Utility Bills (Rs.):",
                                    value = if (hhExpenses.utilityBills > 0) hhExpenses.utilityBills.toInt().toString() else "",
                                    onValueChange = { hhExpenses = hhExpenses.copy(utilityBills = it.toDoubleOrNull() ?: 0.0) }
                                )

                                HouseholdInputField(
                                    label = if (isUrdu) "علاج و ادویات کے سالانہ اخراجات:" else "Annual Medical & Healthcare (Rs.):",
                                    value = if (hhExpenses.medical > 0) hhExpenses.medical.toInt().toString() else "",
                                    onValueChange = { hhExpenses = hhExpenses.copy(medical = it.toDoubleOrNull() ?: 0.0) }
                                )

                                HouseholdInputField(
                                    label = if (isUrdu) "دیگر سالانہ کھانا پینا و سفر خرچ:" else "Annual Food & Dining Out (Rs.):",
                                    value = if (hhExpenses.food > 0) hhExpenses.food.toInt().toString() else "",
                                    onValueChange = { hhExpenses = hhExpenses.copy(food = it.toDoubleOrNull() ?: 0.0) }
                                )

                                HouseholdInputField(
                                    label = if (isUrdu) "دکان سے نکالے گئے ذاتی پیسے (ڈرائنگز):" else "Personal Drawings from Shop (Rs.):",
                                    value = if (hhExpenses.personalDrawings > 0) hhExpenses.personalDrawings.toInt().toString() else "",
                                    onValueChange = { hhExpenses = hhExpenses.copy(personalDrawings = it.toDoubleOrNull() ?: 0.0) }
                                )

                                val totalHhAnnual = (hhExpenses.monthlyHouseholdExpense * 12) +
                                        hhExpenses.schoolFee +
                                        hhExpenses.utilityBills +
                                        hhExpenses.medical +
                                        hhExpenses.food +
                                        hhExpenses.personalDrawings

                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isUrdu) "کل گھریلو اخراجات (سالانہ):" else "Total Household Outflows (Annual):",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Slate800
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(totalHhAnnual),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }
                        }

                        // Right Section: Shop Business Expenses (Auto from Ledger)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (isUrdu) "کاروباری اخراجات (دکان کے لیجر سے خودکار)" else "Shop Business Expenses (Auto-Calculated)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Slate900
                                    )
                                }

                                Divider(color = Slate100)

                                Surface(
                                    color = Color(0xFFECFDF5),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (isUrdu) "کل کاروباری اخراجات ($selectedYear)" else "Total Business Expenses ($selectedYear)",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF065F46)
                                            )
                                            Text(
                                                text = FormatUtils.formatCurrency(fbrSummary.directExpenses),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = DukanGreenHover
                                            )
                                        }
                                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = DukanGreenPrimary, modifier = Modifier.size(24.dp))
                                    }
                                }

                                Text(
                                    text = if (isUrdu) "اخراجات کی تفصیلات (لیجر کیٹیگریز):" else "Expense Categories Breakdown:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )

                                if (expenseBreakdown.isEmpty()) {
                                    Text(
                                        text = if (isUrdu) "اس ٹیکس سال کے لیے دکان کے لیجر میں کوئی کاروباری خرچہ ریکارڈ نہیں ہے۔" else "No business expenses recorded in the shop ledger for this period.",
                                        fontSize = 11.sp,
                                        color = Slate400,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    expenseBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = cat, fontSize = 11.5.sp, color = Slate700, fontWeight = FontWeight.Medium)
                                            Text(text = FormatUtils.formatCurrency(amt), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------- TAB 5: LOANS & BORROWINGS ----------------
                AssetLoanTab.LOANS -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Register New Loan Form Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var lenderNameInput by remember { mutableStateOf("") }
                            var loanAmountInput by remember { mutableStateOf("") }
                            var loanClassInput by remember { mutableStateOf("BUSINESS_LOAN") } // "BUSINESS_LOAN" or "PERSONAL_LOAN"

                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isUrdu) "+ نیا قرضہ اکاؤنٹ شامل کریں" else "+ Register New Loan Account",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Slate700
                                )

                                OutlinedTextField(
                                    value = lenderNameInput,
                                    onValueChange = { lenderNameInput = it },
                                    placeholder = { Text(if (isUrdu) "قرض خواہ / بینک کا نام" else "Lender / Bank Name", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = loanAmountInput,
                                        onValueChange = { loanAmountInput = it },
                                        placeholder = { Text(if (isUrdu) "ابتدائی رقم (Rs.)" else "Initial Amount (Rs.)", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Classification selector pill
                                    Surface(
                                        onClick = {
                                            loanClassInput = if (loanClassInput == "BUSINESS_LOAN") "PERSONAL_LOAN" else "BUSINESS_LOAN"
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (loanClassInput == "BUSINESS_LOAN") Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                                        border = BorderStroke(1.dp, if (loanClassInput == "BUSINESS_LOAN") Color(0xFFA7F3D0) else Slate300),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (loanClassInput == "BUSINESS_LOAN") (if (isUrdu) "کاروباری" else "Business") else (if (isUrdu) "ذاتی" else "Personal"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (loanClassInput == "BUSINESS_LOAN") Color(0xFF065F46) else Slate700
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val amt = loanAmountInput.toDoubleOrNull() ?: 0.0
                                            if (lenderNameInput.isNotBlank() && amt > 0) {
                                                onAddLoan(
                                                    LoanRecord(
                                                        id = "loan_${System.currentTimeMillis()}",
                                                        type = loanClassInput,
                                                        classification = loanClassInput,
                                                        lenderOrBorrower = lenderNameInput.trim(),
                                                        lenderName = lenderNameInput.trim(),
                                                        amount = amt,
                                                        outstandingAmount = amt,
                                                        repaymentDate = FormatUtils.getTodayIsoDate(),
                                                        notes = "Registered as $loanClassInput"
                                                    )
                                                )
                                                lenderNameInput = ""
                                                loanAmountInput = ""
                                                Toast.makeText(context, if (isUrdu) "قرضہ اکاؤنٹ شامل ہو گیا" else "Loan Account Registered", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Text(text = if (isUrdu) "محفوظ کریں" else "Register", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Loans List with Inline Repayment & Borrowing Engine
                        if (state.loans.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isUrdu) "کوئی قرضہ ریکارڈ نہیں" else "No loan records found",
                                    color = Slate400,
                                    fontSize = 12.5.sp
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.loans, key = { it.id }) { loan ->
                                    val dynamicOutstanding = getLoanOutstanding(loan)
                                    val isBusiness = loan.classification == "BUSINESS_LOAN" || (loan.classification != "PERSONAL_LOAN" && loan.type != "PERSONAL_LOAN")
                                    val isSelected = selectedLoanId == loan.id

                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, if (isSelected) DukanGreenPrimary else Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text(
                                                            text = loan.partyName,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = Slate900
                                                        )
                                                        Surface(
                                                            color = if (isBusiness) Color(0xFFECFDF5) else Slate100,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isBusiness) (if (isUrdu) "کاروباری" else "BUSINESS") else (if (isUrdu) "ذاتی" else "PERSONAL"),
                                                                fontSize = 8.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isBusiness) Color(0xFF065F46) else Slate600,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "${if (isUrdu) "ابتدائی رقم:" else "Initial:"} ${FormatUtils.formatCurrency(loan.amount)}",
                                                        fontSize = 10.sp,
                                                        color = Slate500
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(
                                                            text = FormatUtils.formatCurrency(dynamicOutstanding),
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 13.sp,
                                                            color = RedDanger
                                                        )
                                                        Text(
                                                            text = if (isUrdu) "بقایا قرضہ" else "Outstanding",
                                                            fontSize = 9.sp,
                                                            color = Slate500
                                                        )
                                                    }

                                                    // "Log Entry" button
                                                    Button(
                                                        onClick = {
                                                            if (isSelected) {
                                                                selectedLoanId = null
                                                            } else {
                                                                selectedLoanId = loan.id
                                                                movementType = "REPAYMENT"
                                                                movementAmount = ""
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isSelected) DukanGreenPrimary else DukanGreenLight,
                                                            contentColor = if (isSelected) Color.White else DukanGreenHover
                                                        ),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text(text = if (isUrdu) "لیجر / انٹری" else "Log Entry", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    // Delete button with confirmation
                                                    if (deleteConfirmId == loan.id) {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Button(
                                                                onClick = {
                                                                    onDeleteLoan(loan.id)
                                                                    deleteConfirmId = null
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                                                                shape = RoundedCornerShape(6.dp),
                                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp)
                                                            ) {
                                                                Text(if (isUrdu) "ہاں" else "Yes", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                            OutlinedButton(
                                                                onClick = { deleteConfirmId = null },
                                                                shape = RoundedCornerShape(6.dp),
                                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp)
                                                            ) {
                                                                Text(if (isUrdu) "نہیں" else "No", fontSize = 9.sp)
                                                            }
                                                        }
                                                    } else {
                                                        IconButton(onClick = { deleteConfirmId = loan.id }, modifier = Modifier.size(28.dp)) {
                                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Slate400, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            // INLINE REPAYMENT & BORROWING ENGINE
                                            if (isSelected) {
                                                Surface(
                                                    color = Color(0xFFF8FAFC),
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, Slate200),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = if (isUrdu) "ٹرانزیکشن ریکارڈ کریں:" else "Record Transaction:",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Slate800
                                                            )

                                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Surface(
                                                                    onClick = { movementType = "TAKEN" },
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = if (movementType == "TAKEN") Color(0xFFECFDF5) else Color.White,
                                                                    border = BorderStroke(1.dp, if (movementType == "TAKEN") DukanGreenPrimary else Slate300)
                                                                ) {
                                                                    Text(
                                                                        text = if (isUrdu) "+ قرضہ لیا" else "+ Loan Taken",
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (movementType == "TAKEN") Color(0xFF065F46) else Slate600,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                                    )
                                                                }

                                                                Surface(
                                                                    onClick = { movementType = "REPAYMENT" },
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = if (movementType == "REPAYMENT") Color(0xFFFFF1F2) else Color.White,
                                                                    border = BorderStroke(1.dp, if (movementType == "REPAYMENT") RedDanger else Slate300)
                                                                ) {
                                                                    Text(
                                                                        text = if (isUrdu) "- قرض واپسی" else "- Repayment",
                                                                        fontSize = 9.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (movementType == "REPAYMENT") RedDanger else Slate600,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            OutlinedTextField(
                                                                value = movementAmount,
                                                                onValueChange = { movementAmount = it },
                                                                placeholder = { Text(if (isUrdu) "رقم (Rs.)" else "Amount (Rs.)", fontSize = 11.sp) },
                                                                singleLine = true,
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier.weight(1f)
                                                            )

                                                            Button(
                                                                onClick = {
                                                                    val amt = movementAmount.toDoubleOrNull() ?: 0.0
                                                                    if (amt > 0) {
                                                                        val txType = if (movementType == "TAKEN") TransactionType.LOAN_TAKEN else TransactionType.PAYMENT
                                                                        val cat = if (movementType == "TAKEN") "LOAN_TAKEN" else "LOAN_REPAYMENT"
                                                                        val note = movementNotes.ifBlank {
                                                                            if (movementType == "TAKEN") "Additional loan taken from ${loan.partyName}"
                                                                            else "Loan repayment to ${loan.partyName}"
                                                                        }

                                                                        onAddTransaction?.invoke(
                                                                            Transaction(
                                                                                id = "tx_${System.currentTimeMillis()}",
                                                                                type = txType,
                                                                                amount = amt,
                                                                                category = cat,
                                                                                paymentMethod = movementPaymentMethod,
                                                                                partyName = loan.partyName,
                                                                                date = FormatUtils.getTodayIsoDate(),
                                                                                notes = note,
                                                                                loanAccountId = loan.id
                                                                            )
                                                                        )

                                                                        movementAmount = ""
                                                                        movementNotes = ""
                                                                        selectedLoanId = null
                                                                        Toast.makeText(context, if (isUrdu) "انٹری محفوظ ہو گئی" else "Entry Recorded", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier.height(52.dp)
                                                            ) {
                                                                Text(if (isUrdu) "محفوظ کریں" else "Save Entry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }

                                                        // Linked Loan Transactions History
                                                        val linkedTxs = state.transactions.filter { it.loanAccountId == loan.id }
                                                        if (linkedTxs.isNotEmpty()) {
                                                            Divider(color = Slate200)
                                                            Text(
                                                                text = if (isUrdu) "قرضہ کی ٹرانزیکشن ہسٹری:" else "Linked Transactions History:",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Slate500
                                                            )
                                                            linkedTxs.take(4).forEach { tx ->
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = tx.notes?.ifBlank { tx.category } ?: tx.category,
                                                                        fontSize = 10.5.sp,
                                                                        color = Slate700
                                                                    )
                                                                    Text(
                                                                        text = "${if (tx.type == TransactionType.LOAN_TAKEN) "+" else "-"}${FormatUtils.formatCurrency(tx.amount)}",
                                                                        fontSize = 10.5.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (tx.type == TransactionType.LOAN_TAKEN) Color(0xFF065F46) else RedDanger
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
                }
            }
        }
    }
}

@Composable
private fun BalanceSheetItem(
    title: String,
    subtitle: String,
    amount: Double,
    color: Color
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Slate100),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, fontSize = 9.5.sp, color = Slate400)
                }
            }
            Text(
                text = FormatUtils.formatCurrency(amount),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun HouseholdInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
