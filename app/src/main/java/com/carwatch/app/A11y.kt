package com.carwatch.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

data class Job(val kind: String, val on: Boolean, val number: String = "", val text: String = "")

// طابور مهام تنفذها خدمة إمكانية الوصول واحدة ورا التانية
object Jobs {
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Job>()
    private var current: Job? = null
    private var acted = false
    private var seen = false
    private var lastPkg = ""
    private var lastError = ""

    private val dataLabels = listOf(
        "Mobile data", "Mobile Data", "بيانات الجوال", "بيانات الهاتف المحمول",
        "بيانات الهاتف", "بيانات الجوّال", "البيانات الخلوية"
    )

    private fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    private fun title(j: Job): String = when (j.kind) {
        "whatsapp" -> "واتساب"
        "wifi" -> "الواي فاي"
        "data" -> "بيانات الجوال"
        else -> "وضع الطيران"
    }

    private fun err(): String {
        val e = lastError
        if (e.isEmpty()) return ""
        return " (" + (if (e.length > 90) e.substring(0, 90) else e) + ")"
    }

    fun enqueue(ctx: Context, job: Job) {
        val appCtx = ctx.applicationContext
        handler.post {
            if (job.kind == "whatsapp" && job.text.isBlank()) {
                toast(appCtx, "اكتب نص رسالة الواتساب الأول (من غير نص مفيش زر إرسال)")
            } else if (GeoAccessibilityService.instance == null) {
                toast(appCtx, "خدمة إمكانية الوصول مش شغالة، فعّل GeoMode من إعدادات إمكانية الوصول")
            } else {
                queue.addLast(job)
                if (current == null) next(appCtx)
            }
        }
    }

    private fun next(ctx: Context) {
        val j = queue.removeFirstOrNull()
        if (j == null) {
            current = null
            return
        }
        current = j
        acted = false
        seen = false
        lastPkg = ""
        lastError = ""
        if (!launch(ctx, j)) {
            if (j.kind == "whatsapp") {
                toast(ctx, "مقدرتش أفتح واتساب، اتأكد إنه متثبت" + err())
            } else {
                toast(ctx, "مقدرتش أفتح إعدادات " + title(j) + err())
            }
            current = null
            next(ctx)
            return
        }
        poll(j)
        handler.postDelayed({
            if (current === j) {
                if (seen) {
                    val what = if (j.kind == "whatsapp") "زر الإرسال" else "مفتاح التبديل"
                    toast(ctx, "ما لقيتش " + what + " في شاشة " + title(j) + " (الشاشة: " + lastPkg + ")")
                } else {
                    val shown = if (lastPkg.isEmpty()) "ولا شاشة" else lastPkg
                    toast(ctx, "شاشة " + title(j) + " ما اتفتحتش أو الخدمة مش شايفاها. آخر شاشة شفتها: " + shown)
                }
                // في الواتساب نسيب المحادثة مفتوحة عشان تضغط إرسال بإيدك
                finish(ctx, j.kind != "whatsapp")
            }
        }, 25000L)
    }

    // فحص دوري للشاشة، لأن بعض الأجهزة مبتبعتش أحداث كفاية
    private fun poll(j: Job) {
        handler.postDelayed({
            if (current === j && !acted) {
                val svc = GeoAccessibilityService.instance
                if (svc != null) onEvent(svc)
                poll(j)
            }
        }, 700L)
    }

    private fun finish(ctx: Context, back: Boolean) {
        if (back) {
            GeoAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        current = null
        next(ctx)
    }

    fun waNumber(raw: String): String {
        val d = raw.replace(Regex("[^0-9+]"), "")
        if (d.startsWith("+")) return d.substring(1)
        if (d.startsWith("00")) return d.substring(2)
        if (d.startsWith("0")) return "20" + d.substring(1)
        return d
    }

    private fun startFirst(ctx: Context, intents: List<Intent>): Boolean {
        lastError = ""
        for (i in intents) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(i)
                return true
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
            }
        }
        return false
    }

    private fun launch(ctx: Context, j: Job): Boolean {
        val list = ArrayList<Intent>()
        when (j.kind) {
            "airplane" -> {
                list.add(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                list.add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
            "wifi" -> {
                list.add(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            "data" -> {
                if (Build.VERSION.SDK_INT >= 29) list.add(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                list.add(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
                list.add(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
                list.add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
            "whatsapp" -> {
                val pkg = listOf("com.whatsapp", "com.whatsapp.w4b")
                    .firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }
                if (pkg == null) {
                    lastError = "واتساب مش متثبت"
                    return false
                }
                val phone = waNumber(j.number)
                val text = Uri.encode(j.text)
                list.add(Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=" + phone + "&text=" + text)).setPackage(pkg))
                list.add(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone + "?text=" + text)).setPackage(pkg))
            }
            else -> return false
        }
        return startFirst(ctx, list)
    }

    fun onEvent(svc: AccessibilityService) {
        val j = current ?: return
        if (acted) return
        val root = svc.rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        lastPkg = pkg
        val okPkg = if (j.kind == "whatsapp") pkg.startsWith("com.whatsapp") else pkg == "com.android.settings"
        if (!okPkg) return
        seen = true
        if (locate(j, root) == null) return
        acted = true
        handler.postDelayed({
            val r = svc.rootInActiveWindow
            val n = if (r != null) locate(j, r) else null
            val app = svc.applicationContext
            if (n == null) {
                toast(app, "اختفى الزر قبل ما أضغط عليه، جرّب تاني")
            } else if (j.kind == "whatsapp") {
                click(n)
                toast(app, "ضغطت إرسال في واتساب، راجع المحادثة")
            } else if (n.isChecked != j.on) {
                click(n)
                toast(app, "اتغيّر: " + title(j))
            } else {
                toast(app, title(j) + " كان بالحالة المطلوبة")
            }
            doneLater(svc, if (j.kind == "whatsapp") 2 else 1, j)
        }, 900L)
    }

    private fun doneLater(svc: AccessibilityService, backs: Int, j: Job) {
        handler.postDelayed({
            for (i in 0 until backs) {
                handler.postDelayed({
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }, i * 600L)
            }
            handler.postDelayed({
                if (current === j) {
                    current = null
                    next(svc.applicationContext)
                }
            }, backs * 600L + 400L)
        }, 1200L)
    }

    private fun locate(j: Job, root: AccessibilityNodeInfo): AccessibilityNodeInfo? = when (j.kind) {
        "whatsapp" -> findSend(root)
        "data" -> findDataSwitch(root)
        else -> firstCheckable(root)
    }

    private fun findSend(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (byId.isNotEmpty()) return byId[0]
        val byIdBiz = root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/send")
        if (byIdBiz.isNotEmpty()) return byIdBiz[0]
        val bySuffix = findById(root, ":id/send")
        if (bySuffix != null) return bySuffix
        for (label in listOf("Send", "إرسال")) {
            for (n in root.findAccessibilityNodeInfosByText(label)) {
                if (n.isClickable || n.contentDescription?.toString() == label) return n
            }
        }
        return null
    }

    private fun findById(n: AccessibilityNodeInfo?, suffix: String): AccessibilityNodeInfo? {
        if (n == null) return null
        val id = n.viewIdResourceName
        if (id != null && id.endsWith(suffix)) return n
        for (i in 0 until n.childCount) {
            val r = findById(n.getChild(i), suffix)
            if (r != null) return r
        }
        return null
    }

    private fun firstCheckable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isCheckable) return n
        for (i in 0 until n.childCount) {
            val r = firstCheckable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun findDataSwitch(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (label in dataLabels) {
            for (node in root.findAccessibilityNodeInfosByText(label)) {
                var p: AccessibilityNodeInfo? = node
                var depth = 0
                while (p != null && depth < 5) {
                    val sw = firstCheckable(p)
                    if (sw != null) return sw
                    p = p.parent
                    depth++
                }
            }
        }
        return null
    }

    private fun click(n: AccessibilityNodeInfo) {
        var x: AccessibilityNodeInfo? = n
        var depth = 0
        while (x != null && depth < 6) {
            if (x.isClickable) {
                x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            x = x.parent
            depth++
        }
    }
}

class GeoAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Jobs.onEvent(this)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        var instance: GeoAccessibilityService? = null
    }
}
