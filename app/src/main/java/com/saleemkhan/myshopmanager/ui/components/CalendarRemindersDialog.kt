package com.saleemkhan.myshopmanager.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saleemkhan.myshopmanager.model.LoanRecord
import com.saleemkhan.myshopmanager.model.SupplierParty
import com.saleemkhan.myshopmanager.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class ReminderType {
    TAX,
    SUPPLIER,
    UTILITY,
    OTHER
}

data class ShopReminder(
    val id: String,
    val title: String,
    val titleUrdu: String = title,
    val date: String, // YYYY-MM-DD
    val type: ReminderType = ReminderType.OTHER,
    val isDone: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false
)

enum class ReminderFilterTab {
    SELECTED,
    CURRENT,
    PENDING,
    COMPLETED,
    ALL,
    ARCHIVED,
    DELETED
}

private val DEFAULT_REMINDERS = listOf(
    ShopReminder(
        id = "rem_1",
        title = "FBR Monthly Sales Tax Return Filing",
        titleUrdu = "ایف بی آر ماہانہ سیلز ٹیکس گوشوارہ جمع کروائیں",
        date = "2026-08-15",
        type = ReminderType.TAX,
        isDone = false,
        isArchived = false,
        isDeleted = false
    ),
    ShopReminder(
        id = "rem_2",
        title = "Pay Metro Cash & Carry Wholesale Bill",
        titleUrdu = "میٹرو ہول سیلر بل کی ادائیگی",
        date = "2026-08-05",
        type = ReminderType.SUPPLIER,
        isDone = false,
        isArchived = false,
        isDeleted = false
    ),
    ShopReminder(
        id = "rem_3",
        title = "Dukan Commercial Electricity Bill",
        titleUrdu = "دکان کے بجلی بل کی آخری تاریخ",
        date = "2026-08-07",
        type = ReminderType.UTILITY,
        isDone = true,
        isArchived = false,
        isDeleted = false
    )
)

private val MONTH_NAMES_EN = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val MONTH_NAMES_UR = listOf(
    "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
    "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarRemindersDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    suppliers: List<SupplierParty> = emptyList(),
    loans: List<LoanRecord> = emptyList(),
    isUrdu: Boolean = false
) {
    if (!isOpen) return

    BackHandler {
        onClose()
    }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("myshopmanager_reminders_prefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    val todayCalendar = remember { Calendar.getInstance() }
    val todayYMD = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.format(Date())
    }

    var viewYear by remember { mutableIntStateOf(todayCalendar.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(todayCalendar.get(Calendar.MONTH)) } // 0-indexed
    var selectedDate by remember { mutableStateOf(todayYMD) }

    // Load initial reminders from SharedPreferences or default
    var reminders by remember {
        mutableStateOf(
            try {
                val json = prefs.getString("my_shop_reminders", null)
                if (!json.isNullOrBlank()) {
                    val type = object : TypeToken<List<ShopReminder>>() {}.type
                    val loaded: List<ShopReminder> = gson.fromJson(json, type)
                    if (loaded.isNotEmpty()) loaded else DEFAULT_REMINDERS
                } else {
                    DEFAULT_REMINDERS
                }
            } catch (e: Exception) {
                DEFAULT_REMINDERS
            }
        )
    }

    // Save reminders whenever updated
    fun saveReminders(newList: List<ShopReminder>) {
        reminders = newList
        try {
            prefs.edit().putString("my_shop_reminders", gson.toJson(newList)).apply()
        } catch (_: Exception) {}
    }

    var newTitle by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(ReminderType.OTHER) }
    var filterTab by remember { mutableStateOf(ReminderFilterTab.SELECTED) }
    var selectedOverallMonth by remember { mutableStateOf("ALL") }
    val collapsedMonths = remember { mutableStateMapOf<String, Boolean>() }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

    // Dropdown menus for Month and Year
    var showMonthDropdown by remember { mutableStateOf(false) }
    var showYearDropdown by remember { mutableStateOf(false) }
    var showOverallMonthDropdown by remember { mutableStateOf(false) }

    // Navigation functions
    fun handlePrevMonth() {
        if (viewMonth == 0) {
            viewMonth = 11
            viewYear -= 1
        } else {
            viewMonth -= 1
        }
    }

    fun handleNextMonth() {
        if (viewMonth == 11) {
            viewMonth = 0
            viewYear += 1
        } else {
            viewMonth += 1
        }
    }

    fun handlePrevYear() {
        viewYear -= 1
    }

    fun handleNextYear() {
        viewYear += 1
    }

    fun handleJumpToToday() {
        viewYear = todayCalendar.get(Calendar.YEAR)
        viewMonth = todayCalendar.get(Calendar.MONTH)
        selectedDate = todayYMD
    }

    // Dynamic list of combined reminders including supplier and loan dues
    val allCombinedReminders = remember(reminders, suppliers, loans) {
        val extraDues = mutableListOf<ShopReminder>()
        suppliers.filter { it.totalPayable > 0 && !it.dueDate.isNullOrBlank() }.forEach { sup ->
            val due = sup.dueDate ?: ""
            if (reminders.none { it.id == "sup_${sup.id}" }) {
                extraDues.add(
                    ShopReminder(
                        id = "sup_${sup.id}",
                        title = "Pay ${sup.name}: Rs. ${sup.totalPayable.toInt()}",
                        titleUrdu = "${sup.name} کو سپلائر ادائیگی: Rs. ${sup.totalPayable.toInt()}",
                        date = if (due.length >= 10) due.substring(0, 10) else due,
                        type = ReminderType.SUPPLIER,
                        isDone = false
                    )
                )
            }
        }
        loans.filter { it.dueDate.isNotBlank() }.forEach { l ->
            val due = l.dueDate
            if (reminders.none { it.id == "loan_${l.id}" }) {
                extraDues.add(
                    ShopReminder(
                        id = "loan_${l.id}",
                        title = "Loan Due: ${l.partyName} (Rs. ${l.amount.toInt()})",
                        titleUrdu = "قرض ادائیگی: ${l.partyName} (Rs. ${l.amount.toInt()})",
                        date = if (due.length >= 10) due.substring(0, 10) else due,
                        type = ReminderType.TAX,
                        isDone = false
                    )
                )
            }
        }
        reminders + extraDues
    }

    // Helper to format date nicely
    fun formatReadableDate(dateStr: String): String {
        return try {
            val parts = dateStr.trim().split("-")
            if (parts.size >= 3) {
                val y = parts[0].toIntOrNull() ?: 2026
                val m = parts[1].toIntOrNull() ?: 1
                val d = parts[2].substring(0, 2).toIntOrNull() ?: 1
                val monthIdx = (m - 1).coerceIn(0, 11)
                val mName = if (isUrdu) MONTH_NAMES_UR[monthIdx] else MONTH_NAMES_EN[monthIdx].take(3)
                if (isUrdu) "$d $mName $y" else "$d $mName $y"
            } else {
                dateStr
            }
        } catch (_: Exception) {
            dateStr
        }
    }

    // Calendar grid calculations
    val calendarMonthCal = remember(viewYear, viewMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, viewYear)
            set(Calendar.MONTH, viewMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val daysInMonth = remember(viewYear, viewMonth) {
        calendarMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val firstDayOfWeek = remember(viewYear, viewMonth) {
        // 1=Sunday in Calendar, convert to 0=Sun
        calendarMonthCal.get(Calendar.DAY_OF_WEEK) - 1
    }

    fun formatDayYMD(dayNum: Int): String {
        return "%04d-%02d-%02d".format(Locale.US, viewYear, viewMonth + 1, dayNum)
    }

    // Current viewed month statistics
    val monthPrefix = "%04d-%02d".format(Locale.US, viewYear, viewMonth + 1)
    val monthReminders = allCombinedReminders.filter { it.date.startsWith(monthPrefix) }

    val currentMonthCount = monthReminders.filter { it.date == todayYMD && !it.isArchived && !it.isDeleted }.size
    val pendingMonthCount = monthReminders.filter { !it.isDone && !it.isArchived && !it.isDeleted }.size
    val completedMonthCount = monthReminders.filter { it.isDone && !it.isArchived && !it.isDeleted }.size
    val allMonthCount = monthReminders.filter { !it.isArchived && !it.isDeleted }.size
    val archivedMonthCount = monthReminders.filter { it.isArchived && !it.isDeleted }.size
    val deletedMonthCount = monthReminders.filter { it.isDeleted }.size

    // Available unique months from all reminders
    val availableMonths = remember(allCombinedReminders) {
        allCombinedReminders
            .map { it.date.take(7) }
            .filter { it.length == 7 }
            .distinct()
            .sortedDescending()
    }

    // Category counts based on selectedOverallMonth
    val targetReminders = remember(allCombinedReminders, selectedOverallMonth) {
        if (selectedOverallMonth == "ALL") allCombinedReminders
        else allCombinedReminders.filter { it.date.startsWith(selectedOverallMonth) }
    }

    val overallSelectedCount = targetReminders.filter { it.date == selectedDate && !it.isArchived && !it.isDeleted }.size
    val overallCurrentCount = targetReminders.filter { it.date == todayYMD && !it.isArchived && !it.isDeleted }.size
    val overallPendingCount = targetReminders.filter { !it.isDone && !it.isArchived && !it.isDeleted }.size
    val overallCompletedCount = targetReminders.filter { it.isDone && !it.isArchived && !it.isDeleted }.size
    val overallAllCount = targetReminders.filter { !it.isArchived && !it.isDeleted }.size
    val overallArchivedCount = targetReminders.filter { it.isArchived && !it.isDeleted }.size
    val overallDeletedCount = targetReminders.filter { it.isDeleted }.size

    // Filtered reminders list
    val filteredReminders = remember(targetReminders, filterTab, selectedDate) {
        targetReminders.filter { rem ->
            if (filterTab == ReminderFilterTab.DELETED) return@filter rem.isDeleted
            if (rem.isDeleted) return@filter false

            if (filterTab == ReminderFilterTab.ARCHIVED) return@filter rem.isArchived
            if (rem.isArchived) return@filter false

            when (filterTab) {
                ReminderFilterTab.SELECTED -> rem.date == selectedDate
                ReminderFilterTab.CURRENT -> rem.date == todayYMD
                ReminderFilterTab.PENDING -> !rem.isDone
                ReminderFilterTab.COMPLETED -> rem.isDone
                ReminderFilterTab.ALL -> true
                else -> true
            }
        }
    }

    // Group filtered reminders by month key YYYY-MM
    val groupedReminders = remember(filteredReminders) {
        filteredReminders
            .sortedByDescending { it.date }
            .groupBy { it.date.take(7) }
    }

    fun handleAddReminder() {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return

        val newRem = ShopReminder(
            id = "rem_${System.currentTimeMillis()}",
            title = trimmed,
            titleUrdu = trimmed,
            date = selectedDate,
            type = newType,
            isDone = false,
            isArchived = false,
            isDeleted = false
        )
        saveReminders(listOf(newRem) + reminders)
        newTitle = ""
    }

    fun toggleReminder(id: String) {
        saveReminders(reminders.map { if (it.id == id) it.copy(isDone = !it.isDone) else it })
    }

    fun toggleArchiveReminder(id: String) {
        saveReminders(reminders.map { if (it.id == id) it.copy(isArchived = !it.isArchived) else it })
    }

    fun deleteReminder(id: String) {
        saveReminders(reminders.map { if (it.id == id) it.copy(isDeleted = true) else it })
    }

    fun restoreDeletedReminder(id: String) {
        saveReminders(reminders.map { if (it.id == id) it.copy(isDeleted = false) else it })
    }

    fun permanentDeleteReminder(id: String) {
        saveReminders(reminders.filter { it.id != id })
    }

    // Gesture drag tracking
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
            ) {
                // 1. Header - Premium Primary Green Gradient (Synced with React CalendarRemindersModal.tsx)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0F8A5F), Color(0xFF0B724E), Color(0xFF066647))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Calendar",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )

                            Column {
                                Text(
                                    text = if (isUrdu) "کالنڈر اور یاد دہانی (Reminders)" else "Calendar & Shop Reminders",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = formatReadableDate(selectedDate),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFD1FAE5).copy(alpha = 0.95f)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isUrdu) "واپس" else "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Scrollable Body
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mini Calendar Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { accumulatedDrag = 0f },
                                        onDragEnd = {
                                            if (accumulatedDrag > 50f) {
                                                handlePrevMonth()
                                            } else if (accumulatedDrag < -50f) {
                                                handleNextMonth()
                                            }
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            accumulatedDrag += dragAmount
                                        }
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                // Month & Year Navigation Controls Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Month Controls
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = { handlePrevMonth() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronLeft,
                                                contentDescription = "Prev Month",
                                                tint = Color(0xFF065F46),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Box {
                                            Surface(
                                                color = Color(0xFFECFDF5),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.clickable { showMonthDropdown = true }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isUrdu) MONTH_NAMES_UR[viewMonth] else MONTH_NAMES_EN[viewMonth],
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF064E3B)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = Color(0xFF064E3B),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = showMonthDropdown,
                                                onDismissRequest = { showMonthDropdown = false }
                                            ) {
                                                (if (isUrdu) MONTH_NAMES_UR else MONTH_NAMES_EN).forEachIndexed { index, name ->
                                                    DropdownMenuItem(
                                                        text = { Text(name, fontSize = 12.sp, fontWeight = if (index == viewMonth) FontWeight.Bold else FontWeight.Normal) },
                                                        onClick = {
                                                            viewMonth = index
                                                            showMonthDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { handleNextMonth() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Next Month",
                                                tint = Color(0xFF065F46),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Year Controls
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = { handlePrevYear() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronLeft,
                                                contentDescription = "Prev Year",
                                                tint = Color(0xFF065F46),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Box {
                                            Surface(
                                                color = Color(0xFFECFDF5),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.clickable { showYearDropdown = true }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "$viewYear",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF064E3B)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = Color(0xFF064E3B),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = showYearDropdown,
                                                onDismissRequest = { showYearDropdown = false }
                                            ) {
                                                for (y in (viewYear - 15)..(viewYear + 15)) {
                                                    DropdownMenuItem(
                                                        text = { Text("$y", fontSize = 12.sp, fontWeight = if (y == viewYear) FontWeight.Bold else FontWeight.Normal) },
                                                        onClick = {
                                                            viewYear = y
                                                            showYearDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { handleNextYear() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Next Year",
                                                tint = Color(0xFF065F46),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Weekday Labels Grid (7 Columns)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { dayName ->
                                        Text(
                                            text = dayName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Slate400,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Days Grid
                                val totalSlots = firstDayOfWeek + daysInMonth
                                val rowCount = (totalSlots + 6) / 7

                                for (row in 0 until rowCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        for (col in 0..6) {
                                            val slotIndex = row * 7 + col
                                            val dayNum = slotIndex - firstDayOfWeek + 1

                                            if (slotIndex < firstDayOfWeek || dayNum > daysInMonth) {
                                                Spacer(modifier = Modifier.weight(1f).height(32.dp))
                                            } else {
                                                val dayYMD = formatDayYMD(dayNum)
                                                val isSelected = dayYMD == selectedDate
                                                val isToday = dayYMD == todayYMD

                                                val dayReminders = allCombinedReminders.filter { it.date == dayYMD && !it.isArchived && !it.isDeleted }
                                                val hasPending = dayReminders.any { !it.isDone }
                                                val hasDone = dayReminders.isNotEmpty() && dayReminders.all { it.isDone }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(34.dp)
                                                        .padding(2.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = when {
                                                            isSelected -> Color(0xFF0F8A5F)
                                                            isToday -> Color(0xFFFEF3C7)
                                                            else -> Color(0xFFF8FAFC)
                                                        },
                                                        border = BorderStroke(
                                                            1.dp,
                                                            when {
                                                                isSelected -> Color(0xFF065F46)
                                                                isToday -> Color(0xFFF59E0B)
                                                                else -> Color(0xFFE2E8F0)
                                                            }
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clickable { selectedDate = dayYMD }
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "$dayNum",
                                                                fontSize = 11.5.sp,
                                                                fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Bold,
                                                                color = when {
                                                                    isSelected -> Color.White
                                                                    isToday -> Color(0xFF78350F)
                                                                    else -> Slate700
                                                                }
                                                            )

                                                            // Corner status dot
                                                            if (hasPending || hasDone) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(5.5.dp)
                                                                        .background(
                                                                            if (hasPending) Color(0xFFEF4444) else Color(0xFF10B981),
                                                                            CircleShape
                                                                        )
                                                                        .align(Alignment.TopEnd)
                                                                        .padding(end = 2.dp, top = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Divider(color = Slate200, modifier = Modifier.padding(vertical = 10.dp))

                                // Selected Date Indicator Banner with Today Jump button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(modifier = Modifier.size(7.dp).background(Color(0xFF0F8A5F), CircleShape))
                                        Text(
                                            text = if (isUrdu) "منتخب تاریخ:" else "Selected Date:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate700
                                        )
                                        Text(
                                            text = formatReadableDate(selectedDate),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Slate900
                                        )
                                    }

                                    TextButton(
                                        onClick = { handleJumpToToday() },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text(
                                            text = if (isUrdu) "(آج پر جائیں)" else "(Today)",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F8A5F)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Month's Reminders Category Counts Chips
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (isUrdu)
                                            "${MONTH_NAMES_UR[viewMonth]} کی یاد دہانیاں:"
                                        else
                                            "Current Month's Reminders (${MONTH_NAMES_EN[viewMonth]}):",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Slate800
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        // Current
                                        CategoryChip(
                                            label = if (isUrdu) "آج" else "Current",
                                            count = currentMonthCount,
                                            isSelected = filterTab == ReminderFilterTab.CURRENT,
                                            activeColor = Color(0xFF0F8A5F),
                                            inactiveBg = Color(0xFFECFDF5),
                                            inactiveText = Color(0xFF065F46),
                                            onClick = { filterTab = ReminderFilterTab.CURRENT }
                                        )
                                        // Pending
                                        CategoryChip(
                                            label = if (isUrdu) "زیر التواء" else "Pending",
                                            count = pendingMonthCount,
                                            isSelected = filterTab == ReminderFilterTab.PENDING,
                                            activeColor = Color(0xFFE11D48),
                                            inactiveBg = Color(0xFFFFF1F2),
                                            inactiveText = Color(0xFF9F1239),
                                            onClick = { filterTab = ReminderFilterTab.PENDING }
                                        )
                                        // Completed
                                        CategoryChip(
                                            label = if (isUrdu) "مکمل" else "Done",
                                            count = completedMonthCount,
                                            isSelected = filterTab == ReminderFilterTab.COMPLETED,
                                            activeColor = Color(0xFF059669),
                                            inactiveBg = Color(0xFFECFDF5),
                                            inactiveText = Color(0xFF065F46),
                                            onClick = { filterTab = ReminderFilterTab.COMPLETED }
                                        )
                                        // All
                                        CategoryChip(
                                            label = if (isUrdu) "تمام" else "All",
                                            count = allMonthCount,
                                            isSelected = filterTab == ReminderFilterTab.ALL,
                                            activeColor = Color(0xFF334155),
                                            inactiveBg = Color(0xFFF1F5F9),
                                            inactiveText = Color(0xFF334155),
                                            onClick = { filterTab = ReminderFilterTab.ALL }
                                        )
                                        // Archived
                                        CategoryChip(
                                            label = if (isUrdu) "آرکائیو" else "Archived",
                                            count = archivedMonthCount,
                                            isSelected = filterTab == ReminderFilterTab.ARCHIVED,
                                            activeColor = Color(0xFFD97706),
                                            inactiveBg = Color(0xFFFFFBEB),
                                            inactiveText = Color(0xFF92400E),
                                            onClick = { filterTab = ReminderFilterTab.ARCHIVED }
                                        )
                                        // Deleted
                                        CategoryChip(
                                            label = if (isUrdu) "حذف شدہ" else "Deleted",
                                            count = deletedMonthCount,
                                            isSelected = filterTab == ReminderFilterTab.DELETED,
                                            activeColor = Color(0xFFDC2626),
                                            inactiveBg = Color(0xFFFEF2F2),
                                            inactiveText = Color(0xFF991B1B),
                                            onClick = { filterTab = ReminderFilterTab.DELETED }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Reminders Section Header & Category Filters
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = Color(0xFF0F8A5F),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isUrdu) "دکان کی یاد دہانیاں (مجموعی)" else "Dukan Reminders (Overall)",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Slate900
                                    )
                                }

                                // Month Dropdown Filter
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF1F5F9),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.clickable { showOverallMonthDropdown = true }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = if (selectedOverallMonth == "ALL")
                                                    (if (isUrdu) "تمام مہینے" else "All Months")
                                                else
                                                    selectedOverallMonth,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF064E3B)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = Color(0xFF064E3B),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showOverallMonthDropdown,
                                        onDismissRequest = { showOverallMonthDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (isUrdu) "تمام مہینے (All Months)" else "All Months", fontSize = 11.5.sp) },
                                            onClick = {
                                                selectedOverallMonth = "ALL"
                                                showOverallMonthDropdown = false
                                            }
                                        )
                                        availableMonths.forEach { mKey ->
                                            DropdownMenuItem(
                                                text = { Text(mKey, fontSize = 11.5.sp) },
                                                onClick = {
                                                    selectedOverallMonth = mKey
                                                    showOverallMonthDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Filter Tabs Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterTabButton(
                                    label = if (isUrdu) "منتخب دن" else "Selected",
                                    count = overallSelectedCount,
                                    isSelected = filterTab == ReminderFilterTab.SELECTED,
                                    activeBg = Color(0xFF0F8A5F),
                                    onClick = { filterTab = ReminderFilterTab.SELECTED }
                                )
                                FilterTabButton(
                                    label = if (isUrdu) "آج" else "Current",
                                    count = overallCurrentCount,
                                    isSelected = filterTab == ReminderFilterTab.CURRENT,
                                    activeBg = Color(0xFF0F8A5F),
                                    onClick = { filterTab = ReminderFilterTab.CURRENT }
                                )
                                FilterTabButton(
                                    label = if (isUrdu) "زیر التواء" else "Pending",
                                    count = overallPendingCount,
                                    isSelected = filterTab == ReminderFilterTab.PENDING,
                                    activeBg = Color(0xFFE11D48),
                                    onClick = { filterTab = ReminderFilterTab.PENDING }
                                )
                                FilterTabButton(
                                    label = if (isUrdu) "مکمل" else "Completed",
                                    count = overallCompletedCount,
                                    isSelected = filterTab == ReminderFilterTab.COMPLETED,
                                    activeBg = Color(0xFF059669),
                                    onClick = { filterTab = ReminderFilterTab.COMPLETED }
                                )
                                FilterTabButton(
                                    label = if (isUrdu) "تمام" else "All",
                                    count = overallAllCount,
                                    isSelected = filterTab == ReminderFilterTab.ALL,
                                    activeBg = Color(0xFF334155),
                                    onClick = { filterTab = ReminderFilterTab.ALL }
                                )
                                FilterTabButton(
                                    label = if (isUrdu) "آرکائیو" else "Archived",
                                    count = overallArchivedCount,
                                    isSelected = filterTab == ReminderFilterTab.ARCHIVED,
                                    activeBg = Color(0xFFD97706),
                                    onClick = { filterTab = ReminderFilterTab.ARCHIVED }
                                )
                                FilterTabButton(
                                    label = if (isUrdu) "حذف شدہ" else "Deleted",
                                    count = overallDeletedCount,
                                    isSelected = filterTab == ReminderFilterTab.DELETED,
                                    activeBg = Color(0xFFDC2626),
                                    onClick = { filterTab = ReminderFilterTab.DELETED }
                                )
                            }

                            // Empty Trash Button if DELETED tab
                            if (filterTab == ReminderFilterTab.DELETED && overallDeletedCount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { showEmptyTrashConfirm = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFFF1F2),
                                            contentColor = Color(0xFFE11D48)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteForever,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isUrdu) "کوڑا دان خالی کریں" else "Empty Trash",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Grouped Reminders by Month
                    if (groupedReminders.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Slate400,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = when (filterTab) {
                                            ReminderFilterTab.SELECTED -> if (isUrdu) "اس تاریخ کے لیے کوئی یاد دہانی نہیں ہے" else "No reminders for ${formatReadableDate(selectedDate)}"
                                            ReminderFilterTab.CURRENT -> if (isUrdu) "آج کے لیے کوئی یاد دہانی نہیں ہے" else "No reminders for today"
                                            ReminderFilterTab.PENDING -> if (isUrdu) "کوئی زیر التواء یاد دہانی نہیں ہے" else "No pending reminders"
                                            ReminderFilterTab.COMPLETED -> if (isUrdu) "کوئی مکمل شدہ یاد دہانی نہیں ہے" else "No completed reminders"
                                            ReminderFilterTab.ARCHIVED -> if (isUrdu) "کوئی آرکائیو یاد دہانی نہیں" else "No archived reminders"
                                            ReminderFilterTab.DELETED -> if (isUrdu) "کوئی حذف شدہ یاد دہانی نہیں" else "No deleted reminders"
                                            else -> if (isUrdu) "کوئی یاد دہانی نہیں ہے" else "No reminders found"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate500,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        groupedReminders.forEach { (monthKey, monthRems) ->
                            val isCollapsed = collapsedMonths[monthKey] ?: false
                            val mPending = monthRems.count { !it.isDone && !it.isArchived && !it.isDeleted }
                            val mCompleted = monthRems.count { it.isDone && !it.isArchived && !it.isDeleted }
                            val mArchived = monthRems.count { it.isArchived && !it.isDeleted }
                            val mDeleted = monthRems.count { it.isDeleted }

                            // Month Header Banner
                            item(key = "header_$monthKey") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFE2E8F0),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { collapsedMonths[monthKey] = !isCollapsed }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCollapsed) Icons.Default.ChevronRight else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = Slate700,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.CalendarMonth,
                                                contentDescription = null,
                                                tint = Color(0xFF0F8A5F),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = monthKey,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Slate800
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (mPending > 0) {
                                                CountBadge(label = if (isUrdu) "زیر التواء" else "Pending", count = mPending, bg = Color(0xFFFFE4E6), text = Color(0xFF9F1239))
                                            }
                                            if (mCompleted > 0) {
                                                CountBadge(label = if (isUrdu) "مکمل" else "Done", count = mCompleted, bg = Color(0xFFD1FAE5), text = Color(0xFF065F46))
                                            }
                                            if (mArchived > 0) {
                                                CountBadge(label = if (isUrdu) "آرکائیو" else "Archived", count = mArchived, bg = Color(0xFFFEF3C7), text = Color(0xFF92400E))
                                            }
                                            if (mDeleted > 0) {
                                                CountBadge(label = if (isUrdu) "حذف" else "Deleted", count = mDeleted, bg = Color(0xFFFEE2E2), text = Color(0xFF991B1B))
                                            }
                                            Surface(
                                                color = Color.White,
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text(
                                                    text = "${monthRems.size}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF064E3B),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Reminders for this month
                            if (!isCollapsed) {
                                items(monthRems, key = { it.id }) { rem ->
                                    ReminderCard(
                                        reminder = rem,
                                        isUrdu = isUrdu,
                                        onToggleDone = { toggleReminder(rem.id) },
                                        onToggleArchive = { toggleArchiveReminder(rem.id) },
                                        onDelete = { deleteReminder(rem.id) },
                                        onRestore = { restoreDeletedReminder(rem.id) },
                                        onPermanentDelete = { permanentDeleteReminder(rem.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Add Reminder Form at Bottom (Fixed Footer)
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isUrdu) "نئی یاد دہانی:" else "Add Reminder:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatReadableDate(selectedDate),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF064E3B)
                                )
                            }

                            // Category Type Selector Pills
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ReminderType.values().forEach { type ->
                                    val isSelected = newType == type
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) Color(0xFF0F8A5F) else Color(0xFFF1F5F9),
                                        modifier = Modifier.clickable { newType = type }
                                    ) {
                                        Text(
                                            text = type.name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color.White else Slate600,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                placeholder = {
                                    Text(
                                        text = if (isUrdu)
                                            "مثال: ${formatReadableDate(selectedDate)} کا سپلائر بل..."
                                        else
                                            "Reminder for ${formatReadableDate(selectedDate)}...",
                                        fontSize = 11.5.sp,
                                        color = Slate400
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF0F8A5F),
                                    unfocusedBorderColor = Slate300
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )

                            Button(
                                onClick = { handleAddReminder() },
                                enabled = newTitle.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0F8A5F),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUrdu) "شامل کریں" else "Add",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Empty Trash
    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = {
                Text(
                    text = if (isUrdu) "کوڑا دان خالی کریں؟" else "Empty Trash?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isUrdu)
                        "کیا آپ تمام حذف شدہ یاد دہانیوں کو مستقل طور پر صاف کرنا چاہتے ہیں؟"
                    else
                        "Are you sure you want to permanently clear all deleted reminders? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveReminders(reminders.filter { !it.isDeleted })
                        showEmptyTrashConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(if (isUrdu) "صاف کریں" else "Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text(if (isUrdu) "منسوخ" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun CategoryChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    activeColor: Color,
    inactiveBg: Color,
    inactiveText: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeColor else inactiveBg,
        border = BorderStroke(1.dp, if (isSelected) activeColor else inactiveText.copy(alpha = 0.3f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = "$label:",
                fontSize = 9.5.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = if (isSelected) Color.White else inactiveText
            )
            Text(
                text = "$count",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) Color.White else inactiveText
            )
        }
    }
}

@Composable
private fun FilterTabButton(
    label: String,
    count: Int,
    isSelected: Boolean,
    activeBg: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeBg else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, if (isSelected) activeBg else Color(0xFFE2E8F0)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = if (isSelected) Color.White else Slate700
            )
            Text(
                text = "($count)",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White.copy(alpha = 0.9f) else Slate500
            )
        }
    }
}

@Composable
private fun CountBadge(
    label: String,
    count: Int,
    bg: Color,
    text: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = BorderStroke(0.5.dp, text.copy(alpha = 0.3f))
    ) {
        Text(
            text = "$label: $count",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: ShopReminder,
    isUrdu: Boolean,
    onToggleDone: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val cardBg = when {
        reminder.isDeleted -> Color(0xFFFEF2F2)
        reminder.isArchived -> Color(0xFFF8FAFC)
        reminder.isDone -> Color(0xFFECFDF5)
        else -> Color.White
    }

    val cardBorder = when {
        reminder.isDeleted -> Color(0xFFFECDD3)
        reminder.isArchived -> Color(0xFFE2E8F0)
        reminder.isDone -> Color(0xFFA7F3D0)
        else -> Color(0xFFE2E8F0)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (reminder.isDone || reminder.isDeleted || reminder.isArchived) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!reminder.isDeleted) onToggleDone()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Status Checkbox / Radio
                IconButton(
                    onClick = onToggleDone,
                    enabled = !reminder.isDeleted,
                    modifier = Modifier.size(24.dp)
                ) {
                    if (reminder.isDone) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Done",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Pending",
                            tint = Slate400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isUrdu) reminder.titleUrdu else reminder.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                reminder.isDeleted -> Color(0xFFB91C1C)
                                reminder.isDone -> Slate400
                                else -> Slate800
                            },
                            textDecoration = if (reminder.isDone || reminder.isDeleted) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Type Badge
                        TypeBadge(type = reminder.type)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = reminder.date,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate500
                            )
                        }

                        if (reminder.isDone && !reminder.isDeleted) {
                            Surface(
                                color = Color(0xFFD1FAE5),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) "مکمل شدہ" else "Completed",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (reminder.isArchived && !reminder.isDeleted) {
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) "آرکائیو" else "Archived",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (reminder.isDeleted) {
                            Surface(
                                color = Color(0xFFFEE2E2),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isUrdu) "حذف شدہ" else "Deleted",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Right Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (reminder.isDeleted) {
                    IconButton(
                        onClick = onRestore,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFFECFDF5), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Unarchive,
                            contentDescription = "Restore",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onPermanentDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFFFFF1F2), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Delete Forever",
                            tint = Color(0xFFE11D48),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onToggleArchive,
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (reminder.isArchived) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
                                RoundedCornerShape(6.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (reminder.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (reminder.isArchived) "Unarchive" else "Archive",
                            tint = if (reminder.isArchived) Color(0xFF92400E) else Slate500,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Slate400,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: ReminderType) {
    val (bg, border, text) = when (type) {
        ReminderType.TAX -> Triple(Color(0xFFF3E8FF), Color(0xFFE9D5FF), Color(0xFF6B21A8))
        ReminderType.SUPPLIER -> Triple(Color(0xFFFEF3C7), Color(0xFFFDE68A), Color(0xFF92400E))
        ReminderType.UTILITY -> Triple(Color(0xFFDBEAFE), Color(0xFFBFDBFE), Color(0xFF1E40AF))
        ReminderType.OTHER -> Triple(Color(0xFFF1F5F9), Color(0xFFE2E8F0), Color(0xFF334155))
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bg,
        border = BorderStroke(0.5.dp, border)
    ) {
        Text(
            text = type.name,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Black,
            color = text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
