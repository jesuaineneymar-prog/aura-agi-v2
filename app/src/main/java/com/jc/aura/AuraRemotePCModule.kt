package com.jc.aura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue

class AuraRemotePCModule(private val context: Context, private val memory: AuraMemory) {

    private var webSocketClient: WebSocketClient? = null
    private val messageQueue = LinkedBlockingQueue<String>()
    private var isConnected = false
    private var pcIp: String = ""
    private var pcPort: Int = 8765

    suspend fun handleRemotePCCommand(command: String): String {
        return when {
            command.contains("conectar pc") || command.contains("ligar ao computador") || command.contains("conectar computador") -> {
                val ip = extractIP(command) ?: return "Senhor, diga o IP do PC. Exemplo: 'conectar ao PC 192.168.1.100'."
                connectToPC(ip)
            }
            command.contains("desconectar pc") || command.contains("desligar computador") -> {
                disconnectPC()
            }
            command.contains("screenshot pc") || command.contains("ver tela do pc") || command.contains("o que vês no pc") -> {
                requestScreenshot()
            }
            command.contains("clicar") || command.contains("click") -> {
                val coords = extractCoordinates(command) ?: return "Senhor, diga as coordenadas. Exemplo: 'clicar em 500 300'."
                sendMouseClick(coords.first, coords.second)
            }
            command.contains("digitar") || command.contains("escrever no pc") -> {
                val text = extractTextToType(command) ?: return "Senhor, diga o texto. Exemplo: 'digitar no PC Olá mundo'."
                sendKeyboardText(text)
            }
            command.contains("abrir app no pc") || command.contains("executar no pc") -> {
                val app = extractAppName(command) ?: return "Senhor, diga o nome do programa. Exemplo: 'abrir Chrome no PC'."
                sendOpenApp(app)
            }
            command.contains("status pc") || command.contains("estado do computador") -> {
                requestPCStatus()
            }
            command.contains("desligar pc") || command.contains("shutdown") -> {
                sendShutdown()
            }
            command.contains("reiniciar pc") || command.contains("restart") -> {
                sendRestart()
            }
            else -> "Senhor, comandos de PC remoto: 'conectar ao PC 192.168.1.100', 'screenshot do PC', 'clicar em 500 300', 'digitar no PC...', 'abrir Chrome no PC', 'desligar PC'."
        }
    }

    private suspend fun connectToPC(ip: String): String = withContext(Dispatchers.IO) {
        try {
            pcIp = ip
            val uri = URI("ws://$ip:$pcPort")

            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    isConnected = true
                    messageQueue.put("{\"type\":\"auth\",\"client\":\"AuraAndroid\",\"version\":\"2.0\"}")
                }

                override fun onMessage(message: String?) {
                    message?.let { handlePCMessage(it) }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    isConnected = false
                }

                override fun onError(ex: Exception?) {
                    isConnected = false
                }
            }

            webSocketClient?.connect()
            delay(3000)

            if (isConnected) {
                memory.saveFactual("pc_connected", ip)
                "Senhor, conectado ao PC em $ip:$pcPort. Controle remoto ativo."
            } else {
                "Senhor, falha ao conectar ao PC. Verifique se o servidor Aura está rodando no PC e se o IP está correto."
            }
        } catch (e: Exception) {
            "Senhor, erro ao conectar: ${e.message}"
        }
    }

    private fun disconnectPC(): String {
        webSocketClient?.close()
        isConnected = false
        memory.deleteFactual("pc_connected")
        return "Senhor, desconectado do PC."
    }

    private fun requestScreenshot(): String {
        if (!isConnected) return "Senhor, não estou conectado a nenhum PC."

        sendCommand("{\"type\":\"screenshot\"}")
        return "Senhor, pedi screenshot ao PC. Aguarde..."
    }

    private fun sendMouseClick(x: Int, y: Int): String {
        if (!isConnected) return "Senhor, não estou conectado a nenhum PC."

        sendCommand("{\"type\":\"mouse_click\",\"x\":$x,\"y\":$y,\"button\":\"left\"}")
        return "Senhor, clique enviado para coordenadas ($x, $y)."
    }

    private fun sendKeyboardText(text: String): String {
        if (!isConnected) return "Senhor, não estou conectado a nenhum PC."

        sendCommand("{\"type\":\"keyboard_type\",\"text\":\"${text.replace("\\", "\\\\").replace("\"", "\\\"")}\"}")
        return "Senhor, texto enviado ao PC: '$text'"
    }

    private fun sendOpenApp(appName: String): String {
        if (!isConnected) return "Senhor, não estou conectado a nenhum PC."

        sendCommand("{\"type\":\"open_app\",\"app\":\"$appName\"}")
        return "Senhor, pedido para abrir '$appName' enviado ao PC."
    }

    private fun requestPCStatus(): String {
        if (!isConnected) return "Senhor, não estou conectado a nenhum PC."

        sendCommand("{\"type\":\"status\"}")
        return "Senhor, pedi status ao PC. Aguarde resposta..."
    }

    private fun sendShutdown(): String {
        if (!isConnected) return "Senhor, não estou conectado a nenhum PC."

        sendCommand("{\"type\":\"shutdown\"}")
        return "Senhor, comando de desligamento enviado ao PC."
    }

    private fun sendRestart(): String {
        if (!isConnected) return "Senhor, não estou conectado a nenhum PC."

        sendCommand("{\"type\":\"restart\"}")
        return "Senhor, comando de reinício enviado ao PC."
    }

    private fun sendCommand(json: String) {
        webSocketClient?.send(json)
    }

    private fun handlePCMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.getString("type")) {
                "screenshot_response" -> {
                    val base64Image = json.getString("data")
                    val bytes = Base64.getDecoder().decode(base64Image)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    val file = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), 
                        "pc_screenshot_${System.currentTimeMillis()}.png")
                    java.io.FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    AuraVoiceService.speakStatic("Senhor, screenshot do PC recebido e salvo em ${file.absolutePath}")
                }
                "status_response" -> {
                    val cpu = json.optString("cpu_usage", "N/A")
                    val ram = json.optString("ram_usage", "N/A")
                    val disk = json.optString("disk_usage", "N/A")
                    val uptime = json.optString("uptime", "N/A")

                    AuraVoiceService.speakStatic("Senhor, status do PC: CPU $cpu, RAM $ram, Disco $disk, Uptime $uptime")
                }
                "error" -> {
                    AuraVoiceService.speakStatic("Senhor, erro no PC: ${json.getString("message")}")
                }
            }
        } catch (e: Exception) {
            // Ignora mensagens malformadas
        }
    }

    private fun extractIP(command: String): String? {
        val regex = Regex("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})")
        return regex.find(command)?.value
    }

    private fun extractCoordinates(command: String): Pair<Int, Int>? {
        val regex = Regex("(\\d+)\\s+(\\d+)")
        val match = regex.find(command)
        if (match != null) {
            return Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
        return null
    }

    private fun extractTextToType(command: String): String? {
        val patterns = listOf("digitar ", "escrever ", "type ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                val after = command.substring(idx + p.length).trim()
                if (after.startsWith("no pc") || after.startsWith("no PC")) {
                    return after.substring(5).trim()
                }
                return after
            }
        }
        return null
    }

    private fun extractAppName(command: String): String? {
        val patterns = listOf("abrir ", "executar ", "launch ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                val after = command.substring(idx + p.length).trim()
                if (after.contains(" no pc") || after.contains(" no PC")) {
                    return after.substringBefore(" no ").trim()
                }
                return after
            }
        }
        return null
    }
}
