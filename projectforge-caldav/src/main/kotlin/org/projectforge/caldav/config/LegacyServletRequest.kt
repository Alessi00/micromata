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

import jakarta.servlet.ServletRequest
import java.io.BufferedReader
import java.util.*
import javax.servlet.*

class LegacyServletRequest(val request: ServletRequest): javax.servlet.ServletRequest {
  override fun getAttribute(p0: String?): Any? {
    return request.getAttribute(p0)
  }

  override fun getAttributeNames(): Enumeration<String>? {
    return request.attributeNames
  }

  override fun getCharacterEncoding(): String? {
    return request.characterEncoding
  }

  override fun setCharacterEncoding(p0: String?) {
    request.characterEncoding = p0
  }

  override fun getContentLength(): Int {
    return request.contentLength
  }

  override fun getContentLengthLong(): Long {
    return request.contentLengthLong
  }

  override fun getContentType(): String? {
    return request.contentType
  }

  override fun getInputStream(): ServletInputStream {
    throw NotImplementedError("getInputStream()")
  }

  override fun getParameter(p0: String?): String? {
    return request.getParameter(p0)
  }

  override fun getParameterNames(): Enumeration<String>? {
    return request.parameterNames
  }

  override fun getParameterValues(p0: String?): Array<String?>? {
    return request.getParameterValues(p0)
  }

  override fun getParameterMap(): MutableMap<String, Array<String?>>? {
    return request.parameterMap
  }

  override fun getProtocol(): String? {
    return request.protocol
  }

  override fun getScheme(): String? {
    return request.scheme
  }

  override fun getServerName(): String? {
    return request.serverName
  }

  override fun getServerPort(): Int {
    return request.serverPort
  }

  override fun getReader(): BufferedReader? {
    return request.reader
  }

  override fun getRemoteAddr(): String? {
    return request.remoteAddr
  }

  override fun getRemoteHost(): String? {
    return request.remoteHost
  }

  override fun setAttribute(p0: String?, p1: Any?) {
    request.setAttribute(p0, p1)
  }

  override fun removeAttribute(p0: String?) {
    request.removeAttribute(p0)
  }

  override fun getLocale(): Locale? {
    return request.locale
  }

  override fun getLocales(): Enumeration<Locale>? {
    return request.locales
  }

  override fun isSecure(): Boolean {
    return request.isSecure
  }

  override fun getRequestDispatcher(p0: String?): RequestDispatcher? {
    throw NotImplementedError("getRequestDispatcher(String)")
  }

  @Deprecated("")
  override fun getRealPath(p0: String?): String? {
    throw NotImplementedError("getRequestDispatcher(String)")
  }

  override fun getRemotePort(): Int {
    return request.remotePort
  }

  override fun getLocalName(): String? {
    return request.localName
  }

  override fun getLocalAddr(): String? {
    return request.localAddr
  }

  override fun getLocalPort(): Int {
    return request.localPort
  }

  override fun getServletContext(): ServletContext {
    throw NotImplementedError("getServletContext()")
  }

  override fun startAsync(): AsyncContext {
    throw NotImplementedError("startAsync()")
  }

  override fun startAsync(p0: javax.servlet.ServletRequest?, p1: ServletResponse?): AsyncContext {
    throw NotImplementedError("startAsync(ServletRequest, ServletResponse)")
  }

  override fun isAsyncStarted(): Boolean {
    return request.isAsyncStarted
  }

  override fun isAsyncSupported(): Boolean {
    return request.isAsyncSupported
  }

  override fun getAsyncContext(): AsyncContext {
    throw NotImplementedError("getAsyncContext()")
  }

  override fun getDispatcherType(): DispatcherType {
    throw NotImplementedError("getDispatcherType()")
  }
}
