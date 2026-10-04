package dev.angelic.angel.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    val background = Color.rgb(16, 25, 28)
    val surface = Color.rgb(27, 39, 43)
    val mint = Color.rgb(152, 220, 202)
    val muted = Color.rgb(159, 181, 183)
    fun dp(context: Context, value: Int): Int = (context.resources.displayMetrics.density * value).toInt()
    fun shape(color: Int, radius: Float = 22f) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    fun text(context: Context, value: String, size: Float = 16f, color: Int = Color.WHITE) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); setLineSpacing(dp(context, 3).toFloat(), 1f)
    }
    fun heading(context: Context, value: String) = text(context, value, 26f).apply { setTypeface(typeface, Typeface.BOLD) }
    fun button(context: Context, value: String, action: () -> Unit) = Button(context).apply {
        text = value; isAllCaps = false; textSize = 13f; setTextColor(mint)
        minHeight = dp(context, 48); minimumHeight = dp(context, 48)
        setOnClickListener { action() }
    }
    fun add(parent: LinearLayout, child: View, gap: Int = 8) {
        parent.addView(child, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(parent.context, gap) })
    }
}
