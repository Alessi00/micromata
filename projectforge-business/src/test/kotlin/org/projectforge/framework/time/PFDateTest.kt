/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.framework.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.projectforge.test.TestSetup
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.Month
import java.util.*

class PFDateTest {

    @Test
    fun convertTest() {
        // User's time zone is "Europe/Berlin": "UTC+2". Therefore local date should be 2019-04-01 00:00:00
        var date = PFDay.from(LocalDate.of(2019, Month.APRIL, 10))

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z")
        formatter.timeZone = TimeZone.getTimeZone("UTC")

        var sqlDate = date!!.sqlDate
        assertEquals("2019-04-10", sqlDate.toString())

        date = PFDay.from(sqlDate)
        checkDate(date!!.date, 2019, Month.APRIL, 10)
    }

    @Test
    fun baseTest() {
        var date = PFDay.from(LocalDate.of(2019, Month.APRIL, 10))!!
        assertEquals(2019, date.year)
        assertEquals(Month.APRIL, date.month)
        assertEquals(4, date.monthValue)
        assertEquals(1, date.beginOfMonth.dayOfMonth)
        assertEquals(30, date.endOfMonth.dayOfMonth)

        date = PFDay.from(LocalDate.of(2019, Month.JANUARY, 1))!!
        assertEquals(2019, date.year)
        assertEquals(Month.JANUARY, date.month)
        assertEquals(1, date.monthValue)
        assertEquals(1, date.beginOfMonth.dayOfMonth)

        date = PFDay.from(LocalDate.of(2019, Month.JANUARY, 31))!!.plusMonths(1)
        assertEquals(2019, date.year)
        assertEquals(Month.FEBRUARY, date.month)
        assertEquals(28, date.dayOfMonth)

        val dateTime = PFDateTimeUtils.parseUTCDate("2019-11-30 23:00")!!
        date = PFDay.from(dateTime.utilDate)!!
        assertEquals(2019, date.year)
        assertEquals(Month.DECEMBER, date.month)
        assertEquals(1, date.dayOfMonth)
    }

    private fun checkDate(date: LocalDate, year: Int, month: Month, dayOfMonth: Int) {
        assertEquals(year, date.year, "Year check failed.")
        assertEquals(month, date.month, "Month check failed.")
        assertEquals(dayOfMonth, date.dayOfMonth, "Day check failed.")
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            TestSetup.init()
        }
    }
}
