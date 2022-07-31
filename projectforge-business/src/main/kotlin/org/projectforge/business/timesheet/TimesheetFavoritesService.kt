/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2022 Micromata GmbH, Germany (www.micromata.com)
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

@file:Suppress("DEPRECATION")

package org.projectforge.business.timesheet

import mu.KotlinLogging
import org.projectforge.business.user.UserPrefDao
import org.projectforge.business.user.service.UserPrefService
import org.projectforge.favorites.Favorites
import org.projectforge.framework.cache.AbstractCache
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.persistence.user.api.UserPrefArea
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class TimesheetFavoritesService {
  @Autowired
  private lateinit var userPrefDao: UserPrefDao

  @Autowired
  private lateinit var userPrefService: UserPrefService

  private class MigrationCache(val service: TimesheetFavoritesService) : AbstractCache() {
    // Key is user id, value is flag, if the user has entries to migrate.
    private var map = mutableMapOf<Int, Boolean>()

    fun refresh(userId: Int?) {
      userId ?: return
      synchronized(map) {
        map.remove(userId)
      }
    }

    fun hasLegacyFavoritesToMigrate(userId: Int): Boolean {
      synchronized(map) {
        map[userId]?.let { return it }
        // Not in cache:
        service.hasLegacyFavoritesToMigrate(userId).let {
          map[userId] = it
          return it
        }
      }
    }

    override fun refresh() {
      map = mutableMapOf()
    }
  }

  private val migrationCache = MigrationCache(this)

  fun getList(): List<TimesheetFavorite> {
    val favorites = getFavorites()
    return favorites.idTitleList.map { TimesheetFavorite(it.name, it.id) }
  }

  fun selectTimesheet(id: Int): TimesheetFavorite? {
    return getFavorites().get(id)
  }

  fun createFavorite(newFavorite: TimesheetFavorite) {
    getFavorites().add(newFavorite)
  }

  fun deleteFavorite(id: Int) {
    getFavorites().remove(id)
  }

  fun renameFavorite(id: Int, newName: String) {
    getFavorites().rename(id, newName)
  }

  // Ensures filter list (stored one, restored from legacy filter or a empty new one).
  private fun getFavorites(): Favorites<TimesheetFavorite> {
    var favorites: Favorites<TimesheetFavorite>? = null
    try {
      @Suppress("UNCHECKED_CAST", "USELESS_ELVIS")
      favorites = userPrefService.getEntry(
        PREF_AREA,
        Favorites.PREF_NAME_LIST,
        Favorites::class.java
      ) as Favorites<TimesheetFavorite>?
        ?: migrateFromLegacyFavorites()
    } catch (ex: Exception) {
      log.error("Exception while getting user preferred favorites: ${ex.message}. This might be OK for new releases. Ignoring filter.")
    }
    if (favorites == null) {
      // Creating empty filter list (user has no filter list yet):
      favorites = Favorites()
      userPrefService.putEntry(PREF_AREA, Favorites.PREF_NAME_LIST, favorites)
    }
    return favorites
  }

  /**
   * Will not overwrite any existing new favorite (with name same).
   */
  fun migrateFromLegacyFavorites(): Favorites<TimesheetFavorite>? {
    val list = userPrefDao.getUserPrefs(UserPrefArea.TIMESHEET_TEMPLATE)
    if (list.isNullOrEmpty())
      return null
    val favorites = getFavorites()
    var modified = false
    for (userPref in list) {
      if (favorites.get(userPref.name) == null) {
        val timesheet = TimesheetDO()
        userPrefDao.fillFromUserPrefParameters(userPref, timesheet)
        val favorite = TimesheetFavorite(userPref.name)
        favorite.fillFromTimesheet(timesheet)
        favorites.add(favorite)
        modified = true
      }
    }
    if (modified) {
      userPrefService.putEntry(PREF_AREA, Favorites.PREF_NAME_LIST, favorites)
      migrationCache.refresh(ThreadLocalUserContext.getUserId())
    }
    return favorites
  }

  fun hasLegacyFavoritesToMigrate(): Boolean {
    return migrationCache.hasLegacyFavoritesToMigrate(ThreadLocalUserContext.getUserId())
  }

  private fun hasLegacyFavoritesToMigrate(userId: Int): Boolean {
    val list = userPrefDao.getUserPrefs(userId, UserPrefArea.TIMESHEET_TEMPLATE)
    if (list.isNullOrEmpty())
      return false
    val favorites = getFavorites()
    for (userPref in list) {
      if (favorites.get(userPref.name) == null) {
        // Found old favorite, which has no entry in new favorites.
        return true
      }
    }
    return false
  }

  companion object {
    private const val OLD_AREA_ID = "TIMESHEET_TEMPLATE"
    private const val PREF_AREA = "timesheet"
  }
}
