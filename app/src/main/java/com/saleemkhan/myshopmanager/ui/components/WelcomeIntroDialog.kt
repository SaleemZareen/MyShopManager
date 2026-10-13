package com.saleemkhan.myshopmanager.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saleemkhan.myshopmanager.ui.theme.*

data class IntroSlide(
    val titleEn: String,
    val titleUr: String,
    val descEn: String,
    val descUr: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val badgeEn: String,
    val badgeUr: String
)

@Composable
fun WelcomeIntroDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onStartSetup: () -> Unit,
    isUrdu: Boolean
) {
    if (!isOpen) return

    var currentSlideIndex by remember { mutableIntStateOf(0) }

    val slides = remember {
        listOf(
            IntroSlide(
                titleEn = "1. Set Your Shop Profile",
                titleUr = "1. دکان کی تفصیلات درج کریں",
                descEn = "Enter your shop name, owner details, and business category in Settings so your printed bills and customer ledgers display your brand name.",
                descUr = "سیٹنگز میں اپنی دکان کا نام، اپنا نام اور فون نمبر درج کریں تاکہ تمام پرنٹ شدہ بلز اور کسٹمر کھاتے آپ کی دکان کے نام سے بنیں۔",
                icon = Icons.Default.Storefront,
                iconBgColor = Color(0xFF0284C7),
                badgeEn = "Step 1: Profile",
                badgeUr = "مرحلہ 1: دکان کی پروفائل"
            ),
            IntroSlide(
                titleEn = "2. Set Opening Cash & Bank",
                titleUr = "2. گلے کی رقم و بینک بیلنس درج کریں",
                descEn = "Enter your current cash in till and bank balances in Cash & Bank Accounts so daily sales and expenses track your actual liquid money accurately.",
                descUr = "گلہ و بینک اکاؤنٹس میں موجود نقد رقم درج کریں۔ اس سے آپ کی روزمرہ نقد آمدن و اخراجات بالکل صحیح ٹریک ہوں گے۔",
                icon = Icons.Default.Payments,
                iconBgColor = Color(0xFF059669),
                badgeEn = "Step 2: Cash & Bank",
                badgeUr = "مرحلہ 2: گلہ و کیش بیلنس"
            ),
            IntroSlide(
                titleEn = "3. Add Existing Shelf Stock",
                titleUr = "3. دکان کا موجودہ شروعاتی اسٹاک درج کریں",
                descEn = "Add goods already on your shop shelves under 'Opening Stock'. Later, when purchasing new stock from suppliers, record them under 'Purchases'.",
                descUr = "دکان کے کاؤنٹر و الماری میں موجود سامان کو 'شروعاتی اسٹاک (Opening Stock)' کے طور پر ایڈ کریں۔ نیا مال خریدنے پر 'نئی خریداری (Purchase)' درج کریں۔",
                icon = Icons.Default.Inventory2,
                iconBgColor = Color(0xFF7C3AED),
                badgeEn = "Step 3: Opening Stock",
                badgeUr = "مرحلہ 3: شروعاتی سامان"
            ),
            IntroSlide(
                titleEn = "4. Setup Customer Khata & Daily Sales",
                titleUr = "4. پچھلا ادھار کھاتہ اور روزمرہ فروخت",
                descEn = "Record opening credit balances for customers and suppliers. Process daily sales in cash or Khata credit with 1-click automatic stock updates.",
                descUr = "پرانے گاہکوں اور سپلائرز کا بقایا ادھار کھاتہ درج کریں۔ روزانہ سامان بکنے پر 1 کلک میں نقد یا ادھار فروخت درج کریں۔",
                icon = Icons.Default.Receipt,
                iconBgColor = Color(0xFFD97706),
                badgeEn = "Step 4: Khata & Sales",
                badgeUr = "مرحلہ 4: ادھار و فروخت"
            )
        )
    }

    val currentSlide = slides[currentSlideIndex]
    val isLastSlide = currentSlideIndex == slides.size - 1

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header badge & close icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = currentSlide.iconBgColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, currentSlide.iconBgColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (isUrdu) currentSlide.badgeUr else currentSlide.badgeEn,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentSlide.iconBgColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    TextButton(onClick = onClose) {
                        Text(text = if (isUrdu) "چھوڑیں" else "Skip", fontSize = 12.sp, color = Slate500)
                    }
                }

                // Slide Icon Box
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(currentSlide.iconBgColor, currentSlide.iconBgColor.copy(alpha = 0.8f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentSlide.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Slide Content Text
                AnimatedContent(
                    targetState = currentSlide,
                    label = "slide_transition"
                ) { slide ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isUrdu) slide.titleUr else slide.titleEn,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (isUrdu) slide.descUr else slide.descEn,
                            fontSize = 13.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    slides.indices.forEach { index ->
                        val isSelected = index == currentSlideIndex
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (isSelected) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) currentSlide.iconBgColor else Slate300)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentSlideIndex > 0) {
                        OutlinedButton(
                            onClick = { currentSlideIndex-- },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isUrdu) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isUrdu) "پیچھے" else "Back", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (isLastSlide) {
                                onStartSetup()
                            } else {
                                currentSlideIndex++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isLastSlide) DukanGreenPrimary else currentSlide.iconBgColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (isLastSlide) {
                                if (isUrdu) "سیٹ اپ شروع کریں 🎉" else "Start Setup 🎉"
                            } else {
                                if (isUrdu) "آگے چلیں" else "Next"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (!isLastSlide) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isUrdu) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
