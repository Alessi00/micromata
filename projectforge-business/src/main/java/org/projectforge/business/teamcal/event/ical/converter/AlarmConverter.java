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

package org.projectforge.business.teamcal.event.ical.converter;

import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.Trigger;
import org.projectforge.business.teamcal.event.ical.VEventComponentConverter;
import org.projectforge.business.teamcal.event.model.ReminderActionType;
import org.projectforge.business.teamcal.event.model.ReminderDurationUnit;
import org.projectforge.business.teamcal.event.model.TeamEventDO;

import java.util.List;

public class AlarmConverter implements VEventComponentConverter
{
  private static final int DURATION_OF_WEEK = 7;

  @Override
  public boolean toVEvent(final TeamEventDO event, final VEvent vEvent)
  {
    if (event.getReminderDuration() == null || event.getReminderActionType() == null) {
      return false;
    }

    final VAlarm alarm = new VAlarm();
    Dur dur = null;
    // (-1) * needed to set alert before
    if (ReminderDurationUnit.MINUTES.equals(event.getReminderDurationUnit())) {
      dur = new Dur(0, 0, (-1) * event.getReminderDuration(), 0);
    } else if (ReminderDurationUnit.HOURS.equals(event.getReminderDurationUnit())) {
      dur = new Dur(0, (-1) * event.getReminderDuration(), 0, 0);
    } else if (ReminderDurationUnit.DAYS.equals(event.getReminderDurationUnit())) {
      dur = new Dur((-1) * event.getReminderDuration(), 0, 0, 0);
    }

    if (dur == null) {
      return false;
    }

    alarm.getProperties().add(new Trigger(dur));
    alarm.getProperties().add(new Action(event.getReminderActionType().getType()));
    vEvent.getAlarms().add(alarm);

    return true;
  }

  @Override
  public boolean fromVEvent(final TeamEventDO event, final VEvent vEvent)
  {

    final List<VAlarm> alarms = vEvent.getAlarms();
    if (alarms == null || alarms.isEmpty()) {
      return false;
    }

    final VAlarm alarm = alarms.get(0);
    final Dur dur = alarm.getTrigger().getDuration();
    if (alarm.getAction() == null || dur == null) {
      return false;
    }

    if (Action.AUDIO.equals(alarm.getAction())) {
      event.setReminderActionType(ReminderActionType.MESSAGE_SOUND);
    } else {
      event.setReminderActionType(ReminderActionType.MESSAGE);
    }

    // consider weeks
    int weeksToDays = 0;
    if (dur.getWeeks() != 0) {
      weeksToDays = dur.getWeeks() * DURATION_OF_WEEK;
    }

    if (dur.getDays() != 0) {
      event.setReminderDuration(dur.getDays() + weeksToDays);
      event.setReminderDurationUnit(ReminderDurationUnit.DAYS);
    } else if (dur.getHours() != 0) {
      event.setReminderDuration(dur.getHours());
      event.setReminderDurationUnit(ReminderDurationUnit.HOURS);
    } else if (dur.getMinutes() != 0) {
      event.setReminderDuration(dur.getMinutes());
      event.setReminderDurationUnit(ReminderDurationUnit.MINUTES);
    } else {
      event.setReminderDuration(15);
      event.setReminderDurationUnit(ReminderDurationUnit.MINUTES);
    }

    return true;
  }
}
