package com.jc.aura

import android.content.Context
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AuraMwangoBrainModule — Conhecimento profundo sobre a Mwango Brain.
 * A Aura sabe tudo sobre a empresa para responder perguntas, gerar conteúdo
 * autêntico e interagir em nome da Mwango Brain nas redes sociais.
 *
 * Mwango Brain: Creative & Technology Agency
 * - 17 anos de experiência no mercado angolano
 * - CEO: Aniceto D'Carvalho
 * - Sede: Luanda, Angola
 * - Website: https://mwangobrain.com
 * - Email: contacto@mwangobrain.com
 * - Contactos: (+244) 922 377 659 / (+244) 990 377 659
 */
class AuraMwangoBrainModule(
    private val context: Context,
    private val memory: AuraMemory
) {

    companion object {
        // === DADOS COMPLETOS DA MWANGO BRAIN ===
        const val COMPANY_NAME = "Mwango Brain"
        const val TAGLINE = "Let's Brain Together"
        const val FULL_TAGLINE = "Transformando ideias em soluções tecnológicas"
        const val FOUNDED = "2009" // 17 anos (2026 - 2009)
        const val CEO = "Aniceto D'Carvalho"
        const val HEADQUARTERS = "Luanda, Angola"
        const val WEBSITE = "https://mwangobrain.com"
        const val EMAIL = "contacto@mwangobrain.com"
        const val PHONE_1 = "+244 922 377 659"
        const val PHONE_2 = "+244 990 377 659"

        // Redes Sociais
        const val INSTAGRAM = "@mwangobrain"
        const val FACEBOOK = "MwangoBrain"
        const val LINKEDIN = "Mwango Brain"
        const val YOUTUBE = "@mwangobrain4450"

        // Serviços principais
        val SERVICES = listOf(
            "Desenvolvimento de Websites e Aplicações Web",
            "Sistemas e Plataformas Sob Medida",
            "Design Gráfico e Identidade Visual",
            "Dashboards Interactivos e Painéis de Controlo",
            "Gestão de Dados e Analytics",
            "Intranet e Comunicação Interna",
            "Soluções para Gestão Agrícola (MOSAP3)",
            "Soluções GovTech e Transformação Digital",
            "Inteligência Artificial e Machine Learning",
            "Aplicações Móveis (Apps)"
        )

        // Produtos e Projectos
        val PRODUCTS = listOf(
            "PDAC — Plataforma de Dados Agrícolas",
            "MOSAP3 — Sistema Integrado de Gestão de Reclamações e Sugestões",
            "Intranet Corporativa — Plataforma de comunicação interna",
            "RAXIO Storage — Solução de armazenamento",
            "Dashboards para monitoring e tomada de decisão",
            "Plataformas de Gestão de Dados"
        )

        // Eventos e Presenças
        val EVENTS = listOf(
            "ANGOTIC 2026 — Angola ICT Forum (primeira participação, Stand 063 Tenda B)",
            "ADF 2026 — Fórum de Defesa e Segurança",
            "Apresentação de soluções para gestão agrícola com 14 mil produtores"
        )

        // Valores e Missão
        const val MISSION = "Transformar ideias em soluções digitais impactantes e inovadoras, conectando pessoas e gerando impacto real"
        const val VISION = "Ser referência em tecnologia e inovação em Angola, impulsionando a transformação digital do país"
        const val DIFFERENTIATOR = "Tecnologia 'made in Angola' — soluções desenvolvidas à medida para o mercado angolano e africano"

        // Segmentos de mercado
        val MARKET_SEGMENTS = listOf(
            "Sector Público e Governamental (GovTech)",
            "Sector Agrícola (MOSAP3, PDAC)",
            "Empresas Privadas",
            "ONGs e Organizações Internacionais",
            "Startups e Empreendedores"
        )
    }

    suspend fun handleCommand(command: String, openRouterKey: String): String {
        return when {
            command.contains("sobre mwango") || command.contains("mwango brain") || command.contains("empresa") -> getCompanyInfo()
            command.contains("serviços mwango") || command.contains("servicos mwango") -> getServices()
            command.contains("produtos mwango") || command.contains("projectos mwango") -> getProducts()
            command.contains("contactos mwango") || command.contains("contatos mwango") -> getContacts()
            command.contains("eventos mwango") || command.contains("presença mwango") -> getEvents()
            command.contains("valores mwango") || command.contains("missão mwango") -> getValues()
            command.contains("responder comentários") || command.contains("responder comentarios") || command.contains("auto reply") -> "Para responder comentários use: 'Aura, responder todos os comentários do Instagram/Facebook/TikTok/LinkedIn'"
            command.contains("mandar mensagens") || command.contains("enviar dm") || command.contains("campanha dm") -> "Para enviar DMs em massa use: 'Aura, campanha DM Instagram/Facebook/TikTok/LinkedIn com ficheiro CSV'"
            else -> getCompanyInfo()
        }
    }

    fun getCompanyInfo(): String {
        return """
A Mwango Brain é uma agência angolana de Creative & Technology, com $FOUNDED anos de experiência (desde 2009).

CEO: $CEO
Sede: $HEADQUARTERS
Website: $WEBSITE

A empresa é especializada em transformar ideias em soluções digitais impactantes e inovadoras. 
Combina tecnologia de ponta com uma abordagem criativa para atender às necessidades dos clientes.

Presença nas redes sociais:
• Instagram: $INSTAGRAM
• Facebook: $FACEBOOK
• LinkedIn: $LINKEDIN
• YouTube: $YOUTUBE

A Mwango Brain participou em eventos de grande relevo como o ANGOTIC 2026 e o ADF 2026, 
posicionando-se como referência em tecnologia "made in Angola" com soluções para mais de 14 mil produtores agrícolas.
        """.trimIndent()
    }

    fun getServices(): String {
        val sb = StringBuilder("Serviços da Mwango Brain:\n\n")
        SERVICES.forEachIndexed { i, service ->
            sb.appendLine("  ${i + 1}. $service")
        }
        sb.appendLine("\nTodos os serviços são desenvolvidos sob medida para cada cliente,")
        sb.appendLine("combinando criatividade e tecnologia de ponta.")
        return sb.toString()
    }

    fun getProducts(): String {
        val sb = StringBuilder("Produtos e Projectos da Mwango Brain:\n\n")
        PRODUCTS.forEachIndexed { i, product ->
            sb.appendLine("  • $product")
        }
        return sb.toString()
    }

    fun getContacts(): String {
        return """
Contactos da Mwango Brain:
  📧 Email: $EMAIL
  📱 Telefone 1: $PHONE_1
  📱 Telefone 2: $PHONE_2
  🌐 Website: $WEBSITE
  
  📱 Instagram: $INSTAGRAM
  📘 Facebook: $FACEBOOK
  💼 LinkedIn: $LINKEDIN
  🎥 YouTube: $YOUTUBE
        """.trimIndent()
    }

    fun getEvents(): String {
        val sb = StringBuilder("Eventos e Presenças da Mwango Brain:\n\n")
        EVENTS.forEach { event ->
            sb.appendLine("  🎯 $event")
        }
        return sb.toString()
    }

    fun getValues(): String {
        return """
Missão: $MISSION

Visão: $VISION

Diferencial: $DIFFERENTIATOR

Segmentos: ${MARKET_SEGMENTS.joinToString(", ")}
        """.trimIndent()
    }

    /**
     * Gera contexto da Mwango Brain para a IA usar ao responder comentários.
     * Inclui tom de voz, identidade e pontos-chave.
     */
    fun getBrandContext(): String {
        return """
Somos a Mwango Brain (@mwangobrain), uma agência angolana de Creative & Technology com 17 anos de experiência.
Transformamos ideias em soluções tecnológicas impactantes. Tecnologia "made in Angola".
CEO: Aniceto D'Carvalho. Slogan: "Let's Brain Together".
Serviços: desenvolvimento web, apps, dashboards, IA, gestão de dados, design, sistemas sob medida.
Presença: ANGOTIC 2026, ADF 2026, MOSAP3 com 14 mil produtores.
Contactos: contacto@mwangobrain.com | +244 922 377 659
Website: https://mwangobrain.com
Tom de voz: profissional mas acessível, inovador, orgulhoso do que é feito em Angola.
Nunca somos robóticos. Somos humanos, simpáticos e úteis.
        """.trimIndent()
    }

    /**
     * Gera prompt para a IA criar uma resposta humana a um comentário.
     */
    fun buildReplyPrompt(comment: String, platform: String, isDM: Boolean = false): String {
        val brandContext = getBrandContext()
        return if (isDM) {
            """$brandContext
            
Estamos a enviar uma mensagem privada (DM) no $platform para um potencial cliente/parceiro.
O perfil desta pessoa foi identificado durante prospecção.

Regras para a mensagem:
1. Sê simpático e natural, NÃO robótico
2. Começa com um cumprimento casual (não formal demais)
3. Menção algo relevante sobre o que a pessoa pode precisar
4. Apresenta a Mwango Brain de forma leve, sem parecer vendas forçadas
5. Termina com um call-to-action leve (ex: "gostaria de saber mais?" ou "posso ajudar com algo?")
6. Máximo 3-4 frases curtas
7. Usa português de Angola (natural, com expressões locais quando apropriado)
8. Pode usar emojis mas sem exagerar (1-2 no máximo)
9. NÃO menciona que és uma IA

Perfil da pessoa/contexto: $comment

Escreve apenas a mensagem DM, nada mais:"""
        } else {
            """$brandContext
            
Receberemos este comentário numa publicação nossa no $platform:
"$comment"

Regras para responder:
1. Sê simpático e natural, NÃO robótico
2. Se for elogio, agradece de forma genuína e personalizada
3. Se for pergunta, responde directamente e oferece ajuda extra
4. Se for crítica, responde com profissionalismo e abertura
5. Podes usar humor leve quando apropriado
6. Máximo 2-3 frases curtas
7. Usa português de Angola (natural)
8. Pode usar 1 emoji
9. NÃO menciona que és uma IA
10. Adapta o tom ao contexto do comentário

Escreve apenas a resposta, nada mais:"""
        }
    }
}
