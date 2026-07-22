package com.jc.aura

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraDailyReportModule — Relatórios automáticos diários.
 * Gera resumo de tudo o que a Aura fez: leads, eventos, notas, câmbio, notícias.
 * Envia por WhatsApp, SMS ou email automaticamente.
 */
class AuraDailyReportModule(
    private val context: Context,
    private val memory: AuraMemory
) {
    fun handle(cmd: String): String {
        return when {
            cmd.contains("relatório agora") || cmd.contains("gerar relatório") || cmd.contains("report agora") -> generateAndSendReport(cmd)
            cmd.contains("agendar relatório") || cmd.contains("relatório automático") || cmd.contains("relatório diário") -> scheduleReport(cmd)
            cmd.contains("ver relatório") || cmd.contains("último relatório") -> showLastReport()
            cmd.contains("cancelar relatório") || cmd.contains("parar relatório") -> cancelScheduledReport()
            else -> generateAndSendReport(cmd)
        }
    }

    fun generateAndSendReport(cmd: String): String {
        val report = buildReport()
        memory.save("ultimo_relatorio", report)
        memory.save("ultimo_relatorio_data", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))

        // Determinar destino
        return when {
            cmd.contains("whatsapp") -> sendViaWhatsApp(report)
            cmd.contains("email") || cmd.contains("gmail") -> sendViaEmail(report)
            cmd.contains("sms") -> sendViaSMS(report)
            cmd.contains("ficheiro") || cmd.contains("guardar") || cmd.contains("salvar") -> saveToFile(report)
            else -> {
                // Por padrão: mostra e guarda no ficheiro
                saveToFile(report)
                report.take(500)
            }
        }
    }

    private fun buildReport(): String {
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val sb = StringBuilder()
        sb.appendLine("📊 RELATÓRIO AURA AGI — $date às $time")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Leads capturados
        val leads = memory.get("leads_count_today")?.toIntOrNull() ?: 0
        val leadsTotal = memory.get("leads_total")?.toIntOrNull() ?: 0
        sb.appendLine("👥 LEADS:")
        sb.appendLine("  • Hoje: $leads novos leads")
        sb.appendLine("  • Total: $leadsTotal leads acumulados")

        // Redes sociais
        val tikTokActions = memory.get("tiktok_actions_today") ?: "0"
        val instagramActions = memory.get("instagram_actions_today") ?: "0"
        val linkedinActions = memory.get("linkedin_actions_today") ?: "0"
        sb.appendLine("")
        sb.appendLine("📱 REDES SOCIAIS:")
        sb.appendLine("  • TikTok: $tikTokActions ações")
        sb.appendLine("  • Instagram: $instagramActions ações")
        sb.appendLine("  • LinkedIn: $linkedinActions ações")

        // Eventos do calendário
        val eventsToday = memory.get("events_today") ?: "Nenhum evento hoje"
        sb.appendLine("")
        sb.appendLine("📅 AGENDA:")
        sb.appendLine("  • $eventsToday")

        // Notas criadas
        val notesCount = memory.get("notes_count_today")?.toIntOrNull() ?: 0
        sb.appendLine("")
        sb.appendLine("📝 NOTAS: $notesCount criadas hoje")

        // Câmbio
        val kwanzaRate = memory.get("ultimo_cambio") ?: "N/D"
        sb.appendLine("")
        sb.appendLine("💹 CÂMBIO: $kwanzaRate")

        // Financeiro
        val expenses = memory.getExpensesToday()
        if (expenses.isNotEmpty()) {
            sb.appendLine("")
            sb.appendLine("💰 GASTOS HOJE:")
            expenses.take(5).forEach { sb.appendLine("  • ${it.first}: ${it.second} Kz") }
            sb.appendLine("  • Total: ${expenses.sumOf { it.second.toDoubleOrNull() ?: 0.0 }} Kz")
        }

        // Clientes contactados (CRM)
        val clientsContacted = memory.get("clients_contacted_today") ?: "Nenhum"
        sb.appendLine("")
        sb.appendLine("🤝 CLIENTES CONTACTADOS: $clientsContacted")

        // Resumo de atividade geral
        val commandsCount = memory.get("commands_today")?.toIntOrNull() ?: 0
        sb.appendLine("")
        sb.appendLine("🤖 ATIVIDADE:")
        sb.appendLine("  • $commandsCount comandos processados hoje")

        sb.appendLine("")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("J&C Trading Angola | Aura AGI v3.0")

        return sb.toString()
    }

    private fun sendViaWhatsApp(report: String): String {
        return try {
            val whatsappNumber = memory.get("relatorio_whatsapp") ?: ""
            val encodedMsg = Uri.encode(report.take(1000))
            val uri = if (whatsappNumber.isNotBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$whatsappNumber&text=$encodedMsg")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedMsg")
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            "Senhor, relatório enviado via WhatsApp."
        } catch (e: Exception) {
            saveToFile(report)
            "Senhor, guardei o relatório em ficheiro. Erro ao abrir WhatsApp: ${e.message}"
        }
    }

    private fun sendViaEmail(report: String): String {
        return try {
            val email = memory.get("relatorio_email") ?: ""
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                if (email.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, "Relatório Diário Aura AGI — ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}")
                putExtra(Intent.EXTRA_TEXT, report)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Senhor, relatório aberto no Gmail para envio."
        } catch (e: Exception) {
            "Senhor, erro ao abrir Gmail: ${e.message}"
        }
    }

    private fun sendViaSMS(report: String): String {
        val number = memory.get("relatorio_sms") ?: ""
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", report.take(160))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Senhor, a abrir SMS com o relatório."
    }

    private fun saveToFile(report: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, "relatorio_aura_$date.txt")
            file.writeText(report)
            "Senhor, relatório guardado em: ${file.absolutePath}"
        } catch (e: Exception) {
            "Senhor, erro ao guardar ficheiro: ${e.message}"
        }
    }

    private fun scheduleReport(cmd: String): String {
        val hour = extractHour(cmd) ?: 20 // Default: 20h
        memory.save("relatorio_hora", hour.toString())
        memory.save("relatorio_ativo", "true")

        // Destino do relatório
        when {
            cmd.contains("whatsapp") -> memory.save("relatorio_destino", "whatsapp")
            cmd.contains("email") -> memory.save("relatorio_destino", "email")
            cmd.contains("sms") -> memory.save("relatorio_destino", "sms")
            else -> memory.save("relatorio_destino", "ficheiro")
        }

        // Número de WhatsApp/email para o relatório
        val numberMatch = Regex("para o\\s+([+0-9]{8,15})").find(cmd)
        if (numberMatch != null) memory.save("relatorio_whatsapp", numberMatch.groupValues[1])

        return "Senhor, relatório automático agendado para as ${hour}h. Vou enviar todos os dias."
    }

    private fun cancelScheduledReport(): String {
        memory.save("relatorio_ativo", "false")
        return "Senhor, relatório automático cancelado."
    }

    private fun showLastReport(): String {
        val report = memory.get("ultimo_relatorio") ?: return "Senhor, ainda não foi gerado nenhum relatório hoje."
        val date = memory.get("ultimo_relatorio_data") ?: ""
        return "Último relatório ($date):\n${report.take(400)}..."
    }

    private fun extractHour(cmd: String): Int? {
        val regex = Regex("(\\d{1,2})\\s*(?:h|horas?|da manhã|da tarde|da noite)?")
        return regex.find(cmd)?.groupValues?.get(1)?.toIntOrNull()
    }
}

// Extensão para obter gastos de hoje da memória
fun AuraMemory.getExpensesToday(): List<Pair<String, String>> {
    val raw = this.get("expenses_today") ?: return emptyList()
    return try {
        raw.split(";").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
    } catch (_: Exception) { emptyList() }
}
