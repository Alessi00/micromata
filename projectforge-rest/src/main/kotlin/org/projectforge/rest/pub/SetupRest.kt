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

package org.projectforge.rest.pub

import org.projectforge.framework.configuration.ConfigurationParam
import org.projectforge.framework.configuration.GlobalConfiguration
import org.projectforge.framework.persistence.database.DatabaseService
import org.projectforge.rest.config.Rest
import org.projectforge.rest.dto.FormLayoutData
import org.projectforge.ui.UILabel
import org.projectforge.ui.UILayout
import org.projectforge.ui.UINamedContainer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * This rest service should be available without login (public).
 */
@RestController
@RequestMapping("${Rest.PUBLIC_URL}/setup")
open class SetupRest {
    data class LoginData(var username: String? = null, var password: String? = null, var stayLoggedIn: Boolean? = null)

    private val log = org.slf4j.LoggerFactory.getLogger(SetupRest::class.java)

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var databaseService: DatabaseService

    @GetMapping("dynamic")
    fun getForm(): FormLayoutData {
        val layout = UILayout("administration.setup.title")
        if (databaseService.databaseTablesWithEntriesExists()) {
            log.error("Data-base isn't empty: SetupPage shouldn't be used...")
            return FormLayoutData(null, layout, null)
            // throw RestartResponseException(SetupPage::class.java!!)
        }
        layout
                .addTranslations("username", "password", "login.stayLoggedIn", "login.stayLoggedIn.tooltip")
        //.addTranslation("messageOfTheDay")
        layout.add(UINamedContainer("messageOfTheDay").add(UILabel(label = GlobalConfiguration.getInstance().getStringValue(ConfigurationParam.MESSAGE_OF_THE_DAY))))
        return FormLayoutData(null, layout, null)
    }
}
