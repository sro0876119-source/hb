package com.example.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.*
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

object CsvBackupHelper {

    private fun newDateFormat(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun newDateTimeFormat(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    val DEFAULT_ACTIVE_DAY_GROUPS = listOf(
        "Monday",
        "Tuesday",
        "Wednesday",
        "Thursday",
        "Saturday",
        "Sunday mrg",
        "Sunday eve"
    )

    /**
     * Sanitizes any string coming from CSV/Excel so binary garbage or control chars never enter SQLite.
     */
    fun sanitizeText(raw: String?, maxLength: Int = 250): String {
        if (raw.isNullOrBlank()) return ""
        val sb = StringBuilder(minOf(raw.length, maxLength))
        for (i in 0 until raw.length) {
            val c = raw[i]
            if (c == '\uFEFF' || c == '\u0000' || c == '\uFFFD') continue
            if (c < ' ' && c != '\n' && c != '\t') continue
            sb.append(if (c == '\t' || c == '\n' || c == '\r') ' ' else c)
            if (sb.length >= maxLength) break
        }
        return sb.toString().trim()
    }

    /**
     * Checks if a string decoded from bytes is actually binary/corrupted data (e.g. raw ZIP/XLSX decoded as UTF-8).
     */
    fun isBinaryOrCorruptedText(text: String): Boolean {
        if (text.isEmpty()) return false
        val stripped = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (stripped.startsWith("PK\u0003\u0004") || stripped.startsWith("PK\u0005\u0006")) {
            return true
        }
        val sampleLen = minOf(text.length, 4096)
        var nulCount = 0
        var replacementCount = 0
        var controlCount = 0
        for (i in 0 until sampleLen) {
            val c = text[i]
            if (c == '\u0000') nulCount++
            else if (c == '\uFFFD') replacementCount++
            else if (c.code < 32 && c != '\n' && c != '\r' && c != '\t') controlCount++
        }
        return nulCount > 0 || replacementCount > 10 || controlCount > 20
    }

    /**
     * Normalizes a day group name while strictly respecting AGENTS.md rules:
     * - "Friday" is returned as "Friday" so callers can explicitly skip/exclude it.
     * - "Sunday mrg" and "Sunday eve" are preserved as distinct active groups.
     */
    fun normalizeDayGroup(raw: String?, fallback: String = "Monday"): String {
        val cleaned = sanitizeText(raw, 60)
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
        if (cleaned.isBlank()) return fallback

        return when (cleaned.lowercase(Locale.US)) {
            "monday", "mon" -> "Monday"
            "tuesday", "tueasday", "tues", "tue" -> "Tuesday"
            "wednesday", "wed" -> "Wednesday"
            "thursday", "thur", "thurs", "thu" -> "Thursday"
            "friday", "fri" -> "Friday"
            "saturday", "sat" -> "Saturday"
            "sunday mrg", "sunday morning", "sun mrg", "sun morning", "sunday_mrg", "sunday" -> "Sunday mrg"
            "sunday eve", "sunday evening", "sun eve", "sun evening", "sunday_eve" -> "Sunday eve"
            else -> {
                val matchedDefault = DEFAULT_ACTIVE_DAY_GROUPS.find { it.equals(cleaned, ignoreCase = true) }
                if (matchedDefault != null) {
                    matchedDefault
                } else if (cleaned.length <= 30 && cleaned.any { it.isLetter() } && !cleaned.contains("http", ignoreCase = true) && !cleaned.contains(":")) {
                    cleaned
                } else {
                    fallback
                }
            }
        }
    }

    /**
     * Escapes standard CSV values in accordance with RFC-4180.
     */
    private fun escapeCsv(value: String): String {
        val clean = sanitizeText(value, 500)
        if (clean.contains(",") || clean.contains("\"") || clean.contains("\n") || clean.contains("\r")) {
            return "\"" + clean.replace("\"", "\"\"") + "\""
        }
        return clean
    }

    /**
     * Generates a CSV file content for a list of customers belonging to a specific collection day.
     * If dayFilter is "ALL" (case-insensitive), it exports all customers (excluding Friday).
     */
    fun generateCsvString(
        customers: List<Customer>,
        loanCycles: List<LoanCycle>,
        payments: List<WeeklyPayment>,
        dayFilter: String,
        cashBalanceLogs: List<com.example.data.CashBalanceLog> = emptyList()
    ): String {
        val df = newDateFormat()
        val dtf = newDateTimeFormat()

        val activeCustomers = customers.filter { it.status.uppercase(Locale.US) != "DELETED" }
        val validLoanCycles = loanCycles.filter { it.status.uppercase(Locale.US) != "DELETED" }
        val validPayments = payments.filter { it.status.uppercase(Locale.US) != "DELETED" }

        val filteredCustomers = if (dayFilter.trim().equals("ALL", ignoreCase = true)) {
            activeCustomers.filter { !it.collectionDay.trim().equals("Friday", ignoreCase = true) }
        } else {
            activeCustomers.filter { it.collectionDay.trim().equals(dayFilter.trim(), ignoreCase = true) }
        }

        val groupCustomerIds = filteredCustomers.map { it.id }.toSet()
        val totalActiveLoans = validLoanCycles.count { it.customerId in groupCustomerIds && it.status == "ACTIVE" }

        val s = StringBuilder()

        s.append("# Total Customers: ${filteredCustomers.size}, Total Active Loans: $totalActiveLoans\n")
        s.append("# Exported On: ${dtf.format(Date())}\n")

        // 1. Title / Header following mandatory schema rules (No UPI handle column)
        s.append("Customer UUID,Route No (Sort Order),Client Name,Phone Number,City,SMS Settings (Weekly & Entry Confirmation),Loan ID (UUID),Amount Disbursed,Principle (₹),Interest (₹),Date of Dispersal")
        for (w in 1..30) {
            s.append(",Week $w Date & Time,Week $w Amt Received")
        }
        s.append(",Collection Day\n")

        val loansByCustomer = validLoanCycles.groupBy { it.customerId }
        val paymentsByLoan = validPayments.groupBy { it.loanCycleId }

        for (cust in filteredCustomers) {
            val safeUuid = escapeCsv(cust.uuid)
            val customOrder = cust.customOrder.toString()
            val safeName = escapeCsv(cust.name)
            val phoneVal = if (cust.phone2.isNotBlank()) "${cust.phone} / ${cust.phone2}" else cust.phone
            val safePhone = escapeCsv(phoneVal)
            val safeCity = escapeCsv(cust.city)
            val smsSettings = "Weekly Reminder: ${if (cust.smsWeeklyReminder) "YES" else "NO"}, Entry Confirmation: ${if (cust.smsConfirmationOfEntry) "YES" else "NO"}, Auto Weekly SMS: ${if (cust.autoWeeklySms) "YES" else "NO"}, Auto Weekly Whatsapp: ${if (cust.autoWeeklyWhatsapp) "YES" else "NO"}, UPI Name Alias: ${cust.upiNameAlias}, Preferred Language: ${cust.preferredLanguage}"
            val safeSmsSettings = escapeCsv(smsSettings)

            val custCycles = loansByCustomer[cust.id]?.sortedByDescending { it.id } ?: emptyList()

            if (custCycles.isEmpty()) {
                s.append("$safeUuid,$customOrder,$safeName,$safePhone,$safeCity,$safeSmsSettings,")
                s.append(",,,,")
                for (w in 1..30) {
                    s.append(",,")
                }
                s.append(",${escapeCsv(cust.collectionDay)}\n")
            } else {
                for (cycle in custCycles) {
                    val loanUuid = cycle.uuid
                    val amountDisbursed = (cycle.loanAmount - cycle.deduction).toString()
                    val principle = cycle.loanAmount.toString()
                    val interest = cycle.interestAmount.toString()

                    var dispersalDate = try {
                        df.format(Date(cycle.startDate))
                    } catch (_: Exception) {
                        df.format(Date())
                    }
                    val loanMeta = mutableListOf<String>()
                    if (cycle.notes.isNotBlank()) {
                        loanMeta.add("NOTES: ${sanitizeText(cycle.notes, 120)}")
                    }
                    if (cycle.status.isNotBlank()) {
                        loanMeta.add("STATUS: ${sanitizeText(cycle.status, 30)}")
                    }
                    if (loanMeta.isNotEmpty()) {
                        dispersalDate += " " + loanMeta.joinToString(" | ", prefix = "[", postfix = "]")
                    }

                    s.append("$safeUuid,$customOrder,$safeName,$safePhone,$safeCity,$safeSmsSettings,")
                    s.append("${escapeCsv(loanUuid)},$amountDisbursed,$principle,$interest,${escapeCsv(dispersalDate)}")

                    val cyclePayments = paymentsByLoan[cycle.id] ?: emptyList()

                    for (w in 1..30) {
                        val p = cyclePayments.find { it.weekNumber == w && it.amountPaid > 0.0 && it.status.uppercase(Locale.US) != "DELETED" }
                        if (p != null) {
                            var pDate = try {
                                dtf.format(Date(p.paymentDate))
                            } catch (_: Exception) {
                                dtf.format(Date())
                            }
                            val metaList = mutableListOf<String>()
                            if (!p.upiTxnId.isNullOrBlank()) {
                                metaList.add("TXN: ${sanitizeText(p.upiTxnId, 80)}")
                            }
                            if (p.notes.isNotBlank()) {
                                metaList.add("NOTES: ${sanitizeText(p.notes, 120)}")
                            }
                            if (metaList.isNotEmpty()) {
                                pDate += " " + metaList.joinToString(" | ", prefix = "[", postfix = "]")
                            }
                            val pAmt = p.amountPaid.toString()
                            s.append(",${escapeCsv(pDate)},$pAmt")
                        } else {
                            s.append(",,")
                        }
                    }
                    s.append(",${escapeCsv(cust.collectionDay)}\n")
                }
            }
        }

        if (cashBalanceLogs.isNotEmpty()) {
            s.append("\n---CASH_BALANCE_LOGS---\n")
            s.append("ID,Date,Actual Cash,System Cash,Collection Amount,Disbursal Amount,Expenses\n")
            for (log in cashBalanceLogs) {
                s.append("${log.id},${log.date},${log.actualCash},${log.systemCash},${log.collectionAmount},${log.disbursalAmount},${log.expenses}\n")
            }
        }

        return s.toString()
    }

    /**
     * Robust RFC-4180 parser to split entries correctly. Supports both comma and tab delimiters.
     */
    fun parseCsvRows(csvText: String): List<List<String>> {
        val cleanedText = csvText.removePrefix("\uFEFF")
        val result = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < cleanedText.length) {
            val c = cleanedText[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < cleanedText.length && cleanedText[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(currentField.toString())
                        currentField = StringBuilder()
                    }
                    '\n', '\r' -> {
                        currentRow.add(currentField.toString())
                        if (currentRow.isNotEmpty() && currentRow.any { it.isNotBlank() }) {
                            result.add(currentRow.toList())
                        }
                        currentRow.clear()
                        currentField = StringBuilder()
                        if (c == '\r' && i + 1 < cleanedText.length && cleanedText[i + 1] == '\n') {
                            i++
                        }
                    }
                    else -> currentField.append(c)
                }
            }
            i++
        }
        if (currentRow.isNotEmpty() || currentField.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.isNotEmpty() && currentRow.any { it.isNotBlank() }) {
                result.add(currentRow.toList())
            }
        }
        return result
    }

    /**
     * Reads a backup from a Uri (supporting both standard .csv text files and .xlsx Excel workbooks)
     * and imports it into Room safely.
     */
    suspend fun importBackupFromUri(
        context: Context,
        uri: Uri,
        dayGroup: String,
        db: AppDatabase
    ): Boolean {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not read the selected file.")
        return importBackupFromBytes(context, bytes, dayGroup, db)
    }

    /**
     * Reads a backup from raw bytes (automatically detecting .xlsx ZIP archives vs UTF-8 CSV)
     * and imports it into Room safely.
     */
    suspend fun importBackupFromBytes(
        context: Context,
        bytes: ByteArray,
        dayGroup: String,
        db: AppDatabase
    ): Boolean {
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("Selected backup file is empty.")
        }
        // Detect ZIP / .xlsx header (PK\x03\x04)
        val isZipHeader = bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            (bytes[2] == 3.toByte() || bytes[2] == 5.toByte())

        val csvText = if (isZipHeader) {
            extractCsvFromXlsxBytes(bytes)
        } else {
            val decoded = String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
            if (isBinaryOrCorruptedText(decoded)) {
                throw IllegalArgumentException("The selected file is binary or corrupted. Please select a valid CSV or Excel (.xlsx) file.")
            }
            decoded
        }

        return importCsvIntoDay(context, csvText, dayGroup, db)
    }

    /**
     * Extracts all non-Friday sheets from an .xlsx workbook into a multi-group CSV string
     * using === DAY_GROUP: <SheetName> === section headers.
     */
    fun extractCsvFromXlsxBytes(bytes: ByteArray): String {
        val zipEntries = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        zipEntries[entry.name] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid or corrupted Excel (.xlsx) file: ${e.localizedMessage}", e)
        }

        if (zipEntries.isEmpty() || !zipEntries.keys.any { it.startsWith("xl/") }) {
            throw IllegalArgumentException("Selected archive is not a valid Excel (.xlsx) workbook.")
        }

        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }

        // 1. Parse xl/sharedStrings.xml if present
        val sharedStrings = mutableListOf<String>()
        zipEntries["xl/sharedStrings.xml"]?.let { ssBytes ->
            try {
                val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(ssBytes))
                val siNodes = doc.getElementsByTagName("si")
                for (i in 0 until siNodes.length) {
                    val si = siNodes.item(i)
                    sharedStrings.add(extractTextFromXmlNode(si))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Parse xl/_rels/workbook.xml.rels to map r:id -> target path
        val relsMap = mutableMapOf<String, String>()
        zipEntries["xl/_rels/workbook.xml.rels"]?.let { relBytes ->
            try {
                val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(relBytes))
                val relNodes = doc.getElementsByTagName("Relationship")
                for (i in 0 until relNodes.length) {
                    val el = relNodes.item(i) as? Element ?: continue
                    val id = el.getAttribute("Id")
                    val target = el.getAttribute("Target").removePrefix("/")
                    if (id.isNotBlank() && target.isNotBlank()) {
                        val normalizedTarget = if (target.startsWith("xl/")) target else "xl/$target"
                        relsMap[id] = normalizedTarget
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Parse xl/workbook.xml to get ordered sheet names and their targets
        val sheetsToParse = mutableListOf<Pair<String, String>>()
        zipEntries["xl/workbook.xml"]?.let { wbBytes ->
            try {
                val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(wbBytes))
                val sheetNodes = doc.getElementsByTagName("sheet")
                for (i in 0 until sheetNodes.length) {
                    val el = sheetNodes.item(i) as? Element ?: continue
                    val name = el.getAttribute("name").trim()
                    val rId = el.getAttribute("r:id").ifBlank { el.getAttribute("id") }
                    val sheetId = el.getAttribute("sheetId").ifBlank { (i + 1).toString() }
                    val targetPath = relsMap[rId]
                        ?: "xl/worksheets/sheet$sheetId.xml".takeIf { zipEntries.containsKey(it) }
                        ?: "xl/worksheets/sheet${i + 1}.xml"
                    if (name.isNotBlank() && zipEntries.containsKey(targetPath)) {
                        sheetsToParse.add(name to targetPath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (sheetsToParse.isEmpty()) {
            zipEntries.keys
                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sorted()
                .forEachIndexed { idx, path ->
                    val defaultName = DEFAULT_ACTIVE_DAY_GROUPS.getOrElse(idx) { "Monday" }
                    sheetsToParse.add(defaultName to path)
                }
        }

        val csvBuilder = StringBuilder()
        for ((sheetName, sheetPath) in sheetsToParse) {
            if (sheetName.trim().equals("Friday", ignoreCase = true)) continue
            val sheetBytes = zipEntries[sheetPath] ?: continue
            val normalizedGroup = normalizeDayGroup(sheetName, sheetName)
            if (normalizedGroup.equals("Friday", ignoreCase = true)) continue

            try {
                val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(sheetBytes))
                val rowNodes = doc.getElementsByTagName("row")
                if (rowNodes.length == 0) continue

                csvBuilder.append("=== DAY_GROUP: ").append(normalizedGroup).append(" ===\n")
                for (rIdx in 0 until rowNodes.length) {
                    val rowEl = rowNodes.item(rIdx) as? Element ?: continue
                    val cellNodes = rowEl.getElementsByTagName("c")
                    if (cellNodes.length == 0) continue

                    val rowCells = mutableMapOf<Int, String>()
                    var maxColIdx = -1
                    var sequentialCol = 0

                    for (cIdx in 0 until cellNodes.length) {
                        val cellEl = cellNodes.item(cIdx) as? Element ?: continue
                        val ref = cellEl.getAttribute("r")
                        val colIdx = if (ref.isNotBlank()) excelCellRefToColIndex(ref) else sequentialCol
                        sequentialCol = colIdx + 1
                        if (colIdx > maxColIdx) maxColIdx = colIdx

                        val cellType = cellEl.getAttribute("t")
                        val cellValue = when (cellType) {
                            "s" -> {
                                val vText = getFirstChildTagText(cellEl, "v").trim()
                                val ssIdx = vText.toIntOrNull()
                                if (ssIdx != null && ssIdx in sharedStrings.indices) sharedStrings[ssIdx] else vText
                            }
                            "inlineStr" -> {
                                val isNodes = cellEl.getElementsByTagName("is")
                                if (isNodes.length > 0) extractTextFromXmlNode(isNodes.item(0)) else ""
                            }
                            else -> getFirstChildTagText(cellEl, "v")
                        }
                        rowCells[colIdx] = sanitizeText(cellValue, 500)
                    }

                    if (maxColIdx >= 0 && rowCells.values.any { it.isNotBlank() }) {
                        val totalCols = minOf(maxColIdx + 1, 100)
                        for (c in 0 until totalCols) {
                            if (c > 0) csvBuilder.append(",")
                            csvBuilder.append(escapeCsv(rowCells[c] ?: ""))
                        }
                        csvBuilder.append("\n")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val finalCsv = csvBuilder.toString()
        if (finalCsv.isBlank()) {
            throw IllegalArgumentException("The selected Excel (.xlsx) file did not contain any customer rows.")
        }
        return finalCsv
    }

    private fun excelCellRefToColIndex(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return maxOf(0, col - 1)
    }

    private fun getFirstChildTagText(parent: Element, tagName: String): String {
        val nodes = parent.getElementsByTagName(tagName)
        if (nodes.length == 0) return ""
        return nodes.item(0).textContent ?: ""
    }

    private fun extractTextFromXmlNode(node: Node): String {
        val sb = StringBuilder()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeName == "t" || child.nodeName.endsWith(":t")) {
                sb.append(child.textContent ?: "")
            } else if (child.hasChildNodes()) {
                sb.append(extractTextFromXmlNode(child))
            }
        }
        return if (sb.isNotEmpty()) sb.toString() else (node.textContent ?: "")
    }

    private fun parseDateStringWithFallback(dateStr: String, fallback: () -> Long): Long {
        val trimmed = sanitizeText(dateStr, 80)
        if (trimmed.isBlank()) return fallback()

        if (trimmed.all { it.isDigit() }) {
            val longVal = trimmed.toLongOrNull()
            if (longVal != null && longVal > 0L) {
                // Handle Excel serial dates (e.g. 45000) vs Unix epoch ms
                if (longVal in 25569L..90000L) {
                    return (longVal - 25569L) * 86400_000L
                }
                return longVal
            }
        }

        // Check decimal Excel serial date (e.g. "45234.5")
        val asDouble = trimmed.toDoubleOrNull()
        if (asDouble != null && asDouble in 25569.0..90000.0) {
            return ((asDouble - 25569.0) * 86400_000.0).toLong()
        }

        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd",
            "MM/dd/yyyy"
        )

        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val parsed = sdf.parse(trimmed)
                if (parsed != null) {
                    return parsed.time
                }
            } catch (_: Exception) {
                // Keep trying next format
            }
        }

        return fallback()
    }

    private fun parseFiniteDouble(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        val cleaned = raw.trim().replace(Regex("[^0-9.-]"), "")
        val v = cleaned.toDoubleOrNull() ?: return 0.0
        return if (v.isFinite() && v >= 0.0) v else 0.0
    }

    /**
     * Imports a CSV into a specific day's records, or all groups dynamically from the file.
     */
    suspend fun importCsvIntoDay(
        context: Context,
        csvText: String,
        dayGroup: String,
        db: AppDatabase
    ): Boolean {
        val cleanInput = csvText.removePrefix("\uFEFF")
        if (cleanInput.isBlank()) {
            throw IllegalArgumentException("Selected backup file is empty.")
        }
        if (isBinaryOrCorruptedText(cleanInput)) {
            throw IllegalArgumentException("Selected file contains binary or unreadable data. Please choose a valid CSV or Excel (.xlsx) backup file.")
        }

        val sections = cleanInput.split("---CASH_BALANCE_LOGS---")
        val customerCsvText = sections[0]
        val cashLogsText = if (sections.size > 1) sections[1] else ""

        val rows = parseCsvRows(customerCsvText)
        if (rows.isEmpty()) throw IllegalArgumentException("Selected backup file is empty.")

        var expectedCustomers = -1
        var expectedActiveLoans = -1

        for (row in rows) {
            val rowStr = row.joinToString(",")
            if (rowStr.contains("Total Customers", ignoreCase = true) &&
                (rowStr.contains("Total Active", ignoreCase = true) || rowStr.contains("Total_Active", ignoreCase = true))
            ) {
                try {
                    val tcRegex = "Total\\s*Customers\\s*[=:]?\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE)
                    val taRegex = "Total\\s*Active\\s*(?:Loans)?\\s*[=:]?\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE)

                    tcRegex.find(rowStr)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                        expectedCustomers = it
                    }
                    taRegex.find(rowStr)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                        expectedActiveLoans = it
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val importedCustomerUuids = mutableSetOf<String>()
        val importedActiveLoanUuids = mutableSetOf<String>()
        val discoveredDayGroups = mutableSetOf<String>()

        val allCustomersCache = db.collectionDao().getAllCustomersOnce().toMutableList()
        val allLoansCache = db.collectionDao().getAllLoanCyclesOnce().toMutableList()
        val allPaymentsCache = db.collectionDao().getAllPaymentsOnce().toMutableList()

        val customerByUuid = HashMap<String, Customer>(allCustomersCache.size * 2)
        allCustomersCache.forEach { customerByUuid[it.uuid.trim().lowercase(Locale.US)] = it }

        val loanByUuid = HashMap<String, LoanCycle>(allLoansCache.size * 2)
        allLoansCache.forEach { loanByUuid[it.uuid.trim().lowercase(Locale.US)] = it }

        val paymentsByLoanId = HashMap<Int, MutableList<WeeklyPayment>>()
        allPaymentsCache.forEach { p ->
            paymentsByLoanId.getOrPut(p.loanCycleId) { mutableListOf() }.add(p)
        }

        val isAllMode = dayGroup.trim().equals("ALL", ignoreCase = true)
        var currentSectionGroup: String? = null
        // Shifts loan columns by +1 if an older backup has a legacy "UPI Handle" column at index 5
        var legacyUpiColShift = 0
        // Detects simple 6-column or 9-column customer CSV imports
        var isSimpleDirectoryCsv = false

        db.withTransaction {
            val dao = db.collectionDao()

            for (row in rows) {
                if (row.isEmpty()) continue
                val firstCol = sanitizeText(row.getOrNull(0), 200)

                // Skip comment/metadata lines
                if (firstCol.startsWith("#") || firstCol.contains("Total Customers", ignoreCase = true) || firstCol.contains("Exported On", ignoreCase = true)) {
                    continue
                }

                // Check for === DAY_GROUP: <Name> === section header (even if trailing commas exist)
                val dayGroupMarkerCell = row.firstOrNull { it.contains("DAY_GROUP:", ignoreCase = true) }
                if (dayGroupMarkerCell != null) {
                    val extracted = dayGroupMarkerCell
                        .substringAfter("DAY_GROUP:", "")
                        .replace("=", "")
                        .replace("\"", "")
                        .trim()
                    if (extracted.isNotBlank()) {
                        currentSectionGroup = normalizeDayGroup(extracted, "Monday")
                    }
                    continue
                }

                // Check if this row is a header row
                val col0Lower = firstCol.lowercase(Locale.US)
                val col2Lower = sanitizeText(row.getOrNull(2), 100).lowercase(Locale.US)
                if (col0Lower.contains("customer uuid") || col2Lower == "client name" || col0Lower == "name" || col0Lower == "client name") {
                    val col5Lower = sanitizeText(row.getOrNull(5), 100).lowercase(Locale.US)
                    val col6Lower = sanitizeText(row.getOrNull(6), 100).lowercase(Locale.US)
                    legacyUpiColShift = if (col5Lower.contains("upi") && !col6Lower.contains("loan id")) 1 else 0
                    isSimpleDirectoryCsv = (col0Lower == "name" || col0Lower == "client name") && !col0Lower.contains("uuid")
                    continue
                }

                // Handle simple 6/9 column CSV format if detected or if row is short and col 0 is a name (not UUID)
                if (isSimpleDirectoryCsv || (row.size in 2..9 && sanitizeText(row.getOrNull(6), 100).isBlank() && firstCol.isNotBlank() && !firstCol.contains("-"))) {
                    val simpleName = firstCol
                    if (simpleName.isBlank() || simpleName.equals("Name", ignoreCase = true)) continue
                    val secondCol = sanitizeText(row.getOrNull(1), 60)
                    val isDirectory6Col = DEFAULT_ACTIVE_DAY_GROUPS.any { it.equals(secondCol, ignoreCase = true) } || secondCol.equals("Friday", ignoreCase = true)
                    val targetGroup = if (isAllMode) {
                        if (isDirectory6Col) normalizeDayGroup(secondCol, currentSectionGroup ?: "Monday")
                        else (currentSectionGroup ?: "Monday")
                    } else {
                        normalizeDayGroup(dayGroup, "Monday")
                    }
                    if (targetGroup.equals("Friday", ignoreCase = true)) continue
                    discoveredDayGroups.add(targetGroup)

                    val phone = if (isDirectory6Col) sanitizeText(row.getOrNull(4), 40) else sanitizeText(row.getOrNull(1), 40)
                    val city = if (isDirectory6Col) sanitizeText(row.getOrNull(5), 80) else sanitizeText(row.getOrNull(2), 80)
                    val principal = if (isDirectory6Col) parseFiniteDouble(row.getOrNull(2)) else parseFiniteDouble(row.getOrNull(3))
                    val interest = if (isDirectory6Col) 0.0 else parseFiniteDouble(row.getOrNull(4))
                    val paidAmt = if (isDirectory6Col) parseFiniteDouble(row.getOrNull(3)) else parseFiniteDouble(row.getOrNull(7))
                    val tenureWeeks = if (isDirectory6Col) 10 else (row.getOrNull(6)?.trim()?.toIntOrNull()?.coerceIn(1, 100) ?: 10)
                    val weeklyAmt = if (!isDirectory6Col && parseFiniteDouble(row.getOrNull(5)) > 0.0) {
                        parseFiniteDouble(row.getOrNull(5))
                    } else {
                        (principal + interest) / tenureWeeks.toDouble()
                    }

                    val custUuid = UUID.randomUUID().toString()
                    val newCust = Customer(
                        name = simpleName,
                        phone = phone,
                        city = city,
                        collectionDay = targetGroup,
                        uuid = custUuid,
                        lastModified = System.currentTimeMillis()
                    )
                    val custId = dao.insertCustomer(newCust).toInt()
                    val savedCust = newCust.copy(id = custId)
                    allCustomersCache.add(savedCust)
                    customerByUuid[custUuid.lowercase(Locale.US)] = savedCust
                    importedCustomerUuids.add(custUuid)

                    if (principal > 0.0) {
                        val loanUuid = UUID.randomUUID().toString()
                        val isPaid = paidAmt >= (principal + interest)
                        val newLoan = LoanCycle(
                            customerId = custId,
                            loanAmount = principal,
                            interestAmount = interest,
                            weeklyAmount = weeklyAmt,
                            totalWeeks = tenureWeeks,
                            startDate = System.currentTimeMillis(),
                            paidAmount = paidAmt,
                            status = if (isPaid) "PAID" else "ACTIVE",
                            uuid = loanUuid,
                            lastModified = System.currentTimeMillis()
                        )
                        val loanId = dao.insertLoanCycle(newLoan).toInt()
                        val savedLoan = newLoan.copy(id = loanId)
                        allLoansCache.add(savedLoan)
                        loanByUuid[loanUuid.lowercase(Locale.US)] = savedLoan
                        if (!isPaid) importedActiveLoanUuids.add(loanUuid)
                        if (paidAmt > 0.0) {
                            val newP = WeeklyPayment(
                                loanCycleId = loanId,
                                amountPaid = paidAmt,
                                paymentDate = System.currentTimeMillis(),
                                weekNumber = 1,
                                uuid = UUID.randomUUID().toString(),
                                lastModified = System.currentTimeMillis(),
                                status = "ACTIVE"
                            )
                            val pId = dao.insertPayment(newP).toInt()
                            paymentsByLoanId.getOrPut(loanId) { mutableListOf() }.add(newP.copy(id = pId))
                        }
                    }
                    continue
                }

                // Standard 11+60 column schema (and legacy 12+60 shift)
                val clientName = sanitizeText(row.getOrNull(2), 150)
                if (clientName.isBlank() ||
                    clientName.equals("Client Name", ignoreCase = true) ||
                    clientName.contains("Total Customers", ignoreCase = true)
                ) {
                    continue
                }

                val rowGroup = if (isAllMode) {
                    // Priority 1: Explicit === DAY_GROUP: ... === section marker from multi-sheet or bundled CSV
                    val fromSection = currentSectionGroup
                    // Priority 2: Explicit Collection Day column at index 71 (+ shift) or last non-blank column if it matches a day name
                    val col71Val = sanitizeText(row.getOrNull(71 + legacyUpiColShift), 40)
                    val lastVal = sanitizeText(row.lastOrNull(), 40)
                    val matchedCol71 = DEFAULT_ACTIVE_DAY_GROUPS.find { it.equals(col71Val, ignoreCase = true) }
                        ?: if (col71Val.equals("Friday", ignoreCase = true)) "Friday" else null
                    val matchedLast = DEFAULT_ACTIVE_DAY_GROUPS.find { it.equals(lastVal, ignoreCase = true) }
                        ?: if (lastVal.equals("Friday", ignoreCase = true)) "Friday" else null

                    when {
                        matchedCol71 != null -> matchedCol71
                        matchedLast != null -> matchedLast
                        !fromSection.isNullOrBlank() -> fromSection
                        col71Val.isNotBlank() -> normalizeDayGroup(col71Val, "Monday")
                        else -> "Monday"
                    }
                } else {
                    // When importing into a specific day group, ignore rows belonging to other day sections if file is multi-section
                    if (currentSectionGroup != null && !currentSectionGroup.equals(dayGroup.trim(), ignoreCase = true)) {
                        continue
                    }
                    normalizeDayGroup(dayGroup, "Monday")
                }

                // Rule 1 Check: Friday must be completely ignored/excluded from sheets and operations
                if (rowGroup.trim().equals("Friday", ignoreCase = true)) continue
                discoveredDayGroups.add(rowGroup)

                val customerUuidRaw = sanitizeText(row.getOrNull(0), 100)
                val existingCustomer = if (customerUuidRaw.isNotBlank()) {
                    customerByUuid[customerUuidRaw.lowercase(Locale.US)]
                } else null

                val customerUuid = existingCustomer?.uuid
                    ?: if (customerUuidRaw.isNotBlank()) customerUuidRaw else UUID.randomUUID().toString()
                importedCustomerUuids.add(customerUuid)

                val customOrder = row.getOrNull(1)?.trim()?.replace(Regex("[^0-9-]"), "")?.toIntOrNull() ?: 0
                val rawPhone = sanitizeText(row.getOrNull(3), 80)
                val phoneParts = rawPhone.split(Regex("[/,;]")).map { it.trim() }.filter { it.isNotBlank() }
                val phone = phoneParts.getOrNull(0) ?: ""
                val phone2 = phoneParts.getOrNull(1) ?: ""
                val city = sanitizeText(row.getOrNull(4), 100)
                val smsSettingsStr = sanitizeText(row.getOrNull(5 + legacyUpiColShift), 300)

                var smsWeekly = false
                var smsConf = false
                var autoWeeklySms = false
                var autoWeeklyWhatsapp = false
                var upiNameAlias = ""
                var preferredLanguage = "English"

                if (smsSettingsStr.isNotBlank()) {
                    smsWeekly = smsSettingsStr.contains("Weekly Reminder: YES", ignoreCase = true)
                    smsConf = smsSettingsStr.contains("Entry Confirmation: YES", ignoreCase = true)

                    val autoSmsRegex = "Auto Weekly SMS:\\s*(YES|NO)".toRegex(RegexOption.IGNORE_CASE)
                    val autoWhatsappRegex = "Auto Weekly Whatsapp:\\s*(YES|NO)".toRegex(RegexOption.IGNORE_CASE)
                    val upiAliasRegex = "UPI Name Alias:\\s*([^,\\]]+)".toRegex(RegexOption.IGNORE_CASE)
                    val langRegex = "Preferred Language:\\s*([^,\\]]+)".toRegex(RegexOption.IGNORE_CASE)

                    autoWeeklySms = autoSmsRegex.find(smsSettingsStr)?.groupValues?.getOrNull(1)?.trim()?.equals("YES", ignoreCase = true) ?: false
                    autoWeeklyWhatsapp = autoWhatsappRegex.find(smsSettingsStr)?.groupValues?.getOrNull(1)?.trim()?.equals("YES", ignoreCase = true) ?: false
                    upiNameAlias = sanitizeText(upiAliasRegex.find(smsSettingsStr)?.groupValues?.getOrNull(1), 100)
                    preferredLanguage = sanitizeText(langRegex.find(smsSettingsStr)?.groupValues?.getOrNull(1), 40).ifBlank { "English" }
                }

                val finalCustomerId: Int
                if (existingCustomer != null) {
                    finalCustomerId = existingCustomer.id
                    val updatedCustomer = existingCustomer.copy(
                        name = clientName,
                        phone = phone,
                        phone2 = phone2,
                        city = city,
                        collectionDay = rowGroup,
                        customOrder = customOrder,
                        smsWeeklyReminder = smsWeekly,
                        smsConfirmationOfEntry = smsConf,
                        autoWeeklySms = autoWeeklySms,
                        autoWeeklyWhatsapp = autoWeeklyWhatsapp,
                        upiNameAlias = upiNameAlias,
                        preferredLanguage = preferredLanguage,
                        status = "ACTIVE",
                        lastModified = System.currentTimeMillis()
                    )
                    dao.updateCustomer(updatedCustomer)
                    customerByUuid[customerUuid.lowercase(Locale.US)] = updatedCustomer
                } else {
                    val newCustomer = Customer(
                        name = clientName,
                        phone = phone,
                        phone2 = phone2,
                        city = city,
                        customOrder = customOrder,
                        collectionDay = rowGroup,
                        smsWeeklyReminder = smsWeekly,
                        smsConfirmationOfEntry = smsConf,
                        autoWeeklySms = autoWeeklySms,
                        autoWeeklyWhatsapp = autoWeeklyWhatsapp,
                        upiNameAlias = upiNameAlias,
                        preferredLanguage = preferredLanguage,
                        uuid = customerUuid,
                        status = "ACTIVE",
                        lastModified = System.currentTimeMillis()
                    )
                    val insertedId = dao.insertCustomer(newCustomer).toInt()
                    finalCustomerId = insertedId
                    val savedCustomer = newCustomer.copy(id = insertedId)
                    allCustomersCache.add(savedCustomer)
                    customerByUuid[customerUuid.lowercase(Locale.US)] = savedCustomer
                }

                // Schema layout parsing mapping definitions (with optional legacy shift)
                val loanUuidRaw = sanitizeText(row.getOrNull(6 + legacyUpiColShift), 100)
                val amountDisbursed = parseFiniteDouble(row.getOrNull(7 + legacyUpiColShift))
                val rawPrinciple = parseFiniteDouble(row.getOrNull(8 + legacyUpiColShift))
                val principle = if (rawPrinciple > 0.0) rawPrinciple else amountDisbursed
                val interest = parseFiniteDouble(row.getOrNull(9 + legacyUpiColShift))
                val deduction = if (amountDisbursed > 0.0 && principle > amountDisbursed) {
                    principle - amountDisbursed
                } else {
                    0.0
                }
                val dispersalDateStr = sanitizeText(row.getOrNull(10 + legacyUpiColShift), 200)

                val maxPaymentWeek = (1..30).filter { w ->
                    val amtColIdx = 12 + legacyUpiColShift + 2 * (w - 1)
                    parseFiniteDouble(row.getOrNull(amtColIdx)) > 0.0
                }.maxOrNull() ?: 0

                if (loanUuidRaw.isNotBlank() || principle > 0.0 || amountDisbursed > 0.0 || interest > 0.0 || maxPaymentWeek > 0) {
                    val existingLoan = if (loanUuidRaw.isNotBlank()) {
                        loanByUuid[loanUuidRaw.lowercase(Locale.US)]
                    } else null
                    val finalLoanUuid = existingLoan?.uuid
                        ?: if (loanUuidRaw.isNotBlank()) loanUuidRaw else UUID.randomUUID().toString()

                    var cleanDispersalDateStr = dispersalDateStr
                    var parsedLoanNotes = ""
                    var parsedLoanStatus = "ACTIVE"

                    if (dispersalDateStr.contains("[")) {
                        val startIndex = dispersalDateStr.indexOf("[")
                        cleanDispersalDateStr = dispersalDateStr.substring(0, startIndex).trim()
                        val metaContent = dispersalDateStr.substring(startIndex).trim()
                        val notesRegex = "NOTES:\\s*([^|\\]]+)".toRegex()
                        val statusRegex = "STATUS:\\s*([^|\\]]+)".toRegex()

                        notesRegex.find(metaContent)?.groupValues?.getOrNull(1)?.trim()?.let {
                            parsedLoanNotes = sanitizeText(it, 200)
                        }
                        statusRegex.find(metaContent)?.groupValues?.getOrNull(1)?.trim()?.let {
                            parsedLoanStatus = sanitizeText(it, 30).ifBlank { "ACTIVE" }
                        }
                    }

                    val finalTotalWeeks = if (existingLoan != null && existingLoan.totalWeeks >= maxOf(1, maxPaymentWeek)) {
                        existingLoan.totalWeeks
                    } else {
                        maxOf(10, maxPaymentWeek)
                    }

                    val finalWeeklyAmount = if (existingLoan != null && existingLoan.weeklyAmount > 0.0 && existingLoan.weeklyAmount.isFinite()) {
                        existingLoan.weeklyAmount
                    } else {
                        val computed = (principle + interest) / finalTotalWeeks.coerceAtLeast(1).toDouble()
                        if (computed.isFinite() && computed >= 0.0) computed else 0.0
                    }

                    val finalLoanId: Int
                    val activeLoanObj: LoanCycle
                    if (existingLoan != null) {
                        finalLoanId = existingLoan.id
                        activeLoanObj = existingLoan.copy(
                            customerId = finalCustomerId,
                            loanAmount = principle,
                            interestAmount = interest,
                            weeklyAmount = finalWeeklyAmount,
                            totalWeeks = finalTotalWeeks,
                            startDate = parseDateStringWithFallback(cleanDispersalDateStr) { existingLoan.startDate },
                            lastModified = System.currentTimeMillis(),
                            deduction = deduction,
                            notes = parsedLoanNotes,
                            status = parsedLoanStatus
                        )
                        dao.updateLoanCycle(activeLoanObj)
                        loanByUuid[finalLoanUuid.lowercase(Locale.US)] = activeLoanObj
                    } else {
                        val newLoan = LoanCycle(
                            customerId = finalCustomerId,
                            loanAmount = principle,
                            interestAmount = interest,
                            weeklyAmount = finalWeeklyAmount,
                            totalWeeks = finalTotalWeeks,
                            startDate = parseDateStringWithFallback(cleanDispersalDateStr) { System.currentTimeMillis() },
                            status = parsedLoanStatus,
                            notes = parsedLoanNotes,
                            uuid = finalLoanUuid,
                            lastModified = System.currentTimeMillis(),
                            deduction = deduction
                        )
                        val insertedId = dao.insertLoanCycle(newLoan).toInt()
                        finalLoanId = insertedId
                        activeLoanObj = newLoan.copy(id = insertedId)
                        allLoansCache.add(activeLoanObj)
                        loanByUuid[finalLoanUuid.lowercase(Locale.US)] = activeLoanObj
                    }

                    var sumPaid = 0.0
                    val loanPaymentsList = paymentsByLoanId.getOrPut(finalLoanId) { mutableListOf() }

                    for (w in 1..30) {
                        val dateColIdx = 11 + legacyUpiColShift + 2 * (w - 1)
                        val amtColIdx = 12 + legacyUpiColShift + 2 * (w - 1)

                        val pDateStr = sanitizeText(row.getOrNull(dateColIdx), 150)
                        val pAmt = parseFiniteDouble(row.getOrNull(amtColIdx))

                        var cleanDateStr = pDateStr
                        var parsedUpiTxnId: String? = null
                        var parsedPaymentNotes = ""

                        if (pDateStr.contains("[")) {
                            val startIndex = pDateStr.indexOf("[")
                            cleanDateStr = pDateStr.substring(0, startIndex).trim()
                            val metaContent = pDateStr.substring(startIndex).trim()
                            val txnRegex = "TXN:\\s*([^|\\]]+)".toRegex()
                            val notesRegex = "NOTES:\\s*([^|\\]]+)".toRegex()

                            txnRegex.find(metaContent)?.groupValues?.getOrNull(1)?.trim()?.let {
                                parsedUpiTxnId = sanitizeText(it, 80)
                            }
                            notesRegex.find(metaContent)?.groupValues?.getOrNull(1)?.trim()?.let {
                                parsedPaymentNotes = sanitizeText(it, 150)
                            }
                        }

                        val existingIdx = loanPaymentsList.indexOfFirst { it.weekNumber == w && it.status.uppercase(Locale.US) != "DELETED" }
                        val existingP = if (existingIdx != -1) loanPaymentsList[existingIdx] else null

                        if (pAmt > 0.0) {
                            sumPaid += pAmt
                            if (existingP != null) {
                                val updatedP = existingP.copy(
                                    amountPaid = pAmt,
                                    paymentDate = parseDateStringWithFallback(cleanDateStr) { existingP.paymentDate },
                                    upiTxnId = parsedUpiTxnId,
                                    notes = parsedPaymentNotes,
                                    lastModified = System.currentTimeMillis()
                                )
                                dao.insertPayment(updatedP)
                                loanPaymentsList[existingIdx] = updatedP
                            } else {
                                val newP = WeeklyPayment(
                                    loanCycleId = finalLoanId,
                                    amountPaid = pAmt,
                                    paymentDate = parseDateStringWithFallback(cleanDateStr) { System.currentTimeMillis() },
                                    weekNumber = w,
                                    upiTxnId = parsedUpiTxnId,
                                    notes = parsedPaymentNotes,
                                    uuid = UUID.randomUUID().toString(),
                                    lastModified = System.currentTimeMillis(),
                                    status = "ACTIVE"
                                )
                                val insertedId = dao.insertPayment(newP).toInt()
                                loanPaymentsList.add(newP.copy(id = insertedId))
                            }
                        } else if (existingP != null) {
                            val updatedP = existingP.copy(
                                status = "DELETED",
                                lastModified = System.currentTimeMillis()
                            )
                            dao.insertPayment(updatedP)
                            loanPaymentsList[existingIdx] = updatedP
                        }
                    }

                    val targetSettleAmount = activeLoanObj.loanAmount + activeLoanObj.interestAmount
                    val isPaidOff = targetSettleAmount > 0.0 && sumPaid >= targetSettleAmount
                    val finalStatus = when {
                        parsedLoanStatus.equals("DELETED", ignoreCase = true) -> "DELETED"
                        isPaidOff || parsedLoanStatus.equals("PAID", ignoreCase = true) -> "PAID"
                        else -> "ACTIVE"
                    }
                    val updatedLoan = activeLoanObj.copy(
                        paidAmount = sumPaid,
                        status = finalStatus,
                        lastModified = System.currentTimeMillis()
                    )
                    dao.updateLoanCycle(updatedLoan)
                    loanByUuid[finalLoanUuid.lowercase(Locale.US)] = updatedLoan
                    if (finalStatus == "ACTIVE") {
                        importedActiveLoanUuids.add(finalLoanUuid)
                    }
                }
            }

            if (cashLogsText.isNotBlank()) {
                val cashRows = parseCsvRows(cashLogsText)
                var cashHeaderIdx = -1
                for (idx in cashRows.indices) {
                    if (cashRows[idx].getOrNull(0)?.contains("ID", ignoreCase = true) == true) {
                        cashHeaderIdx = idx
                        break
                    }
                }
                val cashDataRows = if (cashHeaderIdx != -1) cashRows.drop(cashHeaderIdx + 1) else cashRows

                if (cashDataRows.isNotEmpty() && isAllMode) {
                    dao.deleteAllCashBalanceLogs()
                }

                for (cashRow in cashDataRows) {
                    if (cashRow.isEmpty() || cashRow[0].isBlank()) continue
                    val cDate = cashRow.getOrNull(1)?.trim()?.toLongOrNull() ?: continue
                    val cActual = parseFiniteDouble(cashRow.getOrNull(2))
                    val cSystem = parseFiniteDouble(cashRow.getOrNull(3))
                    val cCollection = parseFiniteDouble(cashRow.getOrNull(4))
                    val cDisbursal = parseFiniteDouble(cashRow.getOrNull(5))
                    val cExpenses = parseFiniteDouble(cashRow.getOrNull(6))
                    dao.insertCashBalanceLog(
                        com.example.data.CashBalanceLog(
                            id = 0,
                            date = cDate,
                            actualCash = cActual,
                            systemCash = cSystem,
                            collectionAmount = cCollection,
                            disbursalAmount = cDisbursal,
                            expenses = cExpenses
                        )
                    )
                }
            }

            if (expectedCustomers != -1 && importedCustomerUuids.size != expectedCustomers) {
                android.util.Log.w("CsvImport", "Verification count notice: Metadata expected $expectedCustomers clients, parsed ${importedCustomerUuids.size}.")
            }
            if (expectedActiveLoans != -1 && importedActiveLoanUuids.size != expectedActiveLoans) {
                android.util.Log.w("CsvImport", "Verification count notice: Metadata expected $expectedActiveLoans active loans, parsed ${importedActiveLoanUuids.size}.")
            }
        }

        // Persist any newly imported day groups into shared preferences (excluding Friday)
        if (discoveredDayGroups.isNotEmpty()) {
            try {
                val prefs = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
                val existingRaw = prefs.getString("collection_groups_list", DEFAULT_ACTIVE_DAY_GROUPS.joinToString(","))
                    ?: DEFAULT_ACTIVE_DAY_GROUPS.joinToString(",")
                val mergedGroups = existingRaw.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.equals("Friday", ignoreCase = true) }
                    .toMutableList()
                var changed = false
                for (g in discoveredDayGroups) {
                    if (!g.equals("Friday", ignoreCase = true) && mergedGroups.none { it.equals(g, ignoreCase = true) }) {
                        mergedGroups.add(g)
                        changed = true
                    }
                }
                if (changed) {
                    prefs.edit().putString("collection_groups_list", mergedGroups.joinToString(",")).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return true
    }
}
