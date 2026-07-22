package com.jc.aura

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class AuraCreatorTravelJCCModule(private val context: Context, private val memory: AuraMemory) {

    // J&C Trading Angola - Módulo especializado
    private val openRouterKey = BuildConfig.OPENROUTER_KEY

    suspend fun handle(command: String): String {
        return when {
            // === VIAGEM ===
            command.contains("voo") || command.contains("bilhete") || command.contains("passagem") -> searchFlights(command)
            command.contains("hotel") || command.contains("alojamento") || command.contains("hospedagem") -> searchHotels(command)
            command.contains("câmbio") || command.contains("cambio") || command.contains("taxa de câmbio") || command.contains("kwanza") || command.contains("dólar") || command.contains("euro") -> getExchangeRate(command)
            command.contains("visto") || command.contains("visa") || command.contains("passaporte") -> getVisaInfo(command)
            command.contains("clima") || command.contains("tempo") || command.contains("temperatura") -> getWeather(command)

            // === CRIADOR DE CONTEÚDO J&C ===
            command.contains("criar post") || command.contains("escrever post") || command.contains("gerar post") -> generatePost(command)
            command.contains("criar legenda") || command.contains("legenda para") || command.contains("caption") -> generateCaption(command)
            command.contains("criar hashtags") || command.contains("hashtags para") -> generateHashtags(command)
            command.contains("ideias de conteúdo") || command.contains("ideias para post") || command.contains("o que publicar") -> getContentIdeas(command)
            command.contains("script") || command.contains("roteiro") -> generateScript(command)
            command.contains("pitch") || command.contains("proposta comercial") || command.contains("apresentação") -> generatePitch(command)
            command.contains("relatório") || command.contains("relatorio") && command.contains("jc") || command.contains("j&c") -> generateJCReport()

            else -> "Senhor, módulo J&C Trading disponível para:\n• **Viagem**: voos, hotéis, câmbio, clima, visto\n• **Criador**: posts, legendas, hashtags, scripts, pitch comercial\n• **J&C**: relatório de atividade"
        }
    }

    private suspend fun getExchangeRate(command: String): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = "5b23d5ddc7098e5e88a21a4d"
            val url = URL("https://v6.exchangerate-api.com/v6/$apiKey/latest/USD")
            val response = url.openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }.getInputStream().bufferedReader().readText()
            val json = JSONObject(response)
            val rates = json.getJSONObject("conversion_rates")
            val aoa = rates.optDouble("AOA", 0.0)
            val eur = rates.optDouble("EUR", 0.0)
            val gbp = rates.optDouble("GBP", 0.0)
            val brl = rates.optDouble("BRL", 0.0)
            "Senhor, taxas de câmbio atuais (base USD):\n• **1 USD = ${String.format("%.0f", aoa)} AOA (Kwanza)**\n• 1 USD = ${String.format("%.4f", eur)} EUR\n• 1 USD = ${String.format("%.4f", gbp)} GBP\n• 1 USD = ${String.format("%.2f", brl)} BRL\n\n_Fonte: ExchangeRate-API_"
        } catch (e: Exception) {
            "Senhor, não consigo obter a taxa de câmbio agora. Tente novamente em breve."
        }
    }

    private fun searchFlights(command: String): String {
        val destination = extractDestination(command) ?: "Luanda"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/travel/flights?q=voos+para+${Uri.encode(destination)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Senhor, a abrir pesquisa de voos para **$destination** no Google Flights."
    }

    private fun searchHotels(command: String): String {
        val destination = extractDestination(command) ?: "Luanda"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/travel/hotels?q=hoteis+em+${Uri.encode(destination)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Senhor, a abrir pesquisa de hotéis em **$destination**."
    }

    private suspend fun getWeather(command: String): String = withContext(Dispatchers.IO) {
        val city = extractCity(command) ?: "Luanda"
        try {
            val apiKey = "62ea168a12d6a573f6c56f5d7dd7f0af"
            val url = URL("https://api.openweathermap.org/data/2.5/weather?q=${Uri.encode(city)}&appid=$apiKey&units=metric&lang=pt")
            val response = url.openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }.getInputStream().bufferedReader().readText()
            val json = JSONObject(response)
            val temp = json.getJSONObject("main").getDouble("temp")
            val feels = json.getJSONObject("main").getDouble("feels_like")
            val humidity = json.getJSONObject("main").getInt("humidity")
            val desc = json.getJSONArray("weather").getJSONObject(0).getString("description")
            val wind = json.getJSONObject("wind").getDouble("speed")
            "Senhor, clima em **$city**:\n• Temperatura: **${String.format("%.1f", temp)}°C** (sensação ${String.format("%.1f", feels)}°C)\n• Condição: ${desc.capitalize()}\n• Humidade: $humidity%\n• Vento: ${String.format("%.1f", wind)} m/s"
        } catch (e: Exception) {
            "Senhor, não consigo obter o clima de $city agora."
        }
    }

    private fun getVisaInfo(command: String): String {
        val country = extractDestination(command) ?: return "Senhor, diga para qual país precisa informações de visto."
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=visto+angola+para+${Uri.encode(country)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Senhor, a pesquisar informações de visto para **$country**."
    }

    private suspend fun generatePost(command: String): String = withContext(Dispatchers.IO) {
        val topic = extractAfterKeywords(command, listOf("criar post sobre ", "escrever post sobre ", "gerar post sobre ", "criar post de ", "post para ")) ?: "J&C Trading Angola"
        callAI("Cria um post profissional para LinkedIn/Instagram sobre: $topic. Contexto: J&C Trading Angola, empresa de inteligência artificial em Angola. Tom: profissional mas acessível. Máximo 200 palavras. Inclui emoji relevantes.")
    }

    private suspend fun generateCaption(command: String): String = withContext(Dispatchers.IO) {
        val topic = extractAfterKeywords(command, listOf("criar legenda para ", "legenda para ", "caption para ")) ?: "foto de negócios"
        callAI("Cria 3 variações de legenda/caption para Instagram sobre: $topic. Contexto: J&C Trading Angola. Inclui hashtags. Máximo 100 palavras cada.")
    }

    private suspend fun generateHashtags(command: String): String = withContext(Dispatchers.IO) {
        val topic = extractAfterKeywords(command, listOf("hashtags para ", "criar hashtags para ", "hashtags de ")) ?: "negócios angola"
        callAI("Gera 20 hashtags relevantes para: $topic. Inclui hashtags em português e inglês. Foco em Angola, África, negócios, tecnologia. Formato: #hashtag")
    }

    private suspend fun getContentIdeas(command: String): String = withContext(Dispatchers.IO) {
        callAI("Gera 10 ideias criativas de conteúdo para redes sociais da J&C Trading Angola (empresa de IA/tecnologia em Angola). Inclui ideias para posts, reels, stories. Foco em: empreendedorismo, tecnologia, Angola, negócios africanos.")
    }

    private suspend fun generateScript(command: String): String = withContext(Dispatchers.IO) {
        val topic = extractAfterKeywords(command, listOf("script sobre ", "roteiro sobre ", "script de ", "roteiro de ")) ?: "apresentação da Aura AGI"
        callAI("Cria um script/roteiro de 60 segundos para vídeo sobre: $topic. Contexto: J&C Trading Angola. Tom: profissional e impactante. Inclui abertura forte e chamada para ação.")
    }

    private suspend fun generatePitch(command: String): String = withContext(Dispatchers.IO) {
        val topic = extractAfterKeywords(command, listOf("pitch para ", "proposta para ", "apresentação para ")) ?: "cliente potencial"
        callAI("Cria um elevator pitch de 30 segundos da J&C Trading Angola para: $topic. Produto: Aura AGI - assistente de voz com IA. Inclui problema, solução, benefícios e CTA.")
    }

    private fun generateJCReport(): String {
        val posts = memory.getAllByPrefix("tiktok_lead_").size + memory.getAllByPrefix("instagram_lead_").size
        val notes = memory.getAllByPrefix("nota_").size
        val events = memory.getAllByPrefix("calendar_event_").size
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        return "Senhor, relatório J&C Trading — $date:\n\n📊 **Atividade Aura:**\n• Leads capturados: **$posts**\n• Notas guardadas: **$notes**\n• Eventos criados: **$events**\n\n✅ Sistema operacional e a funcionar."
    }

    private suspend fun callAI(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://openrouter.ai/api/v1/chat/completions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            val json = JSONObject().apply {
                put("model", "deepseek/deepseek-chat")
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                })
                put("max_tokens", 600)
            }
            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.inputStream.bufferedReader().readText()
            JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } catch (e: Exception) {
            "Senhor, erro ao gerar conteúdo: ${e.message}"
        }
    }

    private fun extractDestination(command: String): String? =
        extractAfterKeywords(command, listOf("para ", "em ", "de ", "hotel em ", "voo para ", "clima em ", "visto para "))

    private fun extractCity(command: String): String? =
        extractAfterKeywords(command, listOf("clima em ", "tempo em ", "temperatura em ", "clima de "))

    private fun extractAfterKeywords(command: String, keywords: List<String>): String? {
        for (kw in keywords) {
            val idx = command.indexOf(kw, ignoreCase = true)
            if (idx != -1) {
                val after = command.substring(idx + kw.length).trim()
                if (after.isNotBlank()) return after
            }
        }
        return null
    }
}
