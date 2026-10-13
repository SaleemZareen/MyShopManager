package com.saleemkhan.myshopmanager.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saleemkhan.myshopmanager.model.AppState
import com.saleemkhan.myshopmanager.ui.theme.*

data class SetupStepItem(
    val id: Int,
    val titleEn: String,
    val titleUr: String,
    val descEn: String,
    val descUr: String,
    val isComplete: Boolean,
    val actionTextEn: String,
    val actionTextUr: String,
    val onClick: () -> Unit
)

@Composable
fun OnboardingSetupCard(
    state: AppState,
    isUrdu: Boolean,
    onOpenSettings: () -> Unit,
    onOpenCashAccounts: () -> Unit,
    onOpenAddInventory: () -> Unit,
    onOpenKhata: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    // Dynamic Step Completion checks
    val isStep1Complete = state.profile.ownerName.isNotBlank() || state.profile.phone.isNotBlank()
    val isStep2Complete = state.bankAccounts.isNotEmpty()
    val isStep3Complete = state.inventory.isNotEmpty()
    val isStep4Complete = state.customers.isNotEmpty() || state.suppliers.isNotEmpty()

    val steps = listOf(
        SetupStepItem(
            id = 1,
            titleEn = "Shop Profile Details",
            titleUr = "دکان کی معلومات درج کریں",
            descEn = "Owner name, phone & shop category",
            descUr = "مالک کا نام، فون اور دکان کی کیٹیگری",
            isComplete = isStep1Complete,
            actionTextEn = if (isStep1Complete) "Edit" else "Set Up",
            actionTextUr = if (isStep1Complete) "ترمیم کریں" else "سیٹ کریں",
            onClick = onOpenSettings
        ),
        SetupStepItem(
            id = 2,
            titleEn = "Opening Cash in Till",
            titleUr = "گلے میں موجود شروعاتی نقد رقم",
            descEn = "Starting cash in till & bank balances",
            descUr = "گلے کا کیش اور بینک بیلنس",
            isComplete = isStep2Complete,
            actionTextEn = if (isStep2Complete) "View" else "Add Cash",
            actionTextUr = if (isStep2Complete) "دیکھیں" else "کیش ایڈ کریں",
            onClick = onOpenCashAccounts
        ),
        SetupStepItem(
            id = 3,
            titleEn = "Opening Stock on Shelves",
            titleUr = "دکان کا شروعاتی سامان (اسٹاک)",
            descEn = "Existing inventory currently in shop",
            descUr = "دکان میں موجود سامانِ تجارت",
            isComplete = isStep3Complete,
            actionTextEn = if (isStep3Complete) "Add More" else "Add Stock",
            actionTextUr = if (isStep3Complete) "مزید ایڈ کریں" else "مال ایڈ کریں",
            onClick = onOpenAddInventory
        ),
        SetupStepItem(
            id = 4,
            titleEn = "Customer & Supplier Khata",
            titleUr = "گاہکوں و سپلائرز کا پرانا ادھار",
            descEn = "Opening credit balances & contacts",
            descUr = "پرانے گاہکوں کا بقایا ادھار کھاتہ",
            isComplete = isStep4Complete,
            actionTextEn = if (isStep4Complete) "View Khata" else "Add Khata",
            actionTextUr = if (isStep4Complete) "کھاتہ دیکھیں" else "ادھار ایڈ کریں",
            onClick = onOpenKhata
        )
    )

    val completedCount = steps.count { it.isComplete }
    val isAllComplete = completedCount == steps.size
    val progressFraction = completedCount.toFloat() / steps.size.toFloat()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (isAllComplete) Color(0xFFA7F3D0) else Color(0xFFBAE6FD)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isAllComplete) Color(0xFF10B981) else Color(0xFF0284C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAllComplete) Icons.Default.CheckCircle else Icons.Default.Checklist,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isUrdu) "دکان سیٹ اپ گائیڈ (Shop Setup)" else "Shop Setup Guide",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Slate900
                        )
                        Text(
                            text = if (isAllComplete) {
                                if (isUrdu) "🎉 تمام 4 مراحل مکمل ہو گئے!" else "🎉 All 4 steps completed!"
                            } else {
                                if (isUrdu) "$completedCount مندرجہ $${steps.size} مراحل مکمل" else "$completedCount of ${steps.size} steps completed"
                            },
                            fontSize = 11.sp,
                            color = if (isAllComplete) Color(0xFF047857) else Slate600
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle",
                            tint = Slate600
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Linear Progress Bar
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isAllComplete) Color(0xFF10B981) else Color(0xFF0284C7),
                trackColor = Color(0xFFE2E8F0)
            )

            // Step List items
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    steps.forEach { step ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (step.isComplete) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (step.isComplete) Color(0xFFA7F3D0) else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { step.onClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (step.isComplete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (step.isComplete) Color(0xFF059669) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Column {
                                        Text(
                                            text = if (isUrdu) step.titleUr else step.titleEn,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Slate900
                                        )
                                        Text(
                                            text = if (isUrdu) step.descUr else step.descEn,
                                            fontSize = 10.5.sp,
                                            color = Slate600
                                        )
                                    }
                                }

                                Button(
                                    onClick = step.onClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (step.isComplete) Color(0xFFD1FAE5) else Color(0xFF0284C7),
                                        contentColor = if (step.isComplete) Color(0xFF065F46) else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(
                                        text = if (isUrdu) step.actionTextUr else step.actionTextEn,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
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
