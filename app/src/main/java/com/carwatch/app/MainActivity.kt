package com.carwatch.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

class MainActivity : Activity() {
    private lateinit var map: MapView
    private lateinit var status: TextView
    private lateinit var eventsOverlay: MapEventsOverlay
    private var zones: MutableList<Zone> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cfg = Configuration.getInstance()
        cfg.userAgentValue = packageName
        cfg.osmdroidBasePath = File(cacheDir, "osm")
        cfg.osmdroidTileCache = File(cacheDir, "osm/tiles")

        zones = ZoneStore.load(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        status = TextView(this)
        status.textSize = 14f
        status.setPadding(24, 24, 24, 12)
        root.addView(status)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val btnPerm = Button(this)
        btnPerm.text = "الصلاحيات"
        btnPerm.setOnClickListener { step() }
        val btnMe = Button(this)
        btnMe.text = "موقعي"
        btnMe.setOnClickListener { map.controller.animateTo(startPoint(false)) }
        val btnPlaces = Button(this)
        btnPlaces.text = "الأماكن"
        btnPlaces.setOnClickListener { showPlaces() }
        row.addView(btnPerm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(btnMe, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(btnPlaces, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        map = MapView(this)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(14.0)
        map.controller.setCenter(startPoint(true))
        root.addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) openEditor(null, p)
                return true
            }
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        zones = ZoneStore.load(this)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun a11yEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains(GeoAccessibilityService::class.java.name)
    }

    @SuppressLint("MissingPermission")
    private fun startPoint(preferZones: Boolean): GeoPoint {
        if (preferZones) {
            val z = zones.firstOrNull()
            if (z != null) return GeoPoint(z.lat, z.lng)
        }
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

    private fun refresh() {
        map.overlays.clear()
        map.overlays.add(eventsOverlay)
        zones.forEach { z ->
            val center = GeoPoint(z.lat, z.lng)
            val color = if (z.enabled) Color.RED else Color.GRAY
            val circle = Polygon(map)
            circle.points = Polygon.pointsAsCircle(center, z.radius.toDouble())
            circle.fillPaint.color = Color.argb(50, Color.red(color), Color.green(color), Color.blue(color))
            circle.outlinePaint.color = color
            circle.outlinePaint.strokeWidth = 3f
            circle.infoWindow = null
            map.overlays.add(circle)

            val marker = Marker(map)
            marker.position = center
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = (if (z.favorite) "⭐ " else "") + z.name
            marker.setOnMarkerClickListener { _, _ ->
                zoneMenu(z)
                true
            }
            map.overlays.add(marker)
        }
        map.invalidate()

        var s = if (zones.isEmpty()) {
            "اضغط مطولًا على الخريطة لتحديد مكان"
        } else {
            "عدد الأماكن: " + zones.size + " — اضغط مطولًا لإضافة مكان، واضغط على الدبوس للتعديل"
        }
        if (!a11yEnabled()) {
            s += "\n⚠️ إمكانية الوصول غير مفعّلة (مطلوبة للواتساب والواي فاي والبيانات والطيران)"
        }
        status.text = s
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

    private fun zoneMenu(z: Zone) {
        AlertDialog.Builder(this)
            .setTitle(z.name)
            .setItems(arrayOf("عرض على الخريطة", "تعديل", "حذف")) { _, which ->
                if (which == 0) {
                    map.controller.animateTo(GeoPoint(z.lat, z.lng))
                } else if (which == 1) {
                    openEditor(z.id, null)
                } else {
                    confirmDelete(z)
                }
            }
            .show()
    }

    private fun confirmDelete(z: Zone) {
        AlertDialog.Builder(this)
            .setTitle("حذف " + z.name + "؟")
            .setPositiveButton("حذف") { _, _ ->
                ZoneStore.delete(this, z.id)
                zones = ZoneStore.load(this)
                refresh()
                Geo.syncIfAllowed(this)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showPlaces() {
        if (zones.isEmpty()) {
            Toast.makeText(this, "مفيش أماكن محفوظة لسه", Toast.LENGTH_SHORT).show()
            return
        }
        val sorted = zones.sortedWith(compareByDescending<Zone> { it.favorite }.thenBy { it.name })
        val labels = sorted.map {
            (if (it.favorite) "⭐ " else "") + it.name + (if (it.enabled) "" else " (متوقفة)")
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("الأماكن المحفوظة")
            .setItems(labels) { _, i -> zoneMenu(sorted[i]) }
            .show()
    }

    private fun trySync() {
        if (Geo.hasPerms(this)) {
            try {
                Geo.sync(this)
                Toast.makeText(this, "تم تفعيل المراقبة ✅", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ: " + e.message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "فعّل صلاحية الموقع طوال الوقت من زر الصلاحيات", Toast.LENGTH_LONG).show()
        }
    }

    private fun step() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) || !has(Manifest.permission.CALL_PHONE)) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CALL_PHONE
                ), 1
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            Toast.makeText(this, "اختار السماح طوال الوقت للموقع", Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "فعّل العرض فوق التطبيقات ثم ارجع واضغط الصلاحيات", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "اسمح بتجاهل توفير البطارية ثم ارجع واضغط الصلاحيات", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName)))
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "فعّل GeoMode في عدم الإزعاج (للوضع الصامت) ثم ارجع واضغط الصلاحيات", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        if (!a11yEnabled()) {
            Toast.makeText(this, "فعّل GeoMode من إمكانية الوصول ثم ارجع واضغط الصلاحيات", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        trySync()
        refresh()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        step()
    }
}
