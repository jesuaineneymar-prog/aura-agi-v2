package com.jc.aura

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * AuraLiveStreamModule — Controlo de Lives por voz.
 * Inicia lives no Instagram, TikTok. Controla duração, adiciona comentários.
 * Usa acessibilidade para navegar dentro das apps.
 */
class AuraLiveStreamModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val accessibilityService: AuraVoiceService? = null
) {
    private var isLiveActive = false
    private var liveStartTime = 0L
    private var livePlatform = ""

    fun handle(cmd: String): String {
        return when {
            cmd.contains("live no instagram") || cmd.contains("live instagram") || (cmd.contains("instagram") && cmd.contains("live")) -> startInstagramLive(cmd)
            cmd.contains("live no tiktok") || cmd.contains("live tiktok") || (cmd.contains("tiktok") && cmd.contains("live")) -> startTikTokLive(cmd)
            cmd.contains("live no facebook") || cmd.contains("live facebook") -> startFacebookLive(cmd)
            cmd.contains("live no youtube") || (cmd.contains("youtube") && cmd.contains("live")) -> startYouTubeLive(cmd)
            cmd.contains("parar live") || cmd.contains("encerrar live") || cmd.contains("terminar live") -> stopLive()
            cmd.contains("status live") || cmd.contains("tempo de live") || cmd.contains("quantos minutos") -> getLiveStatus()
            cmd.contains("comentário na live") || cmd.contains("comentar na live") -> commentOnLive(cmd)
            else -> "Senhor, diga a plataforma. Ex: 'iniciar live no Instagram', 'iniciar live no TikTok'."
        }
    }

    private fun startInstagramLive(cmd: String): String {
        return try {
            // Abrir Instagram direto na câmara (stories/live)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("instagram://camera")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
            } else {
                val fallback = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
                fallback?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (fallback != null) context.startActivity(fallback)
            }
            isLiveActive = true
            liveStartTime = System.currentTimeMillis()
            livePlatform = "Instagram"
            memory.save("live_active", "true")
            memory.save("live_platform", "Instagram")
            memory.save("live_start", System.currentTimeMillis().toString())

            val topic = extractTopic(cmd)
            buildString {
                append("Senhor, Instagram aberto na câmara. ")
                append("Toque em 'Ao vivo' para iniciar a transmissão. ")
                if (topic != null) append("Tema da live: $topic. ")
                append("Diga 'parar live' quando quiser terminar.")
            }
        } catch (e: Exception) {
            "Senhor, erro ao abrir Instagram: ${e.message}"
        }
    }

    private fun startTikTokLive(cmd: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.zhiliaoapp.musically")
                ?: context.packageManager.getLaunchIntentForPackage("com.ss.android.ugc.trill")
            intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent != null) context.startActivity(intent)

            isLiveActive = true
            liveStartTime = System.currentTimeMillis()
            livePlatform = "TikTok"
            memory.save("live_active", "true")
            memory.save("live_platform", "TikTok")

            val topic = extractTopic(cmd)
            buildString {
                append("Senhor, TikTok aberto. ")
                append("Toque em '+' → 'Ao Vivo' para iniciar. ")
                if (topic != null) append("Sugestão de título: $topic. ")
                append("Nota: Lives no TikTok requerem mínimo 1000 seguidores.")
            }
        } catch (e: Exception) {
            "Senhor, erro ao abrir TikTok: ${e.message}"
        }
    }

    private fun startFacebookLive(cmd: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.facebook.katana")
            intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent != null) context.startActivity(intent)

            isLiveActive = true; livePlatform = "Facebook"
            "Senhor, Facebook aberto. Toque em 'Ao Vivo' para iniciar a transmissão."
        } catch (e: Exception) { "Senhor, erro ao abrir Facebook: ${e.message}" }
    }

    private fun startYouTubeLive(cmd: String): String {
        val uri = Uri.parse("https://studio.youtube.com/channel/live_dashboard")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        isLiveActive = true; livePlatform = "YouTube"
        return "Senhor, YouTube Studio aberto para iniciar live."
    }

    private fun stopLive(): String {
        if (!isLiveActive) return "Senhor, não há nenhuma live ativa."
        val duration = (System.currentTimeMillis() - liveStartTime) / 60000
        isLiveActive = false
        memory.save("live_active", "false")
        val platform = livePlatform.ifBlank { memory.get("live_platform") ?: "desconhecida" }
        return "Senhor, live encerrada. Duração: **$duration minutos** em $platform. Bom trabalho!"
    }

    private fun getLiveStatus(): String {
        return if (isLiveActive || memory.get("live_active") == "true") {
            val start = (memory.get("live_start")?.toLongOrNull() ?: liveStartTime)
            val duration = (System.currentTimeMillis() - start) / 60000
            "Senhor, live ativa há **$duration minutos** no ${memory.get("live_platform") ?: livePlatform}."
        } else {
            "Senhor, não há live ativa no momento."
        }
    }

    private fun commentOnLive(cmd: String): String {
        // Placeholder — dependeria do serviço de acessibilidade ativo na app
        return "Senhor, para comentar na live utilize o serviço de acessibilidade. Diga o comentário que quer colocar e eu tento digitá-lo automaticamente."
    }

    private fun extractTopic(cmd: String): String? {
        val keywords = listOf("sobre ", "tema ", "assunto ", "topic ")
        for (kw in keywords) {
            val idx = cmd.indexOf(kw, ignoreCase = true)
            if (idx != -1) return cmd.substring(idx + kw.length).trim().takeIf { it.isNotBlank() }
        }
        return null
    }
}
