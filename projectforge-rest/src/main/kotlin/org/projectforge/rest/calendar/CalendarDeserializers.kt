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

package org.projectforge.rest.calendar

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import org.projectforge.business.calendar.event.model.ICalendarEvent
import org.projectforge.rest.dto.CalEvent

/**
 * Deserializes [ICalendarEvent] as [CalEvent].
 */
class ICalendarEventDeserializer : StdDeserializer<ICalendarEvent>(ICalendarEvent::class.java) {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ICalendarEvent? {
        return ctxt.readValue(p, CalEvent::class.java)
    }
}

