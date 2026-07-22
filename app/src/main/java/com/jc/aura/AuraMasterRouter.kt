package com.jc.aura

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AuraMasterRouter — Central command routing and intent classification.
 * Routes processed voice commands to the appropriate module.
 */
class AuraMasterRouter(
    private val voiceService: AuraVoiceService,
    private val memory: AuraMemory
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Classify intent category from raw command text.
     */
    fun classifyIntent(command: String): IntentCategory {
        val lower = command.lowercase()
        return when {
            lower.contains("tiktok") || lower.contains("curtir vídeo") -> IntentCategory.TIKTOK
            lower.contains("instagram") || lower.contains("ig ") || lower.contains("insta") -> IntentCategory.INSTAGRAM
            lower.contains("facebook") || lower.contains("fb ") || lower.contains("messenger") -> IntentCategory.FACEBOOK
            lower.contains("linkedin") || lower.contains("linked in") -> IntentCategory.LINKEDIN
            lower.contains("email") || lower.contains("gmail") -> IntentCategory.EMAIL
            lower.contains("calendário") || lower.contains("agenda") || lower.contains("agendar") || lower.contains("reunião") -> IntentCategory.CALENDAR
            lower.contains("ficheiro") || lower.contains("arquivo") || lower.contains("pasta") || lower.contains("nota") -> IntentCategory.FILE_SYSTEM
            lower.contains("emergência") || lower.contains("socorro") || lower.contains("bombeiros") || lower.contains("ambulância") -> IntentCategory.EMERGENCY
            lower.contains("notícia") || lower.contains("noticias") || lower.contains("news") -> IntentCategory.NEWS
            lower.contains("bateria") || lower.contains("ram") || lower.contains("memória") || lower.contains("sistema") || lower.contains("armazenamento") -> IntentCategory.SYSTEM_INFO
            lower.contains("multicaixa") || lower.contains("saldo") || lower.contains("transferência") || lower.contains("banco") -> IntentCategory.WALLET
            lower.contains("voo") || lower.contains("hotel") || lower.contains("câmbio") || lower.contains("kwanza") || lower.contains("clima") || lower.contains("visto") -> IntentCategory.TRAVEL
            lower.contains("criar post") || lower.contains("legenda") || lower.contains("hashtag") || lower.contains("script") || lower.contains("pitch") -> IntentCategory.CREATOR
            lower.contains("criar imagem") || lower.contains("gerar imagem") -> IntentCategory.IMAGE_GEN
            lower.contains("analisar vídeo") || lower.contains("transcrever") -> IntentCategory.VIDEO_ANALYSIS
            lower.contains("pdf") || lower.contains("imagem") && (lower.contains("analis") || lower.contains("descrev")) -> IntentCategory.VISION
            lower.contains("rosto") || lower.contains("face") || lower.contains("reconhece") -> IntentCategory.FACE_ID
            lower.contains("conectar pc") || lower.contains("computador") || lower.contains("screenshot pc") -> IntentCategory.REMOTE_PC
            lower.contains("iot") || lower.contains("casa inteligente") || lower.contains("luz") || lower.contains("alexa") -> IntentCategory.IOT
            lower.contains("modo fantasma") || lower.contains("ghost mode") || lower.contains("invisível") -> IntentCategory.GHOST_MODE
            lower.contains("encriptar") || lower.contains("criptografar") || lower.contains("hash") -> IntentCategory.CRYPTO
            lower.contains("navegar para") || lower.contains("maps") || lower.contains("gps") -> IntentCategory.NAVIGATION
            lower.contains("pesquisar") || lower.contains("procurar") || lower.contains("google") -> IntentCategory.SEARCH
            lower.contains("youtube") || lower.contains("netflix") || lower.contains("spotify") -> IntentCategory.QUICK_APP
            lower.contains("bom dia") || lower.contains("good morning") -> IntentCategory.ROUTINE_MORNING
            lower.contains("boa noite") || lower.contains("good night") -> IntentCategory.ROUTINE_SLEEP
            lower.contains("trabalho") || lower.contains("work mode") -> IntentCategory.ROUTINE_WORK
            lower.contains("relatorio") || lower.contains("relatório") || lower.contains("j&c") || lower.contains("jc trading") -> IntentCategory.JC_REPORT
            else -> IntentCategory.GENERAL_AI
        }
    }

    /**
     * Save conversation turn to memory for context.
     */
    fun saveContext(command: String, response: String) {
        memory.saveConversation("user", command)
        memory.saveConversation("assistant", response)
    }

    /**
     * Retrieve conversation context for AI prompt.
     */
    fun buildContextPrompt(): String {
        val recent = memory.getRecentConversation(6)
        if (recent.isEmpty()) return ""
        val sb = StringBuilder("Conversa anterior:\n")
        recent.forEach { (role, content) ->
            sb.append("${if (role == "user") "Utilizador" else "Aura"}: $content\n")
        }
        return sb.toString()
    }
}

enum class IntentCategory {
    TIKTOK, INSTAGRAM, FACEBOOK, LINKEDIN,
    EMAIL, CALENDAR, FILE_SYSTEM, EMERGENCY, NEWS,
    SYSTEM_INFO, WALLET, TRAVEL, CREATOR,
    IMAGE_GEN, VIDEO_ANALYSIS, VISION, FACE_ID,
    REMOTE_PC, IOT, GHOST_MODE, CRYPTO,
    NAVIGATION, SEARCH, QUICK_APP,
    ROUTINE_MORNING, ROUTINE_SLEEP, ROUTINE_WORK,
    JC_REPORT, GENERAL_AI
}
