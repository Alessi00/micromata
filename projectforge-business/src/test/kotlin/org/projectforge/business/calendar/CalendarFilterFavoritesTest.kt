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

package org.projectforge.business.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.projectforge.favorites.Favorites
import org.projectforge.framework.configuration.ConfigXml
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.persistence.user.api.UserContext
import org.projectforge.framework.persistence.user.entities.PFUserDO
import java.util.*

class CalendarFilterFavoritesTest {
    @Test
    fun autoNameTest() {
        val favs = Favorites<CalendarFilter>()
        favs.add(CalendarFilter())
        val prefix = favs.getElementAt(0)!!.name
        assertTrue(prefix.startsWith("???")) // Translations not available
        assertTrue(prefix.endsWith("???")) // Translations not available
        favs.add(CalendarFilter())
        assertEquals("$prefix 1", favs.getElementAt(1)!!.name)
        favs.add(CalendarFilter(name = "My favorite"))
        favs.add(CalendarFilter(name = "My favorite"))
        favs.add(CalendarFilter(name = "My favorite"))
        assertEquals("My favorite 1", favs.getElementAt(3)!!.name)
        assertEquals("My favorite 2", favs.getElementAt(4)!!.name)

        assertEquals(0, favs.getElementAt(0)!!.id)
        assertEquals(1, favs.getElementAt(1)!!.id)
        assertEquals(2, favs.getElementAt(2)!!.id)
        assertEquals(3, favs.getElementAt(3)!!.id)
        assertEquals(4, favs.getElementAt(4)!!.id)
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            ConfigXml(".")
            val user = PFUserDO()
            user.locale = Locale.GERMAN
            ThreadLocalUserContext.setUserContext(UserContext(user, null))
        }
    }
}
