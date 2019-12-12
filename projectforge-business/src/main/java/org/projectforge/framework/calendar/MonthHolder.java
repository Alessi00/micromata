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

package org.projectforge.framework.calendar;

import org.projectforge.framework.time.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 
 * @author Kai Reinhard (k.reinhard@micromata.de)
 * 
 */
public class MonthHolder
{
  /** Keys of the month names (e. g. needed for I18n). */
  public static final String MONTH_KEYS[] = new String[] { "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"};

  private List<WeekHolder> weeks;

  private int year = -1;

  private int month = -1;

  private Calendar cal;

  private PFDateTime date;

  private Date begin;

  private Date end;

  public MonthHolder()
  {

  }

  /**
   * 
   * @param month Can also be one month before or after if the day of the weeks of this month have an overlap to the nearby months.
   * @param dayOfMonth
   * @return null, if the demanded day is not member of the weeks of the MonthHolder.
   */
  public PFDateTime getDay(final int month, final int dayOfMonth)
  {
    for (final WeekHolder week : weeks) {
      for (final PFDateTime day : week.getDayDates()) {
        if (day.getMonthValue() == month && day.getDayOfMonth() == dayOfMonth) {
          return day;
        }
      }
    }
    return null;
  }

  /** Initializes month containing all days of actual month. */
  public MonthHolder(final TimeZone timeZone, final Locale locale)
  {
    cal = Calendar.getInstance(timeZone, locale);
    calculate();
  }

  public MonthHolder(final Date date, final TimeZone timeZone)
  {
    this.date = PFDateTime.from(date, false, timeZone);
    calculate();
  }

  public MonthHolder(final Date date)
  {
    this.date = PFDateTime.from(date);
    calculate();
  }

  public MonthHolder(final int year, final int month)
  {
    cal = DateHelper.getCalendar();
    cal.clear();
    cal.set(year, month, 1);
    calculate();
  }

  private void calculate()
  {
    PFDateTime dateTime = PFDateTime.from(date.getUtilDate());
    year = dateTime.getYear();
    month = dateTime.getMonthValue();
    begin = dateTime.getBeginOfMonth().getUtilDate(); // Storing begin of month.
    end = dateTime.getEndOfMonth().getUtilDate(); // Storing end of month.
    dateTime = dateTime.getBeginOfMonth().getBeginOfWeek(); // get first week (with days of previous month)

    weeks = new ArrayList<>();
    do {
      final WeekHolder week = new WeekHolder(dateTime, month);
      weeks.add(week);
      dateTime = dateTime.plusWeeks(1);
    } while (dateTime.getMonthValue() == month);
  }

  public int getYear()
  {
    return year;
  }

  public int getMonth()
  {
    return month;
  }

  public List<PFDateTime> getDays()
  {
    final List<PFDateTime> list = new LinkedList<>();
    PFDateTime dtBegin = PFDateTime.from(begin);
    PFDateTime dtEnd = PFDateTime.from(end);
    PFDateTime day = dtBegin;
    int paranoiaCounter = 40;
    while (!day.isAfter(dtEnd) && --paranoiaCounter > 0) {
      list.add(day);
      day = dtBegin.plusDays(1);
    }
    return list;
  }

  public String getMonthKey()
  {
    if (month < 0 || month >= MONTH_KEYS.length) {
      return "unknown";
    }
    return MONTH_KEYS[month];
  }

  /**
   * @return i18n key of the month name.
   */
  public String getI18nKey()
  {
    return "dateTime.month." + getMonthKey();
  }

  public WeekHolder getFirstWeek()
  {
    return getWeeks().get(0);
  }

  public WeekHolder getLastWeek()
  {
    return weeks.get(weeks.size() - 1);
  }

  public List<WeekHolder> getWeeks()
  {
    return weeks;
  }

  public Date getBegin()
  {
    return begin;
  }

  public Date getEnd()
  {
    return end;
  }

  /**
   * Is the given day member of the current month?
   * @param day
   * @return
   */
  public boolean containsDay(final DayHolder day)
  {
    return (!day.getDate().before(begin) && !day.getDate().after(end));
  }

  public BigDecimal getNumberOfWorkingDays()
  {
    final PFDateTime from = PFDateTime.from(this.begin);
    final PFDateTime to = PFDateTime.from(this.end);
    return PFDateTime.getNumberOfWorkingDays(from, to);
  }
}
