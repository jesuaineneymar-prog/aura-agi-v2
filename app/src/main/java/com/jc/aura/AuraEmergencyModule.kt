package com.jc.aura

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AuraEmergencyModule(private val context: Context, private val memory: AuraMemory) {

    private val emergencyContacts = mutableListOf<EmergencyContact>()

    data class EmergencyContact(val name: String, val number: String)

    init {
        loadContacts()
    }

    private fun loadContacts() {
        val stored = memory.getAllByPrefix("emergency_contact_")
        stored.forEach { (_, value) ->
            try {
                val json = JSONObject(value)
                emergencyContacts.add(EmergencyContact(json.getString("name"), json.getString("number")))
            } catch (_: Exception) {}
        }
    }

    suspend fun handle(command: String): String {
        return when {
            command.contains("emergência") || command.contains("socorro") || command.contains("ajuda") ||
            command.contains("perigo") || command.contains("help") -> {
                activateEmergency(command)
            }
            command.contains("bombeiros") -> callEmergency("115", "Bombeiros")
            command.contains("ambulância") || command.contains("ambulancia") -> callEmergency("116", "Ambulância")
            command.contains("polícia") || command.contains("policia") -> callEmergency("113", "Polícia")
            command.contains("adicionar contato emergência") || command.contains("adicionar contacto emergência") -> {
                addEmergencyContact(command)
            }
            command.contains("ligar emergência") || command.contains("ligar para emergência") -> {
                callAllEmergencyContacts()
            }
            command.contains("sms emergência") -> {
                sendEmergencySms(command)
            }
            else -> "Senhor, comandos de emergência: 'emergência', 'bombeiros', 'ambulância', 'polícia', 'adicionar contato emergência João 912345678', 'sms emergência'."
        }
    }

    private suspend fun activateEmergency(command: String): String = withContext(Dispatchers.Main) {
        val sb = StringBuilder()
        sb.append("🚨 MODO EMERGÊNCIA ATIVADO!\n")

        // Ligar para 112
        callEmergency("112", "Número de Emergência Nacional")
        sb.append("• A ligar para 112\n")

        // SMS para contactos
        if (emergencyContacts.isNotEmpty()) {
            val location = memory.getFactual("last_location") ?: "localização desconhecida"
            val msg = "🚨 EMERGÊNCIA! Estou em perigo! Última localização: $location. Por favor ajuda!"
            emergencyContacts.forEach { contact ->
                try {
                    SmsManager.getDefault().sendTextMessage(contact.number, null, msg, null, null)
                    sb.append("• SMS enviado para ${contact.name}\n")
                } catch (_: Exception) {}
            }
        }

        sb.append("\nSenhor, modo emergência ativado. Serviços de socorro contactados.")
        sb.toString()
    }

    private fun callEmergency(number: String, name: String): String {
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Senhor, a ligar para $name ($number)."
        } catch (e: Exception) {
            "Senhor, erro ao ligar para $name: ${e.message}"
        }
    }

    private fun addEmergencyContact(command: String): String {
        val regex = Regex("([A-Za-zÀ-ÿ]+(?:\\s[A-Za-zÀ-ÿ]+)?)\\s+(\\+?\\d[\\d\\s]{6,15})")
        val match = regex.find(command) ?: return "Senhor, formato: 'adicionar contato emergência [Nome] [Número]'. Ex: 'adicionar contato emergência Maria 912345678'."
        val name = match.groupValues[1].trim()
        val number = match.groupValues[2].trim().replace(" ", "")
        val contact = EmergencyContact(name, number)
        emergencyContacts.add(contact)
        memory.saveFactual("emergency_contact_${name.lowercase()}", JSONObject().apply {
            put("name", name)
            put("number", number)
        }.toString())
        return "Senhor, $name ($number) adicionado como contato de emergência."
    }

    private fun callAllEmergencyContacts(): String {
        if (emergencyContacts.isEmpty()) return "Senhor, não tem contactos de emergência. Use 'adicionar contato emergência [Nome] [Número]'."
        emergencyContacts.forEachIndexed { idx, contact ->
            if (idx == 0) callEmergency(contact.number, contact.name)
        }
        return "Senhor, a ligar para ${emergencyContacts.first().name}."
    }

    private fun sendEmergencySms(command: String): String {
        if (emergencyContacts.isEmpty()) return "Senhor, sem contactos de emergência configurados."
        val location = memory.getFactual("last_location") ?: "localização desconhecida"
        val msg = "🚨 EMERGÊNCIA! Estou a precisar de ajuda! Última localização: $location."
        var sent = 0
        emergencyContacts.forEach { contact ->
            try {
                SmsManager.getDefault().sendTextMessage(contact.number, null, msg, null, null)
                sent++
            } catch (_: Exception) {}
        }
        return "Senhor, SMS de emergência enviado para $sent contacto(s)."
    }
}
