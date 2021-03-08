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

package org.projectforge.business.multitenancy;

import de.micromata.genome.jpa.IEmgr;
import de.micromata.genome.jpa.impl.TableTruncater;
import de.micromata.genome.jpa.metainf.EntityMetadata;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.framework.persistence.user.entities.TenantDO;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Set;

/**
 * 
 * @author Roger Rene Kommer (r.kommer.extern@micromata.de)
 *
 */
public class TenantTableTruncater implements TableTruncater
{

  @Override
  public int truncateTable(IEmgr<?> emgr, EntityMetadata entity)
  {
    EntityManager entityManager = emgr.getEntityManager();
    List<TenantDO> tenants = entityManager
        .createQuery("select e from " + TenantDO.class.getName() + " e", TenantDO.class).getResultList();
    for (TenantDO t : tenants) {
      Set<PFUserDO> assignedUser = t.getAssignedUsers();
      if (assignedUser != null) {
        assignedUser.clear();
      }
      entityManager.persist(t);
      entityManager.remove(t);
    }
    return tenants.size();
  }

}
