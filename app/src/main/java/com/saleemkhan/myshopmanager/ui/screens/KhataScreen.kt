package com.saleemkhan.myshopmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhataScreen(
    state: AppState,
    isUrdu: Boolean,
    onAddCustomer: (CustomerParty) -> Unit,
    onAddSupplier: (SupplierParty) -> Unit,
    onRecordPayment: (TransactionType, String, Double) -> Unit,
    onToggleArchiveCustomer: ((CustomerParty) -> Unit)? = null,
    onToggleArchiveSupplier: ((SupplierParty) -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf("CUSTOMERS") }
    var search by remember { mutableStateOf("") }
    var showArchivedAccounts by remember { mutableStateOf(false) }

    // Dialog states for Add Customer and Add Supplier/Vendor
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }

    // Quick payment state
    var selectedPartyForPayment by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // name, isCustomer
    var paymentAmountInput by remember { mutableStateOf("") }

    val totalCustomerUdhaar = remember(state.customers) {
        state.customers.sumOf { it.totalCredit }
    }

    val totalSupplierPayable = remember(state.suppliers) {
        state.suppliers.sumOf { it.totalPayable }
    }

    val activeCustomerCount = remember(state.customers) {
        state.customers.count { it.status != "ARCHIVED" && it.status != "CLOSED" }
    }
    val archivedCustomerCount = remember(state.customers) {
        state.customers.count { it.status == "ARCHIVED" || it.status == "CLOSED" }
    }

    val activeSupplierCount = remember(state.suppliers) {
        state.suppliers.count { it.status != "ARCHIVED" && it.status != "CLOSED" }
    }
    val archivedSupplierCount = remember(state.suppliers) {
        state.suppliers.count { it.status == "ARCHIVED" || it.status == "CLOSED" }
    }

    val filteredCustomers = remember(state.customers, search, showArchivedAccounts) {
        state.customers.filter { cust ->
            val matchesSearch = search.isBlank() || cust.name.contains(search, ignoreCase = true) || cust.phone.contains(search)
            val isArchived = cust.status == "ARCHIVED" || cust.status == "CLOSED"
            val matchesArchive = showArchivedAccounts || !isArchived
            matchesSearch && matchesArchive
        }
    }

    val filteredSuppliers = remember(state.suppliers, search, showArchivedAccounts) {
        state.suppliers.filter { sup ->
            val matchesSearch = search.isBlank() || sup.name.contains(search, ignoreCase = true) || sup.phone.contains(search)
            val isArchived = sup.status == "ARCHIVED" || sup.status == "CLOSED"
            val matchesArchive = showArchivedAccounts || !isArchived
            matchesSearch && matchesArchive
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
        // 1. MAIN HEADING: Customer and Supplier Khata Book
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
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Khata Book",
                        tint = DukanGreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Customer and Supplier Khata Book",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Slate900
                    )
                    Text(
                        text = if (isUrdu) "کسٹمر اور سپلائر کھاتہ بک - بقایا جات اور ادائیگیاں" else "Manage customer credit, supplier debts and settlements",
                        fontSize = 10.5.sp,
                        color = Slate500
                    )
                }
            }
        }

        // ==========================================
        // TOP KPI CARDS
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = BorderStroke(1.dp, Color(0xFFFCD34D))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isUrdu) "کل کسٹمر ادھار (Receivables)" else "Customer Udhaar",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        text = FormatUtils.formatCurrency(totalCustomerUdhaar),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309)
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF)),
                border = BorderStroke(1.dp, Color(0xFFD8B4FE))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isUrdu) "واجب الادا سپلائر (Payables)" else "Supplier Payables",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B21A8)
                    )
                    Text(
                        text = FormatUtils.formatCurrency(totalSupplierPayable),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C3AED)
                    )
                }
            }
        }

        // ==========================================
        // CUSTOMERS VS SUPPLIERS TAB TOGGLE
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedTab = "CUSTOMERS" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "CUSTOMERS") DukanGreenPrimary else Color.White,
                    contentColor = if (selectedTab == "CUSTOMERS") Color.White else Slate700
                ),
                border = BorderStroke(1.dp, if (selectedTab == "CUSTOMERS") DukanGreenPrimary else Slate200),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (isUrdu) "کسٹمرز کھاتہ ($activeCustomerCount)" else "Customers ($activeCustomerCount)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
            }

            Button(
                onClick = { selectedTab = "SUPPLIERS" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "SUPPLIERS") Color(0xFF4F46E5) else Color.White,
                    contentColor = if (selectedTab == "SUPPLIERS") Color.White else Slate700
                ),
                border = BorderStroke(1.dp, if (selectedTab == "SUPPLIERS") Color(0xFF4F46E5) else Slate200),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (isUrdu) "سپلائرز کھاتہ ($activeSupplierCount)" else "Suppliers ($activeSupplierCount)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // ==========================================
        // SEARCH BOX
        // ==========================================
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = {
                Text(
                    text = if (selectedTab == "CUSTOMERS") {
                        if (isUrdu) "کسٹمر کا نام یا فون تلاش کریں..." else "Search customer by name or phone..."
                    } else {
                        if (isUrdu) "سپلائر کا نام یا فون تلاش کریں..." else "Search supplier by name or phone..."
                    },
                    fontSize = 11.5.sp
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Slate400) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = if (selectedTab == "CUSTOMERS") DukanGreenPrimary else Color(0xFF4F46E5),
                unfocusedBorderColor = Slate200
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // ==========================================
        // 3. TWO SEPARATE ADD BUTTONS: Add Customer & Add Supplier/Vendor
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Button 1: Add Customer
            Button(
                onClick = { showAddCustomerDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isUrdu) "+ نیا کسٹمر" else "+ Add Customer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }

            // Button 2: Add Supplier/Vendor
            Button(
                onClick = { showAddSupplierDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isUrdu) "+ نیا سپلائر / وینڈر" else "+ Add Supplier/Vendor",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }

        // ==========================================
        // 2. TOGGLE: Show Closed & Archived Accounts
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(if (showArchivedAccounts) Color(0xFFFEF3C7) else Slate100, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = if (showArchivedAccounts) Color(0xFFD97706) else Slate500,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isUrdu) "Show Closed & Archived Accounts: (بند کھاتے)" else "Show Closed & Archived Accounts:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate800
                        )
                        val totalArchived = if (selectedTab == "CUSTOMERS") archivedCustomerCount else archivedSupplierCount
                        Text(
                            text = if (isUrdu) "$totalArchived بند / محفوظ شدہ کھاتے" else "$totalArchived archived accounts",
                            fontSize = 9.5.sp,
                            color = Slate500
                        )
                    }
                }

                Switch(
                    checked = showArchivedAccounts,
                    onCheckedChange = { showArchivedAccounts = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DukanGreenPrimary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Slate300
                    )
                )
            }
        }

        // ==========================================
        // KHATA ACCOUNTS LIST
        // ==========================================
        if (selectedTab == "CUSTOMERS") {
            if (filteredCustomers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonOutline, contentDescription = null, tint = Slate400, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isUrdu) "کوئی کسٹمر کھاتہ موجود نہیں" else "No customers found",
                            color = Slate500,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredCustomers, key = { it.id }) { cust ->
                        val isArchived = cust.status == "ARCHIVED" || cust.status == "CLOSED"

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isArchived) Color(0xFFF8FAFC) else Color.White),
                            border = BorderStroke(1.dp, if (isArchived) Slate300 else Slate200),
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(if (isArchived) Slate200 else Color(0xFFFEF3C7), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cust.name.take(1),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isArchived) Slate600 else Color(0xFFB45309),
                                            fontSize = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = cust.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isArchived) Slate600 else Slate900
                                            )
                                            if (isArchived) {
                                                Surface(
                                                    color = Color(0xFFE2E8F0),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isUrdu) "بند کھاتہ" else "ARCHIVED",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Slate600,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(text = cust.phone.ifBlank { "No phone" }, fontSize = 10.sp, color = Slate500)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = FormatUtils.formatCurrency(cust.totalCredit),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (cust.totalCredit > 0) AmberWarning else DukanGreenPrimary
                                        )
                                        Text(
                                            text = if (cust.totalCredit > 0) (if (isUrdu) "بقایا ادھار" else "Due") else (if (isUrdu) "کلیئر" else "Clear"),
                                            fontSize = 9.sp,
                                            color = Slate500
                                        )
                                    }

                                    Button(
                                        onClick = { selectedPartyForPayment = Pair(cust.name, true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = DukanGreenLight, contentColor = DukanGreenHover),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(text = if (isUrdu) "وصول کریں" else "Collect", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Archive / Restore toggle button
                                    if (onToggleArchiveCustomer != null) {
                                        IconButton(
                                            onClick = { onToggleArchiveCustomer(cust) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                                contentDescription = if (isArchived) "Unarchive" else "Archive",
                                                tint = if (isArchived) DukanGreenPrimary else Slate400,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // SUPPLIERS LIST
            if (filteredSuppliers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Slate400, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isUrdu) "کوئی سپلائر کھاتہ موجود نہیں" else "No suppliers found",
                            color = Slate500,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredSuppliers, key = { it.id }) { sup ->
                        val isArchived = sup.status == "ARCHIVED" || sup.status == "CLOSED"

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isArchived) Color(0xFFF8FAFC) else Color.White),
                            border = BorderStroke(1.dp, if (isArchived) Slate300 else Slate200),
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(if (isArchived) Slate200 else Color(0xFFF3E8FF), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = sup.name.take(1),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isArchived) Slate600 else Color(0xFF7C3AED),
                                            fontSize = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = sup.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isArchived) Slate600 else Slate900
                                            )
                                            if (isArchived) {
                                                Surface(
                                                    color = Color(0xFFE2E8F0),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isUrdu) "بند کھاتہ" else "ARCHIVED",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Slate600,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(text = sup.phone.ifBlank { "No phone" }, fontSize = 10.sp, color = Slate500)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = FormatUtils.formatCurrency(sup.totalPayable),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (sup.totalPayable > 0) Color(0xFF7C3AED) else DukanGreenPrimary
                                        )
                                        Text(
                                            text = if (sup.totalPayable > 0) (if (isUrdu) "واجب الادا" else "Payable") else (if (isUrdu) "کلیئر" else "Clear"),
                                            fontSize = 9.sp,
                                            color = Slate500
                                        )
                                    }

                                    Button(
                                        onClick = { selectedPartyForPayment = Pair(sup.name, false) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3E8FF), contentColor = Color(0xFF7C3AED)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(text = if (isUrdu) "ادائیگی کریں" else "Pay", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Archive / Restore toggle button
                                    if (onToggleArchiveSupplier != null) {
                                        IconButton(
                                            onClick = { onToggleArchiveSupplier(sup) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                                contentDescription = if (isArchived) "Unarchive" else "Archive",
                                                tint = if (isArchived) Color(0xFF4F46E5) else Slate400,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // DIALOG 1: ADD CUSTOMER DIALOG
        // ==========================================
        if (showAddCustomerDialog) {
            var name by remember { mutableStateOf("") }
            var phone by remember { mutableStateOf("") }
            var initialBal by remember { mutableStateOf("") }
            var notes by remember { mutableStateOf("") }

            Dialog(onDismissRequest = { showAddCustomerDialog = false }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(DukanGreenLight, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DukanGreenPrimary, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = if (isUrdu) "نیا کسٹمر کھاتہ شامل کریں" else "Add New Customer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(if (isUrdu) "کسٹمر کا نام *" else "Customer Name *") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text(if (isUrdu) "موبائل فون نمبر" else "Phone Number") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = initialBal,
                            onValueChange = { initialBal = it },
                            label = { Text(if (isUrdu) "ابتدائی ادھار رقم (روپے)" else "Initial Udhaar Balance (Rs.)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(if (isUrdu) "پتہ یا ریمارکس (اختیاری)" else "Address / Notes (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddCustomerDialog = false },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(text = if (isUrdu) "منسوخ" else "Cancel")
                            }

                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        val bal = initialBal.toDoubleOrNull() ?: 0.0
                                        onAddCustomer(
                                            CustomerParty(
                                                id = "cust_${System.currentTimeMillis()}",
                                                name = name.trim(),
                                                phone = phone.trim(),
                                                totalCredit = bal,
                                                notes = notes.trim().ifBlank { null },
                                                status = "ACTIVE"
                                            )
                                        )
                                        showAddCustomerDialog = false
                                    }
                                },
                                enabled = name.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = DukanGreenPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(text = if (isUrdu) "محفوظ کریں" else "Save Customer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // DIALOG 2: ADD SUPPLIER / VENDOR DIALOG
        // ==========================================
        if (showAddSupplierDialog) {
            var name by remember { mutableStateOf("") }
            var phone by remember { mutableStateOf("") }
            var initialBal by remember { mutableStateOf("") }
            var notes by remember { mutableStateOf("") }

            Dialog(onDismissRequest = { showAddSupplierDialog = false }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFEEF2FF), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = if (isUrdu) "نیا سپلائر / وینڈر کھاتہ شامل کریں" else "Add New Supplier / Vendor",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(if (isUrdu) "سپلائر / کمپنی کا نام *" else "Supplier / Company Name *") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text(if (isUrdu) "فون نمبر / رابطہ" else "Contact Phone") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = initialBal,
                            onValueChange = { initialBal = it },
                            label = { Text(if (isUrdu) "ابتدائی واجب الادا بل (روپے)" else "Initial Payable Amount (Rs.)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(if (isUrdu) "سپلائی کیٹیگری / تفصیلات" else "Supply Category / Notes") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddSupplierDialog = false },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(text = if (isUrdu) "منسوخ" else "Cancel")
                            }

                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        val bal = initialBal.toDoubleOrNull() ?: 0.0
                                        onAddSupplier(
                                            SupplierParty(
                                                id = "sup_${System.currentTimeMillis()}",
                                                name = name.trim(),
                                                phone = phone.trim(),
                                                totalPayable = bal,
                                                notes = notes.trim().ifBlank { null },
                                                status = "ACTIVE"
                                            )
                                        )
                                        showAddSupplierDialog = false
                                    }
                                },
                                enabled = name.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text(text = if (isUrdu) "محفوظ کریں" else "Save Supplier", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // QUICK PAYMENT / COLLECTION DIALOG
        // ==========================================
        selectedPartyForPayment?.let { (partyName, isCustomer) ->
            Dialog(onDismissRequest = { selectedPartyForPayment = null }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isCustomer) (if (isUrdu) "ادھار وصولی: $partyName" else "Collect Udhaar from $partyName") else (if (isUrdu) "ادائیگی برائے: $partyName" else "Pay Supplier: $partyName"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Slate900
                        )

                        OutlinedTextField(
                            value = paymentAmountInput,
                            onValueChange = { paymentAmountInput = it },
                            label = { Text(if (isUrdu) "رقم (روپے)" else "Amount (Rs.)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectedPartyForPayment = null },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Text(text = if (isUrdu) "منسوخ" else "Cancel")
                            }

                            Button(
                                onClick = {
                                    val amt = paymentAmountInput.toDoubleOrNull() ?: 0.0
                                    if (amt > 0) {
                                        val txType = if (isCustomer) TransactionType.RECEIPT else TransactionType.PAYMENT
                                        onRecordPayment(txType, partyName, amt)
                                        selectedPartyForPayment = null
                                        paymentAmountInput = ""
                                    }
                                },
                                enabled = (paymentAmountInput.toDoubleOrNull() ?: 0.0) > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = if (isCustomer) DukanGreenPrimary else Color(0xFF7C3AED)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Text(text = if (isUrdu) "اینٹری محفوظ کریں" else "Confirm", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
