
package com.example.failurereport

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.content.Intent
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Report(
    val number: String,
    val failure: String,
    val time: String,
    val priority: String,
    val comment: String
)

class MainActivity : Activity() {

    private val failures = listOf(
        "142" to "Zipper",
        "145" to "Black Mark",
        "147" to "Chine Wrinkle",
        "151" to "Bad Trim",
        "153" to "Black Spot",
        "201" to "Bad Wash",
        "417" to "Deco Register Poor",
        "418" to "Deco Scratches",
        "420" to "Cut Blanket",
        "433" to "Printing Plate Failure",
        "437" to "Colour Variation",
        "440" to "Overvarnish Drips",
        "454" to "Blanket Contamination",
        "460" to "Printing Plate Contamination",
        "470" to "Void print",
        "471" to "Defective print",
        "502" to "High M/A",
        "505" to "Bubbles",
        "601" to "N/F Scratches",
        "602" to "N/F Damage",
        "604" to "Low Axial Load",
        "606" to "Neck Pleats",
        "620" to "Contamination / Internal",
        "624" to "Dents",
        "645" to "Wavy Flange",
        "654" to "Low wall Dent",
        "705" to "Missing Cans",
        "709" to "Damaged Cans",
        "1500" to "Warehouse Damage Refill Pallet"
    )

    private val reports = mutableListOf<Report>()
    private val prefs by lazy {
        getSharedPreferences("reports", MODE_PRIVATE)
    }

    private lateinit var numberSpinner: Spinner
    private lateinit var failureSpinner: Spinner
    private lateinit var prioritySpinner: Spinner
    private lateinit var timeButton: Button
    private lateinit var comment: EditText
    private lateinit var reportList: TextView
    private lateinit var totalText: TextView

    private var selectedTime = ""
    private var syncing = false

    private val numbers = failures.map {
        it.first.padStart(4, '0')
    }
    private val descriptions = failures.map { it.second }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadReports()
        buildScreen()
        refresh()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 30)
            setBackgroundColor(0xFFF2F5F8.toInt())
        }

        scroll.addView(layout)
        setContentView(scroll)

        addLabel(layout, "FAILURE REPORT", 25)
        totalText = addLabel(layout, "TOTAL: 0", 16)

        addLabel(layout, "1. Failure Number")
        numberSpinner = Spinner(this)
        numberSpinner.adapter = adapter(numbers)
        layout.addView(numberSpinner)

        addLabel(layout, "2. Failure")
        failureSpinner = Spinner(this)
        failureSpinner.adapter = adapter(descriptions)
        layout.addView(failureSpinner)

        numberSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p: AdapterView<*>?) {}
                override fun onItemSelected(
                    p: AdapterView<*>?, v: android.view.View?,
                    position: Int, id: Long
                ) {
                    if (!syncing) {
                        syncing = true
                        failureSpinner.setSelection(position)
                        syncing = false
                    }
                }
            }

        failureSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p: AdapterView<*>?) {}
                override fun onItemSelected(
                    p: AdapterView<*>?, v: android.view.View?,
                    position: Int, id: Long
                ) {
                    if (!syncing) {
                        syncing = true
                        numberSpinner.setSelection(position)
                        syncing = false
                    }
                }
            }

        addLabel(layout, "3. Time")
        timeButton = Button(this).apply {
            text = "SELECT TIME"
            setOnClickListener { chooseTime() }
        }
        layout.addView(timeButton)

        addLabel(layout, "4. Priority")
        prioritySpinner = Spinner(this)
        prioritySpinner.adapter = adapter(listOf("A", "B", "C"))
        layout.addView(prioritySpinner)

        addLabel(layout, "5. Comment")
        comment = EditText(this).apply {
            hint = "Enter comments..."
            minLines = 3
            gravity = Gravity.TOP
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        layout.addView(comment)

        addButton(layout, "ADD", 0xFF16834B) {
            addReport()
        }

        addButton(layout, "RESET ALL", 0xFFC62828) {
            resetFirst()
        }

        addButton(layout, "EXPORT EXCEL", 0xFF1769AA) {
            exportExcel()
        }

        addLabel(layout, "SAVED REPORTS")
        reportList = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF222222.toInt())
        }
        layout.addView(reportList)
    }

    private fun adapter(items: List<String>) =
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            items
        ).also {
            it.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

    private fun addLabel(
        layout: LinearLayout,
        value: String,
        size: Int = 17
    ): TextView {
        val text = TextView(this).apply {
            this.text = value
            textSize = size.toFloat()
            setTextColor(0xFF222222.toInt())
            setPadding(0, 18, 0, 8)
        }
        layout.addView(text)
        return text
    }

    private fun addButton(
        layout: LinearLayout,
        label: String,
        color: Int,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(color)
            setOnClickListener { action() }
        }
        layout.addView(button)
    }

    private fun chooseTime() {
        val now = java.util.Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                selectedTime = String.format(
                    java.util.Locale.US, "%02d:%02d", hour, minute
                )
                timeButton.text = selectedTime
            },
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE),
            true
        ).show()
    }

    private fun addReport() {
        if (selectedTime.isEmpty()) {
            toast("Please select time")
            return
        }

        val i = numberSpinner.selectedItemPosition
        reports.add(
            Report(
                numbers[i],
                descriptions[i],
                selectedTime,
                prioritySpinner.selectedItem.toString(),
                comment.text.toString().trim()
            )
        )

        saveReports()
        refresh()
        clearForm()
        toast("Report added")
    }

    private fun clearForm() {
        numberSpinner.setSelection(0)
        failureSpinner.setSelection(0)
        prioritySpinner.setSelection(0)
        selectedTime = ""
        timeButton.text = "SELECT TIME"
        comment.text.clear()
    }

    private fun resetFirst() {
        AlertDialog.Builder(this)
            .setTitle("Reset confirmation")
            .setMessage("Do you want to delete all reports?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CONTINUE") { _, _ -> resetSecond() }
            .show()
    }

    private fun resetSecond() {
        AlertDialog.Builder(this)
            .setTitle("FINAL WARNING")
            .setMessage("All saved reports will be permanently deleted!")
            .setNegativeButton("NO", null)
            .setPositiveButton("YES, DELETE ALL") { _, _ ->
                reports.clear()
                saveReports()
                clearForm()
                refresh()
                toast("All reports deleted")
            }
            .show()
    }

    private fun refresh() {
        totalText.text = "TOTAL REPORTS: ${reports.size}"

        reportList.text = if (reports.isEmpty()) {
            "No reports yet"
        } else {
            reports.mapIndexed { index, r ->
                "${index + 1}. ${r.number} - ${r.failure}\n" +
                "Time: ${r.time} | Priority: ${r.priority}\n" +
                "Comment: ${r.comment}\n----------------------"
            }.joinToString("\n")
        }
    }

    private fun saveReports() {
        val array = JSONArray()
        reports.forEach { r ->
            array.put(JSONObject().apply {
                put("number", r.number)
                put("failure", r.failure)
                put("time", r.time)
                put("priority", r.priority)
                put("comment", r.comment)
            })
        }
        prefs.edit().putString("data", array.toString()).apply()
    }

    private fun loadReports() {
        try {
            val array = JSONArray(prefs.getString("data", "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                reports.add(
                    Report(
                        o.getString("number"),
                        o.getString("failure"),
                        o.getString("time"),
                        o.getString("priority"),
                        o.getString("comment")
                    )
                )
            }
        } catch (_: Exception) {
            reports.clear()
        }
    }

    private fun exportExcel() {
        if (reports.isEmpty()) {
            toast("No reports to export")
            return
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_TITLE, "FailureReport.xlsx")
        }
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.use {
                    writeExcel(it)
                }
                toast("Excel file saved")
            } catch (e: Exception) {
                toast("Export failed: ${e.message}")
            }
        }
    }

    private fun writeExcel(output: java.io.OutputStream) {
        val rows = mutableListOf(
            listOf("Failure Number", "Failure", "Time", "Priority", "Comment")
        )

        reports.forEach {
            rows.add(
                listOf(
                    it.number, it.failure, it.time,
                    it.priority, it.comment
                )
            )
        }

        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<sheetViews><sheetView workbookViewId="0" rightToLeft="0"/></sheetViews><sheetData>""")

            rows.forEachIndexed { ri, row ->
                val rn = ri + 1
                append("""<row r="$rn">""")
                row.forEachIndexed { ci, value ->
                    val cell = "${('A'.code + ci).toChar()}$rn"
                    append(
                        """<c r="$cell" t="inlineStr"><is><t xml:space="preserve">${escapeXml(value)}</t></is></c>"""
                    )
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val files = mapOf(
            "[Content_Types].xml" to
                """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to
                """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Failure Report" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to
                """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheet
        )

        ZipOutputStream(output).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun escapeXml(value: String) =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
