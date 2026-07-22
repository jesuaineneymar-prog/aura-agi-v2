package com.jc.aura

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Base64

class AuraVideoAnalysisModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val visionModule: AuraVisionModule
) {

    private val geminiApiKey = BuildConfig.GEMINI_KEY
    private val openRouterKey = BuildConfig.OPENROUTER_KEY

    suspend fun handleVideoCommand(command: String, videoUri: Uri? = null): String {
        return when {
            command.contains("analisar vídeo") || command.contains("descreve vídeo") || command.contains("o que acontece") -> {
                if (videoUri != null) analyzeVideo(videoUri)
                else "Senhor, envie o vídeo que deseja que eu analise."
            }
            command.contains("transcrever") || command.contains("legendas") || command.contains("o que dizem") -> {
                if (videoUri != null) transcribeVideo(videoUri)
                else "Senhor, envie o vídeo para transcrição."
            }
            command.contains("resumir vídeo") || command.contains("resumo") -> {
                if (videoUri != null) summarizeVideo(videoUri)
                else "Senhor, envie o vídeo para resumir."
            }
            command.contains("extrair frames") || command.contains("cenas") -> {
                if (videoUri != null) extractKeyFrames(videoUri)
                else "Senhor, envie o vídeo para extrair frames."
            }
            else -> "Senhor, comandos de vídeo: 'analisar este vídeo', 'transcrever vídeo', 'resumir vídeo', 'extrair cenas'."
        }
    }

    private suspend fun analyzeVideo(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            val frames = mutableListOf<Bitmap>()
            val frameCount = minOf((duration / 1000).toInt(), 10)

            for (i in 0 until frameCount) {
                val timeUs = (i * (duration / frameCount)) * 1000
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) frames.add(frame)
            }
            retriever.release()

            if (frames.isEmpty()) return@withContext "Senhor, não consegui extrair frames do vídeo."

            val descriptions = mutableListOf<String>()
            for ((index, frame) in frames.withIndex()) {
                val base64 = bitmapToBase64(frame)
                val desc = callVisionAPI(base64, "Descreva o que está acontecendo nesta cena do vídeo em português. Seja breve.")
                descriptions.add("Cena ${index + 1}: $desc")
            }

            val result = descriptions.joinToString("\n")
            memory.saveFactual("video_analysis_${System.currentTimeMillis()}", JSONObject().apply {
                put("uri", uri.toString())
                put("duration", duration)
                put("analysis", result)
            }.toString())

            "Senhor, análise do vídeo (${duration / 1000}s, ${width}x${height}):\n\n$result"
        } catch (e: Exception) {
            "Senhor, erro ao analisar vídeo: ${e.message}"
        }
    }

    private suspend fun transcribeVideo(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            if (hasAudio == null || hasAudio == "no") {
                return@withContext "Senhor, este vídeo não tem áudio para transcrever."
            }

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            retriever.release()

            val audioFile = extractAudioFromVideo(uri)
            if (audioFile == null) {
                return@withContext "Senhor, não consegui extrair o áudio do vídeo."
            }

            val transcription = transcribeAudioFile(audioFile)
            audioFile.delete()

            memory.saveFactual("video_transcript_${System.currentTimeMillis()}", JSONObject().apply {
                put("uri", uri.toString())
                put("transcription", transcription)
            }.toString())

            "Senhor, transcrição do vídeo (${duration / 1000}s):\n\n$transcription"
        } catch (e: Exception) {
            "Senhor, erro na transcrição: ${e.message}"
        }
    }

    private suspend fun summarizeVideo(uri: Uri): String = withContext(Dispatchers.IO) {
        val analysis = analyzeVideo(uri)
        val transcription = try { transcribeVideo(uri) } catch (_: Exception) { "" }

        val combined = "ANÁLISE VISUAL:\n$analysis\n\nTRANSCRIÇÃO:\n$transcription"

        val summary = callTextAPI("Resuma o seguinte conteúdo de vídeo em 3 parágrafos curtos em português:\n$combined")

        "Senhor, resumo do vídeo:\n\n$summary"
    }

    private suspend fun extractKeyFrames(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0

            val outputDir = File(context.getExternalFilesDir(null), "aura_video_frames")
            outputDir.mkdirs()

            val frameCount = 5
            val savedFrames = mutableListOf<String>()

            for (i in 0 until frameCount) {
                val timeUs = (i * (duration / frameCount)) * 1000
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val file = File(outputDir, "frame_${i + 1}.jpg")
                    FileOutputStream(file).use { out ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    savedFrames.add(file.absolutePath)
                }
            }
            retriever.release()

            "Senhor, extraí ${savedFrames.size} frames-chave do vídeo para ${outputDir.absolutePath}"
        } catch (e: Exception) {
            "Senhor, erro ao extrair frames: ${e.message}"
        }
    }

    private fun extractAudioFromVideo(uri: Uri): File? {
        return try {
            val outputFile = File(context.cacheDir, "extracted_audio_${System.currentTimeMillis()}.wav")
            val ffmpegCommand = "-i ${uri.path} -vn -acodec pcm_s16le -ar 16000 -ac 1 ${outputFile.absolutePath}"

            val process = Runtime.getRuntime().exec(arrayOf("ffmpeg", *ffmpegCommand.split(" ").toTypedArray()))
            process.waitFor()

            if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: Exception) {
            null
        }
    }

    private fun transcribeAudioFile(audioFile: File): String {
        return try {
            val url = URL("https://openrouter.ai/api/v1/audio/transcriptions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=boundary")
            connection.doOutput = true

            val boundary = "boundary"
            val outputStream = connection.outputStream

            outputStream.write("--$boundary\r\n".toByteArray())
            outputStream.write("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"\r\n".toByteArray())
            outputStream.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
            outputStream.write(audioFile.readBytes())
            outputStream.write("\r\n--$boundary--\r\n".toByteArray())
            outputStream.flush()

            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            jsonResponse.optString("text", "Não consegui transcrever o áudio.")
        } catch (e: Exception) {
            "Erro na transcrição: ${e.message}"
        }
    }

    private fun callVisionAPI(base64Image: String, prompt: String): String {
        return try {
            val url = URL("https://openrouter.ai/api/v1/chat/completions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val json = JSONObject().apply {
                put("model", "qwen/qwen-2.5-vl-72b-instruct")
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                })
                            })
                        })
                    })
                })
                put("max_tokens", 500)
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } catch (e: Exception) {
            "Não consegui analisar esta cena."
        }
    }

    private fun callTextAPI(prompt: String): String {
        return try {
            val url = URL("https://openrouter.ai/api/v1/chat/completions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("model", "deepseek/deepseek-v4-pro")
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("text", prompt)
                    })
                })
                put("max_tokens", 1000)
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } catch (e: Exception) {
            "Não consegui gerar o resumo."
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }
}
