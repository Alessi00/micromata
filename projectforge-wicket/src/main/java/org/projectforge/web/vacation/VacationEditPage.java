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

package org.projectforge.web.vacation;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.projectforge.business.vacation.model.VacationDO;
import org.projectforge.business.vacation.model.VacationStatus;
import org.projectforge.business.vacation.service.VacationCalendarService;
import org.projectforge.business.vacation.service.VacationSendMailService;
import org.projectforge.business.vacation.service.VacationService;
import org.projectforge.framework.i18n.I18nHelper;
import org.projectforge.web.fibu.ISelectCallerPage;
import org.projectforge.web.wicket.AbstractEditPage;
import org.projectforge.web.wicket.AbstractSecuredBasePage;
import org.projectforge.web.wicket.EditPage;
import org.slf4j.Logger;

@EditPage(defaultReturnPage = VacationListPage.class)
public class VacationEditPage extends AbstractEditPage<VacationDO, VacationEditForm, VacationService>
    implements ISelectCallerPage
{
  private static final long serialVersionUID = -3899191243765232906L;

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VacationEditPage.class);

  @SpringBean
  private VacationService vacationService;

  @SpringBean
  private VacationCalendarService vacationCalendarService;

  @SpringBean
  private VacationSendMailService vacationMailService;

  Integer employeeIdFromPageParameters;

  private boolean wasNew = false;

  public VacationEditPage(final PageParameters parameters)
  {
    super(parameters, "vacation");
    if (parameters.get("employeeId") != null && parameters.get("employeeId").toString() != null) {
      this.employeeIdFromPageParameters = parameters.get("employeeId").toInt();
    }
    init();
  }

  @Override
  protected void init()
  {
    super.init();
    if (isNew()) {
      wasNew = true;
    }
  }

  /**
   * @see org.projectforge.web.fibu.ISelectCallerPage#select(java.lang.String, java.lang.Object)
   */
  @Override
  public void select(final String property, final Object selectedValue)
  {
  }

  /**
   * @see org.projectforge.web.fibu.ISelectCallerPage#unselect(java.lang.String)
   */
  @Override
  public void unselect(final String property)
  {
  }

  /**
   * @see org.projectforge.web.fibu.ISelectCallerPage#cancelSelection(java.lang.String)
   */
  @Override
  public void cancelSelection(final String property)
  {
    // Do nothing.
  }

  @Override
  protected VacationService getBaseDao()
  {
    return vacationService;
  }

  @Override
  protected VacationEditForm newEditForm(final AbstractEditPage<?, ?, ?> parentPage, final VacationDO data)
  {
    return new VacationEditForm(this, data);
  }

  @Override
  protected Logger getLogger()
  {
    return log;
  }

  @Override
  public AbstractSecuredBasePage onSaveOrUpdate()
  {
    if (isNew() == false && VacationStatus.REJECTED.equals(form.getStatusBeforeModification()) == true) {
      form.getData().setStatus(VacationStatus.IN_PROGRESS);
    }
    return null;
  }

  @Override
  public AbstractSecuredBasePage afterSaveOrUpdate()
  {
    try {
      vacationCalendarService.saveOrUpdateVacationCalendars(form.getData(), form.assignCalendarListHelper.getAssignedItems());
      if (wasNew) {
        vacationMailService.sendMailToVacationInvolved(form.getData(), true, false);
      } else if (VacationStatus.IN_PROGRESS == form.getData().getStatus()) {
        vacationMailService.sendMailToVacationInvolved(form.getData(), false, false);
      }
      if (VacationStatus.APPROVED.equals(form.getData().getStatus())) {
        //To intercept special cases for add or delete team calendars
        vacationCalendarService.markTeamEventsOfVacationAsDeleted(form.getData(), false);
        vacationCalendarService.createEventsForVacationCalendars(form.getData());
      }
      if (form.getStatusBeforeModification() != null) {
        if (form.getStatusBeforeModification() == VacationStatus.IN_PROGRESS) {
          switch (form.getData().getStatus()) {
            case APPROVED:
              // IN_PROGRESS -> APPROVED
              // Not needed anymore: vacationService.updateUsedVacationDaysFromLastYear(form.getData());
              vacationMailService.sendMailToEmployeeAndHR(form.getData(), true);
              break;

            case REJECTED:
              // IN_PROGRESS -> REJECTED
              vacationMailService.sendMailToEmployeeAndHR(form.getData(), false);
              break;

            default:
              // nothing to do
          }
        }
        if (form.getStatusBeforeModification() == VacationStatus.APPROVED) {
          switch (form.getData().getStatus()) {
            case REJECTED:
            case IN_PROGRESS:  // APPROVED -> NOT APPROVED
              vacationCalendarService.markTeamEventsOfVacationAsDeleted(form.getData(), true);
              // Not needed anymore: vacationService.deleteUsedVacationDaysFromLastYear(form.getData());
              break;
            default:
              // nothing to do
          }
        }
      }
    } catch (final Exception e) {
      log.error("There is a exception in afterSaveOrUpdate: " + e.getMessage(), e);
      error(I18nHelper.getLocalizedMessage("vacation.error.sendmail"));
    }
    return null;
  }

  @Override
  public WebPage afterDelete()
  {
    try {
      if (VacationStatus.APPROVED.equals(form.getData().getStatus())) {
        vacationCalendarService.markTeamEventsOfVacationAsDeleted(form.getData(), true);
        // Not needed anymore: vacationService.deleteUsedVacationDaysFromLastYear(form.getData());
        vacationMailService.sendMailToVacationInvolved(form.getData(), false, true);
      }
    } catch (final Exception e) {
      log.error("There is a exception in afterDelete: " + e.getMessage(), e);
      error(I18nHelper.getLocalizedMessage("vacation.error.sendmail"));
    }
    return null;
  }

  @Override
  public WebPage afterUndelete()
  {
    try {
      vacationCalendarService.undeleteTeamEventsOfVacation(form.getData());
      if (VacationStatus.APPROVED.equals(form.getData().getStatus())) {
        vacationCalendarService.markAsUnDeleteEventsForVacationCalendars(form.getData());
        // Not needed anymore: vacationService.updateUsedVacationDaysFromLastYear(form.getData());
        vacationMailService.sendMailToEmployeeAndHR(form.getData(), true);
        vacationCalendarService.createEventsForVacationCalendars(form.getData());
      } else {
        vacationMailService.sendMailToVacationInvolved(form.getData(), false, false);
      }
    } catch (final Exception e) {
      log.error("There is a exception in afterUndelete: " + e.getMessage(), e);
      error(I18nHelper.getLocalizedMessage("vacation.error.sendmail"));
    }
    return null;
  }

}
