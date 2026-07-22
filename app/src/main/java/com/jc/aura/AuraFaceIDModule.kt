package com.jc.aura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

class AuraFaceIDModule(private val context: Context, private val memory: AuraMemory) {

    private suspend fun <T> awaitTask(task: com.google.android.gms.tasks.Task<T>): T =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { result -> cont.resume(result, null) }
            task.addOnFailureListener { e -> cont.resumeWith(kotlin.Result.failure(e)) }
        }

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private val knownFacesDir by lazy {
        File(context.filesDir, "aura_faces").apply { mkdirs() }
    }

    suspend fun handleFaceCommand(command: String, imageProxy: ImageProxy? = null): String {
        return when {
            command.contains("cadastrar rosto") || command.contains("adicionar face") || command.contains("memorizar rosto") -> {
                if (imageProxy != null) registerFace(imageProxy, extractName(command)) 
                else "Senhor, preciso que a câmara esteja ativa para cadastrar um rosto."
            }
            command.contains("quem é") || command.contains("reconhece") || command.contains("identifica") -> {
                if (imageProxy != null) recognizeFace(imageProxy)
                else "Senhor, ative a câmara para eu identificar a pessoa."
            }
            command.contains("lista de rostos") || command.contains("quem conheces") -> {
                listKnownFaces()
            }
            command.contains("apagar rosto") || command.contains("remover face") -> {
                deleteFace(extractName(command))
            }
            else -> "Senhor, comandos de reconhecimento facial: 'cadastrar rosto do João', 'quem é esta pessoa', 'lista de rostos', 'apagar rosto do João'."
        }
    }

    private suspend fun registerFace(imageProxy: ImageProxy, name: String): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = awaitTask(faceDetector.process(inputImage))

            if (faces.isEmpty()) {
                return@withContext "Senhor, não detetei nenhum rosto na imagem. Posicione a pessoa melhor."
            }
            if (faces.size > 1) {
                return@withContext "Senhor, detetei ${faces.size} rostos. Por favor, certifique-se de que só há uma pessoa."
            }

            val face = faces.first()
            val faceBitmap = cropFace(bitmap, face.boundingBox)
            val faceData = extractFaceData(face)

            val faceFile = File(knownFacesDir, "${name}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(faceFile).use { out ->
                faceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            memory.saveFactual("face_${name}", JSONObject().apply {
                put("name", name)
                put("file_path", faceFile.absolutePath)
                put("landmarks", faceData)
                put("registered_at", System.currentTimeMillis())
            }.toString())

            "Senhor, rosto de $name cadastrado com sucesso. ${faceData}"
        } catch (e: Exception) {
            "Senhor, erro ao cadastrar rosto: ${e.message}"
        }
    }

    private suspend fun recognizeFace(imageProxy: ImageProxy): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = awaitTask(faceDetector.process(inputImage))

            if (faces.isEmpty()) {
                return@withContext "Senhor, não vejo nenhum rosto no campo de visão."
            }

            val detectedFace = faces.first()
            val faceBitmap = cropFace(bitmap, detectedFace.boundingBox)
            val currentLandmarks = extractFaceData(detectedFace)

            val knownFaces = memory.getAllByPrefix("face_")
            var bestMatch: String? = null
            var bestScore = Double.MAX_VALUE

            for ((key, value) in knownFaces) {
                val stored = JSONObject(value)
                val storedLandmarks = stored.getJSONObject("landmarks")
                val distance = compareLandmarks(currentLandmarks, storedLandmarks)

                if (distance < bestScore && distance < 0.3) {
                    bestScore = distance
                    bestMatch = stored.getString("name")
                }
            }

            if (bestMatch != null) {
                val confidence = ((1 - bestScore) * 100).toInt()
                "Senhor, reconheci: **$bestMatch** com $confidence% de confiança. ${getEmotionFromFace(detectedFace)}"
            } else {
                val emotion = getEmotionFromFace(detectedFace)
                "Senhor, não reconheço esta pessoa. $emotion Pode ser um intruso ou alguém novo. Quer cadastrá-lo?"
            }
        } catch (e: Exception) {
            "Senhor, erro no reconhecimento: ${e.message}"
        }
    }

    private fun getEmotionFromFace(face: com.google.mlkit.vision.face.Face): String {
        val smileProb = face.smilingProbability ?: -1f
        val leftEyeOpen = face.leftEyeOpenProbability ?: -1f
        val rightEyeOpen = face.rightEyeOpenProbability ?: -1f

        return when {
            smileProb > 0.7f -> "Parece estar feliz e sorridente."
            smileProb < 0.2f && leftEyeOpen < 0.5f -> "Parece estar triste ou cansado."
            leftEyeOpen > 0.8f && rightEyeOpen > 0.8f && smileProb > 0.4f -> "Parece estar alerta e receptivo."
            leftEyeOpen < 0.3f || rightEyeOpen < 0.3f -> "Parece estar com sono ou desinteressado."
            else -> "Expressão neutra detectada."
        }
    }

    private fun extractFaceData(face: com.google.mlkit.vision.face.Face): JSONObject {
        return JSONObject().apply {
            put("left_eye", face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { "${it.x},${it.y}" } ?: "null")
            put("right_eye", face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { "${it.x},${it.y}" } ?: "null")
            put("nose", face.getLandmark(FaceLandmark.NOSE_BASE)?.position?.let { "${it.x},${it.y}" } ?: "null")
            put("mouth", face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.let { "${it.x},${it.y}" } ?: "null")
            put("left_ear", face.getLandmark(FaceLandmark.LEFT_EAR)?.position?.let { "${it.x},${it.y}" } ?: "null")
            put("right_ear", face.getLandmark(FaceLandmark.RIGHT_EAR)?.position?.let { "${it.x},${it.y}" } ?: "null")
            put("smile_prob", face.smilingProbability ?: -1f)
            put("left_eye_open", face.leftEyeOpenProbability ?: -1f)
            put("right_eye_open", face.rightEyeOpenProbability ?: -1f)
            put("head_x", face.headEulerAngleX)
            put("head_y", face.headEulerAngleY)
            put("head_z", face.headEulerAngleZ)
        }
    }

    private fun compareLandmarks(current: JSONObject, stored: JSONObject): Double {
        var distance = 0.0
        val keys = listOf("left_eye", "right_eye", "nose", "mouth")

        for (key in keys) {
            val c = current.optString(key, "0,0").split(",")
            val s = stored.optString(key, "0,0").split(",")
            if (c.size == 2 && s.size == 2) {
                val dx = c[0].toDouble() - s[0].toDouble()
                val dy = c[1].toDouble() - s[1].toDouble()
                distance += kotlin.math.sqrt(dx * dx + dy * dy)
            }
        }
        return distance / keys.size
    }

    private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        return Bitmap.createBitmap(bitmap, 
            boundingBox.left.coerceAtLeast(0),
            boundingBox.top.coerceAtLeast(0),
            boundingBox.width().coerceAtMost(bitmap.width - boundingBox.left),
            boundingBox.height().coerceAtMost(bitmap.height - boundingBox.top)
        )
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
    }

    private fun listKnownFaces(): String {
        val faces = memory.getAllByPrefix("face_")
        if (faces.isEmpty()) return "Senhor, não tenho nenhum rosto cadastrado."

        val sb = StringBuilder("Senhor, aqui estão as pessoas que reconheço:\n")
        faces.forEach { (key, value) ->
            val json = JSONObject(value)
            sb.append("• **${json.getString("name")}** - Cadastrado em ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(json.getLong("registered_at")))}\n")
        }
        return sb.toString()
    }

    private fun deleteFace(name: String): String {
        val key = "face_$name"
        val data = memory.getFactual(key)
        if (data != null) {
            val json = JSONObject(data)
            val filePath = json.optString("file_path")
            if (filePath.isNotEmpty()) File(filePath).delete()
            memory.deleteFactual(key)
            return "Senhor, rosto de $name removido da minha memória."
        }
        return "Senhor, não encontrei $name na minha base de dados faciais."
    }

    private fun extractName(command: String): String {
        val patterns = listOf("do ", "da ", "de ", "nome ")
        for (p in patterns) {
            val idx = command.indexOf(p)
            if (idx != -1) {
                return command.substring(idx + p.length).trim().split(" ").first().capitalize()
            }
        }
        return "Desconhecido_${System.currentTimeMillis()}"
    }
}
