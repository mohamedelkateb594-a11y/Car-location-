package com.carwatch.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val METHOD_CALL = "call"
const val METHOD_WHATSAPP = "whatsapp"

/** إجراء يتنفذ عند الدخول أو الخروج: اتصال أو رسالة واتساب */
data class Rule(
    val enabled: Boolean = false,
    val method: String = METHOD_CALL,
    val phone: String = "",
    val message: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("method", method)
        .put("phone", phone)
        .put("message", message)

    companion object {
        fun fromJson(o: JSONObject?): Rule {
            if (o == null) return Rule()
            return Rule(
                o.optBoolean("enabled", false),
                o.optString("method", METHOD_CALL),
                o.optString("phone", ""),
                o.optString("message", "")
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
    val onEnter: Rule,
    val onExit: Rule
)

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
                val name = o.optString("name", "").ifBlank { "نقطة ${i + 1}" }
                // ترحيل النسخة القديمة: كان فيها رقم واحد بس للدخول
                val enter = if (o.has("enter")) Rule.fromJson(o.optJSONObject("enter"))
                else Rule(true, METHOD_CALL, o.optString("phone", ""), "")
                val exit = Rule.fromJson(o.optJSONObject("exit"))
                out.add(
                    Zone(
                        o.getString("id"), name,
                        o.getDouble("lat"), o.getDouble("lng"),
                        o.getDouble("radius").toFloat(),
                        enter, exit
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
            o.put("name", it.name)
            o.put("lat", it.lat)
            o.put("lng", it.lng)
            o.put("radius", it.radius.toDouble())
            o.put("enter", it.onEnter.toJson())
            o.put("exit", it.onExit.toJson())
            arr.put(o)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    /** يمسح حالة "جوه/بره" المحفوظة لنقطة عشان تتقيّم من جديد بدون إطلاق إجراء */
    fun resetState(ctx: Context, id: String) {
        ctx.getSharedPreferences("state", Context.MODE_PRIVATE).edit().remove(id).apply()
    }
}

object AppPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    fun isMonitoring(ctx: Context): Boolean = p(ctx).getBoolean("monitoring", false)
    fun setMonitoring(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("monitoring", v).apply()
    }
}
