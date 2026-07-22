package com.jc.aura

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AuraFileAndSystemModule(private val context: Context, private val memory: AuraMemory) {

    private val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

    suspend fun handle(command: String): String {
        return when {
            command.contains("listar ficheiros") || command.contains("listar arquivos") || command.contains("lista de ficheiros") -> listFiles(command)
            command.contains("apagar ficheiro") || command.contains("deletar arquivo") || command.contains("eliminar ficheiro") -> deleteFile(command)
            command.contains("criar pasta") || command.contains("nova pasta") -> createFolder(command)
            command.contains("abrir ficheiro") || command.contains("abrir arquivo") -> openFile(command)
            command.contains("procurar ficheiro") || command.contains("procurar arquivo") || command.contains("encontrar ficheiro") -> searchFile(command)
            command.contains("espaço") || command.contains("armazenamento") -> getStorageSummary()
            command.contains("criar nota") || command.contains("escrever nota") || command.contains("salvar nota") -> saveNote(command)
            command.contains("ler nota") || command.contains("notas") -> readNotes()
            else -> "Senhor, comandos de ficheiros: 'listar ficheiros', 'criar pasta [nome]', 'procurar ficheiro [nome]', 'criar nota [texto]', 'ler notas', 'espaço disponível'."
        }
    }

    private suspend fun listFiles(command: String): String = withContext(Dispatchers.IO) {
        val dir = when {
            command.contains("download") || command.contains("transferência") -> downloadsDir
            command.contains("document") -> documentsDir
            else -> downloadsDir
        }
        val files = dir.listFiles() ?: return@withContext "Senhor, não consigo aceder à pasta."
        if (files.isEmpty()) return@withContext "Senhor, a pasta ${dir.name} está vazia."
        val sorted = files.sortedByDescending { it.lastModified() }.take(15)
        val sb = StringBuilder("Senhor, ficheiros em **${dir.name}** (${files.size} total):\n\n")
        sorted.forEachIndexed { i, f ->
            val size = if (f.isDirectory) "pasta" else formatSize(f.length())
            val date = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
            sb.append("${i + 1}. **${f.name}** ($size) - $date\n")
        }
        sb.toString()
    }

    private suspend fun deleteFile(command: String): String = withContext(Dispatchers.IO) {
        val name = extractFileName(command) ?: return@withContext "Senhor, diga o nome do ficheiro a apagar."
        val candidates = listOf(downloadsDir, documentsDir).flatMap { dir ->
            (dir.listFiles() ?: emptyArray()).filter { it.name.contains(name, ignoreCase = true) }
        }
        if (candidates.isEmpty()) return@withContext "Senhor, ficheiro '$name' não encontrado."
        val file = candidates.first()
        return@withContext if (file.delete()) "Senhor, ficheiro **${file.name}** apagado."
        else "Senhor, não consegui apagar **${file.name}**."
    }

    private fun createFolder(command: String): String {
        val name = extractAfterKeyword(command, listOf("criar pasta ", "nova pasta ")) ?: return "Senhor, diga o nome da pasta."
        val folder = File(downloadsDir, name)
        return if (folder.mkdirs()) "Senhor, pasta **$name** criada em Downloads."
        else "Senhor, não consegui criar a pasta. Pode já existir."
    }

    private fun openFile(command: String): String {
        val name = extractFileName(command) ?: return "Senhor, diga o nome do ficheiro a abrir."
        val candidates = (downloadsDir.listFiles() ?: emptyArray()).filter { it.name.contains(name, ignoreCase = true) }
        val file = candidates.firstOrNull() ?: return "Senhor, ficheiro '$name' não encontrado em Downloads."
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            "Senhor, a abrir **${file.name}**."
        } catch (e: Exception) {
            "Senhor, não encontrei app para abrir este tipo de ficheiro."
        }
    }

    private suspend fun searchFile(command: String): String = withContext(Dispatchers.IO) {
        val query = extractAfterKeyword(command, listOf("procurar ficheiro ", "procurar arquivo ", "encontrar ficheiro ", "encontrar arquivo ")) ?: return@withContext "Senhor, diga o que procurar."
        val results = mutableListOf<File>()
        listOf(downloadsDir, documentsDir).forEach { dir ->
            dir.walkTopDown().maxDepth(3).filter { it.name.contains(query, ignoreCase = true) }.forEach { results.add(it) }
        }
        if (results.isEmpty()) return@withContext "Senhor, nenhum ficheiro encontrado com '$query'."
        val sb = StringBuilder("Senhor, encontrei **${results.size}** ficheiro(s) com '$query':\n\n")
        results.take(10).forEachIndexed { i, f -> sb.append("${i + 1}. ${f.absolutePath}\n") }
        sb.toString()
    }

    private fun getStorageSummary(): String {
        val stat = android.os.StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBytes / (1024 * 1024 * 1024)
        val total = stat.totalBytes / (1024 * 1024 * 1024)
        return "Senhor, armazenamento interno: **${total - free}GB usado** de ${total}GB. **${free}GB livres**."
    }

    private fun saveNote(command: String): String {
        val text = extractAfterKeyword(command, listOf("criar nota ", "escrever nota ", "salvar nota ", "guardar nota ")) ?: return "Senhor, diga o conteúdo da nota."
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        memory.saveFactual("nota_${System.currentTimeMillis()}", "[$timestamp] $text")
        return "Senhor, nota guardada: \"$text\""
    }

    private fun readNotes(): String {
        val notes = memory.getAllByPrefix("nota_")
        if (notes.isEmpty()) return "Senhor, não tem notas guardadas."
        val sb = StringBuilder("Senhor, aqui estão as suas notas:\n\n")
        notes.values.take(10).forEachIndexed { i, note -> sb.append("${i + 1}. $note\n") }
        return sb.toString()
    }

    private fun extractFileName(command: String): String? =
        extractAfterKeyword(command, listOf("apagar ficheiro ", "deletar arquivo ", "eliminar ficheiro ", "abrir ficheiro ", "abrir arquivo "))

    private fun extractAfterKeyword(command: String, keywords: List<String>): String? {
        for (kw in keywords) {
            val idx = command.indexOf(kw, ignoreCase = true)
            if (idx != -1) return command.substring(idx + kw.length).trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        else -> "${bytes / (1024 * 1024 * 1024)}GB"
    }

    private fun getMimeType(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "txt" -> "text/plain"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        else -> "*/*"
    }
}
