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

package org.projectforge.fibu;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.projectforge.business.fibu.EmployeeDO;
import org.projectforge.business.fibu.api.EmployeeService;
import org.projectforge.business.user.service.UserService;
import org.projectforge.business.vacation.service.VacationService;
import org.projectforge.business.vacation.service.VacationServiceImpl;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.framework.time.PFDateTime;
import org.projectforge.test.AbstractTestBase;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.NoResultException;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class EmployeeServiceTest extends AbstractTestBase
{
  @Autowired
  private EmployeeService employeeService;

  @Autowired
  private UserService userService;

  @InjectMocks
  private VacationService vacationService = new VacationServiceImpl();

  @Test
  public void testInsertDelete()
  {
    logon(AbstractTestBase.TEST_FULL_ACCESS_USER);
    PFUserDO pfUserDO = getUser(TEST_FINANCE_USER);
    EmployeeDO employeeDO = new EmployeeDO();
    employeeDO.setAccountHolder("Horst Mustermann");
    employeeDO.setAbteilung("Finance");
    employeeDO.setUser(pfUserDO);
    Integer id = employeeService.save(employeeDO);
    assertTrue(id != null && id > 0);
    employeeService.delete(employeeDO);
    EmployeeDO employeeDO1 = null;
    List<Exception> exceptionList = new ArrayList<>();
    try {
      employeeDO1 = employeeService.selectByPkDetached(id);
    } catch (NoResultException e) {
      exceptionList.add(e);
    }

    assertEquals(exceptionList.size(), 1);
    assertEquals(employeeDO1, null);
  }

  @Test
  public void testUpdateAttribute()
  {
    logon(AbstractTestBase.TEST_FULL_ACCESS_USER);
    PFUserDO pfUserDO = getUser(TEST_PROJECT_ASSISTANT_USER);
    EmployeeDO employeeDO = new EmployeeDO();
    employeeDO.setAccountHolder("Vorname Name");
    String abteilung = "Test";
    employeeDO.setAbteilung(abteilung);
    employeeDO.setUser(pfUserDO);
    employeeService.save(employeeDO);
    String expectedNewAccountHolder = "Firstname Lastname";
    employeeService.updateAttribute(pfUserDO.getId(), expectedNewAccountHolder, "accountHolder");
    EmployeeDO employeeByUserId = employeeService.getEmployeeByUserId(pfUserDO.getId());
    assertEquals(employeeByUserId.getAbteilung(), abteilung);
    assertEquals(employeeByUserId.getAccountHolder(), expectedNewAccountHolder);
  }

  @Test
  public void isEmployeeActiveWithoutAustrittsdatumTest()
  {
    EmployeeDO employee = new EmployeeDO();
    boolean result = employeeService.isEmployeeActive(employee);
    assertTrue(result);
  }

  @Test
  public void isEmployeeActiveWithAustrittsdatumTest()
  {
    EmployeeDO employee = new EmployeeDO();
    PFDateTime dt = PFDateTime.now().plusMonths(1);
    employee.setAustrittsDatum(dt.getUtilDate());
    boolean result = employeeService.isEmployeeActive(employee);
    assertTrue(result);
  }

  @Test
  public void isEmployeeActiveWithAustrittsdatumBeforeTest()
  {
    EmployeeDO employee = new EmployeeDO();
    PFDateTime dt = PFDateTime.now().minusMonths(1);
    employee.setAustrittsDatum(dt.getUtilDate());
    boolean result = employeeService.isEmployeeActive(employee);
    assertFalse(result);
  }

  @Test
  public void isEmployeeActiveWithAustrittsdatumNowTest()
  {
    EmployeeDO employee = new EmployeeDO();
    PFDateTime dt = PFDateTime.now();
    employee.setAustrittsDatum(dt.getUtilDate());
    boolean result = employeeService.isEmployeeActive(employee);
    assertFalse(result);
  }

  @Test
  @Disabled
  public void testGetStudentVacationCountPerDay()
  {
    MockitoAnnotations.initMocks(this);
    when(vacationService.getVacationCount(2017, Month.MAY.getValue(), 2017, Month.OCTOBER.getValue(), new PFUserDO())).thenReturn("TestCase 1");
    when(vacationService.getVacationCount(2016, Month.JULY.getValue(), 2017, Month.OCTOBER.getValue(), new PFUserDO())).thenReturn("TestCase 2");
    when(vacationService.getVacationCount(2017, Month.JULY.getValue(), 2017, Month.OCTOBER.getValue(), new PFUserDO())).thenReturn("TestCase 3");

    PFDateTime testCase1 = PFDateTime.now().withYear(2017).withMonth(Month.OCTOBER.getValue());
    when(PFDateTime.now()).thenReturn(testCase1);
    Assertions.assertEquals("TestCase 1", employeeService.getStudentVacationCountPerDay(new EmployeeDO()));

    PFDateTime testCase2 = PFDateTime.now().withYear(2017).withMonth(Month.FEBRUARY.getValue());
    when(PFDateTime.now()).thenReturn(testCase2);

    PFDateTime testCase3 = PFDateTime.now().withYear(2017).withMonth(Month.AUGUST.getValue());
    when(PFDateTime.now()).thenReturn(testCase3);
    when(new EmployeeDO().getEintrittsDatum()).thenReturn(testCase3.getUtilDate());
  }

}
