package com.saleemkhan.myshopmanager.utils

import com.saleemkhan.myshopmanager.model.*
import android.content.Context
import android.app.DatePickerDialog
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

object FormatUtils {
    val DEFAULT_APP_STATE = AppState(
        profile = ShopProfile(
            id = "shop_01",
            shopName = "My Shop",
            shopNameUrdu = "میری دکان",
            subtitle = "Dukan Management",
            ownerName = "",
            phone = "",
            cnic = "",
            ntn = "",
            address = "",
            storeMode = StoreMode.SIMPLE,
            storeCategory = StoreCategory.KIRYANA_GENERAL,
            currencySymbol = "Rs.",
            pinCode = null,
            biometricsEnabled = false,
            activeLanguage = "en"
        ),
        transactions = emptyList(),
        deletedTransactions = emptyList(),
        inventory = emptyList(),
        customers = emptyList(),
        suppliers = emptyList(),
        bankAccounts = listOf(
            BankAccount(
                id = "acc_cash",
                accountTitle = "Cash in Dukan Counter",
                bankName = "Cash Counter",
                accountNumber = "N/A",
                type = "CASH",
                balance = 0.0
            ),
            BankAccount(
                id = "acc_meezan",
                accountTitle = "Bank Account",
                bankName = "Bank",
                accountNumber = "",
                type = "BANK",
                balance = 0.0
            ),
            BankAccount(
                id = "acc_easypaisa",
                accountTitle = "EasyPaisa Wallet",
                bankName = "EasyPaisa",
                accountNumber = "",
                type = "WALLET",
                balance = 0.0
            ),
            BankAccount(
                id = "acc_jazzcash",
                accountTitle = "JazzCash Wallet",
                bankName = "JazzCash",
                accountNumber = "",
                type = "WALLET",
                balance = 0.0
            )
        ),
        businessAssets = emptyList(),
        personalAssets = emptyList(),
        loans = emptyList(),
        householdExpenses = HouseholdExpenses(),
        otherIncome = OtherIncome(),
        taxRecord = TaxRecord(),
        activeShopId = "shop_01",
        otherShops = listOf(ShopInfo("shop_01", "My Shop"))
    )

    private val currencyFormat = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    private val decimalCurrencyFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

    fun sanitizePakistaniCurrency(text: String): String {
        return text
            .replace("₹", "Rs. ")
            .replace("INR", "Rs.", ignoreCase = true)
            .replace("inr", "Rs.", ignoreCase = true)
    }

    fun formatCurrency(amount: Double, symbol: String = "Rs."): String {
        val cleanSymbol = if (symbol.contains("₹") || symbol.equals("INR", ignoreCase = true) || symbol.isBlank()) "Rs." else symbol
        return "$cleanSymbol ${currencyFormat.format(amount)}"
    }

    fun formatMoney(amount: Double, symbol: String = "Rs."): String {
        return formatCurrency(amount, symbol)
    }

    fun formatNumber(amount: Double): String {
        return decimalCurrencyFormat.format(amount)
    }

    fun sanitizeQuantity(qty: Double?): Double {
        if (qty == null || qty.isNaN() || qty.isInfinite()) return 0.0
        return Math.max(0.0, qty)
    }

    fun getTodayIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun formatDisplayDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        return try {
            if (dateStr.length >= 10) {
                dateStr.substring(0, 10)
            } else {
                dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "Today"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = if (dateStr.length >= 10) parser.parse(dateStr.substring(0, 10)) else Date()
            val outSdf = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
            outSdf.format(d ?: Date())
        } catch (e: Exception) {
            dateStr.take(10)
        }
    }

    fun formatFullDateTime(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val d = if (dateStr.contains("T")) parser.parse(dateStr.take(16)) else null
            if (d != null) {
                val outSdf = SimpleDateFormat("dd-MMM-yy hh:mm a", Locale.US)
                outSdf.format(d)
            } else {
                formatDate(dateStr)
            }
        } catch (e: Exception) {
            formatDate(dateStr)
        }
    }

    fun showDatePickerDialog(
        context: Context,
        currentDateStr: String,
        onDateSelected: (String) -> Unit
    ) {
        val cal = Calendar.getInstance()
        if (currentDateStr.isNotBlank()) {
            try {
                val parts = currentDateStr.trim().split("-")
                if (parts.size >= 3) {
                    val year = parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR)
                    val month = (parts[1].toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
                    val day = parts[2].take(2).toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
                    cal.set(year, month, day)
                }
            } catch (_: Exception) {}
        }

        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(formatted)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.show()
    }
}
