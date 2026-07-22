package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * AuraDeepControlModule — Controlo profundo de apps.
 * Pesquisa, reprodução, criação e navegação dentro de qualquer app.
 * Usa deep links, intents e acessibilidade conforme necessário.
 */
class AuraDeepControlModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val accessibilityService: AccessibilityService? = null,
    private val voiceService: AuraVoiceService? = null
) {

    // ─────────────────────────────────────────────────────────────
    // ROUTER PRINCIPAL
    // ─────────────────────────────────────────────────────────────

    suspend fun handle(command: String): String {
        val cmd = command.lowercase().trim()
        return when {
            // YOUTUBE
            isYouTubeCommand(cmd) -> handleYouTube(cmd)

            // SPOTIFY
            isSpotifyCommand(cmd) -> handleSpotify(cmd)

            // WHATSAPP
            isWhatsAppCommand(cmd) -> handleWhatsApp(cmd)

            // TELEGRAM
            isTelegramCommand(cmd) -> handleTelegram(cmd)

            // NETFLIX
            isNetflixCommand(cmd) -> handleNetflix(cmd)

            // CHROME / BROWSER
            isBrowserCommand(cmd) -> handleBrowser(cmd)

            // GOOGLE MAPS
            isMapsCommand(cmd) -> handleMaps(cmd)

            // PLAY STORE
            isPlayStoreCommand(cmd) -> handlePlayStore(cmd)

            // CÂMARA
            isCameraCommand(cmd) -> handleCamera(cmd)

            // GALERIA
            isGalleryCommand(cmd) -> handleGallery(cmd)

            // ALARME / TIMER
            isAlarmCommand(cmd) -> handleAlarm(cmd)

            // CALCULADORA
            isCalculatorCommand(cmd) -> handleCalculator(cmd)

            // CONTACTOS
            isContactsCommand(cmd) -> handleContacts(cmd)

            // CHAMADAS
            isCallCommand(cmd) -> handleCall(cmd)

            // SMS
            isSmsCommand(cmd) -> handleSms(cmd)

            // GMAIL
            isGmailCommand(cmd) -> handleGmail(cmd)

            // GOOGLE DRIVE
            isDriveCommand(cmd) -> handleGoogleDrive(cmd)

            // GOOGLE FOTOS
            isPhotosCommand(cmd) -> handleGooglePhotos(cmd)

            // AMAZON
            isAmazonCommand(cmd) -> handleAmazon(cmd)

            // TWITTER / X
            isTwitterCommand(cmd) -> handleTwitter(cmd)

            // REDDIT
            isRedditCommand(cmd) -> handleReddit(cmd)

            // WIKIPEDIA
            isWikipediaCommand(cmd) -> handleWikipedia(cmd)

            // GITHUB
            isGithubCommand(cmd) -> handleGitHub(cmd)

            // SHAZAM
            isShazamCommand(cmd) -> handleShazam(cmd)

            else -> "Senhor, não reconheci a app alvo. Apps suportadas: YouTube, Spotify, WhatsApp, Telegram, Netflix, Chrome, Maps, Play Store, Câmara, Gmail, Drive, Twitter, Reddit, Wikipedia."
        }
    }

    // ─────────────────────────────────────────────────────────────
    // YOUTUBE
    // ─────────────────────────────────────────────────────────────

    private fun isYouTubeCommand(cmd: String) =
        cmd.contains("youtube") || (cmd.contains("vídeo") && !cmd.contains("tiktok"))

    private suspend fun handleYouTube(cmd: String): String {
        val pkg = "com.google.android.youtube"
        return when {
            // pesquisa Drake no YouTube
            cmd.contains("pesquis") || cmd.contains("procur") || cmd.contains("search") -> {
                val query = extractQueryAfter(cmd, listOf("pesquisa ", "pesquisar ", "procura ", "procurar ", "search ")) ?: extractQueryBefore(cmd, listOf(" no youtube", " youtube"))
                    ?: return "Senhor, diga o que pesquisar. Ex: 'pesquisa Drake no YouTube'."
                openYouTubeSearch(query)
            }
            // toca / reproduz / coloca
            cmd.contains("toca ") || cmd.contains("toca ") || cmd.contains("reproduz") || cmd.contains("play") || cmd.contains("coloca") -> {
                val query = extractMediaQuery(cmd, listOf("toca ", "reproduz ", "play ", "coloca "), listOf(" no youtube", " youtube"))
                    ?: return "Senhor, diga o que reproduzir. Ex: 'toca Drake no YouTube'."
                openYouTubeSearch(query)
            }
            // subscrever canal
            cmd.contains("subscri") || cmd.contains("canal de") -> {
                val channel = extractQueryAfter(cmd, listOf("canal de ", "canal do ", "canal da ", "subscreve ")) ?: return "Senhor, diga o canal."
                openYouTubeChannel(channel)
            }
            // tendências / trending
            cmd.contains("tendência") || cmd.contains("trending") || cmd.contains("popular") -> {
                openYouTubeTrending()
            }
            // minha subscrições
            cmd.contains("subscri") && cmd.contains("minh") -> {
                openYouTubeSubscriptions()
            }
            // histórico
            cmd.contains("histórico") || cmd.contains("historial") -> {
                openYouTubeHistory()
            }
            // pause / pausa
            cmd.contains("paus") || cmd.contains("para o vídeo") -> {
                pressMediaButton(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "Senhor, vídeo pausado."
            }
            // próximo vídeo
            cmd.contains("próximo") || cmd.contains("avançar") -> {
                pressMediaButton(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                "Senhor, próximo vídeo."
            }
            // abre YouTube
            else -> {
                launchApp(pkg, "YouTube")
            }
        }
    }

    private fun openYouTubeSearch(query: String): String {
        return try {
            // Deep link nativo do YouTube para pesquisa
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra(SearchManager.QUERY, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
            } else {
                // Fallback: URI de pesquisa
                val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            }
            "Senhor, a pesquisar '$query' no YouTube."
        } catch (e: Exception) {
            val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            "Senhor, a abrir pesquisa de '$query' no YouTube."
        }
    }

    private fun openYouTubeChannel(channel: String): String {
        val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(channel)}+canal")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.youtube"); flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return "Senhor, a procurar o canal '$channel' no YouTube."
    }

    private fun openYouTubeTrending(): String {
        val uri = Uri.parse("https://www.youtube.com/feed/trending")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.youtube"); flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return "Senhor, a abrir vídeos em tendência no YouTube."
    }

    private fun openYouTubeSubscriptions(): String {
        val uri = Uri.parse("https://www.youtube.com/feed/subscriptions")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.youtube"); flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return "Senhor, a abrir as suas subscrições."
    }

    private fun openYouTubeHistory(): String {
        val uri = Uri.parse("https://www.youtube.com/feed/history")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.youtube"); flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return "Senhor, a abrir o histórico do YouTube."
    }

    // ─────────────────────────────────────────────────────────────
    // SPOTIFY
    // ─────────────────────────────────────────────────────────────

    private fun isSpotifyCommand(cmd: String) = cmd.contains("spotify") ||
        ((cmd.contains("música") || cmd.contains("musica") || cmd.contains("artista") || cmd.contains("playlist") || cmd.contains("álbum")) &&
         !cmd.contains("youtube") && !cmd.contains("tiktok"))

    private suspend fun handleSpotify(cmd: String): String {
        val pkg = "com.spotify.music"
        return when {
            // Toca artista/música
            cmd.contains("toca ") || cmd.contains("play ") || cmd.contains("reproduz") || cmd.contains("coloca") -> {
                val query = extractMediaQuery(cmd, listOf("toca ", "play ", "reproduz ", "coloca "), listOf(" no spotify", " spotify"))
                    ?: return "Senhor, diga o que ouvir. Ex: 'toca Drake no Spotify'."
                openSpotifySearch(query)
            }
            // Pesquisa
            cmd.contains("pesquis") || cmd.contains("procur") -> {
                val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura "))
                    ?: return "Senhor, diga o que pesquisar."
                openSpotifySearch(query)
            }
            // Pausa
            cmd.contains("paus") || cmd.contains("para a música") || cmd.contains("para o spotify") -> {
                pressMediaButton(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "Senhor, música pausada."
            }
            // Continua
            cmd.contains("continua") || cmd.contains("resume") -> {
                pressMediaButton(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
                "Senhor, a continuar a música."
            }
            // Próxima
            cmd.contains("próxima") || cmd.contains("próximo") || cmd.contains("skip") || cmd.contains("avança") -> {
                pressMediaButton(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                "Senhor, próxima música."
            }
            // Anterior
            cmd.contains("anterior") || cmd.contains("volta") || cmd.contains("prev") -> {
                pressMediaButton(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "Senhor, música anterior."
            }
            // Minha playlist / biblioteca
            cmd.contains("minh") && (cmd.contains("playlist") || cmd.contains("biblioteca")) -> {
                launchApp(pkg, "Spotify")
            }
            // Criar playlist
            cmd.contains("criar playlist") || cmd.contains("nova playlist") -> {
                val name = extractQueryAfter(cmd, listOf("criar playlist ", "nova playlist ", "cria playlist ", "playlist chamada ")) ?: "Aura Playlist"
                openSpotifyCreatePlaylist(name)
            }
            // Descobrir / recomendações
            cmd.contains("descobre") || cmd.contains("recomend") || cmd.contains("suger") -> {
                val uri = Uri.parse("spotify://discover")
                launchWithFallback(uri, pkg, "Spotify")
            }
            else -> launchApp(pkg, "Spotify")
        }
    }

    private fun openSpotifySearch(query: String): String {
        return try {
            val uri = Uri.parse("spotify://search/${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
            } else {
                val webUri = Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            }
            "Senhor, a pesquisar '$query' no Spotify."
        } catch (e: Exception) {
            "Senhor, erro ao abrir Spotify: ${e.message}"
        }
    }

    private fun openSpotifyCreatePlaylist(name: String): String {
        launchApp("com.spotify.music", "Spotify")
        return "Senhor, Spotify aberto. Para criar a playlist '$name', toque em 'A sua biblioteca' → '+'. Posso automatizar esta etapa se ativar a acessibilidade completa."
    }

    // ─────────────────────────────────────────────────────────────
    // WHATSAPP
    // ─────────────────────────────────────────────────────────────

    private fun isWhatsAppCommand(cmd: String) = cmd.contains("whatsapp") || cmd.contains("wpp") || cmd.contains("zap")

    private suspend fun handleWhatsApp(cmd: String): String {
        return when {
            // Envia mensagem para contacto
            cmd.contains("envia") || cmd.contains("manda") || cmd.contains("send") || cmd.contains("escreve") -> {
                val contact = extractContact(cmd)
                val message = extractMessageText(cmd, listOf("dizendo ", "diz ", "mensagem ", "texto "))
                if (contact != null) {
                    val number = resolveContactNumber(contact)
                    if (number != null) {
                        openWhatsAppChat(number, message)
                        "Senhor, a abrir conversa com $contact no WhatsApp${if (message != null) " com a mensagem pronta" else ""}."
                    } else {
                        openWhatsAppWithMessage(contact, message)
                    }
                } else {
                    "Senhor, diga para quem. Ex: 'envia mensagem para João no WhatsApp dizendo olá'."
                }
            }
            // Chama contacto
            cmd.contains("liga") || cmd.contains("chama") || cmd.contains("liga") || cmd.contains("chamada") -> {
                val contact = extractContact(cmd)
                if (contact != null) {
                    val number = resolveContactNumber(contact)
                    if (number != null) {
                        openWhatsAppCall(number)
                        "Senhor, a iniciar chamada WhatsApp para $contact."
                    } else {
                        "Senhor, não encontrei o número de $contact nos contactos."
                    }
                } else {
                    "Senhor, diga para quem ligar. Ex: 'liga para João no WhatsApp'."
                }
            }
            // Abre grupo
            cmd.contains("grupo") -> {
                launchApp("com.whatsapp", "WhatsApp")
            }
            // Abre estado / status
            cmd.contains("estado") || cmd.contains("status") -> {
                launchApp("com.whatsapp", "WhatsApp")
            }
            // Ver mensagens / caixa de entrada
            cmd.contains("ver mensagens") || cmd.contains("caixa") || cmd.contains("inbox") -> {
                launchApp("com.whatsapp", "WhatsApp")
            }
            else -> launchApp("com.whatsapp", "WhatsApp")
        }
    }

    private fun openWhatsAppChat(number: String, message: String?): String {
        val cleanNumber = number.replace("[^0-9+]".toRegex(), "")
        val uri = if (message != null) {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
        } else {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return "Chat WhatsApp aberto."
    }

    private fun openWhatsAppWithMessage(contact: String, message: String?): String {
        val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
        return "Senhor, WhatsApp aberto. Número de '$contact' não encontrado nos contactos."
    }

    private fun openWhatsAppCall(number: String): String {
        val cleanNumber = number.replace("[^0-9+]".toRegex(), "")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return "Chamada iniciada."
    }

    // ─────────────────────────────────────────────────────────────
    // TELEGRAM
    // ─────────────────────────────────────────────────────────────

    private fun isTelegramCommand(cmd: String) = cmd.contains("telegram")

    private suspend fun handleTelegram(cmd: String): String {
        return when {
            cmd.contains("envia") || cmd.contains("manda") || cmd.contains("mensagem") -> {
                val contact = extractContact(cmd)
                val message = extractMessageText(cmd, listOf("dizendo ", "diz ", "mensagem "))
                if (contact != null) {
                    val uri = Uri.parse("tg://resolve?domain=${Uri.encode(contact)}")
                    launchWithFallback(uri, "org.telegram.messenger", "Telegram")
                    "Senhor, a abrir Telegram para $contact."
                } else {
                    launchApp("org.telegram.messenger", "Telegram")
                }
            }
            cmd.contains("canal") -> {
                val channel = extractQueryAfter(cmd, listOf("canal ", "canal do ", "canal da "))
                if (channel != null) {
                    val uri = Uri.parse("tg://resolve?domain=${Uri.encode(channel)}")
                    launchWithFallback(uri, "org.telegram.messenger", "Telegram")
                    "Senhor, a abrir o canal '$channel' no Telegram."
                } else launchApp("org.telegram.messenger", "Telegram")
            }
            else -> launchApp("org.telegram.messenger", "Telegram")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // NETFLIX
    // ─────────────────────────────────────────────────────────────

    private fun isNetflixCommand(cmd: String) = cmd.contains("netflix")

    private suspend fun handleNetflix(cmd: String): String {
        val pkg = "com.netflix.mediaclient"
        return when {
            cmd.contains("pesquis") || cmd.contains("procur") || cmd.contains("search") -> {
                val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura ", "search ", "pesquisar "))
                    ?: extractQueryBefore(cmd, listOf(" no netflix", " netflix"))
                    ?: return "Senhor, diga o que procurar. Ex: 'pesquisa Stranger Things no Netflix'."
                openNetflixSearch(query)
            }
            cmd.contains("toca") || cmd.contains("reproduz") || cmd.contains("coloca") || cmd.contains("vê") -> {
                val query = extractMediaQuery(cmd, listOf("toca ", "reproduz ", "vê ", "coloca "), listOf(" no netflix", " netflix"))
                    ?: return "Senhor, diga o título. Ex: 'toca Squid Game no Netflix'."
                openNetflixSearch(query)
            }
            cmd.contains("continua") || cmd.contains("resume") -> {
                launchApp(pkg, "Netflix")
            }
            else -> launchApp(pkg, "Netflix")
        }
    }

    private fun openNetflixSearch(query: String): String {
        return try {
            val uri = Uri.parse("https://www.netflix.com/search?q=${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.netflix.mediaclient")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            }
            "Senhor, a pesquisar '$query' no Netflix."
        } catch (e: Exception) {
            launchApp("com.netflix.mediaclient", "Netflix")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CHROME / BROWSER
    // ─────────────────────────────────────────────────────────────

    private fun isBrowserCommand(cmd: String) = cmd.contains("chrome") || cmd.contains("browser") ||
        cmd.contains("navega para") || cmd.contains("abre o site") || cmd.contains("vai para") ||
        (cmd.contains("pesquisa") && !isYouTubeCommand(cmd) && !isSpotifyCommand(cmd) &&
         !isNetflixCommand(cmd) && !isPlayStoreCommand(cmd))

    private fun handleBrowser(cmd: String): String {
        return when {
            cmd.contains("pesquis") || cmd.contains("procur") || cmd.contains("googl") -> {
                val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura ", "googla ", "google ", "search "))
                    ?: return "Senhor, diga o que pesquisar."
                openBrowserSearch(query)
            }
            cmd.contains("navega para") || cmd.contains("abre o site") || cmd.contains("vai para") || cmd.contains("abre ") -> {
                val url = extractUrl(cmd)
                if (url != null) {
                    openUrl(url)
                    "Senhor, a abrir $url."
                } else {
                    val query = extractQueryAfter(cmd, listOf("navega para ", "abre o site ", "vai para ", "abre "))
                    if (query != null) openBrowserSearch(query)
                    else "Senhor, diga o endereço ou o que pesquisar."
                }
            }
            cmd.contains("nova aba") || cmd.contains("new tab") -> {
                val uri = Uri.parse("about:blank")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.android.chrome")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                "Senhor, nova aba aberta."
            }
            else -> launchApp("com.android.chrome", "Chrome")
        }
    }

    private fun openBrowserSearch(query: String): String {
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return "Senhor, a pesquisar '$query' no Google."
    }

    private fun openUrl(url: String): String {
        val fullUrl = if (url.startsWith("http")) url else "https://$url"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return "Senhor, a abrir $fullUrl."
    }

    // ─────────────────────────────────────────────────────────────
    // GOOGLE MAPS
    // ─────────────────────────────────────────────────────────────

    private fun isMapsCommand(cmd: String) = cmd.contains("maps") || cmd.contains("navega para") ||
        cmd.contains("direcções") || cmd.contains("direções") || cmd.contains("rota para") ||
        cmd.contains("perto de mim") || cmd.contains("restaurante") || cmd.contains("posto de gasolina") ||
        cmd.contains("hospital") || cmd.contains("farmácia") || cmd.contains("banco") || cmd.contains("caixa multibanco")

    private fun handleMaps(cmd: String): String {
        return when {
            cmd.contains("perto de mim") || cmd.contains("perto") -> {
                val place = extractQueryBefore(cmd, listOf(" perto de mim", " perto", " aqui"))
                    ?: extractQueryAfter(cmd, listOf("procura ", "encontra ", "onde há "))
                    ?: return "Senhor, diga o que procurar. Ex: 'farmácia perto de mim'."
                val uri = Uri.parse("geo:0,0?q=${Uri.encode(place)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                "Senhor, a procurar '$place' perto da sua localização."
            }
            cmd.contains("navega para") || cmd.contains("rota para") || cmd.contains("direcções para") || cmd.contains("direções para") -> {
                val destination = extractQueryAfter(cmd, listOf("navega para ", "rota para ", "direcções para ", "direções para ", "como ir para "))
                    ?: return "Senhor, diga o destino."
                val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=d")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                "Senhor, a iniciar navegação para **$destination**."
            }
            cmd.contains("pesquis") || cmd.contains("procur") -> {
                val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura ")) ?: return "Senhor, diga o que procurar."
                val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                "Senhor, a pesquisar '$query' no Google Maps."
            }
            cmd.contains("partilha localização") || cmd.contains("partilhar localização") -> {
                launchApp("com.google.android.apps.maps", "Google Maps")
            }
            else -> launchApp("com.google.android.apps.maps", "Google Maps")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PLAY STORE
    // ─────────────────────────────────────────────────────────────

    private fun isPlayStoreCommand(cmd: String) = cmd.contains("play store") || cmd.contains("instala ") ||
        cmd.contains("descarrega a app") || cmd.contains("baixa a app")

    private fun handlePlayStore(cmd: String): String {
        return when {
            cmd.contains("pesquis") || cmd.contains("procur") || cmd.contains("instala ") || cmd.contains("baixa") || cmd.contains("descarrega") -> {
                val query = extractQueryAfter(cmd, listOf("pesquisa ", "instala ", "instalar ", "baixa ", "descarrega ", "procura "))
                    ?: extractQueryBefore(cmd, listOf(" na play store", " play store"))
                    ?: return "Senhor, diga o nome da app. Ex: 'instala Instagram'."
                val uri = Uri.parse("market://search?q=${Uri.encode(query)}")
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                } catch (e: Exception) {
                    val webUri = Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}&c=apps")
                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                }
                "Senhor, a pesquisar '$query' na Play Store."
            }
            else -> launchApp("com.android.vending", "Play Store")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CÂMARA
    // ─────────────────────────────────────────────────────────────

    private fun isCameraCommand(cmd: String) = cmd.contains("câmara") || cmd.contains("câmera") ||
        cmd.contains("foto") || cmd.contains("fotografia") || cmd.contains("tira uma foto") ||
        cmd.contains("selfie") || cmd.contains("grava um vídeo") || cmd.contains("filma")

    private fun handleCamera(cmd: String): String {
        return when {
            cmd.contains("selfie") || cmd.contains("frontal") -> {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra("android.intent.extras.CAMERA_FACING", android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "Senhor, a abrir câmara frontal para selfie."
            }
            cmd.contains("tira uma foto") || cmd.contains("tirar foto") || cmd.contains("foto") || cmd.contains("fotografia") -> {
                context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                "Senhor, a abrir câmara para tirar foto."
            }
            cmd.contains("grava") || cmd.contains("filma") || cmd.contains("vídeo") -> {
                context.startActivity(Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                "Senhor, a abrir câmara para gravar vídeo."
            }
            cmd.contains("qr") || cmd.contains("código qr") || cmd.contains("qrcode") -> {
                val uri = Uri.parse("zxing://scan/")
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    "Senhor, a abrir leitor de QR Code."
                } catch (e: Exception) {
                    context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    "Senhor, a abrir câmara. Use o modo de QR Code integrado."
                }
            }
            else -> {
                context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                "Senhor, câmara aberta."
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GALERIA / FOTOS
    // ─────────────────────────────────────────────────────────────

    private fun isGalleryCommand(cmd: String) = cmd.contains("galeria") || cmd.contains("gallery") ||
        (cmd.contains("ver") && cmd.contains("foto"))

    private fun handleGallery(cmd: String): String {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Senhor, a abrir galeria de fotos."
    }

    // ─────────────────────────────────────────────────────────────
    // ALARME / TIMER / RELÓGIO
    // ─────────────────────────────────────────────────────────────

    private fun isAlarmCommand(cmd: String) = cmd.contains("alarme") || cmd.contains("timer") ||
        cmd.contains("temporizador") || cmd.contains("cronômetro") || cmd.contains("cronometro") ||
        (cmd.contains("acorda") && cmd.contains("às")) || cmd.contains("lembra-me")

    private fun handleAlarm(cmd: String): String {
        return when {
            // Definir alarme: "alarme às 7 da manhã"
            cmd.contains("alarme") && !cmd.contains("timer") -> {
                val time = extractTime(cmd)
                val message = extractQueryAfter(cmd, listOf("com mensagem ", "dizendo ", "para ")) ?: "Aura"
                if (time != null) {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, time.first)
                        putExtra(AlarmClock.EXTRA_MINUTES, time.second)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    "Senhor, alarme definido para as ${time.first}:${String.format("%02d", time.second)}."
                } else {
                    "Senhor, diga a hora do alarme. Ex: 'alarme às 7 da manhã'."
                }
            }
            // Timer: "timer de 10 minutos"
            cmd.contains("timer") || cmd.contains("temporizador") -> {
                val minutes = extractNumber(cmd)
                if (minutes != null) {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Timer Aura")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    "Senhor, timer de **$minutes minutos** iniciado."
                } else {
                    "Senhor, diga a duração. Ex: 'timer de 10 minutos'."
                }
            }
            // Cronómetro
            cmd.contains("cronômetro") || cmd.contains("cronometro") -> {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, 0)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "Senhor, a abrir cronómetro."
            }
            // Lembrete
            cmd.contains("lembra-me") || cmd.contains("lembra me") -> {
                val time = extractTime(cmd)
                val what = extractQueryAfter(cmd, listOf("lembra-me de ", "lembra me de ", "lembra-me para ")) ?: "Tarefa"
                if (time != null) {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, time.first)
                        putExtra(AlarmClock.EXTRA_MINUTES, time.second)
                        putExtra(AlarmClock.EXTRA_MESSAGE, what)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    "Senhor, lembrete '$what' às ${time.first}:${String.format("%02d", time.second)}."
                } else {
                    "Senhor, diga a hora. Ex: 'lembra-me de tomar medicamento às 8 da manhã'."
                }
            }
            else -> {
                context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                    setPackage("com.google.android.deskclock")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                "Senhor, relógio aberto."
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CALCULADORA
    // ─────────────────────────────────────────────────────────────

    private fun isCalculatorCommand(cmd: String) = cmd.contains("calcul") || cmd.contains("quanto é") ||
        cmd.contains("quanto dá") || cmd.contains("soma ") || cmd.contains("multiplica ") ||
        cmd.contains("divide ") || cmd.contains("subtrai ") || cmd.contains("raiz") ||
        cmd.contains("percentagem") || cmd.contains("% de")

    private fun handleCalculator(cmd: String): String {
        // Tenta calcular localmente primeiro
        val result = tryCalculateLocally(cmd)
        if (result != null) return "Senhor, o resultado é **$result**."

        // Abre a calculadora
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.calculator")
                ?: context.packageManager.getLaunchIntentForPackage("com.android.calculator2")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return "Senhor, calculadora aberta."
            }
        } catch (_: Exception) {}
        return "Senhor, não encontrei a calculadora."
    }

    private fun tryCalculateLocally(cmd: String): String? {
        return try {
            // Percentagem: "20% de 500"
            val percentRegex = Regex("(\\d+(?:[.,]\\d+)?)%\\s*de\\s*(\\d+(?:[.,]\\d+)?)")
            val percentMatch = percentRegex.find(cmd)
            if (percentMatch != null) {
                val pct = percentMatch.groupValues[1].replace(",", ".").toDouble()
                val base = percentMatch.groupValues[2].replace(",", ".").toDouble()
                return String.format("%.2f", pct / 100 * base)
            }
            // Operações simples
            val opRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s*([+\\-×x*÷/])\\s*(\\d+(?:[.,]\\d+)?)")
            val opMatch = opRegex.find(cmd)
            if (opMatch != null) {
                val a = opMatch.groupValues[1].replace(",", ".").toDouble()
                val op = opMatch.groupValues[2]
                val b = opMatch.groupValues[3].replace(",", ".").toDouble()
                val result = when (op) {
                    "+", "mais" -> a + b
                    "-", "menos" -> a - b
                    "×", "x", "*", "vezes" -> a * b
                    "÷", "/", "a dividir por" -> if (b != 0.0) a / b else return "Erro: divisão por zero"
                    else -> return null
                }
                return if (result == result.toLong().toDouble()) result.toLong().toString() else String.format("%.4f", result).trimEnd('0').trimEnd('.')
            }
            null
        } catch (_: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────
    // CONTACTOS
    // ─────────────────────────────────────────────────────────────

    private fun isContactsCommand(cmd: String) = cmd.contains("contacto") || cmd.contains("contato") ||
        (cmd.contains("número") && !cmd.contains("alarme"))

    private fun handleContacts(cmd: String): String {
        return when {
            cmd.contains("pesquis") || cmd.contains("procur") || cmd.contains("número de") -> {
                val name = extractQueryAfter(cmd, listOf("número de ", "contacto de ", "pesquisa contacto ", "procura contacto "))
                    ?: return "Senhor, diga o nome. Ex: 'qual o número de João'."
                val number = resolveContactNumber(name)
                if (number != null) {
                    "Senhor, o número de **$name** é: **$number**."
                } else {
                    "Senhor, não encontrei '$name' nos contactos."
                }
            }
            cmd.contains("adiciona") || cmd.contains("cria contacto") || cmd.contains("novo contacto") -> {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "Senhor, a abrir criação de novo contacto."
            }
            else -> {
                val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "Senhor, contactos abertos."
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CHAMADAS
    // ─────────────────────────────────────────────────────────────

    private fun isCallCommand(cmd: String) = (cmd.contains("liga para") || cmd.contains("ligar para") || cmd.contains("chama ")) &&
        !cmd.contains("whatsapp") && !cmd.contains("telegram")

    private fun handleCall(cmd: String): String {
        val contact = extractQueryAfter(cmd, listOf("liga para ", "ligar para ", "chama ", "chamar "))
            ?: return "Senhor, diga para quem ligar."
        val number = resolveContactNumber(contact)
        return if (number != null) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            "Senhor, a ligar para **$contact** ($number)."
        } else if (contact.matches(Regex("[0-9+]+"))) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$contact")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            "Senhor, a ligar para $contact."
        } else {
            "Senhor, não encontrei o número de '$contact' nos contactos."
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SMS
    // ─────────────────────────────────────────────────────────────

    private fun isSmsCommand(cmd: String) = cmd.contains("sms") || (cmd.contains("mensagem de texto") && !cmd.contains("whatsapp"))

    private fun handleSms(cmd: String): String {
        val contact = extractContact(cmd) ?: return "Senhor, diga o destinatário. Ex: 'envia sms para João dizendo olá'."
        val message = extractMessageText(cmd, listOf("dizendo ", "diz ", "texto ")) ?: ""
        val number = resolveContactNumber(contact) ?: contact
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Senhor, a abrir SMS para $contact${if (message.isNotBlank()) " com a mensagem pronta" else ""}."
    }

    // ─────────────────────────────────────────────────────────────
    // GMAIL
    // ─────────────────────────────────────────────────────────────

    private fun isGmailCommand(cmd: String) = cmd.contains("gmail") || cmd.contains("email") || cmd.contains("e-mail")

    private fun handleGmail(cmd: String): String {
        return when {
            cmd.contains("compõe") || cmd.contains("escreve") || cmd.contains("envia") || cmd.contains("cria") -> {
                val to = extractContact(cmd)
                val subject = extractQueryAfter(cmd, listOf("sobre ", "assunto "))
                val body = extractQueryAfter(cmd, listOf("dizendo ", "com texto ", "mensagem "))
                val toEmail = if (to != null) resolveContactEmail(to) ?: "" else ""
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    if (toEmail.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
                    if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
                    if (body != null) putExtra(Intent.EXTRA_TEXT, body)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "Senhor, a compor email${if (to != null) " para $to" else ""}${if (subject != null) " sobre '$subject'" else ""}."
            }
            cmd.contains("caixa de entrada") || cmd.contains("inbox") || cmd.contains("ver emails") -> {
                launchApp("com.google.android.gm", "Gmail")
            }
            else -> launchApp("com.google.android.gm", "Gmail")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GOOGLE DRIVE
    // ─────────────────────────────────────────────────────────────

    private fun isDriveCommand(cmd: String) = cmd.contains("drive") || cmd.contains("google drive")

    private fun handleGoogleDrive(cmd: String): String {
        return when {
            cmd.contains("pesquis") || cmd.contains("procur") -> {
                val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura ")) ?: return "Senhor, diga o que procurar."
                val uri = Uri.parse("https://drive.google.com/drive/search?q=${Uri.encode(query)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                "Senhor, a pesquisar '$query' no Google Drive."
            }
            cmd.contains("upload") || cmd.contains("carrega") || cmd.contains("guarda no drive") -> {
                launchApp("com.google.android.apps.docs", "Google Drive")
            }
            else -> launchApp("com.google.android.apps.docs", "Google Drive")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GOOGLE FOTOS
    // ─────────────────────────────────────────────────────────────

    private fun isPhotosCommand(cmd: String) = cmd.contains("google fotos") || cmd.contains("google photos")

    private fun handleGooglePhotos(cmd: String): String {
        return when {
            cmd.contains("pesquis") -> {
                val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura ")) ?: return "Senhor, diga o que procurar."
                val uri = Uri.parse("https://photos.google.com/search/${Uri.encode(query)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                "Senhor, a pesquisar '$query' no Google Fotos."
            }
            else -> launchApp("com.google.android.apps.photos", "Google Fotos")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // AMAZON
    // ─────────────────────────────────────────────────────────────

    private fun isAmazonCommand(cmd: String) = cmd.contains("amazon")

    private fun handleAmazon(cmd: String): String {
        val query = extractQueryAfter(cmd, listOf("pesquisa ", "compra ", "procura ", "search "))
            ?: extractQueryBefore(cmd, listOf(" na amazon", " amazon"))
        return if (query != null) {
            val uri = Uri.parse("https://www.amazon.com/s?k=${Uri.encode(query)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            "Senhor, a pesquisar '$query' na Amazon."
        } else {
            launchApp("com.amazon.mShop.android.shopping", "Amazon")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // TWITTER / X
    // ─────────────────────────────────────────────────────────────

    private fun isTwitterCommand(cmd: String) = cmd.contains("twitter") || cmd.contains(" x ") || cmd.contains("tweet")

    private fun handleTwitter(cmd: String): String {
        return when {
            cmd.contains("pesquis") || cmd.contains("procur") -> {
                val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura ")) ?: return "Senhor, diga o que pesquisar."
                val uri = Uri.parse("twitter://search?query=${Uri.encode(query)}")
                launchWithFallback(uri, "com.twitter.android", "Twitter/X")
                "Senhor, a pesquisar '$query' no Twitter."
            }
            cmd.contains("twittar") || cmd.contains("publicar") || cmd.contains("tweet") -> {
                val text = extractQueryAfter(cmd, listOf("twittar ", "publicar ", "tweet ")) ?: ""
                val uri = Uri.parse("twitter://post?message=${Uri.encode(text)}")
                launchWithFallback(uri, "com.twitter.android", "Twitter/X")
                "Senhor, a abrir criação de tweet."
            }
            else -> launchApp("com.twitter.android", "Twitter/X")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // REDDIT
    // ─────────────────────────────────────────────────────────────

    private fun isRedditCommand(cmd: String) = cmd.contains("reddit")

    private fun handleReddit(cmd: String): String {
        val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura ", "r/", "subreddit "))
        return if (query != null) {
            val uri = Uri.parse("https://www.reddit.com/search/?q=${Uri.encode(query)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            "Senhor, a pesquisar '$query' no Reddit."
        } else {
            launchApp("com.reddit.frontpage", "Reddit")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // WIKIPEDIA
    // ─────────────────────────────────────────────────────────────

    private fun isWikipediaCommand(cmd: String) = cmd.contains("wikipedia") || cmd.contains("wikipédia") ||
        cmd.contains("o que é ") || cmd.contains("quem é ")

    private fun handleWikipedia(cmd: String): String {
        val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura ", "o que é ", "quem é ", "wikipedia sobre "))
            ?: return launchApp("org.wikipedia", "Wikipedia")
        val uri = Uri.parse("https://pt.wikipedia.org/wiki/Special:Search?search=${Uri.encode(query)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        return "Senhor, a pesquisar '$query' na Wikipedia."
    }

    // ─────────────────────────────────────────────────────────────
    // GITHUB
    // ─────────────────────────────────────────────────────────────

    private fun isGithubCommand(cmd: String) = cmd.contains("github")

    private fun handleGitHub(cmd: String): String {
        val query = extractQueryAfter(cmd, listOf("pesquisa ", "procura ", "repositório "))
        return if (query != null) {
            val uri = Uri.parse("https://github.com/search?q=${Uri.encode(query)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            "Senhor, a pesquisar '$query' no GitHub."
        } else {
            launchApp("com.github.android", "GitHub")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SHAZAM
    // ─────────────────────────────────────────────────────────────

    private fun isShazamCommand(cmd: String) = cmd.contains("shazam") || cmd.contains("que música é esta") ||
        cmd.contains("identifica esta música") || cmd.contains("qual é esta música")

    private fun handleShazam(cmd: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("com.shazam.android")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                "Senhor, Shazam aberto. A escutar a música..."
            } else {
                // Usar a pesquisa do Spotify como alternativa
                openSpotifySearch("identifique música")
                "Senhor, Shazam não instalado. A abrir Spotify."
            }
        } catch (e: Exception) {
            "Senhor, erro ao abrir Shazam: ${e.message}"
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITÁRIOS PRIVADOS
    // ─────────────────────────────────────────────────────────────

    private fun launchApp(packageName: String, appName: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                "Senhor, $appName aberto."
            } else {
                val storeUri = Uri.parse("market://details?id=$packageName")
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, storeUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                } catch (_: Exception) {
                    val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                }
                "Senhor, $appName não está instalado. A abrir Play Store para instalar."
            }
        } catch (e: Exception) {
            "Senhor, erro ao abrir $appName: ${e.message}"
        }
    }

    private fun launchWithFallback(uri: Uri, packageName: String, appName: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                "$appName aberto."
            } else {
                launchApp(packageName, appName)
            }
        } catch (e: Exception) {
            launchApp(packageName, appName)
        }
    }

    private fun pressMediaButton(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        am.dispatchMediaKeyEvent(downEvent)
        am.dispatchMediaKeyEvent(upEvent)
    }

    private fun resolveContactNumber(name: String): String? {
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"), null
            )
            cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) { null }
    }

    private fun resolveContactEmail(name: String): String? {
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY} LIKE ?",
                arrayOf("%$name%"), null
            )
            cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) { null }
    }

    private fun extractQueryAfter(cmd: String, keywords: List<String>): String? {
        for (kw in keywords) {
            val idx = cmd.indexOf(kw, ignoreCase = true)
            if (idx != -1) {
                val after = cmd.substring(idx + kw.length).trim()
                // Remove trailing app names
                val clean = after.replace(Regex("\\s+(no|na|no|em|do|da)\\s+(youtube|spotify|netflix|whatsapp|telegram|gmail|drive|maps|chrome|twitter|instagram|tiktok|facebook|linkedin|reddit|wikipedia|github|amazon|play store|play|store)$", RegexOption.IGNORE_CASE), "").trim()
                if (clean.isNotBlank()) return clean
            }
        }
        return null
    }

    private fun extractQueryBefore(cmd: String, keywords: List<String>): String? {
        for (kw in keywords) {
            val idx = cmd.indexOf(kw, ignoreCase = true)
            if (idx != -1) {
                val before = cmd.substring(0, idx).trim()
                val appWords = listOf("pesquisa", "pesquisar", "procura", "toca", "reproduz", "play", "coloca", "vê", "ver", "abre", "abrir")
                var clean = before
                for (aw in appWords) clean = clean.replace(Regex("^$aw\\s*", RegexOption.IGNORE_CASE), "").trim()
                if (clean.isNotBlank()) return clean
            }
        }
        return null
    }

    private fun extractMediaQuery(cmd: String, startKeywords: List<String>, endKeywords: List<String>): String? {
        for (start in startKeywords) {
            val startIdx = cmd.indexOf(start, ignoreCase = true)
            if (startIdx != -1) {
                var result = cmd.substring(startIdx + start.length).trim()
                for (end in endKeywords) {
                    val endIdx = result.indexOf(end, ignoreCase = true)
                    if (endIdx != -1) result = result.substring(0, endIdx).trim()
                }
                if (result.isNotBlank()) return result
            }
        }
        return null
    }

    private fun extractContact(cmd: String): String? {
        val patterns = listOf("para ", "ao ", "à ", "de ", "com ", "o ", "a ")
        for (p in patterns) {
            val after = extractQueryAfter(cmd, listOf(p)) ?: continue
            val word = after.split(" ").firstOrNull()?.replace(",", "")?.replace(".", "")
            if (!word.isNullOrBlank() && word.length > 1 && !word.matches(Regex("[0-9]+"))) {
                // Skip app names
                val appNames = setOf("youtube", "spotify", "netflix", "whatsapp", "telegram", "gmail", "drive", "maps", "chrome", "twitter", "instagram", "tiktok", "facebook", "linkedin", "reddit")
                if (word.lowercase() !in appNames) return word.capitalize()
            }
        }
        return null
    }

    private fun extractMessageText(cmd: String, keywords: List<String>): String? {
        for (kw in keywords) {
            val idx = cmd.indexOf(kw, ignoreCase = true)
            if (idx != -1) return cmd.substring(idx + kw.length).trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun extractUrl(cmd: String): String? {
        val urlRegex = Regex("(https?://[^\\s]+|[a-z0-9-]+\\.(com|net|org|io|co|ao|pt|br)[^\\s]*)")
        return urlRegex.find(cmd)?.value
    }

    private fun extractTime(cmd: String): Pair<Int, Int>? {
        val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(?:h|horas?|da manhã|da tarde|da noite|am|pm)?")
        val match = timeRegex.find(cmd) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if ((cmd.contains("tarde") || cmd.contains("pm")) && hour < 12) hour += 12
        if (cmd.contains("meia-noite") || cmd.contains("meia noite")) { return Pair(0, 0) }
        if (cmd.contains("meio-dia") || cmd.contains("meio dia")) { return Pair(12, 0) }
        return Pair(hour, minute)
    }

    private fun extractNumber(cmd: String): Int? {
        val regex = Regex("(\\d+)")
        return regex.find(cmd)?.value?.toIntOrNull()
    }
}
