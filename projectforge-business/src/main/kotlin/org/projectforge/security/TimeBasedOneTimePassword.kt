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

package org.projectforge.security

import mu.KotlinLogging
import java.lang.reflect.UndeclaredThrowableException
import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


private val log = KotlinLogging.logger {}

/**
 * https://datatracker.ietf.org/doc/html/rfc6238
 */
class TimeBasedOneTimePassword(
  private val hmacCrypto: String = "HmacSHA1",
  private val numberOfDigits: Int = 8
) {
  /**
   * @param key - secret credential key (HEX)
   * @return the OTP
   */
  fun getOTP(key: String): String {
    return getOTP(getStep(), key)
  }

  /**
   * @param key - secret credential key (HEX)
   * @param otp - OTP to validate
   * @return valid?
   */
  fun validate(key: String, otp: String): Boolean {
    return validate(getStep(), key, otp)
  }

  internal fun validate(step: Long, key: String, otp: String): Boolean {
    return getOTP(step, key) == otp || getOTP(step - 1, key) == otp
  }

  internal fun getOTP(step: Long, key: String): String {
    // Get the HEX in a Byte[]
    val msg = hexStr2Bytes(asHex(step))
    val k = hexStr2Bytes(key)
    val hash = hmacSHA(k, msg)

    // put selected bytes into result int
    val offset: Int = hash[hash.size - 1].toInt() and 0xf
    val binary: Int = hash[offset].toInt() and 0x7f shl 24 or
        (hash[offset + 1].toInt() and 0xff shl 16) or
        (hash[offset + 2].toInt() and 0xff shl 8) or
        (hash[offset + 3].toInt() and 0xff)
    val otp = binary % DIGITS_POWER[numberOfDigits]
    return otp.toString().padStart(numberOfDigits, '0')
  }


  /**
   * This method uses the JCE to provide the crypto algorithm. HMAC computes a Hashed Message Authentication Code with the crypto hash
   * algorithm as a parameter.
   *
   * @param keyBytes the bytes to use for the HMAC key
   * @param text the message or text to be authenticated.
   */
  private fun hmacSHA(keyBytes: ByteArray, text: ByteArray): ByteArray {
    return try {
      val hmac: Mac = Mac.getInstance(hmacCrypto)
      val macKey = SecretKeySpec(keyBytes, "RAW")
      hmac.init(macKey)
      hmac.doFinal(text)
    } catch (gse: GeneralSecurityException) {
      log.error("Can't create HmacSHA1")
      throw UndeclaredThrowableException(gse)
    }
  }

  companion object {
    private const val timeIntervalMillis = 30000 // 30 seconds

    private val DIGITS_POWER // 0 1  2   3    4     5      6       7        8
        = intArrayOf(1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000)

    internal fun getStep(timeInMillis: Long = System.currentTimeMillis()): Long {
      // 30 seconds step(ID of TOTP)
      return timeInMillis / timeIntervalMillis
    }

    internal fun asHex(step: Long): String {
      // intervalNo as Hex string: "00000000033CB24E"
      return step.toString(16).toUpperCase().padStart(16, '0')
    }

    internal fun hexStr2Bytes(hex: String): ByteArray {
      return hex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
    }
  }
}