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

package org.projectforge.business.timesheet;

import org.apache.poi.hssf.util.HSSFColor;
import org.projectforge.business.common.OutputType;
import org.projectforge.business.excel.*;
import org.projectforge.business.fibu.KundeDO;
import org.projectforge.business.fibu.ProjektDO;
import org.projectforge.business.fibu.kost.Kost2DO;
import org.projectforge.business.task.TaskNode;
import org.projectforge.business.task.TaskTree;
import org.projectforge.business.task.formatter.TaskFormatter;
import org.projectforge.business.user.UserGroupCache;
import org.projectforge.common.DateFormatType;
import org.projectforge.export.MyXlsContentProvider;
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.framework.time.DateFormats;
import org.projectforge.framework.time.DateTimeFormatter;
import org.projectforge.framework.time.PFDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * For excel export.
 *
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
@Service
public class TimesheetExport {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TimesheetExport.class);

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  private TaskTree taskTree;

  @Autowired
  private UserGroupCache userGroupCache;

  private class MyContentProvider extends MyXlsContentProvider {
    public MyContentProvider(final ExportWorkbook workbook) {
      super(workbook);
    }

    @Override
    public MyContentProvider updateRowStyle(final ExportRow row) {
      for (final ExportCell cell : row.getCells()) {
        final CellFormat format = cell.ensureAndGetCellFormat();
        format.setFillForegroundColor(HSSFColor.HSSFColorPredefined.WHITE.getIndex());
        switch (row.getRowNum()) {
          case 0:
            format.setFont(FONT_HEADER);
            break;
          case 1:
            format.setFont(FONT_NORMAL_BOLD);
            // alignment = CellStyle.ALIGN_CENTER;
            break;
          default:
            format.setFont(FONT_NORMAL);
            if (row.getRowNum() % 2 == 0) {
              format.setFillForegroundColor(HSSFColor.HSSFColorPredefined.GREY_25_PERCENT.getIndex());
            }
            break;
        }
      }
      return this;
    }

    @Override
    public ContentProvider newInstance() {
      return new MyContentProvider(this.workbook);
    }
  }

  private enum Col {
    USER, KUNDE, PROJEKT, KOST2, WEEK_OF_YEAR, DAY_OF_WEEK, START_TIME, STOP_TIME, DURATION, HOURS, LOCATION, REFERENCE, TASK_TITLE, TASK_REFERENCE, SHORT_DESCRIPTION, DESCRIPTION, TASK_PATH, ID, CREATED, LAST_UPDATE
  }

  /**
   * Exports the filtered list as table with almost all fields.
   */
  public byte[] export(final List<TimesheetDO> list) {
    log.info("Exporting timesheet list.");
    final ExportWorkbook xls = new ExportWorkbook();
    final ContentProvider contentProvider = new MyContentProvider(xls);
    // create a default Date format and currency column
    xls.setContentProvider(contentProvider);

    final String sheetTitle = ThreadLocalUserContext.getLocalizedString("timesheet.timesheets");
    final ExportSheet sheet = xls.addSheet(sheetTitle);
    sheet.createFreezePane(8, 1);

    final ExportColumn[] cols = new ExportColumn[]{ //
        new I18nExportColumn(Col.USER, "timesheet.user", MyXlsContentProvider.LENGTH_USER),
        new I18nExportColumn(Col.KUNDE, "fibu.kunde", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(Col.PROJEKT, "fibu.projekt", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(Col.KOST2, "fibu.kost2", MyXlsContentProvider.LENGTH_KOSTENTRAEGER),
        new I18nExportColumn(Col.WEEK_OF_YEAR, "calendar.weekOfYearShortLabel", 4),
        new I18nExportColumn(Col.DAY_OF_WEEK, "calendar.dayOfWeekShortLabel", 4),
        new I18nExportColumn(Col.START_TIME, "timesheet.startTime", MyXlsContentProvider.LENGTH_DATETIME),
        new I18nExportColumn(Col.STOP_TIME, "timesheet.stopTime", MyXlsContentProvider.LENGTH_TIMESTAMP),
        new I18nExportColumn(Col.DURATION, "timesheet.duration", MyXlsContentProvider.LENGTH_DURATION),
        new I18nExportColumn(Col.HOURS, "hours", MyXlsContentProvider.LENGTH_DURATION),
        new I18nExportColumn(Col.LOCATION, "timesheet.location", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(Col.REFERENCE, "timesheet.reference", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(Col.TASK_TITLE, "task.title", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(Col.TASK_REFERENCE, "timesheet.taskReference", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(Col.SHORT_DESCRIPTION, "shortDescription", MyXlsContentProvider.LENGTH_STD),
        new I18nExportColumn(Col.DESCRIPTION, "timesheet.description", MyXlsContentProvider.LENGTH_EXTRA_LONG),
        new I18nExportColumn(Col.TASK_PATH, "task.path", MyXlsContentProvider.LENGTH_EXTRA_LONG),
        new I18nExportColumn(Col.ID, "id", MyXlsContentProvider.LENGTH_ID),
        new I18nExportColumn(Col.CREATED, "created", MyXlsContentProvider.LENGTH_TIMESTAMP),
        new I18nExportColumn(Col.LAST_UPDATE, "lastUpdate", MyXlsContentProvider.LENGTH_TIMESTAMP)};
    // column property names
    sheet.setColumns(cols);

    final ContentProvider sheetProvider = sheet.getContentProvider();
    sheetProvider.putFormat(Col.START_TIME, "yyyy-MM-dd HH:mm");
    sheetProvider.putFormat(Col.STOP_TIME, "HH:mm");
    sheetProvider.putFormat(Col.DURATION, "[h]:mm");
    sheetProvider.putFormat(Col.HOURS, "#,##0.00");
    sheetProvider.putFormat(Col.ID, "0");
    sheetProvider.putFormat(Col.CREATED, "yyyy-MM-dd HH:mm");
    sheetProvider.putFormat(Col.LAST_UPDATE, "yyyy-MM-dd HH:mm");

    final PropertyMapping mapping = new PropertyMapping();
    for (final TimesheetDO timesheet : list) {
      final TaskNode node = taskTree.getTaskNodeById(timesheet.getTaskId());
      final PFUserDO user = userGroupCache.getUser(timesheet.getUserId());
      mapping.add(Col.USER, user.getFullname());
      final Kost2DO kost2 = timesheet.getKost2();
      String kost2Name = null;
      String projektName = null;
      String kundeName = null;
      if (kost2 != null) {
        kost2Name = kost2.getDisplayName();
        final ProjektDO projekt = kost2.getProjekt();
        if (projekt != null) {
          projektName = projekt.getName();
          final KundeDO kunde = projekt.getKunde();
          if (kunde != null) {
            kundeName = kunde.getName();
          } else {
          }
        }
      }
      mapping.add(Col.KOST2, kost2Name);
      mapping.add(Col.PROJEKT, projektName);
      mapping.add(Col.KUNDE, kundeName);
      mapping.add(Col.TASK_TITLE, node.getTask().getTitle());
      mapping.add(Col.TASK_PATH, TaskFormatter.getTaskPath(timesheet.getTaskId(), null, true, OutputType.PLAIN));
      mapping.add(Col.WEEK_OF_YEAR, timesheet.getFormattedWeekOfYear());
      mapping.add(Col.DAY_OF_WEEK, dateTimeFormatter.getFormattedDate(timesheet.getStartTime(), DateFormats
          .getFormatString(DateFormatType.DAY_OF_WEEK_SHORT)));
      PFDateTime startTime = PFDateTime.from(timesheet.getStartTime()); // not null
      PFDateTime stopTime = PFDateTime.from(timesheet.getStopTime()); // not null
      mapping.add(Col.START_TIME, startTime);
      mapping.add(Col.STOP_TIME, stopTime);
      final BigDecimal seconds = new BigDecimal(timesheet.getDuration() / 1000); // Seconds
      final BigDecimal duration = seconds.divide(new BigDecimal(60 * 60 * 24), 8, RoundingMode.HALF_UP); // Fraction of day (24 hours)
      mapping.add(Col.DURATION, duration.doubleValue());
      final BigDecimal hours = seconds.divide(new BigDecimal(60 * 60), 2, RoundingMode.HALF_UP);
      mapping.add(Col.HOURS, hours.doubleValue());
      mapping.add(Col.LOCATION, timesheet.getLocation());
      mapping.add(Col.REFERENCE, timesheet.getReference());
      mapping.add(Col.TASK_REFERENCE, node.getReference());
      mapping.add(Col.SHORT_DESCRIPTION, timesheet.getShortDescription());
      mapping.add(Col.DESCRIPTION, timesheet.getDescription());
      mapping.add(Col.ID, timesheet.getId());
      mapping.add(Col.CREATED, timesheet.getCreated());
      mapping.add(Col.LAST_UPDATE, timesheet.getLastUpdate());
      sheet.addRow(mapping.getMapping(), 0);
    }
    sheet.setZoom(75); // 75%
    sheet.setAutoFilter();

    return xls.getAsByteArray();
  }

  public void setDateTimeFormatter(final DateTimeFormatter dateTimeFormatter) {
    this.dateTimeFormatter = dateTimeFormatter;
  }
}
