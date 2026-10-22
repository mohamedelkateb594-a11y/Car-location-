package com.carwatch.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

// ---------- ألوان وأدوات رسم ----------
private val BLUE = Color.parseColor("#1A73E8")
private val TEXT = Color.parseColor("#202124")
private val SUB = Color.parseColor("#5F6368")
private val LINE = Color.parseColor("#DADCE0")
private val SOFT = Color.parseColor("#F1F3F4")
private val RED = Color.parseColor("#D93025")
private val GREEN = Color.parseColor("#188038")

private fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

private fun box(color: Int, radius: Float, strokeColor: Int = 0, strokeW: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.setColor(color)
    g.cornerRadius = radius
    if (strokeW > 0) g.setStroke(strokeW, strokeColor)
    return g
}

private fun topRounded(color: Int, r: Float): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    return g
}

private fun ovalBg(color: Int): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(color)
    return g
}

private fun targetBitmap(size: Int, color: Int): Bitmap {
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val m = size / 2f
    p.color = color
    p.style = Paint.Style.STROKE
    p.strokeWidth = size * 0.09f
    p.strokeCap = Paint.Cap.ROUND
    c.drawCircle(m, m, size * 0.28f, p)
    c.drawLine(m, size * 0.04f, m, size * 0.16f, p)
    c.drawLine(m, size * 0.84f, m, size * 0.96f, p)
    c.drawLine(size * 0.04f, m, size * 0.16f, m, p)
    c.drawLine(size * 0.84f, m, size * 0.96f, m, p)
    p.style = Paint.Style.FILL
    c.drawCircle(m, m, size * 0.11f, p)
    return b
}

private fun styleInput(ctx: Context, e: EditText) {
    e.background = box(Color.WHITE, ctx.dp(10).toFloat(), LINE, ctx.dp(1))
    e.setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
    e.textSize = 15f
    e.setTextColor(TEXT)
    e.setHintTextColor(Color.parseColor("#9AA0A6"))
}

private fun mpWrap(top: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = top
    }

/** scroll بأقصى ارتفاع */
private class MaxHeightScrollView(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }
}

/** بطاقة إعداد إجراء (دخول أو خروج): تفعيل، اتصال/واتساب، رقم، نص الرسالة */
private class RuleView(
    ctx: Context,
    private val title: String,
    accent: Int,
    initial: Rule
) : LinearLayout(ctx) {

    private val sw = Switch(ctx)
    private val body = LinearLayout(ctx)
    private val btnCall = TextView(ctx)
    private val btnWa = TextView(ctx)
    private val phone = EditText(ctx)
    private val msg = EditText(ctx)
    private val note = TextView(ctx)
    private var method: String = if (initial.method == METHOD_WHATSAPP) METHOD_WHATSAPP else METHOD_CALL

    init {
        orientation = LinearLayout.VERTICAL
        background = box(SOFT, ctx.dp(14).toFloat())
        setPadding(ctx.dp(14), ctx.dp(10), ctx.dp(14), ctx.dp(12))

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val t = TextView(ctx)
        t.text = title
        t.textSize = 15f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(accent)
        head.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sw.isChecked = initial.enabled
        head.addView(sw)
        addView(head, mpWrap())

        body.orientation = LinearLayout.VERTICAL
        addView(body, mpWrap(ctx.dp(8)))

        val seg = LinearLayout(ctx)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.background = box(Color.WHITE, ctx.dp(12).toFloat(), LINE, ctx.dp(1))
        seg.setPadding(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
        btnCall.text = "📞 اتصال"
        btnWa.text = "💬 واتساب"
        for (b in listOf(btnCall, btnWa)) {
            b.gravity = Gravity.CENTER
            b.textSize = 14f
            b.setPadding(0, ctx.dp(9), 0, ctx.dp(9))
            seg.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        btnCall.setOnClickListener {
            method = METHOD_CALL
            refreshSeg()
        }
        btnWa.setOnClickListener {
            method = METHOD_WHATSAPP
            refreshSeg()
        }
        body.addView(seg, mpWrap())

        phone.hint = "رقم الزميل (مثال: 01012345678)"
        phone.inputType = InputType.TYPE_CLASS_PHONE
        phone.setText(initial.phone)
        styleInput(ctx, phone)
        body.addView(phone, mpWrap(ctx.dp(8)))

        msg.hint = "نص الرسالة"
        msg.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        msg.minLines = 2
        msg.gravity = Gravity.TOP
        msg.setText(initial.message)
        styleInput(ctx, msg)
        body.addView(msg, mpWrap(ctx.dp(8)))

        note.text = "واتساب هيفتح والرسالة جاهزة، وهتضغط إرسال بإيدك (واتساب مبيسمحش بالإرسال التلقائي)."
        note.textSize = 12f
        note.setTextColor(SUB)
        body.addView(note, mpWrap(ctx.dp(6)))

        sw.setOnCheckedChangeListener { _, on ->
            body.visibility = if (on) View.VISIBLE else View.GONE
        }
        body.visibility = if (sw.isChecked) View.VISIBLE else View.GONE
        refreshSeg()
    }

    private fun refreshSeg() {
        val c = context
        val callSel = method == METHOD_CALL
        btnCall.background = if (callSel) box(BLUE, c.dp(10).toFloat()) else null
        btnCall.setTextColor(if (callSel) Color.WHITE else TEXT)
        btnWa.background = if (!callSel) box(BLUE, c.dp(10).toFloat()) else null
        btnWa.setTextColor(if (!callSel) Color.WHITE else TEXT)
        msg.visibility = if (callSel) View.GONE else View.VISIBLE
        note.visibility = if (callSel) View.GONE else View.VISIBLE
    }

    fun error(): String? {
        if (!sw.isChecked) return null
        val digits = phone.text.toString().filter { it.isDigit() }
        if (digits.length < 8 || digits.length > 15) return "الرقم غير صحيح في: $title"
        return null
    }

    fun toRule(): Rule = Rule(
        sw.isChecked,
        method,
        phone.text.toString().trim().replace(" ", "").replace("-", ""),
        msg.text.toString().trim()
    )
}

class MainActivity : Activity() {

    private lateinit var map: MapView
    private var gmap: GoogleMap? = null
    private var zones: MutableList<Zone> = mutableListOf()

    private lateinit var statusText: TextView
    private lateinit var monitorSwitch: Switch
    private lateinit var permBanner: LinearLayout
    private lateinit var sheet: LinearLayout
    private lateinit var sheetTitle: TextView
    private lateinit var chevron: TextView
    private lateinit var clearBtn: TextView
    private lateinit var listScroll: ScrollView
    private lateinit var listBox: LinearLayout
    private lateinit var fabRow: FrameLayout
    private lateinit var pickBar: LinearLayout
    private lateinit var centerPin: TextView

    private var expanded = true
    private var pickMode = false
    private var updatingSwitch = false
    private var centeredOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        zones = ZoneStore.load(this)

        val root = FrameLayout(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL

        // ----- خريطة جوجل -----
        map = MapView(this)
        map.onCreate(savedInstanceState)
        root.addView(map, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        map.getMapAsync { g ->
            gmap = g
            g.uiSettings.isZoomControlsEnabled = false
            g.uiSettings.isMyLocationButtonEnabled = false
            g.uiSettings.isMapToolbarEnabled = false
            g.moveCamera(CameraUpdateFactory.newLatLngZoom(startPoint(true), 15f))
            g.setOnMapLongClickListener { p ->
                if (!pickMode) openEditor(null, p)
            }
            g.setOnMarkerClickListener { m ->
                val id = m.tag as? String
                zones.find { it.id == id }?.let { openEditor(it, null) }
                true
            }
            ensureMyLocation()
            refresh()
        }

        // دبوس المنتصف (وضع اختيار الموقع)
        centerPin = TextView(this)
        centerPin.text = "📍"
        centerPin.textSize = 40f
        centerPin.visibility = View.GONE
        root.addView(
            centerPin,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                bottomMargin = dp(56)
            }
        )

        root.addView(
            buildTop(),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
                setMargins(dp(12), dp(12), dp(12), 0)
            }
        )
        root.addView(
            buildBottom(),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        )

        setContentView(root)
        refresh()
    }

    // ---------- بناء الواجهة ----------

    private fun buildTop(): View {
        val area = LinearLayout(this)
        area.orientation = LinearLayout.VERTICAL

        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = box(Color.WHITE, dp(18).toFloat())
        card.elevation = dp(6).toFloat()
        card.setPadding(dp(16), dp(10), dp(16), dp(10))

        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = "🚗 CarWatch"
        title.textSize = 17f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(TEXT)
        statusText = TextView(this)
        statusText.textSize = 12f
        statusText.setTextColor(SUB)
        titles.addView(title)
        titles.addView(statusText)
        card.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        monitorSwitch = Switch(this)
        monitorSwitch.setOnCheckedChangeListener { _, on ->
            if (!updatingSwitch) setMonitoring(on)
        }
        card.addView(monitorSwitch)
        area.addView(card, mpWrap())

        permBanner = LinearLayout(this)
        permBanner.orientation = LinearLayout.HORIZONTAL
        permBanner.gravity = Gravity.CENTER_VERTICAL
        permBanner.background = box(Color.parseColor("#FEF7E0"), dp(14).toFloat(), Color.parseColor("#F9AB00"), dp(1))
        permBanner.setPadding(dp(14), dp(8), dp(8), dp(8))
        val bt = TextView(this)
        bt.text = "⚠️ لازم تفعّل الصلاحيات عشان المراقبة تشتغل صح"
        bt.textSize = 13f
        bt.setTextColor(TEXT)
        permBanner.addView(bt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val bb = TextView(this)
        bb.text = "تفعيل"
        bb.textSize = 13f
        bb.setTextColor(Color.WHITE)
        bb.gravity = Gravity.CENTER
        bb.background = box(BLUE, dp(16).toFloat())
        bb.setPadding(dp(16), dp(8), dp(16), dp(8))
        bb.setOnClickListener { step() }
        permBanner.addView(bb)
        area.addView(permBanner, mpWrap(dp(8)))
        return area
    }

    private fun buildBottom(): View {
        val area = LinearLayout(this)
        area.orientation = LinearLayout.VERTICAL

        // أزرار عائمة
        fabRow = FrameLayout(this)
        val fabs = LinearLayout(this)
        fabs.orientation = LinearLayout.VERTICAL

        val locIcon = ImageView(this)
        locIcon.setImageBitmap(targetBitmap(dp(26), BLUE))
        val locFab = roundFab(locIcon, Color.WHITE)
        locFab.setOnClickListener { goToMe() }
        fabs.addView(locFab, LinearLayout.LayoutParams(dp(52), dp(52)))

        val plus = TextView(this)
        plus.text = "+"
        plus.textSize = 30f
        plus.setTextColor(Color.WHITE)
        plus.includeFontPadding = false
        val addFab = roundFab(plus, BLUE)
        addFab.setOnClickListener { setPickMode(true) }
        fabs.addView(addFab, LinearLayout.LayoutParams(dp(52), dp(52)).apply { topMargin = dp(10) })

        fabRow.addView(
            fabs,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT or Gravity.BOTTOM).apply {
                setMargins(dp(12), 0, 0, dp(12))
            }
        )
        area.addView(fabRow, mpWrap())

        // شريط تأكيد الموقع
        pickBar = LinearLayout(this)
        pickBar.orientation = LinearLayout.HORIZONTAL
        pickBar.visibility = View.GONE
        val ok = pill("✔ تأكيد الموقع", BLUE, Color.WHITE)
        ok.setOnClickListener {
            val c = gmap?.cameraPosition?.target
            if (c != null) {
                setPickMode(false)
                openEditor(null, c)
            }
        }
        val cancel = pill("إلغاء", Color.WHITE, TEXT)
        cancel.setOnClickListener { setPickMode(false) }
        pickBar.addView(ok, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f).apply { setMargins(dp(12), 0, dp(6), dp(28)) })
        pickBar.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(6), 0, dp(12), dp(28)) })
        area.addView(pickBar, mpWrap())

        // الشريط السفلي (قائمة النقاط)
        sheet = LinearLayout(this)
        sheet.orientation = LinearLayout.VERTICAL
        sheet.background = topRounded(Color.WHITE, dp(24).toFloat())
        sheet.elevation = dp(12).toFloat()

        val handle = View(this)
        handle.background = box(LINE, dp(2).toFloat())
        sheet.addView(handle, LinearLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(18), dp(10), dp(18), dp(10))
        sheetTitle = TextView(this)
        sheetTitle.textSize = 16f
        sheetTitle.setTypeface(null, Typeface.BOLD)
        sheetTitle.setTextColor(TEXT)
        header.addView(sheetTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        clearBtn = TextView(this)
        clearBtn.text = "مسح الكل"
        clearBtn.textSize = 13f
        clearBtn.setTextColor(RED)
        clearBtn.setPadding(dp(10), dp(6), dp(10), dp(6))
        clearBtn.setOnClickListener { confirmClear() }
        header.addView(clearBtn)
        chevron = TextView(this)
        chevron.textSize = 20f
        chevron.setTextColor(SUB)
        chevron.setPadding(dp(8), 0, 0, 0)
        header.addView(chevron)
        header.setOnClickListener {
            expanded = !expanded
            updateSheet()
        }
        sheet.addView(header, mpWrap())

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(0, 0, 0, dp(10))
        listScroll = MaxHeightScrollView(this, (resources.displayMetrics.heightPixels * 0.36f).toInt())
        listScroll.addView(listBox)
        sheet.addView(listScroll, mpWrap())

        area.addView(sheet, mpWrap())
        return area
    }

    private fun roundFab(content: View, bg: Int): FrameLayout {
        val f = FrameLayout(this)
        f.background = ovalBg(bg)
        f.elevation = dp(6).toFloat()
        f.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        return f
    }

    private fun pill(label: String, bg: Int, fg: Int): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTypeface(null, Typeface.BOLD)
        t.setTextColor(fg)
        t.gravity = Gravity.CENTER
        t.background = box(bg, dp(26).toFloat())
        t.elevation = dp(6).toFloat()
        t.setPadding(dp(16), dp(14), dp(16), dp(14))
        return t
    }

    private fun actionBtn(label: String, color: Int, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 13f
        t.setTextColor(color)
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(10), 0, dp(10))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun describe(r: Rule): String {
        if (!r.enabled) return "غير مفعّل"
        val m = if (r.method == METHOD_WHATSAPP) "واتساب" else "اتصال"
        return m + " ← " + r.phone
    }

    private fun zoneCard(z: Zone): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = box(SOFT, dp(14).toFloat())
        card.setPadding(dp(14), dp(12), dp(14), dp(4))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val name = TextView(this)
        name.text = "📍 " + z.name
        name.textSize = 15f
        name.setTypeface(null, Typeface.BOLD)
        name.setTextColor(TEXT)
        top.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val rad = TextView(this)
        rad.text = "${z.radius.toInt()} م"
        rad.textSize = 12f
        rad.setTextColor(SUB)
        top.addView(rad)
        card.addView(top, mpWrap())

        val enter = TextView(this)
        enter.text = "🟢 دخول: " + describe(z.onEnter)
        enter.textSize = 13f
        enter.setTextColor(if (z.onEnter.enabled) GREEN else SUB)
        card.addView(enter, mpWrap(dp(6)))
        val exit = TextView(this)
        exit.text = "🔴 خروج: " + describe(z.onExit)
        exit.textSize = 13f
        exit.setTextColor(if (z.onExit.enabled) RED else SUB)
        card.addView(exit, mpWrap(dp(2)))

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.addView(actionBtn("عرض", BLUE) {
            gmap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(z.lat, z.lng), 16.5f))
            expanded = false
            updateSheet()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(actionBtn("تعديل", BLUE) { openEditor(z, null) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(actionBtn("حذف", RED) { confirmDelete(z) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(actions, mpWrap(dp(4)))

        card.layoutParams = mpWrap().apply { setMargins(dp(14), dp(4), dp(14), dp(4)) }
        return card
    }

    // ---------- تحديث الواجهة ----------

    private fun refresh() {
        val g = gmap
        if (g != null) {
            g.clear()
            zones.forEach { z ->
                val c = LatLng(z.lat, z.lng)
                g.addCircle(
                    CircleOptions()
                        .center(c)
                        .radius(z.radius.toDouble())
                        .strokeColor(BLUE)
                        .strokeWidth(3f)
                        .fillColor(Color.argb(45, 26, 115, 232))
                )
                val m = g.addMarker(
                    MarkerOptions()
                        .position(c)
                        .title(z.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
                m?.tag = z.id
            }
        }
        updateSheet()
        updateStatus()
        updateBanner()
    }

    private fun updateSheet() {
        sheetTitle.text = "📌 نقاطي (${zones.size})"
        clearBtn.visibility = if (zones.isEmpty()) View.GONE else View.VISIBLE
        chevron.text = if (expanded) "⌄" else "⌃"
        listBox.removeAllViews()
        if (zones.isEmpty()) {
            val e = TextView(this)
            e.text = "مفيش نقاط لسه.\nاضغط + أو اضغط مطولًا على الخريطة لإضافة نقطة."
            e.textSize = 13f
            e.setTextColor(SUB)
            e.setPadding(dp(18), dp(4), dp(18), dp(12))
            listBox.addView(e)
        } else {
            zones.forEach { listBox.addView(zoneCard(it)) }
        }
        listScroll.visibility = if (expanded) View.VISIBLE else View.GONE
        applyMapPadding()
    }

    /** يخلي لوجو جوجل ظاهر فوق الشريط السفلي */
    private fun applyMapPadding() {
        sheet.post {
            gmap?.setPadding(0, 0, 0, if (pickMode) 0 else sheet.height)
        }
    }

    private fun updateStatus() {
        val on = AppPrefs.isMonitoring(this)
        statusText.text = (if (on) "🟢 المراقبة شغالة" else "⚪ المراقبة متوقفة") + " • ${zones.size} نقطة"
        updatingSwitch = true
        monitorSwitch.isChecked = on
        updatingSwitch = false
    }

    private fun updateBanner() {
        permBanner.visibility = if (hasAllPerms()) View.GONE else View.VISIBLE
    }

    private fun setPickMode(on: Boolean) {
        pickMode = on
        centerPin.visibility = if (on) View.VISIBLE else View.GONE
        pickBar.visibility = if (on) View.VISIBLE else View.GONE
        sheet.visibility = if (on) View.GONE else View.VISIBLE
        fabRow.visibility = if (on) View.GONE else View.VISIBLE
        if (on) gmap?.setPadding(0, 0, 0, 0) else applyMapPadding()
        if (on) toast("حرّك الخريطة لحد ما الدبوس يبقى على المكان، وبعدين اضغط تأكيد")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (pickMode) setPickMode(false) else super.onBackPressed()
    }

    // ---------- النقاط: إضافة / تعديل / حذف ----------

    private fun openEditor(existing: Zone?, point: LatLng?) {
        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), dp(8), dp(18), dp(8))
        scroll.addView(content)

        val name = EditText(this)
        name.hint = "اسم النقطة (مثال: البيت)"
        name.inputType = InputType.TYPE_CLASS_TEXT
        name.setText(existing?.name ?: "نقطة ${zones.size + 1}")
        styleInput(this, name)
        content.addView(name, mpWrap())

        val radiusLabel = TextView(this)
        radiusLabel.textSize = 14f
        radiusLabel.setTextColor(TEXT)
        content.addView(radiusLabel, mpWrap(dp(14)))
        val seek = SeekBar(this)
        seek.max = 1950
        seek.progress = ((existing?.radius ?: 200f).toInt() - 50).coerceIn(0, 1950)
        radiusLabel.text = "نصف القطر: ${seek.progress + 50} متر"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                radiusLabel.text = "نصف القطر: ${p + 50} متر"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        content.addView(seek, mpWrap(dp(4)))

        val enterView = RuleView(this, "🟢 لما العربية تدخل المنطقة", GREEN, existing?.onEnter ?: Rule(true))
        content.addView(enterView, mpWrap(dp(14)))
        val exitView = RuleView(this, "🔴 لما العربية تخرج من المنطقة", RED, existing?.onExit ?: Rule(false))
        content.addView(exitView, mpWrap(dp(10)))

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "نقطة جديدة" else "تعديل النقطة")
            .setView(scroll)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val err = enterView.error() ?: exitView.error()
            if (err != null) {
                toast(err)
                return@setOnClickListener
            }
            val er = enterView.toRule()
            val xr = exitView.toRule()
            if (!er.enabled && !xr.enabled) {
                toast("فعّل إجراء واحد على الأقل (دخول أو خروج)")
                return@setOnClickListener
            }
            val nm = name.text.toString().trim().ifEmpty { "نقطة ${zones.size + 1}" }
            val r = (seek.progress + 50).toFloat()
            val id: String
            if (existing != null) {
                id = existing.id
                val idx = zones.indexOfFirst { it.id == existing.id }
                if (idx >= 0) zones[idx] = existing.copy(name = nm, radius = r, onEnter = er, onExit = xr)
            } else {
                id = System.currentTimeMillis().toString()
                zones.add(Zone(id, nm, point!!.latitude, point.longitude, r, er, xr))
            }
            ZoneStore.save(this, zones)
            ZoneStore.resetState(this, id)
            refresh()
            dlg.dismiss()
            toast(if (AppPrefs.isMonitoring(this)) "تم الحفظ ✅" else "تم الحفظ ✅ — لا تنسى تشغّل المراقبة من فوق")
        }
    }

    private fun confirmDelete(z: Zone) {
        AlertDialog.Builder(this)
            .setTitle("حذف النقطة؟")
            .setMessage(z.name)
            .setPositiveButton("حذف") { _, _ ->
                zones.removeAll { it.id == z.id }
                ZoneStore.save(this, zones)
                ZoneStore.resetState(this, z.id)
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmClear() {
        if (zones.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("مسح كل النقاط؟")
            .setPositiveButton("مسح") { _, _ ->
                zones.forEach { ZoneStore.resetState(this, it.id) }
                zones.clear()
                ZoneStore.save(this, zones)
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------- المراقبة والموقع الحي ----------

    private fun setMonitoring(on: Boolean) {
        if (on) {
            if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
                toast("فعّل صلاحية الموقع الأول")
                updateStatus()
                step()
                return
            }
            AppPrefs.setMonitoring(this, true)
            try {
                MonitorService.start(this)
            } catch (e: Exception) {
                AppPrefs.setMonitoring(this, false)
                toast("تعذّر تشغيل المراقبة: " + e.message)
            }
        } else {
            AppPrefs.setMonitoring(this, false)
            MonitorService.stop(this)
        }
        updateStatus()
    }

    /** يفعّل النقطة الزرقا الحية المضمونة في خرائط جوجل (بدائرة الدقة) */
    @SuppressLint("MissingPermission")
    private fun ensureMyLocation() {
        val g = gmap ?: return
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) return
        try {
            g.isMyLocationEnabled = true
        } catch (_: SecurityException) {
        }
        if (!centeredOnce && zones.isEmpty()) {
            centeredOnce = true
            flyToMe(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun flyToMe(showError: Boolean) {
        val g = gmap ?: return
        val client = LocationServices.getFusedLocationProviderClient(this)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
            if (loc != null) {
                g.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17f))
            } else if (showError) {
                toast("مش قادر أحدد موقعك، تأكد إن الـ GPS شغال")
            }
        }
    }

    private fun goToMe() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            toast("فعّل صلاحية الموقع الأول")
            step()
            return
        }
        ensureMyLocation()
        flyToMe(true)
    }

    @SuppressLint("MissingPermission")
    private fun startPoint(preferZones: Boolean): LatLng {
        if (preferZones) {
            zones.firstOrNull()?.let { return LatLng(it.lat, it.lng) }
        }
        if (has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            val lm = getSystemService(LocationManager::class.java)
            for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    val loc = lm.getLastKnownLocation(prov)
                    if (loc != null) return LatLng(loc.latitude, loc.longitude)
                } catch (_: Exception) {
                }
            }
        }
        return LatLng(30.0444, 31.2357)
    }

    // ---------- دورة حياة الخريطة ----------

    override fun onStart() {
        super.onStart()
        map.onStart()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        ensureMyLocation()
        if (AppPrefs.isMonitoring(this) && has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            try {
                MonitorService.start(this) // يتأكد إن الخدمة شغالة
            } catch (_: Exception) {
            }
        }
        updateStatus()
        updateBanner()
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
    }

    override fun onStop() {
        map.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        map.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        map.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        map.onSaveInstanceState(outState)
    }

    // ---------- الصلاحيات ----------

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun hasAllPerms(): Boolean {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) return false
        if (!has(Manifest.permission.CALL_PHONE)) return false
        if (Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) return false
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) return false
        if (!Settings.canDrawOverlays(this)) return false
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) return false
        return true
    }

    private fun step() {
        val need = mutableListOf<String>()
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!has(Manifest.permission.CALL_PHONE)) need.add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), 1)
            return
        }
        stepBackground()
    }

    private fun stepBackground() {
        if (Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            toast("اختار \"السماح طوال الوقت\" للموقع")
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
            return
        }
        stepExtras()
    }

    private fun stepExtras() {
        if (!Settings.canDrawOverlays(this)) {
            toast("فعّل العرض فوق التطبيقات (مهم لفتح واتساب من الخلفية) ثم ارجع واضغط تفعيل")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("اسمح بتجاهل توفير البطارية ثم ارجع واضغط تفعيل")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            return
        }
        toast("كل الصلاحيات جاهزة ✅")
        updateBanner()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        ensureMyLocation()
        updateBanner()
        when (code) {
            1 -> if (has(Manifest.permission.ACCESS_FINE_LOCATION)) stepBackground() else toast("صلاحية الموقع مطلوبة")
            2 -> stepExtras()
        }
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
