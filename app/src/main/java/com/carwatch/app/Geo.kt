package com.carwatch.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.util.Calendar

object Geo {
    fun hasPerms(ctx: Context): Boolean {
        val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bg = Build.VERSION.SDK_INT < 29 ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine && bg
    }

    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    // يمسح المناطق القديمة ويسجل الأماكن المفعّلة المحفوظة حاليًا (دخول وخروج)
    @SuppressLint("MissingPermission")
    fun sync(ctx: Context) {
        val client = LocationServices.getGeofencingClient(ctx)
        val pi = pendingIntent(ctx)
        val zones = ZoneStore.load(ctx).filter { it.enabled }
        client.removeGeofences(pi).addOnCompleteListener { _ ->
            if (zones.isNotEmpty()) {
                val fences = zones.map { z ->
                    Geofence.Builder()
                        .setRequestId(z.id)
                        .setCircularRegion(z.lat, z.lng, z.radius)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                        .build()
                }
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(0)
                    .addGeofences(fences)
                    .build()
                client.addGeofences(request, pi)
            }
        }
    }

    fun syncIfAllowed(ctx: Context) {
        if (hasPerms(ctx)) {
            try {
                sync(ctx)
            } catch (_: Exception) {
            }
        }
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val t = event.geofenceTransition
        if (t != Geofence.GEOFENCE_TRANSITION_ENTER && t != Geofence.GEOFENCE_TRANSITION_EXIT) return
        val entering = t == Geofence.GEOFENCE_TRANSITION_ENTER
        val zones = ZoneStore.load(ctx)
        event.triggeringGeofences?.forEach { g ->
            val zone = zones.find { it.id == g.requestId } ?: return@forEach
            if (zone.enabled) Actuator.run(ctx, zone, entering, false)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Geo.syncIfAllowed(ctx)
        }
    }
}

// يرجّع الإعدادات للحالة السابقة بعد المدة المحددة
class RevertReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("zone") ?: return
        val entering = intent.getBooleanExtra("entering", true)
        val zone = ZoneStore.load(ctx).find { it.id == id } ?: return
        val a = if (entering) zone.onEnter else zone.onExit
        Actuator.execute(ctx, a.inverse(), zone, "revert", true)
    }
}

object Actuator {
    fun run(ctx: Context, zone: Zone, entering: Boolean, force: Boolean) {
        if (!force && !zone.schedule.allows(Calendar.getInstance())) return
        val a = if (entering) zone.onEnter else zone.onExit
        execute(ctx, a, zone, if (entering) "enter" else "exit", force)
        if (zone.schedule.revertMin > 0 && a.hasToggles()) {
            scheduleRevert(ctx.applicationContext, zone.id, entering, zone.schedule.revertMin)
        }
    }

    fun execute(ctx: Context, a: Actions, zone: Zone, tag: String, force: Boolean) {
        val appCtx = ctx.applicationContext

        if (a.silent != 0) setSilent(appCtx, a.silent == 1)

        // الاتصال والواتساب الأول (قبل ما الشبكة تتقفل)، مع فترة انتظار ساعة لمنع التكرار
        val outgoing = a.call.isNotEmpty() || a.waNumber.isNotEmpty()
        if (outgoing) {
            val prefs = appCtx.getSharedPreferences("calls", Context.MODE_PRIVATE)
            val key = zone.id + ":" + tag
            val last = prefs.getLong(key, 0L)
            if (force || System.currentTimeMillis() - last >= COOLDOWN_MS) {
                prefs.edit().putLong(key, System.currentTimeMillis()).apply()
                if (a.waNumber.isNotEmpty()) Jobs.enqueue(appCtx, Job("whatsapp", true, a.waNumber, a.waText))
                if (a.call.isNotEmpty()) placeCall(appCtx, a.call)
            }
        }

        if (a.wifi != 0) Jobs.enqueue(appCtx, Job("wifi", a.wifi == 1))
        if (a.data != 0) Jobs.enqueue(appCtx, Job("data", a.data == 1))
        // وضع الطيران آخر حاجة لأنه بيقفل الشبكة
        if (a.airplane != 0) Jobs.enqueue(appCtx, Job("airplane", a.airplane == 1))
    }

    private fun setSilent(ctx: Context, on: Boolean) {
        try {
            val am = ctx.getSystemService(AudioManager::class.java)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (on) {
                am.ringerMode = if (nm.isNotificationPolicyAccessGranted) {
                    AudioManager.RINGER_MODE_SILENT
                } else {
                    AudioManager.RINGER_MODE_VIBRATE
                }
            } else {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun placeCall(ctx: Context, number: String) {
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return
        try {
            ctx.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (_: Exception) {
        }
    }

    private fun scheduleRevert(ctx: Context, zoneId: String, entering: Boolean, minutes: Int) {
        try {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val i = Intent(ctx, RevertReceiver::class.java)
            i.putExtra("zone", zoneId)
            i.putExtra("entering", entering)
            val pi = PendingIntent.getBroadcast(
                ctx, (zoneId + entering).hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60000L, pi)
        } catch (_: Exception) {
        }
    }
}
