package com.jc.aura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Base64

class AuraImageGenModule(private val context: Context, private val memory: AuraMemory) {

    private val qwenApiKey = BuildConfig.QWEN_IMAGE_KEY
    private val geminiApiKey = BuildConfig.GEMINI_KEY
    private val openRouterKey = BuildConfig.OPENROUTER_KEY

    suspend fun handleImageGenCommand(command: String): String {
        return when {
            command.contains("criar imagem") || command.contains("gerar imagem") || command.contains("faz uma imagem") -> {
                val prompt = extractPrompt(command) ?: return "Senhor, diga-me o que deseja na imagem. Exemplo: 'cria uma imagem de um leão em Luanda'."
                generateImage(prompt)
            }
            command.contains("criar logo") || command.contains("gerar logo") -> {
                val prompt = extractPrompt(command) ?: "Logo profissional minimalista"
                generateLogo(prompt)
            }
            command.contains("criar thumbnail") || command.contains("thumbnail") -> {
                val prompt = extractPrompt(command) ?: "Thumbnail de YouTube chamativa"
                generateThumbnail(prompt)
            }
            command.contains("lista de imagens") || command.contains("minhas imagens") -> {
                listGeneratedImages()
            }
            else -> "Senhor, comandos de imagem: 'cria uma imagem de...', 'gera um logo para...', 'faz uma thumbnail de...'"
        }
    }

    private suspend fun generateImage(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            voiceServiceRef?.speak("Senhor, estou gerando a imagem. Isso pode levar alguns segundos...")

            val result = tryQwenImage(prompt) ?: tryGeminiImage(prompt) ?: tryOpenRouterImage(prompt)

            if (result != null) {
                val fileName = "aura_img_${System.currentTimeMillis()}.png"
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), fileName)
                FileOutputStream(file).use { out ->
                    out.write(result)
                }

                memory.saveFactual("image_gen_${System.currentTimeMillis()}", JSONObject().apply {
                    put("prompt", prompt)
                    put("file_path", file.absolutePath)
                    put("created_at", System.currentTimeMillis())
                }.toString())

                "Senhor, imagem gerada e salva em: **${file.absolutePath}**. Prompt: '$prompt'"
            } else {
                "Senhor, todas as APIs de geração de imagem falharam. Verifique as chaves."
            }
        } catch (e: Exception) {
            "Senhor, erro ao gerar imagem: ${e.message}"
        }
    }

    private fun tryQwenImage(prompt: String): ByteArray? {
        return try {
            val url = URL("https://api.nvidia.com/v1/genai/stabilityai/stable-diffusion-xl")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $qwenApiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val json = JSONObject().apply {
                put("prompt", prompt)
                put("negative_prompt", "blurry, low quality, distorted, ugly, deformed")
                put("width", 1024)
                put("height", 1024)
                put("num_inference_steps", 50)
                put("guidance_scale", 7.5)
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)

            if (jsonResponse.has("image")) {
                Base64.getDecoder().decode(jsonResponse.getString("image"))
            } else if (jsonResponse.has("images")) {
                Base64.getDecoder().decode(jsonResponse.getJSONArray("images").getString(0))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun tryGeminiImage(prompt: String): ByteArray? {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp-image-generation:generateContent?key=$geminiApiKey")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val json = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Generate an image: $prompt. High quality, detailed, professional.")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", org.json.JSONArray().apply {
                        put("Text")
                        put("Image")
                    })
                })
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)

            val parts = jsonResponse.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("inlineData")) {
                    val data = part.getJSONObject("inlineData").getString("data")
                    return Base64.getDecoder().decode(data)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun tryOpenRouterImage(prompt: String): ByteArray? {
        return try {
            val url = URL("https://openrouter.ai/api/v1/images/generations")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val json = JSONObject().apply {
                put("model", "qwen/qwen-2.5-vl-72b-instruct")
                put("prompt", prompt)
                put("size", "1024x1024")
                put("n", 1)
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)

            val imageUrl = jsonResponse.getJSONArray("data").getJSONObject(0).getString("url")
            URL(imageUrl).readBytes()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun generateLogo(prompt: String): String {
        val fullPrompt = "Professional minimalist logo design for: $prompt. Clean vector style, transparent background, corporate branding, high resolution."
        return generateImage(fullPrompt)
    }

    private suspend fun generateThumbnail(prompt: String): String {
        val fullPrompt = "YouTube thumbnail design: $prompt. Eye-catching, bold text, vibrant colors, high contrast, 1280x720, clickbait style but professional."
        return generateImage(fullPrompt)
    }

    private fun listGeneratedImages(): String {
        val images = memory.getAllByPrefix("image_gen_")
        if (images.isEmpty()) return "Senhor, ainda não gerei nenhuma imagem."

        val sb = StringBuilder("Senhor, aqui estão as imagens que gerei:\n")
        images.forEach { (_, value) ->
            val json = JSONObject(value)
            sb.append("• **${json.getString("prompt")}** - ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(json.getLong("created_at")))}\n")
        }
        return sb.toString()
    }

    private fun extractPrompt(command: String): String? {
        val patterns = listOf("criar imagem de ", "gerar imagem de ", "faz uma imagem de ", "cria uma imagem de ", 
                              "criar logo de ", "gerar logo de ", "faz um logo de ", 
                              "criar thumbnail de ", "gerar thumbnail de ", "faz uma thumbnail de ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                return command.substring(idx + p.length).trim()
            }
        }
        return null
    }

    companion object {
        var voiceServiceRef: AuraVoiceService? = null
    }
}
