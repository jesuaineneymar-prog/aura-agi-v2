package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

class AuraLinkedInModule(
    private val accessibilityService: AccessibilityService,
    private val memory: AuraMemory,
    private val voiceService: AuraVoiceService
) {

    private val packageName = "com.linkedin.android"
    private val extractedProfiles = mutableListOf<LinkedInProfile>()

    data class LinkedInProfile(
        val name: String,
        val headline: String,
        val company: String,
        val position: String,
        val location: String,
        val connections: String,
        val isConnected: Boolean,
        val extractedAt: Long = System.currentTimeMillis()
    )

    suspend fun handleLinkedInCommand(command: String): String {
        return when {
            command.contains("prospectar") || command.contains("encontrar perfis") || command.contains("buscar leads") || command.contains("buscar profissionais") -> {
                val role = extractRole(command) ?: "gerente"
                val industry = extractIndustry(command) ?: "tecnologia"
                val location = extractLocation(command) ?: "Angola"
                prospectProfiles(role, industry, location)
            }
            command.contains("connection request") || command.contains("pedido de conexão") || command.contains("conectar com") -> {
                val name = extractName(command) ?: return "Senhor, diga o nome da pessoa."
                sendConnectionRequest(name)
            }
            command.contains("mensagem linkedin") || command.contains("linkedin message") || command.contains("inmail") -> {
                val text = extractMessageText(command) ?: "Olá!"
                sendLinkedInMessage(text)
            }
            command.contains("curtir post") || command.contains("like linkedin") -> {
                likeCurrentPost()
            }
            command.contains("comentar linkedin") || command.contains("comment linkedin") -> {
                val text = extractCommentText(command) ?: "Excelente insight!"
                commentOnPost(text)
            }
            command.contains("compartilhar") || command.contains("share linkedin") -> {
                shareCurrentPost()
            }
            command.contains("postar artigo") || command.contains("publicar linkedin") || command.contains("criar post linkedin") -> {
                val text = extractPostText(command) ?: ""
                createPost(text)
            }
            command.contains("abrir linkedin") || command.contains("linkedin") -> {
                openLinkedIn()
            }
            command.contains("ir para perfil") || command.contains("visitar perfil") -> {
                val name = extractName(command) ?: return "Senhor, diga o nome."
                navigateToProfile(name)
            }
            command.contains("extrair") || command.contains("salvar perfis") -> {
                saveProfilesToFile()
            }
            command.contains("aceitar conexões") || command.contains("aceitar pedidos") -> {
                acceptConnectionRequests()
            }
            else -> "Senhor, comandos LinkedIn: 'prospectar gerentes de tecnologia em Angola', 'conectar com João Silva', 'mensagem linkedin para Maria ola', 'postar artigo sobre IA', 'aceitar conexões'."
        }
    }

    private suspend fun openLinkedIn(): String {
        val intent = accessibilityService.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            accessibilityService.startActivity(intent)
            delay(4000)
            return "Senhor, LinkedIn aberto."
        }
        return "Senhor, LinkedIn não está instalado."
    }

    private suspend fun prospectProfiles(role: String, industry: String, location: String): String {
        openLinkedIn()
        delay(5000)

        // Pesquisar no LinkedIn
        val root = accessibilityService.rootInActiveWindow
        val searchBars = root?.findAccessibilityNodeInfosByViewId("$packageName:id/search_bar_text")

        if (searchBars != null && searchBars.isNotEmpty()) {
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)

            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "$role $industry $location")
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(1000)
            searchBars[0].performAction(0x10000020) // ACTION_IME_ENTER (deprecated API 33+)
            delay(3000)

            // Clicar na aba "Pessoas"
            val peopleTab = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("Pessoas")
            if (peopleTab != null && peopleTab.isNotEmpty()) {
                peopleTab[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2000)
            }
        }

        extractedProfiles.clear()
        var processed = 0
        val count = 25

        voiceService.speak("Senhor, prospectando $role em $industry em $location no LinkedIn.")

        while (processed < count) {
            val rootNode = accessibilityService.rootInActiveWindow ?: break

            val profileResults = rootNode.findAccessibilityNodeInfosByViewId("$packageName:id/actor_name")

            for (profileNode in profileResults) {
                profileNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(3000)

                val profileRoot = accessibilityService.rootInActiveWindow
                val name = findProfileName(profileRoot)

                if (name != null && !extractedProfiles.any { it.name == name }) {
                    val headline = findHeadline(profileRoot)
                    val company = findCompany(profileRoot)
                    val position = findPosition(profileRoot)
                    val loc = findLocation(profileRoot)
                    val connections = findConnections(profileRoot)
                    val isConnected = isConnected(profileRoot)

                    val profile = LinkedInProfile(
                        name = name,
                        headline = headline,
                        company = company,
                        position = position,
                        location = loc,
                        connections = connections,
                        isConnected = isConnected
                    )

                    extractedProfiles.add(profile)
                    processed++
                    memory.saveFactual("linkedin_lead_${System.currentTimeMillis()}", profile.toString())

                    if (processed % 5 == 0) {
                        voiceService.speak("$processed perfis extraídos...")
                    }
                }

                accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                delay(1500)
            }

            scrollNext()
            delay(2000)
        }

        return "Senhor, prospecção B2B concluída. **$processed perfis** de $role em $industry em $location extraídos."
    }

    private suspend fun sendConnectionRequest(name: String): String {
        navigateToProfile(name)
        delay(3000)

        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o LinkedIn."

        val connectButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_actions_connect_button")
        if (connectButtons.isNotEmpty()) {
            connectButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            // Adicionar nota personalizada
            val addNoteButtons = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("Adicionar nota")
            if (addNoteButtons != null && addNoteButtons.isNotEmpty()) {
                addNoteButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(1000)

                val noteText = generateConnectionNote(name)
                val editTexts = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("$packageName:id/connect_invite_custom_message")
                if (editTexts != null && editTexts.isNotEmpty()) {
                    val args = android.os.Bundle()
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, noteText)
                    editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    delay(500)
                }
            }

            // Enviar pedido
            val sendButtons = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("Enviar")
            if (sendButtons != null && sendButtons.isNotEmpty()) {
                sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return "Senhor, pedido de conexão enviado para $name com nota personalizada."
            }
        }

        return "Senhor, não consegui enviar pedido de conexão para $name."
    }

    private suspend fun sendLinkedInMessage(text: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o LinkedIn."

        val messageButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_actions_message_button")
        if (messageButtons.isNotEmpty()) {
            messageButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2500)

            val root2 = accessibilityService.rootInActiveWindow
            val editTexts = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/message_compose_edit")
            if (editTexts != null && editTexts.isNotEmpty()) {
                val args = android.os.Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(500)

                val sendButtons = root2.findAccessibilityNodeInfosByViewId("$packageName:id/message_send_button")
                if (sendButtons.isNotEmpty()) {
                    sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return "Senhor, mensagem InMail enviada: '$text'"
                }
            }
        }
        return "Senhor, não consegui enviar mensagem."
    }

    private suspend fun navigateToProfile(name: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Erro"

        val searchBars = root.findAccessibilityNodeInfosByViewId("$packageName:id/search_bar_text")
        if (searchBars.isNotEmpty()) {
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)

            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name)
            searchBars[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(1500)
            searchBars[0].performAction(0x10000020) // ACTION_IME_ENTER (deprecated API 33+)
            delay(2000)

            val results = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText(name)
            if (results != null && results.isNotEmpty()) {
                results[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2500)
                return "Senhor, perfil de $name aberto."
            }
        }
        return "Senhor, não encontrei $name no LinkedIn."
    }

    private suspend fun likeCurrentPost(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o LinkedIn."

        val likeButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/social_actions_react_button_like")
        if (likeButtons.isNotEmpty()) {
            likeButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return "Senhor, post curtido no LinkedIn."
        }

        val altButtons = AuraAccessibilityUtils.findByContentDescription(root, "Curtir")
        if (altButtons.isNotEmpty()) {
            altButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return "Senhor, post curtido."
        }

        return "Senhor, botão de curtir não encontrado."
    }

    private suspend fun commentOnPost(text: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o LinkedIn."

        val commentButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/social_actions_comment_button")
        if (commentButtons.isNotEmpty()) {
            commentButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            val root2 = accessibilityService.rootInActiveWindow
            val editTexts = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/comment_text")
            if (editTexts != null && editTexts.isNotEmpty()) {
                val args = android.os.Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(500)

                val sendButtons = root2.findAccessibilityNodeInfosByText("Publicar")
                if (sendButtons.isNotEmpty()) {
                    sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return "Senhor, comentário publicado: '$text'"
                }
            }
        }
        return "Senhor, não consegui comentar."
    }

    private suspend fun shareCurrentPost(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o LinkedIn."

        val shareButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/social_actions_share_button")
        if (shareButtons.isNotEmpty()) {
            shareButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            val repostButtons = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("Repostar com os teus pensamentos")
            if (repostButtons != null && repostButtons.isNotEmpty()) {
                repostButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(2000)

                val publishButtons = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("Publicar")
                if (publishButtons != null && publishButtons.isNotEmpty()) {
                    publishButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return "Senhor, post repartilhado."
                }
            }
        }
        return "Senhor, não consegui partilhar."
    }

    private suspend fun createPost(text: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o LinkedIn."

        val postButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/share_post_button")
        if (postButtons.isNotEmpty()) {
            postButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            val root2 = accessibilityService.rootInActiveWindow
            val editTexts = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/share_post_text")
            if (editTexts != null && editTexts.isNotEmpty()) {
                val args = android.os.Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(500)

                val publishButtons = root2.findAccessibilityNodeInfosByText("Publicar")
                if (publishButtons.isNotEmpty()) {
                    publishButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return "Senhor, artigo publicado no LinkedIn: '$text'"
                }
            }
        }
        return "Senhor, não consegui publicar."
    }

    private suspend fun acceptConnectionRequests(): String {
        openLinkedIn()
        delay(3000)

        // Ir para "A minha rede"
        val networkTab = accessibilityService.rootInActiveWindow?.findAccessibilityNodeInfosByText("A minha rede")
        if (networkTab != null && networkTab.isNotEmpty()) {
            networkTab[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(3000)

            var accepted = 0
            while (true) {
                val root = accessibilityService.rootInActiveWindow ?: break
                val acceptButtons = root.findAccessibilityNodeInfosByText("Aceitar")

                if (acceptButtons.isEmpty()) break

                acceptButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                accepted++
                delay(1500)
            }

            return "Senhor, aceitei $accepted pedidos de conexão."
        }
        return "Senhor, não consegui aceder aos pedidos de conexão."
    }

    private suspend fun scrollNext(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não consigo scrollar."

        val recyclerViews = root.findAccessibilityNodeInfosByViewId("$packageName:id/recycler_view")
        if (recyclerViews.isNotEmpty()) {
            recyclerViews[0].performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            return "Scroll realizado."
        }

        return "Senhor, não encontrei área scrollável."
    }

    private suspend fun saveProfilesToFile(): String {
        if (extractedProfiles.isEmpty()) {
            return "Senhor, não há perfis extraídos para salvar."
        }

        val fileName = "linkedin_leads_${System.currentTimeMillis()}.csv"
        val file = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)

        file.writeText("Nome,Headline,Empresa,Cargo,Localização,Conexões,Conectado,Data\n")
        extractedProfiles.forEach { profile ->
            file.appendText("\"${profile.name}\",\"${profile.headline}\",\"${profile.company}\",\"${profile.position}\",\"${profile.location}\",${profile.connections},${profile.isConnected},${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(profile.extractedAt))}\n")
        }

        return "Senhor, **${extractedProfiles.size} perfis B2B** salvos em Downloads/$fileName (CSV)."
    }

    private fun findProfileName(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_name")
        return nodes.firstOrNull()?.text?.toString()?.trim()
    }

    private fun findHeadline(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_headline")
        return nodes.firstOrNull()?.text?.toString() ?: ""
    }

    private fun findCompany(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_company")
        return nodes.firstOrNull()?.text?.toString() ?: ""
    }

    private fun findPosition(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_position")
        return nodes.firstOrNull()?.text?.toString() ?: ""
    }

    private fun findLocation(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_location")
        return nodes.firstOrNull()?.text?.toString() ?: ""
    }

    private fun findConnections(root: AccessibilityNodeInfo?): String {
        if (root == null) return "N/A"
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/profile_connections")
        return nodes.firstOrNull()?.text?.toString() ?: "N/A"
    }

    private fun isConnected(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText("1.º")
        return nodes.isNotEmpty()
    }

    private fun generateConnectionNote(name: String): String {
        return """Olá $name,

Vi o teu perfil e fiquei impressionado com a tua trajetória em ${extractIndustry("tecnologia")}. 

Trabalho com inteligência artificial e automação de negócios em Angola. Acredito que podemos trocar ideias valiosas sobre como a IA está a transformar o mercado angolano.

Gostaria de conectar contigo.

Cumprimentos,
Cristiano"""
    }

    private fun extractRole(command: String): String? {
        val roles = listOf("gerente", "diretor", "ceo", "fundador", "gestor", "administrador", "coordenador", "especialista", "consultor", "engenheiro", "analista", "vendedor", "marketing")
        for (role in roles) {
            if (command.contains(role, ignoreCase = true)) return role
        }
        return null
    }

    private fun extractIndustry(command: String): String? {
        val industries = listOf("tecnologia", "finanças", "saúde", "educação", "construção", "imobiliário", "moda", "alimentação", "turismo", "energia", "telecomunicações", "agricultura")
        for (ind in industries) {
            if (command.contains(ind, ignoreCase = true)) return ind
        }
        return null
    }

    private fun extractLocation(command: String): String? {
        val locations = listOf("angola", "luanda", "benguela", "lobito", "huambo", "lubango", "malanje", "namibe", "cabinda", "africa", "portugal", "brasil")
        for (loc in locations) {
            if (command.contains(loc, ignoreCase = true)) return loc.capitalize()
        }
        return null
    }

    private fun extractName(command: String): String? {
        val regex = Regex("(?:com|para|como|a|o)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)")
        val match = regex.find(command)
        return match?.groupValues?.get(1)
    }

    private fun extractMessageText(command: String): String? {
        val patterns = listOf("mensagem linkedin ", "linkedin message ", "inmail ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                val after = command.substring(idx + p.length).trim()
                return after.replace(Regex("^\\w+\\s*"), "").trim()
            }
        }
        return null
    }

    private fun extractCommentText(command: String): String? {
        val patterns = listOf("comentar linkedin ", "comment linkedin ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim()
        }
        return null
    }

    private fun extractPostText(command: String): String? {
        val patterns = listOf("postar artigo ", "publicar linkedin ", "criar post linkedin ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim()
        }
        return null
    }
}
