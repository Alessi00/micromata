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

package org.projectforge.business.sipgate

import org.projectforge.framework.configuration.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import java.io.*
import java.util.*
import javax.net.ssl.*

/**
 * ProjectForge supports the synchronization from and to a Sipgate.
 */
@Configuration
open class SipgateConfiguration {
  @Value("\${projectforge.sipgate.baseUri}")
  open lateinit var baseUri: String

  /**
   * The token id for authentication.
   */
  @Value("\${projectforge.sipgate.tokenId}")
  open lateinit var tokenId: String

  /**
   * The token for authentication.
   */
  @Value("\${projectforge.sipgate.token}")
  open lateinit var token: String

  /**
   * If true, any changes in Sipgate will update local addresses (false only for testing).
   */
  @Value("\${projectforge.sipgate.updateLocalAddresses}")
  open var updateLocalAddresses: Boolean = true

  fun isConfigured(): Boolean {
    return baseUri.isNotBlank() && token.isNotBlank() && tokenId.isNotBlank()
  }
}