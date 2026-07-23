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
 * AuraContentGenModule — Gera conteúdo para redes sociais da Mwango Brain.
 * 
 * Funcionalidades:
 * - Gerar posts completos (texto + hashtags) para cada plataforma
 * - Gerar captions com call-to-action
 * - Ideias de stories e reels
 * - Calendário de conteúdo semanal
 * - Artigos e blog posts
 * - Respostas a trending topics
 * - Tudo gerado por IA com contexto da marca Mwango Brain
 */
class AuraContentGenModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val mwangoBrain: AuraMwangoBrainModule
) {

    companion object {
        private const val TAG = "ContentGen"
    }

    private val openRouterKey = BuildConfig.OPENROUTER_KEY
    private val geminiApiKey = BuildConfig.GEMINI_KEY

    /**
     * Função principal — roda comandos de geração de conteúdo
     */
    suspend fun handle(command: String): String {
        return try {
            when {
                command.contains("gerar post") || command.contains("criar post") || command.contains("escrever post") -> {
                    val platform = detectPlatform(command)
                    val topic = extractTopic(command)
                    generatePost(platform, topic ?: "")
                }
                command.contains("gerar caption") || command.contains("criar legenda") || command.contains("escrever legenda") -> {
                    val platform = detectPlatform(command)
                    val topic = extractTopic(command)
                    generateCaption(platform, topic ?: "")
                }
                command.contains("ideia de story") || command.contains("story idea") || command.contains("ideias story") -> {
                    val platform = detectPlatform(command)
                    generateStoryIdeas(platform)
                }
                command.contains("calendário") || command.contains("calendario") || command.contains("agenda de conteúdo") -> {
                    generateContentCalendar()
                }
                command.contains("gerar artigo") || command.contains("escrever artigo") || command.contains("blog post") -> {
                    val topic = extractTopic(command)
                    generateArticle(topic ?: "")
                }
                command.contains("hashtags") || command.contains("hashtag") -> {
                    val topic = extractTopic(command) ?: "tecnologia angola"
                    generateHashtags(topic)
                }
                command.contains("sugestão de conteúdo") || command.contains("ideia de conteúdo") || command.contains("sugestao de conteudo") -> {
                    val platform = detectPlatform(command)
                    suggestContent(platform)
                }
                command.contains("anúncio") || command.contains("anuncio") || command.contains("copy vendas") -> {
                    val service = extractTopic(command) ?: "serviços"
                    generateAdCopy(service)
                }
                command.contains(" newsletter") || command.contains("news") -> {
                    val topic = extractTopic(command) ?: "novidades"
                    generateNewsletter(topic)
                }
                else -> {
                    "Senhor, não entendi que tipo de conteúdo deseja. Opções:\n" +
                    "• 'gerar post Instagram sobre apps'\n" +
                    "• 'gerar caption Facebook sobre marketing'\n" +
                    "• 'ideia de story TikTok'\n" +
                    "• 'calendário de conteúdo semanal'\n" +
                    "• 'gerar artigo sobre IA em Angola'\n" +
                    "• 'hashtags para design gráfico'\n" +
                    "• 'copy de vendas para websites'\n" +
                    "• 'newsletter sobre SEO'"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro content gen: ${e.message}")
            "Senhor, ocorreu um erro ao gerar conteúdo: ${e.message}"
        }
    }

    /**
     * Gera post completo para rede social
     */
    private suspend fun generatePost(platform: String, topic: String?): String {
        val brandContext = mwangoBrain.getBrandContext()
        val platformGuide = getPlatformGuide(platform)
        val actualTopic = topic ?: "transformação digital em Angola"

        val prompt = """$brandContext

$platformGuide

Gera UM post de rede social para a Mwango Brain sobre: $actualTopic

Requisitos:
- Tom profissional mas acessível
- Incluir emoji estratégicos (não exagerar)
- Call-to-action no final
- Texto com quebra de linhas naturais
- Hashtags relevantes (5-8)
- Linguagem natural, não robótica
- Máximo 280 caracteres para Twitter, 2200 para LinkedIn, 2200 para Instagram
- Idioma: Português de Angola (pt-AO)"""

        val post = callAI(prompt)

        memory.save("last_generated_post", post)
        memory.save("last_post_platform", platform)
        memory.save("last_post_topic", actualTopic)
        memory.save("last_post_time", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))

        return buildString {
            appendLine("✅ Post gerado para $platform:")
            appendLine()
            appendLine(post)
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Diga 'publicar $platform' se quiser que eu poste agora.")
        }
    }

    /**
     * Gera caption com hashtags
     */
    private suspend fun generateCaption(platform: String, topic: String?): String {
        val brandContext = mwangoBrain.getBrandContext()
        val actualTopic = topic ?: "serviços de desenvolvimento web"

        val prompt = """$brandContext

Gera uma caption atrativa para post de $platform da Mwango Brain sobre: $actualTopic

A caption deve ter:
- Hook forte na primeira linha (parar o scroll)
- Corpo com valor (dica, fato ou história breve)
- Call-to-action claro no final
- 8-15 hashtags no final (#MwangoBrain obrigatória)
- Tom: profissional mas acolhedor
- Idioma: Português de Angola"""

        val caption = callAI(prompt)
        memory.save("last_caption", caption)
        return "✅ Caption gerada:\n\n$caption"
    }

    /**
     * Gera ideias de stories
     */
    private suspend fun generateStoryIdeas(platform: String): String {
        val brandContext = mwangoBrain.getBrandContext()
        val services = mwangoBrain.getServices()

        val prompt = """$brandContext

Serviços da Mwango Brain:
$services

Gera 5 ideias criativas de stories para $platform da Mwango Brain.

Formato para cada ideia:
📸 [Número] - [Título]
   📝 O que mostrar: [descrição visual]
   💬 Texto no story: [texto curto]
   🎯 Objetivo: [engagement/lead/venda/branding]
   ⏰ Melhor horário: [manhã/tarde/noite]

Ideias devem ser variadas (bastidores, dicas, cases, polls, FAQ, bastidores do team).
Idioma: Português de Angola"""

        val ideas = callAI(prompt)
        return "💡 Ideias de Stories para $platform:\n\n$ideas"
    }

    /**
     * Gera calendário de conteúdo semanal
     */
    private suspend fun generateContentCalendar(): String {
        val brandContext = mwangoBrain.getBrandContext()
        val services = mwangoBrain.getServices()

        val prompt = """$brandContext

Serviços:
$services

Cria um calendário de conteúdo semanal para TODAS as redes sociais (Instagram, Facebook, LinkedIn, TikTok).

Formato:
📅 SEGUNDA-FEIRA
  📱 Instagram: [tipo de post + tema]
  📘 Facebook: [tipo de post + tema]
  💼 LinkedIn: [tipo de post + tema]
  🎵 TikTok: [tipo de post + tema]
(repetir para todos os dias)

Tipos de conteúdo: Post educativo, Case de sucesso, Bastidores, Dica rápida, Enquete/Poll, Story interactivo, Reel/TikTok, Carrossel, Artigo
Mix de conteúdo: 40% educativo, 30% vendas, 20% bastidores, 10% entretenimento
Idioma: Português de Angola"""

        val calendar = callAI(prompt)
        memory.save("last_content_calendar", calendar)

        return buildString {
            appendLine("📅 Calendário de Conteúdo Semanal - Mwango Brain")
            appendLine()
            appendLine(calendar)
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Diga 'gerar post [dia] [tema]' para criar o conteúdo de cada dia.")
        }
    }

    /**
     * Gera artigo para blog
     */
    private suspend fun generateArticle(topic: String): String {
        val brandContext = mwangoBrain.getBrandContext()

        val prompt = """$brandContext

Escreve um artigo de blog profissional para o site da Mwango Brain sobre: $topic

Estrutura:
- Título atrativo e SEO-friendly
- Subtítulo (tagline)
- Introdução (2-3 parágrafos engajantes)
- 3-5 secções com subtítulos H2
- Dicas práticas em cada secção
- Conclusão com CTA
- Meta description (150 caracteres)
- 5 palavras-chave para SEO

Tom: Autoridade técnica mas acessível.
Idioma: Português de Angola
Tamanho: 800-1200 palavras"""

        val article = callAI(prompt)
        memory.save("last_article", article)

        return buildString {
            appendLine("📝 Artigo gerado:")
            appendLine()
            appendLine(article)
        }
    }

    /**
     * Gera hashtags
     */
    private suspend fun generateHashtags(topic: String): String {
        val prompt = """Gera 20 hashtags relevantes para posts sobre "$topic" da empresa Mwango Brain (agência digital angolana).

Incluir:
- 5 hashtags da marca/nome
- 5 hashtags do nicho/indústria
- 5 hashtags de localização (Angola, Luanda, África)
- 5 hashtags trending

Retorna apenas as hashtags separadas por espaço, sem explicação."""

        val hashtags = callAI(prompt)
        return "🏷️ Hashtags para '$topic':\n\n$hashtags"
    }

    /**
     * Sugere ideias de conteúdo
     */
    private suspend fun suggestContent(platform: String): String {
        val brandContext = mwangoBrain.getBrandContext()
        val services = mwangoBrain.getServices()

        val prompt = """$brandContext

Serviços:
$services

Sugere 10 ideias de conteúdo viral para $platform que a Mwango Brain pode criar esta semana.

Formato:
🔥 [Ideia] — [Por que funciona] — [Dificuldade: Fácil/Médio/Difícil]

Priorizar ideias com alto potencial de engagement e conversão para agência digital.
Idioma: Português de Angola"""

        val suggestions = callAI(prompt)
        return "🎯 Sugestões de conteúdo para $platform:\n\n$suggestions"
    }

    /**
     * Gera copy de vendas/anúncio
     */
    private suspend fun generateAdCopy(service: String): String {
        val brandContext = mwangoBrain.getBrandContext()

        val prompt = """$brandContext

Gera 3 versões de copy de vendas (anúncio) para o serviço de $service da Mwango Brain.

Cada versão deve ter:
- Headline forte (máx 10 palavras)
- Corpo do anúncio (3-4 linhas com benefícios)
- CTA claro
- Emoção + urgência + prova social

Versões:
1. Copy para Facebook/Instagram Ads
2. Copy para LinkedIn Ads
3. Copy para WhatsApp Broadcast

Idioma: Português de Angola"""

        val copy = callAI(prompt)
        return "📢 Copy de vendas para '$service':\n\n$copy"
    }

    /**
     * Gera newsletter
     */
    private suspend fun generateNewsletter(topic: String): String {
        val brandContext = mwangoBrain.getBrandContext()

        val prompt = """$brandContext

Gera uma newsletter profissional da Mwango Brain sobre: $topic

Estrutura:
📧 Assunto: [assunto do email]
👋 Abertura: [saudação pessoal]
📰 Principal: [notícia ou destaque principal]
💡 Dica: [dica prática do mês]
🔥 Serviço em destaque: [breve pitch de um serviço]
🎯 CTA: [call-to-action]

Tom: Profissional mas amigável.
Idioma: Português de Angola
Tamanho: 300-500 palavras"""

        val newsletter = callAI(prompt)
        return "📧 Newsletter gerada:\n\n$newsletter"
    }

    // === HELPERS ===

    private fun detectPlatform(command: String): String {
        return when {
            command.contains("instagram") || command.contains("ig") || command.contains("insta") -> "Instagram"
            command.contains("facebook") || command.contains("fb") -> "Facebook"
            command.contains("tiktok") || command.contains("tt") -> "TikTok"
            command.contains("linkedin") || command.contains("in") -> "LinkedIn"
            command.contains("twitter") || command.contains("x ") -> "Twitter/X"
            command.contains("youtube") || command.contains("yt") -> "YouTube"
            command.contains("whatsapp") || command.contains("zap") -> "WhatsApp"
            else -> "Instagram"
        }
    }

    private fun extractTopic(command: String): String? {
        val patterns = listOf("sobre", "acerca de", "tema", "tópico", "assunto")
        for (pattern in patterns) {
            val idx = command.indexOf(pattern)
            if (idx >= 0) {
                return command.substring(idx + pattern.length).trim()
            }
        }
        return null
    }

    private fun getPlatformGuide(platform: String): String {
        return when (platform) {
            "Instagram" -> "Instagram: usar quebra de linhas naturais, 2-3 parágrafos curtos, hashtags no final, tom visual e inspirador"
            "Facebook" -> "Facebook: post mais longo e detalhado, pode incluir links, tom conversacional"
            "TikTok" -> "TikTok: texto CURTO (1-2 linhas), trending, informal, jovem, com ganchos"
            "LinkedIn" -> "LinkedIn: tom profissional B2B, insights de indústria, data-driven, networking"
            "Twitter/X" -> "Twitter/X: máximo 280 caracteres, conciso, impactante"
            "YouTube" -> "YouTube: descrição detalhada, SEO-friendly, com timestamps e links"
            "WhatsApp" -> "WhatsApp: direto e pessoal, curto, com emoji"
            else -> "Post genérico de rede social"
        }
    }

    private suspend fun callAI(prompt: String): String {
        return try {
            val json = JSONObject()
            json.put("model", "meta-llama/llama-3.3-70b-instruct")
            val messages = org.json.JSONArray()
            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", "Es um assistente de conteúdo de marketing digital angolano. Nunca diga que es IA. Responde sempre em português de Angola. Sê criativo, profissional e estratégico.")
            messages.put(systemMsg)
            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", prompt)
            messages.put(userMsg)
            json.put("messages", messages)
            json.put("max_tokens", 2000)
            json.put("temperature", 0.85)

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
            Log.e(TAG, "OpenRouter fallback to Gemini: ${e.message}")
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
            contentObj.put("parts", contents.put(part))
            contents.remove(contents.length() - 1)
            contents.put(contentObj)
            json.put("contents", contents)
            json.put("generationConfig", JSONObject().apply {
                put("temperature", 0.85)
                put("maxOutputTokens", 2000)
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
            "Erro ao gerar conteúdo. Tente novamente."
        }
    }
}
