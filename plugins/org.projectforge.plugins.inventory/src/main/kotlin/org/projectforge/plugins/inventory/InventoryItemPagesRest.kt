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

package org.projectforge.plugins.inventory

import org.projectforge.menu.MenuItem
import org.projectforge.menu.MenuItemTargetType
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDTOPagesRest
import org.projectforge.rest.dto.User
import org.projectforge.ui.*
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("${Rest.URL}/inventory")
class InventoryItemPagesRest() : AbstractDTOPagesRest<InventoryItemDO, InventoryItem, InventoryItemDao>(
  InventoryItemDao::class.java,
  "plugins.inventory.title",
  cloneSupport = CloneSupport.CLONE
) {

  override fun transformFromDB(obj: InventoryItemDO, editMode: Boolean): InventoryItem {
    val dto = InventoryItem()
    dto.copyFrom(obj)
    // Usernames needed by React client (for ReactSelect):
    User.restoreDisplayNames(dto.owners)
    dto.ownersAsString = dto.owners?.joinToString { it.displayName ?: "???" } ?: ""
    return dto
  }

  override fun transformForDB(dto: InventoryItem): InventoryItemDO {
    val itemDO = InventoryItemDO()
    dto.copyTo(itemDO)
    return itemDO
  }

  /**
   * Initializes new items for adding.
   */
  override fun newBaseDO(request: HttpServletRequest?): InventoryItemDO {
    val item = super.newBaseDO(request)
    return item
  }

  /**
   * LAYOUT List page
   */
  override fun createListLayout(): UILayout {
    val layout = super.createListLayout()
      .add(
        UITable.createUIResultSetTable()
          .add(lc, "lastUpdate", "item")
          .add(UITableColumn("ownersAsString", "plugins.inventory.owners"))
          .add(lc, "externalOwners", "comment")
      )

    layout.add(
      MenuItem(
        "inventory.export",
        i18nKey = "exportAsXls",
        url = InventoryServicesRest.REST_EXCEL_EXPORT_PATH,
        type = MenuItemTargetType.DOWNLOAD
      )
    )

    return LayoutUtils.processListPage(layout, this)
  }

  /**
   * LAYOUT Edit page
   */
  override fun createEditLayout(dto: InventoryItem, userAccess: UILayout.UserAccess): UILayout {
    val layout = super.createEditLayout(dto, userAccess)
      .add(UIInput("item", lc).enableAutoCompletion(this))
      .add(UISelect.createUserSelect(lc, "owners", multi = true))
      .add(UIInput("externalOwners", lc).enableAutoCompletion(this))
      .add(lc, "comment")
    return LayoutUtils.processEditPage(layout, dto, this)
  }
}
