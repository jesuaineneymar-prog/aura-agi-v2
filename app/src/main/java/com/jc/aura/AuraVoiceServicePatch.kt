package com.jc.aura

/**
 * PATCH — Novas rotas para AuraVoiceService.processCommand()
 *
 * Adicionar ANTES da linha "else -> callOpenRouter(command)" no processCommand:
 *
 * // === CONTROLO PROFUNDO DE APPS (DeepControl) ===
 * isDeepControlCommand(command) -> deepControlModule.handle(command)
 *
 * Adicionar na inicialização dos módulos (onServiceConnected):
 * deepControlModule = AuraDeepControlModule(this, memory, this, this)
 *
 * Declarar o módulo junto aos outros:
 * private lateinit var deepControlModule: AuraDeepControlModule
 *
 * Função isDeepControlCommand:
 * private fun isDeepControlCommand(cmd: String): Boolean {
 *     val triggers = listOf(
 *         "youtube", "spotify", "netflix", "whatsapp", "wpp", "zap",
 *         "telegram", "chrome", "browser", "navega para", "abre o site",
 *         "play store", "instala ", "descarrega a app",
 *         "câmara", "câmera", "tira uma foto", "selfie", "grava um vídeo",
 *         "galeria", "alarme", "timer", "temporizador", "lembra-me",
 *         "calcul", "quanto é", "soma ", "multiplica ", "contacto",
 *         "liga para", "ligar para", "sms", "gmail", "drive",
 *         "google fotos", "amazon", "twitter", "tweet", "reddit",
 *         "wikipedia", "wikipédia", "o que é ", "quem é ",
 *         "github", "shazam", "que música é esta",
 *         "maps", "perto de mim", "rota para", "direções para",
 *         "pesquisa " // pesquisa geral redireccionada
 *     )
 *     return triggers.any { cmd.contains(it, ignoreCase = true) }
 * }
 */
object AuraVoiceServicePatch
