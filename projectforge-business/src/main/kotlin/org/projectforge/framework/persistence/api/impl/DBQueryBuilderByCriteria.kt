/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.framework.persistence.api.impl

import org.projectforge.framework.persistence.api.BaseDao
import org.projectforge.framework.persistence.api.ExtendedBaseDO
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

internal class DBQueryBuilderByCriteria<O : ExtendedBaseDO<Int>>(
        val baseDao: BaseDao<O>
) {
    var cb_: CriteriaBuilder? = null
    val cb: CriteriaBuilder
        get() {
            if (cb_ == null) cb_ = baseDao.session.getCriteriaBuilder()
            return cb_!!
        }
    var cr_: CriteriaQuery<O>? = null
    val cr: CriteriaQuery<O>
        get() {
            if (cr_ == null) cr_ = cb.createQuery(baseDao.doClass)
            return cr_!!
        }
    var root_: Root<O>? = null
    val root: Root<O>
        get() {
            if (root_ == null) root_ = cr.from(baseDao.doClass)
            return root_!!
        }

    /**
     * predicates for criteria search.
     */
    private val predicates = mutableListOf<Predicate>()
    private val order = mutableListOf<javax.persistence.criteria.Order>()

    fun add(matcher: DBPredicate) {
        predicates.add(matcher.asPredicate(cb, root))
    }

    fun createResultIterator(): DBResultIterator<O> {
        return DBCriteriaResultIterator(baseDao.session, cr.select(root).where(*predicates.toTypedArray()).orderBy(*order.toTypedArray()))
    }

    fun addOrder(sortBy: SortBy) {
        order.add(
                if (sortBy.ascending) cb.asc(root.get<Any>(sortBy.field))
                else cb.desc(root.get<Any>(sortBy.field))
        )
    }
}
