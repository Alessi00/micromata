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

import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.framework.persistence.user.entities.TenantDO;

import java.util.Collection;

public interface TenantService
{

  TenantDO getDefaultTenant();

  boolean isMultiTenancyAvailable();

  boolean hasTenants();

  String getUsernameCommaList(TenantDO tenant);

  TenantDO getTenant(Integer id);

  Collection<TenantDO> getTenantsOfUser(Integer userId);

  Collection<TenantDO> getTenantsOfLoggedInUser();

  boolean isUserAssignedToTenant(Integer tenantId, Integer userId);

  boolean isUserAssignedToTenant(TenantDO tenant, Integer userId);

  Collection<TenantDO> getAllTenants();

  boolean hasSelectAccess(PFUserDO loggedInUser, TenantDO tenant, boolean b);

  String getLogName(TenantDO tenant);

  String getTenantShortNames(Collection<TenantDO> tenants);

  void resetTenantTableStatus();

}
