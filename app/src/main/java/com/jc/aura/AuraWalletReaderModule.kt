package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

class AuraWalletReaderModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val accessibilityService: AccessibilityService? = null
) {

    suspend fun handle(command: String): String {
        return when {
            command.contains("multicaixa") || command.contains("express") -> openMulticaixaExpress(command)
            command.contains("saldo") && (command.contains("banco") || command.contains("conta")) -> checkBankBalance()
            command.contains("transferência") || command.contains("transferir") -> openTransfer(command)
            command.contains("pagar") && command.contains("conta") -> openPayBill()
            command.contains("historial") || command.contains("extrato") || command.contains("movimentos") -> openTransactionHistory()
            command.contains("mbway") || command.contains("mb way") -> openMBWay()
            command.contains("paypal") -> openPayPal()
            else -> "Senhor, comandos de carteira: 'abrir Multicaixa Express', 'verificar saldo', 'fazer transferência', 'pagar conta', 'historial de movimentos'."
        }
    }

    private suspend fun openMulticaixaExpress(command: String): String {
        val packageName = "ao.bfa.multicaixaexpress"
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                delay(3000)
                "Senhor, Multicaixa Express aberto. Pronto para as suas operações bancárias."
            } else {
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(playIntent)
                "Senhor, Multicaixa Express não está instalado. A abrir Play Store para instalar."
            }
        } catch (e: Exception) {
            "Senhor, erro ao abrir Multicaixa Express: ${e.message}"
        }
    }

    private fun checkBankBalance(): String {
        val lastBalance = memory.getFactual("last_balance_check")
        return if (lastBalance != null) {
            "Senhor, último saldo registado: $lastBalance. Para ver o saldo atual, diga 'abrir Multicaixa Express'."
        } else {
            openBankApp()
        }
    }

    private fun openBankApp(): String {
        val bankApps = listOf(
            "ao.bfa.multicaixaexpress" to "Multicaixa Express",
            "com.bai.mobile" to "BAI Directo",
            "ao.bpc.mobile" to "BPC Mobile",
            "com.bfa.mobilebfa" to "BFA Mobile"
        )
        for ((pkg, name) in bankApps) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return "Senhor, a abrir **$name** para verificar o saldo."
                }
            } catch (_: Exception) {}
        }
        return "Senhor, não encontrei aplicativo bancário instalado. Instale o Multicaixa Express."
    }

    private fun openTransfer(command: String): String {
        return openMulticaixaAndSay("a abrir Multicaixa Express para transferência.")
    }

    private fun openPayBill(): String {
        return openMulticaixaAndSay("a abrir Multicaixa Express para pagamento de contas.")
    }

    private fun openTransactionHistory(): String {
        return openMulticaixaAndSay("a abrir Multicaixa Express para ver o historial de movimentos.")
    }

    private fun openMBWay(): String {
        val intent = context.packageManager.getLaunchIntentForPackage("pt.sibs.android.mbway")
        return if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            "Senhor, a abrir MB WAY."
        } else {
            "Senhor, MB WAY não está instalado."
        }
    }

    private fun openPayPal(): String {
        val intent = context.packageManager.getLaunchIntentForPackage("com.paypal.android.p2pmobile")
        return if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            "Senhor, a abrir PayPal."
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
            "Senhor, PayPal não instalado. A abrir no browser."
        }
    }

    private fun openMulticaixaAndSay(action: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("ao.bfa.multicaixaexpress")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                "Senhor, $action"
            } else {
                "Senhor, Multicaixa Express não está instalado."
            }
        } catch (e: Exception) {
            "Senhor, erro: ${e.message}"
        }
    }
}
