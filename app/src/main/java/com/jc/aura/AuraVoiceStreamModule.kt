package com.jc.aura

import android.content.Context
import kotlinx.coroutines.*

class AuraVoiceStreamModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val voiceService: AuraVoiceService
) {
    private var isStreaming = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun handleVoiceStreamCommand(command: String): String {
        return when {
            command.contains("modo conversação") || command.contains("conversação contínua") || command.contains("modo stream") -> {
                isStreaming = true
                "Senhor, modo conversação contínua ativado. Fale comigo naturalmente. Diga 'parar conversação' para sair."
            }
            command.contains("parar conversação") || command.contains("sair do modo stream") || command.contains("desativar stream") -> {
                isStreaming = false
                "Senhor, modo conversação desativado. Voltando ao modo normal de wake word."
            }
            command.contains("status stream") || command.contains("estado da conversação") -> {
                if (isStreaming) "Senhor, modo conversação contínua ATIVO."
                else "Senhor, modo conversação está DESATIVADO."
            }
            else -> "Senhor, comandos: 'modo conversação', 'parar conversação', 'status stream'."
        }
    }

    fun isActive(): Boolean = isStreaming
}
