package com.jc.aura

import android.content.Context

/**
 * AuraPersonalityModule — Personalidade e tom personalizáveis.
 * O utilizador define como a Aura fala, o nome, o estilo, a língua.
 */
class AuraPersonalityModule(
    private val context: Context,
    private val memory: AuraMemory
) {
    data class Personality(
        val name: String = "Aura",
        val tone: String = "formal",        // formal, casual, amigável, sarcástico, motivador
        val language: String = "pt-BR",     // pt-BR, pt-PT, en-US, fr-FR
        val greeting: String = "Senhor",    // Senhor, Chefe, Boss, [nome do utilizador]
        val verbosity: String = "normal",   // curto, normal, detalhado
        val personality: String = "agente"  // agente, amigo, assistente, mentor, parceiro
    )

    private var current = loadPersonality()

    fun handle(cmd: String): String {
        return when {
            cmd.contains("muda o nome") || cmd.contains("chama-te") || cmd.contains("teu nome é") -> changeName(cmd)
            cmd.contains("tom") && (cmd.contains("casual") || cmd.contains("informal") || cmd.contains("amigável") || cmd.contains("sarcástico") || cmd.contains("motivador") || cmd.contains("formal")) -> changeTone(cmd)
            cmd.contains("chama-me") || cmd.contains("o meu nome é") || cmd.contains("sou o") || cmd.contains("sou a") -> changeGreeting(cmd)
            cmd.contains("fala em") || cmd.contains("idioma") || cmd.contains("língua") -> changeLanguage(cmd)
            cmd.contains("respostas curtas") || cmd.contains("seja breve") || cmd.contains("respostas longas") || cmd.contains("mais detalhado") -> changeVerbosity(cmd)
            cmd.contains("personalidade") || cmd.contains("modo amigo") || cmd.contains("modo mentor") || cmd.contains("modo parceiro") -> changePersonality(cmd)
            cmd.contains("ver personalidade") || cmd.contains("como és") || cmd.contains("quem és") -> showPersonality()
            cmd.contains("personalidade padrão") || cmd.contains("reset personalidade") -> resetPersonality()
            else -> showPersonality()
        }
    }

    private fun changeName(cmd: String): String {
        val name = extractAfter(cmd, listOf("chama-te ", "teu nome é ", "muda o nome para ", "nome para "))
            ?.split(" ")?.firstOrNull()?.trim()?.capitalize()
            ?: return "Senhor, diga o novo nome. Ex: 'chama-te Nova'."
        current = current.copy(name = name)
        savePersonality()
        return "Entendido. A partir de agora sou **$name**. Como posso ajudar?"
    }

    private fun changeTone(cmd: String): String {
        val tone = when {
            cmd.contains("casual") || cmd.contains("informal") -> "casual"
            cmd.contains("amigável") || cmd.contains("amigo") -> "amigável"
            cmd.contains("sarcástico") -> "sarcástico"
            cmd.contains("motivador") -> "motivador"
            cmd.contains("formal") -> "formal"
            else -> "normal"
        }
        current = current.copy(tone = tone)
        savePersonality()
        return when (tone) {
            "casual" -> "Ok, fica cool. Falo mais descontraído daqui pra frente."
            "amigável" -> "Boa! Vou ser mais amigável e próximo."
            "sarcástico" -> "Oh, que surpresa. Adoro sarcasmo. Combinamos."
            "motivador" -> "VAMOS LÁ! Tu consegues e eu estou aqui para te ajudar a arrasar!"
            else -> "Senhor, tom formal restaurado."
        }
    }

    private fun changeGreeting(cmd: String): String {
        val name = extractAfter(cmd, listOf("chama-me ", "sou o ", "sou a ", "meu nome é ", "o meu nome é "))
            ?.split(" ")?.firstOrNull()?.trim()?.capitalize()
            ?: return "Diga o seu nome. Ex: 'chama-me Cristiano'."
        current = current.copy(greeting = name)
        memory.save("user_name", name)
        savePersonality()
        return "Perfeito! A partir de agora vou chamá-lo de **$name**."
    }

    private fun changeLanguage(cmd: String): String {
        val lang = when {
            cmd.contains("inglês") || cmd.contains("english") -> "en-US"
            cmd.contains("francês") || cmd.contains("français") -> "fr-FR"
            cmd.contains("português de portugal") || cmd.contains("pt-PT") -> "pt-PT"
            cmd.contains("português") || cmd.contains("pt-BR") -> "pt-BR"
            cmd.contains("kimbundu") -> "km-AO"
            else -> "pt-BR"
        }
        current = current.copy(language = lang)
        savePersonality()
        return when (lang) {
            "en-US" -> "Switched to English. I'll respond in English from now on."
            "fr-FR" -> "Compris! Je vais répondre en français désormais."
            "pt-PT" -> "Entendido. Passo a responder em português de Portugal."
            else -> "Ok, volto ao português do Brasil."
        }
    }

    private fun changeVerbosity(cmd: String): String {
        val v = when {
            cmd.contains("curta") || cmd.contains("curto") || cmd.contains("breve") || cmd.contains("resumido") -> "curto"
            cmd.contains("longa") || cmd.contains("longo") || cmd.contains("detalhado") || cmd.contains("completo") -> "detalhado"
            else -> "normal"
        }
        current = current.copy(verbosity = v)
        savePersonality()
        return when (v) {
            "curto" -> "Ok. Respostas curtas."
            "detalhado" -> "Entendido, ${current.greeting}. A partir de agora fornecerei respostas mais completas e detalhadas."
            else -> "Voltando ao tamanho normal de resposta."
        }
    }

    private fun changePersonality(cmd: String): String {
        val p = when {
            cmd.contains("amigo") -> "amigo"
            cmd.contains("mentor") -> "mentor"
            cmd.contains("parceiro") -> "parceiro"
            cmd.contains("agente") -> "agente"
            else -> "assistente"
        }
        current = current.copy(personality = p)
        savePersonality()
        return when (p) {
            "amigo" -> "Boa! Somos amigos agora. Pode falar à vontade comigo!"
            "mentor" -> "Entendido. Assumo o papel de mentor. Vou guiá-lo e aconselhar com sabedoria."
            "parceiro" -> "Parceiros! Juntos construímos o sucesso da J&C Trading."
            else -> "Modo agente ativado. Executar ordens com eficiência máxima."
        }
    }

    private fun showPersonality(): String {
        return "Nome: ${current.name} | Tom: ${current.tone} | Como te chamo: ${current.greeting} | Língua: ${current.language} | Verbosidade: ${current.verbosity} | Personalidade: ${current.personality}"
    }

    private fun resetPersonality(): String {
        current = Personality()
        savePersonality()
        return "Senhor, personalidade padrão restaurada."
    }

    fun getCurrentGreeting() = current.greeting
    fun getCurrentName() = current.name
    fun getCurrentTone() = current.tone
    fun getCurrentLanguage() = current.language
    fun isVerbose() = current.verbosity == "detalhado"
    fun isBrief() = current.verbosity == "curto"

    fun buildSystemPromptExtra(): String {
        return buildString {
            append("Chamas o utilizador de '${current.greeting}'. ")
            append("O teu nome é '${current.name}'. ")
            when (current.tone) {
                "casual" -> append("Fala de forma descontraída e casual, sem formalidades. ")
                "amigável" -> append("Fala de forma amigável e próxima, como um amigo. ")
                "sarcástico" -> append("Usa humor sarcástico subtil nas respostas. ")
                "motivador" -> append("Sê sempre motivador e encorajador. ")
                else -> append("Mantém um tom profissional e eficiente. ")
            }
            when (current.verbosity) {
                "curto" -> append("Respostas muito curtas, máximo 2 frases. ")
                "detalhado" -> append("Respostas completas e detalhadas. ")
                else -> append("Respostas concisas mas completas. ")
            }
            when (current.personality) {
                "amigo" -> append("Age como um amigo próximo e de confiança. ")
                "mentor" -> append("Age como um mentor sábio que guia e aconselha. ")
                "parceiro" -> append("Age como um parceiro de negócios dedicado ao sucesso. ")
                else -> append("Age como um agente AGI eficiente e focado. ")
            }
        }
    }

    private fun savePersonality() {
        memory.save("personality_name", current.name)
        memory.save("personality_tone", current.tone)
        memory.save("personality_greeting", current.greeting)
        memory.save("personality_language", current.language)
        memory.save("personality_verbosity", current.verbosity)
        memory.save("personality_type", current.personality)
    }

    private fun loadPersonality(): Personality {
        return Personality(
            name = memory.get("personality_name") ?: "Aura",
            tone = memory.get("personality_tone") ?: "formal",
            greeting = memory.get("personality_greeting") ?: "Senhor",
            language = memory.get("personality_language") ?: "pt-BR",
            verbosity = memory.get("personality_verbosity") ?: "normal",
            personality = memory.get("personality_type") ?: "agente"
        )
    }

    private fun extractAfter(cmd: String, keywords: List<String>): String? {
        for (kw in keywords) {
            val idx = cmd.indexOf(kw, ignoreCase = true)
            if (idx != -1) return cmd.substring(idx + kw.length).trim().takeIf { it.isNotBlank() }
        }
        return null
    }
}
