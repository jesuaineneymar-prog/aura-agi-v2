package com.jc.aura

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView

/**
 * AuraWidgetService — Botão flutuante sempre visível.
 * Aparece em cima de todas as apps como um botão "A" azul.
 * Toque único: ativa o microfone.
 * Toque longo: abre o painel completo da Aura.
 * Arrastável para qualquer canto do ecrã.
 */
class AuraWidgetService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        var isRunning = false

        fun start(context: Context) {
            if (!isRunning) {
                val intent = Intent(context, AuraWidgetService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AuraWidgetService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
    }

    private fun createFloatingButton() {
        // Criar o botão flutuante manualmente (sem layout XML para ser simples)
        val button = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.parseColor("#00D4FF")) // Azul neon
            setBackgroundColor(Color.parseColor("#CC000000")) // Fundo escuro semitransparente
            setPadding(24, 24, 24, 24)
            contentDescription = "Aura"
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            120, 120, // Tamanho do botão
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        // Tornar arrastável
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isClick = false

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x; initialY = params!!.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) isClick = false
                    params!!.x = initialX + deltaX
                    params!!.y = initialY + deltaY
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        // Ativar microfone da Aura
                        val voiceService = AuraVoiceService.instance
                        if (voiceService != null) {
                            voiceService.startListeningManual()
                            // Piscar botão para indicar escuta
                            button.setColorFilter(Color.parseColor("#FF4444"))
                        } else {
                            // Abrir a app
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            if (intent != null) startActivity(intent)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        button.setOnLongClickListener {
            // Abrir painel principal
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent != null) startActivity(intent)
            true
        }

        floatingView = button
        windowManager?.addView(floatingView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
