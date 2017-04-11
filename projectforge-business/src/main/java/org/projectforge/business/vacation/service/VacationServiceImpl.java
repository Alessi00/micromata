package org.projectforge.business.vacation.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.projectforge.business.configuration.ConfigurationService;
import org.projectforge.business.fibu.EmployeeDO;
import org.projectforge.business.fibu.EmployeeDao;
import org.projectforge.business.fibu.api.EmployeeService;
import org.projectforge.business.teamcal.admin.model.TeamCalDO;
import org.projectforge.business.teamcal.event.TeamEventDao;
import org.projectforge.business.teamcal.event.model.TeamEventDO;
import org.projectforge.business.user.I18nHelper;
import org.projectforge.business.vacation.model.VacationAttrProperty;
import org.projectforge.business.vacation.model.VacationCalendarDO;
import org.projectforge.business.vacation.model.VacationDO;
import org.projectforge.business.vacation.model.VacationStatus;
import org.projectforge.business.vacation.repository.VacationDao;
import org.projectforge.framework.access.AccessException;
import org.projectforge.framework.persistence.api.BaseSearchFilter;
import org.projectforge.framework.persistence.history.DisplayHistoryEntry;
import org.projectforge.framework.persistence.jpa.impl.CorePersistenceServiceImpl;
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.framework.time.DateTimeFormatter;
import org.projectforge.framework.time.DayHolder;
import org.projectforge.mail.Mail;
import org.projectforge.mail.SendMail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Standard implementation of the vacation service interface.
 *
 * @author Florian Blumenstein
 */
@Service
public class VacationServiceImpl extends CorePersistenceServiceImpl<Integer, VacationDO>
    implements VacationService
{
  private static final Logger log = Logger.getLogger(VacationServiceImpl.class);

  @Autowired
  private VacationDao vacationDao;

  @Autowired
  private SendMail sendMailService;

  @Autowired
  private ConfigurationService configService;

  @Autowired
  private EmployeeDao employeeDao;

  @Autowired
  private EmployeeService employeeService;

  @Autowired
  private TeamEventDao teamEventDao;

  private static final String vacationEditPagePath = "/wa/wicket/bookmarkable/org.projectforge.web.vacation.VacationEditPage";

  private static final BigDecimal HALF_DAY = new BigDecimal(0.5);

  private static final DateTimeFormatter dateFormatter = DateTimeFormatter.instance();

  @Override
  public BigDecimal getApprovedAndPlanedVacationdaysForYear(final EmployeeDO employee, final int year)
  {
    final BigDecimal approved = getApprovedVacationdaysForYear(employee, year);
    final BigDecimal planned = getPlannedVacationdaysForYear(employee, year);
    return approved.add(planned);
  }

  @Override
  public void sendMailToVacationInvolved(final VacationDO vacationData, final boolean isNew, final boolean isDeleted)
  {
    final String urlOfVacationEditPage = configService.getDomain() + vacationEditPagePath + "?id=" + vacationData.getId();
    final String employeeFullName = vacationData.getEmployee().getUser().getFullname();
    final String managerFirstName = vacationData.getManager().getUser().getFirstname();

    final String periodI18nKey = vacationData.getHalfDay() ? "vacation.mail.period.halfday" : "vacation.mail.period.fromto";
    final String vacationStartDate = dateFormatter.getFormattedDate(vacationData.getStartDate());
    final String vacationEndDate = dateFormatter.getFormattedDate(vacationData.getEndDate());
    final String periodText = I18nHelper.getLocalizedMessage(periodI18nKey, vacationStartDate, vacationEndDate);

    final String i18nSubject;
    final String i18nPMContent;

    if (isNew == true && isDeleted == false) {
      i18nSubject = I18nHelper.getLocalizedMessage("vacation.mail.subject", employeeFullName);
      i18nPMContent = I18nHelper
          .getLocalizedMessage("vacation.mail.pm.application", managerFirstName, employeeFullName, periodText, urlOfVacationEditPage);
    } else if (isNew == false && isDeleted == false) {
      i18nSubject = I18nHelper.getLocalizedMessage("vacation.mail.subject.edit", employeeFullName);
      i18nPMContent = I18nHelper
          .getLocalizedMessage("vacation.mail.pm.application.edit", managerFirstName, employeeFullName, periodText, urlOfVacationEditPage);
    } else {
      // isDeleted
      i18nSubject = I18nHelper.getLocalizedMessage("vacation.mail.subject.deleted", employeeFullName);
      i18nPMContent = I18nHelper
          .getLocalizedMessage("vacation.mail.application.deleted", managerFirstName, employeeFullName, periodText, urlOfVacationEditPage);
    }

    // Send mail to manager and employee
    sendMail(i18nSubject, i18nPMContent,
        vacationData.getManager().getUser(),
        vacationData.getEmployee().getUser()
    );

    // Send mail to substitutions and employee
    for (final EmployeeDO substitution : vacationData.getSubstitutions()) {
      final PFUserDO substitutionUser = substitution.getUser();
      final String substitutionFirstName = substitutionUser.getFirstname();
      final String i18nSubContent;

      if (isNew == true && isDeleted == false) {
        i18nSubContent = I18nHelper
            .getLocalizedMessage("vacation.mail.sub.application", substitutionFirstName, employeeFullName, periodText, urlOfVacationEditPage);
      } else if (isNew == false && isDeleted == false) {
        i18nSubContent = I18nHelper
            .getLocalizedMessage("vacation.mail.sub.application.edit", substitutionFirstName, employeeFullName, periodText, urlOfVacationEditPage);
      } else {
        // isDeleted
        i18nSubContent = I18nHelper
            .getLocalizedMessage("vacation.mail.application.deleted", substitutionFirstName, employeeFullName, periodText, urlOfVacationEditPage);
      }

      sendMail(i18nSubject, i18nSubContent,
          substitutionUser,
          vacationData.getEmployee().getUser()
      );
    }
  }

  @Override
  public void sendMailToEmployeeAndHR(final VacationDO vacationData, final boolean approved)
  {
    final String urlOfVacationEditPage = configService.getDomain() + vacationEditPagePath + "?id=" + vacationData.getId();
    final String employeeFullName = vacationData.getEmployee().getUser().getFullname();
    final String managerFullName = vacationData.getManager().getUser().getFullname();
    final String substitutionFullNames = vacationData.getSubstitutions().stream()
        .map(EmployeeDO::getUser)
        .map(PFUserDO::getFullname)
        .collect(Collectors.joining(", "));

    final String periodI18nKey = vacationData.getHalfDay() ? "vacation.mail.period.halfday" : "vacation.mail.period.fromto";
    final String vacationStartDate = dateFormatter.getFormattedDate(vacationData.getStartDate());
    final String vacationEndDate = dateFormatter.getFormattedDate(vacationData.getEndDate());
    final String periodText = I18nHelper.getLocalizedMessage(periodI18nKey, vacationStartDate, vacationEndDate);

    if (approved && configService.getHREmailadress() != null) {
      //Send mail to HR (employee in copy)
      final String subject = I18nHelper.getLocalizedMessage("vacation.mail.subject", employeeFullName);
      final String content = I18nHelper
          .getLocalizedMessage("vacation.mail.hr.approved", employeeFullName, periodText, substitutionFullNames, managerFullName, urlOfVacationEditPage);

      sendMail(subject, content,
          configService.getHREmailadress(), "HR-MANAGEMENT",
          vacationData.getManager().getUser(),
          vacationData.getEmployee().getUser()
      );
    }

    // Send mail to substitutions and employee
    final String subject = I18nHelper.getLocalizedMessage("vacation.mail.subject.edit", employeeFullName);
    final String i18nKey = approved ? "vacation.mail.employee.approved" : "vacation.mail.employee.declined";
    final String content = I18nHelper.getLocalizedMessage(i18nKey, employeeFullName, periodText, substitutionFullNames, urlOfVacationEditPage);
    final PFUserDO[] recipients = Stream.concat(vacationData.getSubstitutions().stream(), Stream.of(vacationData.getEmployee()))
        .map(EmployeeDO::getUser)
        .toArray(PFUserDO[]::new);
    sendMail(subject, content, recipients);
  }

  private boolean sendMail(final String subject, final String content, final PFUserDO... recipients)
  {
    return sendMail(subject, content, null, null, recipients);
  }

  private boolean sendMail(final String subject, final String content, final String recipientMailAddress, final String recipientRealName,
      final PFUserDO... additionalRecipients)
  {
    final Mail mail = new Mail();
    mail.setContentType(Mail.CONTENTTYPE_HTML);
    mail.setSubject(subject);
    mail.setContent(content);
    if (StringUtils.isNotBlank(recipientMailAddress) && StringUtils.isNotBlank(recipientRealName)) {
      mail.setTo(recipientMailAddress, recipientRealName);
    }
    Arrays.stream(additionalRecipients).forEach(mail::setTo);
    return sendMailService.send(mail, null, null);
  }

  @Override
  public Calendar getEndDateVacationFromLastYear()
  {
    return configService.getEndDateVacationFromLastYear();
  }

  @Override
  public BigDecimal updateUsedVacationDaysFromLastYear(final VacationDO vacationData)
  {
    if (vacationData == null || vacationData.getEmployee() == null || vacationData.getStartDate() == null || vacationData.getEndDate() == null) {
      return BigDecimal.ZERO;
    }
    final Calendar now = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    final Calendar startDate = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    final Calendar endDateVacationFromLastYear = getEndDateVacationFromLastYear();
    if (vacationData.getIsSpecial() == true) {
      if (vacationData.getId() != null) {
        final VacationDO vacation = vacationDao.getById(vacationData.getId());
        if (vacation.getIsSpecial() == false) {
          return deleteUsedVacationDaysFromLastYear(vacation);
        }
      }
      return BigDecimal.ZERO;
    }
    startDate.setTime(vacationData.getStartDate());
    if (startDate.get(Calendar.YEAR) > now.get(Calendar.YEAR) && vacationData.getStartDate().before(endDateVacationFromLastYear.getTime()) == false) {
      return BigDecimal.ZERO;
    }

    final Date endDate = vacationData.getEndDate().before(endDateVacationFromLastYear.getTime())
        ? vacationData.getEndDate()
        : endDateVacationFromLastYear.getTime();

    final BigDecimal neededDaysForVacationFromLastYear = getVacationDays(vacationData.getStartDate(), endDate, vacationData.getHalfDay());

    final EmployeeDO employee = vacationData.getEmployee();
    final BigDecimal actualUsedDaysOfLastYear = getVacationFromPreviousYearUsed(employee);
    final BigDecimal vacationFromPreviousYear = getVacationFromPreviousYear(employee);

    final BigDecimal freeDaysFromLastYear = vacationFromPreviousYear.subtract(actualUsedDaysOfLastYear);
    final BigDecimal remainValue = freeDaysFromLastYear.subtract(neededDaysForVacationFromLastYear).compareTo(BigDecimal.ZERO) < 0 ?
        BigDecimal.ZERO :
        freeDaysFromLastYear.subtract(neededDaysForVacationFromLastYear);
    final BigDecimal newValue = vacationFromPreviousYear.subtract(remainValue);
    employee.putAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), newValue);
    employeeDao.internalUpdate(employee);
    return newValue;
  }

  @Override
  public void updateUsedNewVacationDaysFromLastYear(final EmployeeDO employee, final int year)
  {
    final BigDecimal availableVacationdays = getAvailableVacationdaysForYear(employee, year, false);
    employee.putAttribute(VacationAttrProperty.PREVIOUSYEARLEAVE.getPropertyName(), availableVacationdays);
    employee.putAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), BigDecimal.ZERO);
    employeeDao.internalSave(employee);
  }

  @Override
  public BigDecimal deleteUsedVacationDaysFromLastYear(final VacationDO vacationData)
  {
    if (vacationData == null || vacationData.getIsSpecial() == true || vacationData.getEmployee() == null || vacationData.getStartDate() == null
        || vacationData.getEndDate() == null) {
      return BigDecimal.ZERO;
    }
    final EmployeeDO employee = vacationData.getEmployee();
    final BigDecimal actualUsedDaysOfLastYear = getVacationFromPreviousYearUsed(employee);
    final BigDecimal vacationFromPreviousYear = getVacationFromPreviousYear(employee);
    final Calendar startDateCalender = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    startDateCalender.setTime(vacationData.getStartDate());
    final Calendar firstOfJanOfStartYearCalender = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    firstOfJanOfStartYearCalender.set(startDateCalender.get(Calendar.YEAR), Calendar.JANUARY, 1);
    final Calendar endDateCalender = configService.getEndDateVacationFromLastYear();
    final List<VacationDO> vacationList = getVacationForDate(vacationData.getEmployee(), startDateCalender.getTime(), endDateCalender.getTime(), false);

    // sum vacation days until "configured end date vacation from last year"
    final BigDecimal dayCount = vacationList.stream()
        .map(vacation -> {
          final Date endDate = vacation.getEndDate().before(endDateCalender.getTime())
              ? vacation.getEndDate()
              : endDateCalender.getTime();
          return getVacationDays(vacation.getStartDate(), endDate, vacation.getHalfDay());
        })
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal newDays = BigDecimal.ZERO;

    if (dayCount.compareTo(vacationFromPreviousYear) < 0) // dayCount < vacationFromPreviousYear
    {
      if (vacationData.getEndDate().compareTo(endDateCalender.getTime()) < 0) {
        newDays = actualUsedDaysOfLastYear.subtract(getVacationDays(vacationData));
      } else {
        newDays = actualUsedDaysOfLastYear.subtract(getVacationDays(vacationData.getStartDate(), endDateCalender.getTime(), vacationData.getHalfDay()));
      }
      if (newDays.compareTo(BigDecimal.ZERO) < 0) {
        newDays = BigDecimal.ZERO;
      }
    }
    employee.putAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), newDays);
    employeeDao.internalUpdate(employee);
    return newDays;
  }

  @Override
  public boolean couldUserUseVacationService(final PFUserDO user, final boolean throwException)
  {
    boolean result = true;
    if (user == null || user.getId() == null) {
      return false;
    }
    final EmployeeDO employee = employeeService.getEmployeeByUserId(user.getId());
    if (employee == null) {
      if (throwException) {
        throw new AccessException("access.exception.noEmployeeToUser");
      }
      result = false;
    } else if (employee.getUrlaubstage() == null) {
      if (throwException) {
        throw new AccessException("access.exception.employeeHasNoVacationDays");
      }
      result = false;
    }
    return result;
  }

  @Override
  public BigDecimal getApprovedVacationdaysForYear(final EmployeeDO employee, final int year)
  {
    return getVacationDaysForYearByStatus(employee, year, VacationStatus.APPROVED);
  }

  @Override
  public BigDecimal getPlannedVacationdaysForYear(final EmployeeDO employee, final int year)
  {
    return getVacationDaysForYearByStatus(employee, year, VacationStatus.IN_PROGRESS);
  }

  private BigDecimal getVacationDaysForYearByStatus(final EmployeeDO employee, final int year, final VacationStatus status)
  {
    return getActiveVacationForYear(employee, year, false)
        .stream()
        .filter(vac -> vac.getStatus().equals(status))
        .map(this::getVacationDays)
        .reduce(BigDecimal.ZERO, BigDecimal::add); // sum
  }

  @Override
  public BigDecimal getAvailableVacationdaysForYear(final PFUserDO user, final int year, final boolean checkLastYear)
  {
    if (user == null) {
      return BigDecimal.ZERO;
    }
    final EmployeeDO employee = employeeService.getEmployeeByUserId(user.getPk());
    if (employee == null) {
      return BigDecimal.ZERO;
    }
    return getAvailableVacationdaysForYear(employee, year, checkLastYear);
  }

  @Override
  public BigDecimal getAvailableVacationdaysForYear(final EmployeeDO employee, final int year, final boolean checkLastYear)
  {
    if (employee == null) {
      return BigDecimal.ZERO;
    }
    final BigDecimal vacationDays = new BigDecimal(employee.getUrlaubstage());

    final Calendar now = Calendar.getInstance(ThreadLocalUserContext.getTimeZone());
    final Calendar endDateVacationFromLastYear = configService.getEndDateVacationFromLastYear();
    final BigDecimal vacationFromPreviousYear;
    if (year != now.get(Calendar.YEAR)) {
      vacationFromPreviousYear = BigDecimal.ZERO;
    } else if (checkLastYear == false || now.after(endDateVacationFromLastYear)) {
      vacationFromPreviousYear = getVacationFromPreviousYearUsed(employee);
    } else {
      // before or same day as endDateVacationFromLastYear
      vacationFromPreviousYear = getVacationFromPreviousYear(employee);
    }

    final BigDecimal approvedVacation = getApprovedVacationdaysForYear(employee, year);
    final BigDecimal planedVacation = getPlannedVacationdaysForYear(employee, year);

    return vacationDays
        .add(vacationFromPreviousYear)
        .subtract(approvedVacation)
        .subtract(planedVacation);
  }

  @Override
  public BigDecimal getPlandVacationDaysForYearAtDate(final EmployeeDO employee, final Date queryDate)
  {
    final Calendar endDate = Calendar.getInstance();
    endDate.setTime(queryDate);
    endDate.set(Calendar.MONTH, Calendar.DECEMBER);
    endDate.set(Calendar.DAY_OF_MONTH, 31);
    endDate.set(Calendar.HOUR_OF_DAY, 0);
    endDate.set(Calendar.MINUTE, 0);
    endDate.set(Calendar.SECOND, 0);
    endDate.set(Calendar.MILLISECOND, 0);

    return getApprovedVacationDaysForYearUntilDate(employee, queryDate, endDate.getTime());
  }

  @Override
  public BigDecimal getAvailableVacationDaysForYearAtDate(final EmployeeDO employee, final Date queryDate)
  {
    final Calendar startDate = Calendar.getInstance();
    startDate.setTime(queryDate);
    startDate.set(Calendar.MONTH, Calendar.JANUARY);
    startDate.set(Calendar.DAY_OF_MONTH, 1);
    startDate.set(Calendar.HOUR_OF_DAY, 0);
    startDate.set(Calendar.MINUTE, 0);
    startDate.set(Calendar.SECOND, 0);
    startDate.set(Calendar.MILLISECOND, 0);

    final BigDecimal vacationDays = new BigDecimal(employee.getUrlaubstage());
    final BigDecimal vacationDaysPrevYear = getVacationDaysFromPrevYearDependingOnDate(employee, queryDate);
    final BigDecimal approvedVacationDays = getApprovedVacationDaysForYearUntilDate(employee, startDate.getTime(), queryDate);

    return vacationDays
        .add(vacationDaysPrevYear)
        .subtract(approvedVacationDays);
  }

  private BigDecimal getVacationDaysFromPrevYearDependingOnDate(final EmployeeDO employee, final Date queryDate)
  {
    final Calendar endDateVacationFromLastYear = configService.getEndDateVacationFromLastYear();
    final Calendar queryDateCal = Calendar.getInstance();
    queryDateCal.setTime(queryDate);

    if (queryDateCal.get(Calendar.YEAR) != endDateVacationFromLastYear.get(Calendar.YEAR)) {
      // year of query is different form the year of endDateVacationFromLastYear
      // therefore the vacation from previous year values are from the wrong year
      // therefore we don't know the right values and return zero
      return BigDecimal.ZERO;
    }

    return queryDateCal.after(endDateVacationFromLastYear)
        ? getVacationFromPreviousYearUsed(employee)
        : getVacationFromPreviousYear(employee);
  }

  private BigDecimal getApprovedVacationDaysForYearUntilDate(final EmployeeDO employee, final Date from, final Date until)
  {

    final List<VacationDO> vacations = getVacationForDate(employee, from, until, false);

    return vacations.stream()
        .filter(v -> v.getStatus().equals(VacationStatus.APPROVED))
        .map(v -> getVacationDays(v, until))
        .reduce(BigDecimal.ZERO, BigDecimal::add); // sum
  }

  private BigDecimal getVacationFromPreviousYearUsed(final EmployeeDO employee)
  {
    final BigDecimal prevYearLeaveUsed = employee.getAttribute(VacationAttrProperty.PREVIOUSYEARLEAVEUSED.getPropertyName(), BigDecimal.class);
    return prevYearLeaveUsed != null ? prevYearLeaveUsed : BigDecimal.ZERO;
  }

  private BigDecimal getVacationFromPreviousYear(final EmployeeDO employee)
  {
    final BigDecimal prevYearLeave = employee.getAttribute(VacationAttrProperty.PREVIOUSYEARLEAVE.getPropertyName(), BigDecimal.class);
    return prevYearLeave != null ? prevYearLeave : BigDecimal.ZERO;
  }

  @Override
  public List<VacationDO> getActiveVacationForYear(final EmployeeDO employee, final int year, final boolean withSpecial)
  {
    return vacationDao.getActiveVacationForYear(employee, year, withSpecial);
  }

  @Override
  public List<VacationDO> getAllActiveVacation(final EmployeeDO employee, final boolean withSpecial)
  {
    return vacationDao.getAllActiveVacation(employee, withSpecial);
  }

  @Override
  public List<VacationDO> getList(final BaseSearchFilter filter)
  {
    return vacationDao.getList(filter);
  }

  @Override
  public List<VacationDO> getVacation(final List<Serializable> idList)
  {
    return vacationDao.internalLoad(idList);
  }

  @Override
  public List<VacationDO> getVacationForDate(final EmployeeDO employee, final Date startDate, final Date endDate, final boolean withSpecial)
  {
    return vacationDao.getVacationForPeriod(employee, startDate, endDate, withSpecial);
  }

  @Override
  public BigDecimal getOpenLeaveApplicationsForUser(final PFUserDO user)
  {
    final EmployeeDO employee = employeeService.getEmployeeByUserId(user.getId());
    if (employee == null) {
      return BigDecimal.ZERO;
    }
    return vacationDao.getOpenLeaveApplicationsForEmployee(employee);
  }

  @Override
  public BigDecimal getSpecialVacationCount(final EmployeeDO employee, final int year, final VacationStatus status)
  {
    return vacationDao
        .getSpecialVacation(employee, year, status)
        .stream()
        .map(this::getVacationDays)
        .reduce(BigDecimal.ZERO, BigDecimal::add); // sum
  }

  @Override
  public List<TeamCalDO> getCalendarsForVacation(final VacationDO vacation)
  {
    return vacationDao.getCalendarsForVacation(vacation);
  }

  public BigDecimal getVacationDays(final VacationDO vacationData)
  {
    return getVacationDays(vacationData, null);
  }

  private BigDecimal getVacationDays(final VacationDO vacationData, final Date until)
  {
    final Date startDate = vacationData.getStartDate();
    final Date endDate = vacationData.getEndDate();

    if (startDate != null && endDate != null) {
      final Date endDateToUse = (until != null && until.before(endDate)) ? until : endDate;
      return getVacationDays(startDate, endDateToUse, vacationData.getHalfDay());
    }
    return null;
  }

  @Override
  public BigDecimal getVacationDays(final Date from, final Date to, final Boolean isHalfDayVacation)
  {
    final BigDecimal numberOfWorkingDays = DayHolder.getNumberOfWorkingDays(from, to);

    // don't return HALF_DAY if there is no working day
    return numberOfWorkingDays.equals(BigDecimal.ZERO) == false && Boolean.TRUE.equals(isHalfDayVacation) // null evaluates to false
        ? HALF_DAY
        : numberOfWorkingDays;
  }

  @Override
  public boolean hasInsertAccess(final PFUserDO user)
  {
    return true;
  }

  @Override
  public boolean hasLoggedInUserInsertAccess()
  {
    return vacationDao.hasLoggedInUserInsertAccess();
  }

  @Override
  public boolean hasLoggedInUserInsertAccess(final VacationDO obj, final boolean throwException)
  {
    return vacationDao.hasLoggedInUserInsertAccess(obj, throwException);
  }

  @Override
  public boolean hasLoggedInUserUpdateAccess(final VacationDO obj, final VacationDO dbObj, final boolean throwException)
  {
    return vacationDao.hasLoggedInUserUpdateAccess(obj, dbObj, throwException);
  }

  @Override
  public boolean hasLoggedInUserDeleteAccess(final VacationDO obj, final VacationDO dbObj, final boolean throwException)
  {
    return vacationDao.hasLoggedInUserDeleteAccess(obj, dbObj, throwException);
  }

  @Override
  public boolean hasDeleteAccess(final PFUserDO user, final VacationDO obj, final VacationDO dbObj, final boolean throwException)
  {
    return vacationDao.hasDeleteAccess(user, obj, dbObj, throwException);
  }

  @Override
  public List<String> getAutocompletion(final String property, final String searchString)
  {
    return vacationDao.getAutocompletion(property, searchString);
  }

  @Override
  public List<DisplayHistoryEntry> getDisplayHistoryEntries(final VacationDO obj)
  {
    return vacationDao.getDisplayHistoryEntries(obj);
  }

  @Override
  public void rebuildDatabaseIndex4NewestEntries()
  {
    vacationDao.rebuildDatabaseIndex4NewestEntries();
  }

  @Override
  public void rebuildDatabaseIndex()
  {
    vacationDao.rebuildDatabaseIndex();
  }

  @Override
  public void saveOrUpdateVacationCalendars(final VacationDO vacation, final Collection<TeamCalDO> calendars)
  {
    if (calendars != null) {
      for (final TeamCalDO teamCalDO : calendars) {
        vacationDao.saveVacationCalendar(getOrCreateVacationCalendarDO(vacation, teamCalDO));
      }
    }
    final List<VacationCalendarDO> vacationCalendars = vacationDao.getVacationCalendarDOs(vacation);
    for (VacationCalendarDO vacationCalendar : vacationCalendars) {
      if (calendars.contains(vacationCalendar.getCalendar()) == false) {
        vacationDao.deleteVacationCalendarDO(vacationCalendar);
      } else {
        vacationDao.unDeleteVacationCalendarDO(vacationCalendar);
      }
    }
  }

  @Override
  public void markAsDeleteEventsForVacationCalendars(final VacationDO vacation)
  {
    final List<VacationCalendarDO> vacationCalendarDOs = vacationDao.getVacationCalendarDOs(vacation);
    for (final VacationCalendarDO vacationCalendarDO : vacationCalendarDOs) {
      if (vacationCalendarDO.isDeleted() == false) {
        if (vacationCalendarDO.getEvent() != null) {
          vacationDao.deleteVacationCalendarDO(vacationCalendarDO);
          teamEventDao.internalMarkAsDeleted(teamEventDao.getById(vacationCalendarDO.getEvent().getId()));
        }
      }
    }
  }

  @Override
  public void markAsUnDeleteVacationCalendars(final VacationDO vacation)
  {
    final List<VacationCalendarDO> vacationCalendarDOs = vacationDao.getVacationCalendarDOs(vacation);
    for (final VacationCalendarDO vacationCalendarDO : vacationCalendarDOs) {
      if (vacationCalendarDO.isDeleted()) {
        vacationDao.unDeleteVacationCalendarDO(vacationCalendarDO);
      }
    }
  }

  @Override
  public void markAsUnDeleteEventsForVacationCalendars(final VacationDO vacation)
  {
    List<VacationCalendarDO> vacationCalendarDOs = vacationDao.getVacationCalendarDOs(vacation);
    for (VacationCalendarDO vacationCalendarDO : vacationCalendarDOs) {
      if (vacationCalendarDO.getEvent() != null) {
        teamEventDao.internalUndelete(teamEventDao.getById(vacationCalendarDO.getEvent().getId()));
      }
    }
  }

  @Override
  public void createEventsForVacationCalendars(final VacationDO vacation)
  {
    final List<VacationCalendarDO> vacationCalendarDOs = vacationDao.getVacationCalendarDOs(vacation);
    for (VacationCalendarDO vacationCalendarDO : vacationCalendarDOs) {
      if (vacationCalendarDO.isDeleted() == false) {
        vacationCalendarDO.setEvent(getOrCreateTeamEventDO(vacationCalendarDO));
        vacationDao.saveVacationCalendar(vacationCalendarDO);
      }
    }
  }

  public VacationCalendarDO getOrCreateVacationCalendarDO(final VacationDO vacation, final TeamCalDO teamCalDO)
  {
    final List<VacationCalendarDO> vacationCalendarDOs = vacationDao.getVacationCalendarDOs(vacation);
    for (final VacationCalendarDO vacationCalendarDO : vacationCalendarDOs) {
      if (vacationCalendarDO.getCalendar().equals(teamCalDO)) {
        vacationCalendarDO.setDeleted(false);
        return vacationCalendarDO;
      }
    }
    final VacationCalendarDO vacationCalendarDO = new VacationCalendarDO();
    vacationCalendarDO.setCalendar(teamCalDO);
    vacationCalendarDO.setVacation(vacation);
    return vacationCalendarDO;
  }

  public TeamEventDO getOrCreateTeamEventDO(final VacationCalendarDO vacationCalendarDO)
  {
    if (vacationCalendarDO.getEvent() != null) {
      final TeamEventDO byId = teamEventDao.getById(vacationCalendarDO.getEvent().getId());
      teamEventDao.internalUndelete(byId);
      return byId;
    } else {
      final TeamEventDO teamEventDO = new TeamEventDO();
      teamEventDO.setAllDay(true);
      final Timestamp startTimestamp = new Timestamp(vacationCalendarDO.getVacation().getStartDate().getTime());
      final Timestamp endTimestamp = new Timestamp(vacationCalendarDO.getVacation().getEndDate().getTime());
      teamEventDO.setStartDate(startTimestamp);
      teamEventDO.setEndDate(endTimestamp);
      teamEventDO.setSubject(vacationCalendarDO.getVacation().getEmployee().getUser().getFullname());
      teamEventDO.setCalendar(vacationCalendarDO.getCalendar());
      teamEventDao.internalSave(teamEventDO);
      return teamEventDO;
    }
  }
}