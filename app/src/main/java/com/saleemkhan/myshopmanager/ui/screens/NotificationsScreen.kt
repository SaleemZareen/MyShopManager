package com.saleemkhan.myshopmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saleemkhan.myshopmanager.model.*
import com.saleemkhan.myshopmanager.ui.theme.*
import com.saleemkhan.myshopmanager.utils.NotificationHelper

@Composable
fun NotificationsScreen(
    state: AppState,
    isUrdu: Boolean,
    dismissedIds: Set<String>,
    onDismissNotification: (String) -> Unit,
    onClearAllNotifications: (List<String>) -> Unit,
    onRestoreHistory: () -> Unit,
    onBack: () -> Unit,
    onNavigateScreen: (Screen) -> Unit
) {
    // Generate all dynamic notifications
    val allNotifications = remember(state.inventory, state.customers, state.suppliers, state.loans) {
        NotificationHelper.generateNotifications(state)
    }

    val activeNotifications = remember(allNotifications, dismissedIds) {
        allNotifications.filter { it.id !in dismissedIds }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // 1. Back & Heading Row Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Slate700,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Bell",
                                    tint = Color(0xFF126A49),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isUrdu) "تمام اہم اطلاعات و الرٹس" else "Shop Alerts & Notifications",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Slate900
                                )
                            }
                            Text(
                                text = if (isUrdu) "اسٹاک، بقایا جات، سپلائر اور لون یاد دہانیاں" else "Live low stock warnings, Udhaar, and payments dues",
                                fontSize = 11.sp,
                                color = Slate500,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Action buttons: Clear All and Restore History
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (activeNotifications.isNotEmpty()) {
                            Button(
                                onClick = {
                                    onClearAllNotifications(activeNotifications.map { it.id })
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFF1F2),
                                    contentColor = Color(0xFFBE123C)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Clear All",
                                    tint = Color(0xFFBE123C),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUrdu) "سب پڑھ لیں" else "Clear All",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (dismissedIds.isNotEmpty()) {
                            IconButton(
                                onClick = onRestoreHistory,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Slate100, RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = if (isUrdu) "اطلاعات کی ہسٹری بحال کریں" else "Restore notification history",
                                    tint = Slate700,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Active Notifications List
        if (activeNotifications.isNotEmpty()) {
            items(activeNotifications, key = { it.id }) { notif ->
                val ribbonColor = when (notif.type) {
                    NotificationType.LOW_STOCK -> Color(0xFFF59E0B)
                    NotificationType.HIGH_CREDIT -> Color(0xFF10B981)
                    NotificationType.SUPPLIER_DUE -> Color(0xFFF43F5E)
                    NotificationType.LOAN_DUE -> Color(0xFF6366F1)
                }

                val cardBgColor = when (notif.type) {
                    NotificationType.LOW_STOCK -> Color(0xFFFFFBEB)
                    NotificationType.HIGH_CREDIT -> Color(0xFFECFDF5)
                    NotificationType.SUPPLIER_DUE -> Color(0xFFFFF1F2)
                    NotificationType.LOAN_DUE -> Color(0xFFEEF2FF)
                }

                val cardBorderColor = when (notif.type) {
                    NotificationType.LOW_STOCK -> Color(0xFFFDE68A)
                    NotificationType.HIGH_CREDIT -> Color(0xFFA7F3D0)
                    NotificationType.SUPPLIER_DUE -> Color(0xFFFECDD3)
                    NotificationType.LOAN_DUE -> Color(0xFFC7D2FE)
                }

                val badgeBgColor = when (notif.type) {
                    NotificationType.LOW_STOCK -> Color(0xFFFEF3C7)
                    NotificationType.HIGH_CREDIT -> Color(0xFFD1FAE5)
                    NotificationType.SUPPLIER_DUE -> Color(0xFFFFE4E6)
                    NotificationType.LOAN_DUE -> Color(0xFFE0E7FF)
                }

                val badgeTextColor = when (notif.type) {
                    NotificationType.LOW_STOCK -> Color(0xFF92400E)
                    NotificationType.HIGH_CREDIT -> Color(0xFF065F46)
                    NotificationType.SUPPLIER_DUE -> Color(0xFF9F1239)
                    NotificationType.LOAN_DUE -> Color(0xFF3730A3)
                }

                val badgeText = when (notif.type) {
                    NotificationType.LOW_STOCK -> if (isUrdu) "کم اسٹاک" else "Low Stock"
                    NotificationType.HIGH_CREDIT -> if (isUrdu) "زیادہ ادھار" else "High Udhaar"
                    NotificationType.SUPPLIER_DUE -> if (isUrdu) "سپلائر واجب الادا" else "Supplier Due"
                    NotificationType.LOAN_DUE -> if (isUrdu) "قرضہ الرٹ" else "Loan Due"
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, cardBorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onNavigateScreen(notif.targetScreen)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left colored ribbon
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .background(ribbonColor)
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Emoji Container
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(cardBgColor, RoundedCornerShape(12.dp))
                                    .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = notif.emoji, fontSize = 20.sp)
                            }

                            // Notification text & badge
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = badgeBgColor,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = badgeTextColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = if (isUrdu) notif.textUr else notif.textEn,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800,
                                    lineHeight = 17.sp
                                )
                            }

                            // Dismiss button
                            IconButton(
                                onClick = { onDismissNotification(notif.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 3. Empty State Card (All Caught Up!)
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFECFDF5), CircleShape)
                                .border(1.dp, Color(0xFFA7F3D0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = "All caught up",
                                tint = Color(0xFF126A49),
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isUrdu) "کوئی نئی نوٹیفکیشن نہیں ہے!" else "All Caught Up!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Slate800,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (isUrdu)
                                    "آپ کی دکان کا سارا کھاتہ اور اسٹاک بالکل ٹھیک اور محفوظ ہے۔"
                                else
                                    "Your shop inventory, Udhaars, and loan repayments are fully in order.",
                                fontSize = 12.sp,
                                color = Slate500,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = { onNavigateScreen(Screen.DASHBOARD) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126A49)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isUrdu) "واپس ہوم پیج" else "Back to Dashboard",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
