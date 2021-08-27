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

import mu.KotlinLogging
import org.projectforge.business.user.UserAuthenticationsService
import org.projectforge.business.user.UserDao
import org.projectforge.business.user.filter.CookieService
import org.projectforge.common.StringHelper
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.model.rest.RestPaths
import org.projectforge.rest.calendar.BarcodeServicesRest
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDynamicPageRest
import org.projectforge.rest.dto.FormLayoutData
import org.projectforge.rest.dto.PostData
import org.projectforge.security.My2FAService
import org.projectforge.security.TimeBased2FA
import org.projectforge.ui.*
import org.projectforge.web.My2FAHttpService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.validation.Valid

private val log = KotlinLogging.logger {}

/**
 * User may setup his/her 2FA and may check this. 2FA is usable via Authenticator apps (Microsoft or Google Authenticator),
 * via texting messages (if sms is configured) or via e-mails as a fall back.
 */
@RestController
@RequestMapping("${Rest.URL}/2FASetup")
class My2FASetupPageRest : AbstractDynamicPageRest() {
  class My2FAData(userDao: UserDao? = null, var mobilePhone: String? = null) {
    var authenticatorKey: String? = null
    var showAuthenticatorKey: Boolean = false

    /**
     * OTP entered by user for doing the 2FA.
     */
    var otp: String? = null

    /**
     * Login password is only needed as additional security factor if OTP is sent via e-mail.
     */
    var password: CharArray? = null

    init {
      userDao?.internalGetById(ThreadLocalUserContext.getUserId())?.let { user ->
        mobilePhone = user.mobilePhone
      }
    }
  }

  @Autowired
  private lateinit var authenticationsService: UserAuthenticationsService

  @Autowired
  private lateinit var cookieService: CookieService

  @Autowired
  private lateinit var my2FAService: My2FAService

  @Autowired
  private lateinit var my2FAHttpService: My2FAHttpService

  @Autowired
  private lateinit var userDao: UserDao

  @GetMapping("dynamic")
  fun getForm(request: HttpServletRequest): FormLayoutData {
    val data = My2FAData(userDao)
    val layout = createLayout(data)
    return FormLayoutData(data, layout, createServerData(request))
  }

  /**
   * Will be called, if the user wants to see the encryption options.
   */
  @PostMapping(RestPaths.WATCH_FIELDS)
  fun watchFields(@Valid @RequestBody postData: PostData<My2FAData>): ResponseEntity<ResponseAction> {
    val data = postData.data
    if (data.showAuthenticatorKey) {
      data.authenticatorKey = authenticationsService.getAuthenticatorToken()
      if (data.authenticatorKey == null) {
        authenticationsService.createNewAuthenticatorToken()
        data.authenticatorKey = authenticationsService.getAuthenticatorToken()
      }
    } else {
      data.authenticatorKey = null
    }
    if (data.authenticatorKey == null) {
      data.showAuthenticatorKey = false // AuthenticatorKey not given, so set checkbox to false.
    }
    return ResponseEntity.ok(
      ResponseAction(targetType = TargetType.UPDATE)
        .addVariable("ui", createLayout(data))
        .addVariable("data", data)
    )
  }

  /**
   * For testing the Authenticator's code.
   */
  @PostMapping("checkOTP")
  fun checkOTP(
    request: HttpServletRequest,
    response: HttpServletResponse,
    @Valid @RequestBody postData: PostData<My2FAData>
  ): ResponseEntity<ResponseAction> {
    val otp = postData.data.otp
    val password = postData.data.password
    if (otp == null) {
      return UIToast.createToastResponseEntity(translate("user.My2FA.setup.check.fail"), color = UIColor.DANGER)
    }
    val otpCheck = my2FAHttpService.checkOTP(request, code = otp, password = password)
    if (otpCheck == My2FAHttpService.OTPCheckResult.SUCCESS) {
      ThreadLocalUserContext.getUserContext().lastSuccessful2FA?.let { lastSuccessful2FA ->
        cookieService.addLast2FACookie(request, response, lastSuccessful2FA)
      }
      return UIToast.createToastResponseEntity(translate("user.My2FA.setup.check.success"), color = UIColor.SUCCESS)
    }
    // otp check wasn't successful:
    if (otpCheck == My2FAHttpService.OTPCheckResult.WRONG_LOGIN_PASSWORD) {
      return showValidationErrors(ValidationError(translate("user.My2FACode.password.wrong"), "password"))
    }
    return UIToast.createToastResponseEntity(translate("user.My2FA.setup.check.fail"), color = UIColor.DANGER)
  }

  /**
   * Enables the 2FA for the logged-in user (if not already enabled). Fails, if authenticator token is already configured.
   */
  @Suppress("UNUSED_PARAMETER")
  @PostMapping("enable")
  fun enable(@Valid @RequestBody postData: PostData<My2FAData>): ResponseEntity<ResponseAction> {
    if (!authenticationsService.getAuthenticatorToken().isNullOrBlank()) {
      log.error { "User tries to enable 2FA, but authenticator token is already given!" }
      throw IllegalArgumentException("2FA already configured.")
    }
    if (!checklastSuccessful2FA()) {
      return showValidationErrors(ValidationError(translate("user.My2FA.required"), "otp"))
    }
    val data = My2FAData(userDao, mobilePhone = postData.data.mobilePhone)
    authenticationsService.createNewAuthenticatorToken()
    data.showAuthenticatorKey = true
    data.authenticatorKey = authenticationsService.getAuthenticatorToken()
    return ResponseEntity.ok(
      ResponseAction(targetType = TargetType.UPDATE)
        .addVariable("ui", createLayout(data))
        .addVariable("data", data)
    )
  }

  /**
   * Disables the 2FA for the logged-in user (if enabled). Fails, if authenticator token isn't configured.
   * Requires a valid 2FA not older than 1 minute.
   */
  @PostMapping("disable")
  fun disable(@Valid @RequestBody postData: PostData<My2FAData>): ResponseEntity<ResponseAction> {
    if (authenticationsService.getAuthenticatorToken().isNullOrBlank()) {
      log.error { "User tries to disable 2FA, but authenticator token isn't given!" }
      throw IllegalArgumentException("2FA not configured.")
    }
    val otp = postData.data.otp
    if (!otp.isNullOrBlank()) {
      my2FAService.validateOTP(otp) // Try to do the fresh 2FA
    }
    if (!checklastSuccessful2FA()) {
      return showValidationErrors(ValidationError(translate("user.My2FA.required"), "otp"))
    }
    val data = My2FAData(userDao, mobilePhone = postData.data.mobilePhone)
    authenticationsService.clearAuthenticatorToken()
    return ResponseEntity.ok(
      ResponseAction(targetType = TargetType.UPDATE)
        .addVariable("ui", createLayout(data))
        .addVariable("data", data)
    )
  }

  /**
   * Save the mobile phone field as is is. Must be empty or in a valid phone number format.
   */
  @PostMapping("saveMobilePhone")
  fun saveMobilePhone(@Valid @RequestBody postData: PostData<My2FAData>): ResponseEntity<ResponseAction> {
    val mobilePhone = postData.data.mobilePhone
    if (!mobilePhone.isNullOrBlank() && !StringHelper.checkPhoneNumberFormat(mobilePhone, false)) {
      return showValidationErrors(ValidationError(translate("user.mobilePhone.invalidFormat"), "mobilePhone"))
    }
    if (!checklastSuccessful2FA()) {
      return showValidationErrors(ValidationError(translate("user.My2FA.required"), "otp"))
    }
    val user = userDao.internalGetById(ThreadLocalUserContext.getUserId())
    user.mobilePhone = mobilePhone
    userDao.internalUpdate(user)
    return UIToast.createToastResponseEntity(translate("operation.updated"), color = UIColor.SUCCESS)
  }

  /**
   * Sends a OTP as code (text to mobile phone).
   */
  @PostMapping("sendCode")
  fun sendCode(
    request: HttpServletRequest,
    @Valid @RequestBody postData: PostData<My2FAData>
  ): ResponseEntity<ResponseAction> {
    val mobilePhone = postData.data.mobilePhone
    val result = my2FAHttpService.createAndSendOTP(request, mobilePhone = mobilePhone)
    val color = if (result.success) {
      UIColor.SUCCESS
    } else {
      UIColor.DANGER
    }
    return UIToast.createToastResponseEntity(result.message, color = color)
  }

  private fun createLayout(data: My2FAData): UILayout {
    val smsConfigured = my2FAHttpService.smsConfigured
    val authenticatorKey = authenticationsService.getAuthenticatorToken()
    val layout = UILayout("user.My2FA.setup.title")
    val userLC = LayoutContext(PFUserDO::class.java)
    val fieldset = UIFieldset(12)
    layout.add(fieldset)
    fieldset.add(
      UIAlert(
        message = "user.My2FA.setup.info",
        markdown = true,
        color = UIColor.LIGHT
      )
    )
    val currentRow = UIRow()
    fieldset.add(currentRow)
    currentRow.add(
      UICol(md = 6)
        .add(
          UIRow()
            .add(
              UICol(lg = 6)
                .add(
                  UIInput(
                    "otp", label = "user.My2FACode.code", tooltip = "user.My2FACode.code.info",
                    autoComplete = UIInput.AutoCompleteType.OFF
                  )
                )
            )
            .add(
              UICol(lg = 6)
                .add(
                  UIInput(
                    "password",
                    label = "password",
                    tooltip = "user.My2FACode.password.info",
                    dataType = UIDataType.PASSWORD,
                    autoComplete = UIInput.AutoCompleteType.OFF
                  )
                )
            )
        )
        .add(
          UIButton(
            "validate",
            title = translate("user.My2FACode.code.validate"),
            color = UIColor.PRIMARY,
            responseAction = ResponseAction("/rs/2FASetup/checkOTP", targetType = TargetType.POST),
            default = true,
          )
        )
        .add(
          UIButton(
            "sendCode",
            title = translate("user.My2FACode.sendCode"),
            tooltip = "user.My2FACode.sendCode.info",
            color = UIColor.SECONDARY,
            responseAction = ResponseAction("/rs/2FASetup/sendCode", targetType = TargetType.POST),
          )
        )
    )
    if (smsConfigured) {
      currentRow.add(UICol(md = 6)
        .add(UIInput("mobilePhone", userLC))
        .add(
          UIButton(
            "save",
            title = translate("save"),
            color = UIColor.LIGHT,
            responseAction = ResponseAction("/rs/2FASetup/saveMobilePhone", targetType = TargetType.POST),
          )
        )
      )
    }
    if (authenticatorKey.isNullOrBlank()) {
      fieldset.add(
        UIButton(
          "enable",
          title = translate("user.My2FA.setup.enable"),
          tooltip = "user.My2FA.setup.enable.info",
          color = UIColor.DANGER,
          responseAction = ResponseAction("/rs/2FASetup/enable", targetType = TargetType.POST),
          confirmMessage = translate("user.My2FA.setup.enable.confirmMessage")
        )
      )
    } else {
      fieldset.add(
        UIButton(
          "disable",
          title = translate("user.My2FA.setup.disable"),
          tooltip = "user.My2FA.setup.disable.info",
          color = UIColor.DANGER,
          responseAction = ResponseAction("/rs/2FASetup/disable", targetType = TargetType.POST),
          confirmMessage = translate("user.My2FA.setup.disable.confirmMessage")
        )
      )
      // Authenticator token is available
      fieldset.add(
        UICheckbox(
          "showAuthenticatorKey",
          label = "user.My2FA.setup.showAuthenticatorKey",
        )
      )
      if (data.showAuthenticatorKey) {
        if (!checklastSuccessful2FA()) {
          fieldset.add(
            UIAlert(
              message = "user.My2FA.required",
              markdown = true,
              color = UIColor.DANGER
            )
          )
          data.showAuthenticatorKey = false // Uncheck the checkbox
        } else {
          val queryURL = TimeBased2FA.standard.getAuthenticatorUrl(
            authenticatorKey,
            ThreadLocalUserContext.getUser().username!!,
            domainService.plainDomain ?: "unknown"
          )
          val barcodeUrl = BarcodeServicesRest.getBarcodeGetUrl(queryURL)
          fieldset.add(
            UIRow()
              .add(
                UICol(md = 6)
                  .add(
                    UIAlert(
                      message = "user.My2FA.setup.authenticator.info",
                      markdown = true,
                      color = UIColor.SUCCESS
                    )
                  )
                  .add(UIReadOnlyField("authenticatorKey", label = "user.My2FA.setup.athenticatorKey"))
              )
              .add(
                UICol(md = 6)
                  .add(UICustomized("image", mutableMapOf("src" to barcodeUrl, "alt" to barcodeUrl)))
              )
          )
        }
      }
    }
    layout.watchFields.add("showAuthenticatorKey")
    LayoutUtils.process(layout)
    return layout
  }

  private fun checklastSuccessful2FA(): Boolean {
    return my2FAService.checklastSuccessful2FA(10, My2FAService.Unit.MINUTES)
  }
}
