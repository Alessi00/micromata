/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2021 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.framework.i18n

import java.util.*

/**
 * Time left builds human readable localized strings for time to left events, such as: in a few seconds, in a minute, in 19 minutes, in an hour ago, ...
 *
 * Used i18n properties:
 * * timeleft.afewseconds=in a few seconds
 * * timeleft.days=in {0} days
 * * timeleft.days.one=tomorrow
 * * timeleft.hours=in {0} hours
 * * timeleft.hours.one=in an hour
 * * timeleft.minutes=in {0} minutes
 * * timeleft.minutes.one=in a minute
 * * timeleft.months=in {0} months
 * * timeleft.months.one=in a month
 * * timeleft.negative=in the past!
 * * timeleft.weeks=in {0} weeks
 * * timeleft.weeks.one=in a week
 * * timeleft.years=in {0} years
 * * timeleft.years.one=in a year
 *
 */
object TimeLeft {
  /**
   * @param date Date in the future to compare with now. For past dates, a message of [TimeAgo] will be returned
   * @param locale Locale to use for translation.
   * @param pastMessage If given for dates in the past, the given past message is returned, otherwise [TimeAgo.getMessage] is called for past times.
   * @return Time ago message or an empty string, if no date was given.
   */
  @JvmStatic
  @JvmOverloads
  fun getMessage(date: Date?, locale: Locale? = null, pastMessage: String? = null): String {
    date ?: return ""
    val result = getI18nKey(date, pastMessage)
    return if (result.first == pastMessage) {
      pastMessage
    } else {
      TimeAgo.translate(result, "timeleft", locale)
    }
  }

  internal fun getI18nKey(date: Date, pastMessage: String? = null): Pair<String, Int> {
    val millis = date.time - System.currentTimeMillis()
    if (millis < 0) {
      return if (pastMessage != null) {
        Pair(pastMessage, -1)
      } else {
        TimeAgo.getI18nKey(date, false)
      }
    }
    return TimeAgo.getUnit(millis)
  }
}