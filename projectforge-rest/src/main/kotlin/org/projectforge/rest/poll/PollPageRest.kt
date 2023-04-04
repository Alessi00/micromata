package org.projectforge.rest.poll

import org.projectforge.business.scripting.I18n
import org.projectforge.rest.config.Rest
import org.projectforge.rest.config.RestUtils
import org.projectforge.rest.core.AbstractDynamicPageRest
import org.projectforge.rest.core.RestResolver
import org.projectforge.rest.dto.FormLayoutData
import org.projectforge.rest.poll.Detail.View.PollDetailRest
import org.projectforge.rest.poll.Exel.ExcelExport
import org.projectforge.ui.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("${Rest.URL}/umfrage")
class PollPageRest : AbstractDynamicPageRest() {

    val months = arrayOf(
        I18n.getString("calendar.month.january"),
        I18n.getString("calendar.month.february"),
        I18n.getString("calendar.month.march"),
        I18n.getString("calendar.month.april"),
        I18n.getString("calendar.month.may"),
        I18n.getString("calendar.month.june"),
        I18n.getString("calendar.month.july"),
        I18n.getString("calendar.month.august"),
        I18n.getString("calendar.month.september"),
        I18n.getString("calendar.month.october"),
        I18n.getString("calendar.month.november"),
        I18n.getString("calendar.month.december")
    )

    @GetMapping("dynamic")
    fun getForm(request: HttpServletRequest): FormLayoutData {
        val layout = UILayout(I18n.getString("menu.birthdayList"))

        val values = ArrayList<UISelectValue<Int>>()
        months.forEachIndexed { index, month -> values.add(UISelectValue(index + 1, month)) }

        layout.add(UISelect("month", required = true, values = values, label = I18n.getString("calendar.month")))
        layout.addAction(
            UIButton.createDefaultButton(
                id = "download_button",
                title = I18n.getString("download"),
                responseAction = ResponseAction(
                    RestResolver.getRestUrl(
                        this::class.java,
                        "Export"
                    ), targetType = TargetType.POST
                )
            )
        )
        LayoutUtils.process(layout)
        val data = PollData()
        return FormLayoutData(data, layout, createServerData(request))
    }

    private val log: Logger = LoggerFactory.getLogger(PollDetailRest::class.java)

    @PostMapping("Export")
    fun export(request: HttpServletRequest) : ResponseEntity<Resource>? {
        val ihkExporter = ExcelExport()
        val bytes: ByteArray? = ihkExporter
            .getExcel()
        val filename = ("test.xlsx")

        if (bytes == null || bytes.size == 0) {
            log.error("Oups, xlsx has zero size. Filename: $filename")
            return null;
        }
        return RestUtils.downloadFile(filename, bytes)
    }
}