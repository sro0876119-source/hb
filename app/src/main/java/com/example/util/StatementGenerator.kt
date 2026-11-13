package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.LoanCycle
import com.example.data.WeeklyPayment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object StatementGenerator {

    const val DEFAULT_STATEMENT_TABLE_COLUMNS = "WEEK_NO,DATE,MODE,TIME,AMOUNT"

    val ALL_AVAILABLE_TABLE_COLUMNS: List<Pair<String, String>> = listOf(
        "WEEK_NO" to "Week Number",
        "DATE" to "Date",
        "TIME" to "Time",
        "MODE" to "Mode of Payment",
        "AMOUNT" to "Amount Paid",
        "REMARKS" to "Remarks / Txn ID"
    )

    fun parseStatementTableColumns(rawCsv: String?): List<String> {
        val validKeys = ALL_AVAILABLE_TABLE_COLUMNS.map { it.first }.toSet()
        val parsed = rawCsv.orEmpty()
            .split(",")
            .map { it.trim().uppercase(Locale.US) }
            .filter { it in validKeys }
            .distinct()
        return if (parsed.isNotEmpty()) parsed else listOf("WEEK_NO", "DATE", "MODE", "TIME", "AMOUNT")
    }

    fun getColumnHeaderLabel(colKey: String): String {
        return when (colKey.uppercase(Locale.US)) {
            "WEEK_NO" -> "WEEK NO."
            "DATE" -> "DATE"
            "TIME" -> "TIME"
            "MODE" -> "MODE"
            "AMOUNT" -> "AMOUNT PAID"
            "REMARKS" -> "REMARKS"
            else -> colKey.uppercase(Locale.US)
        }
    }

    fun formatPaymentCellForColumn(colKey: String, payment: WeeklyPayment, fallbackIndex: Int = 0): String {
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val weekNum = if (payment.weekNumber > 0) payment.weekNumber else (fallbackIndex + 1)
        return when (colKey.uppercase(Locale.US)) {
            "WEEK_NO" -> "Week $weekNum"
            "DATE" -> dateFmt.format(Date(payment.paymentDate))
            "TIME" -> timeFmt.format(Date(payment.paymentDate)).uppercase(Locale.getDefault())
            "MODE" -> payment.notes.trim().ifBlank { "Cash" }
            "AMOUNT" -> "₹${CurrencyFormatter.format(payment.amountPaid)}"
            "REMARKS" -> payment.upiTxnId?.takeIf { it.isNotBlank() } ?: payment.notes.trim().ifBlank { "-" }
            else -> ""
        }
    }

    fun resolveStatementTemplate(
        rawTemplate: String,
        businessName: String,
        customerName: String,
        customerPhone: String,
        collectionDay: String,
        customerCity: String = "",
        routeNo: Int = 1,
        loan: LoanCycle? = null,
        payments: List<WeeklyPayment> = emptyList(),
        upiId: String = ""
    ): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
        val effectiveBiz = if (businessName.isBlank() || businessName.trim().equals("Muneeswaran P", ignoreCase = true)) {
            "MD Finance"
        } else {
            businessName.trim()
        }
        val principal = loan?.loanAmount ?: 10000.0
        val interest = loan?.interestAmount ?: 2000.0
        val totalDue = MoneyMath.add(principal, interest)
        val weeklyInst = loan?.weeklyAmount ?: 1000.0
        val paidAmt = if (loan != null) {
            if (payments.isNotEmpty()) payments.sumOf { it.amountPaid } else loan.paidAmount
        } else {
            4000.0
        }
        val balance = MoneyMath.subtract(totalDue, paidAmt).coerceAtLeast(0.0)
        val startDateStr = if (loan != null && loan.startDate > 0L) dateFormat.format(Date(loan.startDate)) else todayStr
        val totalWeeks = loan?.totalWeeks ?: 12

        return rawTemplate
            .replace("{business}", effectiveBiz)
            .replace("{customer}", customerName.ifBlank { "Customer" })
            .replace("{name}", customerName.ifBlank { "Customer" })
            .replace("{phone}", customerPhone.ifBlank { "N/A" })
            .replace("{phone_number}", customerPhone.ifBlank { "N/A" })
            .replace("{day}", collectionDay.ifBlank { "Monday" })
            .replace("{city}", customerCity.ifBlank { "Local" })
            .replace("{route}", routeNo.toString())
            .replace("{loan_amt}", CurrencyFormatter.format(principal))
            .replace("{interest}", CurrencyFormatter.format(interest))
            .replace("{total_due}", CurrencyFormatter.format(totalDue))
            .replace("{inst_amt}", CurrencyFormatter.format(weeklyInst))
            .replace("{weekly}", CurrencyFormatter.format(weeklyInst))
            .replace("{paid_amt}", CurrencyFormatter.format(paidAmt))
            .replace("{amount}", CurrencyFormatter.format(paidAmt))
            .replace("{p_amount}", CurrencyFormatter.format(paidAmt))
            .replace("{balance}", CurrencyFormatter.format(balance))
            .replace("{outstanding}", CurrencyFormatter.format(balance))
            .replace("{weeks}", totalWeeks.toString())
            .replace("{start_date}", startDateStr)
            .replace("{date}", todayStr)
            .replace("{upi}", upiId.ifBlank { "9440736893@ptyes" })
    }

    /**
     * Maps raw loan status to display status for statements and cards:
     * - "PAID" or "CLOSED" or isClosedLoan -> "CLOSED"
     * - "SUSPENDED" or "BAD_DEBT" -> "SUSPENDED"
     * - Otherwise -> "ACTIVE"
     */
    fun resolveDisplayLoanStatus(loanStatus: String, isClosedLoan: Boolean = false): String {
        val upper = loanStatus.trim().uppercase(Locale.US)
        return when {
            upper == "SUSPENDED" || upper == "BAD_DEBT" -> "SUSPENDED"
            isClosedLoan || upper == "PAID" || upper == "CLOSED" -> "CLOSED"
            else -> "ACTIVE"
        }
    }

    fun generateCustomerStatementBitmap(
        context: Context,
        businessName: String,
        customerName: String,
        customerPhone: String = "",
        collectionDay: String,
        activeLoan: LoanCycle? = null,
        loan: LoanCycle = activeLoan ?: LoanCycle(customerId = 0, loanAmount = 0.0, interestAmount = 0.0, weeklyAmount = 0.0, totalWeeks = 0, startDate = 0L),
        payments: List<WeeklyPayment>,
        themeName: String = "",
        customizationCode: String = "",
        isClosedLoan: Boolean = false,
        customerCity: String = "",
        routeNo: Int = 1,
        tableColumnsCsv: String = ""
    ): Bitmap {
        val prefs = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
        val upiId = prefs.getString("upi_id", "9440736893@ptyes") ?: "9440736893@ptyes"

        val customProps = mutableMapOf<String, String>()
        customizationCode.lines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                customProps[parts[0].trim().uppercase()] = parts[1].trim()
            }
        }

        val rawHeaderTitle = prefs.getString("statement_header_title", "{business}") ?: "{business}"
        val rawHeaderSubtitle = prefs.getString("statement_header_subtitle", customProps["TITLE"] ?: "COLLECTION STATEMENT REPORT")
            ?: "COLLECTION STATEMENT REPORT"
        val rawCustomerBox = prefs.getString("statement_customer_box", "Name: {customer}\nPhone: {phone}\nCollection Day: {day}")
            ?: "Name: {customer}\nPhone: {phone}\nCollection Day: {day}"
        val rawLoanBox = prefs.getString(
            "statement_loan_box",
            "Date of Dispersal: {start_date}\nTotal Contract Value: ₹{total_due}\nAmt Paid: ₹{paid_amt}\nOutstanding: ₹{balance}"
        ) ?: "Date of Dispersal: {start_date}\nTotal Contract Value: ₹{total_due}\nAmt Paid: ₹{paid_amt}\nOutstanding: ₹{balance}"
        val rawFooter = prefs.getString("statement_footer_text", "THANK YOU\n${customProps["FOOTER"] ?: "Please retain this statement for your verification."}")
            ?: "THANK YOU\nPlease retain this statement for your verification."

        val resolvedHeaderTitle = resolveStatementTemplate(
            rawHeaderTitle, businessName, customerName, customerPhone, collectionDay, customerCity, routeNo, loan, payments, upiId
        ).ifBlank { "MD Finance" }
        val resolvedHeaderSubtitle = resolveStatementTemplate(
            rawHeaderSubtitle, businessName, customerName, customerPhone, collectionDay, customerCity, routeNo, loan, payments, upiId
        )
        val customerBoxLines = resolveStatementTemplate(
            rawCustomerBox, businessName, customerName, customerPhone, collectionDay, customerCity, routeNo, loan, payments, upiId
        ).lines().filter { it.isNotBlank() }
        val loanBoxLines = resolveStatementTemplate(
            rawLoanBox, businessName, customerName, customerPhone, collectionDay, customerCity, routeNo, loan, payments, upiId
        ).lines().filter { it.isNotBlank() }
        val footerLines = resolveStatementTemplate(
            rawFooter, businessName, customerName, customerPhone, collectionDay, customerCity, routeNo, loan, payments, upiId
        ).lines().filter { it.isNotBlank() }

        val startColor = try { Color.parseColor(customProps["COLOR_START"] ?: "#0F172A") } catch (e: Exception) { Color.parseColor("#0F172A") }
        val endColor = try { Color.parseColor(customProps["COLOR_END"] ?: "#1E1B4B") } catch (e: Exception) { Color.parseColor("#1E1B4B") }
        val themeBorderColor = try { Color.parseColor(customProps["THEME_BORDER_COLOR"] ?: "#4F46E5") } catch (e: Exception) { Color.parseColor("#4F46E5") }

        val qrBitmap = UpiQrCodeHelper.loadSavedQrBitmap(context)

        val width = 800
        val headerHeight = 165
        val maxBoxLines = maxOf(customerBoxLines.size, loanBoxLines.size, 3)
        val infoSectionHeight = 110 + (maxBoxLines * 36)
        val tableHeaderHeight = 60
        val rowHeight = 50
        val footerHeight = 115 + (footerLines.size.coerceAtLeast(2) * 24)
        val minRows = 5
        val rowsCount = maxOf(payments.size, minRows)
        val totalHeight = headerHeight + infoSectionHeight + tableHeaderHeight + (rowsCount * rowHeight) + footerHeight

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        // 2. Header Banner
        val headerGradient = LinearGradient(
            0f, 0f, width.toFloat(), headerHeight.toFloat(),
            startColor,
            endColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = headerGradient
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), paint)
        paint.shader = null

        val headerCenterX = if (qrBitmap != null) (width - 135f) / 2f else width / 2f

        // Header Text
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(resolvedHeaderTitle.uppercase(), headerCenterX, 65f, paint)

        paint.color = Color.parseColor("#CBD5E1")
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(resolvedHeaderSubtitle.uppercase(), headerCenterX, 105f, paint)

        paint.textSize = 15f
        paint.color = Color.parseColor("#94A3B8")
        canvas.drawText("Generated on: ${dateTimeFormat.format(Date())}", headerCenterX, 138f, paint)

        // 2b. Top-Right QR Code pasted directly without background card
        if (qrBitmap != null) {
            val qrDestRect = RectF(width - 140f, 18f, width - 26f, 132f)
            canvas.drawBitmap(qrBitmap, null, qrDestRect, null)
        }

        // 3. Info Section (Customer & Loan Summary Boxes)
        val boxTop = headerHeight + 25f
        val boxBottom = headerHeight + infoSectionHeight - 25f

        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        val leftBox = RectF(30f, boxTop, 385f, boxBottom)
        val rightBox = RectF(415f, boxTop, 770f, boxBottom)
        canvas.drawRoundRect(leftBox, 16f, 16f, paint)
        canvas.drawRoundRect(rightBox, 16f, 16f, paint)

        paint.color = themeBorderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(leftBox, 16f, 16f, paint)
        canvas.drawRoundRect(rightBox, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        // Customer Details Box Content
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CUSTOMER DETAILS", 50f, boxTop + 35f, paint)

        var custLineY = boxTop + 72f
        customerBoxLines.forEachIndexed { idx, line ->
            if (idx == 0) {
                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 21f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                paint.color = Color.parseColor("#334155")
                paint.textSize = 18f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(line.take(36), 50f, custLineY, paint)
            custLineY += 34f
        }

        // Loan Summary Box Content
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("LOAN SUMMARY", 435f, boxTop + 35f, paint)

        val totalLoan = MoneyMath.add(loan.loanAmount, loan.interestAmount)
        val actualPaid = if (payments.isNotEmpty()) payments.sumOf { it.amountPaid } else loan.paidAmount
        val balance = MoneyMath.subtract(totalLoan, actualPaid).coerceAtLeast(0.0)

        var loanLineY = boxTop + 72f
        loanBoxLines.forEach { line ->
            val lower = line.lowercase(Locale.getDefault())
            when {
                lower.contains("paid") -> {
                    paint.color = Color.parseColor("#059669")
                    paint.textSize = 18f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                lower.contains("outstanding") || lower.contains("balance") -> {
                    paint.color = if (balance > 0) Color.parseColor("#DC2626") else Color.parseColor("#059669")
                    paint.textSize = 19f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                lower.contains("total") || lower.contains("loan") -> {
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 18f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                else -> {
                    paint.color = Color.parseColor("#334155")
                    paint.textSize = 17f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
            }
            canvas.drawText(line.take(36), 435f, loanLineY, paint)
            loanLineY += 34f
        }

        // 4. Dynamic Transaction Table Header (User-configurable & reorderable columns)
        val activeColumns = parseStatementTableColumns(
            tableColumnsCsv.ifBlank { prefs.getString("statement_table_columns", DEFAULT_STATEMENT_TABLE_COLUMNS).orEmpty() }
        )
        val tableTop = (headerHeight + infoSectionHeight).toFloat()
        paint.color = Color.parseColor("#0F172A")
        val tableHeaderRect = RectF(30f, tableTop, 770f, tableTop + tableHeaderHeight)
        canvas.drawRoundRect(tableHeaderRect, 10f, 10f, paint)

        val tableLeft = 50f
        val tableRight = 750f
        val usableTableWidth = tableRight - tableLeft
        val colCount = activeColumns.size.coerceAtLeast(1)
        val colSlotWidth = usableTableWidth / colCount.toFloat()

        paint.color = Color.WHITE
        paint.textSize = if (colCount >= 5) 14.5f else 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        activeColumns.forEachIndexed { colIdx, colKey ->
            val headerText = getColumnHeaderLabel(colKey)
            when {
                colIdx == colCount - 1 && colCount > 1 -> {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(headerText, tableRight, tableTop + 36f, paint)
                }
                colIdx == 0 -> {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(headerText, tableLeft, tableTop + 36f, paint)
                }
                else -> {
                    paint.textAlign = Paint.Align.CENTER
                    val centerX = tableLeft + (colIdx + 0.5f) * colSlotWidth
                    canvas.drawText(headerText, centerX, tableTop + 36f, paint)
                }
            }
        }

        // 5. Dynamic Transaction Table Rows
        var currentY = tableTop + tableHeaderHeight
        val sortedPayments = payments.sortedBy { it.weekNumber }

        if (sortedPayments.isEmpty()) {
            paint.color = Color.WHITE
            canvas.drawRect(30f, currentY, 770f, currentY + (minRows * rowHeight), paint)

            paint.color = Color.parseColor("#94A3B8")
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("No payment records registered yet.", width / 2f, currentY + 120f, paint)
            currentY += (minRows * rowHeight)
        } else {
            val cellTextSize = if (colCount >= 5) 15f else 17.5f
            sortedPayments.forEachIndexed { index, payment ->
                paint.color = if (index % 2 == 0) Color.WHITE else Color.parseColor("#F1F5F9")
                canvas.drawRect(30f, currentY, 770f, currentY + rowHeight, paint)

                paint.color = Color.parseColor("#E2E8F0")
                paint.strokeWidth = 1f
                canvas.drawLine(30f, currentY + rowHeight, 770f, currentY + rowHeight, paint)

                activeColumns.forEachIndexed { colIdx, colKey ->
                    val cellText = formatPaymentCellForColumn(colKey, payment)
                    val isAmountCol = colKey.equals("AMOUNT", ignoreCase = true)
                    paint.typeface = Typeface.create(
                        Typeface.DEFAULT,
                        if (isAmountCol) Typeface.BOLD else Typeface.NORMAL
                    )
                    paint.textSize = cellTextSize
                    paint.color = if (isAmountCol) Color.parseColor("#059669") else Color.parseColor("#1E293B")

                    when {
                        colIdx == colCount - 1 && colCount > 1 -> {
                            paint.textAlign = Paint.Align.RIGHT
                            canvas.drawText(cellText, tableRight, currentY + 32f, paint)
                        }
                        colIdx == 0 -> {
                            paint.textAlign = Paint.Align.LEFT
                            canvas.drawText(cellText, tableLeft, currentY + 32f, paint)
                        }
                        else -> {
                            paint.textAlign = Paint.Align.CENTER
                            val centerX = tableLeft + (colIdx + 0.5f) * colSlotWidth
                            canvas.drawText(cellText, centerX, currentY + 32f, paint)
                        }
                    }
                }

                currentY += rowHeight
            }

            val remainingDummyRows = minRows - sortedPayments.size
            for (i in 0 until remainingDummyRows) {
                val rowIdx = sortedPayments.size + i
                paint.color = if (rowIdx % 2 == 0) Color.WHITE else Color.parseColor("#F1F5F9")
                canvas.drawRect(30f, currentY, 770f, currentY + rowHeight, paint)
                currentY += rowHeight
            }
        }

        // Table outer border
        paint.color = Color.parseColor("#CBD5E1")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(30f, tableTop + tableHeaderHeight, 770f, currentY, paint)
        paint.style = Paint.Style.FILL

        // 6. Footer
        val footerCenterY = currentY + 46f
        paint.textAlign = Paint.Align.CENTER
        footerLines.forEachIndexed { idx, line ->
            if (idx == 0) {
                paint.color = Color.parseColor("#4F46E5")
                paint.textSize = 20f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                paint.color = Color.parseColor("#64748B")
                paint.textSize = 15f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(line.take(65), width / 2f, footerCenterY + (idx * 26f), paint)
        }

        val displayStatus = resolveDisplayLoanStatus(loan.status, isClosedLoan || (totalLoan > 0 && balance <= 0.0))
        val statusBadgeY = footerCenterY + (footerLines.size.coerceAtLeast(1) * 26f) + 10f
        paint.color = when (displayStatus) {
            "CLOSED" -> Color.parseColor("#059669")
            "SUSPENDED" -> Color.parseColor("#DC2626")
            else -> Color.parseColor("#EA580C")
        }
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ACCOUNT STATUS: $displayStatus", width / 2f, statusBadgeY, paint)

        return bitmap
    }

    private fun writeStatementToCacheUri(context: Context, bitmap: Bitmap, customerName: String): Uri? {
        return try {
            val cachePath = File(context.cacheDir, "statements")
            cachePath.mkdirs()
            val cleanName = customerName.replace(Regex("[^a-zA-Z0-9]"), "_").ifBlank { "Customer" }
            val file = File(cachePath, "Statement_${cleanName}.png")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareStatementBitmap(context: Context, bitmap: Bitmap, customerName: String) {
        val contentUri = writeStatementToCacheUri(context, bitmap, customerName) ?: return
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Customer Account Statement for $customerName")
                type = "image/png"
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Share Account Statement via")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareStatementViaWhatsApp(
        context: Context,
        bitmap: Bitmap,
        customerName: String,
        customerPhone: String,
        captionText: String = "Account Statement for $customerName"
    ) {
        val contentUri = writeStatementToCacheUri(context, bitmap, customerName) ?: return
        var cleanPhone = customerPhone.filter { it.isDigit() }
        if (cleanPhone.length == 11 && cleanPhone.startsWith("0")) {
            cleanPhone = cleanPhone.substring(1)
        }
        if (cleanPhone.length == 10) {
            cleanPhone = "91$cleanPhone"
        }

        val packagesToTry = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packagesToTry) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, captionText)
                    if (cleanPhone.isNotBlank()) {
                        putExtra("jid", "$cleanPhone@s.whatsapp.net")
                    }
                    clipData = android.content.ClipData.newRawUri("", contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(pkg)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Fallback to next package or generic share
            }
        }
        shareStatementBitmap(context, bitmap, customerName)
    }

    fun shareStatementViaSms(
        context: Context,
        customerName: String,
        customerPhone: String,
        loan: LoanCycle,
        payments: List<WeeklyPayment>,
        businessName: String,
        isClosedLoan: Boolean = false
    ) {
        val totalDue = MoneyMath.add(loan.loanAmount, loan.interestAmount)
        val paidAmt = if (payments.isNotEmpty()) payments.sumOf { it.amountPaid } else loan.paidAmount
        val balance = MoneyMath.subtract(totalDue, paidAmt).coerceAtLeast(0.0)
        val statusLabel = resolveDisplayLoanStatus(loan.status, isClosedLoan || (totalDue > 0 && balance <= 0.0))
        val effectiveBiz = if (businessName.isBlank() || businessName.trim().equals("Muneeswaran P", ignoreCase = true)) {
            "MD Finance"
        } else {
            businessName.trim()
        }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDateStr = if (loan.startDate > 0L) dateFormat.format(Date(loan.startDate)) else dateFormat.format(Date())

        val summarySms = buildString {
            appendLine("$effectiveBiz - Account Statement")
            appendLine("Customer: $customerName")
            appendLine("Loan Date: $startDateStr")
            appendLine("Total Loan: ₹${CurrencyFormatter.format(totalDue)}")
            appendLine("Total Paid (${payments.size}/${loan.totalWeeks} wks): ₹${CurrencyFormatter.format(paidAmt)}")
            appendLine("Balance: ₹${CurrencyFormatter.format(balance)}")
            append("Status: $statusLabel")
        }

        val prefs = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
        val smsPaused = prefs.getBoolean("sms_paused", false)
        val simSelection = prefs.getString("sim_selection", "SIM 1") ?: "SIM 1"
        com.example.service.SmsService.sendSmsIntent(
            context = context,
            phone = customerPhone,
            text = summarySms,
            smsPaused = smsPaused,
            simSelection = simSelection
        )
    }

    fun saveStatementInternally(context: Context, bitmap: Bitmap, customerName: String): String? {
        return try {
            val dir = File(context.filesDir, "saved_statements")
            dir.mkdirs()
            val cleanName = customerName.replace(Regex("[^a-zA-Z0-9]"), "_").ifBlank { "Customer" }
            val file = File(dir, "Statement_${cleanName}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(context, "Statement saved in app records (${file.name})", Toast.LENGTH_SHORT).show()
            file.absolutePath
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save statement: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun downloadStatementToDevice(context: Context, bitmap: Bitmap, customerName: String): String? {
        val savedPath = UpiQrCodeHelper.saveStatementToDownloads(context, bitmap, customerName)
        if (savedPath != null) {
            Toast.makeText(context, "Downloaded to $savedPath", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Failed to download statement", Toast.LENGTH_SHORT).show()
        }
        return savedPath
    }
}
