/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2023 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.caldav.config

import jakarta.servlet.ServletResponse
import java.io.PrintWriter
import java.util.*

class LegacyServletResponse(val response: ServletResponse): javax.servlet.ServletResponse {
  override fun getCharacterEncoding(): String {
    return response.characterEncoding
  }

  override fun getContentType(): String {
    return response.contentType
  }

  override fun getOutputStream(): javax.servlet.ServletOutputStream {
    throw NotImplementedError("getOutputStream()")
  }

  override fun getWriter(): PrintWriter {
    throw NotImplementedError("getOutputStream()")
  }

  override fun setCharacterEncoding(p0: String?) {
    response.characterEncoding = p0
  }

  override fun setContentLength(p0: Int) {
    response.setContentLength(p0)
  }

  override fun setContentLengthLong(p0: Long) {
    response.setContentLengthLong(p0)
  }

  override fun setContentType(p0: String?) {
    response.contentType = p0
  }

  override fun setBufferSize(p0: Int) {
    response.bufferSize = p0
  }

  override fun getBufferSize(): Int {
    return response.bufferSize
  }

  override fun flushBuffer() {
    response.flushBuffer()
  }

  override fun resetBuffer() {
    response.resetBuffer()
  }

  override fun isCommitted(): Boolean {
    return response.isCommitted
  }

  override fun reset() {
    response.reset()
  }

  override fun setLocale(p0: Locale?) {
    response.locale = p0
  }

  override fun getLocale(): Locale {
    return response.locale
  }
}
