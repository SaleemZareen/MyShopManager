package com.saleemkhan.myshopmanager.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.screens.ReportPeriod
import com.saleemkhan.myshopmanager.ui.screens.ReportType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class ExportFormat(val titleEn: String, val titleUr: String, val extension: String) {
    PDF("PDF Document (.pdf)", "پی ڈی ایف رپورٹ (.pdf)", "pdf"),
    EXCEL("Excel Spreadsheet (.csv)", "ایکسل شیٹ (.csv / ایکسل)", "csv")
}

object ReportExporter {

    private fun getReportsDir(context: Context): File {
        val dir = File(context.cacheDir, "reports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Print or "Save as PDF" using Android's native PrintManager and WebView.
     * This opens the native Android Print Preview where user can choose "Save as PDF"
     * or print to any printer.
     */
    fun printOrSavePdf(
        context: Context,
        reportType: ReportType,
        period: ReportPeriod,
        state: AppState,
        filteredTx: List<Transaction>,
        fbrSummary: FbrSummary,
        isUrdu: Boolean
    ) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, if (isUrdu) "پرنٹ سروس دستیاب نہیں ہے" else "Print service not available", Toast.LENGTH_SHORT).show()
                return
            }

            val htmlContent = generateHtmlReport(
                reportType = reportType,
                period = period,
                state = state,
                filteredTx = filteredTx,
                fbrSummary = fbrSummary,
                isUrdu = isUrdu
            )

            val jobName = "${reportType.name}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}"

            Handler(Looper.getMainLooper()).post {
                val webView = WebView(context)
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        try {
                            val printAdapter = webView.createPrintDocumentAdapter(jobName)
                            val printAttributes = PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(PrintAttributes.Resolution("dukan_id", "High Quality", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()

                            printManager.print(jobName, printAdapter, printAttributes)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Print error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "PDF error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates a physical .pdf file using PdfDocument and shares it.
     */
    fun shareGeneratedPdfFile(
        context: Context,
        reportType: ReportType,
        period: ReportPeriod,
        state: AppState,
        filteredTx: List<Transaction>,
        fbrSummary: FbrSummary,
        isUrdu: Boolean
    ) {
        try {
            val reportsDir = getReportsDir(context)
            val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val pdfFile = File(reportsDir, "${reportType.name}_$dateStamp.pdf")

            val document = PdfDocument()
            val pageWidth = 595 // Standard A4 points (8.27 x 11.69 inches at 72dpi)
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply { isAntiAlias = true }

            // Header Banner
            paint.color = AndroidColor.parseColor("#0F766E") // Teal / DukanGreenPrimary
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 70f, paint)

            paint.color = AndroidColor.WHITE
            paint.textSize = 20f
            paint.isFakeBoldText = true
            val shopTitle = state.profile.shopName.ifBlank { "Dukan Management" }
            canvas.drawText(shopTitle, 24f, 38f, paint)

            paint.textSize = 11f
            paint.isFakeBoldText = false
            val subtitle = "${if (isUrdu) reportType.titleUr else reportType.titleEn} | ${if (isUrdu) period.titleUr else period.titleEn}"
            canvas.drawText(subtitle, 24f, 56f, paint)

            // Date / Gen
            val genDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
            paint.textSize = 9f
            canvas.drawText("Generated: $genDate", pageWidth - 160f, 56f, paint)

            var yPos = 100f
            val leftMargin = 24f
            val rightMargin = pageWidth - 24f

            // Summary Info Box
            paint.color = AndroidColor.parseColor("#F1F5F9")
            canvas.drawRoundRect(leftMargin, yPos, rightMargin, yPos + 65f, 8f, 8f, paint)

            paint.color = AndroidColor.parseColor("#0F172A")
            paint.textSize = 12f
            paint.isFakeBoldText = true
            val summaryTitle = if (isUrdu) "رپورٹ خلاصہ (Executive Summary)" else "Report Summary"
            canvas.drawText(summaryTitle, leftMargin + 14f, yPos + 22f, paint)

            paint.textSize = 10f
            paint.isFakeBoldText = false
            paint.color = AndroidColor.parseColor("#475569")

            when (reportType) {
                ReportType.BALANCE_SHEET -> {
                    canvas.drawText("Total Assets: ${FormatUtils.formatCurrency(fbrSummary.totalAssets)}", leftMargin + 14f, yPos + 40f, paint)
                    canvas.drawText("Total Liabilities: ${FormatUtils.formatCurrency(fbrSummary.totalLiabilities)}", leftMargin + 200f, yPos + 40f, paint)
                    canvas.drawText("Net Worth: ${FormatUtils.formatCurrency(fbrSummary.netWorth)}", leftMargin + 14f, yPos + 55f, paint)
                }
                ReportType.SALES -> {
                    val sales = filteredTx.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
                    canvas.drawText("Total Sales: ${FormatUtils.formatCurrency(sales)}", leftMargin + 14f, yPos + 40f, paint)
                    canvas.drawText("Invoices Count: ${filteredTx.size}", leftMargin + 200f, yPos + 40f, paint)
                    canvas.drawText("Period: ${period.titleEn}", leftMargin + 14f, yPos + 55f, paint)
                }
                ReportType.NET_PROFIT -> {
                    val sales = filteredTx.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
                    val expenses = filteredTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    val net = sales - fbrSummary.rawCogs - expenses
                    canvas.drawText("Revenue: ${FormatUtils.formatCurrency(sales)}", leftMargin + 14f, yPos + 40f, paint)
                    canvas.drawText("Expenses: ${FormatUtils.formatCurrency(expenses)}", leftMargin + 200f, yPos + 40f, paint)
                    canvas.drawText("Net Profit: ${FormatUtils.formatCurrency(net)}", leftMargin + 14f, yPos + 55f, paint)
                }
                ReportType.CUSTOMER_LEDGER -> {
                    val totalCredit = state.customers.sumOf { it.totalCredit }
                    canvas.drawText("Total Receivables: ${FormatUtils.formatCurrency(totalCredit)}", leftMargin + 14f, yPos + 40f, paint)
                    canvas.drawText("Total Customers: ${state.customers.size}", leftMargin + 200f, yPos + 40f, paint)
                }
                ReportType.STOCK_INVENTORY -> {
                    val totalVal = state.inventory.sumOf { it.quantity * it.purchasePrice }
                    canvas.drawText("Total Stock Value: ${FormatUtils.formatCurrency(totalVal)}", leftMargin + 14f, yPos + 40f, paint)
                    canvas.drawText("Total Inventory Items: ${state.inventory.size}", leftMargin + 200f, yPos + 40f, paint)
                }
                else -> {
                    canvas.drawText("Report Type: ${reportType.titleEn}", leftMargin + 14f, yPos + 40f, paint)
                    canvas.drawText("Records Count: ${filteredTx.size}", leftMargin + 200f, yPos + 40f, paint)
                }
            }

            yPos += 85f

            // Table Header
            paint.color = AndroidColor.parseColor("#334155")
            canvas.drawRect(leftMargin, yPos, rightMargin, yPos + 24f, paint)
            paint.color = AndroidColor.WHITE
            paint.textSize = 9.5f
            paint.isFakeBoldText = true

            when (reportType) {
                ReportType.CUSTOMER_LEDGER -> {
                    canvas.drawText("Customer Name", leftMargin + 8f, yPos + 16f, paint)
                    canvas.drawText("Phone", leftMargin + 180f, yPos + 16f, paint)
                    canvas.drawText("Balance (Rs)", rightMargin - 90f, yPos + 16f, paint)
                }
                ReportType.SUPPLIER_LEDGER -> {
                    canvas.drawText("Supplier Name", leftMargin + 8f, yPos + 16f, paint)
                    canvas.drawText("Phone", leftMargin + 180f, yPos + 16f, paint)
                    canvas.drawText("Payable (Rs)", rightMargin - 90f, yPos + 16f, paint)
                }
                ReportType.STOCK_INVENTORY -> {
                    canvas.drawText("Item Name", leftMargin + 8f, yPos + 16f, paint)
                    canvas.drawText("Qty / Unit", leftMargin + 200f, yPos + 16f, paint)
                    canvas.drawText("Purchase", leftMargin + 300f, yPos + 16f, paint)
                    canvas.drawText("Valuation", rightMargin - 80f, yPos + 16f, paint)
                }
                else -> {
                    canvas.drawText("Date", leftMargin + 8f, yPos + 16f, paint)
                    canvas.drawText("Party / Details", leftMargin + 110f, yPos + 16f, paint)
                    canvas.drawText("Category", leftMargin + 280f, yPos + 16f, paint)
                    canvas.drawText("Amount (Rs)", rightMargin - 80f, yPos + 16f, paint)
                }
            }

            yPos += 24f
            paint.isFakeBoldText = false
            paint.textSize = 9f

            // Render Table Rows (up to available page height)
            var rowCount = 0
            val maxRowsOnPage = 28

            when (reportType) {
                ReportType.CUSTOMER_LEDGER -> {
                    for (c in state.customers) {
                        if (rowCount++ >= maxRowsOnPage) break
                        paint.color = if (rowCount % 2 == 0) AndroidColor.parseColor("#F8FAFC") else AndroidColor.WHITE
                        canvas.drawRect(leftMargin, yPos, rightMargin, yPos + 20f, paint)
                        paint.color = AndroidColor.parseColor("#1E293B")
                        canvas.drawText(c.name.take(28), leftMargin + 8f, yPos + 14f, paint)
                        canvas.drawText(c.phone.take(15), leftMargin + 180f, yPos + 14f, paint)
                        canvas.drawText(FormatUtils.formatCurrency(c.totalCredit), rightMargin - 90f, yPos + 14f, paint)
                        yPos += 20f
                    }
                }
                ReportType.SUPPLIER_LEDGER -> {
                    for (s in state.suppliers) {
                        if (rowCount++ >= maxRowsOnPage) break
                        paint.color = if (rowCount % 2 == 0) AndroidColor.parseColor("#F8FAFC") else AndroidColor.WHITE
                        canvas.drawRect(leftMargin, yPos, rightMargin, yPos + 20f, paint)
                        paint.color = AndroidColor.parseColor("#1E293B")
                        canvas.drawText(s.name.take(28), leftMargin + 8f, yPos + 14f, paint)
                        canvas.drawText(s.phone.take(15), leftMargin + 180f, yPos + 14f, paint)
                        canvas.drawText(FormatUtils.formatCurrency(s.totalPayable), rightMargin - 90f, yPos + 14f, paint)
                        yPos += 20f
                    }
                }
                ReportType.STOCK_INVENTORY -> {
                    for (i in state.inventory) {
                        if (rowCount++ >= maxRowsOnPage) break
                        paint.color = if (rowCount % 2 == 0) AndroidColor.parseColor("#F8FAFC") else AndroidColor.WHITE
                        canvas.drawRect(leftMargin, yPos, rightMargin, yPos + 20f, paint)
                        paint.color = AndroidColor.parseColor("#1E293B")
                        canvas.drawText(i.name.take(30), leftMargin + 8f, yPos + 14f, paint)
                        canvas.drawText("${i.quantity} ${i.unit}", leftMargin + 200f, yPos + 14f, paint)
                        canvas.drawText(FormatUtils.formatCurrency(i.purchasePrice), leftMargin + 300f, yPos + 14f, paint)
                        val valCost = i.quantity * i.purchasePrice
                        canvas.drawText(FormatUtils.formatCurrency(valCost), rightMargin - 80f, yPos + 14f, paint)
                        yPos += 20f
                    }
                }
                else -> {
                    for (tx in filteredTx) {
                        if (rowCount++ >= maxRowsOnPage) break
                        paint.color = if (rowCount % 2 == 0) AndroidColor.parseColor("#F8FAFC") else AndroidColor.WHITE
                        canvas.drawRect(leftMargin, yPos, rightMargin, yPos + 20f, paint)
                        paint.color = AndroidColor.parseColor("#1E293B")
                        canvas.drawText(FormatUtils.formatDate(tx.date), leftMargin + 8f, yPos + 14f, paint)
                        canvas.drawText((tx.partyName ?: tx.notes ?: "-").take(26), leftMargin + 110f, yPos + 14f, paint)
                        canvas.drawText(tx.category.take(18), leftMargin + 280f, yPos + 14f, paint)
                        canvas.drawText(FormatUtils.formatCurrency(tx.amount), rightMargin - 80f, yPos + 14f, paint)
                        yPos += 20f
                    }
                }
            }

            // Footer
            paint.color = AndroidColor.parseColor("#94A3B8")
            paint.textSize = 8f
            canvas.drawText("Dukan Manager Report - Confidential - Page 1 of 1", leftMargin, pageHeight - 16f, paint)

            document.finishPage(page)

            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            // Share PDF Intent
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${if (isUrdu) reportType.titleUr else reportType.titleEn} - $shopTitle")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, if (isUrdu) "پی ڈی ایف شیئر کریں" else "Share PDF Report"))
            Toast.makeText(context, if (isUrdu) "پی ڈی ایف فائل تیار ہو گئی ہے" else "PDF file generated", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create PDF file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates and exports an Excel/CSV spreadsheet with UTF-8 BOM.
     */
    fun exportExcelSpreadsheet(
        context: Context,
        reportType: ReportType,
        period: ReportPeriod,
        state: AppState,
        filteredTx: List<Transaction>,
        fbrSummary: FbrSummary,
        isUrdu: Boolean
    ) {
        try {
            val csvContent = generateDetailedCsvContent(
                reportType = reportType,
                period = period,
                state = state,
                filteredTx = filteredTx,
                fbrSummary = fbrSummary,
                isUrdu = isUrdu
            )

            val reportsDir = getReportsDir(context)
            val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val fileName = "${reportType.name}_report_$dateStamp.csv"
            val file = File(reportsDir, fileName)

            FileOutputStream(file).use { out ->
                out.write(csvContent.toByteArray(Charsets.UTF_8))
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${reportType.titleEn} ($fileName)")
                putExtra(Intent.EXTRA_TEXT, "${state.profile.shopName} - ${reportType.titleEn} (${period.titleEn})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, if (isUrdu) "ایکسل شیٹ ایکسپورٹ کریں" else "Export Excel Spreadsheet"))
            Toast.makeText(context, if (isUrdu) "ایکسل شیٹ تیار ہو گئی ہے" else "Excel spreadsheet generated", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Excel export error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Builds comprehensive CSV content with UTF-8 BOM for all 11 reports.
     */
    fun generateDetailedCsvContent(
        reportType: ReportType,
        period: ReportPeriod,
        state: AppState,
        filteredTx: List<Transaction>,
        fbrSummary: FbrSummary,
        isUrdu: Boolean
    ): String {
        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel and Google Sheets render Urdu & English perfectly
        sb.append('\uFEFF')

        val shopName = state.profile.shopName.ifBlank { "Dukan Management" }
        val dateNow = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val periodText = if (isUrdu) period.titleUr else period.titleEn
        val reportTitle = if (isUrdu) reportType.titleUr else reportType.titleEn

        // Header info
        sb.appendLine("\"$shopName\"")
        sb.appendLine("\"Report:\",\"$reportTitle\"")
        sb.appendLine("\"Period:\",\"$periodText\"")
        sb.appendLine("\"Generated On:\",\"$dateNow\"")
        sb.appendLine("")

        when (reportType) {
            ReportType.BALANCE_SHEET -> {
                sb.appendLine("\"Category\",\"Account / Head\",\"Type\",\"Amount (PKR)\"")
                sb.appendLine("\"ASSETS\",\"Cash in Hand / Galla\",\"Current Asset\",${fbrSummary.cashAndBank}")
                state.bankAccounts.forEach { b ->
                    sb.appendLine("\"ASSETS\",\"${b.bankName} - ${b.accountTitle}\",\"Bank/Wallet\",${b.balance}")
                }
                sb.appendLine("\"ASSETS\",\"Stock / Inventory Valuation\",\"Current Asset\",${fbrSummary.stockValue}")
                sb.appendLine("\"ASSETS\",\"Customer Udhaar Receivables\",\"Receivable\",${fbrSummary.totalReceivables}")
                state.businessAssets.forEach { a ->
                    sb.appendLine("\"ASSETS\",\"${a.name} (${a.category})\",\"Fixed Shop Asset\",${a.purchasePrice}")
                }
                state.personalAssets.forEach { p ->
                    sb.appendLine("\"ASSETS\",\"${p.name} (${p.category})\",\"Personal Asset\",${p.value}")
                }
                sb.appendLine("\"LIABILITIES\",\"Supplier Payables\",\"Payable\",${fbrSummary.totalPayables}")
                state.loans.forEach { l ->
                    sb.appendLine("\"LIABILITIES\",\"Loan: ${l.lenderName} (${l.type})\",\"Borrowing\",${l.outstandingAmount}")
                }
                sb.appendLine("")
                sb.appendLine("\"SUMMARY\",\"Total Assets\",,\"${fbrSummary.totalAssets}\"")
                sb.appendLine("\"SUMMARY\",\"Total Liabilities\",,\"${fbrSummary.totalLiabilities}\"")
                sb.appendLine("\"SUMMARY\",\"Net Capital / Wealth\",,\"${fbrSummary.netWorth}\"")
            }

            ReportType.IRIS_TAX -> {
                sb.appendLine("\"IRIS Code\",\"Section\",\"Description\",\"Amount (PKR)\"")
                val mappings = TaxEngine.getIrisMappings(fbrSummary, state)
                mappings.forEach { m ->
                    sb.appendLine("\"${m.code}\",\"${m.section}\",\"${m.titleEn} - ${m.description}\",${m.amount}")
                }
                val recon = TaxEngine.getWealthReconciliation(fbrSummary, state)
                sb.appendLine("")
                sb.appendLine("\"WEALTH RECONCILIATION\",\"Expected Net Wealth\",,${recon.expectedNetWealth}")
                sb.appendLine("\"WEALTH RECONCILIATION\",\"Actual Net Wealth\",,${recon.actualNetWealth}")
                sb.appendLine("\"WEALTH RECONCILIATION\",\"Unexplained Difference\",,${recon.unexplainedDifference}")
                sb.appendLine("\"WEALTH RECONCILIATION\",\"Balanced Status\",,\"${if (recon.isBalanced) "BALANCED" else "UNBALANCED"}\"")
            }

            ReportType.SALES -> {
                sb.appendLine("\"Invoice ID\",\"Date\",\"Customer / Party\",\"Payment Method\",\"Category\",\"Amount (PKR)\",\"Notes\"")
                val sales = filteredTx.filter { it.type == TransactionType.SALE }
                sales.forEach { tx ->
                    sb.appendLine("\"${tx.id}\",\"${tx.date}\",\"${tx.partyName ?: "Counter Sale"}\",\"${tx.paymentMethod.name}\",\"${tx.category}\",${tx.amount},\"${tx.notes ?: ""}\"")
                }
                sb.appendLine("")
                val totalSales = sales.sumOf { it.amount }
                sb.appendLine("\"TOTALS\",\"Total Invoices: ${sales.size}\",,,,\"$totalSales\",")
            }

            ReportType.PURCHASES -> {
                sb.appendLine("\"Bill ID\",\"Date\",\"Supplier / Vendor\",\"Payment Method\",\"Category\",\"Amount (PKR)\",\"Notes\"")
                val purchases = filteredTx.filter { it.type == TransactionType.PURCHASE }
                purchases.forEach { tx ->
                    sb.appendLine("\"${tx.id}\",\"${tx.date}\",\"${tx.partyName ?: "Direct Purchase"}\",\"${tx.paymentMethod.name}\",\"${tx.category}\",${tx.amount},\"${tx.notes ?: ""}\"")
                }
                sb.appendLine("")
                val totalPurchases = purchases.sumOf { it.amount }
                sb.appendLine("\"TOTALS\",\"Total Bills: ${purchases.size}\",,,,\"$totalPurchases\",")
            }

            ReportType.COGS -> {
                sb.appendLine("\"Metric\",\"Description\",\"Amount (PKR)\"")
                sb.appendLine("\"Opening Stock\",\"Inventory value at start\",${fbrSummary.openingStockValue}")
                sb.appendLine("\"Add: Net Purchases\",\"Inventory purchased during period\",${fbrSummary.purchases}")
                sb.appendLine("\"Add: Direct Expenses\",\"Transport & Carriage costs\",${fbrSummary.directExpenses}")
                sb.appendLine("\"Less: Closing Stock\",\"Inventory value on hand\",${fbrSummary.stockValue}")
                sb.appendLine("\"Cost of Goods Sold (COGS)\",\"Net direct product cost\",${fbrSummary.rawCogs}")
                val grossMargin = fbrSummary.sales - fbrSummary.rawCogs
                val marginPct = if (fbrSummary.sales > 0) (grossMargin / fbrSummary.sales) * 100 else 0.0
                sb.appendLine("\"Gross Profit\",\"Sales minus COGS\",$grossMargin")
                sb.appendLine("\"Gross Margin %\",\"Margin percentage\",$marginPct")
            }

            ReportType.NET_PROFIT -> {
                sb.appendLine("\"Section\",\"Line Item\",\"Amount (PKR)\"")
                val sales = filteredTx.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
                val expenses = filteredTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val gross = sales - fbrSummary.rawCogs
                val net = gross - expenses
                sb.appendLine("\"REVENUE\",\"Gross Sales Turnover\",$sales")
                sb.appendLine("\"COST\",\"Less: Cost of Goods Sold (COGS)\",${fbrSummary.rawCogs}")
                sb.appendLine("\"GROSS PROFIT\",\"Gross Trading Margin\",$gross")
                sb.appendLine("\"EXPENSES\",\"Less: Total Operating Expenses\",$expenses")
                sb.appendLine("\"NET PROFIT\",\"Net Business Earnings\",$net")
            }

            ReportType.EXPENSES -> {
                sb.appendLine("\"ID\",\"Date\",\"Expense Title / Party\",\"Category\",\"Payment Method\",\"Amount (PKR)\",\"Notes\"")
                val expenses = filteredTx.filter { it.type == TransactionType.EXPENSE }
                expenses.forEach { tx ->
                    sb.appendLine("\"${tx.id}\",\"${tx.date}\",\"${tx.partyName ?: "Expense"}\",\"${tx.category}\",\"${tx.paymentMethod.name}\",${tx.amount},\"${tx.notes ?: ""}\"")
                }
                sb.appendLine("")
                val totalExp = expenses.sumOf { it.amount }
                sb.appendLine("\"TOTALS\",\"Total Expenses: ${expenses.size}\",,,,\"$totalExp\",")
            }

            ReportType.CUSTOMER_LEDGER -> {
                sb.appendLine("\"Customer ID\",\"Customer Name\",\"Phone Number\",\"City / Notes\",\"Balance Udhaar (PKR)\",\"Last Transaction\",\"Status\"")
                state.customers.forEach { c ->
                    sb.appendLine("\"${c.id}\",\"${c.name}\",\"${c.phone}\",\"${c.notes ?: ""}\",${c.totalCredit},\"${c.lastTransactionDate}\",\"${c.status ?: "ACTIVE"}\"")
                }
                sb.appendLine("")
                val totalUdhaar = state.customers.sumOf { it.totalCredit }
                sb.appendLine("\"TOTALS\",\"Total Customers: ${state.customers.size}\",,,$totalUdhaar,,")
            }

            ReportType.SUPPLIER_LEDGER -> {
                sb.appendLine("\"Supplier ID\",\"Supplier Name\",\"Contact / Phone\",\"Due Date\",\"Payable Balance (PKR)\",\"Notes\"")
                state.suppliers.forEach { s ->
                    sb.appendLine("\"${s.id}\",\"${s.name}\",\"${s.phone}\",\"${s.dueDate ?: ""}\",${s.totalPayable},\"${s.notes ?: ""}\"")
                }
                sb.appendLine("")
                val totalPayable = state.suppliers.sumOf { it.totalPayable }
                sb.appendLine("\"TOTALS\",\"Total Suppliers: ${state.suppliers.size}\",,,$totalPayable,")
            }

            ReportType.STOCK_INVENTORY -> {
                sb.appendLine("\"ID\",\"Barcode\",\"Item Name\",\"Category\",\"Quantity\",\"Unit\",\"Cost Price (PKR)\",\"Retail Price (PKR)\",\"Total Cost Value\",\"Total Retail Value\",\"Margin (PKR)\"")
                state.inventory.forEach { item ->
                    val costVal = item.quantity * item.purchasePrice
                    val retVal = item.quantity * item.salePrice
                    val margin = retVal - costVal
                    sb.appendLine("\"${item.id}\",\"${item.barcode ?: ""}\",\"${item.name}\",\"${item.category}\",${item.quantity},\"${item.unit}\",${item.purchasePrice},${item.salePrice},$costVal,$retVal,$margin")
                }
                sb.appendLine("")
                val totalCostVal = state.inventory.sumOf { it.quantity * it.purchasePrice }
                val totalRetVal = state.inventory.sumOf { it.quantity * it.salePrice }
                sb.appendLine("\"TOTALS\",\"Total Items: ${state.inventory.size}\",,,,,,$totalCostVal,$totalRetVal,")
            }

            ReportType.CASH_FLOW -> {
                sb.appendLine("\"Transaction ID\",\"Date\",\"Flow Type\",\"Account / Source\",\"Party / Description\",\"Category\",\"Amount (PKR)\"")
                filteredTx.sortedBy { it.date }.forEach { tx ->
                    val flowType = when (tx.type) {
                        TransactionType.SALE -> "INFLOW (Sale)"
                        TransactionType.PURCHASE -> "OUTFLOW (Purchase)"
                        TransactionType.EXPENSE -> "OUTFLOW (Expense)"
                        else -> "TRANSFER"
                    }
                    sb.appendLine("\"${tx.id}\",\"${tx.date}\",\"$flowType\",\"${tx.paymentMethod.name}\",\"${tx.partyName ?: ""}\",\"${tx.category}\",${tx.amount}")
                }
                sb.appendLine("")
                val inflows = filteredTx.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
                val outflows = filteredTx.filter { it.type == TransactionType.PURCHASE || it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val netFlow = inflows - outflows
                sb.appendLine("\"SUMMARY\",\"Total Inflows\",,,,,$inflows")
                sb.appendLine("\"SUMMARY\",\"Total Outflows\",,,,,$outflows")
                sb.appendLine("\"SUMMARY\",\"Net Cash Flow\",,,,,$netFlow")
            }
        }

        return sb.toString()
    }

    /**
     * Generates a beautifully formatted HTML report ready for Print / PDF export.
     */
    fun generateHtmlReport(
        reportType: ReportType,
        period: ReportPeriod,
        state: AppState,
        filteredTx: List<Transaction>,
        fbrSummary: FbrSummary,
        isUrdu: Boolean
    ): String {
        val shopName = state.profile.shopName.ifBlank { "Dukan Management" }
        val dateNow = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US).format(Date())
        val periodStr = if (isUrdu) period.titleUr else period.titleEn
        val title = if (isUrdu) reportType.titleUr else reportType.titleEn

        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>$title</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        margin: 20px;
                        color: #1e293b;
                        background: #ffffff;
                    }
                    .header-box {
                        border-bottom: 2px solid #0f766e;
                        padding-bottom: 12px;
                        margin-bottom: 20px;
                        display: flex;
                        justify-content: space-between;
                        align-items: flex-end;
                    }
                    .shop-name {
                        font-size: 24px;
                        font-weight: bold;
                        color: #0f766e;
                        margin: 0;
                    }
                    .report-title {
                        font-size: 16px;
                        font-weight: 600;
                        color: #334155;
                        margin: 4px 0 0 0;
                    }
                    .meta-info {
                        text-align: right;
                        font-size: 11px;
                        color: #64748b;
                    }
                    .kpi-container {
                        display: flex;
                        gap: 12px;
                        margin-bottom: 20px;
                    }
                    .kpi-card {
                        flex: 1;
                        background: #f8fafc;
                        border: 1px solid #e2e8f0;
                        border-radius: 8px;
                        padding: 10px 14px;
                    }
                    .kpi-label {
                        font-size: 11px;
                        color: #64748b;
                        text-transform: uppercase;
                    }
                    .kpi-value {
                        font-size: 17px;
                        font-weight: bold;
                        color: #0f172a;
                        margin-top: 4px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 12px;
                        margin-bottom: 24px;
                    }
                    th {
                        background-color: #0f766e;
                        color: white;
                        text-align: left;
                        padding: 8px 10px;
                        font-weight: 600;
                    }
                    td {
                        padding: 7px 10px;
                        border-bottom: 1px solid #f1f5f9;
                    }
                    tr:nth-child(even) {
                        background-color: #f8fafc;
                    }
                    .total-row {
                        font-weight: bold;
                        background-color: #e2e8f0 !important;
                        border-top: 2px solid #cbd5e1;
                    }
                    .footer {
                        margin-top: 30px;
                        font-size: 10px;
                        color: #94a3b8;
                        border-top: 1px solid #e2e8f0;
                        padding-top: 8px;
                        display: flex;
                        justify-content: space-between;
                    }
                    @media print {
                        body { margin: 0; }
                    }
                </style>
            </head>
            <body>
                <div class="header-box">
                    <div>
                        <h1 class="shop-name">$shopName</h1>
                        <div class="report-title">$title</div>
                    </div>
                    <div class="meta-info">
                        <div><strong>Period:</strong> $periodStr</div>
                        <div><strong>Date:</strong> $dateNow</div>
                        <div><strong>Shop Phone:</strong> ${state.profile.phone.ifBlank { "N/A" }}</div>
                    </div>
                </div>
        """.trimIndent())

        // KPI Box
        when (reportType) {
            ReportType.BALANCE_SHEET -> {
                sb.append("""
                    <div class="kpi-container">
                        <div class="kpi-card">
                            <div class="kpi-label">${if (isUrdu) "کل اثاثے" else "Total Assets"}</div>
                            <div class="kpi-value">${FormatUtils.formatCurrency(fbrSummary.totalAssets)}</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">${if (isUrdu) "کل واجبات" else "Total Liabilities"}</div>
                            <div class="kpi-value">${FormatUtils.formatCurrency(fbrSummary.totalLiabilities)}</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">${if (isUrdu) "خالص مالیت" else "Net Worth"}</div>
                            <div class="kpi-value" style="color: #0f766e;">${FormatUtils.formatCurrency(fbrSummary.netWorth)}</div>
                        </div>
                    </div>
                """.trimIndent())
            }
            ReportType.SALES -> {
                val totalSales = filteredTx.filter { it.type == TransactionType.SALE }.sumOf { it.amount }
                sb.append("""
                    <div class="kpi-container">
                        <div class="kpi-card">
                            <div class="kpi-label">${if (isUrdu) "مجموعی فروخت" else "Total Sales"}</div>
                            <div class="kpi-value" style="color: #0f766e;">${FormatUtils.formatCurrency(totalSales)}</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">${if (isUrdu) "کل بلز" else "Total Invoices"}</div>
                            <div class="kpi-value">${filteredTx.size}</div>
                        </div>
                    </div>
                """.trimIndent())
            }
            ReportType.CUSTOMER_LEDGER -> {
                val totalUdhaar = state.customers.sumOf { it.totalCredit }
                sb.append("""
                    <div class="kpi-container">
                        <div class="kpi-card">
                            <div class="kpi-label">${if (isUrdu) "کل وصول طلب ادھار" else "Total Receivables"}</div>
                            <div class="kpi-value" style="color: #d97706;">${FormatUtils.formatCurrency(totalUdhaar)}</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">${if (isUrdu) "گاہکوں کی تعداد" else "Customers Count"}</div>
                            <div class="kpi-value">${state.customers.size}</div>
                        </div>
                    </div>
                """.trimIndent())
            }
            ReportType.STOCK_INVENTORY -> {
                val totalVal = state.inventory.sumOf { it.quantity * it.purchasePrice }
                sb.append("""
                    <div class="kpi-container">
                        <div class="kpi-card">
                            <div class="kpi-label">${if (isUrdu) "اسٹاک لاگت ویلیو" else "Total Stock Valuation"}</div>
                            <div class="kpi-value" style="color: #0284c7;">${FormatUtils.formatCurrency(totalVal)}</div>
                        </div>
                        <div class="kpi-card">
                            <div class="kpi-label">${if (isUrdu) "کل پراڈکٹس" else "Total Items"}</div>
                            <div class="kpi-value">${state.inventory.size}</div>
                        </div>
                    </div>
                """.trimIndent())
            }
            else -> {}
        }

        // Table Data
        sb.append("<table>")
        when (reportType) {
            ReportType.CUSTOMER_LEDGER -> {
                sb.append("<tr><th>#</th><th>Customer Name</th><th>Phone</th><th>Notes</th><th>Balance (PKR)</th><th>Status</th></tr>")
                state.customers.forEachIndexed { idx, c ->
                    sb.append("<tr><td>${idx + 1}</td><td>${c.name}</td><td>${c.phone}</td><td>${c.notes ?: "-"}</td><td style='font-weight: bold;'>${FormatUtils.formatCurrency(c.totalCredit)}</td><td>${c.status ?: "ACTIVE"}</td></tr>")
                }
                val total = state.customers.sumOf { it.totalCredit }
                sb.append("<tr class='total-row'><td colspan='4'>Total Udhaar Receivables</td><td>${FormatUtils.formatCurrency(total)}</td><td></td></tr>")
            }
            ReportType.SUPPLIER_LEDGER -> {
                sb.append("<tr><th>#</th><th>Supplier Name</th><th>Phone</th><th>Due Date</th><th>Total Payable (PKR)</th></tr>")
                state.suppliers.forEachIndexed { idx, s ->
                    sb.append("<tr><td>${idx + 1}</td><td>${s.name}</td><td>${s.phone}</td><td>${s.dueDate ?: "-"}</td><td style='font-weight: bold;'>${FormatUtils.formatCurrency(s.totalPayable)}</td></tr>")
                }
                val total = state.suppliers.sumOf { it.totalPayable }
                sb.append("<tr class='total-row'><td colspan='4'>Total Payables</td><td>${FormatUtils.formatCurrency(total)}</td></tr>")
            }
            ReportType.STOCK_INVENTORY -> {
                sb.append("<tr><th>#</th><th>Item Name</th><th>Category</th><th>Qty</th><th>Purchase (PKR)</th><th>Sale (PKR)</th><th>Total Valuation</th></tr>")
                state.inventory.forEachIndexed { idx, item ->
                    val valCost = item.quantity * item.purchasePrice
                    sb.append("<tr><td>${idx + 1}</td><td>${item.name}</td><td>${item.category}</td><td>${item.quantity} ${item.unit}</td><td>${FormatUtils.formatCurrency(item.purchasePrice)}</td><td>${FormatUtils.formatCurrency(item.salePrice)}</td><td style='font-weight: bold;'>${FormatUtils.formatCurrency(valCost)}</td></tr>")
                }
                val total = state.inventory.sumOf { it.quantity * it.purchasePrice }
                sb.append("<tr class='total-row'><td colspan='6'>Total Inventory Value</td><td>${FormatUtils.formatCurrency(total)}</td></tr>")
            }
            else -> {
                sb.append("<tr><th>Date</th><th>Party / Details</th><th>Category</th><th>Payment</th><th>Amount (PKR)</th></tr>")
                filteredTx.forEach { tx ->
                    sb.append("<tr><td>${FormatUtils.formatDate(tx.date)}</td><td>${tx.partyName ?: "-"}</td><td>${tx.category}</td><td>${tx.paymentMethod.name}</td><td style='font-weight: bold;'>${FormatUtils.formatCurrency(tx.amount)}</td></tr>")
                }
                val sum = filteredTx.sumOf { it.amount }
                sb.append("<tr class='total-row'><td colspan='4'>Grand Total</td><td>${FormatUtils.formatCurrency(sum)}</td></tr>")
            }
        }
        sb.append("</table>")

        // Footer
        sb.append("""
            <div class="footer">
                <div>Printed from Dukan Manager App</div>
                <div>System Verified Record</div>
            </div>
            </body>
            </html>
        """.trimIndent())

        return sb.toString()
    }
}
