package com.jc.aura

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraLeadManagerModule — Gestão inteligente de leads para Mwango Brain.
 * 
 * Funcionalidades:
 * - Ver hot leads (prioridade alta)
 * - Ver todos os leads com score
 * - Gerar mensagem personalizada de follow-up
 * - Rastrear conversão lead → cliente
 * - Ver resumo do pipeline
 * - Exportar leads para CSV
 */
class AuraLeadManagerModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val mwangoBrain: AuraMwangoBrainModule
) {

    companion object {
        private const val TAG = "LeadManager"
    }

    fun handle(command: String): String {
        return try {
            when {
                command.contains("hot leads") || command.contains("leads quentes") || command.contains("melhores leads") -> {
                    getHotLeads()
                }
                command.contains("ver leads") || command.contains("todos os leads") || command.contains("listar leads") -> {
                    listAllLeads()
                }
                command.contains("pipeline") || command.contains("funil") || command.contains("resumo leads") -> {
                    getPipelineSummary()
                }
                command.contains("converter") || command.contains("conversão") || command.contains("fechou") -> {
                    convertLead(command)
                }
                command.contains("estatísticas leads") || command.contains("stats leads") -> {
                    getLeadStats()
                }
                command.contains("lembrete lead") || command.contains("lembrete para lead") -> {
                    setLeadReminder(command)
                }
                else -> {
                    "Senhor, gestão de leads disponível:\n" +
                    "• 'hot leads' — ver leads prioritários\n" +
                    "• 'ver leads' — listar todos\n" +
                    "• 'pipeline' — resumo do funil\n" +
                    "• 'converter lead [nome]'\n" +
                    "• 'estatísticas leads'\n" +
                    "• 'lembrete lead [nome] dia [data]'"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro lead manager: ${e.message}")
            "Senhor, erro na gestão de leads: ${e.message}"
        }
    }

    private fun getHotLeads(): String {
        val leads = memory.getAllByPrefix("lead_")
        val hotLeads = mutableListOf<String>()

        for ((key, value) in leads) {
            if (value.contains("HOT", ignoreCase = true) || 
                value.contains("Score: [89]") || 
                value.contains("Score: 9") || 
                value.contains("Score: 8")) {
                hotLeads.add("$key: $value")
            }
        }

        return if (hotLeads.isEmpty()) {
            "🔥 Hot Leads: Nenhum lead prioritário encontrado.\n\n" +
            "Dica: Qualifique leads com 'qualificar lead [nome]' para começar a priorizar."
        } else {
            buildString {
                appendLine("🔥 Hot Leads (${hotLeads.size} encontrados):")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                hotLeads.forEach { appendLine("  📍 $it") }
            }
        }
    }

    private fun listAllLeads(): String {
        val leads = memory.getAllByPrefix("lead_")

        return if (leads.isEmpty()) {
            "📋 Leads: Nenhum lead registado.\n\n" +
            "Dica: Adicione leads com 'qualificar lead [nome]' ou carregue um CSV."
        } else {
            buildString {
                appendLine("📋 Todos os Leads (${leads.size}):")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                leads.forEach { (key, value) ->
                    val name = key.removePrefix("lead_").replace("_", " ")
                    appendLine("  👤 $name")
                    val lines = value.split("|")
                    lines.forEach { line ->
                        val parts = line.split("=")
                        if (parts.size == 2) appendLine("     ${parts[0]}: ${parts[1]}")
                    }
                    appendLine()
                }
            }
        }
    }

    private fun getPipelineSummary(): String {
        val leads = memory.getAllByPrefix("lead_")
        val proposals = memory.get("proposals_count")?.toIntOrNull() ?: 0
        val meetings = memory.get("meetings_count")?.toIntOrNull() ?: 0
        val converted = memory.getAllByPrefix("converted_").size

        val hot = leads.values.count { it.contains("HOT", ignoreCase = true) }
        val warm = leads.values.count { it.contains("WARM", ignoreCase = true) }
        val cold = leads.values.count { it.contains("COLD", ignoreCase = true) }

        return buildString {
            appendLine("📊 Pipeline de Leads - Mwango Brain")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("  🔥 Hot: $hot")
            appendLine("  🟡 Warm: $warm")
            appendLine("  🔵 Cold: $cold")
            appendLine("  ─────────────────────")
            appendLine("  📋 Total leads: ${leads.size}")
            appendLine("  📝 Propostas enviadas: $proposals")
            appendLine("  📅 Reuniões agendadas: $meetings")
            appendLine("  ✅ Convertidos: $converted")
            appendLine()
            appendLine("  📈 Taxa de conversão: ${if (leads.isNotEmpty()) String.format("%.1f", (converted.toDouble() / leads.size) * 100) + "%" else "N/A"}")
        }
    }

    private fun convertLead(command: String): String {
        val name = extractName(command) ?: return "Senhor, especifique o lead. Ex: 'converter lead João Silva'"
        val key = "lead_${name.lowercase().replace(" ", "_")}"

        val existingLead = memory.get(key)
        if (existingLead == null) {
            return "Senhor, lead '$name' não encontrado. Use 'ver leads' para listar."
        }

        memory.save("converted_${name.lowercase().replace(" ", "_")}", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))
        memory.save("conversions_count", ((memory.get("conversions_count")?.toIntOrNull() ?: 0) + 1).toString())

        return buildString {
            appendLine("✅ Lead convertido em cliente!")
            appendLine("  👤 $name")
            appendLine("  📅 Data: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("Parabéns ao team Mwango Brain! 🎉")
        }
    }

    private fun getLeadStats(): String {
        val leads = memory.getAllByPrefix("lead_")
        val proposals = memory.get("proposals_count")?.toIntOrNull() ?: 0
        val meetings = memory.get("meetings_count")?.toIntOrNull() ?: 0
        val converted = memory.getAllByPrefix("converted_").size
        val qualified = memory.get("leads_qualified")?.toIntOrNull() ?: 0

        return buildString {
            appendLine("📈 Estatísticas de Leads")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("  📋 Leads qualificados: $qualified")
            appendLine("  📝 Propostas: $proposals")
            appendLine("  📅 Reuniões: $meetings")
            appendLine("  ✅ Conversões: $converted")
            appendLine("  ─────────────────────")
            appendLine("  💰 Prospects no CSV: ${memory.getAllByPrefix("csv_profile_").size}")
            appendLine("  📊 DMs enviados: ${memory.get("dm_campaign_sent") ?: "0"}")
            appendLine("  💬 Comentários respondidos: ${memory.get("social_reply_count") ?: "0"}")
        }
    }

    private fun setLeadReminder(command: String): String {
        val name = extractName(command) ?: return "Especifique o lead. Ex: 'lembrete lead João dia 25/07'"
        val date = Regex("\\d{1,2}[/-]\\d{1,2}").find(command)?.value ?: return "Inclua uma data. Ex: 'dia 25/07'"

        memory.save("reminder_lead_${name.lowercase().replace(" ", "_")}", "$name|$date|pendente")
        return "⏰ Lembrete definido:\n  👤 $name\n  📆 $date\n\n" +
            "A Aura vai lembrá-lo na data certa."
    }

    private fun extractName(command: String): String? {
        val patterns = listOf("lead ", "converter ", "para ")
        for (pattern in patterns) {
            val idx = command.indexOf(pattern)
            if (idx >= 0) {
                val name = command.substring(idx + pattern.length).trim()
                val words = name.split(Regex("\\s+")).take(3)
                if (words.isNotEmpty() && words[0].length > 2) return words.joinToString(" ")
            }
        }
        return null
    }
}
