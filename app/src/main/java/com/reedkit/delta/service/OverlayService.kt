package com.reedkit.delta.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.reedkit.delta.MainActivity
import com.reedkit.delta.R
import com.reedkit.delta.data.CalibrationStore
import com.reedkit.delta.data.Keys

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var calibrating = false
    private var calibIndex = 0
    private lateinit var store: CalibrationStore

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        store = CalibrationStore(this)
        startForeground(1, buildNotification())
        showPanel()
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel("overlay", "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "overlay")
            .setContentTitle("Reedkit 正在运行")
            .setContentText("点击返回应用")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .build()
    }

    private fun lp() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 40
        y = 300
    }

    private fun showPanel() {
        val params = lp()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE202124.toInt())
            setPadding(32, 24, 32, 24)
        }
        val title = TextView(this).apply {
            setTextColor(0xFFE3E3E3.toInt())
            textSize = 14f
        }
        val btnPlay = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(0xFF3D5AFE.toInt())
        }
        container.addView(title)
        container.addView(btnPlay)

        fun refresh() {
            title.text = when {
                calibrating -> {
                    val key = Keys.ALL.getOrNull(calibIndex)
                    if (key == null) "校准完成"
                    else "请点击「$key」(${calibIndex + 1}/${Keys.ALL.size})"
                }
                OverlayController.isPlaying.value -> "演奏中… 点击停止"
                else -> "就绪 — 点击开始演奏"
            }
        }
        refresh()

        btnPlay.setOnClickListener {
            if (calibrating) return@setOnClickListener
            OverlayController.toggle(this)
            refresh()
        }

        // 拖动 + 校准触摸
        var downX = 0f; var downY = 0f; var moved = false
        container.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX; val dy = ev.rawY - downY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        moved = true
                        params.x += dx.toInt(); params.y += dy.toInt()
                        downX = ev.rawX; downY = ev.rawY
                        wm.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { true }
                else -> false
            }
        }

        panel = container
        wm.addView(container, params)
        panelRefresher = { refresh() }
    }

    /** 启动校准模式：全屏透明层捕获点击 */
    fun startCalibration() {
        calibrating = true
        calibIndex = 0
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        val layer = View(this)
        layer.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) {
                val key = Keys.ALL.getOrNull(calibIndex)
                if (key != null) {
                    store.save(key, ev.rawX, ev.rawY)
                    calibIndex++
                    panelRefresher?.invoke()
                    if (calibIndex >= Keys.ALL.size) {
                        calibrating = false
                        wm.removeView(layer)
                        panelRefresher?.invoke()
                    }
                }
                true
            } else false
        }
        wm.addView(layer, params)
        panelRefresher?.invoke()
    }

    override fun onDestroy() {
        panel?.let { wm.removeView(it) }
        super.onDestroy()
    }

    companion object {
        var panelRefresher: (() -> Unit)? = null
        var instance: OverlayService? = null

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        if (intent?.action == "calibrate") startCalibration()
        return START_STICKY
    }
}