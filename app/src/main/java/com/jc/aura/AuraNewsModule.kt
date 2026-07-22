package com.jc.aura

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class AuraNewsModule(private val context: Context, private val memory: AuraMemory) {

    // GNews API (free tier available) or NewsAPI
    private val newsApiKey = "pub_free"

    suspend fun handleNewsCommand(command: String): String {
        return when {
            command.contains("notícias") || command.contains("noticias") || command.contains("news") -> {
                val topic = extractTopic(command)
                fetchNews(topic)
            }
            command.contains("angola") && (command.contains("hoje") || command.contains("notícia")) -> {
                fetchNews("angola")
            }
            command.contains("tecnologia") || command.contains("tech") -> fetchNews("tecnologia")
            command.contains("desporto") || command.contains("futebol") || command.contains("sport") -> fetchNews("desporto angola")
            command.contains("negócios") || command.contains("economia") || command.contains("mercado") -> fetchNews("negócios angola economia")
            command.contains("política") || command.contains("governo") -> fetchNews("política angola")
            else -> "Senhor, posso buscar notícias de: tecnologia, desporto, negócios, política, angola. Ex: 'notícias de tecnologia'."
        }
    }

    private suspend fun fetchNews(topic: String): String = withContext(Dispatchers.IO) {
        try {
            // Use RSS feeds como fallback gratuito
            val rssUrl = when {
                topic.contains("angola") -> "https://feeds.feedburner.com/AngolaPress"
                topic.contains("tecnologia") -> "https://feeds.feedburner.com/TechCrunch"
                topic.contains("desporto") || topic.contains("futebol") -> "https://feeds.bbci.co.uk/sport/rss.xml"
                topic.contains("negócios") || topic.contains("economia") -> "https://feeds.feedburner.com/entrepreneur/latest"
                else -> "https://feeds.bbci.co.uk/news/world/rss.xml"
            }

            val content = URL(rssUrl).openConnection().apply {
                connectTimeout = 8000
                readTimeout = 8000
            }.getInputStream().bufferedReader().readText()

            val headlines = parseRss(content)
            if (headlines.isEmpty()) {
                return@withContext "Senhor, não consegui obter notícias neste momento. Verifique a sua ligação à internet."
            }

            val sb = StringBuilder("Senhor, aqui estão as notícias de **${topic.capitalize()}**:\n\n")
            headlines.take(5).forEachIndexed { i, headline ->
                sb.append("${i + 1}. $headline\n\n")
            }
            sb.append("Para mais detalhes sobre alguma, diga 'explica notícia [número]'.")

            val result = sb.toString()
            memory.saveFactual("last_news_$topic", result)
            result
        } catch (e: Exception) {
            "Senhor, erro ao buscar notícias: ${e.message}. Verifique a internet."
        }
    }

    private fun parseRss(content: String): List<String> {
        val headlines = mutableListOf<String>()
        val titleRegex = Regex("<title><!\\[CDATA\\[(.+?)\\]\\]></title>|<title>(.+?)</title>")
        val matches = titleRegex.findAll(content)
        matches.forEach { match ->
            val title = (match.groupValues[1].ifBlank { match.groupValues[2] }).trim()
            if (title.isNotBlank() && !title.contains("RSS") && !title.contains("Feed") && title.length > 10) {
                headlines.add(title)
            }
        }
        return headlines.drop(1) // Remove o título do canal
    }

    private fun extractTopic(command: String): String {
        val patterns = listOf("notícias de ", "noticias de ", "news sobre ", "notícias sobre ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim()
        }
        return "angola"
    }
}
