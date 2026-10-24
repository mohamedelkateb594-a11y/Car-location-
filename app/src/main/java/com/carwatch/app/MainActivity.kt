package com.carwatch.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var map: MapView
    private lateinit var me: MyLocationNewOverlay
    private lateinit var eventsOverlay: MapEventsOverlay
    private lateinit var etSearch: EditText
    private lateinit var banner: TextView
    private lateinit var chipsRow: LinearLayout
    private lateinit var chipsScroll: HorizontalScrollView
    private lateinit var detailCard: LinearLayout
    private lateinit var addRow: LinearLayout
    private lateinit var pickLayer: FrameLayout
    private var zones: MutableList<Zone> = mutableListOf()
    private var centered = false
    private var picking = false
    private var tileIndex = 0

    private class Perm(val title: String, val desc: String, val granted: () -> Boolean, val grant: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lightBars()

        val cfg = Configuration.getInstance()
        cfg.userAgentValue = packageName
        cfg.osmdroidBasePath = File(cacheDir, "osm")
        cfg.osmdroidTileCache = File(cacheDir, "osm/tiles")

        zones = ZoneStore.load(this)
        tileIndex = getSharedPreferences("ui", MODE_PRIVATE).getInt("tiles", 0)

        val root = FrameLayout(this)

        // ===== الخريطة =====
        map = MapView(this)
        applyTiles()
        map.setMultiTouchControls(true)
        map.setTilesScaledToDpi(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.controller.setZoom(15.0)
        map.controller.setCenter(startPoint())
        root.addView(map, FrameLayout.LayoutParams(MATCH, MATCH))

        // النقطة الزرقا لموقعي
        me = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        val dot = dotBitmap()
        me.setPersonIcon(dot)
        me.setDirectionArrow(dot, dot)
        me.setPersonAnchor(0.5f, 0.5f)

        eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                detailCard.visibility = View.GONE
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) openEditor(null, p)
                return true
            }
        })

        // ===== شريط البحث =====
        val searchBar = LinearLayout(this)
        searchBar.orientation = LinearLayout.HORIZONTAL
        searchBar.gravity = Gravity.CENTER_VERTICAL
        searchBar.background = roundBg(Color.WHITE, 28)
        searchBar.elevation = dp(6).toFloat()
        searchBar.setPadding(dp(18), dp(2), dp(8), dp(2))
        etSearch = EditText(this)
        etSearch.hint = "ابحث عن مكان أو عنوان"
        etSearch.textSize = 15f
        etSearch.background = null
        etSearch.setSingleLine(true)
        etSearch.imeOptions = EditorInfo.IME_ACTION_SEARCH
        etSearch.setOnEditorActionListener { _, _, _ ->
            doSearch(etSearch.text.toString())
            true
        }
        searchBar.addView(etSearch, LinearLayout.LayoutParams(0, WRAP, 1f))
        val btnSearch = tv("🔍", 20f)
        btnSearch.setPadding(dp(10), dp(10), dp(10), dp(10))
        btnSearch.setOnClickListener { doSearch(etSearch.text.toString()) }
        searchBar.addView(btnSearch)
        val sLp = FrameLayout.LayoutParams(MATCH, dp(52), Gravity.TOP)
        sLp.setMargins(dp(12), dp(10), dp(12), 0)
        root.addView(searchBar, sLp)

        // ===== تنبيه الصلاحيات =====
        banner = pill("⚠️ في صلاحيات ناقصة — اضغط هنا", Ui.ORANGE_SOFT, Ui.ORANGE)
        banner.textSize = 13f
        banner.elevation = dp(4).toFloat()
        banner.setOnClickListener { showPermissions() }
        val bLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        bLp.topMargin = dp(70)
        root.addView(banner, bLp)

        // ===== أزرار جانبية =====
        val fabs = LinearLayout(this)
        fabs.orientation = LinearLayout.VERTICAL
        val f1 = LinearLayout.LayoutParams(dp(48), dp(48))
        f1.bottomMargin = dp(10)
        val f2 = LinearLayout.LayoutParams(dp(48), dp(48))
        f2.bottomMargin = dp(10)
        val f3 = LinearLayout.LayoutParams(dp(48), dp(48))
        fabs.addView(circleButton("🎯") { flyToMe() }, f1)
        fabs.addView(circleButton("🗺️") { chooseTiles() }, f2)
        fabs.addView(circleButton("⚙️") { showPermissions() }, f3)
        val fLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
        fLp.setMargins(0, dp(124), dp(12), 0)
        root.addView(fabs, fLp)

        // ===== أسفل الشاشة: زر الإضافة + بطاقة التفاصيل + الأماكن =====
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL

        addRow = LinearLayout(this)
        addRow.gravity = Gravity.END
        addRow.setPadding(0, 0, dp(12), dp(4))
        val addPill = pill("➕  إضافة منطقة", Ui.PRIMARY, Color.WHITE, true)
        addPill.setOnClickListener { setPicking(true) }
        addRow.addView(addPill, LinearLayout.LayoutParams(WRAP, WRAP))
        bottom.addView(addRow, LinearLayout.LayoutParams(MATCH, WRAP))

        detailCard = card()
        detailCard.visibility = View.GONE
        bottom.addView(detailCard)

        chipsRow = LinearLayout(this)
        chipsRow.orientation = LinearLayout.HORIZONTAL
        chipsRow.setPadding(dp(12), 0, dp(12), 0)
        chipsScroll = HorizontalScrollView(this)
        chipsScroll.isHorizontalScrollBarEnabled = false
        chipsScroll.addView(chipsRow)
        bottom.addView(chipsScroll, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(bottom, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        // ===== وضع اختيار المكان (دبوس في النص) =====
        pickLayer = FrameLayout(this)
        pickLayer.visibility = View.GONE
        val pin = TextView(this)
        pin.text = "📍"
        pin.textSize = 42f
        pin.translationY = -dp(24).toFloat()
        pickLayer.addView(pin, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        val hint = pill("حرّك الخريطة لتحديد مكان المنطقة", Color.WHITE, Ui.TEXT, true)
        hint.textSize = 13f
        val hLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        hLp.topMargin = dp(70)
        pickLayer.addView(hint, hLp)
        val pickBtns = LinearLayout(this)
        pickBtns.orientation = LinearLayout.HORIZONTAL
        val cancel = pill("إلغاء", Color.WHITE, Ui.TEXT, true)
        cancel.setOnClickListener { setPicking(false) }
        val ok = pill("✔  تأكيد الموقع", Ui.PRIMARY, Color.WHITE, true)
        ok.setOnClickListener { confirmPick() }
        val cLp = LinearLayout.LayoutParams(WRAP, WRAP)
        cLp.setMargins(0, 0, dp(10), 0)
        pickBtns.addView(cancel, cLp)
        pickBtns.addView(ok, LinearLayout.LayoutParams(WRAP, WRAP))
        val pbLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        pbLp.bottomMargin = dp(28)
        pickLayer.addView(pickBtns, pbLp)
        root.addView(pickLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        setContentView(root)

        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 10
            )
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        zones = ZoneStore.load(this)
        startLocation()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        me.disableMyLocation()
        map.onPause()
    }

    override fun onBackPressed() {
        if (picking) {
            setPicking(false)
        } else if (detailCard.visibility == View.VISIBLE) {
            detailCard.visibility = View.GONE
        } else {
            super.onBackPressed()
        }
    }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun a11yEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains(GeoAccessibilityService::class.java.name)
    }

    // ===== الخريطة =====
    private fun carto(name: String, path: String) = XYTileSource(
        name, 0, 20, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/$path/",
            "https://b.basemaps.cartocdn.com/rastertiles/$path/",
            "https://c.basemaps.cartocdn.com/rastertiles/$path/"
        ),
        "© OpenStreetMap contributors © CARTO"
    )

    private fun applyTiles() {
        if (tileIndex == 1) {
            map.setTileSource(TileSourceFactory.MAPNIK)
        } else if (tileIndex == 2) {
            map.setTileSource(carto("CartoDark", "dark_all"))
        } else {
            map.setTileSource(carto("CartoVoyager", "voyager"))
        }
    }

    private fun chooseTiles() {
        AlertDialog.Builder(this)
            .setTitle("شكل الخريطة")
            .setItems(arrayOf("فاتحة (شبه جوجل)", "عادية (OpenStreetMap)", "داكنة")) { _, i ->
                tileIndex = i
                getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("tiles", i).apply()
                applyTiles()
            }
            .show()
    }

    private fun dotBitmap(): Bitmap {
        val s = dp(30)
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(70, 26, 115, 232)
        c.drawCircle(s / 2f, s / 2f, s / 2f, p)
        p.color = Color.WHITE
        c.drawCircle(s / 2f, s / 2f, s * 0.31f, p)
        p.color = Color.parseColor("#1A73E8")
        c.drawCircle(s / 2f, s / 2f, s * 0.23f, p)
        return b
    }

    @SuppressLint("MissingPermission")
    private fun startPoint(): GeoPoint {
        val z = zones.firstOrNull()
        if (z != null) return GeoPoint(z.lat, z.lng)
        if (has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            val lm = getSystemService(LocationManager::class.java)
            for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    val loc = lm.getLastKnownLocation(prov)
                    if (loc != null) return GeoPoint(loc.latitude, loc.longitude)
                } catch (_: Exception) {
                }
            }
        }
        return GeoPoint(30.0444, 31.2357)
    }

    private fun startLocation() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) return
        if (!me.isMyLocationEnabled) me.enableMyLocation()
        if (!centered) {
            centered = true
            if (zones.isEmpty()) flyToMe()
        }
    }

    @SuppressLint("MissingPermission")
    private fun flyToMe() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            grantLocation()
            return
        }
        startLocation()
        val p = me.myLocation
        if (p != null) {
            map.controller.setZoom(17.0)
            map.controller.animateTo(p)
        }
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    map.controller.setZoom(17.0)
                    map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                }
            }
    }

    private fun doSearch(q: String) {
        if (q.isBlank()) return
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, "البحث مش متاح على الجهاز ده", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "بدوّر...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val res = Geocoder(this, Locale.getDefault()).getFromLocationName(q, 1)
                runOnUiThread {
                    if (res != null && res.isNotEmpty()) {
                        val a = res[0]
                        map.controller.setZoom(16.5)
                        map.controller.animateTo(GeoPoint(a.latitude, a.longitude))
                    } else {
                        Toast.makeText(this, "مفيش نتيجة", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "البحث محتاج إنترنت", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ===== وضع اختيار المكان =====
    private fun setPicking(on: Boolean) {
        picking = on
        pickLayer.visibility = if (on) View.VISIBLE else View.GONE
        addRow.visibility = if (on) View.GONE else View.VISIBLE
        if (on) detailCard.visibility = View.GONE
        refresh()
    }

    private fun confirmPick() {
        val c = map.mapCenter
        val p = GeoPoint(c.latitude, c.longitude)
        setPicking(false)
        openEditor(null, p)
    }

    // ===== رسم المناطق =====
    private fun refresh() {
        map.overlays.clear()
        map.overlays.add(eventsOverlay)
        zones.forEach { z ->
            val center = GeoPoint(z.lat, z.lng)
            val color = if (z.enabled) Ui.PRIMARY else Color.GRAY
            val circle = Polygon(map)
            circle.points = Polygon.pointsAsCircle(center, z.radius.toDouble())
            circle.fillPaint.color = Color.argb(45, Color.red(color), Color.green(color), Color.blue(color))
            circle.outlinePaint.color = color
            circle.outlinePaint.strokeWidth = 4f
            circle.infoWindow = null
            map.overlays.add(circle)

            val marker = Marker(map)
            marker.position = center
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = (if (z.favorite) "⭐ " else "") + z.name
            marker.setOnMarkerClickListener { _, _ ->
                showDetail(z)
                true
            }
            map.overlays.add(marker)
        }
        map.overlays.add(me)
        map.invalidate()

        chipsRow.removeAllViews()
        val sorted = zones.sortedWith(compareByDescending<Zone> { it.favorite }.thenBy { it.name })
        for (z in sorted) {
            val c = TextView(this)
            c.text = (if (z.favorite) "⭐ " else "") + z.name
            c.textSize = 14f
            c.setTextColor(if (z.enabled) Ui.TEXT else Ui.TEXT2)
            c.background = roundBg(Color.WHITE, 20)
            c.elevation = dp(4).toFloat()
            c.setPadding(dp(14), dp(10), dp(14), dp(10))
            c.setOnClickListener { showDetail(z) }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.setMargins(0, dp(4), dp(8), dp(10))
            chipsRow.addView(c, lp)
        }
        chipsScroll.visibility = if (zones.isEmpty() || picking) View.GONE else View.VISIBLE
        banner.visibility = if (!picking && permsMissing()) View.VISIBLE else View.GONE
    }

    private fun describe(a: Actions): String {
        val parts = mutableListOf<String>()
        fun t(name: String, v: Int) {
            if (v == 1) {
                parts.add(name + " ✓")
            } else if (v == 2) {
                parts.add(name + " ✗")
            }
        }
        t("طيران", a.airplane)
        t("صامت", a.silent)
        t("واي فاي", a.wifi)
        t("بيانات", a.data)
        if (a.call.isNotEmpty()) parts.add("اتصال")
        if (a.waNumber.isNotEmpty()) parts.add("واتساب")
        return if (parts.isEmpty()) "لا شيء" else parts.joinToString(" • ")
    }

    private fun describeSchedule(s: Schedule): String {
        val parts = mutableListOf<String>()
        if (!s.limited) {
            parts.add("تعمل دايمًا")
        } else {
            val names = listOf("ح", "ن", "ث", "ر", "خ", "ج", "س")
            val days = names.filterIndexed { i, _ -> (s.days and (1 shl i)) != 0 }
            parts.add("أيام: " + (if (days.size == 7) "كل الأيام" else days.joinToString(" ")))
            parts.add(fmtTime(s.startMin) + " – " + fmtTime(s.endMin))
            if (s.startDate > 0) parts.add("من " + fmtDate(s.startDate))
            if (s.endDate > 0) parts.add("إلى " + fmtDate(s.endDate))
        }
        if (s.revertMin > 0) parts.add("رجوع بعد " + s.revertMin + " د")
        return parts.joinToString(" • ")
    }

    private fun showDetail(z: Zone) {
        if (picking) return
        map.controller.animateTo(GeoPoint(z.lat, z.lng))
        detailCard.removeAllViews()

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(tv((if (z.favorite) "⭐ " else "") + z.name, 18f, Ui.TEXT, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        val close = tv("✕", 18f, Ui.TEXT2)
        close.setPadding(dp(8), dp(4), dp(8), dp(4))
        close.setOnClickListener { detailCard.visibility = View.GONE }
        head.addView(close)
        detailCard.addView(head)

        val sub = tv("نصف القطر " + z.radius.toInt() + " م" + (if (z.enabled) "" else " • متوقفة"), 13f, Ui.TEXT2)
        sub.setPadding(0, dp(4), 0, dp(6))
        detailCard.addView(sub)
        detailCard.addView(tv("↘ عند الدخول: " + describe(z.onEnter), 13f, Ui.GREEN))
        detailCard.addView(tv("↗ عند الخروج: " + describe(z.onExit), 13f, Ui.ORANGE))
        detailCard.addView(tv("🕒 " + describeSchedule(z.schedule), 13f, Ui.TEXT2))

        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.setPadding(0, dp(12), 0, 0)
        val edit = pill("✏️ تعديل", Ui.PRIMARY, Color.WHITE)
        edit.setOnClickListener { openEditor(z.id, null) }
        val toggle = pill(if (z.enabled) "⏸ إيقاف" else "▶ تفعيل", Ui.CHIP, Ui.TEXT)
        toggle.setOnClickListener {
            val updated = z.copy(enabled = !z.enabled)
            ZoneStore.upsert(this, updated)
            zones = ZoneStore.load(this)
            refresh()
            Geo.syncIfAllowed(this)
            showDetail(updated)
        }
        val del = pill("🗑 حذف", Ui.RED_SOFT, Ui.RED)
        del.setOnClickListener { confirmDelete(z) }
        for (b in listOf(edit, toggle, del)) {
            b.textSize = 13f
            b.setPadding(dp(6), dp(10), dp(6), dp(10))
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            btns.addView(b, lp)
        }
        detailCard.addView(btns)
        detailCard.visibility = View.VISIBLE
    }

    private fun confirmDelete(z: Zone) {
        AlertDialog.Builder(this)
            .setTitle("حذف " + z.name + "؟")
            .setPositiveButton("حذف") { _, _ ->
                ZoneStore.delete(this, z.id)
                zones = ZoneStore.load(this)
                detailCard.visibility = View.GONE
                refresh()
                Geo.syncIfAllowed(this)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun openEditor(id: String?, p: GeoPoint?) {
        val i = Intent(this, ZoneEditorActivity::class.java)
        if (id != null) i.putExtra("zoneId", id)
        if (p != null) {
            i.putExtra("lat", p.latitude)
            i.putExtra("lng", p.longitude)
        }
        startActivity(i)
    }

    // ===== الصلاحيات =====
    private fun permList(): List<Perm> = listOf(
        Perm("الموقع (طوال الوقت)", "عشان يحس بدخولك وخروجك من المناطق",
            { Geo.hasPerms(this) }, { grantLocation() }),
        Perm("الاتصال", "للمكالمات التلقائية",
            { has(Manifest.permission.CALL_PHONE) },
            { requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 12) }),
        Perm("العرض فوق التطبيقات", "عشان يفتح الإعدادات وواتساب من الخلفية",
            { Settings.canDrawOverlays(this) },
            { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))) }),
        Perm("تجاهل توفير البطارية", "عشان أندرويد ما يقفلش التطبيق",
            { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) },
            { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName))) }),
        Perm("عدم الإزعاج", "للوضع الصامت",
            { getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted },
            { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }),
        Perm("إمكانية الوصول", "للواتساب والواي فاي والبيانات والطيران",
            { a11yEnabled() },
            { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
    )

    private fun permsMissing(): Boolean = permList().any { !it.granted() }

    private fun grantLocation() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 10
            )
        } else if (Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            Toast.makeText(this, "اختار السماح طوال الوقت", Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 11)
        }
    }

    private fun showPermissions() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), dp(4))
        val dlg = AlertDialog.Builder(this)
            .setTitle("الصلاحيات المطلوبة")
            .setView(box)
            .setPositiveButton("تمام", null)
            .create()
        for (p in permList()) {
            val ok = p.granted()
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(10), 0, dp(10))
            row.addView(tv(if (ok) "✅" else "⭕", 20f))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(dp(12), 0, 0, 0)
            col.addView(tv(p.title, 15f, Ui.TEXT, true))
            col.addView(tv(p.desc, 12f, Ui.TEXT2))
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            row.setOnClickListener {
                if (!ok) {
                    dlg.dismiss()
                    p.grant()
                }
            }
            box.addView(row)
        }
        dlg.show()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        startLocation()
        Geo.syncIfAllowed(this)
        refresh()
    }
}
