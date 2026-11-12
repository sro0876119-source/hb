package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Customer
import com.example.data.EditLog
import com.example.util.CurrencyFormatter
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AuditChangeComparison(
    val fieldName: String,
    val previousValue: String,
    val newValue: String
)

private fun resolveAuditLogDay(
    log: EditLog,
    customerMapById: Map<Int, Customer>,
    customerMapByName: Map<String, Customer>
): String {
    customerMapById[log.customerId]?.collectionDay?.takeIf { it.isNotBlank() }?.let { return it }
    customerMapByName[log.customerName.trim().lowercase(Locale.US)]?.collectionDay?.takeIf { it.isNotBlank() }?.let { return it }
    if (log.previousDataJson.isNotBlank()) {
        try {
            val json = JSONObject(log.previousDataJson)
            if (json.has("collectionDay")) {
                val d = json.optString("collectionDay", "").trim()
                if (d.isNotBlank()) return d
            }
            if (json.has("customer")) {
                val custObj = json.optJSONObject("customer")
                val d = custObj?.optString("collectionDay", "")?.trim() ?: ""
                if (d.isNotBlank()) return d
            }
        } catch (_: Exception) {}
    }
    return "System"
}

private fun extractAuditComparisons(
    log: EditLog,
    customer: Customer?
): List<AuditChangeComparison> {
    val comparisons = mutableListOf<AuditChangeComparison>()
    val desc = log.actionDescription
    val detailsPart = if (desc.contains(": ")) desc.substringAfter(": ").trim() else desc
    val cleanedFactors = if (detailsPart.startsWith("(") && detailsPart.contains(")")) {
        detailsPart.substringAfter(")").trim()
    } else {
        detailsPart
    }

    // 1. Parse explicit "Field: Old ➔ New" or "Old -> New" transitions from actionDescription
    if (cleanedFactors.contains("➔") || cleanedFactors.contains("->")) {
        val segments = cleanedFactors.split(", ")
        segments.forEach { seg ->
            val arrow = if (seg.contains("➔")) "➔" else if (seg.contains("->")) "->" else null
            if (arrow != null) {
                val fieldLabel = if (seg.contains(":")) seg.substringBefore(":").trim() else "Updated Value"
                val transitionPart = if (seg.contains(":")) seg.substringAfter(":").trim() else seg.trim()
                val oldVal = transitionPart.substringBefore(arrow).trim().ifBlank { "None" }
                val newVal = transitionPart.substringAfter(arrow).trim().ifBlank { "None" }
                comparisons.add(
                    AuditChangeComparison(
                        fieldName = fieldLabel,
                        previousValue = oldVal,
                        newValue = newVal
                    )
                )
            }
        }
    }

    // 2. If no arrow transitions were found, inspect previousDataJson & actionType
    if (comparisons.isEmpty() && log.previousDataJson.isNotBlank() && log.previousDataJson != "{}") {
        try {
            val json = JSONObject(log.previousDataJson)
            when {
                log.actionType.contains("DELETE", ignoreCase = true) -> {
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k in listOf("uuid", "id", "customerId", "loanCycleId", "status", "updatedAt")) continue
                        val v = json.opt(k)?.toString() ?: ""
                        if (v.isNotBlank() && v.length < 120) {
                            comparisons.add(
                                AuditChangeComparison(
                                    fieldName = k.replaceFirstChar { it.uppercase() },
                                    previousValue = v,
                                    newValue = "Deleted / Removed"
                                )
                            )
                        }
                    }
                }
                log.actionType.contains("CREATE", ignoreCase = true) || log.actionType.contains("RECORD", ignoreCase = true) -> {
                    comparisons.add(
                        AuditChangeComparison(
                            fieldName = "Record State",
                            previousValue = "Not Created (None)",
                            newValue = cleanedFactors.ifBlank { log.actionDescription }
                        )
                    )
                }
                else -> {
                    if (json.has("old") || json.has("new")) {
                        comparisons.add(
                            AuditChangeComparison(
                                fieldName = json.optString("key", "Setting / Field"),
                                previousValue = json.optString("old", "Default"),
                                newValue = json.optString("new", cleanedFactors)
                            )
                        )
                    } else if (customer != null) {
                        val prevName = json.optString("name", "")
                        if (prevName.isNotBlank() && prevName != customer.name) {
                            comparisons.add(AuditChangeComparison("Customer Name", prevName, customer.name))
                        }
                        val prevPhone = json.optString("phone", "")
                        if (prevPhone.isNotBlank() && prevPhone != customer.phone) {
                            comparisons.add(AuditChangeComparison("Phone Number", prevPhone, customer.phone))
                        }
                        val prevCity = json.optString("city", "")
                        if (prevCity.isNotBlank() && prevCity != (customer.city ?: "")) {
                            comparisons.add(AuditChangeComparison("City", prevCity, customer.city ?: ""))
                        }
                        val prevDay = json.optString("collectionDay", "")
                        if (prevDay.isNotBlank() && prevDay != customer.collectionDay) {
                            comparisons.add(AuditChangeComparison("Collection Day", prevDay, customer.collectionDay))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // 3. Fallback so every log always shows a clear Previous vs New breakdown
    if (comparisons.isEmpty()) {
        val isCreate = log.actionType.contains("CREATE", ignoreCase = true) || log.actionType.contains("RECORD", ignoreCase = true) || log.actionType.contains("ADD", ignoreCase = true)
        val isDelete = log.actionType.contains("DELETE", ignoreCase = true) || log.actionType.contains("REMOVE", ignoreCase = true)
        comparisons.add(
            AuditChangeComparison(
                fieldName = log.actionType.replace("_", " "),
                previousValue = when {
                    isCreate -> "None (New Entry)"
                    isDelete -> cleanedFactors.ifBlank { log.actionDescription }
                    else -> "Previous State"
                },
                newValue = when {
                    isDelete -> "Deleted / Removed"
                    else -> cleanedFactors.ifBlank { log.actionDescription }
                }
            )
        )
    }

    return comparisons
}

@Composable
fun HistoryScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(16.dp),
    filterCustomerId: Int? = null
) {
    val appColors = LocalAppThemeColors.current
    val editLogs by viewModel.allEditLogs.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val collectionGroups by viewModel.collectionGroups.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val unsyncedUuids by viewModel.unsyncedEditUuids.collectAsStateWithLifecycle()
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    if (filterCustomerId != null) {
        BackHandler {
            viewModel.navigateBack()
        }
    }

    val customerMapById = remember(allCustomers) { allCustomers.associateBy { it.id } }
    val customerMapByName = remember(allCustomers) {
        allCustomers.associateBy { it.name.trim().lowercase(Locale.US) }
    }

    val targetCustomer = remember(allCustomers, filterCustomerId) {
        filterCustomerId?.let { id -> customerMapById[id] }
    }

    var restrictToCustomer by remember(filterCustomerId) { mutableStateOf(filterCustomerId != null) }
    var selectedDayFilter by remember { mutableStateOf("All Days") }
    var dayDropdownExpanded by remember { mutableStateOf(false) }
    var selectedLogForMoreInfo by remember { mutableStateOf<EditLog?>(null) }

    val availableDayFilters = remember(collectionGroups) {
        val days = getOrderedDaysList(collectionGroups).filter {
            !it.equals("Friday", ignoreCase = true) && !it.equals("Home", ignoreCase = true)
        }
        listOf("All Days") + days
    }

    val filteredEditLogs = remember(editLogs, restrictToCustomer, filterCustomerId, targetCustomer, selectedDayFilter, customerMapById, customerMapByName) {
        editLogs.filter { log ->
            val matchesCustomer = if (restrictToCustomer && filterCustomerId != null) {
                log.customerId == filterCustomerId ||
                    (targetCustomer != null && log.customerName.trim().equals(targetCustomer.name.trim(), ignoreCase = true))
            } else {
                true
            }
            val matchesDay = if (selectedDayFilter == "All Days") {
                true
            } else {
                val logDay = resolveAuditLogDay(log, customerMapById, customerMapByName)
                logDay.trim().equals(selectedDayFilter.trim(), ignoreCase = true)
            }
            matchesCustomer && matchesDay
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // If opened from a specific Customer's Dashboard, show Customer Filter Banner
        if (filterCustomerId != null && targetCustomer != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.primaryAccent.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, appColors.primaryAccent.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = appColors.primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (restrictToCustomer) "Customer Audit Log: ${targetCustomer.name}" else "Showing All Customers' Audit Logs",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorSlateDark
                            )
                            Text(
                                text = "Route Group: ${targetCustomer.collectionDay} • Read-Only Audit Record",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                    Surface(
                        onClick = { restrictToCustomer = !restrictToCustomer },
                        shape = RoundedCornerShape(16.dp),
                        color = if (restrictToCustomer) appColors.primaryAccent else Color.White,
                        border = BorderStroke(1.dp, appColors.primaryAccent)
                    ) {
                        Text(
                            text = if (restrictToCustomer) "Show All" else "Only ${targetCustomer.name.take(10)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (restrictToCustomer) Color.White else appColors.primaryAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Top Header + Day-Wise Filter Dropdown Row (Read-Only: No Clear/Delete/Edit options)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = translate("System Audit Logs", language),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "${filteredEditLogs.size} ${translate("records", language)} • ${translate("Read-Only Verification", language)}",
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
            }

            // Day Filter Dropdown (All Days, Monday, Tuesday, etc.)
            Box {
                Surface(
                    onClick = { dayDropdownExpanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, appColors.primaryAccent),
                    modifier = Modifier.testTag("audit_day_filter_dropdown")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter by Day",
                            tint = appColors.primaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = translate(selectedDayFilter, language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = appColors.primaryAccent
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = appColors.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = dayDropdownExpanded,
                    onDismissRequest = { dayDropdownExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    availableDayFilters.forEach { dayOption ->
                        val isSelected = selectedDayFilter.equals(dayOption, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = translate(dayOption, language),
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) appColors.primaryAccent else Color.Black
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (dayOption == "All Days") Icons.Default.CalendarMonth else Icons.Default.Event,
                                    contentDescription = null,
                                    tint = if (isSelected) appColors.primaryAccent else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                selectedDayFilter = dayOption
                                dayDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (filteredEditLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Text(
                        text = translate("No audit logs found for this filter.", language),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(filteredEditLogs, key = { _, it -> it.id }) { idx, log ->
                    val matchedCustomer = customerMapById[log.customerId]
                        ?: customerMapByName[log.customerName.trim().lowercase(Locale.US)]
                    val logDay = remember(log, customerMapById, customerMapByName) {
                        resolveAuditLogDay(log, customerMapById, customerMapByName)
                    }
                    val placeLabel = remember(log, matchedCustomer, logDay) {
                        if (matchedCustomer != null) {
                            "${matchedCustomer.name} (${logDay})"
                        } else if (log.customerName.isNotBlank() && log.customerName != "System") {
                            if (logDay != "System") "${log.customerName} ($logDay)" else log.customerName
                        } else {
                            "System Settings"
                        }
                    }

                    val cardBgColor = when {
                        log.actionType.contains("DELETE") -> Color(0xFFFEF2F2)
                        log.actionType.contains("CREATE") || log.actionType.contains("RECORD") -> Color(0xFFF0FDF4)
                        else -> Color(0xFFEFF6FF)
                    }

                    val accentColor = when {
                        log.actionType.contains("DELETE") -> Color(0xFFEF4444)
                        log.actionType.contains("CREATE") || log.actionType.contains("RECORD") -> Color(0xFF22C55E)
                        else -> Color(0xFF3B82F6)
                    }

                    val icon = when {
                        log.actionType.contains("DELETE") -> Icons.Default.DeleteOutline
                        log.actionType.contains("CREATE") || log.actionType.contains("RECORD") -> Icons.Default.CheckCircle
                        else -> Icons.Default.EditNote
                    }

                    AnimatedSlideInItem(index = idx) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLogForMoreInfo = log },
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val parts = log.actionDescription.split(": ", limit = 2)

                                // Top Row: Icon + What Changed Title + Place Badge (No edit/delete/revert buttons)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = parts[0],
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black,
                                                lineHeight = 17.sp
                                            )
                                            Text(
                                                text = "Place: $placeLabel",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                    }

                                    if (logDay != "System") {
                                        Box(
                                            modifier = Modifier
                                                .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = logDay,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accentColor
                                            )
                                        }
                                    }
                                }

                                // Middle Section: Summary of what changed
                                if (parts.size > 1) {
                                    val changeInfo = parts[1]
                                    val factorsListString = changeInfo.substringAfter(")").trim()
                                    val factorCount = changeInfo.substringBefore(")", "0").replace("(", "").trim()

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        if (factorsListString.isNotEmpty() && changeInfo.startsWith("(")) {
                                            Text(
                                                text = "($factorCount)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = accentColor
                                            )
                                            factorsListString.split(", ").forEach { factor ->
                                                Row(
                                                    verticalAlignment = Alignment.Top,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "•",
                                                        fontSize = 12.sp,
                                                        color = accentColor,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = factor,
                                                        fontSize = 12.sp,
                                                        color = Color.DarkGray,
                                                        fontWeight = FontWeight.Medium,
                                                        lineHeight = 16.sp,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = changeInfo,
                                                fontSize = 12.sp,
                                                color = Color.DarkGray,
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                // Bottom Row: Sync Status + Timestamp on Left, "More Info" Button on Right
                                val isUnsynced = log.uuid in unsyncedUuids
                                val statusDotColor = when {
                                    isUnsynced -> Color(0xFFEF4444)
                                    log.customerName == "Firebase Sync" || log.actionType == "SYNC_VERIFY" -> Color.Gray
                                    else -> Color(0xFF22C55E)
                                }
                                val statusText = when {
                                    isUnsynced -> translate("Pending Sync", language)
                                    log.customerName == "Firebase Sync" || log.actionType == "SYNC_VERIFY" -> translate("System Sync", language)
                                    else -> translate("Synced", language)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(statusDotColor, shape = CircleShape)
                                        )
                                        Text(
                                            text = "${sdf.format(java.util.Date(log.timestamp))} • $statusText",
                                            fontSize = 10.sp,
                                            color = Color.DarkGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        onClick = { selectedLogForMoreInfo = log },
                                        shape = RoundedCornerShape(8.dp),
                                        color = appColors.primaryAccent.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, appColors.primaryAccent.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "More Info",
                                                tint = appColors.primaryAccent,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = translate("More Info", language),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appColors.primaryAccent
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
    }

    // "More Info" Dialog: Shows What Changed, At What Place, and Previous Value vs New Value comparison
    if (selectedLogForMoreInfo != null) {
        val log = selectedLogForMoreInfo!!
        val matchedCustomer = customerMapById[log.customerId]
            ?: customerMapByName[log.customerName.trim().lowercase(Locale.US)]
        val logDay = resolveAuditLogDay(log, customerMapById, customerMapByName)
        val comparisons = remember(log, matchedCustomer) {
            extractAuditComparisons(log, matchedCustomer)
        }

        AlertDialog(
            onDismissRequest = { selectedLogForMoreInfo = null },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FactCheck,
                        contentDescription = null,
                        tint = appColors.primaryAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = translate("Audit Log Details", language),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Text(
                            text = sdf.format(java.util.Date(log.timestamp)),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Metadata Card: Place & Action Type
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Place: ${matchedCustomer?.name ?: log.customerName.ifBlank { "System" }} (${logDay})",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorSlateDark
                            )
                            Text(
                                text = "Action: ${log.actionType.replace("_", " ")}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = appColors.primaryAccent
                            )
                            Text(
                                text = log.actionDescription,
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }

                    Text(
                        text = translate("Previous Value vs New Value:", language),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ColorSlateDark
                    )

                    comparisons.forEach { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.fieldName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ColorSlateDark
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Previous Thing Box
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = translate("Previous", language).uppercase(),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFDC2626)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.previousValue,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF7F1D1D)
                                        )
                                    }

                                    // New Thing Box
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = translate("New", language).uppercase(),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF16A34A)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.newValue,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF14532D)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (matchedCustomer != null && filterCustomerId == null) {
                        OutlinedButton(
                            onClick = {
                                val cid = matchedCustomer.id
                                selectedLogForMoreInfo = null
                                viewModel.navigateTo(Screen.CustomerDetail(cid))
                            }
                        ) {
                            Text(translate("View Profile", language), color = appColors.primaryAccent)
                        }
                    }
                    Button(
                        onClick = { selectedLogForMoreInfo = null },
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.primaryAccent)
                    ) {
                        Text(translate("Close", language), color = Color.White)
                    }
                }
            }
        )
    }
}



data class DailyStats(
    val dateLabel: String,
    val collection: Double,
    val dueCreated: Double,
    val interestCollected: Double
)

@Composable
fun FullLedgerHistoryScreen(viewModel: FinanceViewModel) {
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val allLoanCycles by viewModel.allLoanCycles.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val appColors = LocalAppThemeColors.current

    var hideEmptyDays by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Support Days, Months, Year format, with Date Range always visible!
    var activeFilter by remember { mutableStateOf("30D") } // "30D", "3M", "6M", "1Y"
    var customFromDate by remember {
        mutableStateOf(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.run {
                SimpleDateFormat("dd/MM/yyyy", Locale.US).format(time)
            }
        )
    }
    var customToDate by remember {
        mutableStateOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.US).format(java.util.Date())
        )
    }

    // Cache computed list for performance based strictly on customFromDate & customToDate
    val rawDailyStatsList = remember(allPayments, allLoanCycles, language, customFromDate, customToDate) {
        val list = mutableListOf<DailyStats>()
        val currentLocale = when(language) {
            "Tamil" -> Locale("ta")
            "Hindi" -> Locale("hi", "IN")
            "Telugu" -> Locale("te", "IN")
            else -> Locale.US
        }
        val sdf = SimpleDateFormat("dd-MMM-yyyy", currentLocale)
        val dayFormat = SimpleDateFormat("EEEE", currentLocale)
        val loanMap = allLoanCycles.associateBy { it.id }
        
        val sdfParser = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val fromCal = Calendar.getInstance()
        val toCal = Calendar.getInstance()
        try {
            sdfParser.parse(customFromDate)?.let { fromCal.time = it }
        } catch (e: Exception) {
            fromCal.add(Calendar.DAY_OF_YEAR, -30)
        }
        try {
            sdfParser.parse(customToDate)?.let { toCal.time = it }
        } catch (e: Exception) {}
        
        // Normalize times to capture full day boundaries
        fromCal.set(Calendar.HOUR_OF_DAY, 0)
        fromCal.set(Calendar.MINUTE, 0)
        fromCal.set(Calendar.SECOND, 0)
        fromCal.set(Calendar.MILLISECOND, 0)
        
        toCal.set(Calendar.HOUR_OF_DAY, 23)
        toCal.set(Calendar.MINUTE, 59)
        toCal.set(Calendar.SECOND, 59)
        toCal.set(Calendar.MILLISECOND, 999)
        
        val tempCal = Calendar.getInstance().apply { timeInMillis = toCal.timeInMillis }
        while (tempCal.timeInMillis >= fromCal.timeInMillis) {
            val startMs = Calendar.getInstance().apply {
                timeInMillis = tempCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val endMs = Calendar.getInstance().apply {
                timeInMillis = tempCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
            
            val dayPayments = allPayments.filter { it.paymentDate in startMs..endMs && it.status.uppercase() != "DELETED" }
            val dayPaymentsSum = dayPayments.sumOf { it.amountPaid }
            val dayLoanSum = allLoanCycles.filter { it.startDate in startMs..endMs && it.status.uppercase() != "DELETED" }.sumOf { it.loanAmount - it.deduction }
            val dayInterestSum = dayPayments.sumOf { p ->
                val loan = loanMap[p.loanCycleId]
                if (loan != null && loan.status.uppercase() != "DELETED") {
                    val total = loan.loanAmount + loan.interestAmount
                    val ratio = if (total > 0.0) loan.interestAmount / total else 0.0
                    p.amountPaid * ratio
                } else {
                    0.0
                }
            }
            
            val dateLabel = "${sdf.format(tempCal.time)} (${dayFormat.format(tempCal.time)})"
            list.add(DailyStats(dateLabel, dayPaymentsSum, dayLoanSum, dayInterestSum))
            
            tempCal.add(Calendar.DAY_OF_YEAR, -1)
            if (list.size > 2000) break // safety guard
        }
        list
    }

    val filteredList = remember(rawDailyStatsList, hideEmptyDays, searchQuery) {
        rawDailyStatsList.filter { stats ->
            // Search filter
            val matchesSearch = searchQuery.isBlank() || stats.dateLabel.contains(searchQuery, ignoreCase = true)
            // Empty filter
            val matchesEmpty = !hideEmptyDays || (stats.collection > 0 || stats.dueCreated > 0 || stats.interestCollected > 0)
            matchesSearch && matchesEmpty
        }
    }

    val totalCollectionsSum = remember(filteredList) { filteredList.sumOf { it.collection } }
    val totalDuesCreatedSum = remember(filteredList) { filteredList.sumOf { it.dueCreated } }
    val totalInterestSum = remember(filteredList) { filteredList.sumOf { it.interestCollected } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Range & Hide Switch Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Days / Months / Year limit selector in a scrollable container
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                val filterOptions = listOf(
                    Triple("30D", translate("30 Days", language), "30D"),
                    Triple("3M", translate("3 Months", language), "3M"),
                    Triple("6M", translate("6 Months", language), "6M"),
                    Triple("1Y", translate("1 Year", language), "1Y")
                )
                
                filterOptions.forEach { opt ->
                    val isSelected = activeFilter == opt.first
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) appColors.primaryAccent else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                val sdfParser = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                                val cal = Calendar.getInstance()
                                val toStr = sdfParser.format(cal.time)
                                when (opt.first) {
                                    "30D" -> cal.add(Calendar.DAY_OF_YEAR, -30)
                                    "3M" -> cal.add(Calendar.MONTH, -3)
                                    "6M" -> cal.add(Calendar.MONTH, -6)
                                    "1Y" -> cal.add(Calendar.YEAR, -1)
                                }
                                val fromStr = sdfParser.format(cal.time)
                                customFromDate = fromStr
                                customToDate = toStr
                                activeFilter = opt.first
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = opt.second,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Hide Empty Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = translate("Hide Empty", language),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black.copy(alpha = 0.7f)
                )
                Switch(
                    checked = hideEmptyDays,
                    onCheckedChange = { hideEmptyDays = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = appColors.primaryAccent,
                        checkedTrackColor = appColors.primaryAccent.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.scale(0.75f)
                )
            }
        }

        // Custom Selection Date Pickers - ALWAYS VISIBLE NOW
        val context = LocalContext.current
        var showFromDatePickerState by remember { mutableStateOf(false) }
        var showToDatePickerState by remember { mutableStateOf(false) }

        val fromMills = remember(customFromDate) {
            try {
                SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(customFromDate)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
        val toMills = remember(customToDate) {
            try {
                SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(customToDate)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { showFromDatePickerState = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primaryAccent),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = translate("From Date", language),
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = customFromDate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            OutlinedButton(
                onClick = { showToDatePickerState = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primaryAccent),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = translate("To Date", language),
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = customToDate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        if (showFromDatePickerState) {
            com.example.ui.components.AdvancedDatePickerDialog(
                initialTimeMs = fromMills,
                onDismissRequest = { showFromDatePickerState = false },
                onDateSelected = { selectedTimeMs ->
                    val formatted = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(java.util.Date(selectedTimeMs))
                    customFromDate = formatted
                    activeFilter = ""
                    showFromDatePickerState = false
                }
            )
        }

        if (showToDatePickerState) {
            com.example.ui.components.AdvancedDatePickerDialog(
                initialTimeMs = toMills,
                onDismissRequest = { showToDatePickerState = false },
                onDateSelected = { selectedTimeMs ->
                    val formatted = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(java.util.Date(selectedTimeMs))
                    customToDate = formatted
                    activeFilter = ""
                    showToDatePickerState = false
                }
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    translate("Search date or day name...", language),
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = appColors.primaryAccent,
                unfocusedBorderColor = Color(0xFFCBD5E1)
            )
        )

        // Summary Statistics Box
        Card(
            colors = CardDefaults.cardColors(containerColor = appColors.darkBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translate("Total Collections", language),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "₹${CurrencyFormatter.format(totalCollectionsSum)}",
                        color = Color(0xFF4ADE80), // Light green
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(35.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = translate("Dues", language),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "₹${CurrencyFormatter.format(totalDuesCreatedSum)}",
                        color = Color(0xFF60A5FA), // Light blue
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(35.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = translate("Total Profit", language),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "₹${CurrencyFormatter.format(totalInterestSum)}",
                        color = Color(0xFFC084FC), // Light purple
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        // Daily Ledger Table
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translate("Date / Day", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = translate("Collect", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF15803D),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = translate("Given", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF1D4ED8),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = translate("Profit", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF7E22CE),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.9f)
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = translate("No daily records matched.", language),
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(filteredList) { idx, stats ->
                            AnimatedSlideInItem(index = idx) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (idx % 2 == 1) Color(0xFFF8FAFC) else Color.White)
                                            .padding(horizontal = 14.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stats.dateLabel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Black,
                                            modifier = Modifier.weight(1.3f)
                                        )
                                        Text(
                                            text = if (stats.collection > 0) "₹${CurrencyFormatter.format(stats.collection)}" else "0",
                                            fontSize = 12.sp,
                                            fontWeight = if (stats.collection > 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (stats.collection > 0) Color(0xFF16A34A) else Color.Gray,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (stats.dueCreated > 0) "₹${CurrencyFormatter.format(stats.dueCreated)}" else "0",
                                            fontSize = 12.sp,
                                            fontWeight = if (stats.dueCreated > 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (stats.dueCreated > 0) Color(0xFF2563EB) else Color.Gray,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (stats.interestCollected > 0) "₹${CurrencyFormatter.format(stats.interestCollected)}" else "0",
                                            fontSize = 12.sp,
                                            fontWeight = if (stats.interestCollected > 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (stats.interestCollected > 0) Color(0xFF9333EA) else Color.Gray,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(0.9f)
                                        )
                                    }
                                    if (idx < filteredList.size - 1) {
                                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
