package com.saleemkhan.myshopmanager.model

enum class Screen {
    DASHBOARD,
    TRANSACTIONS,
    KHATA,
    INVENTORY,
    ASSETS_LOANS,
    FBR_TAX,
    REPORTS,
    ANALYTICS,
    SETTINGS,
    BACKUP_SYNC,
    NOTIFICATIONS
}

enum class StoreMode {
    SIMPLE, // General Store, Kiryana, Parchoon, Grocery
    SPECIALIZED // Mobile, Pharmacy, Garments, Hardware, Electronics, Cosmetics, Auto Parts
}

enum class StoreCategory {
    KIRYANA_GENERAL,
    MOBILE_SHOP,
    PHARMACY,
    GARMENTS,
    HARDWARE,
    ELECTRONICS,
    COSMETICS,
    AUTO_PARTS,
    OTHER
}

enum class PaymentMethod {
    CASH,
    BANK,
    EASYPAISA,
    JAZZCASH,
    NAYAPAY,
    SADAPAY,
    CREDIT
}

enum class TransactionType {
    SALE,
    PURCHASE,
    EXPENSE,
    RECEIPT,
    PAYMENT,
    LOAN_GIVEN,
    LOAN_TAKEN,
    TRANSFER
}

enum class ExpenseCategory {
    SHOP_RENT,
    ELECTRICITY,
    GAS,
    WATER,
    INTERNET,
    PHONE,
    TRANSPORT,
    FUEL,
    SALARY,
    TEA,
    CLEANING,
    REPAIR,
    MAINTENANCE,
    PACKAGING,
    COURIER,
    ADVERTISING,
    STATIONERY,
    PRINTING,
    PROFESSIONAL_FEE,
    BANK_CHARGES,
    POS_CHARGES,
    MISCELLANEOUS,
    CUSTOM
}

data class Transaction(
    val id: String = "tx_${System.currentTimeMillis()}",
    val type: TransactionType = TransactionType.SALE,
    val amount: Double = 0.0,
    val category: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val partyName: String? = null,
    val partyId: String? = null,
    val invoiceNo: String? = null,
    val date: String = "", // ISO String
    val notes: String? = null,
    val receiptImageUrl: String? = null,
    val itemId: String? = null,
    val qtyChange: Double? = null,
    val unitCost: Double? = null,
    val totalValueChange: Double? = null,
    val adjustmentReason: String? = null,
    val balancingTreatment: String? = null,
    val sourcePolicy: String? = null,
    val loanAccountId: String? = null,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val isCogsEstimated: Boolean = false,
    val isCogsMissing: Boolean = false,
    val legacyClassification: String? = null
)

data class InventoryItem(
    val id: String = "item_${System.currentTimeMillis()}",
    val name: String = "",
    val category: String = "",
    val unit: String = "pcs",
    val purchasePrice: Double = 0.0, // Represents current Weighted Average Unit Cost (AVCO)
    val salePrice: Double = 0.0,
    val quantity: Double = 0.0,
    val minStockAlert: Double = 5.0,
    val barcode: String? = null,
    val sku: String? = null,
    val openingStock: Double = 0.0,
    val openingCostPrice: Double = 0.0,
    val purchasedQty: Double = 0.0,
    val soldQty: Double = 0.0,
    val damagedQty: Double = 0.0,
    val returnedQty: Double = 0.0
) {
    val costPrice: Double get() = purchasePrice
    val sellingPrice: Double get() = salePrice
}

data class CustomerParty(
    val id: String = "cust_${System.currentTimeMillis()}",
    val name: String = "",
    val phone: String = "",
    val totalCredit: Double = 0.0,
    val lastTransactionDate: String = "",
    val notes: String? = null,
    val status: String? = "ACTIVE",
    val openingBalance: Double = 0.0
)

data class SupplierParty(
    val id: String = "supp_${System.currentTimeMillis()}",
    val name: String = "",
    val phone: String = "",
    val totalPayable: Double = 0.0,
    val dueDate: String? = null,
    val notes: String? = null,
    val status: String? = "ACTIVE",
    val openingBalance: Double = 0.0
)

data class BankAccount(
    val id: String = "acc_${System.currentTimeMillis()}",
    val accountTitle: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val type: String = "CASH", // CASH, BANK, WALLET
    val balance: Double = 0.0,
    val openingBalance: Double = 0.0
)

data class BusinessAsset(
    val id: String = "asset_${System.currentTimeMillis()}",
    val name: String = "",
    val category: String = "",
    val purchaseDate: String = "",
    val purchasePrice: Double = 0.0,
    val usefulLifeYears: Int = 5,
    val currentValue: Double = 0.0
)

data class PersonalAsset(
    val id: String = "passet_${System.currentTimeMillis()}",
    val name: String = "",
    val category: String = "",
    val value: Double = 0.0
)

data class LoanRecord(
    val id: String = "loan_${System.currentTimeMillis()}",
    val type: String = "PERSONAL_LOAN", // PERSONAL_LOAN, BUSINESS_LOAN, BANK_LOAN, BORROWING
    val classification: String? = "PERSONAL_LOAN",
    val lenderOrBorrower: String = "",
    val lenderName: String = lenderOrBorrower,
    val amount: Double = 0.0,
    val outstandingAmount: Double = 0.0,
    val repaymentDate: String? = null,
    val startDate: String? = null,
    val interestRate: Double? = null,
    val notes: String? = null
) {
    val partyName: String get() = if (lenderName.isNotBlank()) lenderName else lenderOrBorrower
    val dueDate: String get() = repaymentDate ?: ""
    val loanType: String get() = type
    val purpose: String get() = notes ?: classification ?: ""
}

data class HouseholdExpenses(
    val monthlyHouseholdExpense: Double = 0.0,
    val schoolFee: Double = 0.0,
    val medical: Double = 0.0,
    val food: Double = 0.0,
    val utilityBills: Double = 0.0,
    val personalDrawings: Double = 0.0
)

data class OtherIncome(
    val rentalIncome: Double = 0.0,
    val bankProfit: Double = 0.0,
    val agriculturalIncome: Double = 0.0,
    val capitalGain: Double = 0.0,
    val freelanceIncome: Double = 0.0,
    val otherIncome: Double = 0.0
)

data class TaxRecord(
    val adjustableTax: Double = 0.0,
    val advanceTax: Double = 0.0,
    val psidPayments: Double = 0.0,
    val psidChallanNumber: String = "",
    val bankWithholding: Double = 0.0,
    val utilityWithholding: Double = 0.0,
    val mobileWithholding: Double = 0.0,
    val vehicleTax: Double = 0.0,
    val propertyTax: Double = 0.0,
    val otherTaxCertificates: Double = 0.0,
    val selectedTaxYear: String = "2026",
    val openingOwnerEquity: Map<String, Double> = mapOf("2024" to 0.0, "2025" to 0.0, "2026" to 0.0),
    val openingBusinessLoanBalance: Map<String, Double> = mapOf("2024" to 0.0, "2025" to 0.0, "2026" to 0.0)
)

data class ShopProfile(
    val id: String = "shop_01",
    val shopName: String = "My Shop",
    val shopNameUrdu: String? = "میری دکان",
    val subtitle: String = "Dukan Management",
    val ownerName: String = "",
    val phone: String = "",
    val email: String? = null,
    val cnic: String = "",
    val ntn: String = "",
    val address: String = "",
    val storeMode: StoreMode = StoreMode.SIMPLE,
    val storeCategory: StoreCategory = StoreCategory.KIRYANA_GENERAL,
    val currencySymbol: String = "Rs.",
    val pinCode: String? = null,
    val passwordCode: String? = null,
    val biometricsEnabled: Boolean = false,
    val activeLanguage: String = "en" // 'en' or 'ur'
) {
    val securityPin: String get() = pinCode ?: passwordCode ?: ""
}

data class ShopInfo(
    val id: String = "shop_01",
    val name: String = "My Shop"
)

data class AppState(
    val profile: ShopProfile = ShopProfile(),
    val transactions: List<Transaction> = emptyList(),
    val deletedTransactions: List<Transaction> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val customers: List<CustomerParty> = emptyList(),
    val suppliers: List<SupplierParty> = emptyList(),
    val bankAccounts: List<BankAccount> = emptyList(),
    val businessAssets: List<BusinessAsset> = emptyList(),
    val personalAssets: List<PersonalAsset> = emptyList(),
    val loans: List<LoanRecord> = emptyList(),
    val householdExpenses: HouseholdExpenses = HouseholdExpenses(),
    val otherIncome: OtherIncome = OtherIncome(),
    val taxRecord: TaxRecord = TaxRecord(),
    val activeShopId: String = "shop_01",
    val otherShops: List<ShopInfo> = listOf(ShopInfo("shop_01", "My Shop"))
)

enum class NotificationType {
    LOW_STOCK,
    HIGH_CREDIT,
    SUPPLIER_DUE,
    LOAN_DUE
}

data class AppNotificationItem(
    val id: String,
    val textEn: String,
    val textUr: String,
    val emoji: String,
    val type: NotificationType,
    val targetScreen: Screen,
    val targetId: String? = null
)
