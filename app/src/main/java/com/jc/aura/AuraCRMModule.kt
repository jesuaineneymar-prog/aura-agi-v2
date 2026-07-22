package com.jc.aura

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraCRMModule — CRM integrado para J&C Trading Angola.
 * Regista clientes, histórico de contactos, follow-ups, pipeline de vendas.
 * Tudo guardado localmente com alertas de follow-up por voz.
 */
class AuraCRMModule(
    private val context: Context,
    private val memory: AuraMemory
) {
    fun handle(cmd: String): String {
        return when {
            cmd.contains("adicionar cliente") || cmd.contains("novo cliente") || cmd.contains("cadastrar cliente") -> addClient(cmd)
            cmd.contains("ver cliente") || cmd.contains("procurar cliente") || cmd.contains("pesquisar cliente") -> searchClient(cmd)
            cmd.contains("contactei") || cmd.contains("falei com") || cmd.contains("reunião com") || cmd.contains("liguei para") -> logContact(cmd)
            cmd.contains("follow-up") || cmd.contains("followup") || cmd.contains("agendar contacto") -> scheduleFollowUp(cmd)
            cmd.contains("pipeline") || cmd.contains("funil") || cmd.contains("oportunidades") -> showPipeline()
            cmd.contains("clientes hoje") || cmd.contains("quem contactar hoje") || cmd.contains("agenda de clientes") -> getTodayClients()
            cmd.contains("estatísticas crm") || cmd.contains("resumo crm") || cmd.contains("relatório clientes") -> getCRMStats()
            cmd.contains("proposta para") || cmd.contains("enviar proposta") -> sendProposal(cmd)
            cmd.contains("fechar negócio") || cmd.contains("negócio fechado") || cmd.contains("venda fechada") -> closeDeal(cmd)
            cmd.contains("perdi o cliente") || cmd.contains("cliente perdido") -> loseClient(cmd)
            cmd.contains("listar clientes") || cmd.contains("todos os clientes") || cmd.contains("ver todos") -> listAllClients()
            else -> getCRMStats()
        }
    }

    private fun addClient(cmd: String): String {
        val name = extractAfter(cmd, listOf("cliente ", "cadastrar ", "adicionar ", "novo "))
            ?.split(" ")?.take(3)?.joinToString(" ")?.trim()
            ?: return "Senhor, diga o nome do cliente. Ex: 'novo cliente Empresa XYZ'."

        val id = "client_${System.currentTimeMillis()}"
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        // Detectar informações adicionais
        val phoneMatch = Regex("(\\+?[0-9]{8,15})").find(cmd)
        val phone = phoneMatch?.value ?: ""
        val emailMatch = Regex("[a-zA-Z0-9.]+@[a-zA-Z0-9.]+\\.[a-zA-Z]{2,}").find(cmd)
        val email = emailMatch?.value ?: ""

        val stage = when {
            cmd.contains("lead") -> "lead"
            cmd.contains("contactado") -> "contactado"
            cmd.contains("proposta") -> "proposta"
            cmd.contains("negociação") -> "negociação"
            else -> "lead"
        }

        memory.save(id, "nome:$name|phone:$phone|email:$email|stage:$stage|data:$date|notas:")
        memory.save("client_name_$id", name)

        val totalClients = (memory.get("total_clients")?.toIntOrNull() ?: 0) + 1
        memory.save("total_clients", totalClients.toString())

        return "Senhor, cliente **$name** adicionado ao CRM. Estágio: $stage.${if (phone.isNotBlank()) " Telefone: $phone." else ""}"
    }

    private fun searchClient(cmd: String): String {
        val query = extractAfter(cmd, listOf("ver cliente ", "procurar cliente ", "pesquisar cliente ", "cliente "))
            ?: return "Senhor, diga o nome do cliente."

        val results = memory.getAllByPrefix("client_").entries.filter { (_, value) ->
            value.contains(query, ignoreCase = true)
        }.take(5).associate { it.key to it.value }
        return if (results.isEmpty()) {
            "Senhor, não encontrei nenhum cliente com '$query'."
        } else {
            val sb = StringBuilder("Clientes encontrados:\n")
            results.forEach { (id, data) ->
                val parsed = parseClientData(data)
                sb.appendLine("• ${parsed["nome"]} | Estágio: ${parsed["stage"]} | Último contacto: ${memory.get("last_contact_$id") ?: "nunca"}")
            }
            sb.toString()
        }
    }

    private fun logContact(cmd: String): String {
        val clientName = extractContactedClient(cmd) ?: return "Senhor, diga o nome do cliente. Ex: 'contactei Empresa XYZ'."
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val note = extractAfter(cmd, listOf("sobre ", "discutimos ", "falamos ")) ?: "Contacto realizado"

        val clientId = findClientIdByName(clientName)
        if (clientId != null) {
            memory.save("last_contact_$clientId", date)
            memory.save("contact_note_${System.currentTimeMillis()}", "$clientId|$date|$note")
        }

        // Atualizar contador de clientes contactados hoje
        val contacted = memory.get("clients_contacted_today") ?: ""
        memory.save("clients_contacted_today", if (contacted.isBlank()) clientName else "$contacted, $clientName")

        return "Senhor, contacto com **$clientName** registado em $date. Nota: $note."
    }

    private fun scheduleFollowUp(cmd: String): String {
        val clientName = extractContactedClient(cmd) ?: return "Senhor, diga o cliente. Ex: 'follow-up com Empresa XYZ em 3 dias'."
        val days = extractNumber(cmd) ?: 3
        val futureDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(futureDate.time)

        memory.save("followup_${System.currentTimeMillis()}", "$clientName|$dateStr")

        return "Senhor, follow-up com **$clientName** agendado para **$dateStr** (em $days dias). Vou lembrar."
    }

    private fun showPipeline(): String {
        val stages = mapOf(
            "lead" to "🔵 Leads",
            "contactado" to "🟡 Contactados",
            "proposta" to "🟠 Proposta Enviada",
            "negociação" to "🔴 Em Negociação",
            "fechado" to "✅ Fechados",
            "perdido" to "❌ Perdidos"
        )
        val sb = StringBuilder("📊 PIPELINE J&C TRADING:\n")
        for ((stage, label) in stages) {
            val count = memory.countClientsInStage(stage)
            sb.appendLine("  $label: $count clientes")
        }
        return sb.toString()
    }

    private fun getTodayClients(): String {
        val followUps = memory.getTodayFollowUps()
        return if (followUps.isEmpty()) {
            "Senhor, não tem follow-ups agendados para hoje. Bom momento para prospectar novos clientes!"
        } else {
            val sb = StringBuilder("📋 CLIENTES PARA CONTACTAR HOJE:\n")
            followUps.forEach { sb.appendLine("  • $it") }
            sb.toString()
        }
    }

    private fun getCRMStats(): String {
        val total = memory.get("total_clients")?.toIntOrNull() ?: 0
        val closedDeals = memory.get("deals_closed")?.toIntOrNull() ?: 0
        val totalRevenue = memory.get("total_revenue")?.toDoubleOrNull() ?: 0.0
        val contactedToday = memory.get("clients_contacted_today") ?: "Nenhum"

        return buildString {
            appendLine("📊 CRM J&C TRADING:")
            appendLine("  • Total de clientes: $total")
            appendLine("  • Negócios fechados: $closedDeals")
            appendLine("  • Receita gerada: ${String.format("%.0f", totalRevenue)} Kz")
            appendLine("  • Contactados hoje: $contactedToday")
            val pending = memory.countClientsInStage("proposta")
            if (pending > 0) appendLine("  ⚠️ $pending propostas aguardam resposta")
        }
    }

    private fun sendProposal(cmd: String): String {
        val client = extractContactedClient(cmd) ?: return "Senhor, diga o cliente."
        val clientId = findClientIdByName(client)
        val phone = if (clientId != null) {
            val data = memory.get(clientId) ?: ""
            parseClientData(data)["phone"] ?: ""
        } else ""

        if (phone.isNotBlank()) {
            // Abrir WhatsApp com proposta
            val proposalText = Uri.encode("Olá! Conforme combinado, segue em anexo a proposta da J&C Trading Angola. Estamos ao dispor para qualquer esclarecimento.")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=$proposalText")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            updateClientStage(clientId, "proposta")
            return "Senhor, a abrir WhatsApp para enviar proposta a **$client**. Estágio atualizado para 'Proposta'."
        }

        updateClientStage(clientId, "proposta")
        return "Senhor, estágio de **$client** atualizado para 'Proposta Enviada'. Adicione o contato para envio automático."
    }

    private fun closeDeal(cmd: String): String {
        val client = extractContactedClient(cmd) ?: return "Senhor, diga o cliente."
        val value = extractAmount(cmd)
        val clientId = findClientIdByName(client)
        updateClientStage(clientId, "fechado")
        if (value != null) {
            val total = (memory.get("total_revenue")?.toDoubleOrNull() ?: 0.0) + value
            memory.save("total_revenue", total.toString())
        }
        val deals = (memory.get("deals_closed")?.toIntOrNull() ?: 0) + 1
        memory.save("deals_closed", deals.toString())
        return "🎉 Senhor, negócio com **$client** fechado!${if (value != null) " Valor: ${String.format("%.0f", value)} Kz." else ""} Parabéns!"
    }

    private fun loseClient(cmd: String): String {
        val client = extractContactedClient(cmd) ?: return "Senhor, diga o cliente."
        val clientId = findClientIdByName(client)
        updateClientStage(clientId, "perdido")
        return "Senhor, **$client** marcado como perdido. Vou registar para análise futura."
    }

    private fun listAllClients(): String {
        val total = memory.get("total_clients")?.toIntOrNull() ?: 0
        return "Senhor, tem $total clientes no CRM. Diga 'procurar cliente [nome]' para ver detalhes."
    }

    // Helpers
    private fun updateClientStage(clientId: String?, stage: String) {
        if (clientId == null) return
        val data = memory.get(clientId) ?: return
        memory.save(clientId, data.replace(Regex("stage:[a-z]+"), "stage:$stage"))
    }

    private fun parseClientData(data: String): Map<String, String> {
        return data.split("|").associate {
            val parts = it.split(":", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }
    }

    private fun findClientIdByName(name: String): String? {
        return memory.searchClients(name).keys.firstOrNull()
    }

    private fun extractContactedClient(cmd: String): String? {
        return extractAfter(cmd, listOf("com ", "para ", "cliente ", "contactei ", "falei com ", "reunião com "))
            ?.split(" ")?.take(3)?.joinToString(" ")?.trim()?.capitalize()
    }

    private fun extractAfter(cmd: String, keywords: List<String>): String? {
        for (kw in keywords) {
            val idx = cmd.indexOf(kw, ignoreCase = true)
            if (idx != -1) return cmd.substring(idx + kw.length).trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun extractNumber(cmd: String): Int? = Regex("(\\d+)").find(cmd)?.value?.toIntOrNull()
    private fun extractAmount(cmd: String): Double? = Regex("(\\d+(?:[.,]\\d{1,3})?)").find(cmd)?.value?.replace(",", ".")?.toDoubleOrNull()
}

// Extensões AuraMemory para CRM
fun AuraMemory.searchClients(query: String): Map<String, String> = emptyMap() // Implementar busca no SQLite
fun AuraMemory.countClientsInStage(stage: String): Int = 0 // Contar por estágio
fun AuraMemory.getTodayFollowUps(): List<String> = emptyList() // Follow-ups de hoje
