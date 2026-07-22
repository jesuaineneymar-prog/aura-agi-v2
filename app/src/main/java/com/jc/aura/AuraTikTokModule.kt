package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

class AuraTikTokModule(
    private val accessibilityService: AccessibilityService,
    private val memory: AuraMemory,
    private val voiceService: AuraVoiceService
) {

    private val packageName = "com.zhiliaoapp.musically"
    private val extractedProfiles = mutableListOf<String>()

    suspend fun handleTikTokCommand(command: String): String {
        return when {
            command.contains("prospectar") || command.contains("encontrar perfis") || command.contains("buscar leads") -> {
                val count = extractNumber(command) ?: 20
                prospectProfiles(count)
            }
            command.contains("curtir") || command.contains("like") -> {
                likeCurrentVideo()
            }
            command.contains("comentar") || command.contains("comenta") -> {
                val text = extractCommentText(command) ?: "🔥"
                commentOnVideo(text)
            }
            command.contains("seguir") || command.contains("follow") -> {
                followCurrentProfile()
            }
            command.contains("scroll") || command.contains("próximo") || command.contains("avançar") -> {
                scrollNext()
            }
            command.contains("extrair") || command.contains("salvar perfis") -> {
                saveProfilesToFile()
            }
            command.contains("abrir tiktok") || command.contains("tiktok") -> {
                openTikTok()
            }
            else -> "Senhor, comandos TikTok disponíveis: 'prospectar 50 perfis', 'curtir vídeo', 'comentar que show', 'seguir perfil', 'próximo vídeo', 'salvar perfis em arquivo'."
        }
    }

    private suspend fun openTikTok(): String {
        val intent = accessibilityService.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            accessibilityService.startActivity(intent)
            delay(3000)
            return "Senhor, TikTok aberto. Pronto para ação."
        }
        return "Senhor, TikTok não está instalado."
    }

    private suspend fun prospectProfiles(count: Int): String {
        openTikTok()
        delay(4000)

        extractedProfiles.clear()
        var processed = 0
        var attempts = 0
        val maxAttempts = count * 3

        voiceService.speak("Senhor, iniciando prospecção de $count perfis no TikTok. Isso pode levar alguns minutos.")

        while (processed < count && attempts < maxAttempts) {
            attempts++

            val rootNode = accessibilityService.rootInActiveWindow ?: continue
            val username = findUsernameOnScreen(rootNode)
            val followers = findFollowersOnScreen(rootNode)
            val bio = findBioOnScreen(rootNode)

            if (username != null && !extractedProfiles.contains(username)) {
                val profileData = "@$username | Seguidores: $followers | Bio: $bio"
                extractedProfiles.add(profileData)
                processed++
                memory.saveFactual("tiktok_lead_${System.currentTimeMillis()}", profileData)

                if (processed % 5 == 0) {
                    voiceService.speak("$processed perfis extraídos...")
                }
            }

            scrollNext()
            delay(2000)
        }

        return "Senhor, prospecção concluída. **$processed perfis** extraídos e salvos na memória."
    }

    private fun findUsernameOnScreen(root: AccessibilityNodeInfo): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/title")
        if (nodes.isNotEmpty()) {
            return nodes[0].text?.toString()?.replace("@", "")?.trim()
        }
        val textNodes = root.findAccessibilityNodeInfosByText("@")
        for (node in textNodes) {
            val text = node.text?.toString() ?: continue
            if (text.startsWith("@") && text.length > 2) {
                return text.replace("@", "").trim()
            }
        }
        return null
    }

    private fun findFollowersOnScreen(root: AccessibilityNodeInfo): String {
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/count_layout")
        if (nodes.isNotEmpty()) {
            return nodes[0].text?.toString() ?: "N/A"
        }
        val textNodes = root.findAccessibilityNodeInfosByText("seguidores")
        for (node in textNodes) {
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

    private fun findBioOnScreen(root: AccessibilityNodeInfo): String {
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/desc")
        if (nodes.isNotEmpty()) {
            return nodes[0].text?.toString() ?: ""
        }
        return ""
    }

    private suspend fun likeCurrentVideo(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não consigo ver a tela do TikTok."
        val likeButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/like_icon")

        if (likeButtons.isNotEmpty()) {
            likeButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return "Senhor, vídeo curtido com sucesso."
        }

        val heartNodes = AuraAccessibilityUtils.findByContentDescription(root, "Curtir")
        if (heartNodes.isNotEmpty()) {
            heartNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return "Senhor, vídeo curtido."
        }

        return "Senhor, não encontrei o botão de curtir."
    }

    private suspend fun commentOnVideo(text: String): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o TikTok."

        val commentButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/comment_icon")
        if (commentButtons.isNotEmpty()) {
            commentButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1500)

            val root2 = accessibilityService.rootInActiveWindow
            val editTexts = root2?.findAccessibilityNodeInfosByViewId("$packageName:id/edit_text")
            if (editTexts != null && editTexts.isNotEmpty()) {
                val args = android.os.Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editTexts[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(500)

                val sendButtons = root2.findAccessibilityNodeInfosByViewId("$packageName:id/send_btn")
                if (sendButtons.isNotEmpty()) {
                    sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return "Senhor, comentário enviado: '$text'"
                }
            }
        }
        return "Senhor, não consegui comentar. A interface pode ter mudado."
    }

    private suspend fun followCurrentProfile(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não vejo o TikTok."

        val followButtons = root.findAccessibilityNodeInfosByViewId("$packageName:id/follow_button")
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

    private suspend fun scrollNext(): String {
        val root = accessibilityService.rootInActiveWindow ?: return "Senhor, não consigo scrollar."
        val scrollable = root.findAccessibilityNodeInfosByViewId("$packageName:id/view_pager")

        if (scrollable.isNotEmpty()) {
            scrollable[0].performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            return "Próximo vídeo."
        }

        val recyclerViews = root.findAccessibilityNodeInfosByViewId("$packageName:id/recycler_view")
        if (recyclerViews.isNotEmpty()) {
            recyclerViews[0].performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            return "Próximo vídeo."
        }

        return "Senhor, não encontrei área scrollável."
    }

    private suspend fun saveProfilesToFile(): String {
        if (extractedProfiles.isEmpty()) {
            return "Senhor, não há perfis extraídos para salvar."
        }

        val fileName = "tiktok_leads_${System.currentTimeMillis()}.txt"
        val file = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)

        file.writeText("=== LEADS TIKTOK - J&C TRADING ===\n")
        file.appendText("Gerado em: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date())}\n\n")
        extractedProfiles.forEachIndexed { index, profile ->
            file.appendText("${index + 1}. $profile\n")
        }

        return "Senhor, **${extractedProfiles.size} perfis** salvos em Downloads/$fileName"
    }

    private fun extractNumber(command: String): Int? {
        val regex = Regex("\\d+")
        val match = regex.find(command)
        return match?.value?.toIntOrNull()
    }

    private fun extractCommentText(command: String): String? {
        val patterns = listOf("comentar ", "comenta ", "diz ", "escreve ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                return command.substring(idx + p.length).trim()
            }
        }
        return null
    }
}
