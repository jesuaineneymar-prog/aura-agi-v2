package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraSocialAutoReplyModule — Responde automaticamente a comentários e envia DMs
 * em Instagram, Facebook, TikTok e LinkedIn.
 * 
 * Funcionalidades:
 * - Responder a TODOS os comentários de uma publicação
 * - Enviar DMs em massa a partir de ficheiro CSV
 * - Respostas geradas por IA que parecem humanas
 * - Funciona via AccessibilityService (navegação na árvore de acessibilidade)
 */
class AuraSocialAutoReplyModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val accessibilityService: AccessibilityService,
    private val mwangoBrain: AuraMwangoBrainModule
) {

    companion object {
        private const val TAG = "SocialAutoReply"
        private const val DELAY_BETWEEN_ACTIONS = 2000L  // 2 segundos entre ações
        private const val DELAY_BETWEEN_REPLIES = 5000L  // 5 segundos entre respostas
        private const val DELAY_AFTER_TYPE = 1500L       // 1.5 segundos depois de digitar
        private const val MAX_COMMENTS_PER_SESSION = 50   // Segurança: máximo 50 comentários por sessão
    }

    // API Keys (injected via BuildConfig)
    private val openRouterKey = BuildConfig.OPENROUTER_KEY
    private val geminiApiKey = BuildConfig.GEMINI_KEY

    /**
     * Ponto de entrada principal para todos os comandos de auto-reply
     */
    suspend fun handle(command: String): String {
        return when {
            // === RESPONDER COMENTÁRIOS ===
            command.contains("responder todos") || command.contains("responder comentário") || command.contains("auto reply") -> {
                val platform = detectPlatform(command)
                replyToAllComments(platform)
            }
            command.contains("responder último comentário") -> {
                val platform = detectPlatform(command) ?: "Instagram"
                replyToLastComment(platform)
            }

            // === ENVIAR DMs ===
            command.contains("campanha dm") || command.contains("enviar dm") || command.contains("mandar dm") || command.contains("mandar mensagens") || command.contains("enviar mensagens") -> {
                val platform = detectPlatform(command)
                val source = detectCSVSource(command)
                startDMCampaign(platform, source)
            }

            // === CSV ===
            command.contains("abrir csv") || command.contains("ler csv") || command.contains("carregar csv") -> {
                readCSVFile(command)
            }
            command.contains("listar csv") || command.contains("ver csv") -> {
                listLoadedProfiles()
            }

            // === MWANGO INFO ===
            command.contains("mwango") -> {
                mwangoBrain.handleCommand(command, openRouterKey)
            }

            else -> "Senhor, comando de redes sociais não reconhecido. Opções:\n" +
                    "• 'responder todos os comentários do Instagram'\n" +
                    "• 'campanha DM Instagram com ficheiro leads.csv'\n" +
                    "• 'abrir ficheiro leads.csv'"
        }
    }

    /**
     * Detecta qual plataforma social foi mencionada no comando
     */
    private fun detectPlatform(command: String): String {
        return when {
            command.contains("instagram") || command.contains("ig") -> "Instagram"
            command.contains("facebook") || command.contains("fb") -> "Facebook"
            command.contains("tiktok") || command.contains("tt") -> "TikTok"
            command.contains("linkedin") || command.contains("in") -> "LinkedIn"
            else -> "Instagram"
        }
    }

    /**
     * Detecta a fonte de dados CSV mencionada no comando
     */
    private fun detectCSVSource(command: String): String {
        // Procurar referências a ficheiros
        val patterns = listOf("ficheiro", "arquivo", "csv", "leads", "perfis", "prospecção", "prospeccao")
        for (pattern in patterns) {
            val idx = command.indexOf(pattern, ignoreCase = true)
            if (idx != -1) {
                // Tentar extrair o nome do ficheiro depois da palavra-chave
                val after = command.substring(idx + pattern.length).trim()
                    .replace("com ", "").replace("do ", "").replace("da ", "")
                    .replace("de ", "").trim()
                if (after.isNotBlank()) return after
            }
        }
        return "leads.csv"
    }

    // =====================================================
    // RESPOSTA AUTOMÁTICA A COMENTÁRIOS
    // =====================================================

    /**
     * Responde a todos os comentários de uma publicação.
     * Usa a IA para gerar respostas personalizadas e humanas.
     */
    private suspend fun replyToAllComments(platform: String): String {
        val service = accessibilityService
        val rootNode = service.rootInActiveWindow ?: return "Senhor, não consigo ver o ecrã. Abra o $platform primeiro."

        // Guardar contexto
        memory.save("social_reply_platform", platform)
        memory.save("social_reply_active", "true")
        memory.save("social_reply_start", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))

        // Navegar para os comentários da publicação
        val navigated = navigateToComments(rootNode, platform)
        if (!navigated) {
            return "Senhor, não consegui encontrar os comentários. Abra uma publicação no $platform primeiro."
        }

        // Scroll para carregar comentários
        delay(2000)

        // Coletar comentários visíveis
        val comments = collectVisibleComments(rootNode, platform)
        if (comments.isEmpty()) {
            return "Senhor, não encontrei comentários visíveis nesta publicação."
        }

        // Limitar por segurança
        val toReply = comments.take(MAX_COMMENTS_PER_SESSION)

        // Gerar e enviar respostas
        var repliedCount = 0
        var failedCount = 0

        for ((username, commentText) in toReply) {
            try {
                // Gerar resposta com IA
                val replyText = generateHumanReply(commentText, platform, username)
                if (replyText.isBlank()) continue

                Log.d(TAG, "Respondendo a @$username: '$commentText' -> '$replyText'")

                // Enviar resposta
                val sent = sendReply(rootNode, replyText, platform)
                if (sent) {
                    repliedCount++
                    memory.save("social_reply_count", repliedCount.toString())
                } else {
                    failedCount++
                }

                // Delay entre respostas (anti-spam)
                delay(DELAY_BETWEEN_REPLIES + (Math.random() * 3000).toLong())

                // Refresh root node
                val freshRoot = service.rootInActiveWindow
                if (freshRoot != null) {
                    // Scroll down para ver mais comentários
                    scrollDownComments(freshRoot, platform)
                    delay(DELAY_BETWEEN_ACTIONS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao responder @$username: ${e.message}")
                failedCount++
                delay(2000)
            }
        }

        memory.save("social_reply_active", "false")
        memory.save("social_reply_end", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))

        val totalComments = comments.size
        return buildString {
            appendLine("Senhor, operação concluída no $platform:")
            appendLine("  💬 Comentários encontrados: $totalComments")
            appendLine("  ✅ Respostas enviadas: $repliedCount")
            if (failedCount > 0) appendLine("  ❌ Falhas: $failedCount")
            if (totalComments > MAX_COMMENTS_PER_SESSION) {
                appendLine("  ⚠️ Limitado a $MAX_COMMENTS_PER_SESSION por sessão (restante: ${totalComments - MAX_COMMENTS_PER_SESSION})")
            }
            appendLine("  ⏱️ Duração: ${memory.get("social_reply_start")} - ${memory.get("social_reply_end")}")
        }
    }

    /**
     * Responde apenas ao último comentário
     */
    private suspend fun replyToLastComment(platform: String): String {
        val rootNode = accessibilityService.rootInActiveWindow
            ?: return "Senhor, não consigo ver o ecrã."

        val comments = collectVisibleComments(rootNode, platform)
        if (comments.isEmpty()) {
            return "Senhor, sem comentários visíveis."
        }

        val (username, commentText) = comments.last()
        val replyText = generateHumanReply(commentText, platform, username)
        if (replyText.isBlank()) return "Senhor, não consegui gerar uma resposta."

        val sent = sendReply(rootNode, replyText, platform)
        return if (sent) {
            "Senhor, respondi a @$username: '$replyText'"
        } else {
            "Senhor, não consegui enviar a resposta."
        }
    }

    // =====================================================
    // CAMPANHA DE DMs
    // =====================================================

    /**
     * Inicia uma campanha de envio de DMs a partir de um ficheiro CSV
     */
    private suspend fun startDMCampaign(platform: String, csvSource: String): String {
        // Ler os perfis do CSV
        val profiles = loadProfilesFromCSV(csvSource)
        if (profiles.isEmpty()) {
            return "Senhor, não consegui carregar perfis do ficheiro '$csvSource'. Verifique se o ficheiro existe e tem o formato correto (nome, username, contexto)."
        }

        val toMessage = profiles.take(100) // Limitar a 100 DMs por segurança
        memory.save("dm_campaign_platform", platform)
        memory.save("dm_campaign_active", "true")
        memory.save("dm_campaign_total", toMessage.size.toString())
        memory.save("dm_campaign_start", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))

        var sentCount = 0
        var failedCount = 0

        for ((index, profile) in toMessage.withIndex()) {
            try {
                val username = profile["username"] ?: profile["nome"] ?: "desconhecido"
                val contextInfo = profile.entries.joinToString(", ") { "${it.key}: ${it.value}" }

                Log.d(TAG, "DM [$index+1/${toMessage.size}] para @$username")

                // Gerar mensagem personalizada
                val message = generateDMMesssage(contextInfo, platform)
                if (message.isBlank()) continue

                // Navegar ao perfil
                val rootNode = accessibilityService.rootInActiveWindow
                if (rootNode != null) {
                    val opened = navigateToProfileAndDM(rootNode, username, platform)
                    if (opened) {
                        delay(2000)
                        val dmRoot = accessibilityService.rootInActiveWindow
                        if (dmRoot != null) {
                            val sent = typeAndSendDM(dmRoot, message, platform)
                            if (sent) {
                                sentCount++
                                memory.save("dm_campaign_sent", sentCount.toString())
                            } else {
                                failedCount++
                            }
                        }
                    } else {
                        failedCount++
                    }
                } else {
                    failedCount++
                }

                // Delay entre DMs (muito importante para não ser banido)
                delay(8000L + (Math.random() * 7000).toLong()) // 8-15 segundos aleatórios

            } catch (e: Exception) {
                Log.e(TAG, "Erro DM para ${profile["username"]}: ${e.message}")
                failedCount++
                delay(5000)
            }
        }

        memory.save("dm_campaign_active", "false")
        memory.save("dm_campaign_end", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        return buildString {
            appendLine("Senhor, campanha DM no $platform concluída:")
            appendLine("  📊 Perfis carregados: ${profiles.size}")
            appendLine("  ✅ DMs enviados: $sentCount")
            if (failedCount > 0) appendLine("  ❌ Falhas: $failedCount")
            appendLine("  ⏱️ Período: ${memory.get("dm_campaign_start")} - ${memory.get("dm_campaign_end")}")
        }
    }

    // =====================================================
    // NAVEGAÇÃO POR ACESSIBILIDADE
    // =====================================================

    /**
     * Navega para a secção de comentários dependendo da plataforma
     */
    private suspend fun navigateToComments(root: AccessibilityNodeInfo, platform: String): Boolean {
        try {
            when (platform) {
                "Instagram" -> {
                    // Procurar e clicar no ícone de comentários
                    findAndClickByDesc(root, "comment") || findAndClickByText(root, "View all comments") || findAndClickById(root, "comment_icon")
                }
                "Facebook" -> {
                    findAndClickByText(root, "comment") || findAndClickByDesc(root, "Comment")
                }
                "TikTok" -> {
                    findAndClickByDesc(root, "comment") || findAndClickById(root, "comment_button")
                }
                "LinkedIn" -> {
                    findAndClickByText(root, "Comment") || findAndClickByDesc(root, "comment")
                }
                else -> false
            }
            delay(DELAY_BETWEEN_ACTIONS)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro navigating: ${e.message}")
            false
        }
    }

    /**
     * Coleta comentários visíveis na árvore de acessibilidade
     */
    private fun collectVisibleComments(root: AccessibilityNodeInfo, platform: String): List<Pair<String, String>> {
        val comments = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()

        fun traverseNode(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val viewId = node.viewIdResourceName ?: ""

            // Detectar comentários por padrões específicos de cada plataforma
            val isComment = when (platform) {
                "Instagram" -> {
                    viewId.contains("comment_content") || viewId.contains("comment_text") ||
                    (text.isNotBlank() && !text.startsWith("@mwangobrain") && 
                     node.childCount == 0 && text.length > 3 && text.length < 500)
                }
                "Facebook" -> {
                    desc.contains("Comment by") || viewId.contains("comment") ||
                    (text.isNotBlank() && !text.contains("Like") && !text.contains("Reply"))
                }
                "TikTok" -> {
                    viewId.contains("comment") && text.isNotBlank() && text.length > 2
                }
                "LinkedIn" -> {
                    viewId.contains("comment") && text.isNotBlank()
                }
                else -> text.isNotBlank() && text.length > 3
            }

            if (isComment && text !in seen) {
                // Tentar extrair username e texto
                val parts = text.split(" ", limit = 2)
                val username = if (parts[0].startsWith("@")) parts[0].removePrefix("@") else "user"
                val commentText = if (parts.size > 1) parts.drop(1).joinToString(" ") else text
                comments.add(username to commentText)
                seen.add(text)
            }

            for (i in 0 until node.childCount) {
                traverseNode(node.getChild(i))
            }
        }

        traverseNode(root)
        return comments
    }

    /**
     * Envia uma resposta a um comentário
     */
    private suspend fun sendReply(root: AccessibilityNodeInfo, replyText: String, platform: String): Boolean {
        try {
            when (platform) {
                "Instagram" -> {
                    // Procurar campo de resposta e digitar
                    val replyField = findNodeByDesc(root, "Add a comment") 
                        ?: findNodeById(root, "comment_text_input")
                    if (replyField != null) {
                        typeTextInNode(replyField, replyText)
                        delay(DELAY_AFTER_TYPE)
                        // Clicar em Post
                        findAndClickByText(root, "Post") || findAndClickByDesc(root, "Post")
                        true
                    } else false
                }
                "Facebook" -> {
                    val replyField = findNodeByDesc(root, "Write a comment")
                        ?: findNodeByDesc(root, "Comment")
                    if (replyField != null) {
                        typeTextInNode(replyField, replyText)
                        delay(DELAY_AFTER_TYPE)
                        findAndClickByDesc(root, "Submit") || findAndClickById(root, "submit")
                        true
                    } else false
                }
                "TikTok" -> {
                    val replyField = findNodeByDesc(root, "Add a comment") 
                        ?: findNodeById(root, "comment_input")
                    if (replyField != null) {
                        typeTextInNode(replyField, replyText)
                        delay(DELAY_AFTER_TYPE)
                        findAndClickByDesc(root, "Send") || findAndClickByText(root, "Send")
                        true
                    } else false
                }
                "LinkedIn" -> {
                    val replyField = findNodeByDesc(root, "Add a comment") 
                        ?: findNodeByDesc(root, "Write a comment")
                    if (replyField != null) {
                        typeTextInNode(replyField, replyText)
                        delay(DELAY_AFTER_TYPE)
                        findAndClickByDesc(root, "Post") || findAndClickByText(root, "Post")
                        true
                    } else false
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro sendReply: ${e.message}")
            false
        }
    }

    /**
     * Navega ao perfil de um utilizador e abre DM
     */
    private suspend fun navigateToProfileAndDM(root: AccessibilityNodeInfo, username: String, platform: String): Boolean {
        try {
            // Procurar barra de pesquisa
            val searchBar = findNodeByDesc(root, "Search") 
                ?: findNodeById(root, "search_button")
                ?: findNodeByDesc(root, "Pesquisar")
            if (searchBar != null) {
                searchBar.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(DELAY_BETWEEN_ACTIONS)
                val freshRoot = accessibilityService.rootInActiveWindow
                if (freshRoot != null) {
                    val searchInput = findNodeById(freshRoot, "search_edit_text") 
                        ?: findNodeByDesc(freshRoot, "Search")
                    if (searchInput != null) {
                        typeTextInNode(searchInput, "@$username")
                        delay(DELAY_AFTER_TYPE)
                        // Clicar no primeiro resultado
                        findAndClickById(freshRoot, "row_search_user") 
                            || findAndClickByText(freshRoot, username)
                        delay(DELAY_BETWEEN_ACTIONS * 2)
                        // Clicar em Message
                        val profileRoot = accessibilityService.rootInActiveWindow
                        if (profileRoot != null) {
                            findAndClickByDesc(profileRoot, "Message") 
                                || findAndClickByText(profileRoot, "Message")
                                || findAndClickByDesc(profileRoot, "Enviar mensagem")
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro navigateToProfile: ${e.message}")
            false
        }
    }

    /**
     * Digita texto no campo de mensagem DM e envia
     */
    private suspend fun typeAndSendDM(root: AccessibilityNodeInfo, message: String, platform: String): Boolean {
        try {
            val messageField = findNodeByDesc(root, "Message") 
                ?: findNodeById(root, "message_text_input")
                ?: findNodeByDesc(root, "Write a message")
            if (messageField != null) {
                typeTextInNode(messageField, message)
                delay(DELAY_AFTER_TYPE)
                findAndClickByDesc(root, "Send") || findAndClickById(root, "send_button")
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Erro typeAndSendDM: ${e.message}")
            false
        }
    }

    /**
     * Scroll para baixo nos comentários
     */
    private fun scrollDownComments(root: AccessibilityNodeInfo, platform: String) {
        try {
            val scrollable = findScrollableNode(root)
            scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } catch (_: Exception) {}
    }

    // =====================================================
    // UTILITÁRIOS DE ACESSIBILIDADE
    // =====================================================

    private fun findNodeByDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(desc)
        return nodes?.firstOrNull()
            ?: findNodeByContentDescription(root, desc)
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val cd = node.contentDescription?.toString() ?: ""
        if (cd.contains(desc, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByContentDescription(node.getChild(i), desc)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeById(node: AccessibilityNodeInfo, idPart: String): AccessibilityNodeInfo? {
        val viewId = node.viewIdResourceName ?: ""
        if (viewId.contains(idPart, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val found = findNodeById(node.getChild(i), idPart)
            if (found != null) return found
        }
        return null
    }

    private fun findAndClickByDesc(root: AccessibilityNodeInfo, desc: String): Boolean {
        val node = findNodeByDesc(root, desc)
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findAndClickByText(root: AccessibilityNodeInfo, text: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val node = nodes?.firstOrNull()
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findAndClickById(root: AccessibilityNodeInfo, id: String): Boolean {
        val node = findNodeById(root, id)
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun typeTextInNode(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    // =====================================================
    // GERAÇÃO DE RESPOSTAS COM IA (HUMANAS E NATURAIS)
    // =====================================================

    /**
     * Gera uma resposta humana usando a IA (OpenRouter / Gemini)
     */
    private suspend fun generateHumanReply(comment: String, platform: String, username: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = mwangoBrain.buildReplyPrompt(comment, platform, isDM = false)

                // Tentar OpenRouter primeiro, depois Gemini como fallback
                var response = callOpenRouter(prompt, "meta-llama/llama-3.3-70b-instruct")
                if (response.isBlank()) {
                    response = callGemini(prompt)
                }

                // Limpar a resposta (remover aspas e limitar tamanho)
                response.trim('"').trim().take(300)
            } catch (e: Exception) {
                Log.e(TAG, "Erro generateReply: ${e.message}")
                ""
            }
        }
    }

    /**
     * Gera uma mensagem DM personalizada usando IA
     */
    private suspend fun generateDMMesssage(contextInfo: String, platform: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = mwangoBrain.buildReplyPrompt(contextInfo, platform, isDM = true)

                var response = callOpenRouter(prompt, "deepseek/deepseek-v4-pro")
                if (response.isBlank()) {
                    response = callGemini(prompt)
                }

                response.trim('"').trim().take(500)
            } catch (e: Exception) {
                Log.e(TAG, "Erro generateDM: ${e.message}")
                ""
            }
        }
    }

    private fun callOpenRouter(prompt: String, model: String): String {
        try {
            val url = URL("https://openrouter.ai/api/v1/chat/completions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            val body = JSONObject().apply {
                put("model", model)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", 200)
                put("temperature", 0.85) // Alta criatividade para respostas naturais
            }

            connection.outputStream.write(body.toString().toByteArray())
            val responseCode = connection.responseCode

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } else ""
            connection.disconnect()
        } catch (_: Exception) {
        }
        return ""
    }

    private fun callGemini(prompt: String): String {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiApiKey")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            val body = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 200)
                    put("temperature", 0.85)
                })
            }

            connection.outputStream.write(body.toString().toByteArray())
            val responseCode = connection.responseCode

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val candidates = json.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    if (parts.length() > 0) {
                        parts.getJSONObject(0).getString("text")
                    } else ""
                } else ""
            } else ""
            connection.disconnect()
        } catch (_: Exception) {
        }
        return ""
    }

    // =====================================================
    // LEITURA DE FICHEIROS CSV
    // =====================================================

    /**
     * Lê um ficheiro CSV do armazenamento do dispositivo
     */
    private suspend fun readCSVFile(command: String): String {
        val fileName = extractFileName(command) ?: "leads.csv"
        return withContext(Dispatchers.IO) {
            try {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val file = java.io.File(dir, fileName)

                if (!file.exists()) {
                    // Tentar no Download
                    val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val altFile = java.io.File(downloadDir, fileName)
                    if (!altFile.exists()) {
                        return@withContext "Senhor, ficheiro '$fileName' não encontrado. Coloque-o na pasta Downloads ou em Files/Aura."
                    }
                    parseAndStoreCSV(altFile, fileName)
                } else {
                    parseAndStoreCSV(file, fileName)
                }
            } catch (e: Exception) {
                "Senhor, erro ao ler ficheiro: ${e.message}"
            }
        }
    }

    private fun parseAndStoreCSV(file: java.io.File, fileName: String): String {
        val lines = file.readLines()
        if (lines.isEmpty()) return "Senhor, ficheiro vazio: $fileName"

        // Primeira linha = cabeçalhos
        val headers = lines[0].split(",").map { it.trim().lowercase() }
        var loadedCount = 0

        for (i in 1 until lines.size.coerceAtMost(101)) { // Máx 100 perfis
            val values = lines[i].split(",").map { it.trim() }
            if (values.size >= 2) {
                val profile = mutableMapOf<String, String>()
                headers.forEachIndexed { idx, header ->
                    if (idx < values.size) profile[header] = values[idx]
                }
                memory.save("csv_profile_${i}", profile.entries.joinToString("|") { "${it.key}=${it.value}" })
                loadedCount++
            }
        }

        memory.save("csv_total_profiles", loadedCount.toString())
        memory.save("csv_source_file", fileName)

        return "Senhor, carregados $loadedCount perfis de '$fileName'. Colunas: ${headers.joinToString(", ")}."
    }

    private fun extractFileName(command: String): String? {
        val keywords = listOf("ficheiro ", "arquivo ", "csv ")
        for (kw in keywords) {
            val idx = command.indexOf(kw, ignoreCase = true)
            if (idx != -1) {
                return command.substring(idx + kw.length).trim()
            }
        }
        return null
    }

    private fun listLoadedProfiles(): String {
        val total = memory.get("csv_total_profiles")?.toIntOrNull() ?: 0
        val file = memory.get("csv_source_file") ?: "N/A"

        return if (total == 0) {
            "Senhor, nenhum perfil carregado. Use 'abrir ficheiro leads.csv' primeiro."
        } else {
            val sb = StringBuilder("Perfis carregados de '$file':\n")
            sb.appendLine("  Total: $total perfis")
            sb.appendLine("  Origem: $file")
            // Mostrar primeiros 5
            for (i in 1..total.coerceAtMost(5)) {
                val profile = memory.get("csv_profile_$i") ?: continue
                sb.appendLine("  $i. $profile")
            }
            if (total > 5) sb.appendLine("  ... e mais ${total - 5} perfis")
            sb.toString()
        }
    }

    private fun loadProfilesFromCSV(csvSource: String): List<Map<String, String>> {
        val profiles = mutableListOf<Map<String, String>>()
        val total = memory.get("csv_total_profiles")?.toIntOrNull() ?: 0

        for (i in 1..total) {
            val raw = memory.get("csv_profile_$i") ?: continue
            val profile = mutableMapOf<String, String>()
            raw.split("|").forEach { entry ->
                val parts = entry.split("=", limit = 2)
                if (parts.size == 2) {
                    profile[parts[0].trim()] = parts[1].trim()
                }
            }
            if (profile.isNotEmpty()) profiles.add(profile)
        }

        return profiles
    }
}
