package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraProactiveEngagementModule — Engajamento proativo em redes sociais.
 * 
 * Funcionalidades:
 * - Curtir posts de perfis-alvo automaticamente
 * - Seguir perfis relevantes em massa
 * - Comentar em posts estratégicos
 * - Engajar com conteúdo de hashtags
 * - Monitorar menções à marca
 * - Tudo via AccessibilityService (simulação de toque humano)
 */
class AuraProactiveEngagementModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val accessibilityService: AccessibilityService,
    private val mwangoBrain: AuraMwangoBrainModule
) {

    companion object {
        private const val TAG = "ProactiveEngage"
        private const val DELAY_BETWEEN_ACTIONS = 3000L
        private const val DELAY_AFTER_LIKE = 1500L
        private const val DELAY_AFTER_FOLLOW = 4000L
        private const val MAX_ACTIONS_PER_SESSION = 30
    }

    suspend fun handle(command: String): String {
        return try {
            when {
                command.contains("auto like") || command.contains("curtir tudo") || command.contains("auto-like") -> {
                    val platform = detectPlatform(command)
                    val count = extractNumber(command) ?: 20
                    autoLikePosts(platform, count)
                }
                command.contains("auto seguir") || command.contains("auto follow") || command.contains("seguir todos") -> {
                    val platform = detectPlatform(command)
                    val count = extractNumber(command) ?: 15
                    autoFollowProfiles(platform, count)
                }
                command.contains("auto comentar") || command.contains("comentar em massa") -> {
                    val platform = detectPlatform(command)
                    val count = extractNumber(command) ?: 10
                    autoCommentOnPosts(platform, count)
                }
                command.contains("engajar hashtag") || command.contains("engajar com hashtag") -> {
                    val platform = detectPlatform(command)
                    engageWithHashtag(platform, command)
                }
                command.contains("monitorar menções") || command.contains("monitorar marca") || command.contains("brand monitoring") -> {
                    monitorBrandMentions()
                }
                command.contains("engajamento automático") || command.contains("engajamento total") -> {
                    val platform = detectPlatform(command)
                    fullEngagementSession(platform)
                }
                else -> {
                    "Senhor, engajamento proativo disponível:\n" +
                    "• 'auto like Instagram 20'\n" +
                    "• 'auto seguir LinkedIn 15'\n" +
                    "• 'auto comentar TikTok 10'\n" +
                    "• 'engajar hashtag #DesignAngola no Instagram'\n" +
                    "• 'monitorar menções à marca'\n" +
                    "• 'engajamento total Facebook'"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro proactive: ${e.message}")
            "Senhor, erro no engajamento: ${e.message}"
        }
    }

    /**
     * Curtir posts automaticamente
     */
    private suspend fun autoLikePosts(platform: String, targetCount: Int): String {
        val rootNode = accessibilityService.rootInActiveWindow
            ?: return "Senhor, não consigo aceder ao ecrã. Abra o $platform primeiro."

        var likedCount = 0
        val startTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        memory.save("auto_like_platform", platform)
        memory.save("auto_like_start", startTime)

        for (i in 0 until minOf(targetCount, MAX_ACTIONS_PER_SESSION)) {
            try {
                val freshRoot = accessibilityService.rootInActiveWindow ?: break
                val doubleTapView = findViewToDoubleTap(freshRoot, platform)
                if (doubleTapView != null) {
                    doubleTapView.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(DELAY_AFTER_LIKE)
                    // Second tap for double-tap like (Instagram/TikTok)
                    doubleTapView.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    likedCount++
                    memory.save("auto_like_count", likedCount.toString())
                    delay(DELAY_BETWEEN_ACTIONS)
                } else {
                    // Try single tap on like button
                    val likeBtn = findLikeButton(freshRoot, platform)
                    if (likeBtn != null) {
                        likeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        likedCount++
                        memory.save("auto_like_count", likedCount.toString())
                        delay(DELAY_BETWEEN_ACTIONS)
                    } else {
                        scrollDown(freshRoot)
                        delay(DELAY_AFTER_LIKE)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro like #$i: ${e.message}")
                delay(2000)
            }
        }

        val endTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        memory.save("auto_like_end", endTime)

        return buildString {
            appendLine("👍 Auto Like $platform concluído:")
            appendLine("  📊 Curtidas dadas: $likedCount")
            appendLine("  ⏱️ Período: $startTime - $endTime")
            appendLine("  📱 Plataforma: $platform")
        }
    }

    /**
     * Seguir perfis automaticamente
     */
    private suspend fun autoFollowProfiles(platform: String, targetCount: Int): String {
        val rootNode = accessibilityService.rootInActiveWindow
            ?: return "Senhor, não consigo aceder ao ecrã."

        var followedCount = 0
        val startTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        for (i in 0 until minOf(targetCount, MAX_ACTIONS_PER_SESSION)) {
            try {
                val freshRoot = accessibilityService.rootInActiveWindow ?: break
                val followBtn = findFollowButton(freshRoot, platform)
                if (followBtn != null) {
                    followBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    followedCount++
                    delay(DELAY_AFTER_FOLLOW)
                    // Voltar ao feed
                    val backBtn = findBackButton(freshRoot)
                    backBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(DELAY_BETWEEN_ACTIONS)
                    scrollDown(freshRoot)
                } else {
                    scrollDown(freshRoot)
                    delay(DELAY_BETWEEN_ACTIONS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro follow #$i: ${e.message}")
                delay(2000)
            }
        }

        return buildString {
            appendLine("👤 Auto Follow $platform concluído:")
            appendLine("  📊 Perfis seguidos: $followedCount")
            appendLine("  ⏱️ Início: $startTime")
        }
    }

    /**
     * Comentar em posts automaticamente
     */
    private suspend fun autoCommentOnPosts(platform: String, targetCount: Int): String {
        val comments = listOf(
            "Excelente conteúdo! 🔥",
            "Muito inspirador, parabéns!",
            "Isso é exactamente o que precisávamos ouvir 💪",
            "Partilhar conhecimento é sempre valioso. Obrigado!",
            "Que trabalho incrível! Sucesso!",
            "Conteúdo de qualidade como sempre 👏",
            "Isso faz-me pensar diferente sobre o tema",
            "Adorei a perspectiva, muito bem explicado!"
        )

        val rootNode = accessibilityService.rootInActiveWindow
            ?: return "Senhor, abra o $platform primeiro."

        var commentedCount = 0
        val startTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        for (i in 0 until minOf(targetCount, MAX_ACTIONS_PER_SESSION)) {
            try {
                val freshRoot = accessibilityService.rootInActiveWindow ?: break
                
                // Find comment button
                val commentBtn = findCommentButton(freshRoot, platform)
                if (commentBtn != null) {
                    commentBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(2000)

                    // Find text field and type comment
                    val commentRoot = accessibilityService.rootInActiveWindow
                    if (commentRoot != null) {
                        val textField = findCommentTextField(commentRoot, platform)
                        if (textField != null) {
                            val randomComment = comments[Random().nextInt(comments.size)]
                            setTextField(textField, randomComment)
                            delay(1500)

                            // Find and click post/send
                            val postBtn = findPostButton(commentRoot, platform)
                            postBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            commentedCount++
                            delay(DELAY_BETWEEN_ACTIONS)

                            // Go back
                            val backBtn = findBackButton(commentRoot)
                            backBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            delay(2000)
                        }
                    }
                }

                // Scroll to next post
                val scrollRoot = accessibilityService.rootInActiveWindow
                scrollRoot?.let { scrollDown(it) }
                delay(2000)
            } catch (e: Exception) {
                Log.e(TAG, "Erro comment #$i: ${e.message}")
                delay(2000)
            }
        }

        return buildString {
            appendLine("💬 Auto Comment $platform concluído:")
            appendLine("  📊 Comentários: $commentedCount")
            appendLine("  ⏱️ Início: $startTime")
        }
    }

    /**
     * Engajar com conteúdo de uma hashtag específica
     */
    private suspend fun engageWithHashtag(platform: String, command: String): String {
        val hashtag = extractHashtag(command) ?: "#DesignAngola"
        val rootNode = accessibilityService.rootInActiveWindow
            ?: return "Senhor, abra o $platform primeiro."

        // Reportar início
        memory.save("hashtag_engage", "$hashtag on $platform")
        return "🔍 Iniciando engajamento com $hashtag no $platform...\n\n" +
            "A curtir e comentar nos primeiros 10 posts. " +
            "Pode dizer 'parar' a qualquer momento."
    }

    /**
     * Monitora menções à marca
     */
    private fun monitorBrandMentions(): String {
        val mentions = memory.get("brand_mentions")
        val lastCheck = memory.get("brand_mentions_check")

        return buildString {
            appendLine("🔍 Monitoramento de Marca - Mwango Brain")
            appendLine()
            appendLine("📋 Palavras-chave monitorizadas:")
            appendLine("  • Mwango Brain")
            appendLine("  • MwangoBrain")
            appendLine("  • #MwangoBrain")
            appendLine("  • #LetsBrainTogether")
            appendLine("  • mwangobrain.com")
            appendLine()
            appendLine("📱 Plataformas: Instagram, Facebook, TikTok, LinkedIn")
            appendLine()
            appendLine("📌 Última verificação: ${lastCheck ?: "Ainda não verificada"}")
            appendLine("📢 Menções encontradas: ${mentions ?: "0"}")
            appendLine()
            appendLine("Dica: Active o monitoramento automático com:")
            appendLine("'ativar monitoramento automático'")
        }
    }

    /**
     * Sessão completa de engajamento (like + comment + follow)
     */
    private suspend fun fullEngagementSession(platform: String): String {
        memory.save("engagement_session_active", "true")
        memory.save("engagement_session_platform", platform)
        memory.save("engagement_session_start", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))

        val rootNode = accessibilityService.rootInActiveWindow
            ?: return "Senhor, abra o $platform primeiro."

        val totalActions = mutableMapOf<String, Int>("likes" to 0, "comments" to 0, "follows" to 0)

        for (i in 0 until MAX_ACTIONS_PER_SESSION) {
            try {
                val freshRoot = accessibilityService.rootInActiveWindow ?: break

                // 1. Like
                val likeBtn = findLikeButton(freshRoot, platform)
                if (likeBtn != null) {
                    likeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    totalActions["likes"] = totalActions["likes"]!! + 1
                    delay(DELAY_AFTER_LIKE)
                }

                // 2. Occasionally comment (every 3rd post)
                if (i % 3 == 0) {
                    val commentBtn = findCommentButton(freshRoot, platform)
                    if (commentBtn != null) {
                        commentBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(1500)
                        val commentRoot = accessibilityService.rootInActiveWindow
                        if (commentRoot != null) {
                            val textField = findCommentTextField(commentRoot, platform)
                            if (textField != null) {
                                val quickComments = listOf(
                                    "Top! 🔥", "Muito bom! 👏", "Excelente 💪",
                                    "Inspirador!", "Conteúdo incrível ✨"
                                )
                                setTextField(textField, quickComments[Random().nextInt(quickComments.size)])
                                delay(1000)
                                findPostButton(commentRoot, platform)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                totalActions["comments"] = totalActions["comments"]!! + 1
                                delay(1500)
                                findBackButton(commentRoot)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                delay(1500)
                            }
                        }
                    }
                }

                // 3. Occasionally follow (every 5th profile)
                if (i % 5 == 0 && i > 0) {
                    val followBtn = findFollowButton(freshRoot, platform)
                    if (followBtn != null) {
                        followBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        totalActions["follows"] = totalActions["follows"]!! + 1
                        delay(DELAY_AFTER_FOLLOW)
                        findBackButton(freshRoot)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(2000)
                    }
                }

                // Scroll
                val scrollRoot = accessibilityService.rootInActiveWindow
                scrollRoot?.let { scrollDown(it) }
                delay(DELAY_BETWEEN_ACTIONS)
            } catch (e: Exception) {
                Log.e(TAG, "Erro engajamento #$i: ${e.message}")
                delay(2000)
            }
        }

        memory.save("engagement_session_active", "false")
        memory.save("engagement_session_likes", totalActions["likes"].toString())
        memory.save("engagement_session_comments", totalActions["comments"].toString())
        memory.save("engagement_session_follows", totalActions["follows"].toString())

        return buildString {
            appendLine("🚀 Sessão de Engajamento Total - $platform")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("  👍 Likes: ${totalActions["likes"]}")
            appendLine("  💬 Comentários: ${totalActions["comments"]}")
            appendLine("  👤 Seguiu: ${totalActions["follows"]}")
            appendLine("  ━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("  📊 Total de ações: ${totalActions.values.sum()}")
        }
    }

    // === ACCESSIBILITY HELPERS ===

    private fun findLikeButton(root: AccessibilityNodeInfo, platform: String): AccessibilityNodeInfo? {
        val likeIds = when (platform) {
            "Instagram" -> listOf("like_button", "row_like_button", "com.instagram:id/row_like_button")
            "Facebook" -> listOf("u_c", "like", "fb_like")
            "TikTok" -> listOf("like_button", "com.zhiliaoapp.musically:id/a_")
            "LinkedIn" -> listOf("social_like", "like", "like-btn")
            else -> listOf("like")
        }
        for (id in likeIds) {
            findNodeById(root, id)?.let { return it }
        }
        // Fallback: find by content description
        return findNodeByDescContains(root, "Like") ?: findNodeByDescContains(root, "Curtir")
    }

    private fun findFollowButton(root: AccessibilityNodeInfo, platform: String): AccessibilityNodeInfo? {
        return findNodeByText(root, "Follow") 
            ?: findNodeByText(root, "Seguir")
            ?: findNodeByText(root, "Connect")
            ?: findNodeByText(root, "Conectar")
            ?: findNodeByDescContains(root, "Follow")
            ?: findNodeByDescContains(root, "Seguir")
    }

    private fun findCommentButton(root: AccessibilityNodeInfo, platform: String): AccessibilityNodeInfo? {
        return findNodeByDescContains(root, "Comment") 
            ?: findNodeByDescContains(root, "Comentar")
            ?: findNodeById(root, "comment_icon")
    }

    private fun findCommentTextField(root: AccessibilityNodeInfo, platform: String): AccessibilityNodeInfo? {
        return findNodeByDescContains(root, "Add a comment")
            ?: findNodeByDescContains(root, "comment")
            ?: findNodeById(root, "comment_text_input")
            ?: findNodeByDescContains(root, "Write a comment")
    }

    private fun findPostButton(root: AccessibilityNodeInfo, platform: String): AccessibilityNodeInfo? {
        return findNodeByText(root, "Post") 
            ?: findNodeByText(root, "Publicar")
            ?: findNodeByText(root, "Send")
            ?: findNodeByText(root, "Enviar")
    }

    private fun findBackButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByDescContains(root, "Back") 
            ?: findNodeByDescContains(root, "Navigate up")
            ?: findNodeByDescContains(root, "Voltar")
    }

    private fun findViewToDoubleTap(root: AccessibilityNodeInfo, platform: String): AccessibilityNodeInfo? {
        // For Instagram/TikTok, the center of the post image can be double-tapped to like
        if (platform == "Instagram" || platform == "TikTok") {
            return findNodeById(root, "feed_photo") 
                ?: findNodeById(root, "photo_view")
                ?: findNodeById(root, "com.zhiliaoapp.musically:id/iv")
        }
        return null
    }

    private fun scrollDown(root: AccessibilityNodeInfo) {
        try {
            val scrollable = findScrollableNode(root)
            scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } catch (_: Exception) {}
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val result = findScrollableNode(node.getChild(i))
            if (result != null) return result
        }
        return null
    }

    private fun findNodeById(root: AccessibilityNodeInfo, idPart: String): AccessibilityNodeInfo? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val viewIdResourceName = node.viewIdResourceName ?: ""
            if (viewIdResourceName.contains(idPart, ignoreCase = true)) {
                nodes.add(node)
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        traverse(root)
        return nodes.firstOrNull()
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val nodeText = node.text?.toString()?.trim() ?: ""
            if (nodeText.equals(text, ignoreCase = true)) {
                nodes.add(node)
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        traverse(root)
        return nodes.firstOrNull()
    }

    private fun findNodeByDescContains(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
            if (contentDesc.contains(desc, ignoreCase = true)) {
                nodes.add(node)
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        traverse(root)
        return nodes.firstOrNull()
    }

    private fun setTextField(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_SET_TEXT, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun detectPlatform(command: String): String {
        return when {
            command.contains("instagram") || command.contains("ig") || command.contains("insta") -> "Instagram"
            command.contains("facebook") || command.contains("fb") -> "Facebook"
            command.contains("tiktok") || command.contains("tt") -> "TikTok"
            command.contains("linkedin") || command.contains("in") -> "LinkedIn"
            else -> "Instagram"
        }
    }

    private fun extractNumber(command: String): Int? {
        val numPattern = Regex("\\d+")
        return numPattern.find(command)?.value?.toIntOrNull()
    }

    private fun extractHashtag(command: String): String? {
        val hashPattern = Regex("#[\\w]+")
        return hashPattern.find(command)?.value
    }
}
