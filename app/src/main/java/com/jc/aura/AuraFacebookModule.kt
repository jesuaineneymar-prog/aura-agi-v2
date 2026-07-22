package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

class AuraFacebookModule(
    private val accessibilityService: AccessibilityService,
    private val memory: AuraMemory,
    private val voiceService: AuraVoiceService
) {

    private val packageName = "com.facebook.katana"
    private val messengerPackage = "com.facebook.orca"
    private val extractedPages = mutableListOf<FacebookPage>()

    data class FacebookPage(
        val name: String,
        val category: String,
        val followers: String,
        val likes: String,
        val website: String,
        val phone: String,
        val extractedAt: Long = System.currentTimeMillis()
    )

    suspend fun handleFacebookCommand(command: String): String {
        return when {
            command.contains("prospectar páginas") || command.contains("encontrar páginas") || command.contains("buscar negócios") -> {
                val niche = extractNiche(command) ?: "restaurantes"
                val location = extractLocation(command) ?: "Luanda"
                prospectPages(niche, location)
            }
            command.contains("curtir") || command.contains("like") -> {
                likeCurrentPost()
            }
            command.contains("comentar") || command.contains("comenta") -> {
                val text = extractCommentText(command) ?: "🔥"
                commentOnPost(text)
            }
            command.contains("compartilhar") || command.contains("share") -> {
                shareCurrentPost()
            }
            command.contains("mensagem messenger") || command.contains("facebook message") || command.contains("fb message") -> {
                val text = extractMessageText(command) ?: "Olá!"
                sendMessengerMessage(text)
            }
            command.contains("responder comentários") || command.contains("reply comments") -> {
                replyToComments()
            }
            command.contains("postar") || command.contains("publicar") || command.contains("criar post") -> {
                val text = extractPostText(command) ?: ""
                createPost(text)
            }
            command.contains("abrir facebook") || command.contains("facebook") -> {
                openFacebook()
            }
            command.contains("abrir messenger") || command.contains("messenger") -> {
                openMessenger()
            }
            command.contains("ir para página") || command.contains("visitar página") -> {
                val pageName = extractPageName(command) ?: return "Senhor, diga o nome da página."
                navigateToPage(pageName)
            }
            command.contains("extrair") || command.contains("salvar páginas") -> {
                savePagesToFile()
            }
            else -> "Senhor, comandos Facebook: 'prospectar páginas de restaurantes em Luanda', 'curtir post', 'comentar que show', 'mensagem messenger para João ola', 'postar Bom dia pessoal', 'ir para página J&C Trading'."
        }
    }

    private suspend fun openFacebook(): String {
        val intent = accessibilityService.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            accessibilityService.startActivity(intent)
            delay(4000)
            return "Senhor, Facebook aberto."
        }
        return "Senhor, Facebook não está instalado."
    }

    private suspend fun openMessenger(): String {
        val intent = accessibilityService.packageManager.getLaunchIntentForPackage(messengerPackage)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            accessibilityService.startActivity(intent)
            delay(4000)
            return "Senhor, Messenger aberto."
        }
        return "Senhor, Messenger não está instalado."
    }

    private suspend fun prospectPages(niche: String, location: String): String {
        openFacebook()
        delay(5000)

        // Pesquisar no Facebook
        val root = accessibilityService.rootInActiveWindow
        val searchBars = root?.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")

        if (searchBars != null && searchBars.isNotEmpty()) {
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)

            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "$niche em $location")
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(1000)
            searchBars[0].performAction(0x10000020) // ACTION_IME_ENTER (deprecated API 33+)
            delay(3000)

            // Clicar na aba "Páginas"
            val pagesTab = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("Páginas")
            if (pagesTab != null && pagesTab.isNotEmpty()) {
                pagesTab[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2000)
            }
        }

        extractedPages.clear()
        var processed = 0
        val count = 20

        voiceService.speak("Senhor, prospectando páginas de $niche em $location no Facebook.")

        while (processed < count) {
            val rootNode = accessibilityService.rootInActiveWindow ?: break

            val pageResults = rootNode.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")

            for (pageNode in pageResults) {
                pageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(3000)

                val pageRoot = accessibilityService.rootInActiveWindow
                val name = findPageName(pageRoot)

                if (name != null && !extractedPages.any { it.name == name }) {
                    val category = findPageCategory(pageRoot)
                    val followers = findPageFollowers(pageRoot)
                    val likes = findPageLikes(pageRoot)
                    val website = findPageWebsite(pageRoot)
                    val phone = findPagePhone(pageRoot)

                    val page = FacebookPage(
                        name = name,
                        category = category,
                        followers = followers,
                        likes = likes,
                        website = website,
                        phone = phone
                    )

                    extractedPages.add(page)
                    processed++
                    memory.saveFactual("fb_page_${System.currentTimeMillis()}", page.toString())

                    if (processed % 5 == 0) {
                        voiceService.speak("$processed páginas extraídas...")
                    }
                }

                accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                delay(1500)
            }

            scrollNext()
            delay(2000)
        }

        return "Senhor, prospecção concluída. **$processed páginas** de $niche em $location extraídas."
    }

    private suspend fun navigateToPage(pageName: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Erro"

        val searchBars = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        if (searchBars.isNotEmpty()) {
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)

            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pageName)
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(1500)
            searchBars[0].performAction(0x10000020) // ACTION_IME_ENTER (deprecated API 33+)
            delay(2000)

            val results = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText(pageName)
            if (results != null && results.isNotEmpty()) {
                results[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2500)
                return "Senhor, página '$pageName' aberta."
            }
        }
        return "Senhor, não encontrei a página '$pageName'."
    }

    private fun findPageName(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        return nodes.firstOrNull()?.text?.toString()?.trim()
    }

    private fun findPageCategory(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        return nodes.firstOrNull()?.text?.toString() ?: ""
    }

    private fun findPageFollowers(root: AccessibilityNodeInfo?): String {
        if (root == null) return "N/A"
        val nodes = root.findAccessibilityNodeInfosByText("seguidores")
        for (node in nodes) {
            val parent = node.parent
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i)
                    if (sibling != node && sibling.text != null) {
                        return sibling.text.toString()
                    }
                }
            }
        }
        return "N/A"
    }

    private fun findPageLikes(root: AccessibilityNodeInfo?): String {
        if (root == null) return "N/A"
        val nodes = root.findAccessibilityNodeInfosByText("curtidas")
        for (node in nodes) {
            val parent = node.parent
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i)
                    if (sibling != node && sibling.text != null) {
                        return sibling.text.toString()
                    }
                }
            }
        }
        return "N/A"
    }

    private fun findPageWebsite(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        return nodes.firstOrNull()?.text?.toString() ?: ""
    }

    private fun findPagePhone(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        return nodes.firstOrNull()?.text?.toString() ?: ""
    }

    private suspend fun likeCurrentPost(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Facebook."

        val likeButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        if (likeButtons.isNotEmpty()) {
            likeButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return "Senhor, post curtido no Facebook."
        }

        val altButtons = AuraAccessibilityUtils.findByContentDescription(root, "Curtir")
        if (altButtons.isNotEmpty()) {
            altButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return "Senhor, post curtido."
        }

        return "Senhor, botão de curtir não encontrado."
    }

    private suspend fun commentOnPost(text: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Facebook."

        val commentButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        if (commentButtons.isNotEmpty()) {
            commentButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            val root2 = accessibilityService.rootInActiveWindow
            val editTexts = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
            if (editTexts != null && editTexts.isNotEmpty()) {
                val args = android.os.Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(500)

                val sendButtons = root2.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
                if (sendButtons.isNotEmpty()) {
                    sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return "Senhor, comentário enviado: '$text'"
                }
            }
        }
        return "Senhor, não consegui comentar."
    }

    private suspend fun shareCurrentPost(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Facebook."

        val shareButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        if (shareButtons.isNotEmpty()) {
            shareButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            // Clicar em "Partilhar agora"
            val nowButtons = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("Partilhar agora")
            if (nowButtons != null && nowButtons.isNotEmpty()) {
                nowButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return "Senhor, post partilhado."
            }
        }
        return "Senhor, não consegui partilhar."
    }

    private suspend fun sendMessengerMessage(text: String): String {
        openMessenger()
        delay(3000)

        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Messenger."

        // Procurar contacto ou conversa existente
        val searchBars = root.findAccessibilityNodeInfosByViewId("$messengerPackage:id/(name removed)")
        if (searchBars != null && searchBars.isNotEmpty()) {
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)

            // O contacto já deve estar na conversa se abrimos pelo Facebook
        }

        val editTexts = root.findAccessibilityNodeInfosByViewId("$messengerPackage:id/(name removed)")
        if (editTexts != null && editTexts.isNotEmpty()) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(500)

            val sendButtons = root.findAccessibilityNodeInfosByViewId("$messengerPackage:id/(name removed)")
            if (sendButtons != null && sendButtons.isNotEmpty()) {
                sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return "Senhor, mensagem enviada no Messenger: '$text'"
            }
        }
        return "Senhor, não consegui enviar mensagem no Messenger."
    }

    private suspend fun replyToComments(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Facebook."

        val commentButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        if (commentButtons.isNotEmpty()) {
            commentButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            val root2 = accessibilityService.rootInActiveWindow
            val comments = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")

            var replied = 0
            comments?.forEach { comment ->
                val commentText = comment.text?.toString() ?: return@forEach
                val reply = generateReply(commentText)

                val replyButtons = comment.parent?.findAccessibilityNodeInfosByText("Responder")
                if (replyButtons != null && replyButtons.isNotEmpty()) {
                    replyButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(1000)

                    val root3 = accessibilityService.rootInActiveWindow
                    val editTexts = root3?.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
                    if (editTexts != null && editTexts.isNotEmpty()) {
                        val args = android.os.Bundle()
                        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
                        editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        delay(500)

                        val sendButtons = root3.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
                        if (sendButtons.isNotEmpty()) {
                            sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            replied++
                            delay(1500)
                        }
                    }
                }
            }

            return "Senhor, respondi a $replied comentários no Facebook."
        }
        return "Senhor, não encontrei comentários."
    }

    private suspend fun createPost(text: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o Facebook."

        // Clicar em "O que estás a pensar?"
        val postButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        if (postButtons.isNotEmpty()) {
            postButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            val root2 = accessibilityService.rootInActiveWindow
            val editTexts = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
            if (editTexts != null && editTexts.isNotEmpty()) {
                val args = android.os.Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(500)

                val publishButtons = root2.findAccessibilityNodeInfosByText("Publicar")
                if (publishButtons.isNotEmpty()) {
                    publishButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return "Senhor, post publicado: '$text'"
                }
            }
        }
        return "Senhor, não consegui publicar."
    }

    private suspend fun scrollNext(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não consigo scrollar."

        val scrollable = root.findAccessibilityNodeInfosByViewId("$packageName:id/(name removed)")
        if (scrollable.isNotEmpty()) {
            scrollable[0].performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            return "Scroll realizado."
        }

        return "Senhor, não encontrei área scrollável."
    }

    private suspend fun savePagesToFile(): String {
        if (extractedPages.isEmpty()) {
            return "Senhor, não há páginas extraídas para salvar."
        }

        val fileName = "facebook_leads_${System.currentTimeMillis()}.csv"
        val file = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)

        file.writeText("Nome,Categoria,Seguidores,Curtidas,Website,Telefone,Data\n")
        extractedPages.forEach { page ->
            file.appendText("\"${page.name}\",\"${page.category}\",${page.followers},${page.likes},\"${page.website}\",\"${page.phone}\",${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(page.extractedAt))}\n")
        }

        return "Senhor, **${extractedPages.size} páginas** salvas em Downloads/$fileName (CSV)."
    }

    private fun generateReply(commentText: String): String {
        return when {
            commentText.contains("obrigado") || commentText.contains("thanks") -> "Por nada! 😊"
            commentText.contains("preço") || commentText.contains("quanto") -> "Envia-nos uma mensagem privada para te passarmos os detalhes! 📩"
            commentText.contains("onde") || commentText.contains("local") -> "Estamos em Luanda! Manda mensagem para o endereço exato 📍"
            commentText.contains("horário") || commentText.contains("hora") -> "Abertos de segunda a sábado! Manda mensagem para horários específicos ⏰"
            commentText.contains("lindo") || commentText.contains("bonito") || commentText.contains("amei") -> "Obrigado! ❤️ Ficamos felizes que gostou!"
            else -> "Obrigado pelo teu comentário! 😊"
        }
    }

    private fun extractNiche(command: String): String? {
        val niches = listOf("restaurantes", "hotéis", "clínicas", "lojas", "imobiliárias", "escolas", "ginásios", "salões", "bares", "cafés")
        for (niche in niches) {
            if (command.contains(niche, ignoreCase = true)) return niche
        }
        return null
    }

    private fun extractLocation(command: String): String? {
        val locations = listOf("luanda", "benguela", "lobito", "huambo", "lubango", "malanje", "namibe", "cabinda")
        for (loc in locations) {
            if (command.contains(loc, ignoreCase = true)) return loc.capitalize()
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

    private fun extractMessageText(command: String): String? {
        val patterns = listOf("mensagem messenger ", "facebook message ", "fb message ", "messenger ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                val after = command.substring(idx + p.length).trim()
                return after.replace(Regex("^\\w+\\s*"), "").trim()
            }
        }
        return null
    }

    private fun extractPostText(command: String): String? {
        val patterns = listOf("postar ", "publicar ", "criar post ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim()
        }
        return null
    }

    private fun extractPageName(command: String): String? {
        val patterns = listOf("ir para página ", "visitar página ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim()
        }
        return null
    }
}
