package com.example

import com.example.data.Customer
import com.example.data.LoanCycle
import com.example.data.WeeklyPayment
import com.example.ui.getOverviewListForDay
import com.example.util.MoneyMath
import com.example.util.StatementGenerator
import com.example.util.UpiQrCodeHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExampleUnitTest {

    @Test
    fun moneyMath_realizedProfitStrictlyExcludesUpfrontDeductions() {
        // Loan: Principal 8000, Interest 2000 (Total 10000), Upfront Deduction 500
        // Installment collected: 1000 -> realized interest share must be strictly 200.00 (never +500 deduction)
        val realizedProfit = MoneyMath.realizedInterestShare(
            paymentAmount = 1000.0,
            principalAmount = 8000.0,
            interestAmount = 2000.0
        )
        assertEquals(200.0, realizedProfit, 0.001)
    }

    @Test
    fun moneyMath_badDebtLossBreakdownCalculatesPrincipalInterestAndTotalLoss() {
        // Principal = 8000, Interest = 2000 (Total = 10000), Paid = 3000 (30% paid -> 70% unpaid)
        // Principal Loss = 8000 * 0.70 = 5600.00
        // Interest Loss  = 2000 * 0.70 = 1400.00
        // Total Loss     = 7000.00
        val (principalLoss, interestLoss, totalLoss) = MoneyMath.calculateBadDebtLossBreakdown(
            principalAmount = 8000.0,
            interestAmount = 2000.0,
            paidAmount = 3000.0
        )
        assertEquals(5600.0, principalLoss, 0.001)
        assertEquals(1400.0, interestLoss, 0.001)
        assertEquals(7000.0, totalLoss, 0.001)
    }

    @Test
    fun upiQrCodeHelper_parsesUpiDeepLinkAndVpaCorrectly() {
        val rawQr = "upi://pay?pa=9440736893@ptyes&pn=MD%20Finance&cu=INR"
        val parsed = UpiQrCodeHelper.parseUpiPayload(rawQr)
        assertNotNull(parsed)
        assertEquals("9440736893@ptyes", parsed!!.upiId)
        assertEquals(rawQr, parsed.upiUri)
        assertEquals("MD Finance", parsed.payeeName)
    }

    @Test
    fun statementGenerator_resolvesClosedAndSuspendedStatuses() {
        assertEquals("SUSPENDED", StatementGenerator.resolveDisplayLoanStatus("SUSPENDED", false))
        assertEquals("SUSPENDED", StatementGenerator.resolveDisplayLoanStatus("BAD_DEBT", true))
        assertEquals("CLOSED", StatementGenerator.resolveDisplayLoanStatus("PAID", false))
        assertEquals("CLOSED", StatementGenerator.resolveDisplayLoanStatus("CLOSED", true))
        assertEquals("ACTIVE", StatementGenerator.resolveDisplayLoanStatus("ACTIVE", false))
    }

    @Test
    fun overviewList_excludesBadDebtAndFridayFromActiveRoutes() {
        val customers = listOf(
            Customer(id = 1, name = "Active Monday", phone = "9876543210", collectionDay = "Monday", customOrder = 1, status = "ACTIVE"),
            Customer(id = 2, name = "Bad Debt Monday", phone = "9876543211", collectionDay = "Monday", customOrder = 2, status = "BAD_DEBT"),
            Customer(id = 3, name = "Friday Ignored", phone = "9876543212", collectionDay = "Friday", customOrder = 1, status = "ACTIVE")
        )
        val activeMondayList = getOverviewListForDay(
            day = "Monday",
            allCustomers = customers,
            activeLoanCycles = emptyList(),
            allLoanCycles = emptyList(),
            allPayments = emptyList(),
            search = "",
            sortMode = "ROUTE",
            onlyBadDebts = false
        )
        assertEquals(1, activeMondayList.size)
        assertEquals("Active Monday", activeMondayList.first().customer.name)

        val badDebtMondayList = getOverviewListForDay(
            day = "Monday",
            allCustomers = customers,
            activeLoanCycles = emptyList(),
            allLoanCycles = emptyList(),
            allPayments = emptyList(),
            search = "",
            sortMode = "ROUTE",
            onlyBadDebts = true
        )
        assertEquals(1, badDebtMondayList.size)
        assertEquals("Bad Debt Monday", badDebtMondayList.first().customer.name)
    }

    @Test
    fun cloudSettingsSync_latestModifiedTimestampWinsAutomatically() {
        // Remote is newer than local -> apply remote settings
        assertTrue(
            com.example.network.CloudLedgerSyncEngine.shouldApplyRemoteSettings(
                localLastModified = 1000L,
                remoteLastModified = 2000L,
                hasLocalPendingSync = true
            )
        )
        // Local is newer than remote -> keep local settings
        assertFalse(
            com.example.network.CloudLedgerSyncEngine.shouldApplyRemoteSettings(
                localLastModified = 3000L,
                remoteLastModified = 2000L,
                hasLocalPendingSync = false
            )
        )
        // Fresh install (local = 0, no pending edits) and remote exists -> apply remote settings
        assertTrue(
            com.example.network.CloudLedgerSyncEngine.shouldApplyRemoteSettings(
                localLastModified = 0L,
                remoteLastModified = 1500L,
                hasLocalPendingSync = false
            )
        )
    }

    @Test
    fun cloudSettingsSync_favoriteCustomerUuidsRoundTrip() {
        val original = setOf("cust-uuid-2", "cust-uuid-1", "  cust-uuid-2 ")
        val serializedList = com.example.network.CloudLedgerSyncEngine.serializeFavoriteUuids(original)
        assertEquals(listOf("cust-uuid-1", "cust-uuid-2"), serializedList)

        val parsedFromCollection = com.example.network.CloudLedgerSyncEngine.parseFavoriteUuids(serializedList)
        assertEquals(setOf("cust-uuid-1", "cust-uuid-2"), parsedFromCollection)

        val parsedFromJson = com.example.network.CloudLedgerSyncEngine.parseFavoriteUuids("[\"cust-uuid-1\",\"cust-uuid-2\"]")
        assertEquals(setOf("cust-uuid-1", "cust-uuid-2"), parsedFromJson)
    }

    @Test
    fun statementGenerator_tableColumnsAddRemoveReorderAndFormatCorrectly() {
        // Custom reordered columns with REMARKS added and TIME removed
        val customColumns = StatementGenerator.parseStatementTableColumns("DATE,WEEK_NO,AMOUNT,MODE,REMARKS")
        assertEquals(listOf("DATE", "WEEK_NO", "AMOUNT", "MODE", "REMARKS"), customColumns)

        // Blank or invalid falls back to default 5 columns
        val defaultColumns = StatementGenerator.parseStatementTableColumns(" , INVALID_COL ")
        assertEquals(listOf("WEEK_NO", "DATE", "MODE", "TIME", "AMOUNT"), defaultColumns)

        val fixedTimestamp = 1775966400000L
        val samplePayment = WeeklyPayment(
            id = 10,
            loanCycleId = 1,
            weekNumber = 4,
            amountPaid = 1250.0,
            paymentDate = fixedTimestamp,
            notes = "UPI",
            upiTxnId = "TXN9988"
        )
        assertEquals("Week 4", StatementGenerator.formatPaymentCellForColumn("WEEK_NO", samplePayment))
        assertTrue(StatementGenerator.formatPaymentCellForColumn("DATE", samplePayment).isNotBlank())
        assertTrue(StatementGenerator.formatPaymentCellForColumn("TIME", samplePayment).isNotBlank())
        assertEquals("UPI", StatementGenerator.formatPaymentCellForColumn("MODE", samplePayment))
        assertEquals("₹1,250", StatementGenerator.formatPaymentCellForColumn("AMOUNT", samplePayment))
        assertEquals("TXN9988", StatementGenerator.formatPaymentCellForColumn("REMARKS", samplePayment))
    }

    @Test
    fun upiQrCodeHelper_generatesQrBitmapFromScannedPayload() {
        val rawQr = "upi://pay?pa=9440736893@ptyes&pn=MD%20Finance&cu=INR"
        val bitmap = UpiQrCodeHelper.generateQrBitmapFromPayload(rawQr, 256)
        assertNotNull(bitmap)
        assertEquals(256, bitmap!!.width)
        assertEquals(256, bitmap.height)
    }
}


