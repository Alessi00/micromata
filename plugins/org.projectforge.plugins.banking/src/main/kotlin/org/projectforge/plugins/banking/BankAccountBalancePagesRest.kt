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

package org.projectforge.plugins.banking

import org.apache.commons.lang3.StringUtils
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.api.BaseSearchFilter
import org.projectforge.framework.persistence.api.MagicFilter
import org.projectforge.framework.persistence.api.MagicFilterEntry
import org.projectforge.framework.persistence.api.QueryFilter
import org.projectforge.framework.persistence.api.impl.CustomResultFilter
import org.projectforge.menu.MenuItem
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDTOPagesRest
import org.projectforge.rest.core.PagesResolver
import org.projectforge.ui.*
import org.projectforge.ui.filter.UIFilterListElement
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("${Rest.URL}/bankAccountBalance")
class BankAccountBalancePagesRest :
  AbstractDTOPagesRest<BankAccountBalanceDO, BankAccountBalance, BankAccountBalanceDao>(
    BankAccountBalanceDao::class.java,
    "plugins.banking.account.balance.title",
    cloneSupport = CloneSupport.CLONE
  ) {
  @Autowired
  private lateinit var bankAccountDao: BankAccountDao

  override fun transformFromDB(obj: BankAccountBalanceDO, editMode: Boolean): BankAccountBalance {
    val bankAccountBalance = BankAccountBalance()
    bankAccountBalance.copyFrom(obj)
    return bankAccountBalance
  }

  override fun transformForDB(dto: BankAccountBalance): BankAccountBalanceDO {
    val bankAccountBalanceDO = BankAccountBalanceDO()
    dto.copyTo(bankAccountBalanceDO)
    return bankAccountBalanceDO
  }


  /**
   * LAYOUT List page
   */
  override fun createListLayout(
    request: HttpServletRequest,
    layout: UILayout,
    magicFilter: MagicFilter,
    userAccess: UILayout.UserAccess
  ) {
    agGridSupport.prepareUIGrid4ListPage(
      request,
      layout,
      magicFilter,
      this,
      userAccess = userAccess,
    )
      .add("bankAccount.iban", headerName = "plugins.banking.account.iban")
      .add("bankAccount.name", headerName = "plugins.banking.account.name")
      .add(
        lc,
        BankAccountBalanceDO::date,
        BankAccountBalanceDO::amount,
        BankAccountBalanceDO::comment,
      )
    layout.add(
      MenuItem(
        "banking.account.list",
        i18nKey = "plugins.banking.account.title.list",
        url = PagesResolver.getListPageUrl(BankAccountPagesRest::class.java),
      )
    )
  }

  override fun addMagicFilterElements(elements: MutableList<UILabelledElement>) {
    val accountsFilter = UIFilterListElement(
      "accounts",
      label = translate("plugins.banking.accounts"),
      defaultFilter = true,
      multi = true,
    )
    val values = mutableListOf<UISelectValue<String>>()
    bankAccountDao.getList(BaseSearchFilter())?.forEach { account ->
      values.add(UISelectValue("${account.id}", StringUtils.abbreviate(account.name, 20)))
    }
    accountsFilter.values = values
    elements.add(accountsFilter)
  }

  override fun preProcessMagicFilter(
    target: QueryFilter,
    source: MagicFilter
  ): List<CustomResultFilter<BankAccountBalanceDO>>? {
    source.entries.find { it.field == "accounts" }?.let { entry ->
      entry.synthetic = true
      val ids = entry.value.values?.mapNotNull { it.toIntOrNull() }
      if (!ids.isNullOrEmpty()) {
        target.add(QueryFilter.eq("bankAccount.id", ids[0]))
      }
    }
    return null
  }

  override fun getInitialList(request: HttpServletRequest): InitialListData {
    val magicFilter = getCurrentFilter()
    val bankAccountId = request.getParameter("bankAccount")?.toIntOrNull()
    if (bankAccountId != null) {
      bankAccountDao.getById(bankAccountId)?.let {
        // Show only Balances of given bank account.
        var filterEntry = magicFilter.entries.find { it.field == "accounts" }
        if (filterEntry == null) {
          filterEntry = MagicFilterEntry("accounts")
          magicFilter.entries.add(filterEntry)
        }
        filterEntry.value.values = arrayOf(bankAccountId.toString())
      }
    }
    return getInitialList(request, magicFilter)
  }

  /**
   * LAYOUT Edit page
   */
  override fun createEditLayout(dto: BankAccountBalance, userAccess: UILayout.UserAccess): UILayout {
    val layout = super.createEditLayout(dto, userAccess)
      .add(
        LayoutBuilder.createRowWithColumns(
          UILength(md = 6),
          UIReadOnlyField("bankAccount.name", label = "plugins.banking.account.Balance.accountName"),
          UIReadOnlyField("bankAccount.iban", label = "plugins.banking.account.Balance.accountIban"),
        )
      )
      .add(
        UIRow()
          .add(
            UICol(md = 6)
              .add(
                LayoutBuilder.createRowWithColumns(
                  UILength(md = 6),
                  LayoutBuilder.createElement(lc, BankAccountBalanceDO::date),
                  LayoutBuilder.createElement(lc, BankAccountBalanceDO::amount),
                )
              )
          )
      )
      .add(lc, BankAccountBalanceDO::comment)
    return LayoutUtils.processEditPage(layout, dto, this)
  }
}
