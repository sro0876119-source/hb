package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Customer
import com.example.util.CsvBackupHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MD Finance", appName)
    }

    @Test
    fun `csv bulk import imports customers loans payments and excludes Friday`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val header = buildString {
                append("Customer UUID,Route No (Sort Order),Client Name,Phone Number,City,SMS Settings (Weekly & Entry Confirmation),Loan ID (UUID),Amount Disbursed,Principle (₹),Interest (₹),Date of Dispersal")
                for (w in 1..30) {
                    append(",Week $w Date & Time,Week $w Amt Received")
                }
                append(",Collection Day\n")
            }

            val row1 = buildString {
                append("cust-uuid-1,1,Arun Kumar,9876543210,Madurai,\"Weekly Reminder: YES, Entry Confirmation: YES\",loan-uuid-1,9500,10000,2000,2026-01-01")
                append(",2026-01-08 10:00:00,1200")
                for (w in 2..30) append(",,")
                append(",Monday\n")
            }

            val rowFridayExcluded = buildString {
                append("cust-uuid-fri,2,Friday Ignored,9876543211,Madurai,\"Weekly Reminder: NO, Entry Confirmation: NO\",loan-uuid-fri,5000,5000,1000,2026-01-01")
                for (w in 1..30) append(",,")
                append(",Friday\n")
            }

            val rowSunMrg = buildString {
                append("=== DAY_GROUP: Sunday mrg ===\n")
                append("cust-uuid-sun,1,Bala Murugan,9876543212,Chennai,\"Weekly Reminder: YES, Entry Confirmation: NO\",loan-uuid-sun,19000,20000,4000,2026-01-05")
                append(",2026-01-12 09:30:00,2400")
                for (w in 2..30) append(",,")
                append("\n")
            }

            val csvContent = header + row1 + rowFridayExcluded + rowSunMrg
            val ok = CsvBackupHelper.importCsvIntoDay(context, csvContent, "ALL", db)
            assertTrue(ok)

            val customers = db.collectionDao().getAllCustomersOnce()
            assertEquals(2, customers.size)
            assertTrue(customers.none { it.collectionDay.equals("Friday", ignoreCase = true) })
            assertTrue(customers.any { it.name == "Arun Kumar" && it.collectionDay == "Monday" })
            assertTrue(customers.any { it.name == "Bala Murugan" && it.collectionDay == "Sunday mrg" })

            val loans = db.collectionDao().getAllLoanCyclesOnce()
            assertEquals(2, loans.size)
            val arunLoan = loans.first { it.uuid == "loan-uuid-1" }
            assertEquals(10000.0, arunLoan.loanAmount, 0.01)
            assertEquals(500.0, arunLoan.deduction, 0.01)
            assertEquals(1200.0, arunLoan.paidAmount, 0.01)
        } finally {
            db.close()
        }
    }

    @Test
    fun `xlsx binary workbook import extracts sheets and rejects corrupted binary garbage`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            // Build a valid minimal .xlsx in memory with Monday and Friday sheets
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("xl/workbook.xml"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <workbook><sheets>
                        <sheet name="Monday" sheetId="1" r:id="rId1"/>
                        <sheet name="Friday" sheetId="2" r:id="rId2"/>
                    </sheets></workbook>""".trimIndent().toByteArray()
                )
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <Relationships>
                        <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                        <Relationship Id="rId2" Target="worksheets/sheet2.xml"/>
                    </Relationships>""".trimIndent().toByteArray()
                )
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <worksheet><sheetData>
                        <row r="1">
                            <c r="A1" t="inlineStr"><is><t>Customer UUID</t></is></c>
                            <c r="B1" t="inlineStr"><is><t>Route No (Sort Order)</t></is></c>
                            <c r="C1" t="inlineStr"><is><t>Client Name</t></is></c>
                        </row>
                        <row r="2">
                            <c r="A2" t="inlineStr"><is><t>xlsx-cust-1</t></is></c>
                            <c r="B2"><v>1</v></c>
                            <c r="C2" t="inlineStr"><is><t>Karthik Raja</t></is></c>
                            <c r="D2" t="inlineStr"><is><t>9988776655</t></is></c>
                            <c r="E2" t="inlineStr"><is><t>Salem</t></is></c>
                            <c r="F2" t="inlineStr"><is><t>Weekly Reminder: YES, Entry Confirmation: YES</t></is></c>
                            <c r="G2" t="inlineStr"><is><t>xlsx-loan-1</t></is></c>
                            <c r="H2"><v>15000</v></c>
                            <c r="I2"><v>15000</v></c>
                            <c r="J2"><v>3000</v></c>
                            <c r="K2" t="inlineStr"><is><t>2026-02-01</t></is></c>
                        </row>
                    </sheetData></worksheet>""".trimIndent().toByteArray()
                )
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <worksheet><sheetData>
                        <row r="1">
                            <c r="A1" t="inlineStr"><is><t>xlsx-fri-1</t></is></c>
                            <c r="C1" t="inlineStr"><is><t>Should Not Import</t></is></c>
                        </row>
                    </sheetData></worksheet>""".trimIndent().toByteArray()
                )
                zos.closeEntry()
            }

            val imported = CsvBackupHelper.importBackupFromBytes(context, baos.toByteArray(), "ALL", db)
            assertTrue(imported)

            val customers = db.collectionDao().getAllCustomersOnce()
            assertEquals(1, customers.size)
            assertEquals("Karthik Raja", customers[0].name)
            assertEquals("Monday", customers[0].collectionDay)

            // Verify corrupted binary text is detected and rejected
            val corruptedString = "PK\u0003\u0004\u0000\uFFFD\uFFFD,garbage,binary,row"
            assertTrue(CsvBackupHelper.isBinaryOrCorruptedText(corruptedString))
            var rejected = false
            try {
                CsvBackupHelper.importCsvIntoDay(context, corruptedString, "ALL", db)
            } catch (e: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertFalse( db.collectionDao().getAllCustomersOnce().any { it.name.contains("garbage") })
        } finally {
            db.close()
        }
    }
}
