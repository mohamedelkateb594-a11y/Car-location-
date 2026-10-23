package com.carwatch.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// مكالمة أو رسالة واتساب واحدة كل ساعة لكل مكان وحدث
const val COOLDOWN_MS = 60L * 60L * 1000L

// قيم التبديل: 0 بدون تغيير، 1 تفعيل، 2 إلغاء التفعيل
data class Actions(
    val airplane: Int = 0,
    val silent: Int = 0,
    val wifi: Int = 0,
    val data: Int = 0,
    val call: String = "",
    val waNumber: String = "",
    val waText: String = ""
) {
    fun hasToggles(): Boolean = airplane != 0 || silent != 0 || wifi != 0 || data != 0

    fun inverse(): Actions = Actions(flip(airplane), flip(silent), flip(wifi), flip(data))

    private fun flip(v: Int): Int = when (v) {
        1 -> 2
        2 -> 1
        else -> 0
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("airplane", airplane)
        o.put("silent", silent)
        o.put("wifi", wifi)
        o.put("data", data)
        o.put("call", call)
        o.put("waNumber", waNumber)
        o.put("waText", waText)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject?): Actions {
            if (o == null) return Actions()
            return Actions(
                o.optInt("airplane", 0),
                o.optInt("silent", 0),
                o.optInt("wifi", 0),
                o.optInt("data", 0),
                o.optString("call", ""),
                o.optString("waNumber", ""),
                o.optString("waText", "")
            )
        }
    }
}

data class Schedule(
    val limited: Boolean = false,
    val days: Int = 127,
    val startMin: Int = 8 * 60,
    val endMin: Int = 17 * 60,
    val startDate: Int = 0,
    val endDate: Int = 0,
    val revertMin: Int = 0
) {
    fun allows(c: Calendar): Boolean {
        if (!limited) return true
        val date = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
        if (startDate > 0 && date < startDate) return false
        if (endDate > 0 && date > endDate) return false
        val dow = c.get(Calendar.DAY_OF_WEEK) - 1
        if ((days and (1 shl dow)) == 0) return false
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (startMin <= endMin) m in startMin..endMin else (m >= startMin || m <= endMin)
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("limited", limited)
        o.put("days", days)
        o.put("startMin", startMin)
        o.put("endMin", endMin)
        o.put("startDate", startDate)
        o.put("endDate", endDate)
        o.put("revertMin", revertMin)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject?): Schedule {
            if (o == null) return Schedule()
            return Schedule(
                o.optBoolean("limited", false),
                o.optInt("days", 127),
                o.optInt("startMin", 8 * 60),
                o.optInt("endMin", 17 * 60),
                o.optInt("startDate", 0),
                o.optInt("endDate", 0),
                o.optInt("revertMin", 0)
            )
        }
    }
}

data class Zone(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radius: Float,
    val favorite: Boolean,
    val enabled: Boolean,
    val onEnter: Actions,
    val onExit: Actions,
    val schedule: Schedule
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("lat", lat)
        o.put("lng", lng)
        o.put("radius", radius.toDouble())
        o.put("favorite", favorite)
        o.put("enabled", enabled)
        o.put("onEnter", onEnter.toJson())
        o.put("onExit", onExit.toJson())
        o.put("schedule", schedule.toJson())
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Zone {
            // دعم النسخة القديمة اللي كانت بتحفظ رقم الاتصال فقط
            val legacyPhone = o.optString("phone", "")
            val enter = if (o.has("onEnter")) Actions.fromJson(o.optJSONObject("onEnter")) else Actions(call = legacyPhone)
            val defaultName = if (legacyPhone.isNotEmpty()) legacyPhone else "نقطة"
            return Zone(
                o.getString("id"),
                o.optString("name", defaultName),
                o.getDouble("lat"),
                o.getDouble("lng"),
                o.optDouble("radius", 200.0).toFloat(),
                o.optBoolean("favorite", false),
                o.optBoolean("enabled", true),
                enter,
                Actions.fromJson(o.optJSONObject("onExit")),
                Schedule.fromJson(o.optJSONObject("schedule"))
            )
        }
    }
}

object ZoneStore {
    private const val PREFS = "zones"
    private const val KEY = "list"

    fun load(ctx: Context): MutableList<Zone> {
        val out = mutableListOf<Zone>()
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return out
        try {
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                try {
                    out.add(Zone.fromJson(arr.getJSONObject(i)))
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun save(ctx: Context, list: List<Zone>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun upsert(ctx: Context, z: Zone) {
        val l = load(ctx)
        val i = l.indexOfFirst { it.id == z.id }
        if (i >= 0) l[i] = z else l.add(z)
        save(ctx, l)
    }

    fun delete(ctx: Context, id: String) {
        val l = load(ctx)
        l.removeAll { it.id == id }
        save(ctx, l)
    }
}
