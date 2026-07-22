package com.jc.aura

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraFinanceModule — Tracking financeiro por voz.
 * Regista gastos, cria orçamentos, emite alertas, gera resumos.
 * Todos os dados guardados localmente em SQLite via AuraMemory.
 */
class AuraFinanceModule(
    private val context: Context,
    private val memory: AuraMemory
) {
    fun handle(cmd: String): String {
        return when {
            cmd.contains("gastei") || cmd.contains("paguei") || cmd.contains("comprei") || cmd.contains("despesa") -> registerExpense(cmd)
            cmd.contains("recebi") || cmd.contains("ganhei") || cmd.contains("entrada") || cmd.contains("receita") -> registerIncome(cmd)
            cmd.contains("orçamento") || cmd.contains("budget") -> handleBudget(cmd)
            cmd.contains("quanto gastei") || cmd.contains("resumo financeiro") || cmd.contains("balanço") || cmd.contains("extrato") -> getFinancialSummary(cmd)
            cmd.contains("meta") && (cmd.contains("poupar") || cmd.contains("economizar") || cmd.contains("guardar")) -> setSavingsGoal(cmd)
            cmd.contains("quanto falta") || cmd.contains("progresso da meta") -> checkGoalProgress()
            cmd.contains("apaga") && cmd.contains("gasto") -> deleteLastExpense()
            cmd.contains("categorias") || cmd.contains("ver categorias") -> listCategories()
            else -> getFinancialSummary(cmd)
        }
    }

    private fun registerExpense(cmd: String): String {
        val amount = extractAmount(cmd) ?: return "Senhor, diga o valor. Ex: 'gastei 5000 Kz em alimentação'."
        val category = detectCategory(cmd)
        val description = extractDescription(cmd) ?: category
        val currency = if (cmd.contains("dólar") || cmd.contains("usd")) "USD" else if (cmd.contains("euro") || cmd.contains("eur")) "EUR" else "Kz"
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Salvar na memória
        val key = "expense_${System.currentTimeMillis()}"
        memory.save(key, "$date|$category|$description|$amount|$currency")

        // Atualizar total do dia
        val todayTotal = memory.get("total_gastos_hoje")?.toDoubleOrNull() ?: 0.0
        memory.save("total_gastos_hoje", (todayTotal + amount).toString())

        // Atualizar lista de gastos do dia para relatório
        val existing = memory.get("expenses_today") ?: ""
        memory.save("expenses_today", if (existing.isBlank()) "$description:$amount" else "$existing;$description:$amount")

        // Verificar se excede orçamento
        val budget = memory.get("orcamento_${category}")?.toDoubleOrNull()
        val categoryTotal = getCategoryTotal(category)

        return buildString {
            append("Senhor, registado: **$description — $amount $currency** (categoria: $category). ")
            if (budget != null && categoryTotal > budget) {
                append("⚠️ ATENÇÃO: Excedeu o orçamento de $category! Limite: $budget $currency, gasto: $categoryTotal $currency.")
            } else if (budget != null) {
                val remaining = budget - categoryTotal
                append("Orçamento de $category: $remaining $currency restantes.")
            }
        }
    }

    private fun registerIncome(cmd: String): String {
        val amount = extractAmount(cmd) ?: return "Senhor, diga o valor. Ex: 'recebi 50000 Kz de cliente'."
        val source = extractDescription(cmd) ?: "Receita"
        val currency = if (cmd.contains("dólar") || cmd.contains("usd")) "USD" else if (cmd.contains("euro")) "EUR" else "Kz"
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val key = "income_${System.currentTimeMillis()}"
        memory.save(key, "$date|receita|$source|$amount|$currency")

        val todayIncome = memory.get("total_receitas_hoje")?.toDoubleOrNull() ?: 0.0
        memory.save("total_receitas_hoje", (todayIncome + amount).toString())

        return "Senhor, receita registada: **$source — $amount $currency**. ✅"
    }

    private fun handleBudget(cmd: String): String {
        return when {
            cmd.contains("definir") || cmd.contains("criar") || cmd.contains("definir") || cmd.contains("por") -> setBudget(cmd)
            cmd.contains("ver") || cmd.contains("mostrar") -> showBudgets()
            else -> setBudget(cmd)
        }
    }

    private fun setBudget(cmd: String): String {
        val amount = extractAmount(cmd) ?: return "Senhor, diga o valor do orçamento. Ex: 'orçamento de alimentação 30000 Kz'."
        val category = detectCategory(cmd)
        memory.save("orcamento_$category", amount.toString())
        return "Senhor, orçamento de **$category** definido em **$amount Kz** por mês."
    }

    private fun showBudgets(): String {
        val categories = listOf("alimentação", "transporte", "saúde", "lazer", "negócios", "casa", "comunicações")
        val sb = StringBuilder("📊 Orçamentos definidos:\n")
        var any = false
        for (cat in categories) {
            val budget = memory.get("orcamento_$cat")
            if (budget != null) {
                val spent = getCategoryTotal(cat)
                sb.appendLine("  • $cat: $spent Kz / $budget Kz")
                any = true
            }
        }
        if (!any) sb.append("Nenhum orçamento definido. Ex: 'orçamento de alimentação 30000 Kz'.")
        return sb.toString()
    }

    private fun getFinancialSummary(cmd: String): String {
        val period = when {
            cmd.contains("semana") -> "semana"
            cmd.contains("mês") || cmd.contains("mes") -> "mês"
            cmd.contains("ano") -> "ano"
            else -> "hoje"
        }

        val totalExpenses = memory.get("total_gastos_hoje")?.toDoubleOrNull() ?: 0.0
        val totalIncome = memory.get("total_receitas_hoje")?.toDoubleOrNull() ?: 0.0
        val balance = totalIncome - totalExpenses

        return buildString {
            appendLine("💰 RESUMO FINANCEIRO — ${period.uppercase()}")
            appendLine("  • Receitas: ${String.format("%.0f", totalIncome)} Kz")
            appendLine("  • Despesas: ${String.format("%.0f", totalExpenses)} Kz")
            appendLine("  • Saldo: ${if (balance >= 0) "✅" else "⚠️"} ${String.format("%.0f", balance)} Kz")

            // Gastos por categoria
            val expenses = memory.getExpensesToday()
            if (expenses.isNotEmpty()) {
                appendLine("")
                appendLine("  Gastos por item:")
                expenses.take(5).forEach { appendLine("    - ${it.first}: ${it.second} Kz") }
            }

            // Meta de poupança
            val goal = memory.get("meta_poupança")?.toDoubleOrNull()
            if (goal != null) {
                val saved = totalIncome - totalExpenses
                val progress = if (goal > 0) (saved / goal * 100).toInt() else 0
                appendLine("")
                appendLine("  🎯 Meta de poupança: $progress% (${String.format("%.0f", saved)} / ${String.format("%.0f", goal)} Kz)")
            }
        }
    }

    private fun setSavingsGoal(cmd: String): String {
        val amount = extractAmount(cmd) ?: return "Senhor, diga o valor. Ex: 'meta de poupar 100000 Kz'."
        memory.save("meta_poupança", amount.toString())
        return "Senhor, meta de poupança definida: **${String.format("%.0f", amount)} Kz**. Vou acompanhar o progresso."
    }

    private fun checkGoalProgress(): String {
        val goal = memory.get("meta_poupança")?.toDoubleOrNull()
            ?: return "Senhor, ainda não definiu uma meta. Ex: 'meta de poupar 100000 Kz'."
        val income = memory.get("total_receitas_hoje")?.toDoubleOrNull() ?: 0.0
        val expenses = memory.get("total_gastos_hoje")?.toDoubleOrNull() ?: 0.0
        val saved = income - expenses
        val progress = if (goal > 0) (saved / goal * 100).toInt().coerceIn(0, 100) else 0
        return "Senhor, meta: ${String.format("%.0f", goal)} Kz | Poupado: ${String.format("%.0f", saved)} Kz | Progresso: **$progress%**. ${if (progress >= 100) "🎉 Meta atingida!" else "Faltam ${String.format("%.0f", goal - saved)} Kz."}"
    }

    private fun deleteLastExpense(): String {
        memory.save("expenses_today", "")
        memory.save("total_gastos_hoje", "0")
        return "Senhor, gastos do dia limpos."
    }

    private fun listCategories(): String {
        return "Categorias disponíveis: alimentação, transporte, saúde, lazer, negócios, casa, comunicações, educação, vestuário, outros."
    }

    private fun getCategoryTotal(category: String): Double {
        return try {
            val expenses = memory.getExpensesToday()
            expenses.filter { it.first.contains(category, ignoreCase = true) }.sumOf { it.second.toDoubleOrNull() ?: 0.0 }
        } catch (_: Exception) { 0.0 }
    }

    private fun extractAmount(cmd: String): Double? {
        val regex = Regex("(\\d+(?:[.,]\\d{1,3})?)")
        return regex.find(cmd)?.value?.replace(",", ".")?.toDoubleOrNull()
    }

    private fun detectCategory(cmd: String): String = when {
        cmd.contains("comida") || cmd.contains("alimentação") || cmd.contains("restaurante") || cmd.contains("supermercado") -> "alimentação"
        cmd.contains("transporte") || cmd.contains("taxi") || cmd.contains("combustível") || cmd.contains("gasolina") -> "transporte"
        cmd.contains("saúde") || cmd.contains("médico") || cmd.contains("farmácia") || cmd.contains("hospital") -> "saúde"
        cmd.contains("lazer") || cmd.contains("diversão") || cmd.contains("cinema") || cmd.contains("viagem") -> "lazer"
        cmd.contains("negócio") || cmd.contains("empresa") || cmd.contains("fornecedor") || cmd.contains("cliente") -> "negócios"
        cmd.contains("renda") || cmd.contains("casa") || cmd.contains("aluguer") || cmd.contains("condomínio") -> "casa"
        cmd.contains("telefone") || cmd.contains("internet") || cmd.contains("comunicação") || cmd.contains("recarga") -> "comunicações"
        cmd.contains("escola") || cmd.contains("universidade") || cmd.contains("curso") || cmd.contains("livro") -> "educação"
        cmd.contains("roupa") || cmd.contains("sapato") || cmd.contains("vestuário") -> "vestuário"
        else -> "outros"
    }

    private fun extractDescription(cmd: String): String? {
        val keywords = listOf("em ", "de ", "para ", "no ", "na ", "com ")
        for (kw in keywords) {
            val idx = cmd.lastIndexOf(kw, ignoreCase = true)
            if (idx != -1) {
                val desc = cmd.substring(idx + kw.length).trim()
                if (desc.isNotBlank() && !desc.matches(Regex("\\d+.*"))) return desc.take(50).capitalize()
            }
        }
        return null
    }
}
