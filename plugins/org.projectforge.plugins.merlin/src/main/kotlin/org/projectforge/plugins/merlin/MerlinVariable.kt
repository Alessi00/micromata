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

package org.projectforge.plugins.merlin

import de.micromata.merlin.word.templating.DependentVariableDefinition
import de.micromata.merlin.word.templating.VariableDefinition
import org.projectforge.ui.UIColor

/**
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
class MerlinVariable(
  val name: String,
  val definition: VariableDefinition? = null,
  val dependentVariableDefinition: DependentVariableDefinition? = null,
  var used: Boolean? = null,
  var masterVariable: Boolean? = null,
) {
  val dependant: Boolean
    get() = dependentVariableDefinition != null
  val input: Boolean
    get() = definition != null || !dependant

  val uiColor: UIColor?
    get() {
      return if (masterVariable == true) {
        UIColor.DANGER
      } else if (dependant) {
        UIColor.SECONDARY
      } else if (input) {
        UIColor.SUCCESS
      } else if (used == false) {
        UIColor.LIGHT
      } else {
        null
      }
    }
}
