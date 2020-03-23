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

package org.projectforge.framework.persistence.user.entities

import org.projectforge.business.user.UserTokenType
import org.projectforge.common.anots.PropertyInfo
import org.projectforge.framework.persistence.entities.DefaultBaseDO
import javax.persistence.*

/**
 * Users may have serveral authentication tokens, e. g. for CardDAV/CalDAV-Clients or other clients. ProjectForge shows the usage of this tokens and such tokens
 * may easily be revokable. In addition, no password may be stored on smartphone client e. g. for using ProjectForge's CardDAV/CalDAV service.
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
@Entity
@Table(name = "T_PF_USER_AUTHENTICATIONS", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])], indexes = [Index(name = "idx_fk_t_pf_user_id", columnList = "user_id")])
@NamedQueries(
        NamedQuery(name = UserAuthenticationsDO.FIND_BY_USER_ID,
                query = "from UserAuthenticationsDO t join fetch t.user where t.user.id = :userId"),

        NamedQuery(name = UserAuthenticationsDO.FIND_USER_BY_USERNAME_AND_STAY_LOGGED_IN_KEY,
                query = "select u from PFUserDO u, UserAuthenticationsDO t where u.username = :username and u.id = t.user.id and t.stayLoggedInKey = :token"),
        NamedQuery(name = UserAuthenticationsDO.FIND_USER_BY_USERNAME_AND_CALENDAR_TOKEN,
                query = "select u from PFUserDO u, UserAuthenticationsDO t where u.username = :username and u.id = t.user.id and t.calendarExportToken = :token"),
        NamedQuery(name = UserAuthenticationsDO.FIND_USER_BY_USERNAME_AND_REST_CLIENT_TOKEN,
                query = "select u from PFUserDO u, UserAuthenticationsDO t where u.username = :username and u.id = t.user.id and t.restClientToken = :token"),
        NamedQuery(name = UserAuthenticationsDO.FIND_USER_BY_USERNAME_AND_DAV_TOKEN,
                query = "select u from PFUserDO u, UserAuthenticationsDO t where u.username = :username and u.id = t.user.id and t.davToken = :token"),

        NamedQuery(name = UserAuthenticationsDO.FIND_USER_BY_USERID_AND_STAY_LOGGED_IN_KEY,
                query = "select u from PFUserDO u, UserAuthenticationsDO t where u.id = :userId and u.id = t.user.id and t.stayLoggedInKey = :token"),
        NamedQuery(name = UserAuthenticationsDO.FIND_USER_BY_USERID_AND_CALENDAR_TOKEN,
                query = "select u from PFUserDO u, UserAuthenticationsDO t where u.id = :userId and u.id = t.user.id and t.calendarExportToken = :token"),
        NamedQuery(name = UserAuthenticationsDO.FIND_USER_BY_USERID_AND_REST_CLIENT_TOKEN,
                query = "select u from PFUserDO u, UserAuthenticationsDO t where u.id = :userId and u.id = t.user.id and t.restClientToken = :token"),
        NamedQuery(name = UserAuthenticationsDO.FIND_USER_BY_USERID_AND_DAV_TOKEN,
                query = "select u from PFUserDO u, UserAuthenticationsDO t where u.id = :userId and u.id = t.user.id and t.davToken = :token"),

        NamedQuery(name = UserAuthenticationsDO.CHECK_AUTH_CAL_EXPORT,
                query = "from UserAuthenticationsDO t join fetch t.user where t.calendarExportToken = :calendarExportToken and t.user.username = :username"),
        NamedQuery(name = UserAuthenticationsDO.CHECK_AUTH_DAV,
                query = "from UserAuthenticationsDO t join fetch t.user where t.davToken = :davToken and t.user.username = :username"),
        NamedQuery(name = UserAuthenticationsDO.CHECK_STAY_LOGGED_IN,
                query = "from UserAuthenticationsDO t join fetch t.user where t.stayLoggedInKey = :stayLoggedInKey and t.user.username = :username"))
open class UserAuthenticationsDO : DefaultBaseDO() {

    @PropertyInfo(i18nKey = "user")
    @get:ManyToOne(fetch = FetchType.LAZY)
    @get:JoinColumn(name = "user_id")
    open var user: PFUserDO? = null

    val userId: Int?
        @Transient
        get() = user?.id

    /**
     * Token used for calendar exports
     */
    @PropertyInfo(i18nKey = "user.authenticationToken.calendar_rest", tooltip = "user.authenticationToken.calendar_rest.tooltip")
    @get:Column(name = "calendar_export_token", length = 100, nullable = true)
    open var calendarExportToken: String? = null

    /**
     * Token used for CalDAV and CardDAV clients.
     */
    @PropertyInfo(i18nKey = "user.authenticationToken.dav_token", tooltip = "user.authenticationToken.dav_token.tooltip")
    @get:Column(name = "dav_token", length = 100, nullable = true)
    open var davToken: String? = null

    /**
     * Token used for CalDAV and CardDAV clients.
     */
    @PropertyInfo(i18nKey = "user.authenticationToken.rest_client", tooltip = "user.authenticationToken.rest_client.tooltip")
    @get:Column(name = "rest_client_token", length = 100, nullable = true)
    open var restClientToken: String? = null

    /**
     * Key stored in the cookies for the functionality of stay logged in.
     */
    @get:Column(name = "stay_logged_in_key", length = 255)
    open var stayLoggedInKey: String? = null

    internal fun getToken(type: UserTokenType): String? {
        return when (type) {
            UserTokenType.CALENDAR_REST -> calendarExportToken
            UserTokenType.DAV_TOKEN -> davToken
            UserTokenType.REST_CLIENT -> restClientToken
            UserTokenType.STAY_LOGGED_IN_KEY -> stayLoggedInKey
        }
    }

    internal fun setToken(type: UserTokenType, token: String?) {
        if (!token.isNullOrEmpty() && token.trim().length < 10) {
            log.warn("Token '$type' to short, will not be set for user ${user?.id}.")
            return
        }
        when (type) {
            UserTokenType.CALENDAR_REST -> calendarExportToken = token
            UserTokenType.DAV_TOKEN -> davToken = token
            UserTokenType.REST_CLIENT -> restClientToken = token
            UserTokenType.STAY_LOGGED_IN_KEY -> stayLoggedInKey = token
        }
    }


    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(UserAuthenticationsDO::class.java)

        internal const val FIND_USER_BY_USERNAME_AND_CALENDAR_TOKEN = "UserAuthenticationTokenDO_FindUserByUsernameAndCalendarToken"
        internal const val FIND_USER_BY_USERNAME_AND_DAV_TOKEN = "UserAuthenticationTokenDO_FindUserByUsernameAndDAVToken"
        internal const val FIND_USER_BY_USERNAME_AND_REST_CLIENT_TOKEN = "UserAuthenticationTokenDO_FindUserByUsernameAndRestClientToken"
        internal const val FIND_USER_BY_USERNAME_AND_STAY_LOGGED_IN_KEY = "UserAuthenticationTokenDO_FindUserByUsernameAndStayLoggedInKey"
        internal const val FIND_USER_BY_USERID_AND_CALENDAR_TOKEN = "UserAuthenticationTokenDO_FindUserByUserIdAndCalendarToken"
        internal const val FIND_USER_BY_USERID_AND_DAV_TOKEN = "UserAuthenticationTokenDO_FindUserByUserIdAndDAVToken"
        internal const val FIND_USER_BY_USERID_AND_REST_CLIENT_TOKEN = "UserAuthenticationTokenDO_FindUserByUserIdAndRestClientToken"
        internal const val FIND_USER_BY_USERID_AND_STAY_LOGGED_IN_KEY = "UserAuthenticationTokenDO_FindUserByUserIdAndStayLoggedInKey"
        internal const val FIND_BY_USER_ID = "UserAuthenticationTokenDO_FindByUserId"
        internal const val CHECK_AUTH_CAL_EXPORT = "UserAuthenticationTokenDO_CheckCalExport"
        internal const val CHECK_AUTH_DAV = "UserAuthenticationTokenDO_CheckAuthDAV"
        internal const val CHECK_STAY_LOGGED_IN = "UserAuthenticationTokenDO_CheckStayLoggedIn"
    }
}
