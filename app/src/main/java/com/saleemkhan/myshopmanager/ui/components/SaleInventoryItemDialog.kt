package com.saleemkhan.myshopmanager.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleInventoryItemDialog(
    item: InventoryItem?,
    inventoryList: List<InventoryItem> = emptyList(),
    customers: List<CustomerParty> = emptyList(),
    bankAccounts: List<BankAccount> = emptyList(),
    isOpen: Boolean,
    onClose: () -> Unit,
    onConfirmSale: (
        item: InventoryItem,
        qty: Double,
        unitPrice: Double,
        discount: Double,
        netAmount: Double,
        customer: CustomerParty?,
        newCustomerName: String?,
        newCustomerPhone: String?,
        paymentMethod: PaymentMethod,
        accountId: String?,
        notes: String?
    ) -> Unit,
    isUrdu: Boolean,
    shopName: String = ""
) {
    if (!isOpen || item == null) return

    val context = LocalContext.current

    var currentItem by remember(item) { mutableStateOf(item) }
    var sellQty by remember(item) { mutableStateOf("1") }
    var customPrice by remember(item) { mutableStateOf(item.salePrice.toInt().toString()) }

    // Discount mode: "FIXED" (Rs) vs "PERCENT" (%)
    var discountMode by remember { mutableStateOf("FIXED") }
    var discountInput by remember { mutableStateOf("0") }

    // Customer mode: "WALKIN", "KHATA", "NEW"
    var customerMode by remember { mutableStateOf("WALKIN") }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerPhone by remember { mutableStateOf("") }

    // Payment method & account
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var selectedAccountId by remember {
        mutableStateOf(bankAccounts.firstOrNull { it.type == "CASH" }?.id ?: bankAccounts.firstOrNull()?.id ?: "")
    }
    var saleNotes by remember { mutableStateOf("") }

    // Dropdown states
    var isItemDropdownOpen by remember { mutableStateOf(false) }
    var isCustomerDropdownOpen by remember { mutableStateOf(false) }

    // Calculations
    val activeItem = currentItem ?: item
    val parsedQty = sellQty.toDoubleOrNull() ?: 0.0
    val parsedPrice = customPrice.toDoubleOrNull() ?: activeItem.salePrice

    val rawSubtotal = parsedQty * parsedPrice

    val discountVal = discountInput.toDoubleOrNull() ?: 0.0
    val calculatedDiscount = if (discountMode == "PERCENT") {
        (rawSubtotal * (discountVal / 100.0)).coerceAtLeast(0.0)
    } else {
        discountVal.coerceAtLeast(0.0)
    }

    val netTotal = (rawSubtotal - calculatedDiscount).coerceAtLeast(0.0)

    // Unit validation check
    val unitLower = activeItem.unit.lowercase().trim()
    val isFractionalUnit = unitLower.contains("kg") || unitLower.contains("kilo") ||
            unitLower.contains("ltr") || unitLower.contains("litre") || unitLower.contains("liter") ||
            unitLower.contains("gm") || unitLower.contains("gram") || unitLower.contains("meter") ||
            unitLower.contains("metre") || unitLower.contains("foot") || unitLower.contains("feet")

    val hasUnitValidationError = !isFractionalUnit && parsedQty > 0 && (parsedQty % 1.0 != 0.0)
    val isStockExceeded = parsedQty > activeItem.quantity

    // Profit calculation
    val totalCost = activeItem.purchasePrice * parsedQty
    val grossProfit = netTotal - totalCost
    val marginPercent = if (netTotal > 0) (grossProfit / netTotal * 100.0) else 0.0

    val selectedCustomer = customers.find { it.id == selectedCustomerId }

    fun shareWhatsAppReceipt() {
        val custTitle = when (customerMode) {
            "WALKIN" -> if (isUrdu) "نقد کاؤنٹر گاہک" else "Walk-in Cash Customer"
            "KHATA" -> selectedCustomer?.name ?: if (isUrdu) "کھاتہ دار" else "Khata Customer"
            "NEW" -> newCustomerName.ifBlank { if (isUrdu) "نیا گاہک" else "New Customer" }
            else -> "Customer"
        }
        val sb = StringBuilder()
        sb.appendLine("🧾 *${shopName.ifBlank { "MyShop Manager" }}*")
        sb.appendLine("==========================")
        sb.appendLine("${if (isUrdu) "فروخت رسید" else "Sale Invoice"}")
        sb.appendLine("${if (isUrdu) "تاریخ" else "Date"}: ${FormatUtils.getTodayIsoDate()}")
        sb.appendLine("${if (isUrdu) "گاہک" else "Customer"}: $custTitle")
        sb.appendLine("--------------------------")
        sb.appendLine("${activeItem.name}")
        sb.appendLine("${parsedQty} ${activeItem.unit} x ${FormatUtils.formatCurrency(parsedPrice)} = ${FormatUtils.formatCurrency(rawSubtotal)}")
        if (calculatedDiscount > 0) {
            sb.appendLine("${if (isUrdu) "رعایت (ڈسکاؤنٹ)" else "Discount"}: -${FormatUtils.formatCurrency(calculatedDiscount)}")
        }
        sb.appendLine("--------------------------")
        val prevDue = if (customerMode == "KHATA" && selectedCustomer != null) selectedCustomer.totalCredit else 0.0
        val newBill = netTotal
        val received = if (paymentMethod == PaymentMethod.CREDIT) 0.0 else netTotal
        val totalDue = prevDue + newBill - received

        sb.appendLine("${if (isUrdu) "طریقہ ادائیگی" else "Payment"}: ${paymentMethod.name}")
        sb.appendLine("--------------------------")
        sb.appendLine("1️⃣ ${if (isUrdu) "سابقہ بقایا / ادھار" else "Previous Outstanding Balance"}: ${FormatUtils.formatCurrency(prevDue)}")
        sb.appendLine("2️⃣ ${if (isUrdu) "نیا بل" else "New Bill Amount"}: ${FormatUtils.formatCurrency(newBill)}")
        sb.appendLine("3️⃣ ${if (isUrdu) "وصول شدہ رقم" else "Amount Received"}: ${FormatUtils.formatCurrency(received)}")
        sb.appendLine("4️⃣ *${if (isUrdu) "کل بقایا ادھار" else "Total Outstanding Balance"}: ${FormatUtils.formatCurrency(totalDue)}*")
        sb.appendLine("==========================")
        sb.appendLine(if (isUrdu) "شکریہ! دوبارہ تشریف لائیں۔" else "Thank you for shopping with us!")

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        try {
            val shareIntent = Intent.createChooser(sendIntent, if (isUrdu) "رسید شیئر کریں" else "Share Receipt")
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Share menu not available", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. MODAL TOP HEADER
                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(DukanGreenLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PointOfSale,
                                    contentDescription = "Single Item Sale",
                                    tint = DukanGreenHover,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isUrdu) "براہِ راست آئٹم فروخت (Stock Sale)" else "Single Item Stock Sale",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = if (isUrdu) "انوینٹری سے براہِ راست کمی اور بلنگ" else "Direct inventory item reduction & billing",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Slate100, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Slate700,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 2. SCROLLABLE FORM BODY
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Item Card & Dropdown selector
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "منتخب پروڈکٹ کی تفصیل" else "Selected Product Details",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Slate800
                                )
                                Surface(
                                    color = if (activeItem.quantity <= activeItem.minStockAlert) Color(0xFFFEF2F2) else DukanGreenLight,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (activeItem.quantity <= activeItem.minStockAlert) Color(0xFFFECACA) else GreenSuccessBorder)
                                ) {
                                    Text(
                                        text = "${if (isUrdu) "موجودہ اسٹاک:" else "In Stock:"} ${activeItem.quantity} ${activeItem.unit}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeItem.quantity <= activeItem.minStockAlert) RedDanger else DukanGreenHover,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            if (inventoryList.isNotEmpty()) {
                                ExposedDropdownMenuBox(
                                    expanded = isItemDropdownOpen,
                                    onExpandedChange = { isItemDropdownOpen = !isItemDropdownOpen }
                                ) {
                                    OutlinedTextField(
                                        value = "${activeItem.name} (${activeItem.category})",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isItemDropdownOpen) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = isItemDropdownOpen,
                                        onDismissRequest = { isItemDropdownOpen = false }
                                    ) {
                                        inventoryList.forEach { inv ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(inv.name, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            "${inv.quantity.toInt()} ${inv.unit} • ${FormatUtils.formatCurrency(inv.salePrice)}",
                                                            fontSize = 11.sp,
                                                            color = Slate600
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    currentItem = inv
                                                    customPrice = inv.salePrice.toInt().toString()
                                                    isItemDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = activeItem.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Slate900
                                )
                            }

                            // Unit Price and Quantity inputs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = sellQty,
                                    onValueChange = { sellQty = it },
                                    label = { Text(if (isUrdu) "فروخت مقدار (${activeItem.unit})" else "Quantity (${activeItem.unit})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = if (isFractionalUnit) KeyboardType.Decimal else KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    isError = hasUnitValidationError || isStockExceeded
                                )

                                OutlinedTextField(
                                    value = customPrice,
                                    onValueChange = { customPrice = it },
                                    label = { Text(if (isUrdu) "فی یونٹ قیمت (Rs.)" else "Unit Price (Rs.)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Validation warnings
                            if (hasUnitValidationError) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = RedDanger, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isUrdu) "یونٹ '${activeItem.unit}' میں صرف پورے عدد (1, 2, 3...) الاؤ ہیں!" else "Unit '${activeItem.unit}' only allows whole integers!",
                                        color = RedDanger,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (isStockExceeded) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = "Warning", tint = AmberWarning, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isUrdu) "انتباہ: مانگی گئی مقدار موجودہ اسٹاک (${activeItem.quantity}) سے زیادہ ہے!" else "Alert: Qty exceeds available stock (${activeItem.quantity})!",
                                        color = AmberWarning,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Discount Mode & Value Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "رعایت / ڈسکاؤنٹ" else "Discount Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Slate800
                                )

                                // Fixed Rs vs % Switcher
                                Row(
                                    modifier = Modifier
                                        .background(Slate100, RoundedCornerShape(8.dp))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (discountMode == "FIXED") DukanGreenPrimary else Color.Transparent)
                                            .clickable { discountMode = "FIXED" }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isUrdu) "رقم (Rs.)" else "Fixed (Rs.)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (discountMode == "FIXED") Color.White else Slate700
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (discountMode == "PERCENT") DukanGreenPrimary else Color.Transparent)
                                            .clickable { discountMode = "PERCENT" }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isUrdu) "فیصد (%)" else "Percent (%)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (discountMode == "PERCENT") Color.White else Slate700
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = discountInput,
                                    onValueChange = { discountInput = it },
                                    label = { Text(if (discountMode == "PERCENT") (if (isUrdu) "رعایت (%)" else "Discount (%)") else (if (isUrdu) "رعایت (Rs.)" else "Discount (Rs.)")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                if (discountMode == "PERCENT" && calculatedDiscount > 0) {
                                    Surface(
                                        color = Slate100,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "= ${FormatUtils.formatCurrency(calculatedDiscount)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate700,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Customer Selection & On-The-Spot Registration
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isUrdu) "گاہک کا انتخاب (Customer)" else "Customer Options",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Slate800
                            )

                            // 3-way toggle: Walk-in, Existing Khata, + New Customer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate100, RoundedCornerShape(10.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf(
                                    Triple("WALKIN", if (isUrdu) "نقدی گاہک" else "Walk-in", Icons.Default.Person),
                                    Triple("KHATA", if (isUrdu) "کھاتہ دار" else "Khata", Icons.Default.AccountBalanceWallet),
                                    Triple("NEW", if (isUrdu) "+ نیا گاہک" else "+ New", Icons.Default.PersonAdd)
                                ).forEach { (mode, label, icon) ->
                                    val isSelected = customerMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) DukanGreenPrimary else Color.Transparent)
                                            .clickable { customerMode = mode }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (isSelected) Color.White else Slate700,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else Slate700
                                            )
                                        }
                                    }
                                }
                            }

                            when (customerMode) {
                                "WALKIN" -> {
                                    Surface(
                                        color = Slate50,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Slate500, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isUrdu) "عام نقدی کسٹمر کے لیے کوئی نام یا ریکارڈ رکھنا لازمی نہیں۔" else "Counter cash customer — no ledger registration required.",
                                                fontSize = 11.sp,
                                                color = Slate600
                                            )
                                        }
                                    }
                                }
                                "KHATA" -> {
                                    ExposedDropdownMenuBox(
                                        expanded = isCustomerDropdownOpen,
                                        onExpandedChange = { isCustomerDropdownOpen = !isCustomerDropdownOpen }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedCustomer?.name ?: (if (isUrdu) "کھاتہ دار منتخب کریں..." else "Select Khata Customer..."),
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCustomerDropdownOpen) },
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = isCustomerDropdownOpen,
                                            onDismissRequest = { isCustomerDropdownOpen = false }
                                        ) {
                                            customers.forEach { cust ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(cust.name, fontWeight = FontWeight.Bold)
                                                            if (cust.totalCredit > 0) {
                                                                Text(
                                                                    FormatUtils.formatCurrency(cust.totalCredit),
                                                                    color = RedDanger,
                                                                    fontSize = 11.sp
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedCustomerId = cust.id
                                                        isCustomerDropdownOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (selectedCustomer != null && selectedCustomer.totalCredit > 0) {
                                        Surface(
                                            color = Color(0xFFFEF2F2),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isUrdu) "⚠️ گاہک کا سابقہ ادھار:" else "⚠️ Previous Due Udhaar:",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RedDanger
                                                )
                                                Text(
                                                    text = FormatUtils.formatCurrency(selectedCustomer.totalCredit),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RedDanger
                                                )
                                            }
                                        }
                                    }
                                }
                                "NEW" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = newCustomerName,
                                            onValueChange = { newCustomerName = it },
                                            label = { Text(if (isUrdu) "نئے گاہک کا نام" else "New Customer Name") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = newCustomerPhone,
                                            onValueChange = { newCustomerPhone = it },
                                            label = { Text(if (isUrdu) "فون نمبر (اختیاری)" else "Phone Number (Optional)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Payment Method Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isUrdu) "طریقہ ادائیگی (Payment Method)" else "Payment Method",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Slate800
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    PaymentMethod.CASH to (if (isUrdu) "نقد (Cash)" else "Cash"),
                                    PaymentMethod.EASYPAISA to "EasyPaisa",
                                    PaymentMethod.JAZZCASH to "JazzCash",
                                    PaymentMethod.BANK to (if (isUrdu) "بینک" else "Bank"),
                                    PaymentMethod.CREDIT to (if (isUrdu) "ادھار کھاتہ" else "Credit (Udhaar)")
                                ).forEach { (method, label) ->
                                    val isSelected = paymentMethod == method
                                    val chipColor = if (method == PaymentMethod.CREDIT) Color(0xFFDC2626) else DukanGreenPrimary
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { paymentMethod = method },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = chipColor,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Instant Profit Margin Preview Card (CRITICAL)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (grossProfit >= 0) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (grossProfit >= 0) Color(0xFFA7F3D0) else Color(0xFFFECACA)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (grossProfit >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                        contentDescription = "Profit",
                                        tint = if (grossProfit >= 0) DukanGreenHover else RedDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (grossProfit >= 0) (if (isUrdu) "متوقع خام منافع (Gross Profit)" else "Estimated Gross Profit")
                                        else (if (isUrdu) "⚠️ متوقع نقصان (Loss)" else "⚠️ Estimated Loss"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (grossProfit >= 0) DukanGreenHover else RedDanger
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatCurrency(grossProfit),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (grossProfit >= 0) DukanGreenHover else RedDanger
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${if (isUrdu) "کل لاگت خرید:" else "Total Cost:"} ${FormatUtils.formatCurrency(totalCost)}",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                                Text(
                                    text = "${if (isUrdu) "منافع مارجن:" else "Profit Margin:"} ${String.format("%.1f", marginPercent)}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (grossProfit >= 0) DukanGreenHover else RedDanger
                                )
                            }
                        }
                    }

                    // Net Summary Calculation Box
                    Surface(
                        color = Slate100,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = if (isUrdu) "سب ٹوٹل (بل)" else "Subtotal", fontSize = 12.sp, color = Slate600)
                                Text(text = FormatUtils.formatCurrency(rawSubtotal), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }

                            if (calculatedDiscount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = if (isUrdu) "رعایت (ڈسکاؤنٹ)" else "Discount", fontSize = 12.sp, color = RedDanger)
                                    Text(text = "- ${FormatUtils.formatCurrency(calculatedDiscount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedDanger)
                                }
                            }

                            Divider(color = Slate300, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUrdu) "کل واجب الادا رقم:" else "Net Payable:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(netTotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = DukanGreenPrimary
                                )
                            }

                            if (paymentMethod == PaymentMethod.CREDIT && customerMode == "KHATA" && selectedCustomer != null) {
                                val newBalance = selectedCustomer.totalCredit + netTotal
                                Surface(
                                    color = Color(0xFFFEF2F2),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = if (isUrdu) "نیا کل کھاتہ بقایا:" else "New Total Khata Due:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedDanger)
                                        Text(text = FormatUtils.formatCurrency(newBalance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedDanger)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. BOTTOM ACTION BAR (Save & WhatsApp Receipt)
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // WhatsApp Receipt button
                        OutlinedButton(
                            onClick = { shareWhatsAppReceipt() },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF25D366)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF128C7E)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isUrdu) "رسید" else "Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Confirm & Save Sale Button
                        Button(
                            onClick = {
                                if (parsedQty <= 0) {
                                    Toast.makeText(context, if (isUrdu) "براہ کرم درست تعداد درج کریں" else "Please enter valid quantity", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (hasUnitValidationError) {
                                    Toast.makeText(context, if (isUrdu) "یونٹ کے مطابق پورا عدد درج کریں" else "Please enter whole integer for this unit", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (paymentMethod == PaymentMethod.CREDIT && customerMode == "WALKIN") {
                                    Toast.makeText(context, if (isUrdu) "ادھار کے لیے کسٹمر منتخب کرنا ضروری ہے" else "Khata customer required for credit sale", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                onConfirmSale(
                                    activeItem,
                                    parsedQty,
                                    parsedPrice,
                                    calculatedDiscount,
                                    netTotal,
                                    if (customerMode == "KHATA") selectedCustomer else null,
                                    if (customerMode == "NEW") newCustomerName.ifBlank { null } else null,
                                    if (customerMode == "NEW") newCustomerPhone.ifBlank { null } else null,
                                    paymentMethod,
                                    if (paymentMethod != PaymentMethod.CREDIT) selectedAccountId else null,
                                    "Sale ${activeItem.name} x $parsedQty @ $parsedPrice"
                                )
                                onClose()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Confirm", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isUrdu) "فروخت محفوظ کریں" else "Record Sale",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatCurrency(netTotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
