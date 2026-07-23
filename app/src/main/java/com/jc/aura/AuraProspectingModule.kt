package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraProspectingModule — Extrai perfis de redes sociais por critérios e exporta para CSV.
 * 
 * A Aura entra no Instagram, Facebook, TikTok e LinkedIn, lê perfis no ecrã,
 * filtra pelos critérios definidos, e guarda os resultados num ficheiro CSV.
 * 
 * Exemplos de uso:
 * - "prospectar Instagram com 1000 seguidores angolano que posta regularmente"
 * - "prospectar LinkedIn mais de 500 conexões em Angola"
 * - "prospectar TikTok angolano com mais de 2000 seguidores"
 * - "prospectar Facebook angolano com mais de 50 posts"
 * 
 * Critérios suportados:
 * - Seguidores mínimos
 * - Localização (Angola, Luanda, etc.)
 * - Frequência de postagem (regular, activo, etc.)
 * - Niche/área de actuação
 * - Tipo de conta (pessoal, negócio, criador)
 * - Verificação (verificado ou não)
 * 
 * Exportação:
 * - CSV com cabeçalhos: plataforma, username, nome, seguidores, localização, bio, niche, postagens, verificado, data
 * - Guardado em /Download/AuraProspecting/
 * - Nome: prospeccao_[plataforma]_[data].csv
 */
class AuraProspectingModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val accessibilityService: AccessibilityService
) {

    companion object {
        private const val TAG = "Prospecting"
        private const val MAX_PROFILES_PER_SESSION = 200
        private const val SCROLL_DELAY = 2500L
        private const val PROFILE_READ_DELAY = 1500L
    }

    data class Profile(
        val platform: String,
        val username: String,
        val displayName: String,
        val followers: String,
        val following: String,
        val location: String,
        val bio: String,
        val postCount: String,
        val isVerified: Boolean,
        val isBusiness: Boolean,
        val niche: String,
        val scrapedAt: String
    )

    data class ProspectingCriteria(
        val platform: String,
        val minFollowers: Int = 0,
        val minPosts: Int = 0,
        val locationKeywords: List<String> = listOf(),
        val nicheKeywords: List<String> = listOf(),
        val requireVerified: Boolean = false,
        val requireBusiness: Boolean = false,
        val requireRegularPosting: Boolean = false,
        val hashtag: String = "",
        val maxProfiles: Int = MAX_PROFILES_PER_SESSION
    )

    private val scrapedProfiles = mutableListOf<Profile>()
    private var isProspecting = false
    private var autoReplyActive = false
    private var pendingConfirmation = false
    private var pendingAction: String? = null
    private var pendingCommand: String? = null

    /**
     * Função principal — gere comandos de prospecção
     */
    suspend fun handle(command: String): String {
        return try {
            when {
                // === CONFIRMAÇÃO DE ACÇÕES ===
                pendingConfirmation -> {
                    if (command.contains("sim") || command.contains("confirmar") || command.contains("pode") || command.contains("envia") || command.contains("manda") || command.contains("segue") || command.contains("faz")) {
                        pendingConfirmation = false
                        when (pendingAction) {
                            "send_messages" -> sendAutoMessagesToProfiles(pendingCommand ?: "")
                            "send_reply" -> sendAutoReplyToLastMessage(pendingCommand ?: "")
                            else -> "Acção não reconhecida."
                        }
                    } else if (command.contains("não") || command.contains("nao") || command.contains("cancelar") || command.contains("para") || command.contains("esquece")) {
                        pendingConfirmation = false
                        pendingAction = null
                        pendingCommand = null
                        "Acção cancelada. Nenhuma mensagem foi enviada."
                    } else {
                        "Diga 'sim' para confirmar ou 'não' para cancelar."
                    }
                }

                command.contains("prospectar") || command.contains("prospecção") || command.contains("prospecao") || command.contains("prospect") -> {
                    val criteria = parseCriteria(command)
                    startProspecting(criteria)
                }
                command.contains("parar prospecção") || command.contains("parar prospectar") || command.contains("stop prospect") -> {
                    stopProspecting()
                }
                command.contains("enviar mensagem") || command.contains("mandar mensagem") || command.contains("enviar dm") || command.contains("mandar dm") || command.contains("enviar mensagens para os perfis") || command.contains("mensagens automáticas") || command.contains("contactar perfis") -> {
                    askConfirmation("send_messages", command)
                }
                command.contains("auto responder") || command.contains("auto-responder") || command.contains("responder automaticamente") || command.contains("respostas automáticas") -> {
                    toggleAutoReply(command)
                }
                command.contains("ver respostas automáticas") || command.contains("respostas recebidas") || command.contains("ver respostas") -> {
                    showAutoReplyReport()
                }
                command.contains("definir mensagem automática") || command.contains("definir mensagem") || command.contains("mensagem padrão") || command.contains("configurar mensagem") -> {
                    setAutoMessage(command)
                }
                command.contains("ver mensagens enviadas") || command.contains("relatório de mensagens") || command.contains("relatório dm") || command.contains("msgs enviadas") -> {
                    showSentMessagesReport()
                }
                command.contains("ver perfis raspados") || command.contains("ver prospecção") || command.contains("ver prospectados") -> {
                    showScrapedProfiles()
                }
                command.contains("exportar csv") || command.contains("exportar perfis") || command.contains("guardar csv") || command.contains("salvar csv") -> {
                    exportToCSV()
                }
                command.contains("exportar pdf") || command.contains("guardar pdf") || command.contains("salvar pdf") -> {
                    exportToCSV()
                }
                command.contains("limpar perfis") || command.contains("reset prospecção") -> {
                    clearProfiles()
                }
                command.contains("stats prospecção") || command.contains("estatísticas prospecção") -> {
                    showProspectingStats()
                }
                else -> {
                    "Prospecção e contacto automático disponíveis:\n" +
                    "\n=== PROSPECÇÃO ===\n" +
                    "• 'prospectar Instagram com 1000 seguidores angolano'\n" +
                    "• 'prospectar LinkedIn 500 conexões Luanda marketing'\n" +
                    "• 'prospectar TikTok 2000 seguidores angolano activo'\n" +
                    "• 'prospectar Facebook 500 seguidores Angola criador'\n" +
                    "• 'ver perfis raspados'\n" +
                    "• 'exportar csv' / 'exportar pdf'\n" +
                    "• 'parar prospecção'\n" +
                    "• 'stats prospecção'\n\n" +
                    "=== MENSAGENS AUTOMÁTICAS ===\n" +
                    "• 'enviar mensagens para os perfis encontrados' (pergunta confirmação)\n" +
                    "• 'mandar DM para os prospectados' (pergunta confirmação)\n" +
                    "• 'definir mensagem automática com o texto Olá...'\n" +
                    "• 'ver mensagens enviadas'\n\n" +
                    "=== AUTO-RESPOSTA ===\n" +
                    "• 'auto responder' — activa/desactiva respostas automáticas a DMs recebidos\n" +
                    "• 'auto responder Instagram' — activa para uma rede específica\n" +
                    "• 'ver respostas automáticas' — vê respostas enviadas automaticamente\n\n" +
                    "Nota: A Aura SEMPRE pergunta confirmação antes de enviar mensagens."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro prospecção: ${e.message}")
            "Erro na prospecção: ${e.message}"
        }
    }

    /**
     * Pede confirmação ao Boss antes de executar qualquer acção de envio de mensagens
     */
    private suspend fun askConfirmation(action: String, command: String): String {
        pendingConfirmation = true
        pendingAction = action
        pendingCommand = command

        val profileCount = scrapedProfiles.size
        val customMessage = extractCustomMessage(command)
        val defaultMessage = memory.get("prospect_auto_message")
            ?: "Proposta de aquisição de perfil — Mwango Brain"

        return buildString {
            appendLine("⚠️ PEDIDO DE CONFIRMAÇÃO")
            appendLine("━".repeat(35))
            when (action) {
                "send_messages" -> {
                    appendLine("Vou enviar mensagens para $profileCount perfis encontrados.")
                    appendLine("Mensagem: ${if (customMessage.isNotEmpty()) customMessage else defaultMessage}")
                    appendLine()
                    appendLine("Diga 'sim' para confirmar o envio.")
                    appendLine("Diga 'não' para cancelar.")
                }
                "send_reply" -> {
                    appendLine("Vou responder automaticamente à última mensagem recebida.")
                    appendLine()
                    appendLine("Diga 'sim' para confirmar.")
                    appendLine("Diga 'não' para cancelar.")
                }
            }
        }
    }

    /**
     * Analisa o comando e extrai critérios de prospecção
     */
    private fun parseCriteria(command: String): ProspectingCriteria {
        val platform = detectPlatform(command)
        val minFollowers = extractNumberBefore(command, listOf("seguidor", "seguidores", "followers", "sub")) ?: 1000
        val minPosts = extractNumberBefore(command, listOf("post", "posts", "publicações", "publicacao")) ?: 0
        val locationKeywords = extractLocation(command)
        val nicheKeywords = extractNiche(command)
        val requireVerified = command.contains("verificado") || command.contains("verificado")
        val requireBusiness = command.contains("negócio") || command.contains("business") || command.contains("empresa")
        val requireRegularPosting = command.contains("regular") || command.contains("activo") || command.contains("ativo") || command.contains("frequente")
        val hashtag = extractHashtag(command)

        return ProspectingCriteria(
            platform = platform,
            minFollowers = minFollowers,
            minPosts = minPosts,
            locationKeywords = locationKeywords,
            nicheKeywords = nicheKeywords,
            requireVerified = requireVerified,
            requireBusiness = requireBusiness,
            requireRegularPosting = requireRegularPosting,
            hashtag = hashtag
        )
    }

    /**
     * CORE: Inicia a prospecção automática numa rede social
     * 
     * Fluxo:
     * 1. Abre a app da rede social
     * 2. Navega para a secção de exploração/busca
     * 3. Procura por hashtag ou localização
     * 4. Para cada perfil encontrado:
     *    a. Lê username, nome, seguidores, localização, bio
     *    b. Verifica se cumpre os critérios
     *    c. Se sim, guarda no CSV
     * 5. Continua até ao limite
     * 6. Exporta CSV
     */
    private suspend fun startProspecting(criteria: ProspectingCriteria): String {
        if (isProspecting) {
            return "Já estou a prospectar! Diga 'parar prospecção' se quiser parar."
        }

        isProspecting = true
        scrapedProfiles.clear()
        val startTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        memory.save("prospect_platform", criteria.platform)
        memory.save("prospect_start", startTime)
        memory.save("prospect_criteria", criteria.toString())

        Log.d(TAG, "Iniciando prospecção: $criteria")

        return try {
            // Step 1: Abrir a app
            val opened = openApp(criteria.platform)
            if (!opened) {
                isProspecting = false
                return "Não consegui abrir o ${criteria.platform}. Abra manualmente e tente novamente."
            }
            delay(3000)

            // Step 2: Navegar para exploração ou busca
            val navigated = navigateToExplore(criteria.platform, criteria.hashtag)
            if (!navigated) {
                isProspecting = false
                return "Não consegui navegar para a secção de exploração. Tente navegar manualmente."
            }
            delay(2000)

            // Step 3: Extrair perfis do ecrã
            var totalScanned = 0
            var totalMatched = 0
            val maxScrolls = 20 // Máximo de scrolls por sessão

            for (scroll in 0 until maxScrolls) {
                if (!isProspecting || scrapedProfiles.size >= criteria.maxProfiles) break

                val root = accessibilityService.rootInActiveWindow ?: break
                val profileNodes = findProfileNodes(root, criteria.platform)

                for (node in profileNodes) {
                    if (!isProspecting || scrapedProfiles.size >= criteria.maxProfiles) break

                    val profile = extractProfileFromNode(node, criteria.platform)
                    if (profile != null) {
                        totalScanned++
                        if (matchesCriteria(profile, criteria)) {
                            scrapedProfiles.add(profile)
                            totalMatched++
                            memory.save("prospect_matched", totalMatched.toString())
                            Log.d(TAG, "Match! ${profile.username} — ${profile.followers} seguidores")
                        }
                    }
                }

                // Scroll para ver mais perfis
                val freshRoot = accessibilityService.rootInActiveWindow ?: break
                scrollDown(freshRoot)
                delay(SCROLL_DELAY + (500..1500).random()) // Randomizar para parecer humano
            }

            isProspecting = false
            val endTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            memory.save("prospect_end", endTime)
            memory.save("prospect_scanned", totalScanned.toString())
            memory.save("prospect_matched_total", totalMatched.toString())

            // Auto-exportar
            val csvPath = saveProfilesToCSV(criteria.platform)

            buildString {
                appendLine("🎯 Prospeção concluída!")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📱 Plataforma: ${criteria.platform}")
                appendLine("🔍 Critérios: ${criteria.minFollowers}+ seguidores, ${criteria.locationKeywords}")
                appendLine("📊 Perfis analisados: $totalScanned")
                appendLine("✅ Perfis encontrados: $totalMatched")
                appendLine("⏱️ Tempo: $startTime - $endTime")
                appendLine("📁 CSV: $csvPath")
                appendLine()
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                if (scrapedProfiles.isNotEmpty()) {
                    appendLine("📋 Top 5 perfis encontrados:")
                    scrapedProfiles.take(5).forEach { p ->
                        appendLine("  • @${p.username} — ${p.followers} seg — ${p.location}")
                    }
                }
                appendLine()
                appendLine("Diga 'exportar csv' para re-exportar, ou 'ver perfis raspados' para detalhes.")
            }
        } catch (e: Exception) {
            isProspecting = false
            Log.e(TAG, "Erro prospecção: ${e.message}")
            "Erro na prospecção: ${e.message}"
        }
    }

    /**
     * Para a prospecção
     */
    private fun stopProspecting(): String {
        isProspecting = false
        return "Prospecção parada. ${scrapedProfiles.size} perfis encontrados até agora."
    }

    /**
     * Mostra os perfis raspados
     */
    private fun showScrapedProfiles(): String {
        if (scrapedProfiles.isEmpty()) {
            val total = memory.get("prospect_matched_total")?.toIntOrNull() ?: 0
            return if (total > 0) {
                "Última prospecção encontrou $total perfis. Diga 'exportar csv' para descarregar."
            } else {
                "Nenhum perfil raspado ainda. Diga 'prospectar [rede] com [critérios]'."
            }
        }

        return buildString {
            appendLine("📋 Perfis raspados (${scrapedProfiles.size}):")
            appendLine("━".repeat(50))
            scrapedProfiles.forEachIndexed { i, p ->
                appendLine()
                appendLine("${i + 1}. @${p.username} (${p.displayName})")
                appendLine("   📱 ${p.platform} | 👥 ${p.followers} seg | 📍 ${p.location}")
                appendLine("   📝 ${p.bio.take(100)}")
                appendLine("   📊 ${p.postCount} posts | ${if (p.isVerified) "✅ Verificado" else ""}${if (p.isBusiness) "💼 Negócio" else ""}")
            }
        }
    }

    /**
     * Exporta perfis para CSV
     */
    private fun exportToCSV(): String {
        if (scrapedProfiles.isEmpty()) {
            return "Nenhum perfil para exportar. Faça uma prospecção primeiro."
        }
        val platform = scrapedProfiles.firstOrNull()?.platform ?: "multi"
        return saveProfilesToCSV(platform)
    }

    /**
     * Mostra estatísticas de prospecção
     */
    private fun showProspectingStats(): String {
        return buildString {
            appendLine("📊 Estatísticas de Prospecção:")
            appendLine("━".repeat(35))
            appendLine("  📱 Última plataforma: ${memory.get("prospect_platform") ?: "N/A"}")
            appendLine("  🔍 Perfis analisados: ${memory.get("prospect_scanned") ?: "0"}")
            appendLine("  ✅ Perfis encontrados: ${memory.get("prospect_matched_total") ?: "0"}")
            appendLine("  ⏱️ Última prospecção: ${memory.get("prospect_start") ?: "N/A"}")
            appendLine("  📂 Perfis em memória: ${scrapedProfiles.size}")
            appendLine("  🤖 A prospectar: ${if (isProspecting) "SIM" else "Não"}")
        }
    }

    /**
     * Limpa perfis em memória
     */
    private fun clearProfiles(): String {
        scrapedProfiles.clear()
        return "Perfis limpos. ${scrapedProfiles.size} perfis em memória."
    }

    // =============================================
    // === MENSAGENS AUTOMÁTICAS PARA PERFIS ===
    // =============================================

    /**
     * Envia mensagens automáticas para todos os perfis encontrados na última prospecção.
     * 
     * Fluxo:
     * 1. Carrega perfis em memória
     * 2. Abre a rede social de cada perfil
     * 3. Navega ao perfil do contacto
     * 4. Clica em "Message" / "Enviar mensagem"
     * 5. Escreve a mensagem personalizada
     * 6. Envia
     * 
     * Uso:
     * - "enviar mensagens para os perfis encontrados"
     * - "mandar DM para os prospectados"
     * - "enviar mensagem automática para todos os perfis"
     * - "mandar mensagem para os perfis com texto Olá, vi o seu perfil..."
     */
    private suspend fun sendAutoMessagesToProfiles(command: String): String {
        if (scrapedProfiles.isEmpty()) {
            val lastCount = memory.get("prospect_matched_total")?.toIntOrNull() ?: 0
            return if (lastCount > 0) {
                "Os perfis da última prospecção já foram exportados mas não estão em memória. " +
                "Diga 'prospectar [rede] com [critérios]' para fazer uma nova prospecção e depois enviar mensagens."
            } else {
                "Nenhum perfil encontrado ainda. Faça uma prospecção primeiro."
            }
        }

        // Extrair mensagem personalizada do comando, ou usar padrão
        val customMessage = extractCustomMessage(command)
        val defaultMessage = memory.get("prospect_auto_message")
            ?: "Olá! Somos a Mwango Brain, uma agência digital com 17 anos de mercado. " +
               "Reparamos no seu perfil e temos interesse em adquirir a sua conta. " +
               "Trabalhamos com criadores de conteúdo e oferecemos condições justas. " +
               "Gostaria de conversar sobre uma possível aquisição? Podemos agendar uma chamada rápida. " +
               "Cumprimentos, equipa Mwango Brain. Let's Brain Together."

        val messageToSend = if (customMessage.isNotEmpty()) customMessage else defaultMessage

        isProspecting = true
        var sentCount = 0
        var failedCount = 0
        val maxMessages = 50 // Limite por sessão para segurança
        val startTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        Log.d(TAG, "Iniciando envio de mensagens automáticas para ${scrapedProfiles.size} perfis")

        return try {
            val profilesToSend = scrapedProfiles.take(maxMessages)

            for ((index, profile) in profilesToSend.withIndex()) {
                if (!isProspecting) break

                try {
                    val result = sendMessageToProfile(profile, messageToSend)
                    if (result) {
                        sentCount++
                        memory.save("dm_sent_${profile.username}", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                    } else {
                        failedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "Erro ao enviar DM para ${profile.username}: ${e.message}")
                }

                // Delay entre mensagens para parecer natural (30-90 segundos)
                val delayMs = (30000L..90000L).random()
                delay(delayMs)
            }

            isProspecting = false
            val endTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            memory.save("dm_sent_total", sentCount.toString())
            memory.save("dm_failed_total", failedCount.toString())
            memory.save("dm_session_time", "$startTime - $endTime")

            buildString {
                appendLine("📨 Envio de mensagens concluído!")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📊 Mensagens enviadas: $sentCount")
                appendLine("❌ Falhas: $failedCount")
                appendLine("📋 Total de perfis: ${profilesToSend.size}")
                appendLine("⏱️ Tempo: $startTime - $endTime")
                appendLine()
                if (sentCount > 0) {
                    appendLine("✅ Mensagens enviadas com sucesso para:")
                    scrapedProfiles.take(sentCount).forEach { p ->
                        appendLine("  • @${p.username} (${p.platform})")
                    }
                }
                appendLine()
                appendLine("Diga 'ver mensagens enviadas' para ver o relatório completo.")
            }
        } catch (e: Exception) {
            isProspecting = false
            Log.e(TAG, "Erro no envio de mensagens: ${e.message}")
            "Erro ao enviar mensagens: ${e.message}"
        }
    }

    /**
     * Envia uma mensagem para um perfil específico via Accessibility
     */
    private suspend fun sendMessageToProfile(profile: Profile, message: String): Boolean {
        return try {
            // Step 1: Abrir a app da rede social
            val packageName = when (profile.platform) {
                "Instagram" -> "com.instagram.android"
                "Facebook" -> "com.facebook.katana"
                "LinkedIn" -> "com.linkedin.android"
                "TikTok" -> "com.zhiliaoapp.musically"
                else -> return false
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) return false
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(launchIntent)
            delay(3000)

            // Step 2: Navegar ao perfil (usar a função de busca)
            val root = accessibilityService.rootInActiveWindow ?: return false
            val navigated = navigateToProfileViaSearch(profile, root)
            if (!navigated) return false
            delay(3000)

            // Step 3: Clicar no botão de "Message" / "Enviar mensagem"
            val profileRoot = accessibilityService.rootInActiveWindow ?: return false
            val messageBtn = findMessageButton(profileRoot, profile.platform)
            if (messageBtn == null) return false

            messageBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            // Step 4: Escrever a mensagem
            val msgRoot = accessibilityService.rootInActiveWindow ?: return false
            val textField = findMessageTextField(msgRoot, profile.platform)
            if (textField == null) return false

            textField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(500)

            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    message
                )
            }
            textField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(minOf(message.length * 30L, 3000L))

            // Step 5: Enviar (clicar no botão de enviar)
            val sendRoot = accessibilityService.rootInActiveWindow ?: return false
            val sendBtn = findSendButton(sendRoot, profile.platform)
            sendBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            // Voltar atrás
            val backBtn = findNodeByDescContains(sendRoot, "Back")
                ?: findNodeByDescContains(sendRoot, "Navigate up")
            backBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro sendMessageToProfile: ${e.message}")
            false
        }
    }

    /**
     * Navega ao perfil de um utilizador usando a função de busca
     */
    private suspend fun navigateToProfileViaSearch(profile: Profile, root: AccessibilityNodeInfo): Boolean {
        return try {
            // Clicar na barra de busca
            val searchBtn = findNodeByDescContains(root, "Search")
                ?: findNodeByDescContains(root, "Search and Explore")
                ?: findNodeById(root, "search_tab")
                ?: findNodeById(root, "search_bar")
                ?: findNodeByDescContains(root, "Pesquisar")
            searchBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(2000)

            // Escrever o username na barra de busca
            val searchRoot = accessibilityService.rootInActiveWindow ?: return false
            val searchField = findNodeById(searchRoot, "search_edit_text")
                ?: findNodeById(searchRoot, "action_bar_search_edit_text")
                ?: findNodeByDescContains(searchRoot, "Search")
                ?: findNodeById(searchRoot, "search_bar")
            searchField?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(500)

            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    profile.username
                )
            }
            searchField?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(2000)

            // Clicar no primeiro resultado
            val resultRoot = accessibilityService.rootInActiveWindow ?: return false
            val firstResult = findNodeByText(resultRoot, "@${profile.username}")
                ?: findNodeByText(resultRoot, profile.username)
                ?: findNodeByText(resultRoot, profile.displayName)

            firstResult?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(3000)
            firstResult != null
        } catch (e: Exception) {
            Log.e(TAG, "Erro navigateToProfile: ${e.message}")
            false
        }
    }

    /**
     * Encontra o botão de "Message" no perfil
     */
    private fun findMessageButton(root: AccessibilityNodeInfo, platform: String): AccessibilityNodeInfo? {
        return when (platform) {
            "Instagram" -> {
                findNodeByText(root, "Message")
                    ?: findNodeByDescContains(root, "Message")
                    ?: findNodeById(root, "row_message_button")
                    ?: findNodeByDescContains(root, "Send message")
            }
            "Facebook" -> {
                findNodeByText(root, "Message")
                    ?: findNodeByText(root, "Enviar mensagem")
                    ?: findNodeByDescContains(root, "Send a message")
                    ?: findNodeById(root, "profile_action_message")
            }
            "LinkedIn" -> {
                findNodeByText(root, "Message")
                    ?: findNodeByDescContains(root, "Message")
                    ?: findNodeById(root, "feed_bottom_sheet_message_button")
                    ?: findNodeByDescContains(root, "Send a message")
            }
            "TikTok" -> {
                findNodeByDescContains(root, "Message")
                    ?: findNodeByText(root, "Enviar mensagem")
                    ?: findNodeById(root, "message_btn")
            }
            else -> findNodeByText(root, "Message") ?: findNodeByDescContains(root, "Message")
        }
    }

    /**
     * Encontra o campo de texto para escrever a mensagem
     */
    private fun findMessageTextField(root: AccessibilityNodeInfo, platform: String): AccessibilityNodeInfo? {
        return when (platform) {
            "Instagram" -> {
                findNodeById(root, "com.instagram:id/layout_message_thread_entry_point")
                    ?: findNodeById(root, "message_text_field")
                    ?: findNodeByDescContains(root, "Message")
                    ?: findNodeByDescContains(root, "Write a message")
            }
            "Facebook" -> {
                findNodeById(root, "composer_input_text")
                    ?: findNodeByDescContains(root, "Aa")
                    ?: findNodeByDescContains(root, "Write something")
            }
            "LinkedIn" -> {
                findNodeById(root, "com.linkedin:id/msg_text_editor")
                    ?: findNodeByDescContains(root, "Write a message")
                    ?: findNodeByDescContains(root, "Type a message")
            }
            "TikTok" -> {
                findNodeById(root, "com.zhiliaoapp.musically:id/message_edit_text")
                    ?: findNodeByDescContains(root, "Write a message")
                    ?: findNodeByDescContains(root, "Add a message")
            }
            else -> findNodeByDescContains(root, "Write a message")
                ?: findNodeByDescContains(root, "Message")
        }
    }

    /**
     * Encontra o botão de enviar mensagem
     */
    private fun findSendButton(root: AccessibilityNodeInfo, platform: String): AccessibilityNodeInfo? {
        return when (platform) {
            "Instagram" -> {
                findNodeByDescContains(root, "Send")
                    ?: findNodeById(root, "row_message_send_button")
                    ?: findNodeById(root, "send_button")
            }
            "Facebook" -> {
                findNodeByDescContains(root, "Send")
                    ?: findNodeById(root, "send_button")
            }
            "LinkedIn" -> {
                findNodeByDescContains(root, "Send")
                    ?: findNodeById(root, "com.linkedin:id/send_button")
            }
            "TikTok" -> {
                findNodeByDescContains(root, "Send")
                    ?: findNodeById(root, "com.zhiliaoapp.musically:id/send_btn")
            }
            else -> findNodeByDescContains(root, "Send") ?: findNodeByText(root, "Send")
        }
    }

    /**
     * Extrai mensagem personalizada do comando
     */
    private fun extractCustomMessage(command: String): String {
        val patterns = listOf("com o texto", "com a mensagem", "dizendo", "mensagem:", "texto:")
        for (pattern in patterns) {
            val idx = command.indexOf(pattern, ignoreCase = true)
            if (idx >= 0) return command.substring(idx + pattern.length).trim()
        }
        return ""
    }

    /**
     * Define a mensagem padrão para envio automático
     */
    private fun setAutoMessage(command: String): String {
        val message = extractCustomMessage(command)
        if (message.isEmpty()) {
            return "Indique a mensagem. Exemplo: 'definir mensagem automática com o texto Olá, gostaria de...'"
        }
        memory.save("prospect_auto_message", message)
        return "Mensagem automática definida: \"$message\"\n\nSerá usada quando disser 'enviar mensagens para os perfis encontrados'."
    }

    /**
     * Mostra relatório de mensagens enviadas
     */
    private fun showSentMessagesReport(): String {
        val sentTotal = memory.get("dm_sent_total")?.toIntOrNull() ?: 0
        val failedTotal = memory.get("dm_failed_total")?.toIntOrNull() ?: 0
        val sessionTime = memory.get("dm_session_time") ?: "N/A"
        val sentMessages = memory.getAllByPrefix("dm_sent_")

        return buildString {
            appendLine("📊 Relatório de Mensagens Enviadas:")
            appendLine("━".repeat(40))
            appendLine("  ✅ Enviadas: $sentTotal")
            appendLine("  ❌ Falhas: $failedTotal")
            appendLine("  ⏱️ Sessão: $sessionTime")
            appendLine()
            if (sentMessages.isNotEmpty()) {
                appendLine("📋 Últimas mensagens:")
                sentMessages.entries.take(20).forEach { (key, value) ->
                    val username = key.removePrefix("dm_sent_")
                    appendLine("  • @$username — $value")
                }
            }
        }
    }

    // =============================================
    // === AUTO-RESPOSTA AUTOMÁTICA A DMs ===
    // =============================================

    /**
     * Activa ou desactiva o modo de auto-resposta.
     * 
     * Quando activo, a Aura monitoriza DMs dos perfis contactados e,
     * quando recebe uma resposta, pede confirmação ao Boss e depois
     * responde automaticamente com base no contexto da conversa.
     * 
     * Uso:
     * - "auto responder" — activa/desactiva
     * - "auto responder Instagram" — activa para rede específica
     * - "auto responder todas" — activa para todas as redes
     */
    private suspend fun toggleAutoReply(command: String): String {
        val platform = detectPlatform(command)
        val isTogglingOn = !autoReplyActive || command.contains("activar") || command.contains("ativar") || !command.contains("desactivar") && !command.contains("desativar") && !command.contains("parar")

        if (isTogglingOn) {
            autoReplyActive = true
            memory.save("auto_reply_active", "true")
            memory.save("auto_reply_platform", platform)
            memory.save("auto_reply_start", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

            return buildString {
                appendLine("🔄 Auto-Resposta AUTIVADA!")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📡 Rede: ${if (platform == "multi") "Todas as redes" else platform}")
                appendLine()
                appendLine("Como funciona:")
                appendLine("  1. A Aura monitoriza as mensagens recebidas dos perfis contactados")
                appendLine("  2. Quando alguém responde, a Aura pede confirmação ao Boss")
                appendLine("  3. Após confirmação, a Aura responde de acordo com o contexto")
                appendLine("  4. A resposta é baseada no que o perfil disse")
                appendLine()
                appendLine("Exemplo:")
                appendLine("  Perfil: 'Olá, obrigado pelo contacto. Qual o serviço?'")
                appendLine("  Aura pergunta: 'Recebi resposta de @joao. Respondo sobre os serviços da Mwango Brain?'")
                appendLine("  Boss: 'Sim'")
                appendLine("  Aura: Envia resposta contextual")
                appendLine()
                appendLine("Diga 'auto responder desactivar' para parar.")
                appendLine("Diga 'ver respostas automáticas' para ver o histórico.")
            }
        } else {
            autoReplyActive = false
            memory.save("auto_reply_active", "false")

            val repliesSent = memory.get("auto_reply_sent_total")?.toIntOrNull() ?: 0

            return buildString {
                appendLine("⏹️ Auto-Resposta DESACTIVADA.")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📊 Respostas automáticas enviadas nesta sessão: $repliesSent")
                appendLine("Diga 'auto responder' para activar novamente.")
            }
        }
    }

    /**
     * Envia uma resposta automática contextual à última mensagem recebida.
     * 
     * Fluxo:
     * 1. Lê a última mensagem recebida de um perfil contactado
     * 2. Gera uma resposta contextual usando a IA
     * 3. Navega ao perfil e envia a resposta
     * 
     * A resposta é SEMPRE baseada no que o perfil disse e nos serviços da Mwango Brain.
     */
    private suspend fun sendAutoReplyToLastMessage(command: String): String {
        val lastReply = memory.get("last_incoming_dm")
        val lastSender = memory.get("last_incoming_dm_sender")
        val lastPlatform = memory.get("last_incoming_dm_platform")

        if (lastReply.isNullOrEmpty() || lastSender.isNullOrEmpty()) {
            return "Nenhuma mensagem recebida pendente de resposta."
        }

        val replyCount = memory.get("auto_reply_sent_total")?.toIntOrNull() ?: 0

        // Gerar resposta contextual com base no que o perfil disse
        val contextualResponse = generateContextualReply(lastReply, lastSender)

        // Enviar a resposta
        val profile = scrapedProfiles.find { it.username.equals(lastSender, ignoreCase = true) }
            ?: Profile(lastPlatform ?: "Instagram", lastSender, lastSender, "", "", "", "", "", false, false, "", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        val result = sendMessageToProfile(profile, contextualResponse)

        return if (result) {
            val newCount = replyCount + 1
            memory.save("auto_reply_sent_total", newCount.toString())
            memory.save("auto_reply_sent_${lastSender}_${System.currentTimeMillis()}", "${lastSender}: '$lastReply' → '$contextualResponse'")

            buildString {
                appendLine("✅ Resposta automática enviada!")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("👤 De: @$lastSender ($lastPlatform)")
                appendLine("📩 Recebeu: \"$lastReply\"")
                appendLine("📤 Resposta: \"$contextualResponse\"")
                appendLine("📊 Total de respostas automáticas: $newCount")
            }
        } else {
            "Não consegui enviar a resposta a @$lastSender. Verifique a ligação e tente novamente."
        }
    }

    /**
     * Gera uma resposta contextual usando a IA, baseada na mensagem recebida
     * e nos serviços da Mwango Brain.
     * 
     * A resposta é SEMPRE relevante ao que a pessoa disse.
     */
    private suspend fun generateContextualReply(incomingMessage: String, sender: String): String {
        return try {
            val mwangoContext = "Empresa: Mwango Brain — agência digital angolana, 17 anos de mercado, CEO Aniceto D'Carvalho. " +
                "Serviços: desenvolvimento de apps/websites, marketing digital, branding, design gráfico, gestão de redes sociais, " +
                "SEO, produção de conteúdo, consultoria digital. " +
                "Contexto actual: Estamos a contactar criadores de conteúdo para propor a aquisição dos seus perfis nas redes sociais. " +
                "Let's Brain Together."

            val prompt = """$mwangoContext

Recebeste uma mensagem de um criador de conteúdo cujo perfil pretendemos adquirir (@$sender):
"${incomingMessage}"

Gera UMA resposta curta, inteligente e natural em português europeu (pt-PT).
SEM gírias. SEM calão. Português culto mas com personalidade.

Regras:
- Responde directamente ao que a pessoa disse
- Se perguntou sobre a proposta, explica que somos a Mwango Brain e temos interesse em adquirir o perfil
- Se perguntou sobre condições/valores, diz que depende do perfil e propõe uma chamada para negociar
- Se mostrou interesse, propõe uma conversa rápida (chamada ou reunião)
- Se não mostrou interesse ou recusou, agradece e deixa a porta aberta para o futuro
- Máximo 3 frases. Directo ao ponto.
- Não uses 'Senhor' — usa o nome da pessoa ou um tom profissional mas acessível
- Sê atrevida e confiante, mas respeitosa
- Não digas que és IA

Apenas a mensagem, sem aspas, sem explicação."""

            withContext(Dispatchers.IO) {
                val openRouterKey = BuildConfig.OPENROUTER_KEY
                val url = java.net.URL("https://openrouter.ai/api/v1/chat/completions")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("HTTP-Referer", "https://mwangobrain.co.ao")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val json = org.json.JSONObject().apply {
                    put("model", "meta-llama/llama-3.3-70b-instruct")
                    put("messages", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("role", "system")
                            put("content", "És um assistente comercial da Mwango Brain especializado em aquisição de perfis de redes sociais. Respondes SEMPRE em português europeu (pt-PT). Sê directo, inteligente, atrevido e profissional. SEM gírias. O teu objectivo é negociar a compra de perfis de criadores de conteúdo.")
                        })
                        put(org.json.JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.7)
                    put("max_tokens", 200)
                }

                connection.outputStream.write(json.toString().toByteArray())

                if (connection.responseCode in 200..299) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val responseJson = org.json.JSONObject(response)
                    responseJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                } else {
                    "Obrigado pela mensagem. Gostaria de agendar uma conversa para falarmos melhor sobre como podemos ajudar?"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gerar resposta: ${e.message}")
            "Obrigado pela mensagem. Gostaria de agendar uma conversa rápida para falarmos melhor?"
        }
    }

    /**
     * Regista uma mensagem recebida de um perfil contactado.
     * Chamado pelo sistema quando uma mensagem nova é detectada.
     */
    fun registerIncomingDM(sender: String, message: String, platform: String): String {
        if (!autoReplyActive) return ""

        memory.save("last_incoming_dm", message)
        memory.save("last_incoming_dm_sender", sender)
        memory.save("last_incoming_dm_platform", platform)
        memory.save("last_incoming_dm_time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // Activar confirmação para responder
        pendingConfirmation = true
        pendingAction = "send_reply"
        pendingCommand = "auto_reply_to:$sender:$message:$platform"

        return buildString {
            appendLine("📩 NOVA MENSAGEM RECEBIDA!")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("👤 De: @$sender ($platform)")
            appendLine("💬 Mensagem: \"$message\"")
            appendLine()
            appendLine("⚠️ Diga 'sim' para eu responder automaticamente.")
            appendLine("Diga 'não' para ignorar.")
        }
    }

    /**
     * Mostra relatório de respostas automáticas
     */
    private fun showAutoReplyReport(): String {
        val isActive = memory.get("auto_reply_active") == "true"
        val replyPlatform = memory.get("auto_reply_platform") ?: "N/A"
        val replyStart = memory.get("auto_reply_start") ?: "N/A"
        val replyTotal = memory.get("auto_reply_sent_total")?.toIntOrNull() ?: 0
        val replyHistory = memory.getAllByPrefix("auto_reply_sent_")

        return buildString {
            appendLine("📊 Relatório de Auto-Respostas:")
            appendLine("━".repeat(40))
            appendLine("  🤖 Estado: ${if (isActive) "ACTIVO" else "INACTIVO"}")
            appendLine("  📡 Rede: ${if (replyPlatform == "multi") "Todas" else replyPlatform}")
            appendLine("  ⏱️ Iniciado: $replyStart")
            appendLine("  ✅ Respostas enviadas: $replyTotal")
            appendLine()
            if (replyHistory.isNotEmpty()) {
                appendLine("📋 Últimas respostas:")
                replyHistory.entries.take(15).forEach { (key, value) ->
                    appendLine("  • $value")
                }
            }
            appendLine()
            appendLine("Diga 'auto responder desactivar' para parar.")
        }
    }

    // =============================================
    // === NAVEGAÇÃO & EXTRAÇÃO DE DADOS ===
    // =============================================

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
                else -> return false
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.let {
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                context.startActivity(it)
            }
            delay(3000)
            launchIntent != null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir app: ${e.message}")
            false
        }
    }

    /**
     * Navega para a secção de exploração/busca
     */
    private suspend fun navigateToExplore(platform: String, hashtag: String): Boolean {
        delay(2000)
        val root = accessibilityService.rootInActiveWindow ?: return false

        return try {
            when (platform) {
                "Instagram" -> {
                    // Clicar no ícone de busca (lupa)
                    val searchBtn = findNodeByDescContains(root, "Search") 
                        ?: findNodeByDescContains(root, "Search and Explore")
                        ?: findNodeById(root, "com.instagram:id/search_tab")
                    if (searchBtn != null) {
                        searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(2000)
                        // Se tem hashtag, pesquisar
                        if (hashtag.isNotEmpty()) {
                            val searchField = accessibilityService.rootInActiveWindow?.let { r ->
                                findNodeById(r, "com.instagram:id/action_bar_search_edit_text")
                                    ?: findNodeByDescContains(r, "Search")
                            }
                            searchField?.let { field ->
                                val args = android.os.Bundle().apply {
                                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, hashtag)
                                }
                                field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                                delay(2000)
                            }
                        }
                        true
                    } else false
                }
                "Facebook" -> {
                    // Clicar no ícone de busca
                    val searchBtn = findNodeByDescContains(root, "Search")
                        ?: findNodeById(root, "com.facebook.katana:id/search_bar")
                    searchBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(2000)
                    searchBtn != null
                }
                "LinkedIn" -> {
                    val searchBtn = findNodeByDescContains(root, "Search")
                        ?: findNodeById(root, "com.linkedin:id/search_icon")
                    searchBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(2000)
                    searchBtn != null
                }
                "TikTok" -> {
                    val searchBtn = findNodeByDescContains(root, "Search")
                        ?: findNodeById(root, "com.zhiliaoapp.musically:id/aid")
                        ?: findNodeByDescContains(root, "Discover")
                    searchBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(2000)
                    searchBtn != null
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro navigateToExplore: ${e.message}")
            false
        }
    }

    /**
     * Encontra nós de perfil no ecrã actual
     */
    private fun findProfileNodes(root: AccessibilityNodeInfo, platform: String): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName ?: ""

            // Heurísticas para encontrar perfis por plataforma
            val isProfile = when (platform) {
                "Instagram" -> {
                    id.contains("row_user") || id.contains("user_info") ||
                    desc.contains("profile picture") || desc.contains("foto de perfil") ||
                    (text.startsWith("@") && text.length < 30)
                }
                "Facebook" -> {
                    id.contains("user") || desc.contains("profile") ||
                    (id.contains("name") && node.childCount > 0)
                }
                "LinkedIn" -> {
                    id.contains("member") || id.contains("profile") ||
                    desc.contains("profile photo") || desc.contains("foto de perfil")
                }
                "TikTok" -> {
                    id.contains("user") || id.contains("avatar") ||
                    desc.contains("profile") || desc.contains("avatar")
                }
                else -> false
            }

            if (isProfile && node.isClickable) {
                nodes.add(node)
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(root)
        return nodes
    }

    /**
     * Extrai dados de perfil de um nó da UI
     * Clica no nó, lê o perfil, e volta atrás
     */
    private suspend fun extractProfileFromNode(node: AccessibilityNodeInfo, platform: String): Profile? {
        return try {
            // Clicar no perfil para abrir
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(PROFILE_READ_DELAY)

            val profileRoot = accessibilityService.rootInActiveWindow ?: return null

            val username = extractTextFromNode(profileRoot, platform, "username") ?: "desconhecido"
            val displayName = extractTextFromNode(profileRoot, platform, "name") ?: username
            val followers = extractTextFromNode(profileRoot, platform, "followers") ?: "0"
            val following = extractTextFromNode(profileRoot, platform, "following") ?: "0"
            val location = extractTextFromNode(profileRoot, platform, "location") ?: ""
            val bio = extractTextFromNode(profileRoot, platform, "bio") ?: ""
            val postCount = extractTextFromNode(profileRoot, platform, "posts") ?: "0"
            val isVerified = extractTextFromNode(profileRoot, platform, "verified") != null
            val isBusiness = extractTextFromNode(profileRoot, platform, "business") != null

            // Voltar atrás
            val backBtn = findNodeByDescContains(profileRoot, "Back") 
                ?: findNodeByDescContains(profileRoot, "Navigate up")
                ?: findNodeByDescContains(profileRoot, "Voltar")
            backBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1000)

            // Detectar niche da bio
            val niche = detectNiche(bio)

            Profile(
                platform = platform,
                username = username.removePrefix("@").trim(),
                displayName = displayName.trim(),
                followers = followers.replace("[^0-9]".toRegex(), "").trim(),
                following = following.replace("[^0-9]".toRegex(), "").trim(),
                location = location.trim(),
                bio = bio.trim(),
                postCount = postCount.replace("[^0-9]".toRegex(), "").trim(),
                isVerified = isVerified,
                isBusiness = isBusiness,
                niche = niche,
                scrapedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro extractProfile: ${e.message}")
            null
        }
    }

    /**
     * Extrai texto específico de um perfil (username, seguidores, etc.)
     */
    private fun extractTextFromNode(root: AccessibilityNodeInfo, platform: String, fieldType: String): String? {
        var result: String? = null

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || result != null) return
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val id = node.viewIdResourceName ?: ""

            when (fieldType) {
                "username" -> {
                    if ((text.startsWith("@") || id.contains("username") || id.contains("user_name") || id.contains("title"))
                        && text.isNotEmpty() && text.length < 50) {
                        result = text
                    }
                }
                "name" -> {
                    if ((id.contains("name") || id.contains("title") || id.contains("header") || desc.contains("name"))
                        && text.isNotEmpty() && !text.startsWith("@") && text.length < 100) {
                        result = text
                    }
                }
                "followers" -> {
                    val lower = text.lowercase()
                    if ((lower.contains("follower") || lower.contains("seguidor") || id.contains("follower") || id.contains("seguidor"))
                        && text.isNotEmpty()) {
                        result = text
                    }
                }
                "following" -> {
                    val lower = text.lowercase()
                    if ((lower.contains("following") || lower.contains("a seguir") || id.contains("following"))
                        && text.isNotEmpty()) {
                        result = text
                    }
                }
                "posts" -> {
                    val lower = text.lowercase()
                    if ((lower.contains("post") || lower.contains("publicação") || id.contains("post_count") || id.contains("grid"))
                        && text.isNotEmpty()) {
                        result = text
                    }
                }
                "location" -> {
                    if ((desc.contains("location") || desc.contains("location") || id.contains("location") || id.contains("place"))
                        && text.isNotEmpty()) {
                        result = text
                    }
                }
                "bio" -> {
                    if ((id.contains("bio") || id.contains("description") || id.contains("subtitle"))
                        && text.length > 10) {
                        result = text
                    }
                }
                "verified" -> {
                    if (desc.contains("Verified") || desc.contains("verificado") || id.contains("verified") || id.contains("badge")) {
                        result = "verified"
                    }
                }
                "business" -> {
                    if (desc.contains("Business") || desc.contains("Professional") || desc.contains("negócio") || id.contains("business")) {
                        result = "business"
                    }
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(root)
        return result
    }

    /**
     * Verifica se um perfil cumpre os critérios
     */
    private fun matchesCriteria(profile: Profile, criteria: ProspectingCriteria): Boolean {
        // Seguidores mínimos
        val followerCount = parseFollowerCount(profile.followers)
        if (followerCount < criteria.minFollowers) return false

        // Posts mínimos
        val postCount = profile.postCount.toIntOrNull() ?: 0
        if (postCount < criteria.minPosts) return false

        // Verificação
        if (criteria.requireVerified && !profile.isVerified) return false

        // Negócio
        if (criteria.requireBusiness && !profile.isBusiness) return false

        // Frequência de postagem (se exigida, pelo menos 10 posts)
        if (criteria.requireRegularPosting && postCount < 10) return false

        // Localização
        if (criteria.locationKeywords.isNotEmpty()) {
            val profileLocation = profile.location.lowercase() + " " + profile.bio.lowercase()
            val hasLocation = criteria.locationKeywords.any { kw ->
                profileLocation.contains(kw.lowercase())
            }
            if (!hasLocation) return false
        }

        // Niche
        if (criteria.nicheKeywords.isNotEmpty()) {
            val profileText = (profile.bio + " " + profile.displayName).lowercase()
            val hasNiche = criteria.nicheKeywords.any { kw ->
                profileText.contains(kw.lowercase())
            }
            if (!hasNiche) return false
        }

        return true
    }

    /**
     * Detecta niche/área de actuação a partir da bio
     */
    private fun detectNiche(bio: String): String {
        val lower = bio.lowercase()
        return when {
            lower.contains("marketing") || lower.contains("social media") || lower.contains("growth") -> "Marketing"
            lower.contains("design") || lower.contains("gráfico") || lower.contains("branding") || lower.contains("logo") -> "Design"
            lower.contains("programador") || lower.contains("developer") || lower.contains("software") || lower.contains("app") || lower.contains("tech") -> "Tecnologia"
            lower.contains("fotógrafo") || lower.contains("photographer") || lower.contains("foto") -> "Fotografia"
            lower.contains("música") || lower.contains("music") || lower.contains("cantor") || lower.contains("singer") -> "Música"
            lower.contains("restaurante") || lower.contains("food") || lower.contains("comida") || lower.contains("gastronomia") -> "Gastronomia"
            lower.contains("moda") || lower.contains("fashion") || lower.contains("estilista") -> "Moda"
            lower.contains("fitness") || lower.contains("gym") || lower.contains("treino") || lower.contains("saúde") -> "Fitness/Saúde"
            lower.contains("educação") || lower.contains("professor") || lower.contains("ensino") -> "Educação"
            lower.contains("imobiliário") || lower.contains("imobiliaria") || lower.contains("casa") || lower.contains("construção") -> "Imobiliário"
            lower.contains("evento") || lower.contains("event") || lower.contains("festa") || lower.contains("produção") -> "Eventos"
            lower.contains("empreendedor") || lower.contains("entrepreneur") || lower.contains("business") -> "Negócios"
            lower.contains("influencer") || lower.contains("criador de conteúdo") || lower.contains("content creator") -> "Influencer"
            else -> "Outro"
        }
    }

    /**
     * Guarda perfis em CSV
     */
    private fun saveProfilesToCSV(platform: String): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AuraProspecting")
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val fileName = "prospeccao_${platform.lowercase()}_$timestamp.csv"
        val file = File(dir, fileName)

        try {
            FileWriter(file).use { writer ->
                // Cabeçalho
                writer.appendLine("plataforma,username,nome_display,seguidores,seguindo,localização,bio,niche,posts,verificado,negócio,data_raspagem")

                // Dados
                for (p in scrapedProfiles) {
                    writer.appendLine("${p.platform},${p.username},${p.displayName},${p.followers},${p.following},\"${p.location}\",\"${p.bio.replace("\"", "'")}\",${p.niche},${p.postCount},${p.isVerified},${p.isBusiness},${p.scrapedAt}")
                }
            }

            memory.save("last_csv_path", file.absolutePath)
            memory.save("last_csv_profiles", scrapedProfiles.size.toString())

            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao guardar CSV: ${e.message}")
            return "Erro ao guardar CSV: ${e.message}"
        }
    }

    // === HELPERS ===

    private fun detectPlatform(command: String): String {
        return when {
            command.contains("instagram") || command.contains("ig") || command.contains("insta") -> "Instagram"
            command.contains("facebook") || command.contains("fb") -> "Facebook"
            command.contains("tiktok") || command.contains("tt") -> "TikTok"
            command.contains("linkedin") || command.contains("in") -> "LinkedIn"
            command.contains("todas") || command.contains("todas as redes") -> "multi"
            else -> "Instagram"
        }
    }

    private fun extractNumberBefore(command: String, keywords: List<String>): Int? {
        for (kw in keywords) {
            val idx = command.lowercase().indexOf(kw.lowercase())
            if (idx >= 0) {
                val before = command.substring(0, idx).trim()
                val num = Regex("\\d+").find(before)?.value?.toIntOrNull()
                if (num != null && num > 0) return num
            }
        }
        return null
    }

    private fun extractLocation(command: String): List<String> {
        val locations = mutableListOf<String>()
        if (command.contains("angola") || command.contains("angolano") || command.contains("angolana")) {
            locations.addAll(listOf("angola", "luanda", "ao", "angola", "benguela", "huambo", "lobito", "cabinda"))
        }
        if (command.contains("luanda")) locations.add("luanda")
        if (command.contains("benguela")) locations.add("benguela")
        if (command.contains("huambo")) locations.add("huambo")
        if (command.contains("cabinda")) locations.add("cabinda")
        if (command.contains("lobito")) locations.add("lobito")
        if (command.contains("porto")) locations.add("porto")
        if (command.contains("brasil") || command.contains("brazil")) locations.addAll(listOf("brasil", "brazil", "br"))
        if (command.contains("portugal") || command.contains("pt")) locations.addAll(listOf("portugal", "lisboa", "porto"))
        if (locations.isEmpty()) locations.add("angola") // Default Angola
        return locations.distinct()
    }

    private fun extractNiche(command: String): List<String> {
        val niches = mutableListOf<String>()
        val nicheMap = mapOf(
            "marketing" to listOf("marketing", "social media", "digital"),
            "design" to listOf("design", "gráfico", "branding"),
            "tecnologia" to listOf("tecnologia", "tech", "programação", "dev"),
            "fotografia" to listOf("fotografia", "photography"),
            "música" to listOf("música", "music"),
            "moda" to listOf("moda", "fashion"),
            "fitness" to listOf("fitness", "gym", "saúde"),
            "comida" to listOf("gastronomia", "food", "restaurante"),
            "imobiliário" to listOf("imobiliário", "imobiliaria"),
            "eventos" to listOf("eventos", "event"),
            "educação" to listOf("educação", "ensino"),
            "negócios" to listOf("empreendedor", "business", "negócio")
        )
        for ((key, keywords) in nicheMap) {
            if (keywords.any { command.lowercase().contains(it) }) {
                niches.add(key)
            }
        }
        return niches
    }

    private fun extractHashtag(command: String): String {
        val pattern = Regex("#[\\w]+")
        return pattern.find(command)?.value ?: ""
    }

    private fun parseFollowerCount(followers: String): Int {
        val clean = followers.replace("[^0-9.kmK M]".toRegex(), "").trim().lowercase()
        return when {
            clean.endsWith("k") -> (clean.removeSuffix("k").toDoubleOrNull() ?: 0.0).toInt() * 1000
            clean.endsWith("m") -> (clean.removeSuffix("m").toDoubleOrNull() ?: 0.0).toInt() * 1000000
            else -> clean.toIntOrNull() ?: 0
        }
    }

    private fun findNodeById(root: AccessibilityNodeInfo, idPart: String): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || result != null) return
            val id = node.viewIdResourceName ?: ""
            if (id.contains(idPart, ignoreCase = true)) { result = node; return }
            for (i in 0 until node.childCount) traverse(node.getChild(i))
        }
        traverse(root)
        return result
    }

    private fun findNodeByDescContains(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || result != null) return
            val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
            if (contentDesc.contains(desc, ignoreCase = true)) { result = node; return }
            for (i in 0 until node.childCount) traverse(node.getChild(i))
        }
        traverse(root)
        return result
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || result != null) return
            val nodeText = node.text?.toString()?.trim() ?: ""
            if (nodeText.equals(text, ignoreCase = true)) { result = node; return }
            for (i in 0 until node.childCount) traverse(node.getChild(i))
        }
        traverse(root)
        return result
    }

    private fun scrollDown(root: AccessibilityNodeInfo) {
        try {
            var scrollable: AccessibilityNodeInfo? = null
            fun find(node: AccessibilityNodeInfo?) {
                if (node == null || scrollable != null) return
                if (node.isScrollable) { scrollable = node; return }
                for (i in 0 until node.childCount) find(node.getChild(i))
            }
            find(root)
            scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } catch (_: Exception) {}
    }
}
