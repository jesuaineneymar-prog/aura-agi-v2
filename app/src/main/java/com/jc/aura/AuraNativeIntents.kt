package com.jc.aura

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

class AuraNativeIntents(private val context: Context) {

    fun openMapsNavigation(command: String): String {
        return try {
            val destination = extractDestination(command)
            if (destination.isNullOrBlank()) return "Senhor, diga o destino. Ex: 'navegar para Aeroporto de Luanda'."
            val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=d")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                "Senhor, a abrir navegação para $destination."
            } else {
                val webUri = Uri.parse("https://maps.google.com/?q=${Uri.encode(destination)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                "Senhor, a abrir $destination no browser."
            }
        } catch (e: Exception) {
            "Senhor, erro ao abrir mapas: ${e.message}"
        }
    }

    fun performWebSearch(command: String): String {
        return try {
            val query = extractSearchQuery(command)
            if (query.isNullOrBlank()) return "Senhor, diga o que pesquisar."
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            "Senhor, a pesquisar '$query' no Google."
        } catch (e: Exception) {
            "Senhor, erro ao pesquisar: ${e.message}"
        }
    }

    fun openQuickApp(command: String): String {
        val appMap = mapOf(
            "youtube" to "com.google.android.youtube",
            "netflix" to "com.netflix.mediaclient",
            "spotify" to "com.spotify.music",
            "twitter" to "com.twitter.android",
            "reddit" to "com.reddit.frontpage",
            "wikipedia" to "org.wikipedia",
            "github" to "com.github.android",
            "amazon" to "com.amazon.mShop.android.shopping",
            "gmail" to "com.google.android.gm",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "linkedin" to "com.linkedin.android"
        )
        for ((keyword, pkg) in appMap) {
            if (command.contains(keyword, ignoreCase = true)) {
                return try {
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                        "Senhor, a abrir ${keyword.capitalize()}."
                    } else {
                        val uri = Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        "Senhor, ${keyword.capitalize()} não está instalado. A abrir Play Store."
                    }
                } catch (e: Exception) {
                    "Senhor, erro ao abrir ${keyword.capitalize()}: ${e.message}"
                }
            }
        }
        return "Senhor, não reconheci o aplicativo. Tente: YouTube, Netflix, Spotify, WhatsApp, Gmail..."
    }

    fun makePhoneCall(number: String): String {
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Senhor, a ligar para $number."
        } catch (e: Exception) {
            "Senhor, erro ao ligar: ${e.message}"
        }
    }

    fun sendSms(number: String, message: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number")).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Senhor, a abrir SMS para $number."
        } catch (e: Exception) {
            "Senhor, erro ao enviar SMS: ${e.message}"
        }
    }

    fun shareText(text: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Partilhar via").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            "Senhor, a abrir opções de partilha."
        } catch (e: Exception) {
            "Senhor, erro ao partilhar: ${e.message}"
        }
    }

    private fun extractDestination(command: String): String? {
        val patterns = listOf("navegar para ", "ir para ", "rota para ", "direções para ", "maps para ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim()
        }
        return null
    }

    private fun extractSearchQuery(command: String): String? {
        val patterns = listOf("pesquisar ", "procurar ", "search ", "google ")
        for (p in patterns) {
            val idx = command.indexOf(p, ignoreCase = true)
            if (idx != -1) return command.substring(idx + p.length).trim()
        }
        return command.trim().takeIf { it.isNotBlank() }
    }
}
