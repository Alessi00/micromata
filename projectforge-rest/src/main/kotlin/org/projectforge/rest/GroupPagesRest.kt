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

import org.projectforge.business.user.GroupDao
import org.projectforge.business.user.service.UserService
import org.projectforge.framework.persistence.user.entities.GroupDO
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDTOPagesRest
import org.projectforge.rest.dto.Group
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("${Rest.URL}/group")
class GroupPagesRest: AbstractDTOPagesRest<GroupDO, Group, GroupDao>(GroupDao::class.java, "group.title") {

    @Autowired
    private lateinit var userService: UserService

    override fun transformFromDB(obj: GroupDO, editMode : Boolean): Group {
        val group = Group()
        group.copyFrom(obj)
        group.assignedUsers?.forEach {
            val user = userService.getUser(it.id)
            if (user != null) {
                it.username = user.username
                it.firstname = user.firstname
                it.lastname = user.lastname
            }
        }
        return group
    }

    override fun transformForDB(dto: Group): GroupDO {
        val groupDO = GroupDO()
        dto.copyTo(groupDO)
        return groupDO
    }

    override val classicsLinkListUrl: String?
        get() = "wa/groupList"

    /**
     * LAYOUT List page
     */
    override fun createListLayout(): UILayout {
        val layout = super.createListLayout()
                .add(UITable.UIResultSetTable()
                        .add(lc, "name", "organization", "description", "assignedUsers", "ldapValues"))
        return LayoutUtils.processListPage(layout, this)
    }

    /**
     * LAYOUT Edit page
     */
    override fun createEditLayout(dto: Group, userAccess: UILayout.UserAccess): UILayout {
        val layout = super.createEditLayout(dto, userAccess)
                .add(UIRow()
                        .add(UICol()
                                .add(lc, "name", "organization", "description"))
                        .add(UICol()
                                .add(UISelect.createUserSelect(lc, "assignedUsers", true, "group.assignedUsers"))))
        return LayoutUtils.processEditPage(layout, dto, this)
    }

    override val autoCompleteSearchFields = arrayOf("name", "organization")
}
