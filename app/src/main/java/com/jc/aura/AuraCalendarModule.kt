package com.jc.aura

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AuraCalendarModule(private val context: Context, private val memory: AuraMemory) {

    private val dateFormats = listOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
    )

    suspend fun handle(command: String): String {
        return when {
            command.contains("adicionar evento") || command.contains("criar evento") || command.contains("agendar") || command.contains("marcar reunião") -> addEvent(command)
            command.contains("eventos") || command.contains("agenda") || command.contains("compromissos") || command.contains("o que tenho") -> getEvents(command)
            command.contains("próximos eventos") || command.contains("próximas reuniões") -> getUpcomingEvents()
            command.contains("apagar evento") || command.contains("cancelar evento") -> "Senhor, para cancelar um evento, abra o Calendário e elimine manualmente."
            else -> "Senhor, comandos de calendário: 'agendar reunião amanhã às 15h', 'próximos eventos', 'o que tenho hoje'."
        }
    }

    private suspend fun addEvent(command: String): String = withContext(Dispatchers.IO) {
        val title = extractEventTitle(command) ?: "Evento Aura"
        val date = extractDateTime(command) ?: Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }.time

        val calendarId = getFirstCalendarId()
        if (calendarId == -1L) {
            // Fallback: abrir Calendar app
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, date.time)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, date.time + 3600000)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return@withContext "Senhor, a abrir o Calendário para criar o evento '$title'."
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, date.time)
            put(CalendarContract.Events.DTEND, date.time + 3600000)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.DESCRIPTION, "Criado pela Aura AGI - J&C Trading")
        }

        return@withContext try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                val dateStr = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(date)
                memory.saveFactual("calendar_event_${System.currentTimeMillis()}", "$title - $dateStr")
                "Senhor, evento **'$title'** agendado para $dateStr."
            } else {
                "Senhor, não consegui criar o evento. Verifique as permissões de calendário."
            }
        } catch (e: SecurityException) {
            "Senhor, preciso de permissão para aceder ao calendário. Por favor, conceda acesso nas definições."
        } catch (e: Exception) {
            "Senhor, erro ao criar evento: ${e.message}"
        }
    }

    private suspend fun getEvents(command: String): String = withContext(Dispatchers.IO) {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }
        val endOfDay = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }

        val isToday = command.contains("hoje") || command.contains("today")
        val isTomorrow = command.contains("amanhã") || command.contains("tomorrow")

        if (isTomorrow) {
            today.add(Calendar.DAY_OF_YEAR, 1)
            endOfDay.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(today.timeInMillis.toString(), endOfDay.timeInMillis.toString()),
                CalendarContract.Events.DTSTART + " ASC"
            )

            val events = mutableListOf<String>()
            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: "Sem título"
                    val start = Date(it.getLong(1))
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(start)
                    events.add("• **$title** às $timeStr")
                }
            }

            val dayLabel = when { isTomorrow -> "amanhã"; isToday -> "hoje"; else -> "hoje" }
            if (events.isEmpty()) "Senhor, não tem eventos para $dayLabel."
            else "Senhor, os seus eventos para $dayLabel:\n\n${events.joinToString("\n")}"
        } catch (e: SecurityException) {
            "Senhor, preciso de permissão para ver o calendário."
        }
    }

    private suspend fun getUpcomingEvents(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val weekLater = now + (7 * 24 * 60 * 60 * 1000L)
        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(now.toString(), weekLater.toString()),
                CalendarContract.Events.DTSTART + " ASC LIMIT 10"
            )
            val events = mutableListOf<String>()
            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: "Sem título"
                    val start = Date(it.getLong(1))
                    val dateStr = SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(start)
                    events.add("• **$title** — $dateStr")
                }
            }
            if (events.isEmpty()) "Senhor, nenhum evento nos próximos 7 dias."
            else "Senhor, próximos eventos:\n\n${events.joinToString("\n")}"
        } catch (e: SecurityException) {
            "Senhor, preciso de permissão para ver o calendário."
        }
    }

    private fun getFirstCalendarId(): Long {
        return try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                null, null, null
            )
            cursor?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L
        } catch (_: Exception) { -1L }
    }

    private fun extractEventTitle(command: String): String? {
        val keywords = listOf("agendar ", "criar evento ", "adicionar evento ", "marcar reunião ")
        for (kw in keywords) {
            val idx = command.indexOf(kw, ignoreCase = true)
            if (idx != -1) {
                val after = command.substring(idx + kw.length)
                val dateKeywords = listOf(" amanhã", " hoje", " às", " dia ", " em ")
                var end = after.length
                for (dk in dateKeywords) {
                    val di = after.indexOf(dk, ignoreCase = true)
                    if (di != -1 && di < end) end = di
                }
                val title = after.substring(0, end).trim()
                if (title.isNotBlank()) return title
            }
        }
        return null
    }

    private fun extractDateTime(command: String): Date? {
        val cal = Calendar.getInstance()
        return when {
            command.contains("amanhã") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                extractTime(command, cal)
                cal.time
            }
            command.contains("hoje") -> {
                extractTime(command, cal)
                cal.time
            }
            command.contains("segunda") -> { cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); if (cal.before(Calendar.getInstance())) cal.add(Calendar.WEEK_OF_YEAR, 1); extractTime(command, cal); cal.time }
            command.contains("terça") -> { cal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY); if (cal.before(Calendar.getInstance())) cal.add(Calendar.WEEK_OF_YEAR, 1); extractTime(command, cal); cal.time }
            command.contains("quarta") -> { cal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY); if (cal.before(Calendar.getInstance())) cal.add(Calendar.WEEK_OF_YEAR, 1); extractTime(command, cal); cal.time }
            command.contains("quinta") -> { cal.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY); if (cal.before(Calendar.getInstance())) cal.add(Calendar.WEEK_OF_YEAR, 1); extractTime(command, cal); cal.time }
            command.contains("sexta") -> { cal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY); if (cal.before(Calendar.getInstance())) cal.add(Calendar.WEEK_OF_YEAR, 1); extractTime(command, cal); cal.time }
            else -> null
        }
    }

    private fun extractTime(command: String, cal: Calendar) {
        val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(?:h|horas?|:)")
        val match = timeRegex.find(command)
        if (match != null) {
            cal.set(Calendar.HOUR_OF_DAY, match.groupValues[1].toInt())
            cal.set(Calendar.MINUTE, match.groupValues[2].toIntOrNull() ?: 0)
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
        }
        cal.set(Calendar.SECOND, 0)
    }
}
