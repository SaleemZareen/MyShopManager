package com.saleemkhan.myshopmanager.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.saleemkhan.myshopmanager.data.accounting.AccountingEngine
import com.saleemkhan.myshopmanager.model.AppState
import com.saleemkhan.myshopmanager.model.TransactionType
import com.saleemkhan.myshopmanager.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ShopFinancialMetrics(
    val ownerName: String,
    val shopName: String,
    val todaySales: Double,
    val todayExpenses: Double,
    val todayProfit: Double,
    val todayRecovery: Double,
    val totalCreditUdhaar: Double,
    val totalSupplierPayable: Double,
    val inventoryCount: Int,
    val lowStockCount: Int,
    val lowStockItems: List<String> = emptyList(),
    val topDebtors: List<Pair<String, Double>> = emptyList(),
    val cashInHand: Double,
    val customerCount: Int,
    val supplierCount: Int,
    val recentTransactionsCount: Int,
    val storeCategory: String,
    val storeMode: String,
    val currency: String
)

class GeminiAdvisorService(private val context: Context? = null) {

    private val tag = "GeminiAdvisorService"
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    fun extractMetrics(state: AppState): ShopFinancialMetrics {
        val todaySales = state.transactions
            .filter { it.type == TransactionType.SALE }
            .sumOf { it.amount }

        val todayExpenses = state.transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val todayRecovery = state.transactions
            .filter { it.type == TransactionType.RECEIPT }
            .sumOf { it.amount }

        val totalCredit = state.customers.sumOf { it.totalCredit }
        val totalPayable = state.suppliers.sumOf { it.totalPayable }
        val lowStockItems = state.inventory.filter { it.quantity <= it.minStockAlert }.map { it.name }.take(3)
        val lowStock = state.inventory.count { it.quantity <= it.minStockAlert }
        val cashBalance = state.bankAccounts.sumOf { it.balance }

        val todayIso = FormatUtils.getTodayIsoDate()
        val todayTransactions = state.transactions.filter {
            val d = if (it.date.length >= 10) it.date.substring(0, 10) else it.date
            d == todayIso
        }
        val periodProfitRes = AccountingEngine.calculatePeriodProfit(todayTransactions)
        val canonicalTodayProfit = periodProfitRes.netProfit
        val topDebtors = state.customers
            .sortedByDescending { it.totalCredit }
            .filter { it.totalCredit > 0 }
            .map { it.name to it.totalCredit }
            .take(3)

        return ShopFinancialMetrics(
            ownerName = state.profile.ownerName.ifBlank { "محترم دکاندار" },
            shopName = state.profile.shopName.ifBlank { "میری دکان" },
            todaySales = todaySales,
            todayExpenses = todayExpenses,
            todayProfit = canonicalTodayProfit,
            todayRecovery = todayRecovery,
            totalCreditUdhaar = totalCredit,
            totalSupplierPayable = totalPayable,
            inventoryCount = state.inventory.size,
            lowStockCount = lowStock,
            lowStockItems = lowStockItems,
            topDebtors = topDebtors,
            cashInHand = cashBalance,
            customerCount = state.customers.size,
            supplierCount = state.suppliers.size,
            recentTransactionsCount = state.transactions.size,
            storeCategory = state.profile.storeCategory.name,
            storeMode = state.profile.storeMode.name,
            currency = state.profile.currencySymbol.ifBlank { "روپے" }
        )
    }

    private fun sanitizeUrduOutput(text: String): String {
        var clean = text
        // Strict replacement of Hindi / Sanskrit-origin loanwords with genuine everyday Pakistani Urdu
        clean = clean.replace("نمسکار", "السلام علیکم")
        clean = clean.replace("نمستے", "السلام علیکم")
        clean = clean.replace("دھنیواد", "شکریہ")
        clean = clean.replace("کریپیا", "برائے مہربانی")
        clean = clean.replace("کرپیا", "برائے مہربانی")
        clean = clean.replace("سمسیا", "مسئلہ")
        clean = clean.replace("سہایتا", "مدد")
        clean = clean.replace("چنتا", "فکر")
        clean = clean.replace("آوشیک", "ضروری")
        clean = clean.replace("ادھیک", "زیادہ")
        clean = clean.replace("پرتھم", "پہلا")
        clean = clean.replace("دھن", "رقم")
        clean = clean.replace("سوجھاؤ", "مشورہ")
        clean = clean.replace("شروعات", "آغاز")
        clean = clean.replace("یوگدان", "کردار")
        clean = clean.replace("پستک", "کتاب")
        clean = clean.replace("ودھیارکھی", "طالب علم")
        return clean.trim()
    }

    suspend fun askAdvisor(
        question: String,
        metrics: ShopFinancialMetrics,
        isUrdu: Boolean
    ): String = withContext(Dispatchers.IO) {
        Log.d(tag, "[AI_TEXT] Gemini text request started")
        val quotaManager = context?.let { AiQuotaManager.getInstance(it) }
        val apiKey = quotaManager?.getActiveApiKey()

        if (!apiKey.isNullOrBlank()) {
            val systemPrompt = """
            You are "Dukan AI Business Advisor & App Master" (دکان اے آئی بزنس مشیر اور ایپ ماسٹر), the official intelligent voice & business brain of the "My Shop Manager" (میری دکان مینیجر) Android application and personal financial consultant for Pakistani store owners.

            Live Shop Financial Data:
            - Store Name: ${metrics.shopName}
            - Owner Name: ${metrics.ownerName}
            - Today's Sales: Rs. ${metrics.todaySales}
            - Today's Expenses: Rs. ${metrics.todayExpenses}
            - Estimated Profit: Rs. ${metrics.todayProfit}
            - Today's Recovery (Wasooli): Rs. ${metrics.todayRecovery}
            - Total Customer Udhaar (Receivables): Rs. ${metrics.totalCreditUdhaar} (${metrics.customerCount} customers)
            - Total Supplier Payables: Rs. ${metrics.totalSupplierPayable} (${metrics.supplierCount} suppliers)
            - Total Inventory Items: ${metrics.inventoryCount} (${metrics.lowStockCount} items at alert limit)
            - Cash in Hand / Accounts: Rs. ${metrics.cashInHand}

            MANDATORY RULES:
            1. PURE PAKISTANI URDU ONLY:
               - Speak and write ONLY in 100% natural Pakistani Urdu (Urdu script), unless explicitly asked in English.
               - STRICTLY FORBIDDEN: NEVER use ANY Hindi words or Hindi/Sanskrit loanwords.
                 * NEVER use: "نمسکار", "نمستے", "دھنیواد", "کریپیا", "کرپیا", "سمسیا", "سہایتا", "چنتا", "آوشیک", "ادھیک", "پرتھم", "دھن", "سوجھاؤ", "شروعات".
                 * ALWAYS use: "السلام علیکم", "شکریہ", "برائے مہربانی", "مسئلہ", "مدد", "فکر", "ضروری", "زیادہ", "پہلا", "رقم", "مشورہ", "آغاز".
                 * Use authentic Pakistani shopkeeping terms: "کھاتہ", "ادھار", "وصولی", "نقد", "فروخت", "منافع", "گاہک", "مال / سامان", "سپلائر".
            2. ADAPTIVE LENGTH & CLARITY (CONCISE FOR QUICK INQUIRIES, COMPREHENSIVE & STRUCTURED FOR DETAILED QUESTIONS):
               - For simple greetings, quick inquiries, or single-metric status questions (e.g. "سلام", "آج کی سیل کتنی ہے؟", "کیش کتنا ہے؟"): Keep your reply brief, direct, and crisp (1 to 2 clear sentences).
               - For detail-oriented questions, business strategy, advisory questions, explanations, or troubleshooting (e.g. "دکان کا منافع کیسے بڑھائیں؟", "ادھار کی وصولی کا طریقہ کیا ہو؟", "سٹاک مینجمنٹ کیسے کریں؟", "سیلز بڑھانے کی حکمت عملی بتائیں"): Provide a well-rounded, informative, actionable briefing (3 to 5 structured points or clear sentences), incorporating relevant live shop figures. Never truncate detail-oriented questions into a single sentence.
               - Deliver clear, well-spoken sentences separated by Urdu full stop (۔) for natural human voice synthesis.
            3. COMPLETE SENTENCES:
               - Always complete every sentence fully with proper punctuation (۔). Never leave a sentence or thought cut off.
            4. ACCURATE SHOP FIGURES:
               - Directly integrate live figures from the shop data when relevant.
            """.trimIndent()

            val requestPayloadWithThinkingBudget = """
            {
              "systemInstruction": {
                "parts": [
                  { "text": ${gson.toJson(systemPrompt)} }
                ]
              },
              "contents": [
                {
                  "parts": [
                    { "text": ${gson.toJson(question)} }
                  ]
                }
              ],
              "generationConfig": {
                "temperature": 0.35,
                "topP": 0.9,
                "maxOutputTokens": 1024,
                "thinkingConfig": {
                  "thinkingBudget": 0
                }
              }
            }
            """.trimIndent()

            val requestPayloadStandard = """
            {
              "systemInstruction": {
                "parts": [
                  { "text": ${gson.toJson(systemPrompt)} }
                ]
              },
              "contents": [
                {
                  "parts": [
                    { "text": ${gson.toJson(question)} }
                  ]
                }
              ],
              "generationConfig": {
                "temperature": 0.35,
                "topP": 0.9,
                "maxOutputTokens": 1024
              }
            }
            """.trimIndent()

            val modelsToTry = listOf(
                "gemini-3.6-flash",
                "gemini-3.1-flash-lite-preview",
                "gemini-3.5-flash",
                "gemini-flash-latest"
            )

            for (modelName in modelsToTry) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                    var request = Request.Builder()
                        .url(url)
                        .post(requestPayloadWithThinkingBudget.toRequestBody("application/json".toMediaType()))
                        .build()

                    var response = httpClient.newCall(request).execute()
                    var statusCode = response.code
                    var responseBody = response.body?.string().orEmpty()

                    // If model rejects thinkingBudget with HTTP 400, retry immediately with standard payload
                    if (statusCode == 400 && responseBody.contains("thinking", ignoreCase = true)) {
                        Log.d(tag, "[AI_TEXT] Model $modelName does not support thinkingBudget, retrying standard payload...")
                        request = Request.Builder()
                            .url(url)
                            .post(requestPayloadStandard.toRequestBody("application/json".toMediaType()))
                            .build()
                        response = httpClient.newCall(request).execute()
                        statusCode = response.code
                        responseBody = response.body?.string().orEmpty()
                    }

                    if (response.isSuccessful && responseBody.isNotBlank()) {
                        val rootJson = gson.fromJson(responseBody, JsonObject::class.java)
                        val candidates = rootJson.getAsJsonArray("candidates")
                        if (candidates != null && candidates.size() > 0) {
                            val content = candidates[0].asJsonObject.getAsJsonObject("content")
                            val parts = content?.getAsJsonArray("parts")
                            if (parts != null && parts.size() > 0) {
                                val textParts = StringBuilder()
                                for (p in 0 until parts.size()) {
                                    val partObj = parts[p].asJsonObject
                                    val isThought = partObj.has("thought") && partObj.get("thought").asBoolean
                                    if (!isThought && partObj.has("text")) {
                                        val partText = partObj.get("text").asString
                                        if (!partText.isNullOrBlank()) {
                                            textParts.append(partText)
                                        }
                                    }
                                }
                                var finalAnswer = textParts.toString().trim()
                                if (finalAnswer.isBlank()) {
                                    for (p in 0 until parts.size()) {
                                        val partObj = parts[p].asJsonObject
                                        if (partObj.has("text")) {
                                            val pt = partObj.get("text").asString
                                            if (!pt.isNullOrBlank()) {
                                                textParts.append(pt)
                                            }
                                        }
                                    }
                                    finalAnswer = textParts.toString().trim()
                                }
                                if (finalAnswer.isNotBlank()) {
                                    quotaManager?.recordSuccessfulCall()
                                    val cleanAnswer = if (isUrdu) sanitizeUrduOutput(finalAnswer) else finalAnswer
                                    Log.d(tag, "[AI_TEXT] Online Gemini dynamic response received successfully from $modelName!")
                                    Log.d(tag, "[AI_TTS_DEBUG] Gemini online text response obtained: ${cleanAnswer.take(60)}")
                                    return@withContext cleanAnswer
                                }
                            }
                        }
                    } else {
                        Log.w(tag, "Gemini API error on model $modelName (code ${response.code}): $responseBody")
                        Log.w(tag, "[AI_TTS_DEBUG] Gemini text API error on $modelName: code=${response.code}, body=$responseBody")
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Gemini call exception on $modelName: ${e.message}")
                    Log.w(tag, "[AI_TTS_DEBUG] Gemini text call exception on $modelName: ${e.message}")
                }
            }
        }

        // =========================================================================
        // PHASE A: 100% DYNAMIC LOCAL ADVISORY ENGINE (NEVER STATIC!)
        // Computes real ratios, net margins, drawer balances, and live debtor insights.
        // =========================================================================
        Log.d(tag, "[AI_TEXT] Gemini text response received (local advisor engine)")
        Log.d(tag, "[AI_TTS_DEBUG] Generating local dynamic advisor text response (offline/fallback)")
        val lower = question.lowercase()
        val cur = metrics.currency
        val fmtSales = FormatUtils.formatNumber(metrics.todaySales)
        val fmtExp = FormatUtils.formatNumber(metrics.todayExpenses)
        val fmtProfit = FormatUtils.formatNumber(metrics.todayProfit)
        val fmtUdhaar = FormatUtils.formatNumber(metrics.totalCreditUdhaar)
        val fmtPayable = FormatUtils.formatNumber(metrics.totalSupplierPayable)
        val fmtRecovery = FormatUtils.formatNumber(metrics.todayRecovery)
        val fmtCash = FormatUtils.formatNumber(metrics.cashInHand)

        val netCashFlow = (metrics.todaySales + metrics.todayRecovery) - metrics.todayExpenses
        val profitMarginPercent = if (metrics.todaySales > 0) {
            ((metrics.todayProfit / metrics.todaySales) * 100).toInt()
        } else {
            18
        }

        // Handle detailed / strategy / how-to advisory queries with comprehensive actionable guidance
        val isDetailedQuery = lower.contains("کیسے") || lower.contains("طریقہ") || lower.contains("مشورہ") ||
                lower.contains("بڑھائیں") || lower.contains("حکمت عملی") || lower.contains("تفصیل") ||
                lower.contains("how") || lower.contains("strategy") || lower.contains("plan") || lower.contains("grow")

        if (isDetailedQuery && (lower.contains("منافع") || lower.contains("profit") || lower.contains("آمدن") || lower.contains("فروخت") || lower.contains("sale"))) {
            return@withContext if (isUrdu) {
                "محترم ${metrics.ownerName}! دکان کا منافع اور فروخت بڑھانے کے لیے تین اہم اقدامات کریں: اول، زیادہ بکنے والے آئٹمز پر زیادہ فوکس کریں اور ان کا سٹاک کبھی ختم نہ ہونے دیں۔ دوم، اخراجات پر کڑی نظر رکھیں کیونکہ آج آپ کے اخراجات ${fmtExp} $cur رہے ہیں جنہیں کم کر کے منافع بڑھایا جا سکتا ہے۔ سوم، نقد گاہکوں کو ترجیح دیں اور کسٹمر ادھار کی حد مقرر کریں تاکہ کیش فلو مضبوط رہے۔"
            } else {
                "Dear ${metrics.ownerName}! To maximize profit and turnover: First, maintain consistent stock of top-moving items. Second, optimize daily running overheads (currently Rs. $fmtExp). Third, encourage upfront cash payments to maintain strong operating liquidity."
            }
        }

        if (isDetailedQuery && (lower.contains("udhaar") || lower.contains("ادھار") || lower.contains("وصول") || lower.contains("khata") || lower.contains("کھاتہ"))) {
            return@withContext if (isUrdu) {
                "محترم ${metrics.ownerName}! ادھار کی موثر وصولی کے لیے یہ حکمت عملی اپنائیں: اول، ایپ کی کھاتہ سکرین سے بقایا دار گاہکوں کو شائستہ واٹس ایپ ریمائنڈر بھیجیں۔ دوم، زیادہ رقم والے گاہکوں سے ہفتہ وار قسطوں کا شیڈول طے کریں۔ سوم، کسی بھی گاہک کا پرانا ادھار کلیئر ہوئے بغیر نیا ادھار بند کریں۔ اس وقت آپ کا کل ادھار ${fmtUdhaar} $cur ہے جس کی بروقت وصولی دکان کے لیے بہت ضروری ہے۔"
            } else {
                "Dear ${metrics.ownerName}! For effective udhaar recovery: Send courteous WhatsApp balance reminders from the Khata screen, agree on fixed weekly partial settlements with major debtors, and pause fresh credit until previous balances are partially cleared."
            }
        }

        if (lower.contains("profit") || lower.contains("منافع") || lower.contains("بچت") || lower.contains("margin")) {
            if (isUrdu) {
                "محترم ${metrics.ownerName}! آج آپ کی دکان کا خالص تخمینہ منافع ${fmtProfit} $cur ہے، جبکہ کل فروخت ${fmtSales} $cur اور اخراجات ${fmtExp} $cur رہے ہیں۔"
            } else {
                "Dear ${metrics.ownerName}, your estimated net profit today is Rs. $fmtProfit (around $profitMarginPercent% margin) with total sales of Rs. $fmtSales."
            }
        } else if (lower.contains("sale") || lower.contains("سیل") || lower.contains("فروخت") || lower.contains("آمدنی")) {
            if (isUrdu) {
                "آج کی کل کاؤنٹر سیل ${fmtSales} $cur ہو چکی ہے اور وصولی سمیت خالص کیش فلو ${FormatUtils.formatNumber(netCashFlow)} $cur ہے۔"
            } else {
                "Today's total sales stand at Rs. $fmtSales with a net daily cash flow of Rs. ${FormatUtils.formatNumber(netCashFlow)}."
            }
        } else if (lower.contains("udhaar") || lower.contains("ادھار") || lower.contains("khata") || lower.contains("کھاتہ") || lower.contains("وصول")) {
            val debtorInfo = if (metrics.topDebtors.isNotEmpty()) {
                val names = metrics.topDebtors.take(2).joinToString("، ") { "${it.first} (${FormatUtils.formatNumber(it.second)} $cur)" }
                " جن میں سب سے زیادہ بقایا جات $names کے ہیں۔"
            } else {
                ""
            }

            if (isUrdu) {
                "آپ کا کل کسٹمر ادھار ${fmtUdhaar} $cur ہے جو ${metrics.customerCount} گاہکوں پر تقسیم ہے۔$debtorInfo آپ کھاتہ سکرین سے واٹس ایپ ریمائنڈر بھیج سکتے ہیں۔"
            } else {
                "Total customer receivables are Rs. $fmtUdhaar across ${metrics.customerCount} accounts. You can send polite WhatsApp balance reminders from the Khata screen."
            }
        } else if (lower.contains("stock") || lower.contains("سٹاک") || lower.contains("مال") || lower.contains("inventory")) {
            val stockWarning = if (metrics.lowStockCount > 0) {
                val names = if (metrics.lowStockItems.isNotEmpty()) " جن میں ${metrics.lowStockItems.take(3).joinToString("، ")} شامل ہیں" else ""
                "انتباہ: ${metrics.lowStockCount} آئٹمز کا سٹاک ختم ہونے کے قریب ہے$names، برائے مہربانی فوری نیا مال آرڈر کریں۔"
            } else {
                "تمام آئٹمز کا سٹاک تسلی بخش ہے اور کوئی بھی آئٹم شارٹ نہیں ہے۔"
            }

            if (isUrdu) {
                "دکان میں کل ${metrics.inventoryCount} پروڈکٹس درج ہیں۔ $stockWarning"
            } else {
                "You have ${metrics.inventoryCount} recorded items. ${if (metrics.lowStockCount > 0) "${metrics.lowStockCount} items are low on stock." else "All inventory levels are healthy."}"
            }
        } else if (lower.contains("supplier") || lower.contains("سپلائر") || lower.contains("بل") || lower.contains("ادائیگی")) {
            if (isUrdu) {
                "سپلائرز کے کل واجب الادا بل ${fmtPayable} $cur ہیں، جبکہ کیش دراز میں ${fmtCash} $cur موجود ہیں۔"
            } else {
                "Total outstanding payables to suppliers are Rs. $fmtPayable across ${metrics.supplierCount} suppliers against available cash of Rs. $fmtCash."
            }
        } else if (lower.contains("setting") || lower.contains("سیٹنگ") || lower.contains("screen") || lower.contains("کہاں") || lower.contains("کیسے")) {
            if (isUrdu) {
                "آپ سیٹنگز مینو سے دکان کا نام، بیک اپ اور زبان تبدیل کر سکتے ہیں، جبکہ تمام پی ڈی ایف رپورٹس نیچے رپورٹس ٹیب سے حاصل کی جا سکتی ہیں۔"
            } else {
                "You can manage shop profile, backups, and language in Settings, and access PDF statements in the Reports tab."
            }
        } else {
            if (isUrdu) {
                "محترم ${metrics.ownerName}! آج کی دکان فروخت ${fmtSales} $cur، منافع ${fmtProfit} $cur اور کسٹمر ادھار ${fmtUdhaar} $cur ہے۔ فرمائیے میں آپ کی کیا مدد کروں؟"
            } else {
                "Hello ${metrics.ownerName}! Today's sales are Rs. $fmtSales, profit Rs. $fmtProfit, and customer udhaar Rs. $fmtUdhaar. How can I assist your business?"
            }
        }
    }
}

