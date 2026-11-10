package com.muddassir.squadgym

import com.muddassir.squadgym.core.util.DateText
import com.muddassir.squadgym.core.util.NumberText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure JVM tests for the date/format layer. These guard the rules that are easy to
 * get subtly wrong: gym-local day strings, expiry labels and membership countdowns.
 */
class DateTextTest {

    @Test
    fun `formats server dates without timezone drift`() {
        assertEquals("Tue, 10 Nov 2026", DateText.full("2026-11-10"))
        assertEquals("10 Nov", DateText.short("2026-11-10"))
        assertEquals("November 2026", DateText.monthLabel("2026-11"))
        assertEquals("Tue", DateText.weekdayShort("2026-11-10"))
    }

    @Test
    fun `invalid dates degrade safely`() {
        assertEquals("—", DateText.full("not-a-date"))
        assertEquals("—", DateText.short(""))
        assertFalse(DateText.isFutureMonth("2026-10", "2026-11-10"))
        assertTrue(DateText.isFutureMonth("2026-12", "2026-11-10"))
    }

    @Test
    fun `expiry labels cover past today tomorrow and future`() {
        val today = "2026-11-10"
        assertEquals("Expired 3 days ago", DateText.expiryLabel("2026-11-07", today))
        assertEquals("Expires today", DateText.expiryLabel(today, today))
        assertEquals("Expires tomorrow", DateText.expiryLabel("2026-11-11", today))
        assertEquals("Expires in 5 days", DateText.expiryLabel("2026-11-15", today))
        assertEquals("No membership", DateText.expiryLabel(null, today))
    }

    @Test
    fun `relative days reads naturally`() {
        val today = "2026-11-10"
        assertEquals("today", DateText.relativeDays(today, today))
        assertEquals("tomorrow", DateText.relativeDays("2026-11-11", today))
        assertEquals("in 3 days", DateText.relativeDays("2026-11-13", today))
        assertEquals("yesterday", DateText.relativeDays("2026-11-09", today))
    }

    @Test
    fun `month navigation clamps at year boundaries`() {
        assertEquals("2026-12", DateText.shiftMonth("2026-11", 1))
        assertEquals("2025-12", DateText.shiftMonth("2026-01", -1))
        assertEquals("2027-02", DateText.shiftMonth("2026-11", 3))
    }

    @Test
    fun `month of an iso date is the calendar month`() {
        assertEquals("2026-11", DateText.monthOf("2026-11-10"))
    }

    @Test
    fun `countdown formats minutes and seconds`() {
        assertEquals("5m 00s", DateText.countdown(300))
        assertEquals("4m 59s", DateText.countdown(299))
        assertEquals("45s", DateText.countdown(45))
        assertEquals("0s", DateText.countdown(-10))
    }

    @Test
    fun `money formatting handles zero and currencies`() {
        assertEquals("Free", NumberText.money(0, "INR"))
        assertEquals("₹1500", NumberText.money(150_000, "INR"))
        assertEquals("$25", NumberText.money(2500, "USD"))
    }

    @Test
    fun `gym timezone is applied to instants`() {
        // 19:00Z is 00:30 IST the next day.
        val ist = java.time.ZoneId.of("Asia/Kolkata")
        val instant = java.time.Instant.parse("2026-11-10T19:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 11), instant.atZone(ist).toLocalDate())
        assertEquals("12:30 AM", DateText.clock("2026-11-10T19:00:00Z", "Asia/Kolkata"))
    }

    @Test
    fun `percent formatting trims trailing zeros`() {
        assertEquals("100%", NumberText.percent(100.0))
        assertEquals("71.4%", NumberText.percent(71.43))
    }
}
