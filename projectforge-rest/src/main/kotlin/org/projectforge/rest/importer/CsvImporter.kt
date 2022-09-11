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

package org.projectforge.rest.importer

import mu.KotlinLogging
import org.projectforge.common.BeanHelper
import org.projectforge.common.CSVParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.*

private val log = KotlinLogging.logger {}

object CsvImporter {
  /**
   * @param charset to use, if UTF-8 encoding or UTF-16-encoding doesn't fit.
   */
  fun <O : ImportPairEntry.Modified<O>> parse(
    inputStream: InputStream,
    importStorage: ImportStorage<O>,
    defaultCharset: Charset? = null,
  ) {
    val bytes = inputStream.readAllBytes()
    parse(ByteArrayInputStream(bytes).reader(charset = detectCharset(bytes, defaultCharset)), importStorage)
  }

  fun <O : ImportPairEntry.Modified<O>> parse(reader: Reader, importStorage: ImportStorage<O>) {
    val settings = importStorage.importSettings
    val parser = CSVParser(reader)
    val headCols = parser.parseLine()
    headCols.forEachIndexed { index, head ->
      val fieldSettings = settings.getFieldSettings(head)
      if (fieldSettings != null) {
        log.debug { "Field '$head' found: -> ${fieldSettings.property}." }
        importStorage.columnMapping[index] = fieldSettings
        importStorage.detectedColumns[head] = fieldSettings
      } else {
        log.debug { "Field '$head' not found." }
        importStorage.unknownColumns.add(head)
      }
    }
    for (i in 0..100000) { // Paranoi loop, read 100000 lines at max.
      val line = parser.parseLine()
      if (line == null) {
        // Finished
        break
      }
      val record = importStorage.prepareEntity()
      line.forEachIndexed { index, value ->
        importStorage.columnMapping[index]?.let { fieldSettings ->
          if (!importStorage.setProperty(record, fieldSettings, value)) {
            val targetValue = when (BeanHelper.determinePropertyType(record::class.java, fieldSettings.property)) {
              LocalDate::class.java -> {
                fieldSettings.parseLocalDate(value)
              }

              Date::class.java -> {
                fieldSettings.parseDate(value)
              }

              BigDecimal::class.java -> {
                fieldSettings.parseBigDecimal(value)
              }

              Int::class.java -> {
                fieldSettings.parseInt(value)
              }

              Boolean::class.java -> {
                fieldSettings.parseBoolean(value)
              }

              else -> {
                value
              }
            }
            if (targetValue != null) {
              // Don't write null values (don't overwrite existing values given e. g. by previous column).
              if (targetValue is String) {
                if (targetValue.isNotBlank()) {
                  try {
                    val existingValue = BeanHelper.getProperty(record, fieldSettings.property)
                    if (existingValue != null && existingValue is String && existingValue.isNotBlank()) { // Should be a string....
                      if (existingValue.trim() != targetValue.trim()) {
                        // Only concat, if new value differs:
                        BeanHelper.setProperty(record, fieldSettings.property, "$existingValue$targetValue") // concat
                      }
                    } else { // Set value because no existing one as String given:
                      BeanHelper.setProperty(record, fieldSettings.property, targetValue)
                    }
                  } catch (ex: Exception) {
                    log.error("Can't parse property: '${fieldSettings.property}': ${ex.message}"  )
                  }
                }
              } else {
                BeanHelper.setProperty(record, fieldSettings.property, targetValue)
              }
            }
          }
        }
      }
      importStorage.commitEntity(record)
    }
  }

  /**
   * If the users specified e. g. ISO-8859-1 char set, but special bytes of UTF-8 or UTF-16 are found, the
   * returned charset will be UTF-8 or UTF-16.
   */
  internal fun detectCharset(bytes: ByteArray, defaultCharset: Charset?): Charset {
    var utf8EscapeChars = 0
    var utf16NullBytes = 0
    val size = bytes.size
    for (i in 0..100000) {
      if (i >= size) {
        break
      }
      if (bytes[i] == UTF8_ESCAPE_BYTE) {
        utf8EscapeChars += 1
      } else if (bytes[i] == UTF16_NULL_BYTE) {
        utf16NullBytes += 1
      }
    }
    val utf8EscapeCharsRate = utf8EscapeChars * 1000 / size
    val utf16NullBytesRate = utf16NullBytes * 10 / size
    if (utf16NullBytesRate > 1) {
      // More than 1/10 of all bytes are null bytes, seems to be UTF-16.
      return StandardCharsets.UTF_16
    }
    if (utf8EscapeCharsRate > 1) { // More than 1 promille of chars is Ã
      return StandardCharsets.UTF_8
    }
    return defaultCharset ?: StandardCharsets.UTF_8
  }

  private const val UTF8_ESCAPE_BYTE = 195.toByte() // C3
  private const val UTF16_NULL_BYTE = 0.toByte() // 00
}
