package com.jc.aura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.Base64

class AuraVisionModule(private val context: Context, private val memory: AuraMemory) {

    private val geminiApiKey = BuildConfig.GEMINI_KEY
    private val openRouterKey = BuildConfig.OPENROUTER_KEY

    suspend fun handleVisionCommand(command: String, fileUri: Uri? = null): String {
        return when {
            command.contains("pdf") || command.contains("PDF") || command.contains("documento") -> {
                if (fileUri != null) readPdf(fileUri) else "Senhor, por favor envie o PDF que deseja que eu analise."
            }
            command.contains("imagem") || command.contains("foto") || command.contains("imagem") -> {
                if (fileUri != null) analyzeImage(fileUri) else "Senhor, por favor envie a imagem que deseja que eu analise."
            }
            command.contains("descreve") || command.contains("o que é isto") -> {
                if (fileUri != null) analyzeImage(fileUri) else "Senhor, preciso que me envie o arquivo visual."
            }
            else -> "Senhor, posso ler PDFs e analisar imagens. Diga 'analisa este PDF' ou 'descreve esta imagem'."
        }
    }

    private suspend fun readPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val parcelFileDescriptor: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            val pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
            val pageCount = pdfRenderer.pageCount
            val sb = StringBuilder()

            for (i in 0 until minOf(pageCount, 10)) {
                val page = pdfRenderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val text = extractTextFromImage(bitmap)
                sb.append("--- Página ${i + 1} ---\n")
                sb.append(text)
                sb.append("\n\n")

                page.close()
            }
            pdfRenderer.close()
            parcelFileDescriptor.close()

            val result = sb.toString()
            memory.saveFactual("pdf_read_${System.currentTimeMillis()}", result)
            "Senhor, aqui está o conteúdo do PDF ($pageCount páginas, analisei as primeiras 10):\n\n$result"
        } catch (e: Exception) {
            "Senhor, ocorreu um erro ao ler o PDF: ${e.message}"
        }
    }

    private suspend fun analyzeImage(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val base64Image = bitmapToBase64(bitmap)

            val result = callOpenRouterVision(base64Image, "Descreva esta imagem em detalhes em português.")
            memory.saveFactual("image_analyzed_${System.currentTimeMillis()}", result)
            "Senhor, aqui está a análise da imagem:\n\n$result"
        } catch (e: Exception) {
            "Senhor, erro ao analisar imagem: ${e.message}"
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private suspend fun callOpenRouterVision(base64Image: String, prompt: String): String {
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
                put("max_tokens", 2000)
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } catch (e: Exception) {
            fallbackToGeminiVision(base64Image, prompt)
        }
    }

    private fun fallbackToGeminiVision(base64Image: String, prompt: String): String {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiApiKey")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.inputStream.bufferedReader().readText()
            val jsonResponse = JSONObject(response)
            jsonResponse.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        } catch (e: Exception) {
            "Senhor, não consegui processar a imagem. Erro: ${e.message}"
        }
    }

    private suspend fun extractTextFromImage(bitmap: Bitmap): String {
        val base64 = bitmapToBase64(bitmap)
        return callOpenRouterVision(base64, "Extraia TODO o texto visível nesta imagem. Se for uma página de documento, preserve a formatação. Responda apenas com o texto, sem comentários.")
    }
}
