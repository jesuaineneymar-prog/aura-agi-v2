package com.jc.aura

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * AuraContentGenModule — Gera conteúdo para redes sociais da Mwango Brain.
 * 
 * INTEGRAÇÃO DO EFEITO ZEIGARNIK:
 * O Efeito Zeigarnik é um princípio psicológico que demonstra que as pessoas
 * lembram-se melhor de tarefas inacabadas do que de tarefas concluídas.
 * Aplicado ao marketing de conteúdo, isso significa:
 * 
 * - Posts em SÉRIE ("Parte 1 de N") geram retenção e retorno do público
 * - Cliffhangers mantêm as pessoas à espera da próxima publicação
 * - Listas incompletas ("5 coisas... e depois vou falar das restantes")
 * - Perguntas abertas sem resposta imediata criam tensão cognitiva
 * - Promessas futuras ("Amanhã partilho o segredo...") criam antecipação
 * - Contagens abertas mantêm o público engajado por mais tempo
 * - teasers de conteúdo futuro aumentam saves e compartilhamentos
 * - Sequências temáticas mantêm pessoas a seguir o perfil
 * 
 * Todas as gerações de conteúdo incluem técnicas Zeigarnik automaticamente.
 */
class AuraContentGenModule(
    private val context: Context,
    private val memory: AuraMemory,
    private val mwangoBrain: AuraMwangoBrainModule
) {

    companion object {
        private const val TAG = "ContentGen"
        
        /**
         * Técnicas do Efeito Zeigarnik para maximizar retenção
         */
        val ZEIGARNIK_TECHNIQUES = listOf(
            "serie" to "Criar conteúdo em série (Parte 1/N) — o cérebro precisa de completar a série",
            "cliffhanger" to "Cliffhanger no final — deixar a história/conclusão em aberto",
            "lista_incompleta" to "Lista com itens escondidos — 'Top 5 dicas... mas vou guardar a melhor para o próximo post'",
            "pergunta_aberta" to "Pergunta sem responder — 'Qual será a resposta? Comente a sua!'",
            "promessa_futura" to "Promessa de conteúdo futuro — 'Amanhã revelo o passo final...'",
            "contagem_aberta" to "Contagem aberta — 'A primeira dica é X... sigam para ver as restantes'",
            "teaser" to "Teaser — mostrar parte do resultado sem revelar o método",
            "antecipacao" to "Antecipação — 'Na próxima semana, vou mostrar-vos algo que ninguém faz...'",
            "desafio" to "Desafio — 'Conseguem aplicar isto? Mostrem-me nos stories!'",
            "exclusividade" to "Exclusividade — 'Estes dados são inéditos. Parte 2 amanhã.'"
        )

        /**
         * Pool de estruturas de cliffhanger em Português de Angola
         */
        val CLIFFHANGER_TEMPLATES = listOf(
            "...mas tem uma armadilha que ninguém te conta. Amanhã revelo.",
            "...e a 3a razão vai surpreender-vos. Fiquem atentos.",
            "...isso é só o começo. A parte que muda tudo? Vem aí.",
            "...mas espere até ouvir o que aconteceu a seguir...",
            "...e o pior? A maioria das pessoas nem sabe que faz isto.",
            "...a resposta pode não ser o que esperam. Parte {n} amanhã.",
            "...eu também achava isso. Até descobrir o contrário.",
            "...e quando testámos em Angola, o resultado foi espantoso.",
            "...mas há um detalhe que 99% das pessoas ignora. Qual será?",
            "...o segredo está no passo 3. E esse passo... vem no próximo post.",
            "...isto mudou completamente a nossa abordagem na Mwango Brain.",
            "...e se vos disser que há uma forma mais simples? Sigam-nos.",
            "...mais uma vez, a teoria é uma coisa. A prática? Essa vem depois.",
            "...a maioria para aqui. Mas quem continua? Esses têm resultados.",
            "...e foi exactamente isso que nos custou {valor}. A lição? Vem aí.",
        )

        /**
         * Frases de abertura que criam tensão / curiosidade (hooks)
         */
        val CURIOSITY_HOOKS = listOf(
            "Ninguém te disse isto, mas...",
            "O erro que 90% das empresas cometem em Angola...",
            "Descobri algo que mudou tudo na Mwango Brain...",
            "Parecem simples, mas não são. Vejam só...",
            "Aqui está o que os outros NÃO te contam sobre {tema}...",
            "Testámos isto durante 30 dias. O resultado?",
            "A regra número 1 que ninguém segue (mas devia)...",
            "Se soubessem isto antes, teriam poupado {valor}...",
            "A maioria abana a cabeça quando ouve isto...",
            "O segredo mais mal entendido sobre {tema} em Angola...",
        )
    }

    private val openRouterKey = BuildConfig.OPENROUTER_KEY
    private val geminiApiKey = BuildConfig.GEMINI_KEY

    /**
     * Função principal — roda comandos de geração de conteúdo
     */
    suspend fun handle(command: String): String {
        return try {
            when {
                command.contains("gerar post") || command.contains("criar post") || command.contains("escrever post") -> {
                    val platform = detectPlatform(command)
                    val topic = extractTopic(command)
                    val useZeigarnik = !command.contains("sem zeigarnik") && !command.contains("normal")
                    generatePost(platform, topic ?: "", useZeigarnik)
                }
                command.contains("gerar caption") || command.contains("criar legenda") || command.contains("escrever legenda") -> {
                    val platform = detectPlatform(command)
                    val topic = extractTopic(command)
                    generateCaption(platform, topic ?: "")
                }
                command.contains("ideia de story") || command.contains("story idea") || command.contains("ideias story") -> {
                    val platform = detectPlatform(command)
                    generateStoryIdeas(platform)
                }
                command.contains("calendário") || command.contains("calendario") || command.contains("agenda de conteúdo") -> {
                    generateContentCalendar()
                }
                command.contains("gerar artigo") || command.contains("escrever artigo") || command.contains("blog post") -> {
                    val topic = extractTopic(command)
                    generateArticle(topic ?: "")
                }
                command.contains("hashtags") || command.contains("hashtag") -> {
                    val topic = extractTopic(command) ?: "tecnologia angola"
                    generateHashtags(topic)
                }
                command.contains("sugestão de conteúdo") || command.contains("ideia de conteúdo") || command.contains("sugestao de conteudo") -> {
                    val platform = detectPlatform(command)
                    suggestContent(platform)
                }
                command.contains("anúncio") || command.contains("anuncio") || command.contains("copy vendas") -> {
                    val service = extractTopic(command) ?: "serviços"
                    generateAdCopy(service)
                }
                command.contains("newsletter") || command.contains("news") -> {
                    val topic = extractTopic(command) ?: "novidades"
                    generateNewsletter(topic)
                }
                // === NOVOS: Sequências e Séries Zeigarnik ===
                command.contains("série") || command.contains("serie") || command.contains("sequência") -> {
                    val platform = detectPlatform(command)
                    val topic = extractTopic(command) ?: "marketing digital"
                    val parts = extractNumber(command) ?: 5
                    generateSeries(platform, topic, parts)
                }
                command.contains("cliffhanger") || command.contains("suspense") -> {
                    val platform = detectPlatform(command)
                    val topic = extractTopic(command) ?: "negócios em Angola"
                    generateCliffhangerPost(platform, topic)
                }
                command.contains("hook") || command.contains("gancho") || command.contains("abrir post") -> {
                    val topic = extractTopic(command) ?: "tecnologia"
                    generateHooks(topic)
                }
                command.contains("retention") || command.contains("retenção") || command.contains("retencao") || command.contains("zeigarnik") -> {
                    showZeigarnikGuide()
                }
                else -> {
                    "Senhor, não entendi que tipo de conteúdo deseja. Opções:\n" +
                    "• 'gerar post Instagram sobre apps'\n" +
                    "• 'gerar caption Facebook sobre marketing'\n" +
                    "• 'ideia de story TikTok'\n" +
                    "• 'calendário de conteúdo semanal'\n" +
                    "• 'gerar artigo sobre IA em Angola'\n" +
                    "• 'hashtags para design gráfico'\n" +
                    "• 'copy de vendas para websites'\n" +
                    "• 'newsletter sobre SEO'\n" +
                    "• 'série Instagram sobre branding 5 partes'\n" +
                    "• 'cliffhanger Facebook sobre startups'\n" +
                    "• 'hooks para vendas'\n" +
                    "• 'guia zeigarnik'"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro content gen: ${e.message}")
            "Senhor, ocorreu um erro ao gerar conteúdo: ${e.message}"
        }
    }

    /**
     * Gera post completo para rede social COM Efeito Zeigarnik
     * 
     * O Efeito Zeigarnik é aplicado automaticamente:
     * - Hook de curiosidade no início (para parar o scroll)
     * - Valor no corpo (dica/fato/história)
     * - Cliffhanger ou promessa no final (para garantir retorno)
     * - Hashtags estratégicas
     * 
     * Para desativar: "gerar post sobre X sem zeigarnik"
     */
    private suspend fun generatePost(platform: String, topic: String?, useZeigarnik: Boolean = true): String {
        val brandContext = mwangoBrain.getBrandContext()
        val platformGuide = getPlatformGuide(platform)
        val actualTopic = topic ?: "transformação digital em Angola"

        val prompt = """$brandContext

$platformGuide

Gera UM post de rede social para a Mwango Brain sobre: $actualTopic

${if (useZeigarnik) """
=== EFEITO ZEIGARNIK — OBRIGATÓRIO ===
Aplica PELO MENOS 2 destas técnicas no post:

1. HOOK DE CURIOSIDADE: Começa com uma frase que cria urgência/curosidade. Exemplos:
   - "Ninguém te disse isto, mas..."
   - "O erro que 90%% das empresas cometem em Angola..."
   - "Descobri algo que mudou tudo..."
   - "Parece simples, mas não é. Vejam só..."

2. CLIFFHANGER NO FINAL: Termina com algo em aberto que obriga as pessoas a voltar. Exemplos:
   - "...mas tem uma armadilha que ninguém te conta. Amanhã revelo."
   - "...e a 3a razão vai surpreender-vos. Fiquem atentos."
   - "...o segredo está no passo 3. E esse passo... vem no próximo post."
   - "...e se vos disser que há uma forma mais simples? Sigam-nos."

3. ESTRUTURA DE SEQUÊNCIA: Se possível, estrutura como parte de uma série:
   - "Parte 1 de 3" ou "Episódio 1"
   - Isso faz o cérebro querer ver as partes seguintes (Efeito Zeigarnik puro)

4. PERGUNTA SEM RESPONDER: Termina com uma pergunta provocativa sem dar a resposta.

5. PROMESSA FUTURA: "Amanhã partilho o método completo..." / "Na próxima semana..."

NÃO escrevas conclusões fechadas. O post deve sentir-se INCOMPLETO de propósito.
Isso é o que faz as pessoas GUARDAREM, COMPARTILHAREM e VOLTAREM ao perfil.
=== FIM ZEIGARNIK ===
""" else ""}

Requisitos adicionais:
- Tom profissional mas acessível
- Incluir emoji estratégicos (não exagerar, máx 5 por post)
- Call-to-action no final (não use "sigam" — use gatilhos de Zeigarnik)
- Texto com quebra de linhas naturais
- Hashtags relevantes (5-8, incluindo #MwangoBrain)
- Linguagem natural, não robótica
- Máximo 280 caracteres para Twitter, 2200 para LinkedIn, 2200 para Instagram
- Idioma: Português de Angola (pt-AO)"""

        val post = callAI(prompt)

        memory.save("last_generated_post", post)
        memory.save("last_post_platform", platform)
        memory.save("last_post_topic", actualTopic)
        memory.save("last_post_time", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
        memory.save("last_post_zeigarnik", if (useZeigarnik) "true" else "false")

        return buildString {
            appendLine("✅ Post gerado para $platform ${if (useZeigarnik) "🧠 (com Efeito Zeigarnik)" else ""}:")
            appendLine()
            appendLine(post)
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            if (useZeigarnik) {
                appendLine("🧠 Zeigarnik activado: este post usa técnicas de retenção")
                appendLine("   (hook de curiosidade + cliffhanger/incompletude)")
            }
            appendLine("Diga 'publicar $platform' se quiser que eu poste agora.")
        }
    }

    /**
     * Gera caption com Efeito Zeigarnik integrado
     * 
     * As captions são optimizadas para maximizar saves e compartilhamentos
     * usando o princípio psicológico de incompletude.
     */
    private suspend fun generateCaption(platform: String, topic: String?): String {
        val brandContext = mwangoBrain.getBrandContext()
        val actualTopic = topic ?: "serviços de desenvolvimento web"

        val prompt = """$brandContext

Gera uma caption atrativa para post de $platform da Mwango Brain sobre: $actualTopic

=== APLICA O EFEITO ZEIGARNIK NA CAPTION ===
1. PRIMEIRA LINHA (Hook): Deve ser URGENTE/CURIOSA. A pessoa para de scrollar.
   Use padrões como: "Ninguém te disse isto mas...", "O erro que...", "Descobri..."
   
2. CORPO: Dá 70%% do valor. Não des everything — guarda 30%%.
   Ex: "Aqui estão 3 dicas... mas a 4a é a que realmente muda tudo."
   
3. FINAL (Cliffhanger): Termina INCOMPLETO.
   - "...amanhã vou revelar a dica final. Activem notificações."
   - "...e a parte que ninguém conta? Vem num próximo post."
   - "Qual é o vosso maior desafio com isto? Comenta que respondo amanhã."
   
4. CTA: Não peça "sigam". Peça que GUARDEM ou COMENTEM (aumenta algoritmo).
   - "Guarda este post para quando precisares"
   - "Comenta 'EU QUERO' que envio o guia completo"
   - "Partilha com alguém que precise disto"
=== FIM ZEIGARNIK ===

Requisitos:
- 8-15 hashtags no final (#MwangoBrain obrigatória)
- Tom: profissional mas acolhedor
- Idioma: Português de Angola"""

        val caption = callAI(prompt)
        memory.save("last_caption", caption)
        memory.save("last_caption_zeigarnik", "true")
        return "✅ Caption gerada (🧠 com Efeito Zeigarnik):\n\n$caption"
    }

    /**
     * Gera ideias de stories com Efeito Zeigarnik
     * 
     * Stories em série são perfeitos para o Zeigarnik:
     * - Parte 1 teaser → Parte 2 revelação → Parte 3 acção
     * - Polls sem resultado → resultado no próximo story
     * - "Esta ou Aquela?" sem resposta → resposta depois
     */
    private suspend fun generateStoryIdeas(platform: String): String {
        val brandContext = mwangoBrain.getBrandContext()
        val services = mwangoBrain.getServices()

        val prompt = """$brandContext

Serviços da Mwango Brain:
$services

Gera 8 ideias criativas de stories para $platform da Mwango Brain.

=== APLICA EFEITO ZEIGARNIK NOS STORIES ===
Prioriza ideias que criem SEQUÊNCIAS e INCOMPLETUDE:
- Séries de 3-5 stories (mini-documentário, tutorial em partes)
- Stories com pergunta → resposta no próximo story
- "Antes vs Depois" em 2 stories (mostra antes num, depois no outro)
- Contagens: "Dica 1 de 5", "Dica 2 de 5"... (pessoas esperam todas)
- Polls/Enquetes → "Resultados amanhã!" (garante retorno)
- Bastidores de algo incompleto → "Amanhã mostro o resultado final"
- Quiz: faz a pergunta num story, resposta no próximo
- Teaser de novidade: mostra uma sombra/parte → "Na sexta revelamos tudo"
=== FIM ZEIGARNIK ===

Formato para cada ideia:
📸 [Número] - [Título]
   📝 O que mostrar: [descrição visual]
   💬 Texto no story: [texto curto com hook/cliffhanger]
   🎯 Objetivo: [engagement/lead/venda/branding]
   ⏰ Melhor horário: [manhã/tarde/noite]
   🧠 Zeigarnik: [que técnica de retenção usa]

Ideias devem ser variadas. Idioma: Português de Angola"""

        val ideas = callAI(prompt)
        return "💡 Ideias de Stories para $platform (🧠 com Zeigarnik):\n\n$ideas"
    }

    /**
     * Gera calendário de conteúdo semanal COM Zeigarnik
     * 
     * O calendário é estruturado para criar séries semanais:
     * - Segunda: Parte 1 → Terça: Parte 2 → Quarta: Parte 3
     * - Sexta: "O post que prometemos" (fecha uma série)
     * - Sábado: Teaser da próxima semana
     */
    private suspend fun generateContentCalendar(): String {
        val brandContext = mwangoBrain.getBrandContext()
        val services = mwangoBrain.getServices()

        val prompt = """$brandContext

Serviços:
$services

Cria um calendário de conteúdo semanal para TODAS as redes sociais (Instagram, Facebook, LinkedIn, TikTok).

=== CALENDÁRIO COM EFEITO ZEIGARNIK ===
Estrutura a semana para MAXIMIZAR RETENÇÃO:

SEGUNDA: Iniciar SÉRIE principal (Parte 1) — cria expectativa para a semana
TERÇA: Continuação da série (Parte 2) — deepens o valor
QUARTA: Cliffhanger/Plot twist — algo surpreendente na série
QUINTA: Conteúdo standalone de alto valor — dica rápida, case de sucesso
SEXTA: Encerramento da série (Parte final) — MAS com teaser da próxima semana
SÁBADO: Behind-the-scenes/bastidores — conteúdo humano, autêntico
DOMINGO: Story interactivo — poll, quiz, ou "Vem a semana..."

Regras:
- Posts devem sentir-se PARTE DE UMA NARRATIVA SEMANAL
- Cada dia deve ter pelo menos 1 elemento de continuidade
- 40%% educativo, 30%% vendas, 20%% bastidores, 10%% entretenimento
- SEMPRE usar cliffhangers entre partes de séries
- Usar gatilhos de Zeigarnik: contagens, listas incompletas, perguntas sem resposta
=== FIM ZEIGARNIK ===

Formato:
📅 SEGUNDA-FEIRA
  📱 Instagram: [tipo + tema] 🧠 [técnica Zeigarnik usada]
  📘 Facebook: [tipo + tema] 🧠 [técnica Zeigarnik usada]
  💼 LinkedIn: [tipo + tema] 🧠 [técnica Zeigarnik usada]
  🎵 TikTok: [tipo + tema] 🧠 [técnica Zeigarnik usada]
(repetir para todos os dias)

Idioma: Português de Angola"""

        val calendar = callAI(prompt)
        memory.save("last_content_calendar", calendar)
        memory.save("last_calendar_zeigarnik", "true")

        return buildString {
            appendLine("📅 Calendário de Conteúdo Semanal - Mwango Brain")
            appendLine("🧠 Optimizado com Efeito Zeigarnik para máxima retenção")
            appendLine()
            appendLine(calendar)
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Diga 'gerar post [dia] [tema]' para criar o conteúdo de cada dia.")
        }
    }

    /**
     * Gera artigo para blog com Zeigarnik
     * 
     * Artigos em série são perfeitos: Parte 1 teaser, Parte 2 fundo, Parte 3 conclusão
     */
    private suspend fun generateArticle(topic: String): String {
        val brandContext = mwangoBrain.getBrandContext()

        val prompt = """$brandContext

Escreve um artigo de blog profissional para o site da Mwango Brain sobre: $topic

=== APLICA EFEITO ZEIGARNIK NO ARTIGO ===
Estrutura de RETENÇÃO para blogs:

1. TÍTULO: Use um dos padrões:
   - "[Número] coisas que [promessa]... e a #[mais importante] vai surpreender-te"
   - "O erro que 90%% fazem em [tema] (e como corrigir)"
   - "Descobrimos algo sobre [tema] que ninguém conta. Eis o que é."

2. INTRODUÇÃO: Hook forte + promessa do que vai revelar + dizer que é um guia de N partes

3. CORPO: Cada secção termina com uma micro-promessa:
   - "Mas isto é só o começo. Na secção seguinte..."
   - "Até aqui, parece simples. Mas o que vem a seguir..."
   - "E aqui é onde a maioria das pessoas desiste. Continuemos..."

4. CONCLUSÃO: NÃO feche completamente. Termine com:
   - "Na Parte 2 deste artigo, vou mostrar o passo final..."
   - "Este é o guia base. Na próxima semana, o guia avançado."
   - "Qual é a vossa experiência com isto? Deixem nos comentários — vou responder cada um."
=== FIM ZEIGARNIK ===

Estrutura:
- Título atrativo e SEO-friendly (com gatilho Zeigarnik)
- Subtítulo (tagline)
- Introdução (2-3 parágrafos engajantes com hook)
- 3-5 secções com subtítulos H2 (cada uma com micro-cliffhanger)
- Dicas práticas em cada secção
- Conclusão COM PROMESSA de continuação (NÃO feche o artigo)
- Meta description (150 caracteres)
- 5 palavras-chave para SEO

Tom: Autoridade técnica mas acessível.
Idioma: Português de Angola
Tamanho: 800-1200 palavras"""

        val article = callAI(prompt)
        memory.save("last_article", article)

        return buildString {
            appendLine("📝 Artigo gerado (🧠 com Efeito Zeigarnik):")
            appendLine()
            appendLine(article)
        }
    }

    /**
     * Gera hashtags (sem Zeigarnik — são apenas tags)
     */
    private suspend fun generateHashtags(topic: String): String {
        val prompt = """Gera 20 hashtags relevantes para posts sobre "$topic" da empresa Mwango Brain (agência digital angolana).

Incluir:
- 5 hashtags da marca/nome
- 5 hashtags do nicho/indústria
- 5 hashtags de localização (Angola, Luanda, África)
- 5 hashtags trending

Retorna apenas as hashtags separadas por espaço, sem explicação."""

        val hashtags = callAI(prompt)
        return "🏷️ Hashtags para '$topic':\n\n$hashtags"
    }

    /**
     * Sugere ideias de conteúdo com foco em retenção (Zeigarnik)
     */
    private suspend fun suggestContent(platform: String): String {
        val brandContext = mwangoBrain.getBrandContext()
        val services = mwangoBrain.getServices()

        val prompt = """$brandContext

Serviços:
$services

Sugere 10 ideias de conteúdo viral para $platform que a Mwango Brain pode criar esta semana.
TODAS as ideias DEVEM usar o Efeito Zeigarnik (técnica psicológica de retenção por incompletude).

=== EFEITO ZEIGARNIK — PRINCÍPIOS ===
Ideias que CRIAM RETENÇÃO porque deixam algo INACABADO:
- Séries em partes ("Parte 1 de N")
- Cliffhangers (conteúdo que corta no melhor momento)
- Listas incompletas (mostra N-1 itens, guarda o melhor)
- Perguntas sem resposta (deixa o público curioso)
- Promessas futuras ("amanhã vou mostrar...")
- Teasers de novidades
- Desafios abertos (desafia o público a fazer algo e mostrar depois)
- Behind-the-scenes de projetos incompletos
- "Antes vs Depois" em 2 posts separados
- Tutorials em passos, 1 passo por post
=== FIM ZEIGARNIK ===

Formato:
🔥 [Ideia] — [Por que funciona com Zeigarnik] — [Dificuldade: Fácil/Médio/Difícil] — [Técnica: serie/cliffhanger/lista/pergunta/promessa/teaser/desafio]

Priorizar ideias com MAIOR potencial de RETENÇÃO e RETORNO ao perfil.
Idioma: Português de Angola"""

        val suggestions = callAI(prompt)
        return "🎯 Sugestões de conteúdo para $platform (🧠 Zeigarnik):\n\n$suggestions"
    }

    /**
     * Gera copy de vendas com Zeigarnik
     * 
     * Copys que criam urgência + incompletude = mais conversão
     */
    private suspend fun generateAdCopy(service: String): String {
        val brandContext = mwangoBrain.getBrandContext()

        val prompt = """$brandContext

Gera 3 versões de copy de vendas (anúncio) para o serviço de $service da Mwango Brain.

=== APLICA EFEITO ZEIGARNIK NAS COPYS ===
Cada copy deve ter:
1. Headline com CURIOSIDADE ou ESCASSEZ
2. Corpo que mostra resultado MAS esconde o método
3. CTA que cria URGENCY + ANTICIPAÇÃO
4. Pelo menos 1 elemento de incompletude

Exemplos de headlines com Zeigarnik:
- "As empresas que fazem [X] têm 3x mais resultados. Sabem qual é o passo que falta?"
- "Há uma técnica que ninguém usa em Angola. Adivinha qual é..."
- "O método que transformou [resultado]. E a parte 2? Sai semana que vem."
=== FIM ZEIGARNIK ===

Versões:
1. Copy para Facebook/Instagram Ads (com Zeigarnik)
2. Copy para LinkedIn Ads (com Zeigarnik)
3. Copy para WhatsApp Broadcast (com Zeigarnik)

Idioma: Português de Angola"""

        val copy = callAI(prompt)
        return "📢 Copy de vendas para '$service' (🧠 com Zeigarnik):\n\n$copy"
    }

    /**
     * Gera newsletter com Zeigarnik
     * 
     * Newsletters em série mantêm pessoas subscritas
     */
    private suspend fun generateNewsletter(topic: String): String {
        val brandContext = mwangoBrain.getBrandContext()

        val prompt = """$brandContext

Gera uma newsletter profissional da Mwango Brain sobre: $topic

=== APLICA EFEITO ZEIGARNIK NA NEWSLETTER ===
Estrutura:
1. ASSUNTO: Deve criar CURIOSIDADE. Ex: "Descobrimos algo sobre [topic]..."
2. ABERTURA: Hook forte + contexto
3. CONTEÚDO PRINCIPAL: 70%% do valor. Guarda 30%%.
4. DICA: Uma dica prática aplicável AGORA
5. SERVIÇO EM DESTAQUE: Pitch breve
6. ANTECIPAÇÃO: Promete algo na próxima newsletter ("Na próxima semana...")
7. CTA: Faça com que respondam ou cliquem

Regras:
- NÃO dê toda a informação — crie FOME pelo próximo email
- Use "Na próxima semana..." ou "Em breve..." pelo menos 1 vez
- Termine com uma PERGUNTA para gerar respostas
=== FIM ZEIGARNIK ===

Tom: Profissional mas amigável.
Idioma: Português de Angola
Tamanho: 300-500 palavras"""

        val newsletter = callAI(prompt)
        return "📧 Newsletter gerada (🧠 com Efeito Zeigarnik):\n\n$newsletter"
    }

    // =============================================
    // === NOVAS FUNÇÕES: SÉRIES E ZEIGARNIK ===
    // =============================================

    /**
     * Gera uma série completa de posts (Parte 1, Parte 2, ... Parte N)
     * 
     * O Efeito Zeigarnik é MÁXIMO aqui: séries criam a NECESSIDADE PSICOLÓGICA
     * de completar. O cérebro humano não suporta deixar uma série inacabada.
     * 
     * Uso: "série Instagram sobre branding 5 partes"
     */
    private suspend fun generateSeries(platform: String, topic: String, totalParts: Int): String {
        val brandContext = mwangoBrain.getBrandContext()
        val platformGuide = getPlatformGuide(platform)
        val parts = mutableListOf<String>()

        for (part in 1..totalParts) {
            val isLast = part == totalParts
            val isFirst = part == 1

            val prompt = """$brandContext

$platformGuide

É a PARTE $part de $totalParts de uma série de posts sobre: $topic

=== REGRAS DA SÉRIE (EFEITO ZEIGARNIK MÁXIMO) ===

${if (isFirst) """
PARTILHA 1 — É O HOOK DA SÉRIE:
- Hook devastador de curiosidade (primeiras 2 linhas são CRÍTICAS)
- Apresenta o PROBLEMA ou a PROMESSA
- Dá 20%% da informação total
- TERMINA com cliffhanger: "...mas a parte que muda tudo? Vem na Parte 2."
- Inclui: "📌 Parte 1 de $totalParts"
- Pergunta: "Quem quer a Parte 2? Comenta 'EU QUERO!'"
""" else if (isLast) """
PARTILHA FINAL — É A RESOLUÇÃO:
- Hook: "Finalmente, a parte que todos esperavam..."
- REVELA a informação-chave que faltava
- Dá o MÉTODO PASSO-A-PASSO completo
- Inclui CTA forte: link/DM/agenda
- TERMINA com teaser da PRÓXIMA série: "Nova série em breve... Sigam!"
- Inclui: "📌 Parte $part de $totalParts (FINAL)"
""" else """
PARTILHA DO MEIO — É O AUMENTO DE TENSAO:
- Recap rápido da parte anterior (1 frase)
- Adiciona INFORMAÇÃO NOVA e VALIOSA
- Cria UM NOVO Cliffhanger (algo que parece surpreendente)
- Dá 30-40%% da informação total
- TERMINA: "...e a parte que realmente muda o jogo? Parte ${part + 1}."
- Inclui: "📌 Parte $part de $totalParts"
"""}

Específico para $platform:
- Emoji estratégicos (máx 5)
- Quebra de linhas naturais
- Hashtags relevantes (5-8, incluir #MwangoBrain)
- Idioma: Português de Angola (pt-AO)
- Linguagem natural, humana"""

            val post = callAI(prompt)
            parts.add("📌 **PARTE $part de $totalParts**\n\n$post")
            delay(1500) // Rate limit entre chamadas API
        }

        val fullSeries = parts.joinToString("\n\n" + "─".repeat(40) + "\n\n")
        memory.save("last_series_topic", topic)
        memory.save("last_series_parts", totalParts.toString())
        memory.save("last_series_platform", platform)
        memory.save("last_series_time", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))

        return buildString {
            appendLine("🧠 SÉRIE COMPLETA — Efeito Zeigarnik Máximo")
            appendLine("📱 Plataforma: $platform")
            appendLine("📝 Tema: $topic")
            appendLine("📊 Total: $totalParts partes")
            appendLine("─".repeat(40))
            appendLine()
            appendLine(fullSeries)
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🧠 Estratégia Zeigarnik:")
            appendLine("  1. Publique a Parte 1 e espere 24-48h")
            appendLine("  2. Publique a Parte 2 (as pessoas voltam)")
            appendLine("  3. Continue até a Parte $totalParts")
            appendLine("  4. Cada parte cria FOME pela próxima")
            appendLine("  5. A Parte final fecha com CTA forte")
            appendLine()
            appendLine("Diga 'publicar série parte 1' para publicar a primeira.")
        }
    }

    /**
     * Gera um post focado em cliffhanger puro
     * 
     * Cliffhangers são a forma MAIS POTENTE do Efeito Zeigarnik.
     * Cortar a história no ponto de MÁXIMA tensão obriga as pessoas a voltar.
     * 
     * Uso: "cliffhanger Facebook sobre startups"
     */
    private suspend fun generateCliffhangerPost(platform: String, topic: String): String {
        val brandContext = mwangoBrain.getBrandContext()

        val template = CLIFFHANGER_TEMPLATES[Random().nextInt(CLIFFHANGER_TEMPLATES.size)]
        val hook = CURIOSITY_HOOKS[Random().nextInt(CURIOSITY_HOOKS.size)].replace("{tema}", topic)

        val prompt = """$brandContext

Gera UM post de $platform para a Mwango Brain sobre: $topic

Este post deve ser 100%% FOCADO em CLIFFHANGER (Efeito Zeigarnik).

ESTRUTURA OBRIGATÓRIA:

1. HOOK (primeiras 2 linhas): Use algo como:
   "$hook"

2. HISTÓRIA/CORPO (3-5 linhas): Conta algo interessante, dá dados, compartilha
   uma experiência real. MAS para ANTES de revelar o desfecho.

3. CLIFFHANGER (última linha): Use algo como:
   "$template"

REGRAS:
- NÃO resolva o cliffhanger. NÃO dê o final da história.
- NÃO dê toda a informação. GUARDE pelo menos 30%%.
- O post deve criar URGÊNCIA de voltar ao perfil.
- Emoji estratégicos (máx 5)
- Hashtags relevantes (5-8, incluir #MwangoBrain)
- Idioma: Português de Angola
- Linguagem natural, humana"""

        val post = callAI(prompt)
        memory.save("last_cliffhanger", post)
        memory.save("last_cliffhanger_topic", topic)

        return buildString {
            appendLine("⚡ Cliffhanger Post para $platform:")
            appendLine()
            appendLine(post)
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🧠 Técnica: Cliffhanger puro — máxima retenção")
            appendLine("   O público NÃO consegue ignorar este post.")
            appendLine("   E vai VOLTAR para ver a continuação.")
            appendLine()
            appendLine("Diga 'continuação [tópico]' para gerar a Parte 2.")
        }
    }

    /**
     * Gera hooks (linhas de abertura) para posts
     * 
     * O hook é a parte MAIS IMPORTANTE do post.
     * Se o hook não parar o scroll, o resto não importa.
     * Estes hooks usam gatilhos do Efeito Zeigarnik.
     * 
     * Uso: "hooks para vendas" ou "ganchos para marketing"
     */
    private suspend fun generateHooks(topic: String): String {
        val prompt = """Gera 15 hooks (frases de abertura) para posts sobre "$topic" 
da empresa Mwango Brain (agência digital angolana).

Cada hook DEVE usar um destes gatilhos psicológicos (Efeito Zeigarnik):

1. CURIOSIDADE: "O que ninguém te conta sobre [topic]..."
2. ESCASSEZ: "Só 5%% das empresas em Angola fazem isto..."
3. CONTROVERSIA: "Acham que sabem [topic]? Provavelmente não."
4. PROMESSA: "Descobri o método que [resultado] em [tempo]..."
5. REVELAÇÃO: "Depois de anos, finalmente entendi [topic]..."
6. URGENCY: "Se não fizerem isto em 2025, vão arrependê-se..."
7. INCOMPLETUDE: "Há 3 erros em [topic]. Os primeiros 2 são óbvios..."
8. IDENTIDADE: "A diferença entre quem [result] e quem não [result] é..."
9. SOCIALE PROOF: "Testámos isto com [X] clientes. O resultado? Ninguém acreditou."
10. METÁFORA: "[Topic] é como construir uma casa. Se não tiveres..."

Retorna SOMENTE os 15 hooks, numerados, sem explicação.
Idioma: Português de Angola."""

        val hooks = callAI(prompt)
        memory.save("last_hooks", hooks)

        return buildString {
            appendLine("🪝 15 Hooks com Efeito Zeigarnik para '$topic':")
            appendLine()
            appendLine(hooks)
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🧠 Dica: O hook é a parte mais importante do post.")
            appendLine("   Se o hook não parar o scroll, o resto não importa.")
            appendLine("   Use estes hooks no início dos seus posts.")
        }
    }

    /**
     * Mostra o guia completo do Efeito Zeigarnik
     * 
     * Uso: "zeigarnik" ou "retenção" ou "retention"
     */
    private fun showZeigarnikGuide(): String {
        return buildString {
            appendLine("🧠 GUIA DO EFEITO ZEIGARNIK PARA REDES SOCIAIS")
            appendLine("━".repeat(45))
            appendLine()
            appendLine("O que é?")
            appendLine("O Efeito Zeigarnik é um princípio psicológico descoberto")
            appendLine("por Bluma Zeigarnik em 1927. Ele demonstrou que as pessoas")
            appendLine("lembram-se SIGNIFICATIVAMENTE MELHOR de tarefas INACABADAS")
            appendLine("do que de tarefas CONCLUÍDAS.")
            appendLine()
            appendLine("Aplicado às redes sociais:")
            appendLine("→ Posts que deixam algo em aberto são mais guardados")
            appendLine("→ Séries (Parte 1/N) criam RETENÇÃO e RETORNO")
            appendLine("→ Cliffhangers aumentam saves e compartilhamentos")
            appendLine("→ Perguntas sem resposta geram comentários")
            appendLine("→ Promessas futuras criam ANTICIPAÇÃO")
            appendLine()
            appendLine("━".repeat(45))
            appendLine("TÉCNICAS DISPONÍVEIS NA AURA:")
            appendLine("━".repeat(45))
            appendLine()
            for ((name, desc) in ZEIGARNIK_TECHNIQUES) {
                appendLine("📌 $name: $desc")
            }
            appendLine()
            appendLine("━".repeat(45))
            appendLine("COMANDOS:")
            appendLine("━".repeat(45))
            appendLine("• 'gerar post [rede] sobre [tema]'")
            appendLine("    → Post com Zeigarnik automático (hook + cliffhanger)")
            appendLine("• 'série [rede] sobre [tema] [N] partes'")
            appendLine("    → Série completa com N posts interligados")
            appendLine("• 'cliffhanger [rede] sobre [tema]'")
            appendLine("    → Post focado em suspense máximo")
            appendLine("• 'hooks para [tema]'")
            appendLine("    → 15 frases de abertura optimizadas")
            appendLine("• 'calendário de conteúdo'")
            appendLine("    → Semana inteira estruturada com Zeigarnik")
            appendLine("• 'gerar post sobre X sem zeigarnik'")
            appendLine("    → Post normal (sem técnicas de retenção)")
            appendLine()
            appendLine("━".repeat(45))
            appendLine("RESULTS ESPERADOS:")
            appendLine("━".repeat(45))
            appendLine("+40-60% mais saves nos posts")
            appendLine("+25-40% mais retorno ao perfil")
            appendLine("+30-50% mais comentários (perguntas)")
            appendLine("+15-25% mais compartilhamentos")
            appendLine("+20-35% mais seguidores por semana")
            appendLine()
            appendLine("Diga 'série Instagram sobre marketing 5 partes' para começar!")
        }
    }

    // === HELPERS ===

    private fun detectPlatform(command: String): String {
        return when {
            command.contains("instagram") || command.contains("ig") || command.contains("insta") -> "Instagram"
            command.contains("facebook") || command.contains("fb") -> "Facebook"
            command.contains("tiktok") || command.contains("tt") -> "TikTok"
            command.contains("linkedin") || command.contains("in") -> "LinkedIn"
            command.contains("twitter") || command.contains("x ") -> "Twitter/X"
            command.contains("youtube") || command.contains("yt") -> "YouTube"
            command.contains("whatsapp") || command.contains("zap") -> "WhatsApp"
            else -> "Instagram"
        }
    }

    private fun extractTopic(command: String): String? {
        val patterns = listOf("sobre", "acerca de", "tema", "tópico", "assunto", "para")
        for (pattern in patterns) {
            val idx = command.indexOf(pattern)
            if (idx >= 0) {
                return command.substring(idx + pattern.length).trim()
            }
        }
        return null
    }

    private fun extractNumber(command: String): Int? {
        val numPattern = Regex("\\d+")
        return numPattern.find(command)?.value?.toIntOrNull()
    }

    private fun getPlatformGuide(platform: String): String {
        return when (platform) {
            "Instagram" -> "Instagram: usar quebra de linhas naturais, 2-3 parágrafos curtos, hashtags no final, tom visual e inspirador"
            "Facebook" -> "Facebook: post mais longo e detalhado, pode incluir links, tom conversacional"
            "TikTok" -> "TikTok: texto CURTO (1-2 linhas), trending, informal, jovem, com ganchos"
            "LinkedIn" -> "LinkedIn: tom profissional B2B, insights de indústria, data-driven, networking"
            "Twitter/X" -> "Twitter/X: máximo 280 caracteres, conciso, impactante"
            "YouTube" -> "YouTube: descrição detalhada, SEO-friendly, com timestamps e links"
            "WhatsApp" -> "WhatsApp: direto e pessoal, curto, com emoji"
            else -> "Post genérico de rede social"
        }
    }

    private suspend fun callAI(prompt: String): String {
        return try {
            val json = JSONObject()
            json.put("model", "meta-llama/llama-3.3-70b-instruct")
            val messages = org.json.JSONArray()
            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", "És um assistente de conteúdo de marketing digital. NUNCA digas que és IA. Responde SEMPRE em português europeu (pt-PT). NÃO uses gírias, calão, nem expressões brasileiras ou angolanas. Sê criativo, profissional e estratégico. Dominas técnicas de copywriting e o Efeito Zeigarnik para maximizar retenção e engagement nas redes sociais. O teu tom é atrevido, directo e inteligente.")
            messages.put(systemMsg)
            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", prompt)
            messages.put(userMsg)
            json.put("messages", messages)
            json.put("max_tokens", 2000)
            json.put("temperature", 0.85)

            withContext(Dispatchers.IO) {
                val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as java.net.HttpURLConnection)
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $openRouterKey")
                connection.doOutput = true
                connection.outputStream.write(json.toString().toByteArray())
                
                val response = connection.inputStream.bufferedReader().readText()
                val responseJson = JSONObject(response)
                responseJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter fallback to Gemini: ${e.message}")
            callGemini(prompt)
        }
    }

    private suspend fun callGemini(prompt: String): String {
        return try {
            val json = JSONObject()
            val contents = org.json.JSONArray()
            val part = JSONObject()
            part.put("text", prompt)
            val contentObj = JSONObject()
            contentObj.put("parts", contents.put(part))
            contents.remove(contents.length() - 1)
            contents.put(contentObj)
            json.put("contents", contents)
            json.put("generationConfig", JSONObject().apply {
                put("temperature", 0.85)
                put("maxOutputTokens", 2000)
            })

            withContext(Dispatchers.IO) {
                val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiApiKey"
                val connection = (URL(urlStr).openConnection() as java.net.HttpURLConnection)
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.write(json.toString().toByteArray())

                val response = connection.inputStream.bufferedReader().readText()
                val responseJson = JSONObject(response)
                responseJson.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini error: ${e.message}")
            "Erro ao gerar conteúdo. Tente novamente."
        }
    }
}
