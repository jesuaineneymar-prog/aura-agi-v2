package com.jc.aura

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class AuraIoTModule(private val context: Context, private val memory: AuraMemory) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val devices = mutableMapOf<String, IoTDevice>()
    private val hueApiKey = ""
    private val tuyaClientId = ""
    private val tuyaSecret = ""
    private val smartThingsToken = ""

    init {
        loadSavedDevices()
    }

    suspend fun handleIoTCommand(command: String): String {
        return when {
            command.contains("ligar luz") || command.contains("acender") || command.contains("turn on light") -> {
                val room = extractRoom(command) ?: "sala"
                controlDevice("light_$room", "on")
            }
            command.contains("desligar luz") || command.contains("apagar") || command.contains("turn off light") -> {
                val room = extractRoom(command) ?: "sala"
                controlDevice("light_$room", "off")
            }
            command.contains("mudar cor") || command.contains("cor da luz") || command.contains("change color") -> {
                val color = extractColor(command) ?: "white"
                val room = extractRoom(command) ?: "sala"
                changeColor("light_$room", color)
            }
            command.contains("diminuir luz") || command.contains("dimmer") || command.contains("dim") -> {
                val level = extractPercentage(command) ?: 50
                val room = extractRoom(command) ?: "sala"
                setBrightness("light_$room", level)
            }
            command.contains("temperatura") || command.contains("ar condicionado") || command.contains("AC") || command.contains("ar condicionado") -> {
                val temp = extractNumber(command)
                if (temp != null) {
                    setTemperature("ac_${extractRoom(command) ?: "quarto"}", temp)
                } else {
                    getTemperature("ac_${extractRoom(command) ?: "quarto"}")
                }
            }
            command.contains("ligar AC") || command.contains("acender ar") -> {
                controlDevice("ac_${extractRoom(command) ?: "quarto"}", "on")
            }
            command.contains("desligar AC") || command.contains("apagar ar") -> {
                controlDevice("ac_${extractRoom(command) ?: "quarto"}", "off")
            }
            command.contains("fechar porta") || command.contains("trancar") || command.contains("lock") -> {
                val door = extractDoor(command) ?: "principal"
                controlDevice("lock_$door", "lock")
            }
            command.contains("abrir porta") || command.contains("destrancar") || command.contains("unlock") -> {
                val door = extractDoor(command) ?: "principal"
                controlDevice("lock_$door", "unlock")
            }
            command.contains("modo cinema") || command.contains("movie mode") -> {
                activateScene("movie")
            }
            command.contains("modo sono") || command.contains("sleep mode") || command.contains("boa noite") -> {
                activateScene("sleep")
            }
            command.contains("modo trabalho") || command.contains("work mode") -> {
                activateScene("work")
            }
            command.contains("modo festa") || command.contains("party mode") -> {
                activateScene("party")
            }
            command.contains("adicionar dispositivo") || command.contains("cadastrar") -> {
                addDevice(command)
            }
            command.contains("lista de dispositivos") || command.contains("dispositivos") -> {
                listDevices()
            }
            command.contains("status casa") || command.contains("estado da casa") -> {
                getHomeStatus()
            }
            else -> "Senhor, comandos IoT: 'ligar luz da sala', 'desligar luz do quarto', 'mudar cor para azul', 'temperatura 22 graus', 'trancar porta', 'modo cinema', 'modo sono'."
        }
    }

    private suspend fun controlDevice(deviceId: String, action: String): String = withContext(Dispatchers.IO) {
        val device = devices[deviceId]
        if (device == null) {
            return@withContext "Senhor, dispositivo $deviceId não encontrado. Diga 'adicionar dispositivo' para cadastrar."
        }

        return@withContext when (device.protocol) {
            "hue" -> controlHue(device, action)
            "tuya" -> controlTuya(device, action)
            "smartthings" -> controlSmartThings(device, action)
            "generic_http" -> controlGenericHTTP(device, action)
            "mqtt" -> controlMQTT(device, action)
            else -> "Senhor, protocolo ${device.protocol} não suportado."
        }
    }

    private fun controlHue(device: IoTDevice, action: String): String {
        return try {
            val state = if (action == "on") "true" else "false"
            val url = URL("http://${device.ip}/api/$hueApiKey/lights/${device.deviceId}/state")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("on", action == "on")
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.responseCode

            if (response in 200..299) {
                "Senhor, ${device.name} ${if (action == "on") "ligado" else "desligado"}."
            } else {
                "Senhor, erro ao controlar ${device.name}. Código: $response"
            }
        } catch (e: Exception) {
            "Senhor, erro na comunicação com Philips Hue: ${e.message}"
        }
    }

    private fun controlTuya(device: IoTDevice, action: String): String {
        return try {
            val url = URL("https://openapi.tuyaus.com/v1.0/devices/${device.deviceId}/commands")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("client_id", tuyaClientId)
            connection.setRequestProperty("access_token", getTuyaToken())
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("commands", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("code", "switch_1")
                        put("value", action == "on")
                    })
                })
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.responseCode

            if (response in 200..299) {
                "Senhor, ${device.name} ${if (action == "on") "ligado" else "desligado"} via Tuya."
            } else {
                "Senhor, erro Tuya: $response"
            }
        } catch (e: Exception) {
            "Senhor, erro Tuya: ${e.message}"
        }
    }

    private fun controlSmartThings(device: IoTDevice, action: String): String {
        return try {
            val url = URL("https://api.smartthings.com/v1/devices/${device.deviceId}/commands")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $smartThingsToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val commandName = if (action == "on") "on" else "off"
            val json = JSONObject().apply {
                put("commands", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("component", "main")
                        put("capability", "switch")
                        put("command", commandName)
                    })
                })
            }

            connection.outputStream.write(json.toString().toByteArray())
            val response = connection.responseCode

            if (response in 200..299) {
                "Senhor, ${device.name} ${if (action == "on") "ligado" else "desligado"} via SmartThings."
            } else {
                "Senhor, erro SmartThings: $response"
            }
        } catch (e: Exception) {
            "Senhor, erro SmartThings: ${e.message}"
        }
    }

    private fun controlGenericHTTP(device: IoTDevice, action: String): String {
        return try {
            val url = URL("${device.ip}/${action}")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val response = connection.responseCode
            if (response in 200..299) {
                "Senhor, ${device.name} ${if (action == "on") "ativado" else "desativado"}."
            } else {
                "Senhor, erro no dispositivo HTTP: $response"
            }
        } catch (e: Exception) {
            "Senhor, erro: ${e.message}"
        }
    }

    private fun controlMQTT(device: IoTDevice, action: String): String {
        return "Senhor, MQTT requer biblioteca adicional. Dispositivo ${device.name} cadastrado mas não controlável ainda."
    }

    private fun changeColor(deviceId: String, color: String): String {
        val device = devices[deviceId] ?: return "Senhor, dispositivo não encontrado."

        val hueColor = when (color.lowercase()) {
            "vermelho", "red" -> 0
            "verde", "green" -> 25500
            "azul", "blue" -> 46920
            "amarelo", "yellow" -> 12750
            "roxo", "purple" -> 56100
            "laranja", "orange" -> 8500
            "rosa", "pink" -> 60000
            "branco", "white" -> 0
            "quente", "warm" -> 3500
            "frio", "cold" -> 6500
            else -> 0
        }

        return try {
            if (device.protocol == "hue") {
                val url = URL("http://${device.ip}/api/$hueApiKey/lights/${device.deviceId}/state")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val json = JSONObject().apply {
                    put("hue", hueColor)
                    put("sat", 254)
                    put("bri", 254)
                }

                connection.outputStream.write(json.toString().toByteArray())
                "Senhor, cor da ${device.name} alterada para $color."
            } else {
                "Senhor, mudança de cor só suportada em Philips Hue por enquanto."
            }
        } catch (e: Exception) {
            "Senhor, erro ao mudar cor: ${e.message}"
        }
    }

    private fun setBrightness(deviceId: String, level: Int): String {
        val device = devices[deviceId] ?: return "Senhor, dispositivo não encontrado."
        val bri = (level * 254 / 100).coerceIn(0, 254)

        return try {
            if (device.protocol == "hue") {
                val url = URL("http://${device.ip}/api/$hueApiKey/lights/${device.deviceId}/state")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val json = JSONObject().apply {
                    put("bri", bri)
                }

                connection.outputStream.write(json.toString().toByteArray())
                "Senhor, brilho da ${device.name} ajustado para $level%."
            } else {
                "Senhor, ajuste de brilho só suportado em Hue por enquanto."
            }
        } catch (e: Exception) {
            "Senhor, erro: ${e.message}"
        }
    }

    private fun setTemperature(deviceId: String, temp: Int): String {
        val device = devices[deviceId] ?: return "Senhor, AC não encontrado."

        return try {
            if (device.protocol == "tuya") {
                val url = URL("https://openapi.tuyaus.com/v1.0/devices/${device.deviceId}/commands")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("client_id", tuyaClientId)
                connection.setRequestProperty("access_token", getTuyaToken())
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val json = JSONObject().apply {
                    put("commands", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("code", "temp_set")
                            put("value", temp)
                        })
                    })
                }

                connection.outputStream.write(json.toString().toByteArray())
                "Senhor, temperatura do ${device.name} ajustada para $temp°C."
            } else {
                "Senhor, controle de temperatura requer dispositivo Tuya."
            }
        } catch (e: Exception) {
            "Senhor, erro: ${e.message}"
        }
    }

    private fun getTemperature(deviceId: String): String {
        return "Senhor, temperatura atual do ambiente: 24°C (simulado - requer sensor real)."
    }

    private fun activateScene(scene: String): String {
        val msg = when (scene) {
            "movie" -> "Senhor, modo cinema ativado. Luzes baixas e quentes."
            "sleep" -> "Senhor, modo sono ativado. Luzes apagadas, AC em 22°C, porta trancada. Boa noite."
            "work" -> "Senhor, modo trabalho ativado. Iluminação máxima e fria."
            "party" -> "Senhor, modo festa ativado. Luzes no máximo!"
            else -> "Senhor, cena não reconhecida."
        }
        scope.launch {
            when (scene) {
                "movie" -> {
                    controlDevice("light_sala", "on")
                    setBrightness("light_sala", 10)
                    changeColor("light_sala", "warm")
                }
                "sleep" -> {
                    controlDevice("light_quarto", "off")
                    controlDevice("light_sala", "off")
                    controlDevice("ac_quarto", "on")
                    setTemperature("ac_quarto", 22)
                    controlDevice("lock_principal", "lock")
                }
                "work" -> {
                    controlDevice("light_escritorio", "on")
                    setBrightness("light_escritorio", 100)
                    changeColor("light_escritorio", "white")
                }
                "party" -> {
                    controlDevice("light_sala", "on")
                    setBrightness("light_sala", 100)
                }
                else -> {}
            }
        }
        return msg
    }

    private fun addDevice(command: String): String {
        return "Senhor, para adicionar um dispositivo, preciso: nome, tipo (luz/AC/porta), protocolo (hue/tuya/smartthings), IP e ID. Exemplo: 'adicionar luz da sala, protocolo hue, IP 192.168.1.50, ID 1'."
    }

    private fun listDevices(): String {
        if (devices.isEmpty()) return "Senhor, nenhum dispositivo IoT cadastrado."

        val sb = StringBuilder("Senhor, dispositivos cadastrados:\n")
        devices.forEach { (id, device) ->
            sb.append("• **${device.name}** ($id) - ${device.protocol} - ${device.ip}\n")
        }
        return sb.toString()
    }

    private fun getHomeStatus(): String {
        val sb = StringBuilder("Senhor, status da casa:\n")
        devices.forEach { (id, device) ->
            sb.append("• ${device.name}: ${if (device.lastState == "on") "🔴 Ligado" else "⚫ Desligado"}\n")
        }
        return sb.toString()
    }

    private fun getTuyaToken(): String {
        return memory.getFactual("tuya_token") ?: ""
    }

    private fun loadSavedDevices() {
        val saved = memory.getAllByPrefix("iot_device_")
        saved.forEach { (_, value) ->
            try {
                val json = JSONObject(value)
                val device = IoTDevice(
                    name = json.getString("name"),
                    type = json.getString("type"),
                    protocol = json.getString("protocol"),
                    ip = json.optString("ip", ""),
                    deviceId = json.getString("device_id")
                )
                devices["${device.type}_${device.name}"] = device
            } catch (_: Exception) {}
        }
    }

    private fun extractRoom(command: String): String? {
        val rooms = listOf("sala", "quarto", "cozinha", "banheiro", "escritorio", "escritório", "garagem", "jardim", "hall")
        for (room in rooms) {
            if (command.contains(room, ignoreCase = true)) return room
        }
        return null
    }

    private fun extractColor(command: String): String? {
        val colors = listOf("vermelho", "verde", "azul", "amarelo", "roxo", "laranja", "rosa", "branco", "quente", "frio",
                           "red", "green", "blue", "yellow", "purple", "orange", "pink", "white", "warm", "cold")
        for (color in colors) {
            if (command.contains(color, ignoreCase = true)) return color
        }
        return null
    }

    private fun extractPercentage(command: String): Int? {
        val regex = Regex("(\\d+)%?|(\\d+) por cento")
        val match = regex.find(command)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: match?.groupValues?.get(2)?.toIntOrNull()
    }

    private fun extractNumber(command: String): Int? {
        val regex = Regex("(\\d+)")
        val match = regex.find(command)
        return match?.value?.toIntOrNull()
    }

    private fun extractDoor(command: String): String? {
        val doors = listOf("principal", "fundos", "garagem", "quarto", "escritorio", "escritório")
        for (door in doors) {
            if (command.contains(door, ignoreCase = true)) return door
        }
        return null
    }

    data class IoTDevice(
        val name: String,
        val type: String,
        val protocol: String,
        val ip: String,
        val deviceId: String,
        var lastState: String = "unknown"
    )
}
