/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.plugins.poll.event;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.projectforge.common.DateFormatType;
import org.projectforge.framework.time.DateFormats;
import org.projectforge.framework.time.PFDateTime;
import org.projectforge.web.wicket.flowlayout.AjaxIconButtonPanel;
import org.projectforge.web.wicket.flowlayout.IconType;

/**
 * @author M. Lauterbach (m.lauterbach@micromata.de)
 */
public abstract class PollEventEntryPanel extends Panel
{
  private static final long serialVersionUID = 8389532050901086582L;

  /**
   * @param id
   * @param model
   */
  public PollEventEntryPanel(final String id, final PollEventDO poll)
  {
    super(id);

    final PFDateTime start = PFDateTime.from(poll.getStartDate()); // not null
    final PFDateTime end = PFDateTime.from(poll.getEndDate()); // not null

    final String pattern = DateFormats.getFormatString(DateFormatType.DATE_TIME_MINUTES);
    add(new Label("startDate", "Start: " + DateFormatUtils.format(start.getEpochMilli(), pattern)));
    add(new Label("endDate", "Ende: " + DateFormatUtils.format(end.getEpochMilli(), pattern)));

    final AjaxIconButtonPanel iconButton = new AjaxIconButtonPanel("delete", IconType.REMOVE)
    {
      private static final long serialVersionUID = -2464985733387718199L;

      /**
       * @see org.projectforge.web.wicket.flowlayout.AjaxIconButtonPanel#onSubmit(org.apache.wicket.ajax.AjaxRequestTarget)
       */
      @Override
      protected void onSubmit(final AjaxRequestTarget target)
      {
        onDeleteClick(target);
      }
    };
    add(iconButton);
  }

  /**
   * @param target
   */
  protected abstract void onDeleteClick(AjaxRequestTarget target);
}
