package com.carwatch.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class ZoneEditorActivity : Activity() {
    private lateinit var body: LinearLayout
    private lateinit var etName: EditText
    private lateinit var seek: SeekBar
    private lateinit var tvRadius: TextView
    private lateinit var chipFav: Chip
    private lateinit var swEnabled: Switch
    private lateinit var enterForm: ActionsForm
    private lateinit var exitForm: ActionsForm
    private lateinit var swLimited: Switch
    private lateinit var schedBox: LinearLayout
    private lateinit var revertRow: LinearLayout
    private val dayChips = mutableListOf<Chip>()
    private val revertChips = mutableListOf<Chip>()
    private lateinit var pStartTime: TextView
    private lateinit var pEndTime: TextView
    private lateinit var pStartDate: TextView
    private lateinit var pEndDate: TextView

    private var zoneId = ""
    private var lat = 0.0
    private var lng = 0.0
    private var radius = 200
    private var startMin = 8 * 60
    private var endMin = 17 * 60
    private var startDate = 0
    private var endDate = 0
    private var revertMin = 0

    private fun sectionCard(title: String, accent: Int): LinearLayout {
        val c = card()
        c.addView(tv(title, 16f, accent, true))
        body.addView(c)
        return c
    }

    private fun spaced(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(10)
        return lp
    }

    private fun clean(s: String): String = s.replace(" ", "").replace("-", "").trim()

    private inner class ActionsForm(parent: LinearLayout) {
        val airplane = Segmented(this@ZoneEditorActivity, listOf("بدون", "تفعيل", "إلغاء"))
        val silent = Segmented(this@ZoneEditorActivity, listOf("بدون", "تفعيل", "إلغاء"))
        val wifi = Segmented(this@ZoneEditorActivity, listOf("بدون", "تفعيل", "إلغاء"))
        val data = Segmented(this@ZoneEditorActivity, listOf("بدون", "تفعيل", "إلغاء"))
        val call = input("رقم للاتصال التلقائي (اختياري)", InputType.TYPE_CLASS_PHONE)
        val waNum = input("رقم واتساب (اختياري)", InputType.TYPE_CLASS_PHONE)
        val waText = input("نص رسالة الواتساب", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)

        init {
            row(parent, "✈️  وضع الطيران", airplane)
            row(parent, "🔕  الوضع الصامت", silent)
            row(parent, "📶  الواي فاي", wifi)
            row(parent, "📱  بيانات الجوال", data)
            val note = tv("الطيران والواي فاي والبيانات تجريبية (بتشتغل عن طريق إمكانية الوصول)", 11f, Ui.TEXT2)
            note.setPadding(0, dp(6), 0, dp(6))
            parent.addView(note)
            parent.addView(tv("📞  اتصال تلقائي", 14f, Ui.TEXT, true), spaced())
            parent.addView(call, spaced())
            parent.addView(tv("💬  رسالة واتساب", 14f, Ui.TEXT, true), spaced())
            parent.addView(waNum, spaced())
            parent.addView(waText, spaced())
        }

        private fun row(parent: LinearLayout, label: String, seg: Segmented) {
            val r = LinearLayout(this@ZoneEditorActivity)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(0, dp(8), 0, dp(4))
            r.addView(tv(label, 14f, Ui.TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
            r.addView(seg, LinearLayout.LayoutParams(dp(180), WRAP))
            parent.addView(r)
        }

        fun set(a: Actions) {
            airplane.choice = a.airplane
            silent.choice = a.silent
            wifi.choice = a.wifi
            data.choice = a.data
            call.setText(a.call)
            waNum.setText(a.waNumber)
            waText.setText(a.waText)
        }

        fun get(): Actions = Actions(
            airplane.choice,
            silent.choice,
            wifi.choice,
            data.choice,
            clean(call.text.toString()),
            clean(waNum.text.toString()),
            waText.text.toString().trim()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lightBars()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Color.WHITE)
        bar.elevation = dp(4).toFloat()
        bar.setPadding(dp(8), dp(8), dp(16), dp(8))
        val close = tv("✕", 20f, Ui.TEXT2)
        close.setPadding(dp(12), dp(8), dp(12), dp(8))
        close.setOnClickListener { finish() }
        bar.addView(close)
        bar.addView(tv("إعدادات المكان", 18f, Ui.TEXT, true))
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        val scroll = ScrollView(this)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, dp(6), 0, dp(16))
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // ===== بطاقة المكان =====
        val c1 = sectionCard("📍  المكان", Ui.PRIMARY)
        etName = input("اسم المكان (مثلًا: البيت، الشغل)", InputType.TYPE_CLASS_TEXT)
        c1.addView(etName, spaced())

        val rr = LinearLayout(this)
        rr.orientation = LinearLayout.HORIZONTAL
        rr.gravity = Gravity.CENTER_VERTICAL
        rr.setPadding(0, dp(14), 0, 0)
        rr.addView(tv("نصف القطر", 14f, Ui.TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
        tvRadius = tv("200 م", 14f, Ui.PRIMARY, true)
        rr.addView(tvRadius)
        c1.addView(rr)

        seek = SeekBar(this)
        seek.max = 78
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                radius = 50 + p * 25
                tvRadius.text = radius.toString() + " م"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        seek.progress = 6
        c1.addView(seek)

        val rowFav = LinearLayout(this)
        rowFav.orientation = LinearLayout.HORIZONTAL
        rowFav.gravity = Gravity.CENTER_VERTICAL
        rowFav.setPadding(0, dp(10), 0, 0)
        chipFav = Chip(this, "⭐ مفضلة")
        rowFav.addView(chipFav, LinearLayout.LayoutParams(WRAP, WRAP))
        val spacer = View(this)
        rowFav.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        swEnabled = Switch(this)
        swEnabled.text = "مفعّلة  "
        swEnabled.isChecked = true
        rowFav.addView(swEnabled)
        c1.addView(rowFav)

        // ===== الدخول والخروج =====
        val c2 = sectionCard("↘  عند الدخول للمنطقة", Ui.GREEN)
        enterForm = ActionsForm(c2)
        val c3 = sectionCard("↗  عند الخروج من المنطقة", Ui.ORANGE)
        exitForm = ActionsForm(c3)

        buildSchedule()

        // ===== شريط الأزرار السفلي =====
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.setBackgroundColor(Color.WHITE)
        actions.elevation = dp(8).toFloat()
        actions.setPadding(dp(8), dp(8), dp(8), dp(8))
        val bIn = pill("جرّب الدخول", Ui.CHIP, Ui.TEXT)
        bIn.setOnClickListener { test(true) }
        val bOut = pill("جرّب الخروج", Ui.CHIP, Ui.TEXT)
        bOut.setOnClickListener { test(false) }
        val bSave = pill("💾  حفظ", Ui.PRIMARY, Color.WHITE)
        bSave.setOnClickListener { save() }
        for (b in listOf(bIn, bOut, bSave)) {
            b.textSize = 13f
            b.setPadding(dp(6), dp(12), dp(6), dp(12))
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.setMargins(dp(4), 0, dp(4), 0)
            actions.addView(b, lp)
        }
        root.addView(actions, LinearLayout.LayoutParams(MATCH, WRAP))

        setContentView(root)

        val id = intent.getStringExtra("zoneId")
        val existing = if (id != null) ZoneStore.load(this).find { it.id == id } else null
        if (existing != null) {
            zoneId = existing.id
            lat = existing.lat
            lng = existing.lng
            etName.setText(existing.name)
            val p = ((existing.radius.toInt() - 50) / 25).coerceIn(0, 78)
            seek.progress = p
            radius = 50 + p * 25
            tvRadius.text = radius.toString() + " م"
            chipFav.on = existing.favorite
            swEnabled.isChecked = existing.enabled
            enterForm.set(existing.onEnter)
            exitForm.set(existing.onExit)
            applySchedule(existing.schedule)
        } else {
            zoneId = System.currentTimeMillis().toString()
            lat = intent.getDoubleExtra("lat", 0.0)
            lng = intent.getDoubleExtra("lng", 0.0)
        }
    }

    // ===== الجدولة =====
    private fun buildSchedule() {
        val c = sectionCard("🕒  الجدولة", Ui.PRIMARY)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(0, dp(8), 0, 0)
        top.addView(tv("تحديد أيام ووقت", 14f, Ui.TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
        swLimited = Switch(this)
        top.addView(swLimited)
        c.addView(top)
        c.addView(tv("لو مقفولة: بتشتغل في أي وقت", 12f, Ui.TEXT2))

        schedBox = LinearLayout(this)
        schedBox.orientation = LinearLayout.VERTICAL
        schedBox.visibility = View.GONE
        swLimited.setOnCheckedChangeListener { _, checked ->
            schedBox.visibility = if (checked) View.VISIBLE else View.GONE
        }

        schedBox.addView(tv("الأيام", 13f, Ui.TEXT2, true), spaced())
        val daysRow = LinearLayout(this)
        daysRow.orientation = LinearLayout.HORIZONTAL
        for (n in listOf("ح", "ن", "ث", "ر", "خ", "ج", "س")) {
            val ch = Chip(this, n, true)
            dayChips.add(ch)
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.setMargins(dp(2), dp(6), dp(2), 0)
            daysRow.addView(ch, lp)
        }
        schedBox.addView(daysRow)
        schedBox.addView(tv("ح أحد، ن اثنين، ث ثلاثاء، ر أربعاء، خ خميس، ج جمعة، س سبت", 11f, Ui.TEXT2))

        schedBox.addView(tv("الساعات", 13f, Ui.TEXT2, true), spaced())
        val timeRow = LinearLayout(this)
        timeRow.orientation = LinearLayout.HORIZONTAL
        pStartTime = pill("", Ui.CHIP, Ui.TEXT)
        pStartTime.setOnClickListener { pickTime(true) }
        pEndTime = pill("", Ui.CHIP, Ui.TEXT)
        pEndTime.setOnClickListener { pickTime(false) }
        timeRow.addView(pStartTime, pillLp())
        timeRow.addView(pEndTime, pillLp())
        schedBox.addView(timeRow)

        schedBox.addView(tv("التواريخ (اضغط مطولًا للمسح)", 13f, Ui.TEXT2, true), spaced())
        val dateRow = LinearLayout(this)
        dateRow.orientation = LinearLayout.HORIZONTAL
        pStartDate = pill("", Ui.CHIP, Ui.TEXT)
        pStartDate.setOnClickListener { pickDate(true) }
        pStartDate.setOnLongClickListener {
            startDate = 0
            refreshPills()
            true
        }
        pEndDate = pill("", Ui.CHIP, Ui.TEXT)
        pEndDate.setOnClickListener { pickDate(false) }
        pEndDate.setOnLongClickListener {
            endDate = 0
            refreshPills()
            true
        }
        dateRow.addView(pStartDate, pillLp())
        dateRow.addView(pEndDate, pillLp())
        schedBox.addView(dateRow)
        c.addView(schedBox)

        c.addView(tv("↩️  إعادة الحالة السابقة بعد", 14f, Ui.TEXT, true), spaced())
        c.addView(tv("مثلًا: صامت لمدة ساعة وبعدها يرجع طبيعي", 12f, Ui.TEXT2))
        revertRow = LinearLayout(this)
        revertRow.orientation = LinearLayout.HORIZONTAL
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(revertRow)
        c.addView(hs)
        rebuildRevert()
        refreshPills()
    }

    private fun pillLp(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
        lp.setMargins(dp(3), dp(6), dp(3), 0)
        return lp
    }

    private fun rebuildRevert() {
        revertRow.removeAllViews()
        revertChips.clear()
        val base = mutableListOf(0, 15, 30, 60, 120, 240)
        if (!base.contains(revertMin)) base.add(revertMin)
        for (v in base) {
            val label = if (v == 0) {
                "بدون"
            } else if (v >= 60 && v % 60 == 0) {
                (v / 60).toString() + " س"
            } else {
                v.toString() + " د"
            }
            val ch = Chip(this, label, v == revertMin) { _ ->
                revertMin = v
                for (k in revertChips.indices) {
                    revertChips[k].on = (base[k] == revertMin)
                }
            }
            revertChips.add(ch)
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.setMargins(0, dp(8), dp(8), dp(4))
            revertRow.addView(ch, lp)
        }
    }

    private fun refreshPills() {
        pStartTime.text = "من الساعة  " + fmtTime(startMin)
        pEndTime.text = "إلى الساعة  " + fmtTime(endMin)
        pStartDate.text = "من  " + fmtDate(startDate)
        pEndDate.text = "إلى  " + fmtDate(endDate)
        for (p in listOf(pStartTime, pEndTime, pStartDate, pEndDate)) {
            p.textSize = 12f
            p.setPadding(dp(6), dp(12), dp(6), dp(12))
        }
    }

    private fun pickTime(start: Boolean) {
        val cur = if (start) startMin else endMin
        TimePickerDialog(this, { _, h, m ->
            if (start) {
                startMin = h * 60 + m
            } else {
                endMin = h * 60 + m
            }
            refreshPills()
        }, cur / 60, cur % 60, true).show()
    }

    private fun pickDate(start: Boolean) {
        val c = Calendar.getInstance()
        val cur = if (start) startDate else endDate
        if (cur > 0) c.set(cur / 10000, (cur / 100) % 100 - 1, cur % 100)
        DatePickerDialog(this, { _, y, m, d ->
            val v = y * 10000 + (m + 1) * 100 + d
            if (start) {
                startDate = v
            } else {
                endDate = v
            }
            refreshPills()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun applySchedule(s: Schedule) {
        swLimited.isChecked = s.limited
        for (i in 0..6) dayChips[i].on = (s.days and (1 shl i)) != 0
        startMin = s.startMin
        endMin = s.endMin
        startDate = s.startDate
        endDate = s.endDate
        revertMin = s.revertMin
        rebuildRevert()
        refreshPills()
    }

    private fun readSchedule(): Schedule {
        var mask = 0
        for (i in 0..6) {
            if (dayChips[i].on) mask = mask or (1 shl i)
        }
        return Schedule(swLimited.isChecked, mask, startMin, endMin, startDate, endDate, revertMin)
    }

    // ===== الحفظ والتجربة =====
    private fun validPhone(s: String): Boolean = Regex("^[+]?[0-9]{8,16}$").matches(s)

    private fun collect(): Zone? {
        val nm = etName.text.toString().trim().ifEmpty { "نقطة" }
        val enter = enterForm.get()
        val exit = exitForm.get()
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
        return Zone(
            zoneId, nm, lat, lng, radius.toFloat(), chipFav.on, swEnabled.isChecked,
            enter, exit, readSchedule()
        )
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
