package com.jc.aura

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AuraEmailModule(private val context: Context, private val memory: AuraMemory) {

    suspend fun handle(command: String): String {
        return when {
            command.contains("enviar email") || command.contains("mandar email") || command.contains("escrever email") || command.contains("send email") -> composeEmail(command)
            command.contains("abrir gmail") || command.contains("ver emails") || command.contains("caixa de entrada") -> openGmail()
            command.contains("responder email") -> "Senhor, para responder, abra o Gmail e responda manualmente, ou diga 'abrir gmail'."
            command.contains("guardar contacto email") || command.contains("salvar email") -> saveEmailContact(command)
            command.contains("contactos email") || command.contains("emails guardados") -> listEmailContacts()
            else -> "Senhor, comandos de email: 'enviar email para João sobre reunião', 'abrir gmail', 'guardar contacto email João joao@empresa.com'."
        }
    }

    private fun composeEmail(command: String): String {
        val recipient = extractRecipient(command)
        val subject = extractSubject(command)
        val body = extractBody(command)

        val recipientEmail = if (recipient != null) {
            // Tentar encontrar email guardado
            val stored = memory.getFactual("email_contact_${recipient.lowercase()}")
            if (stored != null) {
                try { JSONObject(stored).getString("email") } catch (_: Exception) { null }
            } else null
        } else null

        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                if (recipientEmail != null) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
                if (body != null) putExtra(Intent.EXTRA_TEXT, body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            val to = recipientEmail ?: recipient ?: "destinatário"
            "Senhor, a abrir email para $to${if (subject != null) " sobre '$subject'" else ""}."
        } catch (e: Exception) {
            "Senhor, erro ao abrir email: ${e.message}"
        }
    }

    private fun openGmail(): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                "Senhor, a abrir Gmail."
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                "Senhor, Gmail não instalado. A abrir no browser."
            }
        } catch (e: Exception) {
            "Senhor, erro ao abrir Gmail: ${e.message}"
        }
    }

    private fun saveEmailContact(command: String): String {
        val regex = Regex("guardar contacto email\\s+([A-Za-zÀ-ÿ]+(?:\\s[A-Za-zÀ-ÿ]+)?)\\s+([\\w.+-]+@[\\w-]+\\.[\\w.]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(command) ?: return "Senhor, formato: 'guardar contacto email [Nome] [email@dominio.com]'."
        val name = match.groupValues[1].trim()
        val email = match.groupValues[2].trim()
        memory.saveFactual("email_contact_${name.lowercase()}", JSONObject().apply {
            put("name", name)
            put("email", email)
        }.toString())
        return "Senhor, contacto **$name** ($email) guardado."
    }

    private fun listEmailContacts(): String {
        val contacts = memory.getAllByPrefix("email_contact_")
        if (contacts.isEmpty()) return "Senhor, não tem contactos de email guardados. Use 'guardar contacto email [Nome] [email]'."
        val sb = StringBuilder("Senhor, contactos de email:\n\n")
        contacts.values.forEachIndexed { i, v ->
            try {
                val json = JSONObject(v)
                sb.append("${i + 1}. **${json.getString("name")}** — ${json.getString("email")}\n")
            } catch (_: Exception) {}
        }
        return sb.toString()
    }

    private fun extractRecipient(command: String): String? {
        val patterns = listOf("para ", "to ", "ao ", "à ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                val after = command.substring(idx + p.length).trim()
                return after.split(" ").firstOrNull()?.replace(",", "")
            }
        }
        return null
    }

    private fun extractSubject(command: String): String? {
        val patterns = listOf("sobre ", "assunto ", "subject ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                val after = command.substring(idx + p.length).trim()
                val end = after.indexOf(" com texto ").takeIf { it != -1 } ?: after.length
                return after.substring(0, end).trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun extractBody(command: String): String? {
        val patterns = listOf("com texto ", "mensagem ", "body ", "dizendo ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim().takeIf { it.isNotBlank() }
        }
        return null
    }
}
