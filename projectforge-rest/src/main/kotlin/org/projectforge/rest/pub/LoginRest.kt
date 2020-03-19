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

import org.projectforge.Const
import org.projectforge.business.login.LoginDefaultHandler
import org.projectforge.business.login.LoginProtection
import org.projectforge.business.login.LoginResult
import org.projectforge.business.login.LoginResultStatus
import org.projectforge.business.multitenancy.TenantRegistry
import org.projectforge.business.multitenancy.TenantRegistryMap
import org.projectforge.business.user.UserAuthenticationsService
import org.projectforge.business.user.UserGroupCache
import org.projectforge.business.user.UserTokenType
import org.projectforge.business.user.filter.CookieService
import org.projectforge.business.user.filter.UserFilter
import org.projectforge.business.user.service.UserService
import org.projectforge.framework.configuration.ConfigurationParam
import org.projectforge.framework.configuration.GlobalConfiguration
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.user.api.UserContext
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.RestResolver
import org.projectforge.rest.dto.FormLayoutData
import org.projectforge.rest.dto.PostData
import org.projectforge.ui.*
import org.projectforge.web.rest.AbstractRestUserFilter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.InetAddress
import java.net.UnknownHostException
import javax.servlet.ServletRequest
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * This rest service should be available without login (public).
 */
@RestController
@RequestMapping("${Rest.PUBLIC_URL}/login")
open class LoginRest {
    class LoginData(var username: String? = null, var password: String? = null, var stayLoggedIn: Boolean? = null)

    private val log = org.slf4j.LoggerFactory.getLogger(LoginRest::class.java)

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userAuthenticationsService: UserAuthenticationsService

    @Autowired
    private lateinit var cookieService: CookieService

    @GetMapping("dynamic")
    fun getForm(): FormLayoutData {
        val motd = GlobalConfiguration.getInstance().getStringValue(ConfigurationParam.MESSAGE_OF_THE_DAY)
        val responseAction = ResponseAction(RestResolver.getRestUrl(this::class.java), targetType = TargetType.POST)
        val layout = UILayout("login.title")
                .add(UIAlert(motd, color = UIColor.INFO))
                .add(UIInput("username", required = true, label = "username", focus = true))
                .add(UIInput("password", required = true, label = "password", dataType = UIDataType.PASSWORD))
                .add(UICheckbox("stayLoggedIn", label = "login.stayLoggedIn", tooltip = "login.stayLoggedIn.tooltip"))
                .add(UIButton("login", translate("login"), UIColor.SUCCESS, responseAction = responseAction, default = true))
        LayoutUtils.process(layout)
        return FormLayoutData(null, layout, null)
    }

    @PostMapping
    fun login(request: HttpServletRequest,
              response: HttpServletResponse,
              @RequestBody postData: PostData<LoginData>)
            : ResponseEntity<String> {
        val loginResultStatus = _login(request, response, postData.data)
        if (loginResultStatus == LoginResultStatus.SUCCESS)
            return ResponseEntity.ok("Success")
        return ResponseEntity(HttpStatus.UNAUTHORIZED)
    }

    private fun _login(request: HttpServletRequest, response: HttpServletResponse, loginData: LoginData): LoginResultStatus {
        val loginResult = checkLogin(request, loginData)
        val user = loginResult.user
        if (user == null || loginResult.loginResultStatus != LoginResultStatus.SUCCESS) {
            return loginResult.loginResultStatus
        }
        if (UserFilter.isUpdateRequiredFirst()) {
            log.warn("******* Update of ProjectForge required first. Please login via old login page. LoginService should be used instead.")
            return LoginResultStatus.FAILED
        }
        log.info("User successfully logged in: " + user.userDisplayName)
        if (loginData.stayLoggedIn == true) {
            val loggedInUser = userService.internalGetById(user.id)
            val stayLoggedInKey = userAuthenticationsService.internalGetToken(user.id, UserTokenType.STAY_LOGGED_IN_KEY)
            val cookie = Cookie(Const.COOKIE_NAME_FOR_STAY_LOGGED_IN, "${loggedInUser.getId()}:${loggedInUser.username}:$stayLoggedInKey")
            cookieService.addStayLoggedInCookie(request, response, cookie)
        }
        // Execute login:
        val userContext = UserContext(PFUserDO.createCopyWithoutSecretFields(user)!!, getUserGroupCache())
        AbstractRestUserFilter.executeLogin(request, userContext)
        return LoginResultStatus.SUCCESS
    }

    private fun checkLogin(request: HttpServletRequest, loginData: LoginData): LoginResult {
        if (loginData.username == null || loginData.password == null) {
            return LoginResult().setLoginResultStatus(LoginResultStatus.FAILED)
        }
        val loginProtection = LoginProtection.instance()
        val clientIpAddress = getClientIp(request)
        val offset = loginProtection.getFailedLoginTimeOffsetIfExists(loginData.username, clientIpAddress)
        if (offset > 0) {
            val seconds = (offset / 1000).toString()
            log.warn("The account for '${loginData.username}' is locked for $seconds seconds due to failed login attempts. Please try again later.")

            val numberOfFailedAttempts = loginProtection.getNumberOfFailedLoginAttempts(loginData.username, clientIpAddress)
            return LoginResult().setLoginResultStatus(LoginResultStatus.LOGIN_TIME_OFFSET).setMsgParams(seconds,
                    numberOfFailedAttempts.toString())
        }
        val loginHandler = applicationContext.getBean(LoginDefaultHandler::class.java)

        val result = loginHandler.checkLogin(loginData.username, loginData.password)
        if (result.getLoginResultStatus() == LoginResultStatus.SUCCESS) {
            loginProtection.clearLoginTimeOffset(result.user?.username, result.user?.id, clientIpAddress)
        } else if (result.getLoginResultStatus() == LoginResultStatus.FAILED) {
            loginProtection.incrementFailedLoginTimeOffset(loginData.username, clientIpAddress)
        }
        return result
    }

    private fun getClientIp(request: ServletRequest): String? {
        var remoteAddr: String? = null
        if (request is HttpServletRequest) {
            remoteAddr = request.getHeader("X-Forwarded-For")
        }
        if (remoteAddr != null) {
            if (remoteAddr.contains(",")) {
                // sometimes the header is of form client ip,proxy 1 ip,proxy 2 ip,...,proxy n ip,
                // we just want the client
                remoteAddr = remoteAddr.split(',')[0].trim({ it <= ' ' })
            }
            try {
                // If ip4/6 address string handed over, simply does pattern validation.
                InetAddress.getByName(remoteAddr)
            } catch (e: UnknownHostException) {
                remoteAddr = request.remoteAddr
            }

        } else {
            remoteAddr = request.remoteAddr
        }
        return remoteAddr
    }

    private fun getTenantRegistry(): TenantRegistry {
        return TenantRegistryMap.getInstance().tenantRegistry
    }

    private fun getUserGroupCache(): UserGroupCache {
        return getTenantRegistry().userGroupCache
    }
}
