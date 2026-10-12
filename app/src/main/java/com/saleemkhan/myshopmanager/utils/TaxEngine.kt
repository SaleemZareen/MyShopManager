package com.saleemkhan.myshopmanager.utils

import com.saleemkhan.myshopmanager.model.*

data class OperatingExpensesBreakdown(
    val rent: Double = 0.0,
    val salaries: Double = 0.0,
    val utilityBills: Double = 0.0,
    val travellingFuel: Double = 0.0,
    val communication: Double = 0.0,
    val otherExpenses: Double = 0.0,
    val totalExpenses: Double = 0.0
)

data class IrisMappingItem(
    val code: String,
    val section: String, // "TRADING", "EXPENSES", "BALANCE_SHEET"
    val titleEn: String,
    val titleUr: String,
    val amount: Double,
    val description: String
)

data class WealthRecon(
    val openingWealth: Double,
    val netBusinessProfit: Double,
    val otherIncomeTotal: Double,
    val capitalInflow: Double,
    val householdExpensesTotal: Double,
    val taxesPaidTotal: Double,
    val expectedNetWealth: Double,
    val actualNetWealth: Double,
    val unexplainedDifference: Double,
    val isBalanced: Boolean // Difference <= 50,000 Rs
)

data class WithholdingTaxData(
    val utilityWithholding: Double,
    val mobileWithholding: Double,
    val bankWithholding: Double,
    val vehicleTax: Double,
    val advanceTax: Double,
    val psidPayments: Double,
    val psidChallanNumber: String,
    val otherWithholding: Double,
    val totalAdjustableTax: Double,
    val estimatedIncomeTax: Double,
    val netTaxPayable: Double // estimatedIncomeTax - totalAdjustableTax
)

data class FbrSummary(
    val sales: Double = 0.0,
    val purchases: Double = 0.0,
    val directExpenses: Double = 0.0,
    val stockValue: Double = 0.0,
    val openingStockValue: Double = 0.0,
    val rawCogs: Double = 0.0,
    val estimatedCogs: Double? = null,
    val grossProfit: Double? = null,
    val netProfit: Double? = null,
    val totalReceivables: Double = 0.0,
    val totalPayables: Double = 0.0,
    val outstandingLoans: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val totalAssets: Double = 0.0,
    val netWorth: Double = 0.0,
    val ownerCapital: Double? = null,
    val isStockDataMissing: Boolean = false,
    val isStockUnavailable: Boolean = false,
    val cashAndBank: Double = 0.0,
    val totalBusinessAssets: Double = 0.0,
    val expensesBreakdown: OperatingExpensesBreakdown = OperatingExpensesBreakdown()
) {
    val totalSales: Double get() = sales
    val cogs: Double get() = estimatedCogs ?: rawCogs
    val closingStockValue: Double get() = stockValue
    val totalSupplierPayables: Double get() = totalPayables
    val totalLoans: Double get() = outstandingLoans
    val estimatedIncomeTax: Double get() = TaxEngine.calculateFbrNonSalariedTax(netProfit ?: 0.0)
}

object TaxEngine {

    /**
     * Official FBR Income Tax Slab calculation for Non-Salaried Individuals & AOPs
     * according to the First Schedule (Part I, Division I) of Income Tax Ordinance 2001:
     * - Up to Rs. 600,000: 0% tax
     * - Rs. 600,001 to Rs. 1,200,000: 15% of amount exceeding Rs. 600,000
     * - Rs. 1,200,001 to Rs. 1,600,000: Rs. 90,000 + 20% of amount exceeding Rs. 1,200,000
     * - Rs. 1,600,001 to Rs. 3,200,000: Rs. 170,000 + 30% of amount exceeding Rs. 1,600,000
     * - Rs. 3,200,001 to Rs. 5,600,000: Rs. 650,000 + 40% of amount exceeding Rs. 3,200,000
     * - Exceeding Rs. 5,600,000: Rs. 1,610,000 + 45% of amount exceeding Rs. 5,600,000
     */
    fun calculateFbrNonSalariedTax(taxableIncome: Double): Double {
        if (taxableIncome <= 600000.0) return 0.0
        val income = (taxableIncome * 100.0).run { Math.round(this) / 100.0 }

        return when {
            income <= 1200000.0 -> (income - 600000.0) * 0.15
            income <= 1600000.0 -> 90000.0 + (income - 1200000.0) * 0.20
            income <= 3200000.0 -> 170000.0 + (income - 1600000.0) * 0.30
            income <= 5600000.0 -> 650000.0 + (income - 3200000.0) * 0.40
            else -> 1610000.0 + (income - 5600000.0) * 0.45
        }
    }

    fun calculateFbrSummary(state: AppState): FbrSummary {
        val rawTransactions = state.transactions
        val inventory = state.inventory
        val businessAssets = state.businessAssets
        val personalAssets = state.personalAssets
        val loans = state.loans
        val householdExpenses = state.householdExpenses
        val customers = state.customers
        val suppliers = state.suppliers
        val bankAccounts = state.bankAccounts
        val selectedYear = state.taxRecord.selectedTaxYear.ifBlank { "2026" }

        val isWithinPeriod = { dateStr: String? ->
            if (dateStr.isNullOrBlank()) false
            else if (dateStr.length >= 4) dateStr.substring(0, 4) == selectedYear
            else false
        }

        val transactions = rawTransactions.filter { t ->
            if (!isWithinPeriod(t.date)) return@filter false
            val isLoanTx = t.type == TransactionType.LOAN_TAKEN || (t.type == TransactionType.PAYMENT && t.category.equals("LOAN_REPAYMENT", ignoreCase = true))
            if (isLoanTx && t.loanAccountId == null) {
                return@filter false
            }
            true
        }

        val sales = transactions
            .filter { it.type == TransactionType.SALE }
            .sumOf { it.amount }

        val purchases = transactions
            .filter { it.type == TransactionType.PURCHASE }
            .sumOf { it.amount }

        // Filter standard operating expense transactions
        val expenseTxList = transactions.filter {
            it.type == TransactionType.EXPENSE &&
            !it.category.equals("DRAWINGS", ignoreCase = true) &&
            !it.category.equals("PERSONAL_DRAWINGS", ignoreCase = true) &&
            !it.category.equals("LOAN_REPAYMENT", ignoreCase = true)
        }

        var rentExp = 0.0
        var salaryExp = 0.0
        var utilityExp = 0.0
        var travelExp = 0.0
        var commExp = 0.0
        var otherExp = 0.0

        for (tx in expenseTxList) {
            val cat = tx.category.lowercase()
            when {
                cat.contains("rent") || cat.contains("دکان کرایہ") || cat.contains("کرایہ") -> rentExp += tx.amount
                cat.contains("salary") || cat.contains("salaries") || cat.contains("staff") || cat.contains("ملازمین") || cat.contains("تنخواہ") -> salaryExp += tx.amount
                cat.contains("electric") || cat.contains("bijli") || cat.contains("utility") || cat.contains("bill") || cat.contains("بجلی") -> utilityExp += tx.amount
                cat.contains("travel") || cat.contains("fuel") || cat.contains("petrol") || cat.contains("conveyance") || cat.contains("کرایہ سواری") -> travelExp += tx.amount
                cat.contains("phone") || cat.contains("mobile") || cat.contains("internet") || cat.contains("نیٹ") || cat.contains("فون") -> commExp += tx.amount
                else -> otherExp += tx.amount
            }
        }

        val directExpensesBase = rentExp + salaryExp + utilityExp + travelExp + commExp + otherExp

        val stockAdjustments = transactions.filter { it.category.equals("STOCK_ADJUSTMENT", ignoreCase = true) }
        val negativeAdjustmentsValue = stockAdjustments
            .filter { (it.totalValueChange ?: 0.0) < 0 }
            .sumOf { Math.abs(it.totalValueChange ?: 0.0) }

        val positiveAdjCapital = stockAdjustments
            .filter { (it.totalValueChange ?: 0.0) > 0 && it.balancingTreatment == "CAPITAL_CONTRIBUTION" }
            .sumOf { it.totalValueChange ?: 0.0 }

        val positiveAdjIncome = stockAdjustments
            .filter { (it.totalValueChange ?: 0.0) > 0 && it.balancingTreatment == "OTHER_INCOME" }
            .sumOf { it.totalValueChange ?: 0.0 }

        val positiveAdjPastError = stockAdjustments
            .filter { (it.totalValueChange ?: 0.0) > 0 && it.balancingTreatment == "PAST_ERROR_CORRECTION" }
            .sumOf { it.totalValueChange ?: 0.0 }

        val positiveAdjUnknown = stockAdjustments
            .filter { (it.totalValueChange ?: 0.0) > 0 && (it.balancingTreatment == null || it.balancingTreatment == "UNKNOWN") }
            .sumOf { it.totalValueChange ?: 0.0 }

        val positiveAdjustmentsValue = positiveAdjCapital + positiveAdjIncome + positiveAdjPastError + positiveAdjUnknown

        val totalExpensesWithAdj = directExpensesBase + negativeAdjustmentsValue
        val breakdown = OperatingExpensesBreakdown(
            rent = rentExp,
            salaries = salaryExp,
            utilityBills = utilityExp,
            travellingFuel = travelExp,
            communication = commExp,
            otherExpenses = otherExp + negativeAdjustmentsValue,
            totalExpenses = totalExpensesWithAdj
        )

        val isStockDataMissing = inventory.isEmpty()
        val stockValue = inventory.sumOf { it.quantity * it.purchasePrice }
        val openingStockValue = inventory.sumOf { it.openingStock * it.purchasePrice }

        val rawCogs = openingStockValue + purchases - stockValue - negativeAdjustmentsValue + positiveAdjustmentsValue
        val isCogsNegative = !isStockDataMissing && rawCogs < 0
        val isStockUnavailable = isStockDataMissing || isCogsNegative

        val estimatedCogs = if (isStockUnavailable) null else rawCogs
        val grossProfit = if (isStockUnavailable) null else (sales - rawCogs)
        val netProfit = if (isStockUnavailable) null else (sales + positiveAdjIncome - rawCogs - totalExpensesWithAdj)

        val totalReceivables = customers.sumOf { it.totalCredit }
        val totalPayables = suppliers.sumOf { it.totalPayable }

        val outstandingLoans = loans.sumOf { it.outstandingAmount }
        val totalLiabilities = outstandingLoans + totalPayables

        val businessAssetValue = businessAssets.sumOf { it.currentValue }
        val personalAssetValue = personalAssets.sumOf { it.value }
        val totalCashAndBank = bankAccounts.sumOf { it.balance }

        val totalBusinessAssets = businessAssetValue + (if (isStockUnavailable) 0.0 else stockValue) + totalReceivables + totalCashAndBank
        val totalAssets = totalBusinessAssets + personalAssetValue
        val netWorth = totalAssets - totalLiabilities

        val openingOwnerEquity = (state.taxRecord.openingOwnerEquity[selectedYear] ?: 120000.0) + positiveAdjPastError
        val capitalIntroduced = transactions
            .filter { it.type == TransactionType.RECEIPT && (it.category.contains("CAPITAL", ignoreCase = true) || it.category.contains("OWNER", ignoreCase = true)) }
            .sumOf { it.amount } + positiveAdjCapital

        val drawings = householdExpenses.personalDrawings + transactions
            .filter { it.type == TransactionType.PAYMENT && (it.category.contains("DRAWINGS", ignoreCase = true)) }
            .sumOf { it.amount }

        val closingOwnerEquity = if (isStockUnavailable) null else (openingOwnerEquity + capitalIntroduced + (netProfit ?: 0.0) - drawings)

        return FbrSummary(
            sales = sales,
            purchases = purchases,
            directExpenses = totalExpensesWithAdj,
            stockValue = stockValue,
            openingStockValue = openingStockValue,
            rawCogs = rawCogs,
            estimatedCogs = estimatedCogs,
            grossProfit = grossProfit,
            netProfit = netProfit,
            totalReceivables = totalReceivables,
            totalPayables = totalPayables,
            outstandingLoans = outstandingLoans,
            totalLiabilities = totalLiabilities,
            totalAssets = totalAssets,
            netWorth = netWorth,
            ownerCapital = closingOwnerEquity,
            isStockDataMissing = isStockDataMissing,
            isStockUnavailable = isStockUnavailable,
            cashAndBank = totalCashAndBank,
            totalBusinessAssets = totalBusinessAssets,
            expensesBreakdown = breakdown
        )
    }

    fun getIrisMappings(summary: FbrSummary, state: AppState): List<IrisMappingItem> {
        val exp = summary.expensesBreakdown
        return listOf(
            // ---- SECTION 1: TRADING / MANUFACTURING ACCOUNT (SECTION 114) ----
            IrisMappingItem(
                code = "Code 7000",
                section = "TRADING",
                titleEn = "Gross Turnover / Total Sales",
                titleUr = "مجموعی سالانہ سیلز / ٹرن اوور",
                amount = summary.sales,
                description = "Total revenue from sales before deductions"
            ),
            IrisMappingItem(
                code = "Code 7001",
                section = "TRADING",
                titleEn = "Opening Stock Value",
                titleUr = "شروع کا اسٹاک (مالِ آغاز)",
                amount = summary.openingStockValue,
                description = "Inventory valuation at the start of the tax year"
            ),
            IrisMappingItem(
                code = "Code 7002",
                section = "TRADING",
                titleEn = "Total Purchases (Net)",
                titleUr = "سالانہ خریداری (مالِ درآمد)",
                amount = summary.purchases,
                description = "Gross purchases of goods for resale"
            ),
            IrisMappingItem(
                code = "Code 7010",
                section = "TRADING",
                titleEn = "Closing Stock Value",
                titleUr = "اختتامی اسٹاک (مالِ آخر)",
                amount = summary.closingStockValue,
                description = "Inventory valuation at year end"
            ),
            IrisMappingItem(
                code = "Code 7011",
                section = "TRADING",
                titleEn = "Cost of Sales / COGS",
                titleUr = "فروخت شدہ مال کی لاگت (COGS)",
                amount = summary.cogs,
                description = "Opening stock + purchases - closing stock"
            ),
            IrisMappingItem(
                code = "Code 7020",
                section = "TRADING",
                titleEn = "Gross Profit",
                titleUr = "مجموعی منافع (Gross Profit)",
                amount = summary.grossProfit ?: 0.0,
                description = "Turnover minus Cost of Sales"
            ),

            // ---- SECTION 2: MANAGEMENT & OPERATING EXPENSES (SECTION 114) ----
            IrisMappingItem(
                code = "Code 7031",
                section = "EXPENSES",
                titleEn = "Rent of Shop / Warehouse",
                titleUr = "دکان / گودام کا سالانہ کرایہ",
                amount = exp.rent,
                description = "Rental expenses for commercial premises"
            ),
            IrisMappingItem(
                code = "Code 7032",
                section = "EXPENSES",
                titleEn = "Salaries, Wages & Staff Benefits",
                titleUr = "ملازمین کی تنخواہیں و اجرت",
                amount = exp.salaries,
                description = "Remuneration paid to employees"
            ),
            IrisMappingItem(
                code = "Code 7033",
                section = "EXPENSES",
                titleEn = "Electricity & Utility Bills",
                titleUr = "بجلی اور گیس کے بل",
                amount = exp.utilityBills,
                description = "Commercial electricity, gas and water utilities"
            ),
            IrisMappingItem(
                code = "Code 7034",
                section = "EXPENSES",
                titleEn = "Travelling, Conveyance & Fuel",
                titleUr = "سفری اخراجات، سواری اور پیٹرول",
                amount = exp.travellingFuel,
                description = "Transport, vehicle fuel and goods delivery costs"
            ),
            IrisMappingItem(
                code = "Code 7035",
                section = "EXPENSES",
                titleEn = "Communication, Phone & Internet",
                titleUr = "ٹیلی فون، موبائل اور انٹرنیٹ",
                amount = exp.communication,
                description = "Commercial telecom and broadband expenses"
            ),
            IrisMappingItem(
                code = "Code 7039",
                section = "EXPENSES",
                titleEn = "Other Operating & Sundry Expenses",
                titleUr = "متفرق انتظامی و دفتری اخراجات",
                amount = exp.otherExpenses,
                description = "General maintenance, tea, stationery, stock loss"
            ),
            IrisMappingItem(
                code = "Code 7040",
                section = "EXPENSES",
                titleEn = "Total Management & Operating Deductions",
                titleUr = "مجموعی دفتری و انتظامی اخراجات",
                amount = exp.totalExpenses,
                description = "Sum of all business deductions"
            ),
            IrisMappingItem(
                code = "Code 7050",
                section = "EXPENSES",
                titleEn = "Net Taxable Business Profit",
                titleUr = "خالص قابلِ ٹیکس کاروباری منافع",
                amount = summary.netProfit ?: 0.0,
                description = "Gross profit minus total deductions"
            ),

            // ---- SECTION 3: BALANCE SHEET & WEALTH STATEMENT (SECTION 116) ----
            IrisMappingItem(
                code = "Code 7001",
                section = "BALANCE_SHEET",
                titleEn = "Cash in Hand & Bank Balances",
                titleUr = "نقدی رقم (گلہ) اور بینک بیلنس",
                amount = summary.cashAndBank,
                description = "Total liquid cash in till and bank accounts"
            ),
            IrisMappingItem(
                code = "Code 7002",
                section = "BALANCE_SHEET",
                titleEn = "Stock-in-Trade / Inventory",
                titleUr = "دکان میں موجود سامانِ تجارت (اسٹاک)",
                amount = summary.closingStockValue,
                description = "Cost of closing stock in possession"
            ),
            IrisMappingItem(
                code = "Code 7003",
                section = "BALANCE_SHEET",
                titleEn = "Trade Debtors / Customer Udhaar Receivables",
                titleUr = "گاہکوں کے ذمہ واجب الادا ادھار کھاتے",
                amount = summary.totalReceivables,
                description = "Outstanding credit receivable from buyers"
            ),
            IrisMappingItem(
                code = "Code 7004",
                section = "BALANCE_SHEET",
                titleEn = "Business Fixed Assets (Fixtures & Equipment)",
                titleUr = "دکان کے فکسڈ اثاثے (فرنیچر، کاؤنٹر، مشینری)",
                amount = summary.totalBusinessAssets - summary.cashAndBank - summary.closingStockValue - summary.totalReceivables,
                description = "Book value of shop equipment, freezers, solar"
            ),
            IrisMappingItem(
                code = "Code 7005",
                section = "BALANCE_SHEET",
                titleEn = "Trade Creditors / Supplier Payables",
                titleUr = "سپلائرز کے واجبات (ادھار مال کی ادائیگیاں)",
                amount = summary.totalSupplierPayables,
                description = "Amounts payable to distributors and vendors"
            ),
            IrisMappingItem(
                code = "Code 7006",
                section = "BALANCE_SHEET",
                titleEn = "Business Loans & Borrowings Payable",
                titleUr = "کاروباری قرضہ جات و ادھار واجب الادا",
                amount = summary.outstandingLoans,
                description = "Total outstanding bank and personal borrowings"
            ),
            IrisMappingItem(
                code = "Code 7009",
                section = "BALANCE_SHEET",
                titleEn = "Owner's Net Capital / Business Equity",
                titleUr = "دکان کا اصل سرمایہ و مالکانہ حصہ (Equity)",
                amount = summary.ownerCapital ?: 0.0,
                description = "Owner capital balance at year end"
            ),
            IrisMappingItem(
                code = "Code 7020",
                section = "BALANCE_SHEET",
                titleEn = "Total Net Wealth (Business + Personal Net Assets)",
                titleUr = "مجموعی خالص دولت (خالص مالیت / Net Worth)",
                amount = summary.netWorth,
                description = "All business & personal assets minus all liabilities"
            )
        )
    }

    fun getWealthReconciliation(summary: FbrSummary, state: AppState): WealthRecon {
        val selectedYear = state.taxRecord.selectedTaxYear.ifBlank { "2026" }
        val openingWealth = state.taxRecord.openingOwnerEquity[selectedYear] ?: 120000.0
        val netBusinessProfit = summary.netProfit ?: 0.0

        val other = state.otherIncome
        val otherIncomeTotal = other.rentalIncome + other.bankProfit + other.agriculturalIncome +
                other.capitalGain + other.freelanceIncome + other.otherIncome

        val positiveAdjCapital = state.transactions
            .filter { it.category.equals("STOCK_ADJUSTMENT", ignoreCase = true) && (it.totalValueChange ?: 0.0) > 0 && it.balancingTreatment == "CAPITAL_CONTRIBUTION" }
            .sumOf { it.totalValueChange ?: 0.0 }

        val capitalInflow = state.transactions
            .filter { it.type == TransactionType.RECEIPT && (it.category.contains("CAPITAL", ignoreCase = true) || it.category.contains("OWNER", ignoreCase = true)) }
            .sumOf { it.amount } + positiveAdjCapital

        val hh = state.householdExpenses
        val annualHhExpenses = (hh.monthlyHouseholdExpense * 12.0) + (hh.schoolFee * 12.0) +
                (hh.medical * 12.0) + (hh.food * 12.0) + (hh.utilityBills * 12.0) + hh.personalDrawings

        val txDrawings = state.transactions
            .filter { it.type == TransactionType.PAYMENT && it.category.contains("DRAWINGS", ignoreCase = true) }
            .sumOf { it.amount }

        val totalHousehold = annualHhExpenses + txDrawings

        val taxRec = state.taxRecord
        val taxesPaidTotal = taxRec.adjustableTax + taxRec.advanceTax + taxRec.psidPayments +
                taxRec.bankWithholding + taxRec.utilityWithholding + taxRec.mobileWithholding +
                taxRec.vehicleTax + taxRec.propertyTax + taxRec.otherTaxCertificates

        val expectedNetWealth = openingWealth + netBusinessProfit + otherIncomeTotal + capitalInflow - totalHousehold - taxesPaidTotal
        val actualNetWealth = summary.netWorth
        val diff = Math.abs(expectedNetWealth - actualNetWealth)

        return WealthRecon(
            openingWealth = openingWealth,
            netBusinessProfit = netBusinessProfit,
            otherIncomeTotal = otherIncomeTotal,
            capitalInflow = capitalInflow,
            householdExpensesTotal = totalHousehold,
            taxesPaidTotal = taxesPaidTotal,
            expectedNetWealth = expectedNetWealth,
            actualNetWealth = actualNetWealth,
            unexplainedDifference = diff,
            isBalanced = diff <= 50000.0
        )
    }

    fun getWithholdingTaxData(summary: FbrSummary, state: AppState): WithholdingTaxData {
        val taxRec = state.taxRecord
        val totalAdjustable = taxRec.utilityWithholding + taxRec.mobileWithholding +
                taxRec.bankWithholding + taxRec.vehicleTax + taxRec.advanceTax +
                taxRec.psidPayments + taxRec.otherTaxCertificates + taxRec.propertyTax

        val estimatedGrossTax = summary.estimatedIncomeTax
        val netPayable = (estimatedGrossTax - totalAdjustable).coerceAtLeast(0.0)

        return WithholdingTaxData(
            utilityWithholding = taxRec.utilityWithholding,
            mobileWithholding = taxRec.mobileWithholding,
            bankWithholding = taxRec.bankWithholding,
            vehicleTax = taxRec.vehicleTax,
            advanceTax = taxRec.advanceTax,
            psidPayments = taxRec.psidPayments,
            psidChallanNumber = taxRec.psidChallanNumber,
            otherWithholding = taxRec.otherTaxCertificates + taxRec.propertyTax,
            totalAdjustableTax = totalAdjustable,
            estimatedIncomeTax = estimatedGrossTax,
            netTaxPayable = netPayable
        )
    }
}
