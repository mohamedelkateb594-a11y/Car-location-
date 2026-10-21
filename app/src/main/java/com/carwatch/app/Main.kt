package com.carwatch.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

data class Zone(
    val id: String,
    val lat: Double,
    val lng: Double,
    val radius: Float,
    val phone: String
)

const val COOLDOWN_MS = 60L * 60L * 1000L // مكالمة واحدة كل ساعة لكل منطقة

object ZoneStore {
    private const val PREFS = "zones"
    private const val KEY = "list"

    fun load(ctx: Context): MutableList<Zone> {
        val out = mutableListOf<Zone>()
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return out
        try {
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Zone(
                        o.getString("id"),
                        o.getDouble("lat"),
                        o.getDouble("lng"),
                        o.getDouble("radius").toFloat(),
                        o.getString("phone")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun save(ctx: Context, list: List<Zone>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("lat", it.lat)
            o.put("lng", it.lng)
            o.put("radius", it.radius.toDouble())
            o.put("phone", it.phone)
            arr.put(o)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}

object Geo {
    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    // يمسح المناطق القديمة ويسجل المناطق المحفوظة حاليًا
    @SuppressLint("MissingPermission")
    fun sync(ctx: Context) {
        val client = LocationServices.getGeofencingClient(ctx)
        val pi = pendingIntent(ctx)
        val zones = ZoneStore.load(ctx)
        client.removeGeofences(pi).addOnCompleteListener {
            if (zones.isNotEmpty()) {
                val fences = zones.map {
                    Geofence.Builder()
                        .setRequestId(it.id)
                        .setCircularRegion(it.lat, it.lng, it.radius)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                        .build()
                }
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(fences)
                    .build()
                client.addGeofences(request, pi)
            }
        }
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val zones = ZoneStore.load(ctx)
        val prefs = ctx.getSharedPreferences("calls", Context.MODE_PRIVATE)
        event.triggeringGeofences?.forEach { g ->
            val zone = zones.find { it.id == g.requestId } ?: return@forEach
            val last = prefs.getLong(zone.id, 0L)
            if (System.currentTimeMillis() - last < COOLDOWN_MS) return@forEach
            prefs.edit().putLong(zone.id, System.currentTimeMillis()).apply()
            if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                ctx.getSystemService(TelecomManager::class.java)
                    .placeCall(Uri.parse("tel:" + zone.phone), Bundle())
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                Geo.sync(ctx)
            } catch (_: Exception) {
            }
        }
    }
}

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
        val btnClear = Button(this)
        btnClear.text = "مسح الكل"
        btnClear.setOnClickListener { confirmClear() }
        val bp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(btnPerm, bp)
        row.addView(btnMe, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(btnClear, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
                if (p != null) askZone(p)
                return true
            }
        })

        setContentView(root)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun hasAllPerms(): Boolean =
        has(Manifest.permission.ACCESS_FINE_LOCATION) &&
            has(Manifest.permission.CALL_PHONE) &&
            (Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    @SuppressLint("MissingPermission")
    private fun startPoint(preferZones: Boolean): GeoPoint {
        if (preferZones) {
            zones.firstOrNull()?.let { return GeoPoint(it.lat, it.lng) }
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
            val circle = Polygon(map)
            circle.points = Polygon.pointsAsCircle(center, z.radius.toDouble())
            circle.fillPaint.color = Color.argb(50, 255, 0, 0)
            circle.outlinePaint.color = Color.RED
            circle.outlinePaint.strokeWidth = 3f
            circle.infoWindow = null
            map.overlays.add(circle)

            val marker = Marker(map)
            marker.position = center
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = z.phone
            marker.setOnMarkerClickListener { _, _ ->
                confirmDelete(z)
                true
            }
            map.overlays.add(marker)
        }
        map.invalidate()
        status.text = if (zones.isEmpty())
            "اضغط مطولًا على الخريطة لتحديد نقطة"
        else
            "عدد النقاط: ${zones.size} — اضغط مطولًا لإضافة نقطة، واضغط على الدبوس لحذفها"
    }

    private fun askZone(p: GeoPoint) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(48, 24, 48, 0)
        val phone = EditText(this)
        phone.hint = "رقم الزميل"
        phone.inputType = InputType.TYPE_CLASS_PHONE
        val radius = EditText(this)
        radius.hint = "نصف القطر بالمتر"
        radius.setText("200")
        radius.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(phone)
        box.addView(radius)

        AlertDialog.Builder(this)
            .setTitle("نقطة جديدة")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val number = phone.text.toString().replace(" ", "").replace("-", "")
                if (!Regex("^\\+?[0-9]{8,15}$").matches(number)) {
                    Toast.makeText(this, "رقم غير صحيح", Toast.LENGTH_LONG).show()
                } else {
                    val r = (radius.text.toString().toFloatOrNull() ?: 200f).coerceIn(50f, 5000f)
                    zones.add(Zone(System.currentTimeMillis().toString(), p.latitude, p.longitude, r, number))
                    ZoneStore.save(this, zones)
                    refresh()
                    trySync()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmDelete(z: Zone) {
        AlertDialog.Builder(this)
            .setTitle("حذف النقطة؟")
            .setMessage("الرقم: " + z.phone)
            .setPositiveButton("حذف") { _, _ ->
                zones.removeAll { it.id == z.id }
                ZoneStore.save(this, zones)
                refresh()
                trySync()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmClear() {
        if (zones.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("مسح كل النقاط؟")
            .setPositiveButton("مسح") { _, _ ->
                zones.clear()
                ZoneStore.save(this, zones)
                refresh()
                trySync()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun trySync() {
        if (hasAllPerms()) {
            try {
                Geo.sync(this)
                Toast.makeText(this, "تم التحديث ✅", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ: " + e.message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "اضغط زر الصلاحيات وفعّل كل الصلاحيات", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "اختار \"السماح طوال الوقت\" للموقع", Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "فعّل العرض فوق التطبيقات ثم ارجع واضغط الصلاحيات", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "اسمح بتجاهل توفير البطارية ثم ارجع واضغط الصلاحيات", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            return
        }
        trySync()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        step()
    }
}
