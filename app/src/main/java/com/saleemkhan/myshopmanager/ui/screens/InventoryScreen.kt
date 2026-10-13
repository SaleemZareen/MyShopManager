package com.saleemkhan.myshopmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.components.AddInventoryItemDialog
import com.saleemkhan.myshopmanager.ui.components.BarcodeScannerDialog
import com.saleemkhan.myshopmanager.ui.components.SaleInventoryItemDialog
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: AppState,
    isUrdu: Boolean,
    onAddItem: (InventoryItem) -> Unit,
    onUpdateQty: (String, Double) -> Unit,
    onDeleteItem: (String) -> Unit,
    onOpenScanner: (() -> Unit)? = null,
    onSaleItem: ((
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
    ) -> Unit)? = null
) {
    var search by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var prefilledBarcode by remember { mutableStateOf("") }
    var selectedSaleItem by remember { mutableStateOf<InventoryItem?>(null) }

    val totalValuation = remember(state.inventory) {
        state.inventory.sumOf { it.quantity * it.purchasePrice }
    }

    val lowStockCount = remember(state.inventory) {
        state.inventory.count { it.quantity <= it.minStockAlert }
    }

    val filteredItems = remember(state.inventory, search) {
        state.inventory.filter {
            search.isBlank() ||
                    it.name.contains(search, ignoreCase = true) ||
                    it.category.contains(search, ignoreCase = true) ||
                    (it.barcode?.contains(search, ignoreCase = true) == true) ||
                    (it.sku?.contains(search, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ==========================================
        // 1. PAGE HEADING: Stock & Inventory Management
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
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = "Stock & Inventory",
                        tint = DukanGreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Stock & Inventory Management",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Slate900
                    )
                    Text(
                        text = if (isUrdu) "دکان کے سامان کا اسٹاک، خریداری لاگت، بارکوڈ اور انوینٹری کنٹرول" else "Real-time stock valuation, barcode tracking and alerts",
                        fontSize = 10.5.sp,
                        color = Slate500
                    )
                }
            }
        }

        // KPI strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isUrdu) "کل مالیت اسٹاک" else "Stock Valuation",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF065F46)
                    )
                    Text(
                        text = FormatUtils.formatCurrency(totalValuation),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DukanGreenHover
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (lowStockCount > 0) Color(0xFFFEF3C7) else Color(0xFFF1F5F9)),
                border = BorderStroke(1.dp, if (lowStockCount > 0) Color(0xFFFCD34D) else Slate200)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isUrdu) "کم اسٹاک الرٹس" else "Low Stock Alerts",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lowStockCount > 0) AmberWarning else Slate600
                    )
                    Text(
                        text = "$lowStockCount ${if (isUrdu) "آئٹمز" else "items"}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lowStockCount > 0) AmberWarning else Slate700
                    )
                }
            }
        }

        // ==========================================
        // 2. SEARCH BAR WITH EMBEDDED BARCODE SCANNER & ADD BUTTON
        // Placeholder: "Search by item name, category or barcode.."
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = {
                    Text(
                        text = "Search by item name, category or barcode..",
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (search.isNotEmpty()) {
                            IconButton(
                                onClick = { search = "" },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        // Embedded Barcode Scanner inside Search Bar
                        IconButton(
                            onClick = {
                                if (onOpenScanner != null) {
                                    onOpenScanner()
                                } else {
                                    showBarcodeScanner = true
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .background(DukanGreenLight, RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Barcode",
                                tint = DukanGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = DukanGreenPrimary,
                    unfocusedBorderColor = Slate200
                ),
                modifier = Modifier.weight(1f)
            )

            // Add Item Button
            Button(
                onClick = {
                    prefilledBarcode = ""
                    showAddDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isUrdu) "نیا آئٹم" else "Add Item",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }

        // Items List
        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = "Empty",
                        tint = Slate300,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isUrdu) "کوئی اسٹاک آئٹم نہیں ملا" else "No inventory items found",
                        color = Slate500,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val isLowStock = item.quantity <= item.minStockAlert

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, if (isLowStock) Color(0xFFFCD34D) else Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(if (isLowStock) Color(0xFFFEF3C7) else DukanGreenLight, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isLowStock) Icons.Default.WarningAmber else Icons.Default.Inventory2,
                                        contentDescription = "Stock",
                                        tint = if (isLowStock) AmberWarning else DukanGreenHover,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.category} • Sale: ${FormatUtils.formatCurrency(item.salePrice)} • Buy: ${FormatUtils.formatCurrency(item.purchasePrice)}",
                                        fontSize = 10.sp,
                                        color = Slate500
                                    )
                                    // Prominent Barcode indicator under each item
                                    if (!item.barcode.isNullOrBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QrCodeScanner,
                                                contentDescription = "Barcode",
                                                tint = DukanGreenPrimary,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Barcode: ${item.barcode}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Slate600
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${item.quantity.toInt()} ${item.unit}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isLowStock) AmberWarning else DukanGreenPrimary
                                    )
                                    Text(
                                        text = if (isLowStock) (if (isUrdu) "کم اسٹاک!" else "Low Stock!") else (if (isUrdu) "دستیاب" else "In Stock"),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLowStock) RedDanger else Slate500
                                    )
                                }

                                // Sale / Less Button
                                Button(
                                    onClick = { selectedSaleItem = item },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DukanGreenLight,
                                        contentColor = DukanGreenHover
                                    ),
                                    border = BorderStroke(1.dp, GreenSuccessBorder),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.PointOfSale, contentDescription = "Sale", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isUrdu) "فروخت" else "Sale",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteItem(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = Slate400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Product Dialog (Fullscreen, Category & Unit LOVs, Camera Barcode Scanner)
        if (showAddDialog) {
            AddInventoryItemDialog(
                isOpen = showAddDialog,
                onClose = {
                    showAddDialog = false
                    prefilledBarcode = ""
                },
                onAddItem = onAddItem,
                isUrdu = isUrdu,
                initialBarcode = prefilledBarcode,
                inventory = state.inventory
            )
        }

        // Local Barcode Scanner Dialog on Inventory Screen
        if (showBarcodeScanner) {
            BarcodeScannerDialog(
                isOpen = showBarcodeScanner,
                onClose = { showBarcodeScanner = false },
                inventory = state.inventory,
                onItemScanned = { matched ->
                    search = matched.name
                    showBarcodeScanner = false
                },
                onQuickSale = { matched ->
                    showBarcodeScanner = false
                    selectedSaleItem = matched
                },
                onQuickPurchase = { matched ->
                    onUpdateQty(matched.id, matched.quantity + 1.0)
                    showBarcodeScanner = false
                },
                onAddNewItemWithBarcode = { unmatchedBarcode ->
                    showBarcodeScanner = false
                    prefilledBarcode = unmatchedBarcode
                    showAddDialog = true
                },
                isUrdu = isUrdu
            )
        }

        // Single Item Stock Sale Modal (SaleInventoryItemDialog)
        if (selectedSaleItem != null) {
            SaleInventoryItemDialog(
                item = selectedSaleItem,
                inventoryList = state.inventory,
                customers = state.customers,
                bankAccounts = state.bankAccounts,
                isOpen = selectedSaleItem != null,
                onClose = { selectedSaleItem = null },
                onConfirmSale = { itm, qty, unitPrice, discount, netAmount, customer, newName, newPhone, payMethod, accId, notes ->
                    onSaleItem?.invoke(itm, qty, unitPrice, discount, netAmount, customer, newName, newPhone, payMethod, accId, notes)
                    selectedSaleItem = null
                },
                isUrdu = isUrdu,
                shopName = state.profile.shopName
            )
        }
    }
}
