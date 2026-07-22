package com.jc.aura

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * AuraMultiLanguageModule — Suporte a múltiplos idiomas.
 * A Aura detecta automaticamente o idioma do utilizador e responde no mesmo.
 * Suporta: Português, Inglês, Francês.
 * Muda o idioma do TTS dinamicamente.
 */
class AuraMultiLanguageModule(
    private val context: Context,
    private val memory: AuraMemory
) {
    enum class Language(val code: String, val locale: Locale, val label: String) {
        PORTUGUESE_BR("pt-BR", Locale("pt", "BR"), "Português (Brasil)"),
        PORTUGUESE_PT("pt-PT", Locale("pt", "PT"), "Português (Portugal)"),
        ENGLISH("en-US", Locale.US, "English"),
        FRENCH("fr-FR", Locale.FRANCE, "Français");
    }

    private var currentLanguage = loadLanguage()

    fun detectLanguage(text: String): Language {
        val lower = text.lowercase()
        return when {
            // Palavras em inglês
            lower.split(" ").count { it in englishWords } >= 2 -> Language.ENGLISH
            // Palavras em francês
            lower.split(" ").count { it in frenchWords } >= 2 -> Language.FRENCH
            // Português de Portugal
            lower.contains("fazer") || lower.contains("ecrã") || lower.contains("utilizador") -> Language.PORTUGUESE_PT
            // Default: Português Brasil
            else -> Language.PORTUGUESE_BR
        }
    }

    fun setLanguage(lang: Language, tts: TextToSpeech?) {
        currentLanguage = lang
        memory.save("app_language", lang.code)
        tts?.language = lang.locale
    }

    fun setLanguageByCode(code: String, tts: TextToSpeech?) {
        val lang = Language.values().find { it.code == code } ?: Language.PORTUGUESE_BR
        setLanguage(lang, tts)
    }

    fun getCurrentLanguage() = currentLanguage
    fun getCurrentLocale() = currentLanguage.locale

    fun translateResponse(text: String, targetLang: Language): String {
        // Para tradução completa usaria a API do OpenRouter/Gemini
        // Aqui fazemos ajustes básicos de saudação
        if (targetLang == currentLanguage) return text
        return when (targetLang) {
            Language.ENGLISH -> text
                .replace("Senhor", "Sir")
                .replace("Obrigado", "Thank you")
                .replace("Entendido", "Understood")
                .replace("Pronto", "Done")
            Language.FRENCH -> text
                .replace("Senhor", "Monsieur")
                .replace("Entendido", "Compris")
                .replace("Pronto", "Fait")
            else -> text
        }
    }

    fun getGreetingForLanguage(name: String = "Senhor"): String {
        return when (currentLanguage) {
            Language.ENGLISH -> "Sir"
            Language.FRENCH -> "Monsieur"
            else -> name
        }
    }

    fun buildMultiLangPrompt(): String {
        return when (currentLanguage) {
            Language.ENGLISH -> "Respond ONLY in English. Be concise and professional."
            Language.FRENCH -> "Répondez UNIQUEMENT en français. Soyez concis et professionnel."
            Language.PORTUGUESE_PT -> "Responda APENAS em português de Portugal. Use vocabulário europeu."
            else -> "Responda APENAS em português do Brasil."
        }
    }

    private fun loadLanguage(): Language {
        val code = memory.get("app_language") ?: "pt-BR"
        return Language.values().find { it.code == code } ?: Language.PORTUGUESE_BR
    }

    private val englishWords = setOf(
        "the", "and", "is", "are", "was", "were", "have", "has", "had",
        "do", "does", "did", "will", "would", "could", "should", "may",
        "can", "what", "where", "when", "who", "how", "why", "search",
        "open", "play", "stop", "help", "yes", "no", "please", "thank"
    )

    private val frenchWords = setOf(
        "le", "la", "les", "un", "une", "des", "est", "sont", "avoir",
        "faire", "aller", "vouloir", "pouvoir", "devoir", "je", "tu",
        "il", "elle", "nous", "vous", "ils", "elles", "oui", "non",
        "merci", "bonjour", "bonsoir", "comment", "pourquoi", "quand"
    )
}
