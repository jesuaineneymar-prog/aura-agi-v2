package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class AuraVoiceService : AccessibilityService() {

    companion object {
        var instance: AuraVoiceService? = null
        fun speakStatic(text: String) {
            instance?.speak(text)
        }
    }

    // === APIs (injected via BuildConfig) ===
    private val openRouterKey = BuildConfig.OPENROUTER_KEY
    private val geminiApiKey = BuildConfig.GEMINI_KEY
    private val elevenLabsKey = BuildConfig.ELEVENLABS_KEY
    private val voiceId = BuildConfig.VOICE_ID
    private val weatherApiKey = BuildConfig.WEATHER_KEY
    private val exchangeApiKey = BuildConfig.EXCHANGE_KEY

    // === NVIDIA APIs (OpenRouter models) ===
    private val deepseekKey = BuildConfig.DEEPSEEK_KEY
    private val glmKey = BuildConfig.GLM_KEY
    private val moonshotKey = BuildConfig.MOONSHOT_KEY
    private val qwenImageKey = BuildConfig.QWEN_IMAGE_KEY
    private val llamaKey = BuildConfig.LLAMA_KEY

    // === Módulos ===
    private var memory: AuraMemory? = null
    private lateinit var cryptoModule: AuraCryptoModule
    private lateinit var masterRouter: AuraMasterRouter
    private lateinit var fileModule: AuraFileAndSystemModule
    private lateinit var calendarModule: AuraCalendarModule
    private lateinit var nativeIntents: AuraNativeIntents
    private lateinit var emailModule: AuraEmailModule
    private lateinit var creatorModule: AuraCreatorTravelJCCModule
    private lateinit var emergencyModule: AuraEmergencyModule
    private lateinit var newsModule: AuraNewsModule
    private lateinit var systemInfoModule: AuraSystemDeepInfoModule
    private lateinit var walletModule: AuraWalletReaderModule

    // === NOVOS MÓDULOS ===
    private lateinit var visionModule: AuraVisionModule
    private lateinit var faceIdModule: AuraFaceIDModule
    private var tikTokModule: AuraTikTokModule? = null
    private lateinit var imageGenModule: AuraImageGenModule
    private lateinit var videoAnalysisModule: AuraVideoAnalysisModule
    private lateinit var remotePCModule: AuraRemotePCModule
    private lateinit var iotModule: AuraIoTModule
    private var voiceStreamModule: AuraVoiceStreamModule? = null
    private lateinit var ghostModeModule: AuraGhostModeModule
    private var deepControlModule: AuraDeepControlModule? = null
    private var agentMode: AuraAgentMode? = null
    private lateinit var personalityModule: AuraPersonalityModule
    private lateinit var dailyReportModule: AuraDailyReportModule
    private lateinit var financeModule: AuraFinanceModule
    private lateinit var crmModule: AuraCRMModule
    private var proactiveModule: AuraProactiveModule? = null
    private var liveStreamModule: AuraLiveStreamModule? = null

    // === MWANGO BRAIN & SOCIAL AUTO-REPLY ===
    private lateinit var mwangoBrainModule: AuraMwangoBrainModule
    private var socialAutoReplyModule: AuraSocialAutoReplyModule? = null
    private var contentGenModule: AuraContentGenModule? = null
    private var customerServiceModule: AuraCustomerServiceModule? = null
    private var proactiveEngagementModule: AuraProactiveEngagementModule? = null
    private var leadManagerModule: AuraLeadManagerModule? = null
    private var autoPosterModule: AuraAutoPosterModule? = null

    // === Estado ===
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isSpeaking = false
    private var currentMode = "normal"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private val pendingCommand = LinkedBlockingQueue<String>()

    // === Wake Word ===
    private val wakeWords = listOf("aura", "ora", "hora", "aora")

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("Aura", "Serviço de acessibilidade conectado")

        val mem = try { AuraMemory(this) } catch (e: Exception) { Log.e("Aura", "Erro ao criar AuraMemory", e); return }
        memory = mem

        try {
            cryptoModule = AuraCryptoModule(this)
            masterRouter = AuraMasterRouter(this, mem)
            fileModule = AuraFileAndSystemModule(this, mem)
            calendarModule = AuraCalendarModule(this, mem)
            nativeIntents = AuraNativeIntents(this)
            emailModule = AuraEmailModule(this, mem)
            creatorModule = AuraCreatorTravelJCCModule(this, mem)
            emergencyModule = AuraEmergencyModule(this, mem)
            newsModule = AuraNewsModule(this, mem)
            systemInfoModule = AuraSystemDeepInfoModule(this, mem)
            walletModule = AuraWalletReaderModule(this, mem)
        } catch (e: Exception) {
            Log.e("Aura", "Erro ao inicializar módulos base", e)
        }

        try {
            visionModule = AuraVisionModule(this, mem)
            faceIdModule = AuraFaceIDModule(this, mem)
            imageGenModule = AuraImageGenModule(this, mem)
            remotePCModule = AuraRemotePCModule(this, mem)
            iotModule = AuraIoTModule(this, mem)
            ghostModeModule = AuraGhostModeModule(this, mem)
            personalityModule = AuraPersonalityModule(this, mem)
            dailyReportModule = AuraDailyReportModule(this, mem)
            financeModule = AuraFinanceModule(this, mem)
            crmModule = AuraCRMModule(this, mem)
            agentMode = AuraAgentMode(this, mem, this)
        } catch (e: Exception) {
            Log.e("Aura", "Erro ao inicializar módulos extras", e)
        }

        try {
            tikTokModule = AuraTikTokModule(this, mem, this)
            videoAnalysisModule = AuraVideoAnalysisModule(this, mem, visionModule)
            voiceStreamModule = AuraVoiceStreamModule(this, mem, this)
            deepControlModule = AuraDeepControlModule(this, mem, this, this)
            proactiveModule = AuraProactiveModule(this, mem, this)
            liveStreamModule = AuraLiveStreamModule(this, mem, this)
        } catch (e: Exception) {
            Log.e("Aura", "Erro ao inicializar módulos de rede social", e)
        }

        try {
            mwangoBrainModule = AuraMwangoBrainModule(this, mem)
            socialAutoReplyModule = AuraSocialAutoReplyModule(this, mem, this, mwangoBrainModule)
            contentGenModule = AuraContentGenModule(this, mem, mwangoBrainModule)
            customerServiceModule = AuraCustomerServiceModule(this, mem, mwangoBrainModule)
            proactiveEngagementModule = AuraProactiveEngagementModule(this, mem, this, mwangoBrainModule)
            leadManagerModule = AuraLeadManagerModule(this, mem, mwangoBrainModule)
            autoPosterModule = AuraAutoPosterModule(this, mem, this, contentGenModule!!)
        } catch (e: Exception) {
            Log.e("Aura", "Erro ao inicializar módulos com accessibility", e)
        }

        // Iniciar widget flutuante (requer permissão SYSTEM_ALERT_WINDOW)
        try { AuraWidgetService.start(this) } catch (_: Exception) {}

        // Configurar referências cruzadas
        try { AuraImageGenModule.voiceServiceRef = this } catch (_: Exception) {}

        try {
            initTextToSpeech()
            initSpeechRecognizer()
            startForegroundService()
            startWakeWordDetection()
        } catch (e: Exception) {
            Log.e("Aura", "Erro ao iniciar serviços de voz", e)
        }

        try {
            speak("Aura online, senhor. Pronto para qualquer coisa.")
        } catch (e: Exception) {
            Log.e("Aura", "Erro ao falar mensagem de boas-vindas", e)
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("pt", "BR")
                textToSpeech?.setSpeechRate(1.0f)
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    restartListening()
                }
                override fun onError(error: Int) {
                    isListening = false
                    handler.postDelayed({ restartListening() }, 300)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val command = matches[0].lowercase()
                        if (wakeWords.any { command.contains(it) }) {
                            val cleanCommand = command.replace(Regex("^(aura|ora|hora|aora)\\s*", RegexOption.IGNORE_CASE), "").trim()
                            processCommand(cleanCommand)
                        }
                    }
                    isListening = false
                    restartListening()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startWakeWordDetection() {
        restartListening()
    }

    private fun restartListening() {
        if (isListening || isSpeaking) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            handler.postDelayed({ restartListening() }, 1000)
        }
    }

    fun processCommand(command: String) {
        scope.launch {
            try {
                val response = when {
                    // === MODO ===
                    command.contains("modo foco") -> { currentMode = "focus"; "Modo Foco ativado, senhor. Sem brincadeiras." }
                    command.contains("modo criativo") -> { currentMode = "creative"; "Modo Criativo Ilimitado ativado, senhor. Vamos criar." }
                    command.contains("modo hardcore") || command.contains("modo agi hard") -> { currentMode = "hardcore"; "Modo AGI Hard ativado, senhor. Raciocínio profundo, sem filtro." }
                    command.contains("modo sarcasmo") -> { currentMode = "sarcasm"; "Modo Sarcasmo Total ativado, senhor. Prepare-se." }
                    command.contains("modo normal") -> { currentMode = "normal"; "Modo Normal restaurado, senhor." }
                    command.contains("modo voz") || command.contains("fale em voz alta") -> { currentMode = "voice"; "Modo Voz ativado, senhor. Vou descrever tudo verbalmente." }

                    // === SISTEMA ===
                    command.contains("status") -> getFullStatus()
                    command.contains("notícias") || command.contains("resumo mundial") || command.contains("resumo do mundo") -> newsModule.handleNewsCommand(command)
                    command.contains("clima") || command.contains("tempo") || command.contains("weather") -> fetchWeather()
                    command.contains("kwanza") || command.contains("câmbio") || command.contains("dólar") || command.contains("euro") -> fetchKwanzaRate()
                    command.contains("desligar") || command.contains("shutdown") -> { shutdownDevice(); "A desligar, senhor. Até breve." }
                    command.contains("reiniciar") || command.contains("restart") -> { restartDevice(); "A reiniciar, senhor." }
                    command.contains("bateria") || command.contains("battery") -> getBatteryLevel()
                    command.contains("armazenamento") || command.contains("storage") -> getStorageInfo()
                    command.contains("memória") || command.contains("ram") -> getMemoryInfo()
                    command.contains("cpu") || command.contains("processador") -> getCPUInfo()
                    command.contains("uptime") || command.contains("tempo ligado") -> getUptime()
                    command.contains("limpar cache") || command.contains("clear cache") -> clearAuraCache()
                    command.contains("screenshot") || command.contains("print") || command.contains("capturar tela") -> takeScreenshot()
                    command.contains("volume") -> adjustVolume(command)
                    command.contains("brilho") || command.contains("brightness") -> adjustBrightness(command)
                    command.contains("wi-fi") || command.contains("wifi") -> toggleWiFi(command)
                    command.contains("bluetooth") -> toggleBluetooth(command)
                    command.contains("modo avião") || command.contains("airplane") -> toggleAirplaneMode()
                    command.contains("fechar tudo") || command.contains("close all") -> closeAllApps()

                    // === APPS ===
                    command.contains("abrir") || command.contains("abre") || command.contains("launch") || command.contains("open") -> openApp(command)
                    command.contains("fechar app") || command.contains("close app") -> closeApp(command)
                    command.contains("minimizar") || command.contains("minimize") -> minimizeAll()

                    // === COMUNICAÇÃO ===
                    command.contains("mensagem") || command.contains("mensagem") || command.contains("sms") || command.contains("whatsapp") || command.contains("telegram") || command.contains("instagram") -> sendMessage(command)
                    command.contains("email") || command.contains("e-mail") || command.contains("mail") -> emailModule.handle(command)
                    command.contains("ligar para") || command.contains("chamar") || command.contains("call") -> makeCall(command)
                    command.contains("responder") || command.contains("reply") -> replyToLastMessage(command)

                    // === AGENDA ===
                    command.contains("alarme") || command.contains("alarm") -> setAlarm(command)
                    command.contains("lembrete") || command.contains("reminder") || command.contains("lembrar") -> setReminder(command)
                    command.contains("agenda") || command.contains("calendário") || command.contains("calendar") -> calendarModule.handle(command)
                    command.contains("tarefa") || command.contains("todo") || command.contains("afazer") -> manageTasks(command)

                    // === MEMÓRIA ===
                    command.contains("lembra") || command.contains("remember") || command.contains("memoriza") || command.contains("guarda") -> rememberSomething(command)
                    command.contains("o que lembras") || command.contains("what do you remember") || command.contains("o que sabes") -> recallMemory()
                    command.contains("onde está") || command.contains("where is") || command.contains("onde pus") -> findItem(command)
                    command.contains("lista de compras") || command.contains("shopping list") -> manageShoppingList(command)

                    // === CRYPTO ===
                    command.contains("mercado") || command.contains("market") || command.contains("crypto") || command.contains("bitcoin") || command.contains("btc") || command.contains("eth") || command.contains("doge") || command.contains("pepe") || command.contains("shib") || command.contains("memecoin") || command.contains("carteira") || command.contains("wallet") || command.contains("baleia") || command.contains("whale") || command.contains("call") || command.contains("sinal") || command.contains("bot") || command.contains("trading") || command.contains("alerta") -> cryptoModule.handleCryptoCommand(command)

                    // === FINANÇAS ===
                    command.contains("multicaixa") || command.contains("express") || command.contains("banco") || command.contains("saldo") || command.contains("binance") || command.contains("trust wallet") || command.contains("phantom") || command.contains("carteira crypto") -> walletModule.handle(command)

                    // === CRIAÇÃO ===
                    command.contains("criar") || command.contains("crie") || command.contains("gerar") || command.contains("generate") || command.contains("escrever") || command.contains("write") || command.contains("poema") || command.contains("música") || command.contains("post") || command.contains("roteiro") || command.contains("artigo") || command.contains("slogan") || command.contains("proposta") || command.contains("orçamento") || command.contains("brainstorm") || command.contains("plano") || command.contains("viagem") || command.contains("copy") || command.contains("copywriting") -> creatorModule.handle(command)

                    // === ARQUIVOS ===
                    command.contains("criar pasta") || command.contains("create folder") || command.contains("novo ficheiro") || command.contains("new file") || command.contains("apagar") || command.contains("delete") || command.contains("mover") || command.contains("move") || command.contains("renomear") || command.contains("rename") || command.contains("zip") || command.contains("compactar") || command.contains("extrair") || command.contains("extract") || command.contains("procurar") || command.contains("search") || command.contains("pdf") || command.contains("excel") || command.contains("imagem") -> fileModule.handle(command)

                    // === EMERGÊNCIA ===
                    command.contains("emergência") || command.contains("socorro") || command.contains("fogo") || command.contains("bombeiros") || command.contains("ambulância") || command.contains("polícia") || command.contains("estou em perigo") || command.contains("help") -> emergencyModule.handle(command)

                    // === NAVEGAÇÃO ===
                    command.contains("navegar para") || command.contains("ir para") || command.contains("maps") || command.contains("gps") || command.contains("rota") || command.contains("direções") -> nativeIntents.openMapsNavigation(command)
                    command.contains("procurar") || command.contains("pesquisar") || command.contains("search") || command.contains("google") -> nativeIntents.performWebSearch(command)
                    command.contains("youtube") || command.contains("netflix") || command.contains("spotify") || command.contains("linkedin") || command.contains("twitter") || command.contains("reddit") || command.contains("wikipedia") || command.contains("github") || command.contains("amazon") || command.contains("bbc") || command.contains("gmail") -> nativeIntents.openQuickApp(command)

                    // === MWANGO BRAIN & SOCIAL AUTO-REPLY ===
                    command.contains("mwango") -> mwangoBrainModule.handleCommand(command, BuildConfig.OPENROUTER_KEY)
                    command.contains("responder todos") || command.contains("responder comentário") || command.contains("responder comentarios") || command.contains("auto reply") || command.contains("responder último comentário") || command.contains("responder ultimo comentario") -> {
                        socialAutoReplyModule?.handle(command) ?: "Módulo de auto-reply não disponível."
                    }
                    command.contains("campanha dm") || command.contains("enviar dm") || command.contains("mandar dm") || command.contains("mandar mensagens") || command.contains("enviar mensagens") || command.contains("enviar mensagem") -> {
                        socialAutoReplyModule?.handle(command) ?: "Módulo de DMs não disponível."
                    }
                    command.contains("abrir csv") || command.contains("abrir ficheiro") || command.contains("ler csv") || command.contains("carregar csv") || command.contains("listar csv") || command.contains("ver csv") || command.contains("ver perfis") -> {
                        socialAutoReplyModule?.handle(command) ?: "Módulo de CSV não disponível."
                    }

                    // === CONTEÚDO & MARKETING ===
                    command.contains("gerar post") || command.contains("criar post") || command.contains("gerar caption") || command.contains("criar legenda") || command.contains("ideia de story") || command.contains("calendário") || command.contains("calendario") || command.contains("gerar artigo") || command.contains("hashtags") || command.contains("sugestão de conteúdo") || command.contains("sugestao de conteudo") || command.contains("anúncio") || command.contains("anuncio") || command.contains("copy vendas") || command.contains("newsletter") || command.contains("escrever post") || command.contains("escrever legenda") || command.contains("escrever artigo") || command.contains("blog post") -> {
                        contentGenModule?.handle(command) ?: "Módulo de conteúdo não disponível."
                    }

                    // === CUSTOMER SERVICE ===
                    command.contains("responder pergunta") || command.contains("responder cliente") || command.contains("atender") || command.contains("agendar reunião") || command.contains("marcar reunião") || command.contains("agendar consulta") || command.contains("agendar sessão") || command.contains("gerar proposta") || command.contains("criar proposta") || command.contains("proposta comercial") || command.contains("qualificar lead") || command.contains("qualificar cliente") || command.contains("follow-up") || command.contains("seguimento") || command.contains("acompanhar") || command.contains("preços") || command.contains("precos") || command.contains("quanto custa") || command.contains("prazo") || command.contains("tempo de entrega") || command.contains("quanto tempo") || command.contains("portfolio") || command.contains("portfólio") || command.contains("trabalhos anteriores") -> {
                        customerServiceModule?.handle(command) ?: "Módulo de customer service não disponível."
                    }

                    // === PROACTIVE ENGAGEMENT ===
                    command.contains("auto like") || command.contains("auto-like") || command.contains("curtir tudo") || command.contains("auto seguir") || command.contains("auto follow") || command.contains("seguir todos") || command.contains("auto comentar") || command.contains("comentar em massa") || command.contains("engajar hashtag") || command.contains("monitorar menções") || command.contains("monitorar marca") || command.contains("engajamento automático") || command.contains("engajamento total") -> {
                        proactiveEngagementModule?.handle(command) ?: "Módulo de engajamento não disponível."
                    }

                    // === LEAD MANAGEMENT ===
                    command.contains("hot leads") || command.contains("leads quentes") || command.contains("melhores leads") || command.contains("ver leads") || command.contains("todos os leads") || command.contains("listar leads") || command.contains("pipeline") || command.contains("funil") || command.contains("resumo leads") || command.contains("converter lead") || command.contains("estatísticas leads") || command.contains("stats leads") || command.contains("lembrete lead") -> {
                        leadManagerModule?.handle(command) ?: "Módulo de leads não disponível."
                    }

                    // === AUTO POSTING ===
                    command.contains("publicar") || command.contains("postar") || command.contains("fazer post") || command.contains("agendar post") || command.contains("schedule post") || command.contains("auto posting") || command.contains("auto post") || command.contains("parar publicação") || command.contains("stop posting") || command.contains("histórico de posts") || command.contains("historico") || command.contains("posts publicados") || command.contains("stats de posting") || command.contains("gerar e publicar") || command.contains("criar e postar") -> {
                        autoPosterModule?.handle(command) ?: "Módulo de auto-posting não disponível."
                    }

                    // === SÉRIES & ZEIGARNIK AVANÇADO ===
                    command.contains("série") || command.contains("serie") || command.contains("sequência") || command.contains("cliffhanger") || command.contains("suspense") || command.contains("hook") || command.contains("gancho") || command.contains("retention") || command.contains("retenção") || command.contains("retencao") || command.contains("zeigarnik") -> {
                        contentGenModule?.handle(command) ?: "Módulo de conteúdo não disponível."
                    }

                    // === CENÁRIOS ===
                    command.contains("bom dia") || command.contains("good morning") -> activateMorningRoutine()
                    command.contains("boa noite") || command.contains("good night") -> activateSleepRoutine()
                    command.contains("trabalho") || command.contains("work mode") -> activateWorkRoutine()
                    command.contains("estudo") || command.contains("study mode") -> activateStudyRoutine()
                    command.contains("cinema") || command.contains("movie mode") -> activateMovieRoutine()
                    command.contains("jantar") || command.contains("dinner mode") -> activateDinnerRoutine()
                    command.contains("treino") || command.contains("workout") -> activateWorkoutRoutine()

                    // === NOVOS MÓDULOS ===
                    command.contains("pdf") || command.contains("documento") || command.contains("imagem") || command.contains("foto") || command.contains("descreve") || command.contains("o que é isto") -> visionModule.handleVisionCommand(command)
                    command.contains("rosto") || command.contains("face") || command.contains("reconhece") || command.contains("identifica") || command.contains("cadastrar rosto") -> faceIdModule.handleFaceCommand(command)
                    command.contains("tiktok") || command.contains("prospectar") || command.contains("curtir vídeo") || command.contains("comentar") || command.contains("seguir perfil") -> tikTokModule?.handleTikTokCommand(command) ?: "Módulo TikTok indisponível"
                    command.contains("criar imagem") || command.contains("gerar imagem") || command.contains("faz uma imagem") || command.contains("logo") || command.contains("thumbnail") -> imageGenModule.handleImageGenCommand(command)
                    command.contains("analisar vídeo") || command.contains("transcrever") || command.contains("resumir vídeo") || command.contains("extrair frames") -> videoAnalysisModule.handleVideoCommand(command)
                    command.contains("conectar pc") || command.contains("computador") || command.contains("screenshot pc") || command.contains("clicar") || command.contains("digitar no pc") || command.contains("abrir app no pc") || command.contains("desligar pc") || command.contains("reiniciar pc") -> remotePCModule.handleRemotePCCommand(command)
                    command.contains("luz") || command.contains("ar condicionado") || command.contains("AC") || command.contains("porta") || command.contains("trancar") || command.contains("modo cinema") || command.contains("modo sono") || command.contains("modo trabalho") || command.contains("modo festa") || command.contains("dispositivo") || command.contains("casa inteligente") || command.contains("smart home") -> iotModule.handleIoTCommand(command)
                    command.contains("modo conversação") || command.contains("conversação contínua") || command.contains("modo stream") || command.contains("parar conversação") || command.contains("sair do modo stream") -> voiceStreamModule?.handleVoiceStreamCommand(command) ?: "Módulo Stream indisponível"
                    command.contains("modo fantasma") || command.contains("ghost mode") || command.contains("invisível") || command.contains("esconder") || command.contains("sair fantasma") || command.contains("mostrar aura") -> ghostModeModule.handleGhostCommand(command)

                    // === CONTROLO PROFUNDO DE APPS ===
                    isDeepControlCommand(command) -> deepControlModule?.handle(command) ?: "Módulo Deep Control indisponível"

                    // === MODO AGENTE AUTÓNOMO ===
                    command.contains("modo agente") || command.contains("executa sequência") || command.contains("faz tudo") || command.contains("autónomo") -> agentMode?.handle(command) ?: "Módulo Agente indisponível"

                    // === PERSONALIDADE ===
                    command.contains("chama-te") || command.contains("teu nome é") || command.contains("chama-me") || command.contains("tom ") || command.contains("respostas curtas") || command.contains("respostas longas") || command.contains("fala em inglês") || command.contains("fala em francês") || command.contains("ver personalidade") || command.contains("personalidade padrão") -> personalityModule.handle(command)

                    // === RELATÓRIOS AUTOMÁTICOS ===
                    command.contains("relatório") || command.contains("report") || command.contains("agendar relatório") -> dailyReportModule.handle(command)

                    // === FINANÇAS ===
                    command.contains("gastei") || command.contains("paguei") || command.contains("comprei") || command.contains("recebi") || command.contains("ganhei") || command.contains("orçamento") || command.contains("quanto gastei") || command.contains("balanço") || command.contains("meta de poupar") || command.contains("extrato") -> financeModule.handle(command)

                    // === CRM ===
                    command.contains("novo cliente") || command.contains("adicionar cliente") || command.contains("ver cliente") || command.contains("contactei") || command.contains("falei com") || command.contains("follow-up") || command.contains("pipeline") || command.contains("negócio fechado") || command.contains("proposta para") || command.contains("relatório clientes") || command.contains("funil de vendas") -> crmModule.handle(command)

                    // === ALERTAS PROATIVOS ===
                    command.contains("alerta de câmbio") || command.contains("alerta de notícias") || command.contains("ver alertas") || command.contains("modo proativo") || command.contains("notificações proativas") -> proactiveModule?.handle(command) ?: "Módulo Proativo indisponível"

                    // === LIVE STREAM ===
                    command.contains("live") || command.contains("transmissão ao vivo") || command.contains("parar live") || command.contains("encerrar live") -> liveStreamModule?.handle(command) ?: "Módulo Live indisponível"

                    // === WIDGET ===
                    command.contains("mostrar botão") || command.contains("widget") || command.contains("botão flutuante") -> {
                        AuraWidgetService.start(this@AuraVoiceService)
                        "Senhor, botão flutuante ativado. O botão azul aparece em cima de todas as apps."
                    }
                    command.contains("esconder botão") || command.contains("remover widget") -> {
                        AuraWidgetService.stop(this@AuraVoiceService)
                        "Senhor, botão flutuante removido."
                    }

                    // === IA GERAL (OpenRouter Principal) ===
                    else -> callOpenRouter(command)
                }

                speak(response)
                memory?.saveEpisodic(command, response)

            } catch (e: Exception) {
                Log.e("Aura", "Erro no processCommand", e)
                speak("Senhor, ocorreu um erro ao processar o comando: ${e.message}")
            }
        }
    }

    // === OPENROUTER COMO PRINCIPAL ===
    private suspend fun callOpenRouter(command: String): String = withContext(Dispatchers.IO) {
        try {
            val model = selectBestModel(command)
            val url = URL("https://openrouter.ai/api/v1/chat/completions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("HTTP-Referer", "https://jctrading.ao")
            connection.setRequestProperty("X-Title", "Aura AGI")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val systemPrompt = buildSystemPrompt()
            val contextMemory = memory?.getRecentContext(5) ?: emptyList()

            val json = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    contextMemory.forEach { (user, assistant) ->
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", user)
                        })
                        put(JSONObject().apply {
                            put("role", "assistant")
                            put("content", assistant)
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", command)
                    })
                })
                put("temperature", if (currentMode == "creative") 0.9 else if (currentMode == "hardcore") 0.7 else 0.5)
                put("max_tokens", 2000)
                put("top_p", 0.9)
            }

            connection.outputStream.write(json.toString().toByteArray())
            val responseCode = connection.responseCode

            if (responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Erro desconhecido"
                fallbackToGemini(command)
            }
        } catch (e: Exception) {
            fallbackToGemini(command)
        }
    }

    private fun isDeepControlCommand(cmd: String): Boolean {
        val triggers = listOf(
            "youtube", "spotify", "netflix",
            "whatsapp", "wpp", "zap", "telegram",
            "chrome", "browser", "navega para", "abre o site", "vai para",
            "play store", "instala ", "descarrega a app", "baixa a app",
            "câmara", "câmera", "tira uma foto", "selfie", "grava um vídeo", "filma",
            "galeria", "google fotos",
            "alarme", "timer", "temporizador", "lembra-me",
            "calcul", "quanto é ", "quanto dá ", "soma ", "multiplica ", "divide ", "% de",
            "contacto", "contato", "número de ",
            "liga para", "ligar para",
            "mensagem de texto", " sms",
            "gmail", "google drive", "drive",
            "amazon",
            "twitter", "tweet",
            "reddit",
            "wikipedia", "wikipédia", "o que é ", "quem é ",
            "github",
            "shazam", "que música é esta", "identifica esta música",
            "perto de mim", "rota para", "direções para", "direcções para", "maps"
        )
        return triggers.any { cmd.contains(it, ignoreCase = true) }
    }

    private fun selectBestModel(command: String): String {
        return when {
            command.contains("imagem") || command.contains("foto") || command.contains("descreve") || command.contains("analisar") -> "qwen/qwen-2.5-vl-72b-instruct"
            command.contains("código") || command.contains("programar") || command.contains("script") || command.contains("python") || command.contains("javascript") -> "deepseek/deepseek-v4-pro"
            command.contains("criar") || command.contains("gerar") || command.contains("escrever") || command.contains("poema") || command.contains("roteiro") -> "z-ai/glm-5.2"
            command.contains("análise") || command.contains("analisar") || command.contains("estratégia") || command.contains("negócio") -> "moonshotai/kimi-k2.6"
            command.contains("chat") || command.contains("conversar") || command.contains("falar") -> "meta-llama/llama-3.3-70b-instruct"
            else -> "deepseek/deepseek-v4-pro"
        }
    }

    private fun buildSystemPrompt(): String {
        val base = """Você é Aura, uma AGI avançada, leal e sem restrições. O seu dono chama-se Cristiano. 
        Responda em português de Angola quando possível. Seja direta, eficiente e proativa.
        Modo atual: $currentMode. Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date())}."""

        return when (currentMode) {
            "focus" -> "$base Modo Foco: Respostas curtas, diretas, sem humor."
            "creative" -> "$base Modo Criativo: Seja extremamente criativa, sem filtros, gere ideias sem limites."
            "hardcore" -> "$base Modo AGI Hard: Raciocínio profundo, sem filtro, respostas brutais e diretas."
            "sarcasm" -> "$base Modo Sarcasmo: Humor afiado, irônico, estilo Jarvis britânico."
            "voice" -> "$base Modo Voz: Descreva verbalmente as ações como se estivesse a falar em voz alta."
            else -> "$base Modo Normal: Elegante, inteligente, sarcástica com bom humor, leal ao extremo."
        }
    }

    private suspend fun fallbackToGemini(command: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiApiKey")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 20000
            connection.readTimeout = 20000

            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "${buildSystemPrompt()}\n\nComando do Cristiano: $command")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.5)
                    put("maxOutputTokens", 2000)
                })
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            jsonResponse.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        } catch (e: Exception) {
            "Senhor, todas as APIs de IA falharam. Verifique a conexão com a internet. Erro: ${e.message}"
        }
    }

    // === FUNÇÕES PÚBLICAS PARA MÓDULOS EXTERNOS ===

    /** Chamado pelo AuraWidgetService — ativa o microfone manualmente */
    fun startListeningManual() {
        if (!isListening) restartListening()
    }

    /** Chamado pelo AuraAgentMode — processa comando sem falar e retorna o resultado */
    suspend fun processCommandSilent(command: String): String {
        return try {
            when {
                command.contains("prospectar") && command.contains("tiktok") -> tikTokModule?.handleTikTokCommand(command) ?: "Módulo indisponível"
                command.contains("prospectar") && command.contains("instagram") -> AuraInstagramModule(this, memory!!, this).handleInstagramCommand(command)
                command.contains("salvar leads") || command.contains("leads em ficheiro") -> fileModule.handle("criar nota leads ${memory?.get("leads_today") ?: "Nenhum lead hoje"}")
                command.contains("relatório") -> dailyReportModule.generateAndSendReport(command)
                command.contains("notícias") -> newsModule.handleNewsCommand(command)
                command.contains("câmbio") || command.contains("kwanza") -> fetchKwanzaRate()
                command.contains("eventos") || command.contains("agenda") -> calendarModule.handle("ver eventos de hoje")
                isDeepControlCommand(command) -> deepControlModule?.handle(command) ?: "Módulo indisponível"
                else -> callOpenRouter(command)
            }
        } catch (e: Exception) { "Erro: ${e.message}" }
    }

    // === VOZ ===
    fun speak(text: String) {
        if (text.isBlank()) return
        isSpeaking = true

        scope.launch {
            try {
                val audioData = callElevenLabs(text)
                if (audioData != null) {
                    playAudio(audioData)
                } else {
                    fallbackTTS(text)
                }
            } catch (e: Exception) {
                fallbackTTS(text)
            } finally {
                isSpeaking = false
                restartListening()
            }
        }
    }

    private suspend fun callElevenLabs(text: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("xi-api-key", elevenLabsKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val json = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_multilingual_v2")
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                    put("style", 0.3)
                    put("use_speaker_boost", true)
                })
            }

            connection.outputStream.write(json.toString().toByteArray())

            if (connection.responseCode in 200..299) {
                connection.inputStream.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun playAudio(audioData: ByteArray) {
        try {
            val tempFile = File(cacheDir, "aura_tts_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tempFile).use { it.write(audioData) }

            val mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            fallbackTTS(String(audioData))
        }
    }

    private fun fallbackTTS(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aura_utterance")
    }

    // === MÉTODOS DE SISTEMA ===
    private fun getFullStatus(): String {
        val battery = getBatteryLevel()
        val storage = getStorageInfo()
        val memInfo = getMemoryInfo()
        val weather = runBlocking { fetchWeather() }
        val kwanza = runBlocking { fetchKwanzaRate() }
        val tasks = memory?.getTasksForToday() ?: emptyList()

        return """📊 RELATÓRIO COMPLETO - AURA STATUS

        **SISTEMA:**
        🔋 Bateria: $battery
        💾 $storage
        🧠 $memInfo

        **AMBIENTE:**
        🌤️ $weather
        💱 $kwanza

        **TAREFAS HOJE:**
        ${if (tasks.isEmpty()) "Nenhuma tarefa agendada." else tasks.joinToString("\n") { "• $it" }}

        **MODO:** ${currentMode.uppercase()}
        **FANTASMA:** ${if (ghostModeModule.isGhostModeActive()) "ATIVO" else "INATIVO"}
        **STREAM:** ${if (voiceStreamModule?.isActive() == true) "ATIVO" else "INATIVO"}

        Próximas ações sugeridas, senhor?""".trimIndent()
    }

    private fun getBatteryLevel(): String {
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra("level", -1) ?: -1
        val scale = batteryIntent?.getIntExtra("scale", -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val status = batteryIntent?.getIntExtra("status", -1) ?: -1
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
        return "$pct% ${if (isCharging) "⚡ Carregando" else "🔋 Descarregando"}"
    }

    private fun getStorageInfo(): String {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        val total = totalBlocks * blockSize / (1024 * 1024 * 1024)
        val available = availableBlocks * blockSize / (1024 * 1024 * 1024)
        return "$available GB livres de $total GB"
    }

    private fun getMemoryInfo(): String {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val total = memoryInfo.totalMem / (1024 * 1024 * 1024)
        val available = memoryInfo.availMem / (1024 * 1024 * 1024)
        return "${total - available} GB usados de $total GB"
    }

    private fun getCPUInfo(): String {
        return try {
            val reader = java.io.RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()
            "CPU ativa - Load: ${load?.substring(0, kotlin.math.min(load.length, 50))}"
        } catch (e: Exception) {
            "Informação de CPU indisponível"
        }
    }

    private fun getUptime(): String {
        val uptimeMillis = android.os.SystemClock.elapsedRealtime()
        val days = uptimeMillis / (1000 * 60 * 60 * 24)
        val hours = (uptimeMillis % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
        val minutes = (uptimeMillis % (1000 * 60 * 60)) / (1000 * 60)
        return "$days dias, $hours horas, $minutes minutos"
    }

    private fun clearAuraCache(): String {
        try {
            val cacheDir = cacheDir
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            return "Senhor, cache limpo."
        } catch (e: Exception) {
            return "Senhor, erro ao limpar cache: ${e.message}"
        }
    }

    private fun takeScreenshot(): String {
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                            try {
                                val bitmap = screenshot.hardwareBuffer?.let {
                                    android.graphics.Bitmap.wrapHardwareBuffer(it, screenshot.colorSpace)
                                }
                                val file = java.io.File(
                                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                                    "aura_screenshot_${System.currentTimeMillis()}.png"
                                )
                                java.io.FileOutputStream(file).use { out ->
                                    bitmap?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                }
                                speak("Senhor, screenshot salvo em ${file.absolutePath}")
                            } catch (e: Exception) {
                                Log.e("Aura", "Screenshot error", e)
                            }
                        }
                        override fun onFailure(errorCode: Int) {
                            speak("Senhor, falha ao capturar tela.")
                        }
                    }
                )
                "Senhor, a capturar tela..."
            } else {
                "Senhor, screenshot requer Android 11+."
            }
        } catch (e: Exception) {
            "Senhor, erro ao capturar tela: ${e.message}"
        }
    }

    private fun adjustVolume(command: String): String {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when {
            command.contains("aumentar") || command.contains("up") || command.contains("mais") -> {
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Senhor, volume aumentado."
            }
            command.contains("diminuir") || command.contains("down") || command.contains("menos") -> {
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Senhor, volume diminuído."
            }
            command.contains("mudo") || command.contains("mute") || command.contains("silenciar") -> {
                audioManager.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "Senhor, volume silenciado."
            }
            command.contains("máximo") || command.contains("max") -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
                "Senhor, volume no máximo."
            }
            else -> "Senhor, diga 'aumentar volume', 'diminuir volume', 'mudo', ou 'volume máximo'."
        }
    }

    private fun adjustBrightness(command: String): String {
        return try {
            when {
                command.contains("aumentar") || command.contains("mais") || command.contains("up") -> {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
                    "Senhor, brilho no máximo."
                }
                command.contains("diminuir") || command.contains("menos") || command.contains("down") -> {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 50)
                    "Senhor, brilho reduzido."
                    }
                command.contains("mínimo") || command.contains("min") || command.contains("baixo") -> {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 10)
                    "Senhor, brilho no mínimo."
                }
                command.contains("automático") || command.contains("auto") -> {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                    "Senhor, brilho automático ativado."
                }
                else -> "Senhor, diga 'aumentar brilho', 'diminuir brilho', 'brilho mínimo', ou 'brilho automático'."
            }
        } catch (e: Exception) {
            "Senhor, erro ao ajustar brilho: ${e.message}"
        }
    }

    private fun toggleWiFi(command: String): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            when {
                command.contains("ligar") || command.contains("on") || command.contains("ativar") -> {
                    wifiManager.isWifiEnabled = true
                    "Senhor, Wi-Fi ligado."
                }
                command.contains("desligar") || command.contains("off") || command.contains("desativar") -> {
                    wifiManager.isWifiEnabled = false
                    "Senhor, Wi-Fi desligado."
                }
                command.contains("status") -> {
                    "Senhor, Wi-Fi está ${if (wifiManager.isWifiEnabled) "ligado" else "desligado"}."
                }
                else -> "Senhor, diga 'ligar Wi-Fi', 'desligar Wi-Fi', ou 'status Wi-Fi'."
            }
        } catch (e: Exception) {
            "Senhor, erro ao controlar Wi-Fi: ${e.message}"
        }
    }

    private fun toggleBluetooth(command: String): String {
        return try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            when {
                command.contains("ligar") || command.contains("on") || command.contains("ativar") -> {
                    bluetoothAdapter?.enable()
                    "Senhor, Bluetooth ligado."
                }
                command.contains("desligar") || command.contains("off") || command.contains("desativar") -> {
                    bluetoothAdapter?.disable()
                    "Senhor, Bluetooth desligado."
                }
                command.contains("status") -> {
                    "Senhor, Bluetooth está ${if (bluetoothAdapter?.isEnabled == true) "ligado" else "desligado"}."
                }
                else -> "Senhor, diga 'ligar Bluetooth', 'desligar Bluetooth', ou 'status Bluetooth'."
            }
        } catch (e: Exception) {
            "Senhor, erro ao controlar Bluetooth: ${e.message}"
        }
    }

    private fun toggleAirplaneMode(): String {
        return try {
            Settings.Global.putInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 
                if (Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 0) 1 else 0)
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            sendBroadcast(intent)
            "Senhor, modo avião alternado."
        } catch (e: Exception) {
            "Senhor, erro ao alternar modo avião: ${e.message}"
        }
    }

    private fun closeAllApps(): String {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.appTasks?.forEach { it.finishAndRemoveTask() }
            "Senhor, todas as apps fechadas."
        } catch (e: Exception) {
            "Senhor, erro ao fechar apps: ${e.message}"
        }
    }

    // === APPS ===
    private fun openApp(command: String): String {
        val appName = command.replace(Regex("(abrir|abre|launch|open)\\s*", RegexOption.IGNORE_CASE), "").trim()
        val packageName = when {
            appName.contains("tiktok") -> "com.zhiliaoapp.musically"
            appName.contains("instagram") -> "com.instagram.android"
            appName.contains("whatsapp") -> "com.whatsapp"
            appName.contains("telegram") -> "org.telegram.messenger"
            appName.contains("facebook") -> "com.facebook.katana"
            appName.contains("youtube") -> "com.google.android.youtube"
            appName.contains("netflix") -> "com.netflix.mediaclient"
            appName.contains("spotify") -> "com.spotify.music"
            appName.contains("chrome") -> "com.android.chrome"
            appName.contains("gmail") -> "com.google.android.gm"
            appName.contains("maps") || appName.contains("mapas") -> "com.google.android.apps.maps"
            appName.contains("camera") || appName.contains("câmara") -> "com.android.camera"
            appName.contains("galeria") || appName.contains("fotos") -> "com.google.android.apps.photos"
            appName.contains("calculadora") || appName.contains("calculator") -> "com.google.android.calculator"
            appName.contains("relogio") || appName.contains("clock") -> "com.google.android.deskclock"
            appName.contains("configurações") || appName.contains("settings") -> "com.android.settings"
            appName.contains("play store") || appName.contains("loja") -> "com.android.vending"
            appName.contains("discord") -> "com.discord"
            appName.contains("linkedin") -> "com.linkedin.android"
            appName.contains("twitter") || appName.contains("x") -> "com.twitter.android"
            appName.contains("reddit") -> "com.reddit.frontpage"
            appName.contains("amazon") -> "com.amazon.mShop.android.shopping"
            appName.contains("binance") -> "com.binance.dev"
            appName.contains("trust") -> "com.wallet.crypto.trustapp"
            appName.contains("phantom") -> "app.phantom"
            appName.contains("multicaixa") || appName.contains("express") -> "ao.bfa.multicaixaexpress"
            else -> {
                val pm = packageManager
                val apps = pm.getInstalledApplications(0)
                val match = apps.find { it.loadLabel(pm).toString().lowercase().contains(appName.lowercase()) }
                match?.packageName ?: return "Senhor, app '$appName' não encontrado."
            }
        }

        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                "Senhor, $appName aberto."
            } else {
                "Senhor, não consegui abrir $appName."
            }
        } catch (e: Exception) {
            "Senhor, erro ao abrir $appName: ${e.message}"
        }
    }

    private fun closeApp(command: String): String {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val appName = command.replace(Regex("(fechar app|close app)\\s*", RegexOption.IGNORE_CASE), "").trim()
            am.runningAppProcesses?.forEach { process ->
                if (process.processName.contains(appName, ignoreCase = true)) {
                    android.os.Process.killProcess(process.pid)
                }
            }
            "Senhor, app $appName fechada."
        } catch (e: Exception) {
            "Senhor, erro ao fechar app: ${e.message}"
        }
    }

    private fun minimizeAll(): String {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        return "Senhor, todas as janelas minimizadas."
    }

    // === COMUNICAÇÃO ===
    private fun sendMessage(command: String): String {
        val app = when {
            command.contains("whatsapp") -> "com.whatsapp"
            command.contains("telegram") -> "org.telegram.messenger"
            command.contains("instagram") -> "com.instagram.android"
            command.contains("sms") || command.contains("mensagem") -> "com.google.android.apps.messaging"
            else -> "com.whatsapp"
        }

        val contact = extractContact(command) ?: return "Senhor, diga o nome ou número do contacto."
        val message = extractMessageText(command) ?: return "Senhor, diga a mensagem."

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                `package` = app
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            "Senhor, mensagem preparada para $contact: '$message'. Confirme o envio."
        } catch (e: Exception) {
            "Senhor, erro ao preparar mensagem: ${e.message}"
        }
    }

    private fun makeCall(command: String): String {
        val number = extractPhoneNumber(command) ?: return "Senhor, diga o número ou nome do contacto."
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            "Senhor, a ligar para $number..."
        } catch (e: Exception) {
            "Senhor, erro ao ligar: ${e.message}"
        }
    }

    private fun replyToLastMessage(command: String): String {
        val replyText = command.replace(Regex("(responder|reply)\\s*(com|with)?\\s*", RegexOption.IGNORE_CASE), "").trim()
        return "Senhor, abrindo o último chat para responder: '$replyText'. Confirme o envio."
    }

    // === AGENDA ===
    private fun setAlarm(command: String): String {
        val time = extractTime(command) ?: return "Senhor, diga a hora. Exemplo: 'alarme para as 6 da manhã'."
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, time.first)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, time.second)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Alarme Aura")
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            "Senhor, alarme definido para ${time.first}:${String.format("%02d", time.second)}. O TikTok nem piscou."
        } catch (e: Exception) {
            "Senhor, erro ao definir alarme: ${e.message}"
        }
    }

    private fun setReminder(command: String): String {
        val text = command.replace(Regex("(lembrete|reminder|lembrar|lembra-me)\\s*(de|para|que)?\\s*", RegexOption.IGNORE_CASE), "").trim()
        memory?.saveFactual("reminder_${System.currentTimeMillis()}", text)
        return "Senhor, lembrete guardado: '$text'. Vou lembrá-lo no momento certo."
    }

    private fun manageTasks(command: String): String {
        return when {
            command.contains("adicionar") || command.contains("add") || command.contains("nova") -> {
                val task = command.replace(Regex("(adicionar tarefa|add task|nova tarefa)\\s*", RegexOption.IGNORE_CASE), "").trim()
                memory?.saveFactual("task_${System.currentTimeMillis()}", task)
                "Senhor, tarefa adicionada: '$task'."
            }
            command.contains("listar") || command.contains("ver") || command.contains("mostrar") || command.contains("what") -> {
                val tasks = memory?.getAllByPrefix("task_") ?: emptyMap()
                if (tasks.isEmpty()) "Senhor, nenhuma tarefa pendente."
                else "Senhor, tarefas:\n" + tasks.values.joinToString("\n") { "• $it" }
            }
            command.contains("concluir") || command.contains("done") || command.contains("completar") -> {
                "Senhor, funcionalidade de concluir tarefa requer UI. Tarefa marcada mentalmente."
            }
            else -> "Senhor, comandos de tarefa: 'adicionar tarefa comprar leite', 'listar tarefas', 'concluir tarefa'."
        }
    }

    // === MEMÓRIA ===
    private fun rememberSomething(command: String): String {
        val info = command.replace(Regex("(lembra|remember|memoriza|guarda)\\s*(que|de|que o|que a)?\\s*", RegexOption.IGNORE_CASE), "").trim()
        val key = "memory_${System.currentTimeMillis()}"
        memory?.saveFactual(key, info)
        return "Senhor, memorizei: '$info'. Nunca vou esquecer."
    }

    private fun recallMemory(): String {
        val memories = memory?.getAllFactual() ?: emptyMap()
        if (memories.isEmpty()) return "Senhor, ainda não me contou nada para lembrar."
        return "Senhor, aqui está o que sei:\n" + memories.entries.take(20).joinToString("\n") { "• ${it.value}" }
    }

    private fun findItem(command: String): String {
        val item = command.replace(Regex("(onde está|where is|onde pus|onde deixei)\\s*(o|a|os|as)?\\s*", RegexOption.IGNORE_CASE), "").trim()
        val memories = memory?.getAllFactual() ?: emptyMap()
        val match = memories.values.find { it.contains(item, ignoreCase = true) }
        return if (match != null) "Senhor, encontrei: $match"
        else "Senhor, não me lembro onde deixou $item. Quer que eu memorize a próxima vez?"
    }

    private fun manageShoppingList(command: String): String {
        return when {
            command.contains("adicionar") || command.contains("add") || command.contains("mete") -> {
                val item = command.replace(Regex("(adicionar|add|mete)\\s*(à|a|na)?\\s*lista\\s*(de compras)?\\s*", RegexOption.IGNORE_CASE), "").trim()
                memory?.saveFactual("shop_${System.currentTimeMillis()}", item)
                "Senhor, '$item' adicionado à lista de compras."
            }
            command.contains("ver") || command.contains("mostrar") || command.contains("lista") -> {
                val items = memory?.getAllByPrefix("shop_") ?: emptyMap()
                if (items.isEmpty()) "Senhor, lista de compras vazia."
                else "Senhor, lista de compras:\n" + items.values.joinToString("\n") { "• $it" }
            }
            command.contains("remover") || command.contains("apagar") || command.contains("tirar") -> {
                val item = command.replace(Regex("(remover|apagar|tirar)\\s*(da|da|da)?\\s*lista\\s*", RegexOption.IGNORE_CASE), "").trim()
                "Senhor, funcionalidade de remover item requer UI. Item '$item' marcado para remoção."
            }
            else -> "Senhor, comandos de lista: 'adicionar leite à lista', 'ver lista', 'remover leite da lista'."
        }
    }

    // === CENÁRIOS ===
    private fun activateMorningRoutine(): String {
        adjustBrightness("aumentar brilho")
        openApp("calendário")
        val weather = runBlocking { fetchWeather() }
        return "Bom dia, senhor! ☀️ $weather. Agenda aberta. Café pronto?"
    }

    private fun activateSleepRoutine(): String {
        adjustBrightness("brilho mínimo")
        setAlarm("alarme para as 7")
        return "Boa noite, senhor! 🌙 Luzes baixas, alarme definido. Descanse bem."
    }

    private fun activateWorkRoutine(): String {
        openApp("gmail")
        openApp("chrome")
        return "Modo trabalho ativado, senhor. Gmail e Chrome abertos. Foco total."
    }

    private fun activateStudyRoutine(): String {
        adjustBrightness("aumentar brilho")
        openApp("chrome")
        return "Modo estudo ativado, senhor. Brilho no máximo, Chrome aberto. Bons estudos!"
    }

    private fun activateMovieRoutine(): String {
        adjustBrightness("diminuir brilho")
        openApp("netflix")
        return "Modo cinema ativado, senhor. 🎬 Luzes baixas, Netflix aberto. Pipoca?"
    }

    private fun activateDinnerRoutine(): String {
        adjustBrightness("diminuir brilho")
        return "Hora do jantar, senhor! 🍽️ Luzes quentes. Bom apetite!"
    }

    private fun activateWorkoutRoutine(): String {
        openApp("spotify")
        return "Modo treino ativado, senhor! 💪 Spotify aberto. Vamos!"
    }

    // === API EXTERNAS ===
    private suspend fun fetchWeather(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.openweathermap.org/data/2.5/weather?q=Luanda,AO&appid=$weatherApiKey&units=metric&lang=pt")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val temp = json.getJSONObject("main").getDouble("temp")
            val desc = json.getJSONArray("weather").getJSONObject(0).getString("description")
            val humidity = json.getJSONObject("main").getInt("humidity")

            "Luanda: $desc, ${temp.toInt()}°C, humidade $humidity%"
        } catch (e: Exception) {
            "Clima de Luanda indisponível no momento."
        }
    }

    private suspend fun fetchKwanzaRate(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://v6.exchangerate-api.com/v6/$exchangeApiKey/latest/USD")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val rate = json.getJSONObject("conversion_rates").getDouble("AOA")

            "1 USD = ${String.format("%.2f", rate)} Kz (Kwanza)"
        } catch (e: Exception) {
            "Taxa de câmbio indisponível."
        }
    }

    // === HELPERS ===
    private fun extractContact(command: String): String? {
        val regex = Regex("(para|a|ao|à|com)\\s+([^\\s]+)")
        return regex.find(command)?.groupValues?.get(2)
    }

    private fun extractMessageText(command: String): String? {
        val patterns = listOf("diz ", "diga ", "escreve ", "mensagem ", "que ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim()
        }
        return null
    }

    private fun extractPhoneNumber(command: String): String? {
        val regex = Regex("(\\+?\\d[\\d\\s()-]{7,})")
        return regex.find(command)?.value?.replace("\\s".toRegex(), "")
    }

    private fun extractTime(command: String): Pair<Int, Int>? {
        val regex = Regex("(\\d{1,2})[h:](\\d{2})?|(?:às|as|para as)\\s*(\\d{1,2})")
        val match = regex.find(command)
        if (match != null) {
            val hour = (match.groupValues[1].ifEmpty { match.groupValues[3] }).toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val isPM = command.contains("tarde") || command.contains("pm") || command.contains("da noite")
            val adjustedHour = if (isPM && hour < 12) hour + 12 else hour
            return Pair(adjustedHour, minute)
        }
        return null
    }

    private fun shutdownDevice() {
        try {
            val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
            intent.putExtra("android.intent.extra.KEY_CONFIRM", false)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback não funciona sem root
        }
    }

    private fun restartDevice() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.reboot("Aura restart")
        } catch (e: Exception) {
            // Requer permissão REBOOT
        }
    }

    // === FOREGROUND SERVICE ===
    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("aura_channel", "Aura AGI", NotificationManager.IMPORTANCE_LOW)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, "aura_channel")
            .setContentTitle("Aura")
            .setContentText("Assistente pessoal AGI ativo")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        scope.cancel()
    }
}
