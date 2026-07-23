package com.jc.aura

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraCustomerServiceModule — Atendimento ao cliente automático para Mwango Brain.
 * 
 * Funcionalidades:
 * - Responder perguntas sobre serviços, preços, prazos
 * - Agendar reuniões/consultas
 * - Gerar propostas comerciais personalizadas
 * - Qualificar leads automaticamente
 * - FAQ automático com respostas da marca
 * - Seguir-up automático após primeiro contacto
 */
class AuraCustomerServiceModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val mwangoBrain: AuraMwangoBrainModule
) {

    companion object {
        private const val TAG = "CustomerService"
    }

    private val openRouterKey = BuildConfig.OPENROUTER_KEY
    private val geminiApiKey = BuildConfig.GEMINI_KEY

    suspend fun handle(command: String): String {
        return try {
            when {
                command.contains("responder pergunta") || command.contains("responder cliente") || command.contains("atender") -> {
                    val question = extractQuestion(command)
                    answerCustomerQuestion(question)
                }
                command.contains("agendar reunião") || command.contains("marcar reunião") || command.contains("agendar consulta") || command.contains("agendar sessão") -> {
                    scheduleMeeting(command)
                }
                command.contains("gerar proposta") || command.contains("criar proposta") || command.contains("proposta comercial") -> {
                    generateProposal(command)
                }
                command.contains("qualificar lead") || command.contains("qualificar cliente") || command.contains("avaliar lead") -> {
                    qualifyLead(command)
                }
                command.contains("follow-up") || command.contains("seguimento") || command.contains("acompanhar") -> {
                    generateFollowUp(command)
                }
                command.contains("preços") || command.contains("precos") || command.contains("quanto custa") || command.contains("valor") -> {
                    answerPricing(command)
                }
                command.contains("prazo") || command.contains("tempo de entrega") || command.contains("quanto tempo") -> {
                    answerTimeline(command)
                }
                command.contains("portfolio") || command.contains("portfólio") || command.contains("trabalhos anteriores") -> {
                    mwangoBrain.getProducts() + "\n\nVisite o nosso portfólio em: https://mwangobrain.com"
                }
                command.contains("contacto") || command.contains("contactos") || command.contains("telefone") || command.contains("morada") -> {
                    mwangoBrain.getContacts()
                }
                command.contains("serviços mwango") || command.contains("o que fazem") -> {
                    mwangoBrain.getServices()
                }
                else -> {
                    "Senhor, posso ajudar com:\n" +
                    "• 'responder pergunta [pergunta]'\n" +
                    "• 'agendar reunião com [cliente] dia [data]'\n" +
                    "• 'gerar proposta para [cliente] serviço [tipo]'\n" +
                    "• 'qualificar lead [nome]'\n" +
                    "• 'follow-up para [cliente]'\n" +
                    "• 'preços de [serviço]'\n" +
                    "• 'prazo para [serviço]'"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro customer service: ${e.message}")
            "Senhor, erro no atendimento: ${e.message}"
        }
    }

    /**
     * Responde perguntas de clientes com contexto da Mwango Brain
     */
    private suspend fun answerCustomerQuestion(question: String?): String {
        val brandContext = mwangoBrain.getBrandContext()
        val services = mwangoBrain.getServices()
        val contacts = mwangoBrain.getContacts()
        val actualQuestion = question ?: "serviços da Mwango Brain"

        val prompt = """$brandContext

Serviços:
$services

Contactos:
$contacts

Um cliente perguntou: "$actualQuestion"

Responde como representante da Mwango Brain:
- Tom profissional mas amigável (como um humano)
- Resposta directa e útil
- Se não souber, oferecer contacto directo
- NUNCA mencionar que es IA
- Idioma: Português de Angola
- Máximo 150 palavras"""

        val answer = callAI(prompt)
        memory.save("last_customer_question", actualQuestion)
        memory.save("last_customer_answer", answer)

        return "💬 Resposta ao cliente:\n\n$answer"
    }

    /**
     * Agenda reunião e guarda na memória
     */
    private fun scheduleMeeting(command: String): String {
        val clientName = extractName(command)
        val date = extractDate(command)
        val time = extractTime(command)

        if (clientName == null || date == null) {
            return "Senhor, preciso do nome do cliente e da data. Exemplo:\n" +
                "'agendar reunião com João dia 25/07 às 14h'"
        }

        val meetingKey = "meeting_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}_${clientName.replace(" ", "_")}"
        val meetingData = "cliente=$clientName|data=$date|hora=${time ?: "a definir"}|status=agendada"
        memory.save(meetingKey, meetingData)
        memory.save("meetings_count", ((memory.get("meetings_count")?.toIntOrNull() ?: 0) + 1).toString())

        return buildString {
            appendLine("📅 Reunião agendada!")
            appendLine("  👤 Cliente: $clientName")
            appendLine("  📆 Data: $date")
            if (time != null) appendLine("  ⏰ Hora: $time")
            appendLine("  ✅ Status: Agendada")
            appendLine()
            appendLine("Diga 'ver reuniões' para listar todas as agendadas.")
        }
    }

    /**
     * Gera proposta comercial personalizada
     */
    private suspend fun generateProposal(command: String): String {
        val clientName = extractName(command) ?: "Cliente"
        val service = extractService(command) ?: "desenvolvimento web"
        val brandContext = mwangoBrain.getBrandContext()

        val prompt = """$brandContext

Gera uma proposta comercial profissional para a Mwango Brain:

Cliente: $clientName
Serviço: $service

A proposta deve ter:
📋 CABEÇALHO
- Logo/nome da Mwango Brain
- Data
- Nº da proposta

📋 INTRODUÇÃO
- Saudação personalizada ao cliente
- Resumo do que foi discutido

📋 SCOPE DO PROJECTO
- Descrição detalhada do serviço
- Entregáveis específicos

📋 CRONOGRAMA
- Fases do projecto com prazos estimados

📋 INVESTIMENTO
- Valores estimados (faixa de preço)

📋 CONDIÇÕES
- Forma de pagamento
- Garantias
- Próximos passos

Tom: Profissional B2B, persuasivo mas honesto.
Idioma: Português de Angola"""

        val proposal = callAI(prompt)
        memory.save("last_proposal", proposal)
        memory.save("last_proposal_client", clientName)
        memory.save("proposals_count", ((memory.get("proposals_count")?.toIntOrNull() ?: 0) + 1).toString())

        return buildString {
            appendLine("📋 Proposta gerada para $clientName:")
            appendLine()
            appendLine(proposal)
        }
    }

    /**
     * Qualifica um lead (hot/warm/cold)
     */
    private suspend fun qualifyLead(command: String): String {
        val name = extractName(command) ?: return "Senhor, preciso do nome do lead. Exemplo: 'qualificar lead João Silva'"

        val prompt = """É um assistente de vendas de uma agência digital angolana (Mwango Brain).
Qualifica este lead: $name
Considera: interesse demonstrado, orçamento, urgência, fit com serviços (web, apps, marketing, design, SEO).

Retorna NESTE formato exacto:
Nome: $name
Score: [1-100]
Classificação: [HOT/WARM/COLD]
Motivo: [1 frase]
Próximo passo: [1 acção específica]"""

        val result = callAI(prompt)
        memory.save("lead_${name.replace(" ", "_")}", result)
        memory.save("leads_qualified", ((memory.get("leads_qualified")?.toIntOrNull() ?: 0) + 1).toString())

        return "🎯 Qualificação do lead:\n\n$result"
    }

    /**
     * Gera mensagem de follow-up
     */
    private suspend fun generateFollowUp(command: String): String {
        val name = extractName(command) ?: "cliente"

        val prompt = """É um assistente de vendas da Mwango Brain (agência digital angolana).
Gera uma mensagem de follow-up profissional e natural para: $name

A mensagem deve:
- Ser amigável mas profissional
- Referir o último contacto
- Oferecer algo de valor (dica, convite para reunião, desconto)
- NÃO ser agressiva/vendedora
- Parecer escrita por um humano
- Máximo 100 palavras
- Idioma: Português de Angola"""

        val message = callAI(prompt)
        return "📝 Follow-up para $name:\n\n$message"
    }

    /**
     * Responde sobre preços
     */
    private suspend fun answerPricing(command: String): String {
        val service = extractService(command) ?: "serviços"

        val prompt = """É um consultor comercial da Mwango Brain (agência digital angolana com 16 anos).
Um cliente perguntou sobre preços de: $service

Responde:
- Dar uma faixa de preço indicativa (não valor exacto, pois depende do scope)
- Explicar factores que influenciam o preço
- Oferecer consulta gratuita para orçamento detalhado
- Tom: consultivo, não vendedor
- Máximo 100 palavras
- Idioma: Português de Angola"""

        return "💰 Preços - $service:\n\n" + callAI(prompt)
    }

    /**
     * Responde sobre prazos
     */
    private suspend fun answerTimeline(command: String): String {
        val service = extractService(command) ?: "projecto"

        val prompt = """É um project manager da Mwango Brain (agência digital angolana).
Um cliente perguntou sobre o prazo de: $service

Responde com:
- Prazo típico estimado
- Fases do projecto
- Factores que podem acelerar ou atrasar
- Garantia de qualidade vs velocidade
- Máximo 80 palavras
- Idioma: Português de Angola"""

        return "⏱️ Prazo - $service:\n\n" + callAI(prompt)
    }

    // === HELPERS ===

    private fun extractName(command: String): String? {
        val patterns = listOf("com ", "cliente ", "para ", "lead ", "senhor ", "dona ")
        for (pattern in patterns) {
            val idx = command.indexOf(pattern)
            if (idx >= 0) {
                val name = command.substring(idx + pattern.length).trim()
                val words = name.split(Regex("\\s+"))
                if (words.size >= 2) return words.take(3).joinToString(" ")
                if (words.size == 1 && words[0].length > 2) return words[0]
            }
        }
        return null
    }

    private fun extractDate(command: String): String? {
        val datePattern = Regex("\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?")
        return datePattern.find(command)?.value
    }

    private fun extractTime(command: String): String? {
        val timePattern = Regex("(?:às|as|hora)\\s*(\\d{1,2})(?::?\\d{0,2})?")
        return timePattern.find(command)?.groupValues?.get(1)?.let { "${it}h" }
    }

    private fun extractService(command: String): String? {
        val patterns = listOf("serviço", "servico", "de ", "sobre ")
        for (pattern in patterns) {
            val idx = command.indexOf(pattern)
            if (idx >= 0) {
                val service = command.substring(idx + pattern.length).trim()
                if (service.length > 3) return service
            }
        }
        return null
    }

    private fun extractQuestion(command: String): String? {
        val patterns = listOf("pergunta ", "responder ", "atender ", "cliente perguntou ")
        for (pattern in patterns) {
            val idx = command.indexOf(pattern)
            if (idx >= 0) {
                return command.substring(idx + pattern.length).trim()
            }
        }
        return null
    }

    private suspend fun callAI(prompt: String): String {
        return try {
            val json = JSONObject()
            json.put("model", "deepseek/deepseek-v4-pro")
            val messages = org.json.JSONArray()
            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", "É um consultor comercial da Mwango Brain, agência digital angolana. Responde em português de Angola. Sê directo, profissional e amigável. NUNCA menciones que es IA.")
            messages.put(systemMsg)
            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", prompt)
            messages.put(userMsg)
            json.put("messages", messages)
            json.put("max_tokens", 1500)
            json.put("temperature", 0.7)

            withContext(Dispatchers.IO) {
                val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as java.net.HttpURLConnection)
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
                connection.doOutput = true
                connection.outputStream.write(json.toString().toByteArray())

                val response = connection.inputStream.bufferedReader().readText()
                val responseJson = JSONObject(response)
                responseJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter error: ${e.message}")
            callGemini(prompt)
        }
    }

    private suspend fun callGemini(prompt: String): String {
        return try {
            val json = JSONObject()
            val contents = org.json.JSONArray()
            val part = JSONObject()
            part.put("text", prompt)
            val contentObj = JSONObject()
            contentObj.put("parts", org.json.JSONArray().put(part))
            contents.put(contentObj)
            json.put("contents", contents)
            json.put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 1500)
            })

            withContext(Dispatchers.IO) {
                val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiApiKey"
                val connection = (URL(urlStr).openConnection() as java.net.HttpURLConnection)
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.write(json.toString().toByteArray())

                val response = connection.inputStream.bufferedReader().readText()
                val responseJson = JSONObject(response)
                responseJson.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini error: ${e.message}")
            "Erro ao processar. Tente novamente."
        }
    }
}
