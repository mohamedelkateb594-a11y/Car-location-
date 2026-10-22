package com.carwatch.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager

object Actions {

    /** يحوّل الرقم لصيغة دولية بدون + (مطلوبة للواتساب). الرقم اللي يبدأ بـ 0 بيتعامل معاه كرقم مصري (20) */
    fun normalizeIntl(phone: String): String {
        val t = phone.trim()
        val digits = t.filter { it.isDigit() }
        return when {
            t.startsWith("+") -> digits
            digits.startsWith("00") -> digits.substring(2)
            digits.startsWith("0") -> "20" + digits.substring(1)
            else -> digits
        }
    }

    @SuppressLint("MissingPermission")
    fun call(ctx: Context, phone: String): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            ctx.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", phone, null), Bundle())
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Intent يفتح محادثة واتساب مع الرقم والرسالة جاهزة */
    fun whatsappIntent(ctx: Context, phone: String, message: String): Intent {
        val uri = Uri.parse("https://wa.me/" + normalizeIntl(phone) + "?text=" + Uri.encode(message))
        val i = Intent(Intent.ACTION_VIEW, uri)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                ctx.packageManager.getPackageInfo(pkg, 0)
                i.setPackage(pkg)
                break
            } catch (_: Exception) {
            }
        }
        return i
    }
}
