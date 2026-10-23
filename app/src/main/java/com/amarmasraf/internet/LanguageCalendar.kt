package com.amarmasraf.internet

import android.content.Context
import java.util.Calendar
import java.util.Locale

object LanguageCalendar {
    private const val PREFS = "settings"
    private const val LANGUAGE = "language"
    private const val CALENDAR = "calendar"

    fun isEnglish(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(LANGUAGE, "system")) {
            "en" -> true
            "fa" -> false
            else -> Locale.getDefault().language.equals("en", ignoreCase = true)
        }
    }

    fun isSolar(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CALENDAR, "solar") != "gregorian"

    fun text(context: Context, persian: String, english: String): String =
        if (isEnglish(context)) english else persian

    fun date(context: Context, millis: Long, separator: String = "/"): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH) + 1
        val day = c.get(Calendar.DAY_OF_MONTH)
        val parts = if (isSolar(context)) toJalali(year, month, day) else intArrayOf(year, month, day)
        return String.format(Locale.US, "%04d%s%02d%s%02d", parts[0], separator, parts[1], separator, parts[2])
    }

    fun monthYear(context: Context, millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val parts = if (isSolar(context)) toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
            else intArrayOf(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return String.format(Locale.US, "%02d/%04d", parts[1], parts[0])
    }

    fun year(context: Context, millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return (if (isSolar(context)) toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))[0]
            else c.get(Calendar.YEAR)).toString()
    }

    // Gregorian-to-Jalali conversion, using the 33-year Jalali cycle.
    private fun toJalali(gyInput: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy = gyInput
        var jy = 0
        if (gy > 1600) { jy = 979; gy -= 1600 } else { gy -= 621 }
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + gdm[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
        val jm: Int
        val jd: Int
        if (days < 186) { jm = 1 + days / 31; jd = 1 + days % 31 }
        else { jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30 }
        return intArrayOf(jy, jm, jd)
    }
}
