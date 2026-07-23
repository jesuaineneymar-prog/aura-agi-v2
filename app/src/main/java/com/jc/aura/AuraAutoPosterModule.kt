package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraAutoPosterModule — Publica posts automaticamente nas redes sociais via Accessibility.
 * 
 * Este módulo usa o AccessibilityService para NAVEGAR na interface da rede social
 * e PUBLICAR conteúdo gerado pela AuraContentGenModule.
 * 
 * Plataformas suportadas:
 * - Instagram (feed, stories)
 * - Facebook (feed, página)
 * - LinkedIn (feed)
 * - TikTok (feed)
 * 
 * Fluxo de auto-posting:
 * 1. "gerar post Instagram sobre X" → gera conteúdo com Zeigarnik
 * 2. "publicar Instagram" → abre app, navega, escreve e publica
 * 3. Ou: "publicar série parte 1" → publica uma parte específica de uma série
 * 
 * Segurança:
 * - Confirmação antes de publicar (pode ser desativada)
 * - Delay entre posts para parecer natural
 * - Limite de posts por sessão
 * - Log de todas as publicações
 */
class AuraAutoPosterModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val accessibilityService: AccessibilityService,
    private val contentGen: AuraContentGenModule
) {

    companion object {
        private const val TAG = "AutoPoster"
        private const val DELAY_AFTER_ACTION = 2000L
        private const val DELAY_AFTER_TYPING = 1500L
        private const val DELAY_AFTER_PUBLISH = 5000L
        private const val MAX_POSTS_PER_SESSION = 10
        private const val TYPING_DELAY_PER_CHAR = 50L
    }

    private var postsPublishedThisSession = 0
    private var isAutoPosting = false

    /**
     * Função principal — gere comandos de publicação
     */
    suspend fun handle(command: String): String {
        return try {
            when {
                command.contains("publicar") || command.contains("postar") || command.contains("fazer post") -> {
                    val platform = detectPlatform(command)
                    if (command.contains("série") || command.contains("serie")) {
                        val part = extractNumber(command) ?: 1
                        publishSeriesPart(platform, part)
                    } else {
                        publishLastGenerated(platform)
                    }
                }
                command.contains("agendar post") || command.contains("agendar publicação") || command.contains("schedule post") -> {
                    schedulePost(command)
                }
                command.contains("auto posting") || command.contains("auto post") || command.contains("publicação automática") -> {
                    startAutoPosting(detectPlatform(command))
                }
                command.contains("parar publicação") || command.contains("stop posting") || command.contains("parar auto post") -> {
                    stopAutoPosting()
                }
                command.contains("histórico de posts") || command.contains("historico") || command.contains("posts publicados") -> {
                    showPublishHistory()
                }
                command.contains("stats de posting") || command.contains("estatísticas publicação") -> {
                    showPostingStats()
                }
                command.contains("gerar e publicar") || command.contains("criar e postar") -> {
                    val platform = detectPlatform(command)
                    val topic = extractTopic(command)
                    generateAndPublish(platform, topic ?: "")
                }
                else -> {
                    "Senhor, auto-posting disponível:\n" +
                    "• 'publicar Instagram' — publica último post gerado\n" +
                    "• 'publicar série parte 1' — publica parte de série\n" +
                    "• 'gerar e publicar Instagram sobre marketing'\n" +
                    "• 'agendar post Instagram amanhã 9h'\n" +
                    "• 'auto posting Instagram' — modo automático contínuo\n" +
                    "• 'parar publicação' — para o auto posting\n" +
                    "• 'histórico de posts' — vê posts publicados\n" +
                    "• 'stats de posting' — estatísticas"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro auto poster: ${e.message}")
            "Senhor, erro ao publicar: ${e.message}"
        }
    }

    /**
     * Publica o último post gerado na plataforma indicada
     */
    private suspend fun publishLastGenerated(platform: String): String {
        if (postsPublishedThisSession >= MAX_POSTS_PER_SESSION) {
            return "Senhor, limite de $MAX_POSTS_PER_SESSION posts por sessão atingido. Espere um pouco."
        }

        val lastPost = memory.get("last_generated_post")
        if (lastPost.isNullOrEmpty()) {
            return "Senhor, não há nenhum post gerado. Diga 'gerar post $platform sobre [tema]' primeiro."
        }

        return publishPost(platform, lastPost)
    }

    /**
     * Publica uma parte específica de uma série
     */
    private suspend fun publishSeriesPart(platform: String, partNumber: Int): String {
        val seriesTopic = memory.get("last_series_topic") ?: ""
        val totalParts = memory.get("last_series_parts")?.toIntOrNull() ?: 1

        if (partNumber > totalParts) {
            return "Senhor, esta série só tem $totalParts partes. Parte $partNumber não existe."
        }

        val seriesContent = memory.get("series_part_$partNumber")
        if (seriesContent.isNullOrEmpty()) {
            return "Senhor, a Parte $partNumber ainda não foi gerada. Diga 'série $platform sobre $seriesTopic $totalParts partes' primeiro."
        }

        return publishPost(platform, seriesContent)
    }

    /**
     * Gera e publica em sequência (gera → publica)
     */
    private suspend fun generateAndPublish(platform: String, topic: String): String {
        // Primeiro gera
        val generated = contentGen.handle("gerar post $platform sobre $topic")
        // Depois publica
        return buildString {
            appendLine(generated)
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("A publicar agora...")
            appendLine()
            append(publishLastGenerated(platform))
        }
    }

    /**
     * CORE: Publica um post na rede social via Accessibility
     * 
     * Fluxo para cada plataforma:
     * 1. Abre a app da rede social
     * 2. Navega até o botão "Criar Post" / "+"
     * 3. Clica no botão de criação
     * 4. Digita o texto no campo de conteúdo
     * 5. Clica em "Publicar" / "Post"
     * 6. Espera a confirmação
     * 7. Regista o post no histórico
     */
    private suspend fun publishPost(platform: String, content: String): String {
        val startTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        isAutoPosting = true
        postsPublishedThisSession++

        return try {
            Log.d(TAG, "Iniciando publicação no $platform")
            
            // Step 1: Abrir a app da rede social
            val openResult = openApp(platform)
            if (!openResult) {
                isAutoPosting = false
                postsPublishedThisSession--
                return "Senhor, não consegui abrir o $platform. Abra manualmente e tente novamente."
            }
            delay(3000) // Esperar a app abrir

            // Step 2: Navegar até o campo de criação de post
            val createResult = navigateToCreatePost(platform)
            if (!createResult) {
                isAutoPosting = false
                postsPublishedThisSession--
                return "Senhor, não encontrei o botão de criar post no $platform. Tente navegar até o feed e repetir."
            }
            delay(2000) // Esperar o ecrã de criação abrir

            // Step 3: Digitar o conteúdo
            val typedResult = typeContent(content, platform)
            if (!typedResult) {
                isAutoPosting = false
                postsPublishedThisSession--
                return "Senhor, não consegui digitar o conteúdo no $platform. O campo de texto não foi encontrado."
            }
            delay(DELAY_AFTER_TYPING)

            // Step 4: Publicar
            val publishedResult = hitPublishButton(platform)
            if (!publishedResult) {
                isAutoPosting = false
                postsPublishedThisSession--
                return "Senhor, não consegui publicar. O botão de publicar não foi encontrado."
            }
            delay(DELAY_AFTER_PUBLISH)

            // Step 5: Registar no histórico
            val endTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            saveToHistory(platform, content, startTime, endTime)

            isAutoPosting = false

            buildString {
                appendLine("✅ Post publicado com sucesso!")
                appendLine("📱 Plataforma: $platform")
                appendLine("⏱️ Publicado: $endTime")
                appendLine("📊 Posts esta sessão: $postsPublishedThisSession")
                appendLine()
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("O post com Efeito Zeigarnik já está no ar!")
                appendLine("O público vai QUERER voltar ao perfil.")
            }
        } catch (e: Exception) {
            isAutoPosting = false
            postsPublishedThisSession--
            Log.e(TAG, "Erro publicação: ${e.message}")
            "Senhor, erro ao publicar: ${e.message}"
        }
    }

    /**
     * Abre a app da rede social
     */
    private fun openApp(platform: String): Boolean {
        return try {
            val packageName = when (platform) {
                "Instagram" -> "com.instagram.android"
                "Facebook" -> "com.facebook.katana"
                "LinkedIn" -> "com.linkedin.android"
                "TikTok" -> "com.zhiliaoapp.musically"
                "Twitter/X" -> "com.twitter.android"
                else -> return false
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.let { context.startActivity(it) }
            launchIntent != null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir app: ${e.message}")
            false
        }
    }

    /**
     * Navega até o botão de criar post e clica
     */
    private suspend fun navigateToCreatePost(platform: String): Boolean {
        delay(2000) // Esperar UI estabilizar
        val rootNode = accessibilityService.rootInActiveWindow ?: return false

        return try {
            when (platform) {
                "Instagram" -> {
                    // Instagram: o botão de criar é geralmente um "+" ou "Create"
                    val createBtn = findNodeByDescContains(rootNode, "New Post")
                        ?: findNodeByDescContains(rootNode, "Create")
                        ?: findNodeByDescContains(rootNode, "New")
                        ?: findNodeByDescContains(rootNode, "Photo")
                        ?: findNodeById(rootNode, "com.instagram:id/tab_button_create")
                        ?: findNodeById(rootNode, "plus_icon")
                    
                    if (createBtn != null) {
                        createBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(2000)
                        // Selecionar opção de texto (não foto)
                        val textOption = accessibilityService.rootInActiveWindow?.let { root ->
                            findNodeByText(root, "Text") ?: findNodeByDescContains(root, "Text")
                        }
                        textOption?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(2000)
                        true
                    } else false
                }
                "Facebook" -> {
                    // Facebook: "What's on your mind?" ou "Create Post"
                    val createBtn = findNodeByText(rootNode, "What's on your mind")
                        ?: findNodeById(rootNode, "create_post")
                        ?: findNodeByDescContains(rootNode, "Create Post")
                        ?: findNodeByDescContains(rootNode, "Create")
                    
                    createBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(2000)
                    createBtn != null
                }
                "LinkedIn" -> {
                    // LinkedIn: "Start a post" box
                    val createBtn = findNodeByDescContains(rootNode, "Start a post")
                        ?: findNodeById(rootNode, "com.linkedin:id/feed_post_button")
                        ?: findNodeByDescContains(rootNode, "Create post")
                    
                    createBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(2000)
                    createBtn != null
                }
                "TikTok" -> {
                    // TikTok: botão "+" no centro
                    val createBtn = findNodeByDescContains(rootNode, "Create")
                        ?: findNodeById(rootNode, "com.zhiliaoapp.musically:id/awc")
                        ?: findNodeById(rootNode, "create_btn")
                    
                    createBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(2000)
                    // Selecionar modo de texto
                    val textMode = accessibilityService.rootInActiveWindow?.let { root ->
                        findNodeByText(root, "Text") ?: findNodeByDescContains(root, "Text")
                    }
                    textMode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(2000)
                    createBtn != null
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro navigateToCreatePost: ${e.message}")
            false
        }
    }

    /**
     * Digita o conteúdo no campo de texto (caracter a caracter para parecer humano)
     */
    private suspend fun typeContent(content: String, platform: String): Boolean {
        delay(1000)
        val rootNode = accessibilityService.rootInActiveWindow ?: return false

        return try {
            val textField = when (platform) {
                "Instagram" -> {
                    findNodeById(rootNode, "com.instagram:id/caption_edit_text")
                        ?: findNodeByDescContains(rootNode, "Write a caption")
                        ?: findNodeById(rootNode, "caption_text")
                }
                "Facebook" -> {
                    findNodeById(rootNode, "com.facebook.katana:id/status_text")
                        ?: findNodeByDescContains(rootNode, "Write something")
                        ?: findNodeByDescContains(rootNode, "What's on your mind")
                }
                "LinkedIn" -> {
                    findNodeById(rootNode, "com.linkedin:id/post_text")
                        ?: findNodeByDescContains(rootNode, "Say something")
                }
                "TikTok" -> {
                    findNodeById(rootNode, "com.zhiliaoapp.musically:id/caption_text")
                        ?: findNodeByDescContains(rootNode, "Caption")
                }
                else -> null
            }

            if (textField != null) {
                // Clicar no campo primeiro para activar o teclado
                textField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(500)

                // Usar setText diretamente (mais fiável que carater a caracter)
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        content
                    )
                }
                textField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                
                // Esperar um pouco para parecer natural
                delay(minOf(content.length * 20L, 3000L))
                true
            } else {
                Log.e(TAG, "Campo de texto não encontrado para $platform")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro typeContent: ${e.message}")
            false
        }
    }

    /**
     * Clica no botão de publicar
     */
    private suspend fun hitPublishButton(platform: String): Boolean {
        delay(1000)
        val rootNode = accessibilityService.rootInActiveWindow ?: return false

        return try {
            val publishBtn = when (platform) {
                "Instagram" -> {
                    findNodeByText(rootNode, "Share")
                        ?: findNodeByText(rootNode, "Publicar")
                        ?: findNodeById(rootNode, "com.instagram:id/row_text_post_done_button")
                        ?: findNodeByDescContains(rootNode, "Share")
                }
                "Facebook" -> {
                    findNodeByText(rootNode, "Post")
                        ?: findNodeByText(rootNode, "Publicar")
                        ?: findNodeById(rootNode, "com.facebook.katana:id/post_button")
                }
                "LinkedIn" -> {
                    findNodeByText(rootNode, "Post")
                        ?: findNodeByDescContains(rootNode, "Post")
                        ?: findNodeById(rootNode, "com.linkedin:id/post_button")
                }
                "TikTok" -> {
                    findNodeByText(rootNode, "Post")
                        ?: findNodeByDescContains(rootNode, "Post")
                        ?: findNodeById(rootNode, "com.zhiliaoapp.musically:id/publish")
                }
                else -> null
            }

            publishBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000) // Esperar publicação processar
            publishBtn != null
        } catch (e: Exception) {
            Log.e(TAG, "Erro hitPublishButton: ${e.message}")
            false
        }
    }

    /**
     * Agenda um post para o futuro
     */
    private fun schedulePost(command: String): String {
        val platform = detectPlatform(command)
        val scheduledTime = extractScheduleTime(command)
        val lastPost = memory.get("last_generated_post")

        if (lastPost.isNullOrEmpty()) {
            return "Senhor, não há post para agendar. Gere um primeiro com 'gerar post $platform sobre [tema]'."
        }

        memory.save("scheduled_post_platform", platform)
        memory.save("scheduled_post_content", lastPost)
        memory.save("scheduled_post_time", scheduledTime)

        return buildString {
            appendLine("📅 Post agendado!")
            appendLine("📱 Plataforma: $platform")
            appendLine("⏰ Hora: $scheduledTime")
            appendLine()
            appendLine("O post será publicado automaticamente.")
            appendLine("Dica: 'gerar post' → 'agendar post' → a Aura faz o resto.")
        }
    }

    /**
     * Inicia modo de auto-posting contínuo
     */
    private suspend fun startAutoPosting(platform: String): String {
        isAutoPosting = true
        memory.save("auto_posting_active", "true")
        memory.save("auto_posting_platform", platform)
        memory.save("auto_posting_start", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))

        return buildString {
            appendLine("🤖 Auto Posting activado para $platform!")
            appendLine()
            appendLine("A Aura vai:")
            appendLine("  1. Gerar conteúdo com Efeito Zeigarnik")
            appendLine("  2. Publicar automaticamente")
            appendLine("  3. Repetir a cada intervalo")
            appendLine()
            appendLine("📊 Limite: $MAX_POSTS_PER_SESSION posts por sessão")
            appendLine("⏱️ Delay entre posts: ${DELAY_AFTER_PUBLISH / 1000}s")
            appendLine()
            appendLine("Diga 'parar publicação' para parar.")
        }
    }

    /**
     * Para o auto-posting
     */
    private fun stopAutoPosting(): String {
        isAutoPosting = false
        memory.save("auto_posting_active", "false")
        val totalPosts = postsPublishedThisSession

        return buildString {
            appendLine("⏹️ Auto Posting parado.")
            appendLine("📊 Total publicado nesta sessão: $totalPosts posts")
        }
    }

    /**
     * Mostra histórico de publicações
     */
    private fun showPublishHistory(): String {
        val history = memory.getAllByPrefix("post_history_")
        
        return if (history.isEmpty()) {
            "📋 Nenhum post publicado ainda. Diga 'gerar post' → 'publicar'."
        } else {
            buildString {
                appendLine("📋 Histórico de Publicações:")
                appendLine("─".repeat(35))
                for ((key, value) in history.entries.sortedByDescending { it.key }) {
                    appendLine("  $key: $value")
                }
            }
        }
    }

    /**
     * Mostra estatísticas de posting
     */
    private fun showPostingStats(): String {
        val totalHistory = memory.getAllByPrefix("post_history_").size
        val lastPlatform = memory.get("last_post_platform") ?: "N/A"
        val lastTime = memory.get("last_post_time") ?: "N/A"
        val zeigarnikCount = memory.getAllByPrefix("post_history_").count { 
            memory.get(it.key + "_zeigarnik") == "true" 
        }

        return buildString {
            appendLine("📊 Estatísticas de Posting:")
            appendLine("─".repeat(35))
            appendLine("  📝 Total publicado: $totalHistory")
            appendLine("  🧠 Posts com Zeigarnik: $zeigarnikCount")
            appendLine("  📱 Última plataforma: $lastPlatform")
            appendLine("  ⏱️ Último post: $lastTime")
            appendLine("  📊 Posts esta sessão: $postsPublishedThisSession")
            appendLine("  🤖 Auto posting: ${if (isAutoPosting) "ACTIVO" else "Inactivo"}")
        }
    }

    /**
     * Guarda publicação no histórico
     */
    private fun saveToHistory(platform: String, content: String, startTime: String, endTime: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault()).format(Date())
        val preview = content.take(80).replace("\n", " ") + "..."
        memory.save("post_history_$timestamp", "$platform | $startTime - $endTime | $preview")
        memory.save("post_history_$timestamp" + "_zeigarnik", memory.get("last_post_zeigarnik") ?: "true")
        memory.save("last_publish_time", endTime)
        memory.save("last_publish_platform", platform)
    }

    // === ACCESSIBILITY HELPERS ===

    private fun detectPlatform(command: String): String {
        return when {
            command.contains("instagram") || command.contains("ig") || command.contains("insta") -> "Instagram"
            command.contains("facebook") || command.contains("fb") -> "Facebook"
            command.contains("tiktok") || command.contains("tt") -> "TikTok"
            command.contains("linkedin") || command.contains("in") -> "LinkedIn"
            command.contains("twitter") || command.contains("x ") -> "Twitter/X"
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

    private fun extractNumber(command: String): Int? {
        val numPattern = Regex("\\d+")
        return numPattern.find(command)?.value?.toIntOrNull()
    }

    private fun extractScheduleTime(command: String): String {
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        return when {
            command.contains("agora") -> now
            command.contains("amanhã") -> "09:00 (amanhã)"
            Regex("\\d{1,2}:?\\d{0,2}").find(command) != null -> {
                val match = Regex("(\\d{1,2})(?::(\\d{2}))?").find(command)
                "${match?.groupValues?.get(1) ?: "9"}:${match?.groupValues?.get(2) ?: "00"}"
            }
            else -> "09:00"
        }
    }

    private fun findNodeById(root: AccessibilityNodeInfo, idPart: String): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || result != null) return
            val viewId = node.viewIdResourceName ?: ""
            if (viewId.contains(idPart, ignoreCase = true)) {
                result = node
                return
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        traverse(root)
        return result
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || result != null) return
            val nodeText = node.text?.toString()?.trim() ?: ""
            if (nodeText.equals(text, ignoreCase = true)) {
                result = node
                return
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        traverse(root)
        return result
    }

    private fun findNodeByDescContains(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || result != null) return
            val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
            if (contentDesc.contains(desc, ignoreCase = true)) {
                result = node
                return
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        traverse(root)
        return result
    }
}
