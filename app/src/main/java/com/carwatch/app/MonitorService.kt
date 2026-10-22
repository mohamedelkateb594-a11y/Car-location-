package com.carwatch.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

const val COOLDOWN_MS = 5L * 60L * 1000L // منع تكرار نفس الإجراء لنفس النقطة خلال 5 دقايق
const val EXIT_MARGIN_M = 25f // هامش عشان إشارة الـ GPS المتذبذبة ما تعملش دخول/خروج وهمي
private const val CH_MONITOR = "monitor"
private const val CH_EVENTS = "events"
private const val NOTIF_ID = 1001

/** خدمة شغالة في المقدمة بتتابع الموقع لحظيًا وتنفّذ إجراءات الدخول والخروج */
class MonitorService : Service() {

    private lateinit var client: FusedLocationProviderClient
    private var callback: LocationCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val n = monitorNotification()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        startUpdates()
        return START_STICKY
    }

    private fun startUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        callback?.let { client.removeLocationUpdates(it) }
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                handle(loc)
            }
        }
        callback = cb
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()
        try {
            client.requestLocationUpdates(req, cb, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun handle(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > 150f) return // قراءة ضعيفة، نتجاهلها
        val zones = ZoneStore.load(this)
        if (zones.isEmpty()) return
        val state = getSharedPreferences("state", Context.MODE_PRIVATE)
        val dist = FloatArray(1)
        for (z in zones) {
            Location.distanceBetween(loc.latitude, loc.longitude, z.lat, z.lng, dist)
            val d = dist[0]
            val was: Boolean? = if (state.contains(z.id)) state.getBoolean(z.id, false) else null
            val isIn = if (was == true) d <= z.radius + EXIT_MARGIN_M else d <= z.radius
            if (was == null) {
                // أول قراءة: نسجّل الحالة بس من غير ما نتصل
                state.edit().putBoolean(z.id, isIn).apply()
                continue
            }
            if (was == isIn) continue
            state.edit().putBoolean(z.id, isIn).apply()
            fire(z, if (isIn) z.onEnter else z.onExit, isIn)
        }
    }

    private fun fire(z: Zone, rule: Rule, entering: Boolean) {
        if (!rule.enabled || rule.phone.isBlank()) return
        val key = z.id + if (entering) ":in" else ":out"
        val cd = getSharedPreferences("cooldown", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - cd.getLong(key, 0L) < COOLDOWN_MS) return
        cd.edit().putLong(key, now).apply()

        val what = if (entering) "دخلت" else "خرجت من"
        val title = "العربية $what ${z.name}"
        if (rule.method == METHOD_WHATSAPP) {
            val intent = Actions.whatsappIntent(this, rule.phone, rule.message)
            try {
                startActivity(intent)
            } catch (_: Exception) {
            }
            notifyEvent(title, "اضغط لإرسال رسالة واتساب إلى ${rule.phone}", intent)
        } else {
            val ok = Actions.call(this, rule.phone)
            notifyEvent(
                title,
                if (ok) "جاري الاتصال بـ ${rule.phone}" else "تعذّر الاتصال، تأكد من صلاحية الهاتف",
                null
            )
        }
    }

    private fun notifyEvent(title: String, text: String, intent: Intent?) {
        val nm = getSystemService(NotificationManager::class.java)
        val b = Notification.Builder(this, CH_EVENTS)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        val id = (System.currentTimeMillis() % 100000L).toInt() + 2000
        if (intent != null) {
            b.setContentIntent(
                PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
        nm.notify(id, b.build())
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MONITOR, "المراقبة", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "تنبيهات النقاط", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun monitorNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH_MONITOR)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("CarWatch شغّال")
            .setContentText("بيتابع موقع العربية لحظيًا")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, MonitorService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (AppPrefs.isMonitoring(ctx)) {
                try {
                    MonitorService.start(ctx)
                } catch (_: Exception) {
                }
            }
        }
    }
}
