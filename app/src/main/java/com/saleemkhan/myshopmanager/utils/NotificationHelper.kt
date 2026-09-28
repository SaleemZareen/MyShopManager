package com.saleemkhan.myshopmanager.utils

import com.saleemkhan.myshopmanager.model.*
import java.text.SimpleDateFormat
import java.util.*

object NotificationHelper {

    fun generateNotifications(state: AppState): List<AppNotificationItem> {
        val list = mutableListOf<AppNotificationItem>()

        // 1. Low Stock Alerts
        state.inventory
            .filter { it.quantity <= it.minStockAlert }
            .forEach { item ->
                val unitText = if (item.unit.isNotBlank()) item.unit else "units"
                val unitTextUr = if (item.unit.isNotBlank()) item.unit else "یونٹ"
                list.add(
                    AppNotificationItem(
                        id = "low_stock_${item.id}",
                        textEn = "Low Stock Alert: \"${item.name}\" has only ${FormatUtils.formatNumber(item.quantity)} $unitText left!",
                        textUr = "کم اسٹاک الرٹ: \"${item.name}\" کے پاس صرف ${FormatUtils.formatNumber(item.quantity)} $unitTextUr باقی ہیں!",
                        emoji = "📦",
                        type = NotificationType.LOW_STOCK,
                        targetScreen = Screen.INVENTORY,
                        targetId = item.id
                    )
                )
            }

        // 2. High Udhaar Notices (Customer credit > 10,000)
        state.customers
            .filter { it.totalCredit > 10000 }
            .forEach { cust ->
                list.add(
                    AppNotificationItem(
                        id = "high_credit_${cust.id}",
                        textEn = "High Udhaar Notice: ${cust.name} owes Rs. ${FormatUtils.formatCurrency(cust.totalCredit)}",
                        textUr = "زیادہ بقایا ادھار: ${cust.name} کے ذمے Rs. ${FormatUtils.formatCurrency(cust.totalCredit)} ہیں",
                        emoji = "💰",
                        type = NotificationType.HIGH_CREDIT,
                        targetScreen = Screen.KHATA,
                        targetId = cust.id
                    )
                )
            }

        // 3. Supplier Due or Overdue
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        state.suppliers
            .filter { it.totalPayable > 0 && !it.dueDate.isNullOrBlank() }
            .forEach { sup ->
                val isOverdue = try {
                    val d = sdf.parse(sup.dueDate!!)
                    d != null && d.before(today)
                } catch (e: Exception) {
                    false
                }

                list.add(
                    AppNotificationItem(
                        id = "supplier_due_${sup.id}",
                        textEn = "${if (isOverdue) "Overdue" else "Due"} Supplier Payment: Rs. ${FormatUtils.formatCurrency(sup.totalPayable)} to ${sup.name}",
                        textUr = "${if (isOverdue) "تاخیر شدہ" else "واجب الادا"} سپلائر ادائیگی: Rs. ${FormatUtils.formatCurrency(sup.totalPayable)} برائے ${sup.name}",
                        emoji = "🚚",
                        type = NotificationType.SUPPLIER_DUE,
                        targetScreen = Screen.KHATA,
                        targetId = sup.id
                    )
                )
            }

        // 4. Loan Due or Overdue
        state.loans
            .filter { it.outstandingAmount > 0 && !it.repaymentDate.isNullOrBlank() }
            .forEach { loan ->
                val isOverdue = try {
                    val d = sdf.parse(loan.repaymentDate!!)
                    d != null && d.before(today)
                } catch (e: Exception) {
                    false
                }

                list.add(
                    AppNotificationItem(
                        id = "loan_due_${loan.id}",
                        textEn = "${if (isOverdue) "Overdue" else "Due"} Loan Repayment: Rs. ${FormatUtils.formatCurrency(loan.outstandingAmount)} (${loan.lenderOrBorrower})",
                        textUr = "${if (isOverdue) "تاخیر شدہ" else "قریب ترین"} قرض واپسی: Rs. ${FormatUtils.formatCurrency(loan.outstandingAmount)} (${loan.lenderOrBorrower})",
                        emoji = "🏦",
                        type = NotificationType.LOAN_DUE,
                        targetScreen = Screen.ASSETS_LOANS,
                        targetId = loan.id
                    )
                )
            }

        return list
    }

    val defaultSafeNotification = AppNotificationItem(
        id = "default_safe_alert",
        textEn = "Your shop is running perfectly! Low stock, pending udhaar, and loans are all clear.",
        textUr = "آپ کی دکان کا نظام بہترین چل رہا ہے! کم اسٹاک، بقایا ادھار اور قرضے سب کلیئر ہیں۔",
        emoji = "✨",
        type = NotificationType.LOW_STOCK,
        targetScreen = Screen.DASHBOARD
    )
}
