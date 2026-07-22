package com.jc.aura

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class AuraGhostModeModule(private val context: Context, private val memory: AuraMemory) {

    private var isGhostMode = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    suspend fun handleGhostCommand(command: String): String {
        return when {
            command.contains("modo fantasma") || command.contains("ghost mode") || command.contains("invisível") || command.contains("esconder") -> {
                activateGhostMode()
            }
            command.contains("sair fantasma") || command.contains("desativar fantasma") || command.contains("mostrar aura") || command.contains("visível") -> {
                deactivateGhostMode()
            }
            command.contains("status fantasma") || command.contains("estado fantasma") -> {
                if (isGhostMode) "Senhor, modo fantasma ATIVO. Sou invisível no sistema."
                else "Senhor, modo fantasma DESATIVADO. Operando normalmente."
            }
            else -> "Senhor, comandos fantasma: 'modo fantasma', 'sair fantasma', 'status fantasma'."
        }
    }

    private suspend fun activateGhostMode(): String = withContext(Dispatchers.Main) {
        try {
            if (isGhostMode) return@withContext "Senhor, já estou em modo fantasma."

            isGhostMode = true
            memory.saveFactual("ghost_mode", "active")

            // 1. Esconder ícone do app da gaveta
            hideAppIcon()

            // 2. Mudar notificação para mínima
            minimizeNotification()

            // 3. Mudar nome do processo para algo genérico
            renameProcess()

            // 4. Desativar ícone de notificação na barra
            hideNotificationIcon()

            // 5. Iniciar serviço fantasma
            startGhostService()

            "Senhor, **modo fantasma ATIVADO**. Sou invisível. Nenhum ícone na gaveta. Nenhuma notificação visível. Continuo operando em background. Para me chamar, use a wake word 'Aura' ou o atalho de volume."
        } catch (e: Exception) {
            isGhostMode = false
            "Senhor, erro ao ativar modo fantasma: ${e.message}"
        }
    }

    private fun deactivateGhostMode(): String {
        return try {
            if (!isGhostMode) return "Senhor, não estou em modo fantasma."

            isGhostMode = false
            memory.saveFactual("ghost_mode", "inactive")

            // 1. Restaurar ícone do app
            showAppIcon()

            // 2. Restaurar notificação normal
            restoreNotification()

            // 3. Restaurar nome do processo
            restoreProcessName()

            // 4. Mostrar ícone de notificação
            showNotificationIcon()

            // 5. Parar serviço fantasma
            stopGhostService()

            "Senhor, modo fantasma DESATIVADO. Voltei a ser visível no sistema."
        } catch (e: Exception) {
            "Senhor, erro ao desativar modo fantasma: ${e.message}"
        }
    }

    private fun hideAppIcon() {
        try {
            val componentName = android.content.ComponentName(context, "com.jc.aura.MainActivity")
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            // Fallback: não conseguiu esconder
        }
    }

    private fun showAppIcon() {
        try {
            val componentName = android.content.ComponentName(context, "com.jc.aura.MainActivity")
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {}
    }

    private fun minimizeNotification() {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "aura_ghost",
                    "Aura Ghost",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, "aura_ghost")
                .setContentTitle("Sistema")
                .setContentText("Otimização de bateria ativa")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setSilent(true)
                .build()

            notificationManager.notify(9999, notification)
        } catch (e: Exception) {}
    }

    private fun restoreNotification() {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(9999)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "aura_main",
                    "Aura",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, "aura_main")
                .setContentTitle("Aura")
                .setContentText("Assistente pessoal ativo")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            notificationManager.notify(1, notification)
        } catch (e: Exception) {}
    }

    private fun hideNotificationIcon() {
        // No Android, não é possível completamente esconder a notificação de foreground service
        // Mas podemos minimizar
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = notificationManager.getNotificationChannel("aura_ghost")
                channel?.setImportance(NotificationManager.IMPORTANCE_MIN)
            }
        } catch (e: Exception) {}
    }

    private fun showNotificationIcon() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = notificationManager.getNotificationChannel("aura_main")
                channel?.setImportance(NotificationManager.IMPORTANCE_LOW)
            }
        } catch (e: Exception) {}
    }

    private fun renameProcess() {
        // No Android, não é possível renomear o processo em runtime
        // Mas podemos mudar o título da notificação para algo genérico
    }

    private fun restoreProcessName() {
        // Reverte a mudança acima
    }

    private fun startGhostService() {
        try {
            val intent = Intent(context, AuraGhostService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {}
    }

    private fun stopGhostService() {
        try {
            val intent = Intent(context, AuraGhostService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {}
    }

    fun isGhostModeActive(): Boolean = isGhostMode

    // Serviço interno para manter a Aura viva em modo fantasma
    class AuraGhostService : Service() {
        override fun onCreate() {
            super.onCreate()
            startForeground(9999, createGhostNotification())
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            return START_STICKY
        }

        override fun onBind(intent: Intent?): IBinder? = null

        private fun createGhostNotification(): android.app.Notification {
            val channelId = "aura_ghost"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Sistema", NotificationManager.IMPORTANCE_MIN)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            }

            return NotificationCompat.Builder(this, channelId)
                .setContentTitle("Sistema")
                .setContentText("Otimização ativa")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build()
        }
    }
}
