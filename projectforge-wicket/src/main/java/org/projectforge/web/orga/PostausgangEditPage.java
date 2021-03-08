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

package org.projectforge.web.orga;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.projectforge.business.orga.PostType;
import org.projectforge.business.orga.PostausgangDO;
import org.projectforge.business.orga.PostausgangDao;
import org.projectforge.framework.time.DayHolder;
import org.projectforge.web.wicket.AbstractEditPage;
import org.projectforge.web.wicket.EditPage;
import org.slf4j.Logger;


@EditPage(defaultReturnPage = PostausgangListPage.class)
public class PostausgangEditPage extends AbstractEditPage<PostausgangDO, PostausgangEditForm, PostausgangDao>
{
  private static final long serialVersionUID = 4375220914096256551L;

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PostausgangEditPage.class);

  @SpringBean
  private PostausgangDao postausgangDao;

  public PostausgangEditPage(final PageParameters parameters)
  {
    super(parameters, "orga.postausgang");
    init();
    if (isNew()) {
      getData().setDatum(new DayHolder().getLocalDate());
      getData().setType(PostType.BRIEF);
    }
  }

  @Override
  protected PostausgangDao getBaseDao()
  {
    return postausgangDao;
  }

  @Override
  protected PostausgangEditForm newEditForm(final AbstractEditPage< ? , ? , ? > parentPage, final PostausgangDO data)
  {
    return new PostausgangEditForm(this, data);
  }

  @Override
  protected Logger getLogger()
  {
    return log;
  }
}
