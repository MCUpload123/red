package com.smith.fireassist

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class CalibrateActivity : Activity() {

    private var markerX = -1f
    private var markerY = -1f
    private var circle: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.argb(80, 0, 0, 0))

        val hint = TextView(this).apply {
            text = "Tap the center of your FIRE button.\nThen press Save."
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(40, 80, 40, 40)
        }
        val hintLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        root.addView(hint, hintLp)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setPadding(24, 24, 24, 24)
        }
        val save = Button(this).apply {
            text = "Save"
            setOnClickListener {
                if (markerX < 0) { Toast.makeText(this@CalibrateActivity,
                    "tap first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                State.fireX = markerX.toInt()
                State.fireY = markerY.toInt()
                State.fireRadius = 90
                Toast.makeText(this@CalibrateActivity, "saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        val cancel = Button(this).apply {
            text = "Cancel"; setOnClickListener { finish() }
        }
        bar.addView(save); bar.addView(cancel)
        val barLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        root.addView(bar, barLp)

        val c = View(this)
        c.setBackgroundColor(Color.argb(120, 255, 0, 0))
        root.addView(c, FrameLayout.LayoutParams(1, 1))
        circle = c

        root.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) {
                markerX = ev.rawX
                markerY = ev.rawY
                val lp = circle!!.layoutParams as FrameLayout.LayoutParams
                lp.leftMargin = (ev.x - 45).toInt()
                lp.topMargin  = (ev.y - 45).toInt()
                lp.width = 90; lp.height = 90
                circle!!.layoutParams = lp
                // use a circular-ish look by rounding background impossible without drawable;
                // alpha red square is enough for calibration
                circle!!.setBackgroundColor(Color.argb(120, 255, 60, 60))
            }
            true
        }

        setContentView(root)
    }
}