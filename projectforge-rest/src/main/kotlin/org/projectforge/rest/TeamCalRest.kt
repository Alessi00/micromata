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

import org.projectforge.business.group.service.GroupService
import org.projectforge.business.teamcal.admin.TeamCalDao
import org.projectforge.business.teamcal.admin.model.TeamCalDO
import org.projectforge.business.teamcal.admin.right.TeamCalRight
import org.projectforge.business.timesheet.TimesheetFilter
import org.projectforge.business.user.service.UserService
import org.projectforge.framework.access.AccessChecker
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDTORest
import org.projectforge.rest.dto.Group
import org.projectforge.rest.dto.TeamCal
import org.projectforge.rest.dto.User
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("${Rest.URL}/teamCal")
class TeamCalRest : AbstractDTORest<TeamCalDO, TeamCal, TeamCalDao>(TeamCalDao::class.java, "plugins.teamcal.title") {

    @Autowired
    private lateinit var groupService: GroupService

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var accessChecker: AccessChecker

    override fun transformFromDB(obj: TeamCalDO, editMode: Boolean): TeamCal {
        val teamCal = TeamCal()
        teamCal.copyFrom(obj)
        var anonymize = true
        if (editMode) {
            if (obj.id != null) {
                val right = TeamCalRight(accessChecker)
                if (right.hasUpdateAccess(ThreadLocalUserContext.getUser(), obj, obj)) {
                    // User has update access right, so don't remove externalSubscriptionUrl due to privacy reasons:
                    anonymize = false
                }
            }
        }
        if (anonymize) {
            // In list view and for users hasn't access to update the current object, the url will be anonymized due to privacy.
            teamCal.externalSubscriptionUrlAnonymized = obj.externalSubscriptionUrlAnonymized
            teamCal.externalSubscriptionUrl = null // Due to privacy reasons! Must be changed for editing mode.
        }

        // Group names needed by React client (for ReactSelect):
        Group.restoreDisplayNames(teamCal.fullAccessGroups, groupService)
        Group.restoreDisplayNames(teamCal.readonlyAccessGroups, groupService)
        Group.restoreDisplayNames(teamCal.minimalAccessGroups, groupService)
        Group.restoreDisplayNames(teamCal.includeLeaveDaysForGroups, groupService)

        // Usernames needed by React client (for ReactSelect):
        User.restoreDisplayNames(teamCal.fullAccessUsers, userService)
        User.restoreDisplayNames(teamCal.readonlyAccessUsers, userService)
        User.restoreDisplayNames(teamCal.minimalAccessUsers, userService)
        User.restoreDisplayNames(teamCal.includeLeaveDaysForUsers, userService)

        return teamCal
    }

    override fun transformForDB(dto: TeamCal): TeamCalDO {
        val teamCalDO = TeamCalDO()
        dto.copyTo(teamCalDO)
        return teamCalDO
    }

    override fun validate(validationErrors: MutableList<ValidationError>, dto: TeamCal) {
    }

    /**
     * LAYOUT List page
     */
    override fun createListLayout(): UILayout {
        val layout = super.createListLayout()
                .add(UITable.UIResultSetTable()
                        .add(lc, "title", "externalSubscriptionUrlAnonymized", "description", "owner",
                                "accessright", "last_update", "externalSubscription"))
        layout.getTableColumnById("owner").formatter = Formatter.USER
        layout.getTableColumnById("last_update").formatter = Formatter.TIMESTAMP_MINUTES
        LayoutUtils.addListFilterContainer(layout, "longFormat", "recursive",
                filterClass = TimesheetFilter::class.java)
        return LayoutUtils.processListPage(layout, this)
    }

    /**
     * LAYOUT Edit page
     */
    override fun createEditLayout(dto: TeamCal, userAccess: UILayout.UserAccess): UILayout {
        val layout = super.createEditLayout(dto, userAccess)
                .add(UIRow()
                        .add(UICol()
                                .add(lc, "title")
                                .add(lc, "description"))
                        .add(UICol()
                                .add(lc, "owner")))
                .add(UIRow()
                        .add(UICol()
                                .add(UISelect.creatUserSelect(lc, "fullAccessUsers", true))
                                .add(UISelect.creatUserSelect(lc, "readonlyAccessUsers", true))
                                .add(UISelect.creatUserSelect(lc, "minimalAccessUsers", true)))
                        .add(UICol()
                                .add(UISelect.createGroupSelect(lc, "fullAccessGroups", true))
                                .add(UISelect.createGroupSelect(lc, "readonlyAccessGroups", true))
                                .add(UISelect.createGroupSelect(lc, "minimalAccessUsers", true))))
                .add(UIRow()
                        .add(UICol()
                                .add(UISelect.creatUserSelect(lc, "includeLeaveDaysForUsers", true)))
                        .add(UICol()
                                .add(UISelect.createGroupSelect(lc, "includeLeaveDaysForGroups", true))))
        return LayoutUtils.processEditPage(layout, dto, this)
    }
}
