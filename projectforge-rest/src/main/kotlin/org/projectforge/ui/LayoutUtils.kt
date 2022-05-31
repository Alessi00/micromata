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

package org.projectforge.ui

import mu.KotlinLogging
import org.projectforge.favorites.Favorites
import org.projectforge.framework.i18n.addTranslations
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.api.BaseDao
import org.projectforge.framework.persistence.api.ExtendedBaseDO
import org.projectforge.framework.persistence.api.HibernateUtils
import org.projectforge.model.rest.RestPaths
import org.projectforge.rest.core.AbstractPagesRest
import org.projectforge.rest.dto.FormLayoutData
import org.projectforge.rest.dto.ServerData

private val log = KotlinLogging.logger {}

/**
 * Utils for the Layout classes for handling auto max-length (get from JPA entities) and translations as well as
 * generic default layouts for list and edit pages.
 */
object LayoutUtils {

  @JvmStatic
  fun addCommonTranslations(translations: MutableMap<String, String>) {
    addTranslations("select.placeholder", "calendar.today", "task.title.list.select", translations = translations)
  }

  /**
   * Auto-detects max-length of input fields (by referring the @Column annotations of clazz) and
   * i18n-keys (by referring the [org.projectforge.common.anots.PropertyInfo] annotations of clazz).
   * Calls [processAllElements].
   * @return List of all elements used in the layout.
   */
  @JvmStatic
  fun process(layout: UILayout): List<Any?> {
    val elements = processAllElements(layout, layout.getAllElements())
    var counter = 0
    layout.namedContainers.forEach {
      it.key = "nc-${++counter}"
    }
    return elements
  }

  /**
   * Auto-detects max-length of input fields (by referring the @Column annotations of clazz) and
   * i18n-keys (by referring the [org.projectforge.common.anots.PropertyInfo] annotations of clazz).
   */
  @JvmStatic
  fun processListPage(
    layout: UILayout,
    pagesRest: AbstractPagesRest<out ExtendedBaseDO<Int>, *, out BaseDao<*>>
  ): UILayout {
    layout.layout.find { it is UIAgGrid }?.let { agGrid ->
      pagesRest.agGridSupport.restoreColumnsFromUserPref(pagesRest.category, agGrid as UIAgGrid)
    }
    layout
      .addAction(
        UIButton.createResetButton(
          ResponseAction(
            pagesRest.getRestPath(RestPaths.FILTER_RESET),
            targetType = TargetType.GET
          )
        )
      )
      .addAction(
        UIButton.createSearchButton(
          ResponseAction(pagesRest.getRestPath(RestPaths.LIST), targetType = TargetType.POST, true)
        )
      )
    process(layout)
    layout.addTranslations("search", "cancel")
    addCommonTranslations(layout)
    Favorites.addTranslations(layout.translations)
    return layout
  }

  /**
   * Auto-detects max-length of input fields (by referring the @Column annotations of clazz) and
   * i18n-keys (by referring the @PropertColumn annotations of clazz).<br>
   * Adds the action buttons (cancel, undelete, markAsDeleted, update and/or add dependent on the given data.<br>
   * Calls also fun [process].
   * @see LayoutUtils.process
   */
  @JvmStatic
  fun <O : ExtendedBaseDO<Int>> processEditPage(
    layout: UILayout,
    dto: Any,
    pagesRest: AbstractPagesRest<O, *, out BaseDao<O>>
  )
      : UILayout {
    val userAccess = layout.userAccess
    if (userAccess.cancel != false) {
      layout.addAction(
        UIButton.createCancelButton(
          ResponseAction(
            pagesRest.getRestPath(RestPaths.CANCEL),
            targetType = TargetType.POST
          )
        )
      )
    }
    if (pagesRest.isHistorizable()) {
      // 99% of the objects are historizable (undeletable):
      if (pagesRest.getId(dto) != null) {
        if (userAccess.history == true) {
          layout.showHistory = true
        }
        if (pagesRest.isDeleted(dto)) {
          if (userAccess.insert == true) {
            layout.addAction(
              UIButton.createUndeleteButton(
                ResponseAction(
                  pagesRest.getRestPath(RestPaths.UNDELETE),
                  targetType = TargetType.PUT
                )
              )
            )
          }
          addForceDeleteButton(pagesRest, layout, userAccess)
        } else if (userAccess.delete == true) {
          addForceDeleteButton(pagesRest, layout, userAccess)
          layout.addAction(
            UIButton.createMarkAsDeletedButton(
              layout,
              ResponseAction(
                pagesRest.getRestPath(RestPaths.MARK_AS_DELETED),
                targetType = TargetType.DELETE
              ),
            )
          )
        }
      }
    } else if (userAccess.delete == true) {
      // MemoDO for example isn't historizable:
      layout.addAction(
        UIButton.createDeleteButton(
          layout,
          ResponseAction(pagesRest.getRestPath(RestPaths.DELETE), targetType = TargetType.DELETE),
        )
      )

      layout.addTranslations("yes", "cancel")
    }
    if (pagesRest.getId(dto) != null) {
      if (pagesRest.cloneSupport != AbstractPagesRest.CloneSupport.NONE) {
        layout.addAction(
          UIButton.createCloneButton(
            ResponseAction(pagesRest.getRestPath(RestPaths.CLONE), targetType = TargetType.POST)
          )
        )
      }
      if (!pagesRest.isDeleted(dto)) {
        if (userAccess.update == true) {
          layout.addAction(
            UIButton.createUpdateButton(
              responseAction = ResponseAction(
                pagesRest.getRestPath(RestPaths.SAVE_OR_UDATE),
                targetType = TargetType.PUT
              )
            )
          )
        }
      }
    } else if (userAccess.insert == true) {
      layout.addAction(
        UIButton.createCreateButton(
          ResponseAction(pagesRest.getRestPath(RestPaths.SAVE_OR_UDATE), targetType = TargetType.PUT)
        )
      )
    }
    process(layout)
    layout.addTranslations("label.historyOfChanges")
    addCommonTranslations(layout)
    return layout
  }

  /**
   * Will only be added, if user has delete access as well as the baseDao.isForceDeletionSupport == true.
   */
  private fun addForceDeleteButton(
    pagesRest: AbstractPagesRest<out ExtendedBaseDO<Int>, *, out BaseDao<*>>,
    layout: UILayout,
    userAccess: UILayout.UserAccess,
  ) {
    if (pagesRest.baseDao.isForceDeletionSupport && userAccess.delete == true) {
      layout.addAction(
        UIButton.createForceDeleteButton(
          layout,
          responseAction = ResponseAction(
            pagesRest.getRestPath(RestPaths.FORCE_DELETE),
            targetType = TargetType.DELETE
          ),
        )
      )
    }
  }

  private fun addCommonTranslations(layout: UILayout) {
    addCommonTranslations(layout.translations)
  }

  /**
   * @param layoutSettings One element is returned including the label (e. g. UIInput).
   * @param minLengthOfTextArea For text fields longer than minLengthOfTextArea, a UITextArea is used instead of UIInput.
   *                            Default length is [DEFAULT_MIN_LENGTH_OF_TEXT_AREA], meaning fields with max length of more
   *                            than [DEFAULT_MIN_LENGTH_OF_TEXT_AREA] will be displayed as TextArea.
   */
  internal fun buildLabelInputElement(
    layoutSettings: LayoutContext,
    id: String,
    minLengthOfTextArea: Int = DEFAULT_MIN_LENGTH_OF_TEXT_AREA,
  ): UIElement {
    return ElementsRegistry.buildElement(layoutSettings, id, minLengthOfTextArea)
  }

  /**
   * @param createRowCol If true, a new [UIRow] containing a new [UICol] with the given element is returned,
   * otherwise the element itself without any other operation.
   * @return The element itself or the surrounding [UIRow].
   */
  internal fun prepareElementToAdd(element: UIElement, createRowCol: Boolean): UIElement {
    return if (createRowCol) {
      val row = UIRow()
      val col = UICol()
      row.add(col)
      col.add(element)
      row
    } else {
      element
    }
  }

  internal fun setLabels(elementInfo: ElementInfo?, element: UILabelledElement) {
    if (elementInfo == null)
      return
    if (!elementInfo.i18nKey.isNullOrEmpty())
      element.label = elementInfo.i18nKey
    if (!elementInfo.additionalI18nKey.isNullOrEmpty() && !element.ignoreAdditionalLabel)
      element.additionalLabel = elementInfo.additionalI18nKey
    if (!elementInfo.tooltipI18nKey.isNullOrEmpty() && !element.ignoreTooltip)
      element.tooltip = elementInfo.tooltipI18nKey
  }

  /**
   * Does translation of buttons and UILabels
   * @param elements List of all elements used in the layout.
   * @return The unmodified parameter elements.
   * @see HibernateUtils.getPropertyLength
   */
  private fun processAllElements(layout: UILayout, elements: List<Any>): List<Any?> {
    var counter = 0
    elements.forEach {
      if (it is UIElement) {
        it.key = "el-${++counter}"
      }
      when (it) {
        is UILabelledElement -> {
          it.label = getLabelTransformation(it.label, it as UIElement)
          it.additionalLabel = getLabelTransformation(it.additionalLabel, it, LabelType.ADDITIONAL_LABEL)
          it.tooltip = getLabelTransformation(it.tooltip, it, LabelType.TOOLTIP)
        }
        is UIFieldset -> {
          it.title = getLabelTransformation(it.title, it as UIElement)
        }
        is UITableColumn -> {
          getLabelTransformation(it.title)?.let { translation ->
            it.title = translation
          }
        }
        is UIAgGridColumnDef -> {
          getLabelTransformation(it.headerName)?.let { translation ->
            it.headerName = translation
          }
        }
        is UIAlert -> {
          val title = getLabelTransformation(it.title)
          if (title != null) it.title = title
          val message = getLabelTransformation(it.message)
          if (message != null) it.message = message
        }
        is UIButton -> {
          if (it.title == null) {
            val i18nKey = when (it.id) {
              "cancel" -> "cancel"
              "clone" -> "clone"
              "create" -> "create"
              "deleteIt" -> "delete"
              "forceDelete" -> "forceDelete"
              "markAsDeleted" -> "markAsDeleted"
              "reset" -> "reset"
              "search" -> "search"
              "undelete" -> "undelete"
              "update" -> "save"
              else -> null
            }
            if (i18nKey == null) {
              log.error("i18nKey not found for action button '${it.id}'.")
            } else {
              it.title = translate(i18nKey)
            }
          }
          if (!it.confirmMessage.isNullOrBlank()) {
            layout.addTranslations("cancel", "yes")
          }
          val tooltip = getLabelTransformation(it.tooltip)
          if (tooltip != null) it.tooltip = tooltip

        }
        is UIList -> {
          // Translate position label
          it.positionLabel = translate(it.positionLabel)
        }
        is UIAttachmentList -> {
          it.addTranslations(layout)
        }
        is UIDropArea -> {
          it.title = getLabelTransformation(it.title, it as UIElement)
          it.tooltip = getLabelTransformation(it.tooltip, it, LabelType.TOOLTIP)
        }
      }
    }
    return elements
  }

  /**
   * @return The id of the given element if supported.
   */
  internal fun getId(element: UIElement?, followLabelReference: Boolean = true): String? {
    if (element == null) return null
    if (followLabelReference && element is UILabel) {
      return getId(element.reference)
    }
    return when (element) {
      is UIInput -> element.id
      is UICheckbox -> element.id
      is UICreatableSelect -> element.id
      is UIRadioButton -> element.id
      is UIReadOnlyField -> element.id
      is UISelect<*> -> element.id
      is UITextArea -> element.id
      is UITableColumn -> element.id
      else -> null
    }
  }

  /**
   * If the given label starts with "'" the label itself as substring after "'" will be returned: "'This is an text." -> "This is an text"<br>
   * Otherwise method [translate] will be called and the result returned.
   * @param label to process
   * @return Modified label or unmodified label.
   */
  internal fun getLabelTransformation(
    label: String?,
    labelledElement: UIElement? = null,
    labelType: LabelType? = null
  ): String? {
    if (label == null) {
      if (labelledElement is UILabelledElement) {
        val layoutSettings = labelledElement.layoutContext
        if (layoutSettings != null) {
          val id = getId(labelledElement)
          if (id != null) {
            val elementInfo = ElementsRegistry.getElementInfo(layoutSettings, id)
            when (labelType) {
              LabelType.ADDITIONAL_LABEL -> {
                if (labelledElement.ignoreAdditionalLabel) {
                  return null
                } else if (elementInfo?.additionalI18nKey != null) {
                  return translate(elementInfo.additionalI18nKey)
                }
              }
              LabelType.TOOLTIP -> {
                if (labelledElement.ignoreTooltip) {
                  return null
                } else if (elementInfo?.tooltipI18nKey != null) {
                  return translate(elementInfo.tooltipI18nKey)
                }
              }
              else -> {
                if (elementInfo?.i18nKey != null) {
                  return translate(elementInfo.i18nKey)
                }
              }
            }
          }
        }
      }
      return null
    }
    return translate(label)
  }

  /**
   * @param layoutTitle I18n-key. If already translated use trailing apostrophe.
   * @param message I18n-key for alert box. If already translated use trailing apostrophe.
   * @param color Color of alert box. [UIColor.INFO] is default.
   * @param alertTitle Optional I18n-key for alert box.  If already translated use trailing apostrophe.
   * @param markDown If true, the message will be converted from markdown to html. Default is false.
   * @param id Optional id of the alert box.
   * @param data Optional data object (will be sent to client).
   */
  fun getMessageFormLayoutData(
    layoutTitle: String,
    message: String,
    color: UIColor? = UIColor.INFO,
    alertTitle: String? = null,
    markDown: Boolean? = null,
    id: String? = null,
    data: Any? = null,
  ): FormLayoutData {
    val layout = getMessageLayout(layoutTitle, message, color, alertTitle, markDown, id)
    return FormLayoutData(data, layout, ServerData())
  }

  /**
   * @param layoutTitle I18n-key. If already translated use trailing apostrophe.
   * @param message I18n-key for alert box. If already translated use trailing apostrophe.
   * @param color Color of alert box. [UIColor.INFO] is default.
   * @param alertTitle Optional I18n-key for alert box.  If already translated use trailing apostrophe.
   * @param markDown If true, the message will be converted from markdown to html. Default is false.
   * @param id Optional id of the alert box.
   */
  fun getMessageLayout(
    layoutTitle: String,
    message: String,
    color: UIColor? = UIColor.INFO,
    alertTitle: String? = null,
    markDown: Boolean? = null,
    id: String? = null,
  ): UILayout {
    val layout = UILayout(layoutTitle)
    layout.add(UIAlert(message, title = alertTitle, id = id, color = color, markdown = markDown))
    process(layout)
    return layout
  }

  internal enum class LabelType { ADDITIONAL_LABEL, TOOLTIP }

  const val DEFAULT_MIN_LENGTH_OF_TEXT_AREA = 256
}
