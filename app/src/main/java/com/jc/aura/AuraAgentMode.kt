package com.jc.aura

import android.content.Context
import kotlinx.coroutines.delay

/**
 * AuraAgentMode — Modo Agente Autónomo.
 * A Aura executa tarefas em sequência sozinha, sem precisar de um comando para cada passo.
 * Ex: "Aura, prospecta 50 leads no TikTok, salva em ficheiro e envia relatório no WhatsApp"
 */
class AuraAgentMode(
    private val context: Context,
    private val memory: AuraMemory,
    private val voiceService: AuraVoiceService
) {
    private var isAgentRunning = false
    private var currentTask = ""
    private val taskLog = mutableListOf<String>()

    suspend fun handle(cmd: String): String {
        return when {
            cmd.contains("modo agente") || cmd.contains("agent mode") || cmd.contains("autónomo") -> {
                activateAgentMode(cmd)
            }
            cmd.contains("parar agente") || cmd.contains("stop agent") || cmd.contains("cancelar tarefa") -> {
                stopAgent()
            }
            cmd.contains("status agente") || cmd.contains("o que estás a fazer") -> {
                getAgentStatus()
            }
            cmd.contains("executa") || cmd.contains("faz isto") || cmd.contains("sequência") -> {
                executeSequence(cmd)
            }
            else -> activateAgentMode(cmd)
        }
    }

    private suspend fun activateAgentMode(cmd: String): String {
        isAgentRunning = true
        taskLog.clear()
        currentTask = cmd

        // Parsear a tarefa composta
        val subTasks = parseCompoundTask(cmd)
        if (subTasks.isEmpty()) {
            isAgentRunning = false
            return "Senhor, descreva a sequência de tarefas. Ex: 'prospecta 50 leads no TikTok e envia relatório'."
        }

        voiceService.speak("Senhor, modo agente ativado. Vou executar ${subTasks.size} tarefas em sequência.")

        var completed = 0
        for (task in subTasks) {
            if (!isAgentRunning) break
            voiceService.speak("Executando: $task")
            val result = voiceService.processCommandSilent(task)
            taskLog.add("✅ $task — $result")
            completed++
            delay(2000) // Pausa entre tarefas
        }

        isAgentRunning = false
        val summary = buildAgentSummary(completed, subTasks.size)
        memory.save("ultimo_relatorio_agente", summary)
        return summary
    }

    private fun parseCompoundTask(cmd: String): List<String> {
        val tasks = mutableListOf<String>()
        val separators = listOf(" e depois ", " depois ", " em seguida ", " a seguir ", " e também ", " e ", ", ")

        var remaining = cmd
            .replace("executa ", "")
            .replace("faz isto: ", "")
            .replace("modo agente ", "")
            .replace("autónomo ", "")
            .trim()

        // Detectar padrões comuns de tarefas compostas
        if (remaining.contains("prospe") && remaining.contains("tiktok")) tasks.add("prospectar 50 perfis no tiktok")
        if (remaining.contains("prospe") && remaining.contains("instagram")) tasks.add("prospectar 30 perfis no instagram")
        if (remaining.contains("prospe") && remaining.contains("linkedin")) tasks.add("prospectar 20 perfis no linkedin")
        if (remaining.contains("salva") || remaining.contains("guarda") || remaining.contains("ficheiro")) tasks.add("salvar leads em ficheiro")
        if (remaining.contains("relatório") || remaining.contains("report")) tasks.add("gerar relatório de atividade")
        if (remaining.contains("whatsapp") && (remaining.contains("envia") || remaining.contains("manda"))) tasks.add("enviar relatório por whatsapp")
        if (remaining.contains("curtir") && remaining.contains("vídeo")) tasks.add("curtir vídeos no tiktok")
        if (remaining.contains("comentar")) tasks.add("comentar posts")
        if (remaining.contains("seguir")) tasks.add("seguir perfis")
        if (remaining.contains("pesquisa") && remaining.contains("notícias")) tasks.add("notícias de angola")
        if (remaining.contains("câmbio") || remaining.contains("kwanza")) tasks.add("câmbio kwanza")
        if (remaining.contains("calendário") || remaining.contains("agenda")) tasks.add("ver eventos do dia")

        // Se não detectou padrões, dividir por separadores
        if (tasks.isEmpty()) {
            for (sep in separators) {
                if (remaining.contains(sep)) {
                    tasks.addAll(remaining.split(sep).map { it.trim() }.filter { it.length > 3 })
                    break
                }
            }
        }

        return tasks.take(10) // Máximo 10 tarefas por sequência
    }

    private fun buildAgentSummary(completed: Int, total: Int): String {
        return buildString {
            append("Senhor, missão concluída. ")
            append("$completed de $total tarefas executadas. ")
            if (taskLog.isNotEmpty()) {
                append("Resumo: ")
                taskLog.take(3).forEach { append(it.take(50)).append(". ") }
            }
        }
    }

    private fun stopAgent(): String {
        isAgentRunning = false
        return "Senhor, agente parado. ${taskLog.size} tarefas já concluídas."
    }

    private fun getAgentStatus(): String {
        return if (isAgentRunning) {
            "Senhor, estou a executar: $currentTask. ${taskLog.size} tarefas concluídas até agora."
        } else {
            "Senhor, agente inativo. Última missão: ${if (taskLog.isEmpty()) "nenhuma" else taskLog.lastOrNull() ?: ""}."
        }
    }

    private suspend fun executeSequence(cmd: String): String {
        val parts = cmd.split(",", ";", " e depois ", " depois ", " em seguida ").map { it.trim() }.filter { it.length > 3 }
        if (parts.isEmpty()) return "Senhor, diga as tarefas separadas por vírgula ou 'e depois'."
        return activateAgentMode(cmd)
    }
}
