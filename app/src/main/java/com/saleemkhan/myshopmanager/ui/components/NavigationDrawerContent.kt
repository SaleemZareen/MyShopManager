package com.saleemkhan.myshopmanager.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saleemkhan.myshopmanager.model.Screen
import com.saleemkhan.myshopmanager.ui.theme.*

data class NavItem(
    val screen: Screen,
    val labelEn: String,
    val labelUr: String,
    val icon: ImageVector
)

val PRIMARY_NAV_ITEMS = listOf(
    NavItem(Screen.DASHBOARD, "Home", "ہوم", Icons.Default.Home),
    NavItem(Screen.TRANSACTIONS, "Recent Journal", "روزنامچہ (اینٹریز)", Icons.Default.Receipt),
    NavItem(Screen.KHATA, "Khata", "کھاتہ", Icons.Default.MenuBook),
    NavItem(Screen.INVENTORY, "Stock", "اسٹاک", Icons.Default.Inventory2),
    NavItem(Screen.ASSETS_LOANS, "Assets & Loans", "اثاثے و قرضے", Icons.Default.AccountBalance),
    NavItem(Screen.FBR_TAX, "Tax", "ٹیکس تیاری", Icons.Default.AssignmentTurnedIn),
    NavItem(Screen.REPORTS, "Reports", "رپورٹس", Icons.Default.Assessment),
    NavItem(Screen.ANALYTICS, "Analytics", "گراف و تجزیہ", Icons.Default.BarChart),
    NavItem(Screen.SETTINGS, "Settings", "سیٹنگز", Icons.Default.Settings)
)

/**
 * 1. PINNED MINI-RAIL (Ultra-thin 40-42px vertical icon bar fixed to screen edge)
 * Exactly mirrors React LeftSidebarNav pinned mode (lines 750-850)
 */
@Composable
fun PinnedSideNavRail(
    currentScreen: Screen,
    onSelectScreen: (Screen) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    isUrdu: Boolean
) {
    val isBackupSelected = currentScreen == Screen.BACKUP_SYNC

    // Pulsing green glow animation for Backup & Sync indicator
    val infiniteTransition = rememberInfiniteTransition(label = "SyncPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(42.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
        border = BorderStroke(
            1.dp,
            if (isUrdu) Slate200 else Slate200
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Pin / Unpin Button
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (isPinned) DukanGreenLight else Slate100,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = if (isUrdu) "ان پن کریں" else "Unpin Menu",
                    tint = if (isPinned) DukanGreenHover else Slate500,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Middle: Vertical Icon List for All Main Modules
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PRIMARY_NAV_ITEMS.forEach { item ->
                    val isSelected = currentScreen == item.screen
                    val label = if (isUrdu) item.labelUr else item.labelEn

                    Surface(
                        onClick = { onSelectScreen(item.screen) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) DukanGreenHover else Color.Transparent,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label,
                                tint = if (isSelected) Color.White else Slate600,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // Bottom: Dedicated Distinct Backup & Sync Button at the very end
            Box(
                modifier = Modifier.padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = { onSelectScreen(Screen.BACKUP_SYNC) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isBackupSelected) Color(0xFF0F766E) else Color(0xFFF0FDFA),
                    border = BorderStroke(
                        1.dp,
                        if (isBackupSelected) Color(0xFF14B8A6) else Color(0xFF99F6E4)
                    ),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = if (isUrdu) "بیک اپ اور سنک" else "Backup & Sync",
                            tint = if (isBackupSelected) Color.White else Color(0xFF0D9488),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Pulsing indicator dot on top-right of Backup & Sync button
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(pulseScale)
                        .align(Alignment.TopEnd)
                        .background(Color(0xFF10B981), CircleShape)
                )
            }
        }
    }
}

/**
 * 2. FULL OVERLAY DRAWER (Slide-out menu)
 * Exactly mirrors React LeftSidebarNav Full Overlay Drawer (lines 850-1045)
 */
@Composable
fun NavigationDrawerContent(
    currentScreen: Screen,
    onSelectScreen: (Screen) -> Unit,
    isUrdu: Boolean,
    onClose: () -> Unit,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null
) {
    val isBackupSelected = currentScreen == Screen.BACKUP_SYNC

    ModalDrawerSheet(
        drawerContainerColor = Color.White,
        modifier = Modifier.width(260.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Upper Section: Brand Header & Standard Navigation Menu
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header of Drawer with Brand Logo, Pin/Unpin, and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "MS" Brand Icon Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DukanGreenHover,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "MS",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "MyShop Manager",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Slate900,
                                maxLines = 1
                            )
                            Text(
                                text = if (isUrdu) "دکان مینیجر" else "Dukan Management",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DukanGreenPrimary
                            )
                        }
                    }

                    // Action Controls: Pin/Unpin & Close
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (onTogglePin != null) {
                            IconButton(
                                onClick = {
                                    onTogglePin()
                                    onClose()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = if (isUrdu) "اسکرین پر پن کریں" else "Pin Sidebar",
                                    tint = if (isPinned) DukanGreenHover else Slate500,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Slate600,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Slate100, modifier = Modifier.padding(vertical = 10.dp))

                // Navigation Items List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    PRIMARY_NAV_ITEMS.forEach { item ->
                        val isSelected = currentScreen == item.screen
                        val label = if (isUrdu) item.labelUr else item.labelEn

                        Surface(
                            color = if (isSelected) DukanGreenLight else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) BorderStroke(1.dp, GreenSuccessBorder) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectScreen(item.screen)
                                    onClose()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) DukanGreenHover else Slate100,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = label,
                                            tint = if (isSelected) Color.White else Slate600,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                AutoScrollText(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DukanGreenHover else Slate800,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Dedicated Distinct Option at the VERY END OF THE VERTICAL BAR
            // (Matching React lines 1010-1040 Backup & Sync card)
            Surface(
                onClick = {
                    onSelectScreen(Screen.BACKUP_SYNC)
                    onClose()
                },
                shape = RoundedCornerShape(14.dp),
                color = if (isBackupSelected) Color(0xFF0F766E) else Color(0xFFF0FDFA),
                border = BorderStroke(
                    1.dp,
                    if (isBackupSelected) Color(0xFF14B8A6) else Color(0xFF99F6E4)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBackupSelected) Color.White.copy(alpha = 0.2f) else Color(0xFF0D9488),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Sync",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AutoScrollText(
                                    text = if (isUrdu) "بیک اپ اور سنک" else "Backup & Sync",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isBackupSelected) Color.White else Slate900
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                            }
                            Text(
                                text = if (isUrdu) "کلاؤڈ و لوکل بیک اپ" else "Cloud & Local Sync",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isBackupSelected) Color(0xFFCCFBF1) else Color(0xFF0D9488)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Cloud",
                        tint = if (isBackupSelected) Color.White else Color(0xFF0D9488),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
