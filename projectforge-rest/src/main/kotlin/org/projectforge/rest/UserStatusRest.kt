/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.rest

import org.projectforge.SystemAlertMessage
import org.projectforge.business.fibu.EmployeeDao
import org.projectforge.business.user.filter.UserFilter
import org.projectforge.common.DateFormatType
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.time.DateFormats
import org.projectforge.framework.time.TimeNotation
import org.projectforge.rest.config.Rest
import org.projectforge.rest.pub.SystemStatusRest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*
import javax.servlet.http.HttpServletRequest

/**
 * This rest service should be available without login (public).
 */
@RestController
@RequestMapping("${Rest.URL}/userStatus")
open class UserStatusRest {
    companion object {
        internal val WEEKDAYS = arrayOf("-", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    }

    @Autowired
    private lateinit var systemStatusRest: SystemStatusRest

    @Autowired
    private lateinit var employeeDao: EmployeeDao

    data class UserData(var username: String? = null,
                        var organization: String? = null,
                        var fullname: String? = null,
                        var lastName: String? = null,
                        var firstName: String? = null,
                        var userId: Int? = null,
                        var employeeId: Int? = null,
                        var locale: Locale? = null,
                        var timeZone: String? = null,
                        var dateFormat: String? = null,
                        var dateFormatShort: String? = null,
                        var timestampFormatMinutes: String? = null,
                        var timestampFormatSeconds: String? = null,
                        var timestampFormatMillis: String? = null,
                        var jsDateFormat: String? = null,
                        var jsDateFormatShort: String? = null,
                        var jsTimestampFormatMinutes: String? = null,
                        var jsTimestampFormatSeconds: String? = null,
                        var firstDayOfWeekNo: Int? = null,
                        var firstDayOfWeek: String? = null,
                        var timeNotation: TimeNotation? = null)

    data class Result(val userData: UserData,
                      val systemData: SystemStatusRest.SystemData,
                      val alertMessage: String? = null)

    private val log = org.slf4j.LoggerFactory.getLogger(UserStatusRest::class.java)

    @GetMapping
    fun loginTest(request: HttpServletRequest): ResponseEntity<Result> {
        val user = UserFilter.getUser(request) ?: return ResponseEntity(HttpStatus.UNAUTHORIZED)
        var employeeId: Int? = user.getTransientAttribute("employeeId") as Int?
        if (employeeId == null) {
            employeeId = employeeDao.getEmployeeIdByByUserId(user.id) ?: -1
            user.setTransientAttribute("employeeId", employeeId) // Avoid multiple calls of db
        }
        val firstDayOfWeekNo = ThreadLocalUserContext.getFirstDayOfWeekValue() // Mon - 1, Tue - 2, ..., Sun - 7
        val userData = UserData(username = user.username,
                organization = user.organization,
                fullname = user.getFullname(),
                firstName = user.firstname,
                lastName = user.lastname,
                userId = user.id,
                employeeId = employeeId,
                locale = ThreadLocalUserContext.getLocale(),
                timeZone = ThreadLocalUserContext.getTimeZone().id,
                timeNotation = DateFormats.ensureAndGetDefaultTimeNotation(),
                dateFormat = DateFormats.getFormatString(DateFormatType.DATE),
                dateFormatShort = DateFormats.getFormatString(DateFormatType.DATE_SHORT),
                timestampFormatMinutes = DateFormats.getFormatString(DateFormatType.DATE_TIME_MINUTES),
                timestampFormatSeconds = DateFormats.getFormatString(DateFormatType.DATE_TIME_SECONDS),
                timestampFormatMillis = DateFormats.getFormatString(DateFormatType.DATE_TIME_MILLIS),
                firstDayOfWeekNo = firstDayOfWeekNo,
                firstDayOfWeek = WEEKDAYS[firstDayOfWeekNo])
        userData.jsDateFormat = convertToJavascriptFormat(userData.dateFormat)
        userData.jsDateFormatShort = convertToJavascriptFormat(userData.dateFormatShort)
        userData.jsTimestampFormatMinutes = convertToJavascriptFormat(userData.timestampFormatMinutes)
        userData.jsTimestampFormatSeconds = convertToJavascriptFormat(userData.timestampFormatSeconds)

        val systemData = systemStatusRest.systemData
        return ResponseEntity<Result>(Result(userData, systemData, SystemAlertMessage.alertMessage), HttpStatus.OK)
    }

    /**
     * 'dd.MM.yyyy HH:mm:ss' -> 'DD.MM.YYYY HH:mm:ss'.
     */
    private fun convertToJavascriptFormat(dateFormat: String?): String? {
        if (dateFormat == null) return null
        return dateFormat.replace('d', 'D', false)
                .replace('y', 'Y', false)
    }
}
