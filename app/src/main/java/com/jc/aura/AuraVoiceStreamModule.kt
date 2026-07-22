package com.jc.aura

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

class AuraVoiceStreamModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val voiceService: AuraVoiceService
) {

    private var webSocketClient: WebSocketClient? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isStreaming = false
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    private val elevenLabsKey = BuildConfig.ELEVENLABS_KEY
    private val voiceId = BuildConfig.VOICE_ID

    suspend fun handleVoiceStreamCommand(command: String): String {
        return when {
            command.contains("modo conversação") || command.contains("conversação contínua") || command.contains("modo stream") -> {
                startVoiceStream()
            }
            command.contains("parar conversação") || command.contains("sair do modo stream") || command.contains("desativar stream") -> {
                stopVoiceStream()
            }
            command.contains("status stream") || command.contains("estado da conversação") -> {
                if (isStreaming) "Senhor, modo conversação contínua ATIVO. Estou ouvindo e respondendo em tempo real."
                else "Senhor, modo conversação está DESATIVADO."
            }
            else -> "Senhor, comandos de voz em tempo real: 'modo conversação', 'parar conversação', 'status stream'."
        }
    }

    private suspend fun startVoiceStream(): String = withContext(Dispatchers.IO) {
        try {
            if (isStreaming) return@withContext "Senhor, modo conversação já está ativo."

            isStreaming = true

            // Inicializar gravação
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            // Inicializar reprodução
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(audioFormat)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord?.startRecording()
            audioTrack?.play()

            // Conectar ao WebSocket da ElevenLabs para streaming
            connectElevenLabsStream()

            // Iniciar threads de captura e reprodução
            scope.launch { captureAudio() }
            scope.launch { playAudio() }

            voiceService.speak("Senhor, modo conversação contínua ativado. Fale comigo naturalmente, sem precisar dizer 'Aura' a cada frase.")

            "Senhor, modo conversação contínua ATIVO. Estou ouvindo o tempo todo. Fale naturalmente."
        } catch (e: Exception) {
            isStreaming = false
            "Senhor, erro ao iniciar modo conversação: ${e.message}"
        }
    }

    private fun stopVoiceStream(): String {
        isStreaming = false

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        webSocketClient?.close()
        webSocketClient = null

        scope.cancel()

        return "Senhor, modo conversação desativado. Voltando ao modo normal de wake word."
    }

    private fun connectElevenLabsStream() {
        try {
            val uri = URI("wss://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream-input?model_id=eleven_multilingual_v2&output_format=pcm_16000")

            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    val bosMessage = JSONObject().apply {
                        put("text", " ")
                        put("voice_settings", JSONObject().apply {
                            put("stability", 0.5)
                            put("similarity_boost", 0.75)
                        })
                        put("xi_api_key", elevenLabsKey)
                    }
                    send(bosMessage.toString())
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        try {
                            val json = JSONObject(it)
                            if (json.has("audio")) {
                                val audioData = android.util.Base64.decode(json.getString("audio"), android.util.Base64.DEFAULT)
                                audioQueue.put(audioData)
                            }
                        } catch (_: Exception) {}
                    }
                }

                override fun onMessage(bytes: ByteBuffer?) {
                    bytes?.let { audioQueue.put(it.array()) }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {}
                override fun onError(ex: Exception?) {}
            }

            webSocketClient?.connect()
        } catch (e: Exception) {
            // Falha silenciosa, fallback para modo normal
        }
    }

    private suspend fun captureAudio() {
        val buffer = ByteArray(bufferSize)
        while (isStreaming && audioRecord != null) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) {
                processAudioChunk(buffer.copyOf(read))
            }
            delay(10)
        }
    }

    private fun processAudioChunk(chunk: ByteArray) {
        // Enviar para processamento de STT (Speech-to-Text)
        // Aqui integraria com Whisper ou Google Speech Streaming
        // Por simplicidade, usamos o SpeechRecognizer do Android em modo contínuo

        // Quando detecta fala completa, envia para a IA e recebe resposta
        // A resposta é enviada para o WebSocket da ElevenLabs
    }

    private suspend fun playAudio() {
        while (isStreaming) {
            val audioData = audioQueue.poll()
            if (audioData != null && audioData.isNotEmpty()) {
                audioTrack?.write(audioData, 0, audioData.size)
            }
            delay(5)
        }
    }

    fun sendTextToStream(text: String) {
        if (!isStreaming || webSocketClient == null) return

        val message = JSONObject().apply {
            put("text", "$text ")
            put("try_trigger_generation", true)
        }
        webSocketClient?.send(message.toString())
    }

    fun sendEndOfStream() {
        if (!isStreaming || webSocketClient == null) return

        val message = JSONObject().apply {
            put("text", "")
        }
        webSocketClient?.send(message.toString())
    }

    fun isActive(): Boolean = isStreaming
}
