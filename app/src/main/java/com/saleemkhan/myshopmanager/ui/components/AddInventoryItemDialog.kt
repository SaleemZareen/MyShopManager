package com.saleemkhan.myshopmanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saleemkhan.myshopmanager.model.InventoryItem
import com.saleemkhan.myshopmanager.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryItemDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onAddItem: (InventoryItem) -> Unit,
    isUrdu: Boolean,
    initialBarcode: String = "",
    inventory: List<InventoryItem> = emptyList()
) {
    if (!isOpen) return

    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var costPrice by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("pcs") }
    var minStockAlert by remember { mutableStateOf("5") }
    var barcode by remember(initialBarcode) { mutableStateOf(initialBarcode) }

    var isCategoryMenuOpen by remember { mutableStateOf(false) }
    var isUnitMenuOpen by remember { mutableStateOf(false) }
    var isInnerScannerOpen by remember { mutableStateOf(false) }

    val defaultCategories = remember(isUrdu) {
        listOf(
            "General" to (if (isUrdu) "جنرل الائڈ (General)" else "General"),
            "Grocery" to (if (isUrdu) "گروسری / راشن (Grocery)" else "Grocery"),
            "Beverages" to (if (isUrdu) "مشروبات / کولڈڈرنکس (Beverages)" else "Beverages"),
            "Snacks & Sweets" to (if (isUrdu) "سنیکس و بسکٹ (Snacks & Sweets)" else "Snacks & Sweets"),
            "Dairy & Bakery" to (if (isUrdu) "ڈیری و بیکری (Dairy & Bakery)" else "Dairy & Bakery"),
            "Personal Care" to (if (isUrdu) "صابن و شیمپو (Personal Care)" else "Personal Care"),
            "Household Cleaning" to (if (isUrdu) "صفائی و ڈٹرجنٹ (Cleaning)" else "Household Cleaning"),
            "Spices & Pulses" to (if (isUrdu) "دالیں و مسالہ جات (Spices & Pulses)" else "Spices & Pulses"),
            "Frozen Foods" to (if (isUrdu) "فروزن فوڈز (Frozen Foods)" else "Frozen Foods"),
            "Stationery" to (if (isUrdu) "اسٹیشنری (Stationery)" else "Stationery"),
            "Cigarettes & Paan" to (if (isUrdu) "سگریٹ و پان (Cigarettes)" else "Cigarettes")
        )
    }

    val allCategoriesList = remember(inventory, defaultCategories) {
        val existingCustom = inventory.map { it.category }.filter { it.isNotBlank() }.distinct()
            .filter { custom -> defaultCategories.none { it.first.equals(custom, ignoreCase = true) } }
            .map { it to it }
        defaultCategories + existingCustom
    }

    val unitsList = remember(isUrdu) {
        listOf(
            "pcs" to (if (isUrdu) "عدد / پیس (pcs)" else "Piece (pcs)"),
            "kg" to (if (isUrdu) "کلوگرام (kg)" else "Kg"),
            "gm" to (if (isUrdu) "گرام (gm)" else "Gram"),
            "ltr" to (if (isUrdu) "لیٹر (ltr)" else "Litre"),
            "pkt" to (if (isUrdu) "پیکٹ (pkt)" else "Packet"),
            "box" to (if (isUrdu) "باکس / ڈبہ (box)" else "Box"),
            "ctn" to (if (isUrdu) "کارٹن (ctn)" else "Carton"),
            "bag" to (if (isUrdu) "بوری / بیگ (bag)" else "Bag"),
            "dz" to (if (isUrdu) "درجن (dz)" else "Dozen"),
            "m" to (if (isUrdu) "میٹر (m)" else "Meter"),
            "ft" to (if (isUrdu) "فٹ (ft)" else "Feet")
        )
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUrdu) "نیا اسٹاک آئٹم شامل کریں" else "Add New Inventory Item",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                    }
                }

                // Item Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isUrdu) "پروڈکٹ کا نام" else "Product / Item Name") },
                    placeholder = { Text("e.g. Basmati Rice 5kg") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Dropdown LOV & Barcode Scan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category Dropdown LOV
                    ExposedDropdownMenuBox(
                        expanded = isCategoryMenuOpen,
                        onExpandedChange = { isCategoryMenuOpen = !isCategoryMenuOpen },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = allCategoriesList.find { it.first.equals(category, ignoreCase = true) }?.second ?: category,
                            onValueChange = { category = it },
                            label = { Text(if (isUrdu) "کیٹگری" else "Category") },
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryMenuOpen) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = isCategoryMenuOpen,
                            onDismissRequest = { isCategoryMenuOpen = false }
                        ) {
                            allCategoriesList.forEach { (catKey, catLabel) ->
                                DropdownMenuItem(
                                    text = { Text(catLabel, fontSize = 13.sp, fontWeight = if (category == catKey) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        category = catKey
                                        isCategoryMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Barcode field with Camera Scanner icon
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text(if (isUrdu) "بارکوڈ" else "Barcode / SKU") },
                        placeholder = { Text("Scan / Type") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { isInnerScannerOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan Barcode",
                                    tint = DukanGreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f)
                    )
                }

                // Cost Price & Sale Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = costPrice,
                        onValueChange = { costPrice = it },
                        label = { Text(if (isUrdu) "خرید قیمت" else "Cost Price") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sellingPrice,
                        onValueChange = { sellingPrice = it },
                        label = { Text(if (isUrdu) "فروخت قیمت" else "Sale Price") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quantity & Unit Dropdown LOV
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text(if (isUrdu) "ابتدائی مقدار" else "Quantity") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    // Unit Dropdown LOV
                    ExposedDropdownMenuBox(
                        expanded = isUnitMenuOpen,
                        onExpandedChange = { isUnitMenuOpen = !isUnitMenuOpen },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = unitsList.find { it.first.equals(unit, ignoreCase = true) }?.second ?: unit,
                            onValueChange = { unit = it },
                            label = { Text(if (isUrdu) "یونٹ" else "Unit") },
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isUnitMenuOpen) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = isUnitMenuOpen,
                            onDismissRequest = { isUnitMenuOpen = false }
                        ) {
                            unitsList.forEach { (unitKey, unitLabel) ->
                                DropdownMenuItem(
                                    text = { Text(unitLabel, fontSize = 13.sp, fontWeight = if (unit == unitKey) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        unit = unitKey
                                        isUnitMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Min Stock Alert Level
                OutlinedTextField(
                    value = minStockAlert,
                    onValueChange = { minStockAlert = it },
                    label = { Text(if (isUrdu) "کم اسٹاک الرٹ کی حد" else "Min Stock Alert Level") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Add to Inventory Button
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val item = InventoryItem(
                                id = "item_${System.currentTimeMillis()}",
                                name = name,
                                category = category.ifBlank { "General" },
                                purchasePrice = costPrice.toDoubleOrNull() ?: 0.0,
                                salePrice = sellingPrice.toDoubleOrNull() ?: 0.0,
                                quantity = quantity.toDoubleOrNull() ?: 0.0,
                                unit = unit,
                                minStockAlert = minStockAlert.toDoubleOrNull() ?: 5.0,
                                barcode = barcode.ifBlank { null }
                            )
                            onAddItem(item)
                            onClose()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = if (isUrdu) "اسٹاک میں شامل کریں" else "Add to Inventory",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // Inner Barcode Scanner when tapping the camera icon
    if (isInnerScannerOpen) {
        BarcodeScannerDialog(
            isOpen = isInnerScannerOpen,
            onClose = { isInnerScannerOpen = false },
            inventory = inventory,
            onItemScanned = { scannedItem ->
                barcode = scannedItem.barcode ?: scannedItem.sku ?: ""
                if (name.isBlank()) name = scannedItem.name
                if (category.isBlank()) category = scannedItem.category
                if (costPrice.isBlank()) costPrice = scannedItem.purchasePrice.toString()
                if (sellingPrice.isBlank()) sellingPrice = scannedItem.salePrice.toString()
                isInnerScannerOpen = false
            },
            onManualCodeEntered = { manualCode ->
                barcode = manualCode
                isInnerScannerOpen = false
            },
            onAddNewItemWithBarcode = { code ->
                barcode = code
                isInnerScannerOpen = false
            },
            isUrdu = isUrdu
        )
    }
}
