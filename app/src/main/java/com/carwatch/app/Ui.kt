package com.carwatch.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Ui {
    val PRIMARY = Color.parseColor("#1A73E8")
    val PRIMARY_SOFT = Color.parseColor("#E8F0FE")
    val GREEN = Color.parseColor("#188038")
    val ORANGE = Color.parseColor("#E8710A")
    val ORANGE_SOFT = Color.parseColor("#FEF3E8")
    val RED = Color.parseColor("#D93025")
    val RED_SOFT = Color.parseColor("#FCE8E6")
    val BG = Color.parseColor("#F3F4F6")
    val CHIP = Color.parseColor("#EEF0F3")
    val TEXT = Color.parseColor("#202124")
    val TEXT2 = Color.parseColor("#5F6368")
    val LINE = Color.parseColor("#DADCE0")
}

fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun Context.roundBg(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.setColor(color)
    g.cornerRadius = dp(radiusDp).toFloat()
    if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor)
    return g
}

fun fmtTime(m: Int): String = String.format("%02d:%02d", m / 60, m % 60)

fun fmtDate(d: Int): String =
    if (d == 0) "بدون حد" else String.format("%04d-%02d-%02d", d / 10000, (d / 100) % 100, d % 100)

fun Activity.lightBars() {
    window.statusBarColor = Color.WHITE
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
}

fun Context.tv(text: String, sizeSp: Float = 14f, color: Int = Ui.TEXT, bold: Boolean = false): TextView {
    val v = TextView(this)
    v.text = text
    v.textSize = sizeSp
    v.setTextColor(color)
    if (bold) v.setTypeface(null, Typeface.BOLD)
    return v
}

fun Context.input(hint: String, type: Int): EditText {
    val e = EditText(this)
    e.hint = hint
    e.inputType = type
    e.textSize = 14f
    e.setTextColor(Ui.TEXT)
    e.setHintTextColor(Ui.TEXT2)
    e.background = roundBg(Color.WHITE, 10, Ui.LINE, 1)
    e.setPadding(dp(12), dp(10), dp(12), dp(10))
    return e
}

fun Context.pill(text: String, bg: Int, fg: Int, elevated: Boolean = false): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = 14f
    t.setTextColor(fg)
    t.gravity = Gravity.CENTER
    t.setTypeface(null, Typeface.BOLD)
    t.background = roundBg(bg, 24)
    t.setPadding(dp(18), dp(12), dp(18), dp(12))
    if (elevated) t.elevation = dp(6).toFloat()
    return t
}

fun Context.circleButton(glyph: String, onClick: () -> Unit): TextView {
    val t = TextView(this)
    t.text = glyph
    t.textSize = 20f
    t.gravity = Gravity.CENTER
    t.setTextColor(Ui.PRIMARY)
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(Color.WHITE)
    t.background = g
    t.elevation = dp(6).toFloat()
    t.setOnClickListener { onClick() }
    return t
}

fun Context.card(): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.background = roundBg(Color.WHITE, 16)
    c.setPadding(dp(16), dp(14), dp(16), dp(16))
    c.elevation = dp(2).toFloat()
    val lp = LinearLayout.LayoutParams(MATCH, WRAP)
    lp.setMargins(dp(12), dp(6), dp(12), dp(6))
    c.layoutParams = lp
    return c
}

class Chip(ctx: Context, label: String, checked: Boolean = false, private val onChange: (Boolean) -> Unit = {}) : TextView(ctx) {
    var on: Boolean = checked
        set(v) {
            field = v
            render()
        }

    init {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
        setOnClickListener {
            on = !on
            onChange(on)
        }
        render()
    }

    private fun render() {
        background = context.roundBg(
            if (on) Ui.PRIMARY_SOFT else Ui.CHIP, 20,
            if (on) Ui.PRIMARY else Color.TRANSPARENT, 1
        )
        setTextColor(if (on) Ui.PRIMARY else Ui.TEXT2)
    }
}

class Segmented(ctx: Context, labels: List<String>, private val onChange: (Int) -> Unit = {}) : LinearLayout(ctx) {
    var choice: Int = 0
        set(v) {
            field = v
            render()
        }

    private val cells = mutableListOf<TextView>()

    init {
        orientation = HORIZONTAL
        background = ctx.roundBg(Ui.CHIP, 10)
        setPadding(ctx.dp(2), ctx.dp(2), ctx.dp(2), ctx.dp(2))
        labels.forEachIndexed { i, l ->
            val t = TextView(ctx)
            t.text = l
            t.gravity = Gravity.CENTER
            t.textSize = 12f
            t.setPadding(ctx.dp(4), ctx.dp(8), ctx.dp(4), ctx.dp(8))
            t.setOnClickListener {
                choice = i
                onChange(i)
            }
            addView(t, LayoutParams(0, WRAP, 1f))
            cells.add(t)
        }
        render()
    }

    private fun render() {
        val accent = intArrayOf(Ui.TEXT2, Ui.GREEN, Ui.RED)
        for (i in cells.indices) {
            val t = cells[i]
            val a = accent[if (i > 2) 2 else i]
            if (i == choice) {
                t.background = context.roundBg(Color.WHITE, 8, a, 1)
                t.setTextColor(a)
                t.setTypeface(null, Typeface.BOLD)
            } else {
                t.background = null
                t.setTextColor(Ui.TEXT2)
                t.setTypeface(null, Typeface.NORMAL)
            }
        }
    }
}
