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

package org.projectforge.rest.scripting

import de.micromata.merlin.utils.ReplaceUtils
import mu.KotlinLogging
import org.projectforge.business.scripting.ScriptDO
import org.projectforge.business.scripting.ScriptDao
import org.projectforge.business.scripting.ScriptParameterType
import org.projectforge.framework.time.DateHelper
import org.projectforge.menu.MenuItem
import org.projectforge.menu.MenuItemTargetType
import org.projectforge.rest.AddressServicesRest
import org.projectforge.rest.MessageType
import org.projectforge.rest.ResponseData
import org.projectforge.rest.config.Rest
import org.projectforge.rest.config.RestUtils
import org.projectforge.rest.core.AbstractDTOPagesRest
import org.projectforge.rest.core.PagesResolver
import org.projectforge.rest.dto.Script
import org.projectforge.ui.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.StringWriter
import java.util.*
import javax.annotation.PostConstruct

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("${Rest.URL}/script")
class ScriptPagesRest : AbstractDTOPagesRest<ScriptDO, Script, ScriptDao>(
  baseDaoClazz = ScriptDao::class.java,
  i18nKeyPrefix = "scripting.title"
) {
  @PostConstruct
  private fun postConstruct() {
    /**
     * Enable attachments for this entity.
     */
    enableJcr()
  }

  override fun transformForDB(dto: Script): ScriptDO {
    val scriptDO = ScriptDO()
    dto.copyTo(scriptDO)
    scriptDO.scriptAsString = dto.script
    if (dto.id != null) {
      // Restore filename and file for older scripts, edited by classical Wicket-version:
      val origScript = baseDao.getById(dto.id)
      scriptDO.filename = origScript.filename
      scriptDO.file = origScript.file
    }
    return scriptDO
  }

  override fun transformFromDB(obj: ScriptDO, editMode: Boolean): Script {
    val script = Script()
    script.filename = obj.filename
    script.copyFrom(obj)
    script.availableVariables = baseDao.getScriptVariableNames(obj).joinToString()
    script.script = obj.scriptAsString
    return script
  }

  /**
   * LAYOUT List page
   */
  override fun createListLayout(): UILayout {
    val layout = super.createListLayout()
      .add(
        UITable.createUIResultSetTable()
          .add(lc, "name", "description")
          .add(UITableColumn("parameter", title = "scripting.script.parameter"))
          .add(UITableColumn("type", title = "scripting.script.type"))
          .add(UITableColumn("attachmentsSizeFormatted", titleIcon = UIIconType.PAPER_CLIP))
      )
    return LayoutUtils.processListPage(layout, this)
  }

  /**
   * @return the execution page.
   */
  override fun getStandardEditPage(): String {
    return "${PagesResolver.getDynamicPageUrl(ScriptExecutePageRest::class.java)}:id"
  }


  /**
   * LAYOUT Edit page
   */
  override fun createEditLayout(dto: Script, userAccess: UILayout.UserAccess): UILayout {
    val langs = listOf(
      UISelectValue(ScriptDO.ScriptType.KOTLIN, "Kotlin script"),
      UISelectValue(ScriptDO.ScriptType.GROOVY, "Groovy script"),
    )
    val layout = super.createEditLayout(dto, userAccess)
      .add(
        UIRow()
          .add(
            UICol()
              .add(lc, "name")
          )
          .add(
            UICol()
              .add(
                UISelect(
                  "type", lc,
                  label = "scripting.script.type",
                  values = langs,
                )
              )
          )
      )

    if (!dto.filename.isNullOrBlank()) {
      layout.add(lc, "filename")
    }
    for (i in 1..6) {
      layout.add(createParameterRow(i))
    }
    layout
      .add(lc, "description")
      .add(
        UIFieldset(title = "attachment.list")
          .add(UIAttachmentList(category, dto.id))
      )
      .add(UIEditor("script"))
      .add(UIReadOnlyField("availableVariables", label = "scripting.script.availableVariables"))

    if (dto.id != null) {
      layout.add(
        MenuItem(
          "scripting.scriptBackup",
          i18nKey = "scripting.scriptBackup.show",
          url = "${getRestPath()}/downloadBackupScript/${dto.id}",
          type = MenuItemTargetType.DOWNLOAD
        )
      )
    }
    return LayoutUtils.processEditPage(layout, dto, this)
  }

  @GetMapping("downloadBackupScript/{id}")
  fun downloadBackupScript(@PathVariable("id") id: Int?): ResponseEntity<*> {
    log.info("Downloading backup script of script with id=$id")
    val scriptDO = baseDao.getById(id) ?: throw IllegalArgumentException("Script not found.")
    val filename = ReplaceUtils.encodeFilename("${scriptDO.name}-backup.${baseDao.getScriptSuffix(scriptDO)}")
    return RestUtils.downloadFile(filename, scriptDO.scriptBackupAsString ?: "")
  }

  private fun createParameterRow(number: Int): UIRow {
    return UIRow()
      .add(
        UICol()
          .add(UIInput("parameter$number.name", label = "scripting.script.parameterName"))
      )
      .add(
        UICol()
          .add(
            UISelect<ScriptParameterType>("parameter$number.type", required = false).buildValues(
              ScriptParameterType::class.java
            )
          )
      )
  }
}