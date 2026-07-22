package com.jc.aura

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraProactiveModule — Notificações e alertas proativos.
 * A Aura fala sozinha quando algo importante acontece:
 * - Câmbio favorável / alerta de câmbio
 * - Reuniões que se aproximam
 * - Follow-ups de clientes pendentes
 * - Notícias urgentes de Angola
 * - Lembrete de tarefas em atraso
 */
class AuraProactiveModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val voiceService: AuraVoiceService
) {
    fun handle(cmd: String): String {
        return when {
            cmd.contains("alerta de câmbio") || cmd.contains("avisa-me se o câmbio") -> setCurrencyAlert(cmd)
            cmd.contains("alerta de notícias") || cmd.contains("notícias urgentes") -> setNewsAlert(cmd)
            cmd.contains("lembrar reunião") || cmd.contains("lembrete de reunião") -> setMeetingReminder(cmd)
            cmd.contains("ver alertas") || cmd.contains("meus alertas") || cmd.contains("alertas ativos") -> showActiveAlerts()
            cmd.contains("desativar alertas") || cmd.contains("cancelar alertas") -> disableAlerts()
            cmd.contains("proativo") || cmd.contains("notificações proativas") -> configureProactive(cmd)
            else -> showActiveAlerts()
        }
    }

    private fun setCurrencyAlert(cmd: String): String {
        val rate = Regex("(\\d+(?:[.,]\\d+)?)").find(cmd)?.value?.replace(",", ".")?.toDoubleOrNull()
        val direction = when {
            cmd.contains("abaixo de") || cmd.contains("menos de") || cmd.contains("cair") -> "below"
            cmd.contains("acima de") || cmd.contains("mais de") || cmd.contains("subir") -> "above"
            else -> "below"
        }

        if (rate != null) {
            memory.save("alert_currency_rate", rate.toString())
            memory.save("alert_currency_direction", direction)
            memory.save("alert_currency_active", "true")
            return "Senhor, alerta de câmbio definido. Vou avisá-lo quando o Kwanza ${if (direction == "below") "estiver abaixo de" else "ultrapassar"} **$rate Kz/USD**."
        }
        return "Senhor, diga o valor. Ex: 'alerta se o câmbio cair abaixo de 800 Kz'."
    }

    private fun setNewsAlert(cmd: String): String {
        val topic = when {
            cmd.contains("angola") -> "angola"
            cmd.contains("negócios") -> "negócios"
            cmd.contains("política") -> "política"
            else -> "angola"
        }
        memory.save("alert_news_topic", topic)
        memory.save("alert_news_active", "true")
        return "Senhor, alerta de notícias urgentes de $topic ativado. Vou avisá-lo quando houver novidades importantes."
    }

    private fun setMeetingReminder(cmd: String): String {
        val minutes = Regex("(\\d+)").find(cmd)?.value?.toIntOrNull() ?: 15
        memory.save("alert_meeting_minutes", minutes.toString())
        memory.save("alert_meeting_active", "true")
        return "Senhor, vou avisá-lo **$minutes minutos antes** de cada reunião."
    }

    private fun showActiveAlerts(): String {
        val sb = StringBuilder("🔔 Alertas ativos:\n")
        if (memory.get("alert_currency_active") == "true") {
            val rate = memory.get("alert_currency_rate") ?: "N/D"
            val dir = if (memory.get("alert_currency_direction") == "below") "abaixo de" else "acima de"
            sb.appendLine("  • Câmbio $dir $rate Kz/USD")
        }
        if (memory.get("alert_news_active") == "true") {
            sb.appendLine("  • Notícias urgentes de ${memory.get("alert_news_topic") ?: "angola"}")
        }
        if (memory.get("alert_meeting_active") == "true") {
            sb.appendLine("  • Reuniões: ${memory.get("alert_meeting_minutes") ?: "15"} min antes")
        }
        if (sb.toString() == "🔔 Alertas ativos:\n") sb.append("  Nenhum alerta ativo.")
        return sb.toString()
    }

    private fun disableAlerts(): String {
        memory.save("alert_currency_active", "false")
        memory.save("alert_news_active", "false")
        memory.save("alert_meeting_active", "false")
        return "Senhor, todos os alertas proativos desativados."
    }

    private fun configureProactive(cmd: String): String {
        val enabled = !cmd.contains("desativar") && !cmd.contains("desligar")
        memory.save("proactive_mode", if (enabled) "true" else "false")
        return if (enabled) {
            "Senhor, modo proativo ativado. Vou falar sozinho quando houver algo importante."
        } else {
            "Senhor, modo proativo desativado."
        }
    }

    suspend fun checkAndAlert() = withContext(Dispatchers.IO) {
        // Verificar alerta de câmbio
        if (memory.get("alert_currency_active") == "true") {
            try {
                val targetRate = memory.get("alert_currency_rate")?.toDoubleOrNull() ?: return@withContext
                val direction = memory.get("alert_currency_direction") ?: "below"
                val currentRate = fetchCurrentKwanzaRate()
                if (currentRate != null) {
                    val shouldAlert = if (direction == "below") currentRate < targetRate else currentRate > targetRate
                    if (shouldAlert) {
                        voiceService.speak("Senhor, ALERTA! O câmbio está em $currentRate Kz por dólar. ${if (direction == "below") "Abaixo" else "Acima"} do seu limite de $targetRate!")
                        memory.save("alert_currency_active", "false") // Disparar uma vez
                    }
                }
            } catch (_: Exception) {}
        }

        // Verificar follow-ups do dia
        if (memory.get("proactive_mode") == "true") {
            val followUps = memory.getTodayFollowUps()
            if (followUps.isNotEmpty() && isWorkHour()) {
                val lastReminder = memory.get("last_followup_reminder")
                val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                if (lastReminder != today) {
                    voiceService.speak("Senhor, lembrete! Tem ${followUps.size} follow-ups para fazer hoje: ${followUps.first()}.")
                    memory.save("last_followup_reminder", today)
                }
            }
        }
    }

    private fun isWorkHour(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 8..18
    }

    private fun fetchCurrentKwanzaRate(): Double? {
        return try {
            val url = URL("https://api.exchangerate-api.com/v4/latest/USD")
            val conn = url.openConnection()
            conn.connectTimeout = 5000
            val json = conn.getInputStream().bufferedReader().readText()
            val idx = json.indexOf("\"AOA\":")
            if (idx != -1) {
                val start = idx + 6
                val end = json.indexOf(",", start).takeIf { it != -1 } ?: json.indexOf("}", start)
                json.substring(start, end).trim().toDoubleOrNull()
            } else null
        } catch (_: Exception) { null }
    }
}
