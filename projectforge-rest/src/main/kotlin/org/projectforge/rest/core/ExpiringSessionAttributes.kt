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

package org.projectforge.rest.core

import javax.servlet.http.HttpSession
import kotlin.concurrent.timer

/**
 * For storing large objects in the user's session with expiring times. A timer will check every minute for large objects
 * for expiration and will set them to null size. This is useful, if large objects (such as image uploads) will resist
 * in the user's session and the session terminates after several hours.
 */
object ExpiringSessionAttributes {
    /** Store all session attributes for deleting content after expire time. Key is the index
     * of the ExpirintAttribute. */
    private val attributesMap = mutableMapOf<Long, ExpiringAttribute>()
    private var counter = 0L

    init {
        timer("ExpiringSessionAttributesTime", period = 60000) {
            check()
        }
    }

    fun setAttribute(session: HttpSession, name: String, value: Any, ttlMinutes: Int) {
        val attribute = ExpiringAttribute(System.currentTimeMillis(), value, ttlMinutes)
        session.setAttribute(name, attribute)
        synchronized(attributesMap) {
            attributesMap[attribute.index] = attribute
        }
    }

    fun getAttribute(session: HttpSession, name: String): Any? {
        checkSession(session)
        val value = session.getAttribute(name)
        if (value == null)
            return null
        if (value is ExpiringAttribute)
            return value.value
        return value
    }

    fun removeAttribute(session: HttpSession, name: String) {
        val value = getAttribute(session, name)
        if (value == null)
            return
        if (value is ExpiringAttribute) {
            synchronized(attributesMap) {
                attributesMap.remove(value.index)
            }
        }
        session.removeAttribute(name)
    }

    private fun checkSession(session: HttpSession) {
        val current = System.currentTimeMillis()
        session.attributeNames.iterator().forEach {
            val value = session.getAttribute(it)
            if (value is ExpiringAttribute) {
                if (current - value.timestamp > value.ttlMillis) {
                    synchronized(attributesMap) {
                        attributesMap.remove(value.index)
                    }
                    session.removeAttribute(it)
                }
            }
        }
    }

    private fun check() {
        val current = System.currentTimeMillis()
        synchronized(attributesMap) {
            val attributesToRemove = mutableListOf<Long>()
            attributesMap.forEach {
                val attribute = it.value
                if (current - attribute.timestamp > attribute.ttlMillis) {
                    attribute.value = null // Save memory
                    attributesToRemove.add(it.key) // Don't remove here due to ConcurrentModificationException
                }
            }
            attributesToRemove.forEach {
                attributesMap.remove(it)
            }
        }
    }

    private class ExpiringAttribute(val timestamp: Long, var value: Any?, ttlMinutes: Int) {
        val ttlMillis = ttlMinutes * 60000
        val index = ++counter
    }
}
