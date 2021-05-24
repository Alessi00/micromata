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

package org.projectforge.framework.jcr

import mu.KotlinLogging
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import org.projectforge.SystemStatus
import org.projectforge.business.user.UserGroupCache
import org.projectforge.common.DataSizeConfig
import org.projectforge.common.FormatterUtils
import org.projectforge.common.i18n.UserException
import org.projectforge.framework.access.OperationType
import org.projectforge.framework.persistence.api.BaseDao
import org.projectforge.framework.persistence.api.ExtendedBaseDO
import org.projectforge.framework.persistence.api.IdObject
import org.projectforge.framework.persistence.entities.DefaultBaseDO
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.utils.NumberHelper
import org.projectforge.jcr.FileInfo
import org.projectforge.jcr.FileObject
import org.projectforge.jcr.RepoService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.unit.DataSize
import org.springframework.util.unit.DataUnit
import java.io.InputStream
import javax.annotation.PostConstruct

private val log = KotlinLogging.logger {}

/**
 * Service for handling attachments of DO's. It's possible to attach files to each [IdObject].
 */
@Service
open class AttachmentsService {
  @Value("\${$MAX_DEFAULT_FILE_SIZE_SPRING_PROPERTY:100MB}")
  internal open lateinit var maxDefaultFileSizeConfig: String

  open lateinit var maxDefaultFileSize: DataSize
    internal set

  @PostConstruct
  private fun postConstruct() {
    maxDefaultFileSize = DataSizeConfig.init(maxDefaultFileSizeConfig, DataUnit.MEGABYTES)
    log.info { "Maximum configured default size of attachments: $MAX_DEFAULT_FILE_SIZE_SPRING_PROPERTY=$maxDefaultFileSizeConfig." }
  }

  @Autowired
  private lateinit var repoService: RepoService

  @Autowired
  private lateinit var userGroupCache: UserGroupCache

  /**
   * @param path Unique path of data object.
   * @param id Id of data object.
   */
  @JvmOverloads
  open fun getAttachments(
    path: String,
    id: Any,
    accessChecker: AttachmentsAccessChecker?,
    subPath: String? = null
  ): List<Attachment>? {
    val loggedInUser = ThreadLocalUserContext.getUser()
    accessChecker?.checkSelectAccess(loggedInUser, path = path, id = id, subPath = subPath)
    return internalGetAttachments(path, id, subPath)?.filter {
      accessChecker?.hasAccess(
        loggedInUser,
        path,
        id,
        subPath,
        OperationType.SELECT,
        it
      ) != false
    }
  }

  /**
   * @param path Unique path of data object.
   * @param id Id of data object.
   */
  @JvmOverloads
  open fun internalGetAttachments(
    path: String,
    id: Any,
    subPath: String? = null
  ): List<Attachment>? {
    return repoService.getFileInfos(getPath(path, id), subPath ?: DEFAULT_NODE)?.map { asAttachment(it) }
  }

  /**
   * @param path Unique path of data object.
   * @param id Id of data object.
   */
  @JvmOverloads
  open fun getAttachmentInfo(
    path: String,
    id: Any,
    fileId: String,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null
  ): Attachment? {
    accessChecker.checkSelectAccess(ThreadLocalUserContext.getUser(), path = path, id = id, subPath = subPath)
    val fileObject = repoService.getFileInfo(
      getPath(path, id),
      subPath ?: DEFAULT_NODE,
      fileId = fileId
    )
      ?: return null
    return asAttachment(fileObject)
  }

  /**
   * @param path Unique path of data object.
   * @param id Id of data object.
   */
  @JvmOverloads
  open fun getAttachmentContent(
    path: String,
    id: Any,
    fileId: String,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null
  ): ByteArray? {
    val fileObject = repoService.getFileInfo(
      getPath(path, id),
      subPath ?: DEFAULT_NODE,
      fileId = fileId
    )
      ?: return null
    accessChecker.checkDownloadAccess(
      ThreadLocalUserContext.getUser(),
      path = path,
      id = id,
      file = fileObject,
      subPath = subPath
    )
    return if (repoService.retrieveFile(fileObject)) {
      fileObject.content
    } else {
      null
    }
  }

  /**
   * @param path Unique path of data object.
   * @param id Id of data object.
   */
  @JvmOverloads
  open fun getAttachmentInputStream(
    path: String,
    id: Any,
    fileId: String,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null,
    attachmentsEventListener: AttachmentsEventListener? = null,
    /**
     * data for AttachmentsEventListener if needed.
     */
    data: Any? = null,
    /**
     * Only for external users. Otherwise logged in user will be assumed.
     */
    userString: String? = null
  )
      : Pair<FileObject, InputStream>? {
    val fileObject = repoService.getFileInfo(
      getPath(path, id),
      subPath ?: DEFAULT_NODE,
      fileId = fileId
    ) ?: return null
    accessChecker.checkDownloadAccess(
      ThreadLocalUserContext.getUser(),
      path = path,
      id = id,
      file = fileObject,
      subPath = subPath
    )
    val inputStream = if (fileObject != null) {
      repoService.retrieveFileInputStream(fileObject)
    } else {
      null
    }
    if (fileObject == null || inputStream == null) {
      log.error {
        "Can't download file of ${
          getPath(
            path,
            id
          )
        } #$fileId, because user has no access to this object or it doesn't exist."
      }
      return null
    }
    attachmentsEventListener?.onAttachmentEvent(
      AttachmentsEventType.DOWNLOAD,
      fileObject,
      data,
      ThreadLocalUserContext.getUser(),
      userString
    )
    return Pair(fileObject, inputStream)
  }

  /**
   * @param path Unique path of data object.
   * @param id Id of data object.
   * @param password Optional password for encryption. The password will not be stored in any kind!
   */
  @JvmOverloads
  open fun addAttachment(
    path: String,
    id: Any,
    fileInfo: FileInfo,
    content: ByteArray,
    enableSearchIndex: Boolean,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null,
    password: String? = null,
  ): Attachment {
    return addAttachment(
      path,
      id,
      fileInfo,
      content.inputStream(),
      enableSearchIndex,
      accessChecker,
      subPath = subPath,
      password = password
    )
  }

  /**
   * @param path Unique path of data object.
   * @param password Optional password for encryption. The password will not be stored in any kind!
   */
  @JvmOverloads
  open fun addAttachment(
    path: String,
    fileInfo: FileInfo,
    content: ByteArray,
    baseDao: BaseDao<out ExtendedBaseDO<Int>>,
    obj: ExtendedBaseDO<Int>,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null,
    password: String? = null,
  )
      : Attachment {
    return addAttachment(
      path,
      fileInfo,
      content.inputStream(),
      baseDao,
      obj,
      accessChecker,
      subPath = subPath,
      password = password
    )
  }

  /**
   * @param path Unique path of data object.
   * @param id Id of data object.
   * @param password Optional password for encryption. The password will not be stored in any kind!
   */
  @JvmOverloads
  open fun addAttachment(
    path: String,
    id: Any,
    fileInfo: FileInfo,
    inputStream: InputStream,
    enableSearchIndex: Boolean,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null,
    password: String? = null,
    /**
     * Only for external users. Otherwise logged in user will be assumed.
     */
    userString: String? = null,
    /**
     * Optional data e. g. for fileSizeChecker of data transfer area size.
     */
    data: Any? = null
  ): Attachment {
    developerWarning(path, id, "addAttachment", enableSearchIndex)
    accessChecker.checkUploadAccess(ThreadLocalUserContext.getUser(), path = path, id = id, subPath = subPath)
    repoService.ensureNode(null, getPath(path, id))
    val fileObject = FileObject(getPath(path, id), subPath ?: DEFAULT_NODE, fileInfo = fileInfo)
    fileObject.isCrypted = !password.isNullOrBlank()
    repoService.storeFile(
      fileObject,
      inputStream,
      accessChecker.fileSizeChecker,
      userString ?: ThreadLocalUserContext.getUserId()!!.toString(),
      data,
      password = password
    )
    return asAttachment(fileObject)
  }

  /**
   * @param path Unique path of data object.
   * @param password Optional password for encryption. The password will not be stored in any kind!
   */
  @JvmOverloads
  open fun addAttachment(
    path: String,
    fileInfo: FileInfo,
    inputStream: InputStream,
    baseDao: BaseDao<out ExtendedBaseDO<Int>>,
    obj: ExtendedBaseDO<Int>,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null,
    password: String? = null,
    /**
     * Only for external users. Otherwise logged in user will be assumed.
     */
    userString: String? = null
  )
      : Attachment {
    accessChecker.checkUploadAccess(ThreadLocalUserContext.getUser(), path = path, id = obj.id, subPath = subPath)
    val attachments = getAttachments(path, obj.id, null, subPath)
    attachments?.forEach { attachment ->
      if (attachment.name == fileInfo.fileName && attachment.size == fileInfo.size) {
        log.warn { "Can't upload file '${fileInfo.fileName}' of size ${FormatterUtils.formatBytes(fileInfo.size)}. A file with same name and of same size does already exist." }
        throw UserException("plugins.datatransfer.validation.error.fileAlreadyExists")
      }
    }
    val attachment =
      addAttachment(path, obj.id, fileInfo, inputStream, false, accessChecker, subPath, password, userString, obj)
    updateAttachmentsInfo(
      path,
      baseDao,
      obj,
      AttachmentsEventType.UPLOAD,
      fileInfo,
      subPath = subPath,
      lastUserAction = "Attachment uploaded: '${fileInfo.fileName}'.",
      userString = userString
    )
    return attachment
  }

  /**
   * @param path Unique path of data object.
   * @param id Id of data object.
   */
  @JvmOverloads
  open fun deleteAttachment(
    path: String,
    id: Any,
    fileId: String,
    enableSearchIndex: Boolean,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null
  )
      : Boolean {
    developerWarning(path, id, "deleteAttachment", enableSearchIndex)
    accessChecker.checkDeleteAccess(
      ThreadLocalUserContext.getUser(),
      path = path,
      id = id,
      fileId = fileId,
      subPath = subPath
    )
    val fileObject = FileObject(getPath(path, id), subPath ?: DEFAULT_NODE, fileId = fileId)
    return repoService.deleteFile(fileObject)
  }

  /**
   * @param path Unique path of data object.
   */
  @JvmOverloads
  open fun deleteAttachment(
    path: String,
    fileId: String,
    baseDao: BaseDao<out ExtendedBaseDO<Int>>,
    obj: ExtendedBaseDO<Int>,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null
  )
      : Boolean {
    accessChecker.checkDeleteAccess(
      ThreadLocalUserContext.getUser(),
      path = path,
      id = obj.id,
      fileId = fileId,
      subPath = subPath
    )
    return internalDeleteAttachment(path, fileId, baseDao, obj, subPath)
  }

  /**
   * Without access checking (needed by clean-up job of data transfer).
   */
  @JvmOverloads
  open fun internalDeleteAttachment(
    path: String,
    fileId: String,
    baseDao: BaseDao<out ExtendedBaseDO<Int>>,
    obj: ExtendedBaseDO<Int>,
    subPath: String? = null,
    userString: String? = null
  )
      : Boolean {
    val fileObject = FileObject(getPath(path, obj.id), subPath ?: DEFAULT_NODE, fileId = fileId)
    val result = repoService.deleteFile(fileObject)
    if (result) {
      updateAttachmentsInfo(
        path,
        baseDao,
        obj,
        AttachmentsEventType.DELETE,
        fileObject,
        subPath = subPath,
        lastUserAction = "Attachment '${fileObject.fileName}' deleted.",
        userString = userString
      )
    }
    return result
  }


  /**
   * @param path Unique path of data object.
   * @param id Id of data object.
   */
  @JvmOverloads
  open fun changeFileInfo(
    path: String,
    id: Any,
    fileId: String,
    enableSearchIndex: Boolean,
    newFileName: String?,
    newDescription: String?,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null
  )
      : FileObject? {
    developerWarning(path, id, "changeProperty", enableSearchIndex)
    accessChecker.checkUpdateAccess(
      ThreadLocalUserContext.getUser(),
      path = path,
      id = id,
      fileId = fileId,
      subPath = subPath
    )
    val fileObject = FileObject(getPath(path, id), subPath ?: DEFAULT_NODE, fileId = fileId)
    return repoService.changeFileInfo(
      fileObject,
      user = ThreadLocalUserContext.getUserId()!!.toString(),
      newFileName = newFileName,
      newDescription = newDescription
    )
  }

  /**
   * @param path Unique path of data object.
   */
  @JvmOverloads
  open fun changeFileInfo(
    path: String,
    fileId: String,
    baseDao: BaseDao<out ExtendedBaseDO<Int>>,
    obj: ExtendedBaseDO<Int>,
    newFileName: String?,
    newDescription: String?,
    accessChecker: AttachmentsAccessChecker,
    subPath: String? = null,
    /**
     * Only for external users. Otherwise logged in user will be assumed.
     */
    userString: String? = null,
  )
      : FileObject? {
    accessChecker.checkUpdateAccess(
      ThreadLocalUserContext.getUser(),
      path = path,
      id = obj.id,
      fileId = fileId,
      subPath = subPath
    )
    val fileObject = FileObject(getPath(path, obj.id), subPath ?: DEFAULT_NODE, fileId = fileId)
    val result = repoService.changeFileInfo(
      fileObject,
      ThreadLocalUserContext.getUserId()?.toString() ?: userString!!,
      newFileName,
      newDescription
    )
    if (result != null) {
      val fileNameChanged = if (!newFileName.isNullOrBlank()) "filename='$newFileName'" else null
      val descriptionChanged = if (newDescription != null) "description='$newDescription'" else null
      updateAttachmentsInfo(
        path,
        baseDao,
        obj,
        AttachmentsEventType.MODIFICATION,
        fileObject,
        subPath = subPath,
        lastUserAction = "Attachment infos changed of file '${result.fileName}': ${fileNameChanged ?: " "}${descriptionChanged ?: ""}".trim()
      )
    }
    return result
  }

  /**
   * Path will be path/id.
   * @return path relative to main node ProjectForge.
   */
  open fun getPath(path: String, id: Any): String {
    return "$path/$id"
  }

  private fun updateAttachmentsInfo(
    path: String,
    baseDao: BaseDao<out ExtendedBaseDO<Int>>,
    obj: ExtendedBaseDO<Int>,
    event: AttachmentsEventType,
    fileInfo: FileInfo,
    subPath: String? = null,
    lastUserAction: String? = null,
    /**
     * Only for external users. Otherwise logged in user will be assumed.
     */
    userString: String? = null
  ) {
    if (obj !is AttachmentsInfo) {
      return // Nothing to do.
    }
    val dbObj = baseDao.internalGetById(obj.id)
    if (dbObj is AttachmentsInfo) {
      // TODO: multiple subPath support (all attachments of all lists should be used for indexing).
      if (subPath != null && subPath != DEFAULT_NODE) {
        log.warn("********* Support of multiple lists in attachments not yet supported by search index.")
      }
      val attachments = getAttachments(path, obj.id, null)//, subPath)
      if (attachments != null) {
        dbObj.attachmentsNames = attachments.joinToString(separator = " ") { "${it.name}" }
        dbObj.attachmentsIds = attachments.joinToString(separator = " ") { "${it.fileId}" }
        dbObj.attachmentsCounter = attachments.size
        dbObj.attachmentsSize = attachments.sumByLong { it.size ?: 0 }
        if (fileInfo.fileName.isNullOrBlank() && fileInfo is FileObject) {
          // Try to get filename from attachments
          fileInfo.fileName = attachments.find { it.fileId == fileInfo.fileId }?.name
        }
      } else {
        dbObj.attachmentsNames = null
        dbObj.attachmentsIds = null
        dbObj.attachmentsCounter = null
        dbObj.attachmentsSize = null
      }
      if (dbObj is DefaultBaseDO && lastUserAction != null) {
        dbObj.attachmentsLastUserAction = lastUserAction
      }
      if (baseDao is AttachmentsEventListener) {
        baseDao.onAttachmentEvent(event, fileInfo, dbObj, ThreadLocalUserContext.getUser(), userString)
      }
      // Without access checking, because there is no logged-in user or access checking is already done by caller.
      baseDao.internalUpdateAny(dbObj)
    } else {
      val msg =
        "Can't update search index of ${dbObj::class.java.name}. Dear developer, it's not of type ${AttachmentsInfo::class.java.name}!"
      if (SystemStatus.isDevelopmentMode()) {
        throw UnsupportedOperationException(msg)
      }
      log.warn { msg }
    }
  }

  private fun asAttachment(fileObject: FileObject): Attachment {
    val attachment = Attachment(fileObject)
    NumberHelper.parseInteger(fileObject.createdByUser, false)?.let {
      val user = userGroupCache.getUser(it)
      attachment.createdByUser = user?.getFullname()
      attachment.createdByUserId = user?.id
    }
    NumberHelper.parseInteger(fileObject.lastUpdateByUser, false)?.let {
      attachment.lastUpdateByUser = userGroupCache.getUser(it)?.getFullname()
    }
    return attachment
  }

  private fun developerWarning(path: String, id: Any, method: String, enableSearchIndex: Boolean) {
    if (enableSearchIndex) {
      val msg = "Can't update search index of ${
        getPath(
          path,
          id
        )
      }. Dear developer, call method '$method' with data object and baseDao instead!"
      if (SystemStatus.isDevelopmentMode()) {
        throw UnsupportedOperationException(msg)
      }
      log.warn { msg }
    }
  }

  companion object {
    const val DEFAULT_NODE = "attachments"
    const val MAX_DEFAULT_FILE_SIZE_SPRING_PROPERTY = "projectforge.jcr.maxDefaultFileSize"
  }
}
