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

package org.projectforge.rest

import org.projectforge.Const
import org.projectforge.business.user.UserDao
import org.projectforge.framework.configuration.Configuration
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.api.BaseSearchFilter
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.framework.time.TimeNotation
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDTOPagesRest
import org.projectforge.rest.dto.User
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.*
import javax.servlet.http.HttpServletRequest


@RestController
@RequestMapping("${Rest.URL}/user")
class UserPagesRest
    : AbstractDTOPagesRest<PFUserDO, User, UserDao>(UserDao::class.java, "user.title") {

    override fun transformFromDB(obj: PFUserDO, editMode: Boolean): User {
        val user = User()
        val copy = PFUserDO.createCopyWithoutSecretFields(obj)
        if (copy != null) {
            user.copyFrom(copy)
        }
        return user
    }

    override fun transformForDB(dto: User): PFUserDO {
        val userDO = PFUserDO()
        dto.copyTo(userDO)
        return userDO
    }

    @Autowired
    private lateinit var userDao: UserDao

    /**
     * LAYOUT List page
     */
    override fun createListLayout(): UILayout {
        val layout = super.createListLayout()
                .add(UITable.createUIResultSetTable()
                        .add(lc, "username", "deactivated", "lastname", "firstname", "personalPhoneIdentifiers",
                                "description", "rights", "ldapValues"))
        return LayoutUtils.processListPage(layout, this)
    }

    /**
     * LAYOUT Edit page
     */
    override fun createEditLayout(dto: User, userAccess: UILayout.UserAccess): UILayout {
        val layout = super.createEditLayout(dto, userAccess)
                .add(UIRow()
                        .add(UICol()
                                .add(lc, "username", "firstname", "lastname", "organization", "email",
                                        /*"authenticationToken",*/
                                        "jiraUsername", "hrPlanning", "deactivated"/*, "password"*/))
                        .add(createUserSettingsCol(UILength(1)))
                        .add(UICol().add(lc, "sshPublicKey")))
                /*.add(UISelect<Int>("readonlyAccessUsers", lc,
                        multi = true,
                        label = "user.assignedGroups",
                        additionalLabel = "access.groups",
                        autoCompletion = AutoCompletion<Int>(url = "group/aco"),
                        labelProperty = "name",
                        valueProperty = "id"))
                .add(UISelect<Int>("readonlyAccessUsers", lc,
                        multi = true,
                        label = "multitenancy.assignedTenants",
                        additionalLabel = "access.groups",
                        autoCompletion = AutoCompletion<Int>(url = "group/aco"),
                        labelProperty = "name",
                        valueProperty = "id"))*/
                .add(lc, "description")

        return LayoutUtils.processEditPage(layout, dto, this)
    }

    override val autoCompleteSearchFields = arrayOf("username", "firstname", "lastname", "email")

    override fun queryAutocompleteObjects(request: HttpServletRequest, filter: BaseSearchFilter): List<PFUserDO> {
        val list = super.queryAutocompleteObjects(request, filter)
        if (filter.searchString.isNullOrBlank() || request.getParameter(AutoCompletion.SHOW_ALL_PARAM) != "true") {
            // Show deactivated users only if search string is given or param SHOW_ALL_PARAM is true:
            return list.filter { !it.deactivated } // Remove deactivated users when returning all.
        }
        return list
    }

    companion object {
        internal fun createUserSettingsCol(uiLength: UILength): UICol {
            val userLC = LayoutContext(PFUserDO::class.java)

            val locales = Const.LOCALIZATIONS.map { UISelectValue(Locale(it), translate("locale.$it")) }.toMutableList()
            locales.add(0, UISelectValue(Locale("DEFAULT"), translate("user.defaultLocale")))

            val today = LocalDate.now()
            val formats = Configuration.getInstance().dateFormats
            val dateFormats = formats.map { createUISelectValue(it, today) }.toMutableList()
            val excelDateFormats = formats.map { createUISelectValue(it, today, true) }.toMutableList()

            val timeNotations = listOf(
                    UISelectValue(TimeNotation.H12, translate("timeNotation.12")),
                    UISelectValue(TimeNotation.H24, translate("timeNotation.24"))
            )

            return UICol(uiLength).add(UIReadOnlyField("lastLogin", userLC))
                    .add(userLC, "timeZone", "personalPhoneIdentifiers")
                    .add(UISelect("locale", userLC, required = true, values = locales))
                    .add(UISelect("dateFormat", userLC, required = false, values = dateFormats))
                    .add(UISelect("excelDateFormat", userLC, required = false, values = excelDateFormats))
                    .add(UISelect("timeNotation", userLC, required = false, values = timeNotations))
        }

        private fun createUISelectValue(pattern: String, today: LocalDate, excelDateFormat: Boolean = false): UISelectValue<String> {
            val str = if (excelDateFormat) {
                pattern.replace('y', 'Y').replace('d', 'D')
            } else {
                pattern
            }
            return UISelectValue(str, "$str: ${java.time.format.DateTimeFormatter.ofPattern(pattern).format(today)}")
        }
    }
}
