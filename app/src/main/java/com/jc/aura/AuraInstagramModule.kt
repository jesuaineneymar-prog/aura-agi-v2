package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

class AuraInstagramModule(
    private val accessibilityService: AccessibilityService,
    private val memory: AuraMemory,
    private val voiceService: AuraVoiceService
) {

    private val packageName = "com.instagram.android"
    private val extractedProfiles = mutableListOf<InstagramProfile>()

    data class InstagramProfile(
        val username: String,
        val bio: String,
        val followers: String,
        val following: String,
        val posts: String,
        val isVerified: Boolean,
        val isBusiness: Boolean,
        val extractedAt: Long = System.currentTimeMillis()
    )

    suspend fun handleInstagramCommand(command: String): String {
        return when {
            command.contains("prospectar") || command.contains("encontrar perfis") || command.contains("buscar leads") -> {
                val count = extractNumber(command) ?: 30
                val niche = extractNiche(command) ?: "geral"
                prospectProfiles(count, niche)
            }
            command.contains("curtir") || command.contains("like") -> {
                likeCurrentPost()
            }
            command.contains("comentar") || command.contains("comenta") -> {
                val text = extractCommentText(command) ?: "🔥"
                commentOnPost(text)
            }
            command.contains("seguir") || command.contains("follow") -> {
                followCurrentProfile()
            }
            command.contains("dm") || command.contains("mensagem direta") || command.contains("direct") -> {
                val text = extractDMText(command) ?: "Olá!"
                sendDM(text)
            }
            command.contains("responder comentários") || command.contains("reply comments") -> {
                replyToComments()
            }
            command.contains("postar story") || command.contains("story") || command.contains("stories") -> {
                postStory()
            }
            command.contains("scroll") || command.contains("próximo") || command.contains("avançar") -> {
                scrollNext()
            }
            command.contains("extrair") || command.contains("salvar perfis") -> {
                saveProfilesToFile()
            }
            command.contains("abrir instagram") || command.contains("instagram") -> {
                openInstagram()
            }
            command.contains("ir para perfil") || command.contains("visitar perfil") -> {
                val username = extractUsername(command) ?: return "Senhor, diga o @username."
                navigateToProfile(username)
            }
            else -> "Senhor, comandos Instagram: 'prospectar 50 perfis de fitness', 'curtir post', 'comentar que show', 'seguir perfil', 'dm para @joao ola', 'responder comentários', 'postar story', 'ir para perfil @joao'."
        }
    }

    private suspend fun openInstagram(): String {
        val intent = accessibilityService.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            accessibilityService.startActivity(intent)
            delay(4000)
            return "Senhor, Instagram aberto. Pronto para ação."
        }
        return "Senhor, Instagram não está instalado."
    }

    private suspend fun prospectProfiles(count: Int, niche: String): String {
        openInstagram()
        delay(5000)

        // Primeiro, procurar hashtag do nicho
        val searchHashtag = niche.replace(" ", "")
        navigateToHashtag(searchHashtag)
        delay(3000)

        extractedProfiles.clear()
        var processed = 0
        var attempts = 0
        val maxAttempts = count * 4

        voiceService.speak("Senhor, iniciando prospecção de $count perfis de $niche no Instagram. Isso pode levar alguns minutos.")

        while (processed < count && attempts < maxAttempts) {
            attempts++

            val rootNode = accessibilityService.rootInActiveWindow ?: continue

            // Clicar no primeiro post da grid
            val posts = rootNode.findAccessibilityNodeInfosByViewId("$packageName:id/media_set_row_content_identifier")
            if (posts.isNotEmpty()) {
                posts[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2500)

                // Extrair dados do perfil do post
                val profileRoot = accessibilityService.rootInActiveWindow
                val username = findUsernameOnScreen(profileRoot)

                if (username != null && !extractedProfiles.any { it.username == username }) {
                    // Clicar no username para ir ao perfil
                    clickText(username)
                    delay(2500)

                    val profileRoot2 = accessibilityService.rootInActiveWindow
                    val bio = findBioOnScreen(profileRoot2)
                    val followers = findFollowersOnScreen(profileRoot2)
                    val following = findFollowingOnScreen(profileRoot2)
                    val postsCount = findPostsCountOnScreen(profileRoot2)
                    val isVerified = isVerifiedOnScreen(profileRoot2)
                    val isBusiness = isBusinessOnScreen(profileRoot2)

                    val profile = InstagramProfile(
                        username = username,
                        bio = bio,
                        followers = followers,
                        following = following,
                        posts = postsCount,
                        isVerified = isVerified,
                        isBusiness = isBusiness
                    )

                    extractedProfiles.add(profile)
                    processed++
                    memory.saveFactual("ig_lead_${System.currentTimeMillis()}", profile.toString())

                    if (processed % 5 == 0) {
                        voiceService.speak("$processed perfis extraídos...")
                    }

                    // Voltar para o feed de hashtag
                    accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(1000)
                    accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(1000)
                }
            }

            // Scroll para próximo post
            scrollNext()
            delay(2000)
        }

        return "Senhor, prospecção concluída. **$processed perfis** de $niche extraídos e salvos."
    }

    private suspend fun navigateToHashtag(hashtag: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Erro"

        // Clicar na barra de pesquisa
        val searchBars = root.findAccessibilityNodeInfosByViewId("$packageName:id/action_bar_search_edit_text")
        if (searchBars.isNotEmpty()) {
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)

            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "#$hashtag")
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(1000)

            // Pressionar enter
            searchBars[0].performAction(0x10000020) // ACTION_IME_ENTER (deprecated API 33+)
            delay(2000)

            // Clicar na aba "Tags"
            val tagsTab = root.findAccessibilityNodeInfosByText("Tags")
            if (tagsTab.isNotEmpty()) {
                tagsTab[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(1500)
            }

            // Clicar na primeira hashtag
            val firstHashtag = root.findAccessibilityNodeInfosByViewId("$packageName:id/row_search_keyword_title")
            if (firstHashtag.isNotEmpty()) {
                firstHashtag[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2000)
            }
        }
        return "Navegado para #$hashtag"
    }

    private suspend fun navigateToProfile(username: String): String {
        val cleanUsername = username.replace("@", "")
        val root = accessibilityService.rootInActiveWindow ?: return "Erro"

        val searchBars = root.findAccessibilityNodeInfosByViewId("$packageName:id/action_bar_search_edit_text")
        if (searchBars.isNotEmpty()) {
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)

            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cleanUsername)
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(1500)

            searchBars[0].performAction(0x10000020) // ACTION_IME_ENTER (deprecated API 33+)
            delay(2000)

            // Clicar no primeiro resultado
            val results = root.findAccessibilityNodeInfosByViewId("$packageName:id/row_search_user_username")
            if (results.isNotEmpty()) {
                results[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2500)
                return "Senhor, perfil @$cleanUsername aberto."
            }
        }
        return "Senhor, não encontrei @$cleanUsername."
    }

    private fun findUsernameOnScreen(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/row_feed_photo_profile_name")
        if (nodes.isNotEmpty()) {
            return nodes[0].text?.toString()?.replace("@", "")?.trim()
        }
        val altNodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/action_bar_title")
        if (altNodes.isNotEmpty()) {
            return altNodes[0].text?.toString()?.trim()
        }
        return null
    }

    private fun findBioOnScreen(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_header_bio_text")
        return nodes.firstOrNull()?.text?.toString() ?: ""
    }

    private fun findFollowersOnScreen(root: AccessibilityNodeInfo?): String {
        if (root == null) return "N/A"
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/row_profile_header_textview_followers_count")
        return nodes.firstOrNull()?.text?.toString() ?: "N/A"
    }

    private fun findFollowingOnScreen(root: AccessibilityNodeInfo?): String {
        if (root == null) return "N/A"
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/row_profile_header_textview_following_count")
        return nodes.firstOrNull()?.text?.toString() ?: "N/A"
    }

    private fun findPostsCountOnScreen(root: AccessibilityNodeInfo?): String {
        if (root == null) return "N/A"
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/row_profile_header_textview_media_count")
        return nodes.firstOrNull()?.text?.toString() ?: "N/A"
    }

    private fun isVerifiedOnScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_header_verified_badge")
        return nodes.isNotEmpty()
    }

    private fun isBusinessOnScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_header_business_category")
        return nodes.isNotEmpty()
    }

    private suspend fun likeCurrentPost(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Instagram."

        val likeButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/row_feed_button_like")
        if (likeButtons.isNotEmpty()) {
            likeButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return "Senhor, post curtido."
        }

        val heartNodes = AuraAccessibilityUtils.findByContentDescription(root, "Curtir")
        if (heartNodes.isNotEmpty()) {
            heartNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return "Senhor, post curtido."
        }

        return "Senhor, botão de curtir não encontrado."
    }

    private suspend fun commentOnPost(text: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Instagram."

        val commentButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/row_feed_button_comment")
        if (commentButtons.isNotEmpty()) {
            commentButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            val root2 = accessibilityService.rootInActiveWindow
            val editTexts = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/layout_comment_thread_edittext")
            if (editTexts != null && editTexts.isNotEmpty()) {
                val args = android.os.Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(500)

                val sendButtons = root2.findAccessibilityNodeInfosByViewId("$packageName:id/layout_comment_thread_post_button")
                if (sendButtons.isNotEmpty()) {
                    sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return "Senhor, comentário enviado: '$text'"
                }
            }
        }
        return "Senhor, não consegui comentar."
    }

    private suspend fun followCurrentProfile(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Instagram."

        val followButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_header_follow_button")
        if (followButtons.isNotEmpty()) {
            val btn = followButtons[0]
            val btnText = btn.text?.toString() ?: ""
            if (btnText.contains("Seguir") || btnText.contains("Follow")) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return "Senhor, perfil seguido."
            } else {
                return "Senhor, já segues este perfil."
            }
        }
        return "Senhor, botão de seguir não encontrado."
    }

    private suspend fun sendDM(text: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Instagram."

        // Clicar no botão de mensagem no perfil
        val messageButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_header_message_button")
        if (messageButtons.isNotEmpty()) {
            messageButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2500)

            val root2 = accessibilityService.rootInActiveWindow
            val editTexts = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/direct_text_input")
            if (editTexts != null && editTexts.isNotEmpty()) {
                val args = android.os.Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(500)

                val sendButtons = root2.findAccessibilityNodeInfosByViewId("$packageName:id/direct_send_button")
                if (sendButtons.isNotEmpty()) {
                    sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return "Senhor, DM enviada: '$text'"
                }
            }
        }
        return "Senhor, não consegui enviar DM."
    }

    private suspend fun replyToComments(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Instagram."

        // Abrir comentários do post atual
        val commentButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/row_feed_button_comment")
        if (commentButtons.isNotEmpty()) {
            commentButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            val root2 = accessibilityService.rootInActiveWindow
            val comments = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/row_comment_textview_comment")

            var replied = 0
            comments?.forEach { comment ->
                val commentText = comment.text?.toString() ?: return@forEach

                // Gerar resposta automática via IA
                val reply = generateReply(commentText)

                // Clicar no botão de responder deste comentário
                val replyButtons = comment.parent?.findAccessibilityNodeInfosByViewId("$packageName:id/row_comment_button_reply")
                if (replyButtons != null && replyButtons.isNotEmpty()) {
                    replyButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(1000)

                    val root3 = accessibilityService.rootInActiveWindow
                    val editTexts = root3?.findAccessibilityNodeInfosByViewId("$packageName:id/layout_comment_thread_edittext")
                    if (editTexts != null && editTexts.isNotEmpty()) {
                        val args = android.os.Bundle()
                        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
                        editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        delay(500)

                        val sendButtons = root3.findAccessibilityNodeInfosByViewId("$packageName:id/layout_comment_thread_post_button")
                        if (sendButtons.isNotEmpty()) {
                            sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            replied++
                            delay(1500)
                        }
                    }
                }
            }

            return "Senhor, respondi a $replied comentários automaticamente."
        }
        return "Senhor, não encontrei comentários para responder."
    }

    private fun generateReply(commentText: String): String {
        return when {
            commentText.contains("lindo") || commentText.contains("bonito") || commentText.contains("amei") -> "Obrigado! ❤️ Fico feliz que gostou!"
            commentText.contains("preço") || commentText.contains("quanto") || commentText.contains("valor") -> "Olá! Manda DM que te passo todos os detalhes! 📩"
            commentText.contains("onde") || commentText.contains("local") || commentText.contains("endereço") -> "Estamos em Luanda! Manda DM para mais info 📍"
            commentText.contains("horário") || commentText.contains("hora") || commentText.contains("aberto") -> "Funcionamos de segunda a sábado! DM para horários específicos ⏰"
            commentText.contains("obrigado") || commentText.contains("thanks") -> "Por nada! Sempre às ordens 😊"
            else -> "Obrigado pelo comentário! ❤️"
        }
    }

    private suspend fun postStory(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Instagram."

        // Clicar no botão de story (canto superior esquerdo)
        val storyButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/avatar_container")
        if (storyButtons.isNotEmpty()) {
            storyButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(3000)

            // Clicar em "Câmara" ou "Galeria"
            val cameraButtons = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("Câmara")
            if (cameraButtons != null && cameraButtons.isNotEmpty()) {
                cameraButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2000)

                // Tirar foto (simular clique no botão de captura)
                val captureButtons = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("$packageName:id/camera_shutter_button")
                if (captureButtons != null && captureButtons.isNotEmpty()) {
                    captureButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(2000)

                    // Clicar em "A tua história"
                    val storyShareButtons = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("A tua história")
                    if (storyShareButtons != null && storyShareButtons.isNotEmpty()) {
                        storyShareButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return "Senhor, story publicada!"
                    }
                }
            }
        }
        return "Senhor, não consegui publicar story. A interface pode ter mudado."
    }

    private suspend fun scrollNext(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não consigo scrollar."

        val recyclerViews = root.findAccessibilityNodeInfosByViewId("$packageName:id/recycler_view")
        if (recyclerViews.isNotEmpty()) {
            recyclerViews[0].performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            return "Próximo post."
        }

        return "Senhor, não encontrei área scrollável."
    }

    private suspend fun saveProfilesToFile(): String {
        if (extractedProfiles.isEmpty()) {
            return "Senhor, não há perfis extraídos para salvar."
        }

        val fileName = "instagram_leads_${System.currentTimeMillis()}.csv"
        val file = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)

        file.writeText("Username,Bio,Seguidores,Seguindo,Posts,Verificado,Business,Data\n")
        extractedProfiles.forEach { profile ->
            file.appendText("${profile.username},\"${profile.bio}\",${profile.followers},${profile.following},${profile.posts},${profile.isVerified},${profile.isBusiness},${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(profile.extractedAt))}\n")
        }

        return "Senhor, **${extractedProfiles.size} perfis** salvos em Downloads/$fileName (formato CSV)."
    }

    private fun clickText(text: String): Boolean {
        val root = accessibilityService.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNotEmpty()) {
            nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return false
    }

    private fun extractNumber(command: String): Int? {
        val regex = Regex("\\d+")
        val match = regex.find(command)
        return match?.value?.toIntOrNull()
    }

    private fun extractNiche(command: String): String? {
        val niches = listOf("fitness", "moda", "beleza", "comida", "viagem", "tecnologia", "negócios", "música", "arte", "esporte", "saúde", "educação")
        for (niche in niches) {
            if (command.contains(niche, ignoreCase = true)) return niche
        }
        return null
    }

    private fun extractCommentText(command: String): String? {
        val patterns = listOf("comentar ", "comenta ", "diz ", "escreve ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim()
        }
        return null
    }

    private fun extractDMText(command: String): String? {
        val patterns = listOf("dm ", "mensagem direta ", "direct ", "mensagem para ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                val after = command.substring(idx + p.length).trim()
                // Remover username se presente
                return after.replace(Regex("^@?\\w+\\s*"), "").trim()
            }
        }
        return null
    }

    private fun extractUsername(command: String): String? {
        val regex = Regex("@?(\\w+)")
        val matches = regex.findAll(command)
        return matches.lastOrNull()?.value?.replace("@", "")
    }
}
