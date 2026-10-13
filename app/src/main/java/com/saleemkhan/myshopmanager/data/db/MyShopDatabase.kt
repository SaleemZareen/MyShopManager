package com.saleemkhan.myshopmanager.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.utils.FormatUtils

class MyShopDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val gson = Gson()

    companion object {
        private const val DATABASE_NAME = "myshopmanager.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: MyShopDatabase? = null

        fun getInstance(context: Context): MyShopDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MyShopDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE profile (
                id TEXT PRIMARY KEY,
                shopName TEXT,
                shopNameUrdu TEXT,
                subtitle TEXT,
                ownerName TEXT,
                phone TEXT,
                email TEXT,
                cnic TEXT,
                ntn TEXT,
                address TEXT,
                storeMode TEXT,
                storeCategory TEXT,
                currencySymbol TEXT,
                pinCode TEXT,
                passwordCode TEXT,
                biometricsEnabled INTEGER,
                activeLanguage TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE inventory (
                id TEXT PRIMARY KEY,
                name TEXT,
                category TEXT,
                unit TEXT,
                purchasePrice REAL,
                salePrice REAL,
                quantity REAL,
                minStockAlert REAL,
                barcode TEXT,
                sku TEXT,
                openingStock REAL,
                openingCostPrice REAL,
                purchasedQty REAL,
                soldQty REAL,
                damagedQty REAL,
                returnedQty REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE customers (
                id TEXT PRIMARY KEY,
                name TEXT,
                phone TEXT,
                totalCredit REAL,
                lastTransactionDate TEXT,
                notes TEXT,
                status TEXT,
                openingBalance REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE suppliers (
                id TEXT PRIMARY KEY,
                name TEXT,
                phone TEXT,
                totalPayable REAL,
                dueDate TEXT,
                notes TEXT,
                status TEXT,
                openingBalance REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE bank_accounts (
                id TEXT PRIMARY KEY,
                accountTitle TEXT,
                bankName TEXT,
                accountNumber TEXT,
                type TEXT,
                balance REAL,
                openingBalance REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE transactions (
                id TEXT PRIMARY KEY,
                type TEXT,
                amount REAL,
                category TEXT,
                paymentMethod TEXT,
                partyName TEXT,
                partyId TEXT,
                invoiceNo TEXT,
                date TEXT,
                notes TEXT,
                receiptImageUrl TEXT,
                itemId TEXT,
                qtyChange REAL,
                unitCost REAL,
                totalValueChange REAL,
                adjustmentReason TEXT,
                balancingTreatment TEXT,
                sourcePolicy TEXT,
                loanAccountId TEXT,
                accountId TEXT,
                toAccountId TEXT,
                isCogsEstimated INTEGER,
                isCogsMissing INTEGER,
                legacyClassification TEXT,
                cashPaid REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE deleted_transactions (
                id TEXT PRIMARY KEY,
                type TEXT,
                amount REAL,
                category TEXT,
                paymentMethod TEXT,
                partyName TEXT,
                partyId TEXT,
                invoiceNo TEXT,
                date TEXT,
                notes TEXT,
                receiptImageUrl TEXT,
                itemId TEXT,
                qtyChange REAL,
                unitCost REAL,
                totalValueChange REAL,
                adjustmentReason TEXT,
                balancingTreatment TEXT,
                sourcePolicy TEXT,
                loanAccountId TEXT,
                accountId TEXT,
                toAccountId TEXT,
                isCogsEstimated INTEGER,
                isCogsMissing INTEGER,
                legacyClassification TEXT,
                cashPaid REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE business_assets (
                id TEXT PRIMARY KEY,
                name TEXT,
                category TEXT,
                purchaseDate TEXT,
                purchasePrice REAL,
                usefulLifeYears INTEGER,
                currentValue REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE personal_assets (
                id TEXT PRIMARY KEY,
                name TEXT,
                category TEXT,
                value REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE loans (
                id TEXT PRIMARY KEY,
                type TEXT,
                classification TEXT,
                lenderOrBorrower TEXT,
                amount REAL,
                outstandingAmount REAL,
                repaymentDate TEXT,
                startDate TEXT,
                interestRate REAL,
                notes TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE household_expenses (
                id INTEGER PRIMARY KEY DEFAULT 1,
                monthlyHouseholdExpense REAL,
                schoolFee REAL,
                medical REAL,
                food REAL,
                utilityBills REAL,
                personalDrawings REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE other_income (
                id INTEGER PRIMARY KEY DEFAULT 1,
                rentalIncome REAL,
                bankProfit REAL,
                agriculturalIncome REAL,
                capitalGain REAL,
                freelanceIncome REAL,
                otherIncome REAL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE tax_record (
                id INTEGER PRIMARY KEY DEFAULT 1,
                adjustableTax REAL,
                advanceTax REAL,
                psidPayments REAL,
                psidChallanNumber TEXT,
                bankWithholding REAL,
                utilityWithholding REAL,
                mobileWithholding REAL,
                vehicleTax REAL,
                propertyTax REAL,
                otherTaxCertificates REAL,
                selectedTaxYear TEXT,
                openingOwnerEquityJson TEXT,
                openingBusinessLoanBalanceJson TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try { db.execSQL("ALTER TABLE transactions ADD COLUMN cashPaid REAL") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE deleted_transactions ADD COLUMN cashPaid REAL") } catch (_: Exception) {}
    }

    fun saveAppStateAtomic(state: AppState): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            clearAllTables(db)

            // 1. Profile
            val pCv = ContentValues().apply {
                put("id", state.profile.id)
                put("shopName", state.profile.shopName)
                put("shopNameUrdu", state.profile.shopNameUrdu)
                put("subtitle", state.profile.subtitle)
                put("ownerName", state.profile.ownerName)
                put("phone", state.profile.phone)
                put("email", state.profile.email)
                put("cnic", state.profile.cnic)
                put("ntn", state.profile.ntn)
                put("address", state.profile.address)
                put("storeMode", state.profile.storeMode.name)
                put("storeCategory", state.profile.storeCategory.name)
                put("currencySymbol", state.profile.currencySymbol)
                put("pinCode", state.profile.pinCode)
                put("passwordCode", state.profile.passwordCode)
                put("biometricsEnabled", if (state.profile.biometricsEnabled) 1 else 0)
                put("activeLanguage", state.profile.activeLanguage)
            }
            db.insert("profile", null, pCv)

            // 2. Inventory
            for (item in state.inventory) {
                val cv = ContentValues().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("category", item.category)
                    put("unit", item.unit)
                    put("purchasePrice", item.purchasePrice)
                    put("salePrice", item.salePrice)
                    put("quantity", item.quantity)
                    put("minStockAlert", item.minStockAlert)
                    put("barcode", item.barcode)
                    put("sku", item.sku)
                    put("openingStock", item.openingStock)
                    put("openingCostPrice", item.openingCostPrice)
                    put("purchasedQty", item.purchasedQty)
                    put("soldQty", item.soldQty)
                    put("damagedQty", item.damagedQty)
                    put("returnedQty", item.returnedQty)
                }
                db.insert("inventory", null, cv)
            }

            // 3. Customers
            for (c in state.customers) {
                val cv = ContentValues().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("totalCredit", c.totalCredit)
                    put("lastTransactionDate", c.lastTransactionDate)
                    put("notes", c.notes)
                    put("status", c.status)
                    put("openingBalance", c.openingBalance)
                }
                db.insert("customers", null, cv)
            }

            // 4. Suppliers
            for (s in state.suppliers) {
                val cv = ContentValues().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("phone", s.phone)
                    put("totalPayable", s.totalPayable)
                    put("dueDate", s.dueDate)
                    put("notes", s.notes)
                    put("status", s.status)
                    put("openingBalance", s.openingBalance)
                }
                db.insert("suppliers", null, cv)
            }

            // 5. Bank Accounts
            for (acc in state.bankAccounts) {
                val cv = ContentValues().apply {
                    put("id", acc.id)
                    put("accountTitle", acc.accountTitle)
                    put("bankName", acc.bankName)
                    put("accountNumber", acc.accountNumber)
                    put("type", acc.type)
                    put("balance", acc.balance)
                    put("openingBalance", acc.openingBalance)
                }
                db.insert("bank_accounts", null, cv)
            }

            // 6. Transactions
            for (tx in state.transactions) {
                insertTransactionRow(db, "transactions", tx)
            }

            // 7. Deleted Transactions
            for (tx in state.deletedTransactions) {
                insertTransactionRow(db, "deleted_transactions", tx)
            }

            // 8. Business Assets
            for (asset in state.businessAssets) {
                val cv = ContentValues().apply {
                    put("id", asset.id)
                    put("name", asset.name)
                    put("category", asset.category)
                    put("purchaseDate", asset.purchaseDate)
                    put("purchasePrice", asset.purchasePrice)
                    put("usefulLifeYears", asset.usefulLifeYears)
                    put("currentValue", asset.currentValue)
                }
                db.insert("business_assets", null, cv)
            }

            // 9. Personal Assets
            for (asset in state.personalAssets) {
                val cv = ContentValues().apply {
                    put("id", asset.id)
                    put("name", asset.name)
                    put("category", asset.category)
                    put("value", asset.value)
                }
                db.insert("personal_assets", null, cv)
            }

            // 10. Loans
            for (loan in state.loans) {
                val cv = ContentValues().apply {
                    put("id", loan.id)
                    put("type", loan.type)
                    put("classification", loan.classification)
                    put("lenderOrBorrower", loan.lenderOrBorrower)
                    put("amount", loan.amount)
                    put("outstandingAmount", loan.outstandingAmount)
                    put("repaymentDate", loan.repaymentDate)
                    put("startDate", loan.startDate)
                    put("interestRate", loan.interestRate)
                    put("notes", loan.notes)
                }
                db.insert("loans", null, cv)
            }

            // 11. Household Expenses
            val hCv = ContentValues().apply {
                put("id", 1)
                put("monthlyHouseholdExpense", state.householdExpenses.monthlyHouseholdExpense)
                put("schoolFee", state.householdExpenses.schoolFee)
                put("medical", state.householdExpenses.medical)
                put("food", state.householdExpenses.food)
                put("utilityBills", state.householdExpenses.utilityBills)
                put("personalDrawings", state.householdExpenses.personalDrawings)
            }
            db.insert("household_expenses", null, hCv)

            // 12. Other Income
            val oCv = ContentValues().apply {
                put("id", 1)
                put("rentalIncome", state.otherIncome.rentalIncome)
                put("bankProfit", state.otherIncome.bankProfit)
                put("agriculturalIncome", state.otherIncome.agriculturalIncome)
                put("capitalGain", state.otherIncome.capitalGain)
                put("freelanceIncome", state.otherIncome.freelanceIncome)
                put("otherIncome", state.otherIncome.otherIncome)
            }
            db.insert("other_income", null, oCv)

            // 13. Tax Record
            val tCv = ContentValues().apply {
                put("id", 1)
                put("adjustableTax", state.taxRecord.adjustableTax)
                put("advanceTax", state.taxRecord.advanceTax)
                put("psidPayments", state.taxRecord.psidPayments)
                put("psidChallanNumber", state.taxRecord.psidChallanNumber)
                put("bankWithholding", state.taxRecord.bankWithholding)
                put("utilityWithholding", state.taxRecord.utilityWithholding)
                put("mobileWithholding", state.taxRecord.mobileWithholding)
                put("vehicleTax", state.taxRecord.vehicleTax)
                put("propertyTax", state.taxRecord.propertyTax)
                put("otherTaxCertificates", state.taxRecord.otherTaxCertificates)
                put("selectedTaxYear", state.taxRecord.selectedTaxYear)
                put("openingOwnerEquityJson", gson.toJson(state.taxRecord.openingOwnerEquity))
                put("openingBusinessLoanBalanceJson", gson.toJson(state.taxRecord.openingBusinessLoanBalance))
            }
            db.insert("tax_record", null, tCv)

            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            db.endTransaction()
        }
    }

    private fun insertTransactionRow(db: SQLiteDatabase, table: String, tx: Transaction) {
        val cv = ContentValues().apply {
            put("id", tx.id)
            put("type", tx.type.name)
            put("amount", tx.amount)
            put("category", tx.category)
            put("paymentMethod", tx.paymentMethod.name)
            put("partyName", tx.partyName)
            put("partyId", tx.partyId)
            put("invoiceNo", tx.invoiceNo)
            put("date", tx.date)
            put("notes", tx.notes)
            put("receiptImageUrl", tx.receiptImageUrl)
            put("itemId", tx.itemId)
            put("qtyChange", tx.qtyChange)
            put("unitCost", tx.unitCost)
            put("totalValueChange", tx.totalValueChange)
            put("adjustmentReason", tx.adjustmentReason)
            put("balancingTreatment", tx.balancingTreatment)
            put("sourcePolicy", tx.sourcePolicy)
            put("loanAccountId", tx.loanAccountId)
            put("accountId", tx.accountId)
            put("toAccountId", tx.toAccountId)
            put("isCogsEstimated", if (tx.isCogsEstimated) 1 else 0)
            put("isCogsMissing", if (tx.isCogsMissing) 1 else 0)
            put("legacyClassification", tx.legacyClassification)
            put("cashPaid", tx.cashPaid)
        }
        db.insert(table, null, cv)
    }

    private fun clearAllTables(db: SQLiteDatabase) {
        db.delete("profile", null, null)
        db.delete("inventory", null, null)
        db.delete("customers", null, null)
        db.delete("suppliers", null, null)
        db.delete("bank_accounts", null, null)
        db.delete("transactions", null, null)
        db.delete("deleted_transactions", null, null)
        db.delete("business_assets", null, null)
        db.delete("personal_assets", null, null)
        db.delete("loans", null, null)
        db.delete("household_expenses", null, null)
        db.delete("other_income", null, null)
        db.delete("tax_record", null, null)
    }

    fun loadAppState(): AppState {
        val db = readableDatabase
        return try {
            val profile = loadProfile(db)
            val inventory = loadInventory(db)
            val customers = loadCustomers(db)
            val suppliers = loadSuppliers(db)
            val bankAccounts = loadBankAccounts(db)
            val transactions = loadTransactions(db, "transactions")
            val deletedTransactions = loadTransactions(db, "deleted_transactions")
            val businessAssets = loadBusinessAssets(db)
            val personalAssets = loadPersonalAssets(db)
            val loans = loadLoans(db)
            val householdExpenses = loadHouseholdExpenses(db)
            val otherIncome = loadOtherIncome(db)
            val taxRecord = loadTaxRecord(db)

            AppState(
                profile = profile,
                inventory = inventory,
                customers = customers,
                suppliers = suppliers,
                bankAccounts = bankAccounts,
                transactions = transactions,
                deletedTransactions = deletedTransactions,
                businessAssets = businessAssets,
                personalAssets = personalAssets,
                loans = loans,
                householdExpenses = householdExpenses,
                otherIncome = otherIncome,
                taxRecord = taxRecord,
                activeShopId = profile.id.ifBlank { "shop_01" },
                otherShops = listOf(ShopInfo(profile.id.ifBlank { "shop_01" }, profile.shopName.ifBlank { "My Shop" }))
            )
        } catch (e: Exception) {
            e.printStackTrace()
            FormatUtils.DEFAULT_APP_STATE
        }
    }

    private fun loadProfile(db: SQLiteDatabase): ShopProfile {
        val cursor = db.query("profile", null, null, null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                val modeStr = it.getString(it.getColumnIndexOrThrow("storeMode")) ?: "SIMPLE"
                val catStr = it.getString(it.getColumnIndexOrThrow("storeCategory")) ?: "KIRYANA_GENERAL"
                return ShopProfile(
                    id = it.getString(it.getColumnIndexOrThrow("id")) ?: "shop_01",
                    shopName = it.getString(it.getColumnIndexOrThrow("shopName")) ?: "My Shop",
                    shopNameUrdu = it.getString(it.getColumnIndexOrThrow("shopNameUrdu")),
                    subtitle = it.getString(it.getColumnIndexOrThrow("subtitle")) ?: "Dukan Management",
                    ownerName = it.getString(it.getColumnIndexOrThrow("ownerName")) ?: "",
                    phone = it.getString(it.getColumnIndexOrThrow("phone")) ?: "",
                    email = it.getString(it.getColumnIndexOrThrow("email")),
                    cnic = it.getString(it.getColumnIndexOrThrow("cnic")) ?: "",
                    ntn = it.getString(it.getColumnIndexOrThrow("ntn")) ?: "",
                    address = it.getString(it.getColumnIndexOrThrow("address")) ?: "",
                    storeMode = try { StoreMode.valueOf(modeStr) } catch (_: Exception) { StoreMode.SIMPLE },
                    storeCategory = try { StoreCategory.valueOf(catStr) } catch (_: Exception) { StoreCategory.KIRYANA_GENERAL },
                    currencySymbol = it.getString(it.getColumnIndexOrThrow("currencySymbol")) ?: "Rs.",
                    pinCode = it.getString(it.getColumnIndexOrThrow("pinCode")),
                    passwordCode = it.getString(it.getColumnIndexOrThrow("passwordCode")),
                    biometricsEnabled = it.getInt(it.getColumnIndexOrThrow("biometricsEnabled")) == 1,
                    activeLanguage = it.getString(it.getColumnIndexOrThrow("activeLanguage")) ?: "en"
                )
            }
        }
        return ShopProfile()
    }

    private fun loadInventory(db: SQLiteDatabase): List<InventoryItem> {
        val list = mutableListOf<InventoryItem>()
        val cursor = db.query("inventory", null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    InventoryItem(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "",
                        unit = it.getString(it.getColumnIndexOrThrow("unit")) ?: "pcs",
                        purchasePrice = it.getDouble(it.getColumnIndexOrThrow("purchasePrice")),
                        salePrice = it.getDouble(it.getColumnIndexOrThrow("salePrice")),
                        quantity = it.getDouble(it.getColumnIndexOrThrow("quantity")),
                        minStockAlert = it.getDouble(it.getColumnIndexOrThrow("minStockAlert")),
                        barcode = it.getString(it.getColumnIndexOrThrow("barcode")),
                        sku = it.getString(it.getColumnIndexOrThrow("sku")),
                        openingStock = it.getDouble(it.getColumnIndexOrThrow("openingStock")),
                        openingCostPrice = it.getDouble(it.getColumnIndexOrThrow("openingCostPrice")),
                        purchasedQty = it.getDouble(it.getColumnIndexOrThrow("purchasedQty")),
                        soldQty = it.getDouble(it.getColumnIndexOrThrow("soldQty")),
                        damagedQty = it.getDouble(it.getColumnIndexOrThrow("damagedQty")),
                        returnedQty = it.getDouble(it.getColumnIndexOrThrow("returnedQty"))
                    )
                )
            }
        }
        return list
    }

    private fun loadCustomers(db: SQLiteDatabase): List<CustomerParty> {
        val list = mutableListOf<CustomerParty>()
        val cursor = db.query("customers", null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CustomerParty(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        phone = it.getString(it.getColumnIndexOrThrow("phone")) ?: "",
                        totalCredit = it.getDouble(it.getColumnIndexOrThrow("totalCredit")),
                        lastTransactionDate = it.getString(it.getColumnIndexOrThrow("lastTransactionDate")) ?: "",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")),
                        status = it.getString(it.getColumnIndexOrThrow("status")) ?: "ACTIVE",
                        openingBalance = it.getDouble(it.getColumnIndexOrThrow("openingBalance"))
                    )
                )
            }
        }
        return list
    }

    private fun loadSuppliers(db: SQLiteDatabase): List<SupplierParty> {
        val list = mutableListOf<SupplierParty>()
        val cursor = db.query("suppliers", null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SupplierParty(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        phone = it.getString(it.getColumnIndexOrThrow("phone")) ?: "",
                        totalPayable = it.getDouble(it.getColumnIndexOrThrow("totalPayable")),
                        dueDate = it.getString(it.getColumnIndexOrThrow("dueDate")),
                        notes = it.getString(it.getColumnIndexOrThrow("notes")),
                        status = it.getString(it.getColumnIndexOrThrow("status")) ?: "ACTIVE",
                        openingBalance = it.getDouble(it.getColumnIndexOrThrow("openingBalance"))
                    )
                )
            }
        }
        return list
    }

    private fun loadBankAccounts(db: SQLiteDatabase): List<BankAccount> {
        val list = mutableListOf<BankAccount>()
        val cursor = db.query("bank_accounts", null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    BankAccount(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        accountTitle = it.getString(it.getColumnIndexOrThrow("accountTitle")) ?: "",
                        bankName = it.getString(it.getColumnIndexOrThrow("bankName")) ?: "",
                        accountNumber = it.getString(it.getColumnIndexOrThrow("accountNumber")) ?: "",
                        type = it.getString(it.getColumnIndexOrThrow("type")) ?: "CASH",
                        balance = it.getDouble(it.getColumnIndexOrThrow("balance")),
                        openingBalance = it.getDouble(it.getColumnIndexOrThrow("openingBalance"))
                    )
                )
            }
        }
        return list
    }

    private fun loadTransactions(db: SQLiteDatabase, table: String): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val cursor = db.query(table, null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                val typeStr = it.getString(it.getColumnIndexOrThrow("type")) ?: "SALE"
                val pmStr = it.getString(it.getColumnIndexOrThrow("paymentMethod")) ?: "CASH"
                list.add(
                    Transaction(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        type = try { TransactionType.valueOf(typeStr) } catch (_: Exception) { TransactionType.SALE },
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "",
                        paymentMethod = try { PaymentMethod.valueOf(pmStr) } catch (_: Exception) { PaymentMethod.CASH },
                        partyName = it.getString(it.getColumnIndexOrThrow("partyName")),
                        partyId = it.getString(it.getColumnIndexOrThrow("partyId")),
                        invoiceNo = it.getString(it.getColumnIndexOrThrow("invoiceNo")),
                        date = it.getString(it.getColumnIndexOrThrow("date")) ?: "",
                        notes = it.getString(it.getColumnIndexOrThrow("notes")),
                        receiptImageUrl = it.getString(it.getColumnIndexOrThrow("receiptImageUrl")),
                        itemId = it.getString(it.getColumnIndexOrThrow("itemId")),
                        qtyChange = if (it.isNull(it.getColumnIndexOrThrow("qtyChange"))) null else it.getDouble(it.getColumnIndexOrThrow("qtyChange")),
                        unitCost = if (it.isNull(it.getColumnIndexOrThrow("unitCost"))) null else it.getDouble(it.getColumnIndexOrThrow("unitCost")),
                        totalValueChange = if (it.isNull(it.getColumnIndexOrThrow("totalValueChange"))) null else it.getDouble(it.getColumnIndexOrThrow("totalValueChange")),
                        adjustmentReason = it.getString(it.getColumnIndexOrThrow("adjustmentReason")),
                        balancingTreatment = it.getString(it.getColumnIndexOrThrow("balancingTreatment")),
                        sourcePolicy = it.getString(it.getColumnIndexOrThrow("sourcePolicy")),
                        loanAccountId = it.getString(it.getColumnIndexOrThrow("loanAccountId")),
                        accountId = it.getString(it.getColumnIndexOrThrow("accountId")),
                        toAccountId = it.getString(it.getColumnIndexOrThrow("toAccountId")),
                        isCogsEstimated = it.getInt(it.getColumnIndexOrThrow("isCogsEstimated")) == 1,
                        isCogsMissing = it.getInt(it.getColumnIndexOrThrow("isCogsMissing")) == 1,
                        legacyClassification = it.getString(it.getColumnIndexOrThrow("legacyClassification")),
                        cashPaid = run {
                            val idx = it.getColumnIndex("cashPaid")
                            if (idx != -1 && !it.isNull(idx)) it.getDouble(idx) else null
                        }
                    )
                )
            }
        }
        return list
    }

    private fun loadBusinessAssets(db: SQLiteDatabase): List<BusinessAsset> {
        val list = mutableListOf<BusinessAsset>()
        val cursor = db.query("business_assets", null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    BusinessAsset(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "",
                        purchaseDate = it.getString(it.getColumnIndexOrThrow("purchaseDate")) ?: "",
                        purchasePrice = it.getDouble(it.getColumnIndexOrThrow("purchasePrice")),
                        usefulLifeYears = it.getInt(it.getColumnIndexOrThrow("usefulLifeYears")),
                        currentValue = it.getDouble(it.getColumnIndexOrThrow("currentValue"))
                    )
                )
            }
        }
        return list
    }

    private fun loadPersonalAssets(db: SQLiteDatabase): List<PersonalAsset> {
        val list = mutableListOf<PersonalAsset>()
        val cursor = db.query("personal_assets", null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    PersonalAsset(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        category = it.getString(it.getColumnIndexOrThrow("category")) ?: "",
                        value = it.getDouble(it.getColumnIndexOrThrow("value"))
                    )
                )
            }
        }
        return list
    }

    private fun loadLoans(db: SQLiteDatabase): List<LoanRecord> {
        val list = mutableListOf<LoanRecord>()
        val cursor = db.query("loans", null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LoanRecord(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        type = it.getString(it.getColumnIndexOrThrow("type")) ?: "PERSONAL_LOAN",
                        classification = it.getString(it.getColumnIndexOrThrow("classification")),
                        lenderOrBorrower = it.getString(it.getColumnIndexOrThrow("lenderOrBorrower")) ?: "",
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        outstandingAmount = it.getDouble(it.getColumnIndexOrThrow("outstandingAmount")),
                        repaymentDate = it.getString(it.getColumnIndexOrThrow("repaymentDate")),
                        startDate = it.getString(it.getColumnIndexOrThrow("startDate")),
                        interestRate = if (it.isNull(it.getColumnIndexOrThrow("interestRate"))) null else it.getDouble(it.getColumnIndexOrThrow("interestRate")),
                        notes = it.getString(it.getColumnIndexOrThrow("notes"))
                    )
                )
            }
        }
        return list
    }

    private fun loadHouseholdExpenses(db: SQLiteDatabase): HouseholdExpenses {
        val cursor = db.query("household_expenses", null, null, null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return HouseholdExpenses(
                    monthlyHouseholdExpense = it.getDouble(it.getColumnIndexOrThrow("monthlyHouseholdExpense")),
                    schoolFee = it.getDouble(it.getColumnIndexOrThrow("schoolFee")),
                    medical = it.getDouble(it.getColumnIndexOrThrow("medical")),
                    food = it.getDouble(it.getColumnIndexOrThrow("food")),
                    utilityBills = it.getDouble(it.getColumnIndexOrThrow("utilityBills")),
                    personalDrawings = it.getDouble(it.getColumnIndexOrThrow("personalDrawings"))
                )
            }
        }
        return HouseholdExpenses()
    }

    private fun loadOtherIncome(db: SQLiteDatabase): OtherIncome {
        val cursor = db.query("other_income", null, null, null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return OtherIncome(
                    rentalIncome = it.getDouble(it.getColumnIndexOrThrow("rentalIncome")),
                    bankProfit = it.getDouble(it.getColumnIndexOrThrow("bankProfit")),
                    agriculturalIncome = it.getDouble(it.getColumnIndexOrThrow("agriculturalIncome")),
                    capitalGain = it.getDouble(it.getColumnIndexOrThrow("capitalGain")),
                    freelanceIncome = it.getDouble(it.getColumnIndexOrThrow("freelanceIncome")),
                    otherIncome = it.getDouble(it.getColumnIndexOrThrow("otherIncome"))
                )
            }
        }
        return OtherIncome()
    }

    private fun loadTaxRecord(db: SQLiteDatabase): TaxRecord {
        val cursor = db.query("tax_record", null, null, null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                val equityJson = it.getString(it.getColumnIndexOrThrow("openingOwnerEquityJson"))
                val loanJson = it.getString(it.getColumnIndexOrThrow("openingBusinessLoanBalanceJson"))
                val equityMap = try { gson.fromJson(equityJson, Map::class.java) as Map<String, Double> } catch (_: Exception) { mapOf("2024" to 0.0, "2025" to 0.0, "2026" to 0.0) }
                val loanMap = try { gson.fromJson(loanJson, Map::class.java) as Map<String, Double> } catch (_: Exception) { mapOf("2024" to 0.0, "2025" to 0.0, "2026" to 0.0) }

                return TaxRecord(
                    adjustableTax = it.getDouble(it.getColumnIndexOrThrow("adjustableTax")),
                    advanceTax = it.getDouble(it.getColumnIndexOrThrow("advanceTax")),
                    psidPayments = it.getDouble(it.getColumnIndexOrThrow("psidPayments")),
                    psidChallanNumber = it.getString(it.getColumnIndexOrThrow("psidChallanNumber")) ?: "",
                    bankWithholding = it.getDouble(it.getColumnIndexOrThrow("bankWithholding")),
                    utilityWithholding = it.getDouble(it.getColumnIndexOrThrow("utilityWithholding")),
                    mobileWithholding = it.getDouble(it.getColumnIndexOrThrow("mobileWithholding")),
                    vehicleTax = it.getDouble(it.getColumnIndexOrThrow("vehicleTax")),
                    propertyTax = it.getDouble(it.getColumnIndexOrThrow("propertyTax")),
                    otherTaxCertificates = it.getDouble(it.getColumnIndexOrThrow("otherTaxCertificates")),
                    selectedTaxYear = it.getString(it.getColumnIndexOrThrow("selectedTaxYear")) ?: "2026",
                    openingOwnerEquity = equityMap,
                    openingBusinessLoanBalance = loanMap
                )
            }
        }
        return TaxRecord()
    }
}
