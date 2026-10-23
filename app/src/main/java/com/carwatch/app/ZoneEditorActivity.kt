package com.carwatch.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class ZoneEditorActivity : Activity() {
    private val options = arrayOf("بدون تغيير", "تفعيل", "إلغاء التفعيل")

    private lateinit var content: LinearLayout
    private lateinit var etName: EditText
    private lateinit var etRadius: EditText
    private lateinit var cbFav: CheckBox
    private lateinit var cbEnabled: CheckBox
    private lateinit var onEnterForm: ActionsForm
    private lateinit var onExitForm: ActionsForm
    private lateinit var cbLimited: CheckBox
    private val dayBoxes = mutableListOf<CheckBox>()
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var etRevert: EditText

    private var zoneId = ""
    private var lat = 0.0
    private var lng = 0.0
    private var startMin = 8 * 60
    private var endMin = 17 * 60
    private var startDate = 0
    private var endDate = 0

    private fun tv(t: String, size: Float = 14f, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        if (bold) v.setTypeface(null, Typeface.BOLD)
        v.setPadding(0, 24, 0, 8)
        return v
    }

    private fun edit(hint: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        return e
    }

    private fun spinner(): Spinner {
        val s = Spinner(this)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        return s
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { onClick() }
        return b
    }

    private fun clean(s: String): String = s.replace(" ", "").replace("-", "").trim()

    private inner class ActionsForm(title: String) {
        val airplane = spinner()
        val silent = spinner()
        val wifi = spinner()
        val data = spinner()
        val call = edit("رقم للاتصال التلقائي (اختياري)", InputType.TYPE_CLASS_PHONE)
        val waNum = edit("رقم واتساب (اختياري)", InputType.TYPE_CLASS_PHONE)
        val waText = edit("نص رسالة الواتساب", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)

        init {
            content.addView(tv(title, 18f, true))
            row("وضع الطيران", airplane)
            row("الوضع الصامت", silent)
            row("الواي فاي", wifi)
            row("بيانات الجوال", data)
            content.addView(call)
            content.addView(waNum)
            content.addView(waText)
        }

        private fun row(label: String, s: Spinner) {
            val r = LinearLayout(this@ZoneEditorActivity)
            r.orientation = LinearLayout.HORIZONTAL
            val t = TextView(this@ZoneEditorActivity)
            t.text = label
            r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(s, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            content.addView(r)
        }

        fun set(a: Actions) {
            airplane.setSelection(a.airplane)
            silent.setSelection(a.silent)
            wifi.setSelection(a.wifi)
            data.setSelection(a.data)
            call.setText(a.call)
            waNum.setText(a.waNumber)
            waText.setText(a.waText)
        }

        fun get(): Actions = Actions(
            airplane.selectedItemPosition,
            silent.selectedItemPosition,
            wifi.selectedItemPosition,
            data.selectedItemPosition,
            clean(call.text.toString()),
            clean(waNum.text.toString()),
            waText.text.toString().trim()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(32, 32, 32, 96)
        scroll.addView(content)
        setContentView(scroll)

        content.addView(tv("بيانات المكان", 18f, true))
        etName = edit("اسم المكان (مثلًا: البيت، الشغل)", InputType.TYPE_CLASS_TEXT)
        etRadius = edit("نصف القطر بالمتر", InputType.TYPE_CLASS_NUMBER)
        etRadius.setText("200")
        cbFav = CheckBox(this)
        cbFav.text = "⭐ إضافة للأماكن المفضلة"
        cbEnabled = CheckBox(this)
        cbEnabled.text = "المكان مفعّل (بيراقب الدخول والخروج)"
        cbEnabled.isChecked = true
        content.addView(etName)
        content.addView(etRadius)
        content.addView(cbFav)
        content.addView(cbEnabled)

        onEnterForm = ActionsForm("عند الدخول للمكان")
        onExitForm = ActionsForm("عند الخروج من المكان")
        buildSchedule()

        content.addView(button("حفظ") { save() })
        content.addView(button("جرّب تنفيذ إجراءات الدخول الآن") { test(true) })
        content.addView(button("جرّب تنفيذ إجراءات الخروج الآن") { test(false) })
        content.addView(button("إلغاء") { finish() })

        val id = intent.getStringExtra("zoneId")
        val existing = if (id != null) ZoneStore.load(this).find { it.id == id } else null
        if (existing != null) {
            zoneId = existing.id
            lat = existing.lat
            lng = existing.lng
            etName.setText(existing.name)
            etRadius.setText(existing.radius.toInt().toString())
            cbFav.isChecked = existing.favorite
            cbEnabled.isChecked = existing.enabled
            onEnterForm.set(existing.onEnter)
            onExitForm.set(existing.onExit)
            applySchedule(existing.schedule)
        } else {
            zoneId = System.currentTimeMillis().toString()
            lat = intent.getDoubleExtra("lat", 0.0)
            lng = intent.getDoubleExtra("lng", 0.0)
        }
    }

    private fun buildSchedule() {
        content.addView(tv("الجدولة", 18f, true))
        cbLimited = CheckBox(this)
        cbLimited.text = "تحديد أيام ووقت (من غيرها: بتشتغل دايمًا)"
        content.addView(cbLimited)

        content.addView(tv("الأيام: ح أحد، ن اثنين، ث ثلاثاء، ر أربعاء، خ خميس، ج جمعة، س سبت"))
        val daysRow = LinearLayout(this)
        daysRow.orientation = LinearLayout.HORIZONTAL
        for (n in arrayOf("ح", "ن", "ث", "ر", "خ", "ج", "س")) {
            val c = CheckBox(this)
            c.text = n
            c.isChecked = true
            dayBoxes.add(c)
            daysRow.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(daysRow)

        btnStartTime = button("") { pickTime(true) }
        btnEndTime = button("") { pickTime(false) }
        btnStartDate = button("") { pickDate(true) }
        btnEndDate = button("") { pickDate(false) }
        btnStartDate.setOnLongClickListener {
            startDate = 0
            refreshButtons()
            true
        }
        btnEndDate.setOnLongClickListener {
            endDate = 0
            refreshButtons()
            true
        }
        content.addView(btnStartTime)
        content.addView(btnEndTime)
        content.addView(btnStartDate)
        content.addView(btnEndDate)
        content.addView(tv("اضغط مطولًا على زر التاريخ لمسحه"))

        etRevert = edit("إعادة الإعدادات للحالة السابقة بعد كام دقيقة (0 = بدون)", InputType.TYPE_CLASS_NUMBER)
        etRevert.setText("0")
        content.addView(etRevert)
        refreshButtons()
    }

    private fun fmtTime(m: Int): String = String.format("%02d:%02d", m / 60, m % 60)

    private fun fmtDate(d: Int): String =
        if (d == 0) "بدون حد" else String.format("%04d-%02d-%02d", d / 10000, (d / 100) % 100, d % 100)

    private fun refreshButtons() {
        btnStartTime.text = "من الساعة: " + fmtTime(startMin)
        btnEndTime.text = "إلى الساعة: " + fmtTime(endMin)
        btnStartDate.text = "من تاريخ: " + fmtDate(startDate)
        btnEndDate.text = "إلى تاريخ: " + fmtDate(endDate)
    }

    private fun pickTime(start: Boolean) {
        val cur = if (start) startMin else endMin
        TimePickerDialog(this, { _, h, m ->
            if (start) startMin = h * 60 + m else endMin = h * 60 + m
            refreshButtons()
        }, cur / 60, cur % 60, true).show()
    }

    private fun pickDate(start: Boolean) {
        val c = Calendar.getInstance()
        val cur = if (start) startDate else endDate
        if (cur > 0) c.set(cur / 10000, (cur / 100) % 100 - 1, cur % 100)
        DatePickerDialog(this, { _, y, m, d ->
            val v = y * 10000 + (m + 1) * 100 + d
            if (start) startDate = v else endDate = v
            refreshButtons()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun applySchedule(s: Schedule) {
        cbLimited.isChecked = s.limited
        for (i in 0..6) dayBoxes[i].isChecked = (s.days and (1 shl i)) != 0
        startMin = s.startMin
        endMin = s.endMin
        startDate = s.startDate
        endDate = s.endDate
        etRevert.setText(s.revertMin.toString())
        refreshButtons()
    }

    private fun readSchedule(): Schedule {
        var mask = 0
        for (i in 0..6) if (dayBoxes[i].isChecked) mask = mask or (1 shl i)
        val rev = (etRevert.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
        return Schedule(cbLimited.isChecked, mask, startMin, endMin, startDate, endDate, rev)
    }

    private fun validPhone(s: String): Boolean = Regex("^[+]?[0-9]{8,16}$").matches(s)

    private fun collect(): Zone? {
        val rad = (etRadius.text.toString().toFloatOrNull() ?: 200f).coerceIn(50f, 5000f)
        val nm = etName.text.toString().trim().ifEmpty { "نقطة" }
        val enter = onEnterForm.get()
        val exit = onExitForm.get()
        for (a in listOf(enter, exit)) {
            if (a.call.isNotEmpty() && !validPhone(a.call)) {
                Toast.makeText(this, "رقم الاتصال غير صحيح", Toast.LENGTH_LONG).show()
                return null
            }
            if (a.waNumber.isNotEmpty() && !validPhone(a.waNumber)) {
                Toast.makeText(this, "رقم الواتساب غير صحيح", Toast.LENGTH_LONG).show()
                return null
            }
        }
        return Zone(zoneId, nm, lat, lng, rad, cbFav.isChecked, cbEnabled.isChecked, enter, exit, readSchedule())
    }

    private fun save() {
        val z = collect() ?: return
        ZoneStore.upsert(this, z)
        Geo.syncIfAllowed(this)
        Toast.makeText(this, "تم الحفظ ✅", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun test(entering: Boolean) {
        val z = collect() ?: return
        Actuator.run(this, z, entering, true)
        Toast.makeText(this, "تم التنفيذ (تجربة)", Toast.LENGTH_SHORT).show()
    }
}
