package com.gameaicoach

import org.json.JSONArray
import org.json.JSONObject

object GameParser {
    private val genericUi = setOf(
        "home","voltar","back","menu","shop","loja","store","settings","configurações","configuracoes","event","evento",
        "claim","resgatar","collect","coletar","team","equipe","squad","time","level","nível","nivel","power","poder","gear",
        "iso","ability","abilities","habilidade","habilidades","cosmo","cosmos","driver","piloto","component","componente","series","série","serie"
    )

    fun parse(game: String, frames: List<List<String>>): JSONObject {
        val lines = frames.flatten().map { it.trim() }.filter { it.length > 1 }
        val unique = lines.distinct()
        val all = unique.joinToString(" ").lowercase()
        return when {
            game.startsWith("Saint") -> parseSaint(unique, all)
            game.startsWith("Marvel") -> parseMsf(unique, all)
            else -> parseF1(unique, all)
        }
    }

    private fun parseMsf(lines: List<String>, all: String): JSONObject {
        val coverage = linkedMapOf(
            "Roster" to has(all, "roster", "personagens", "characters"),
            "Detalhe do personagem" to has(all, "power", "poder", "level", "nível", "nivel"),
            "Gear" to has(all, "gear", "equip", "tier"),
            "ISO-8" to has(all, "iso-8", "iso 8", "iso"),
            "Habilidades" to has(all, "abilities", "ability", "habilidade", "habilidades"),
            "Recursos" to has(all, "gold", "ouro", "cores", "energy", "energia", "training"),
            "Modos" to has(all, "raid", "arena", "war", "crucible", "blitz")
        )
        val entities = candidateEntities(lines, setOf("gear","iso","power","poder","ability","level","lvl","star","estrela"))
        val metrics = linkedMapOf<String, String>()
        extractFirst(lines, Regex("(?i)(?:power|poder)\\s*[: -]?\\s*([0-9.,]+)"))?.let { metrics["Poder"] = it }
        extractFirst(lines, Regex("(?i)(?:level|nível|nivel|lvl)\\s*[: -]?\\s*(\\d{1,3})"))?.let { metrics["Nível"] = it }
        extractFirst(lines, Regex("(?i)(?:gear|tier)\\s*[: -]?\\s*(\\d{1,2})"))?.let { metrics["Gear"] = it }
        val recommendations = JSONArray().apply {
            if (!coverage.getValue("Roster")) put("Abra o Roster durante o uso normal para o Coach mapear seus personagens automaticamente.")
            if (!coverage.getValue("Gear")) put("Quando abrir um personagem, passe pela aba de Gear; isso melhora a prioridade de evolução.")
            if (!coverage.getValue("ISO-8")) put("Passe pela tela ISO-8 dos personagens que você realmente usa; não é necessário abrir todos.")
            if (coverage.getValue("Recursos")) put("Recursos foram detectados. O Coach vai comparar o próximo uso para identificar onde você está gastando mais.")
            put("Priorize registrar naturalmente os personagens que você usa em Raid, Arena e Crucible; eles terão maior peso no plano diário.")
        }
        return buildResult("Marvel Strike Force", lines, coverage, entities, metrics, recommendations)
    }

    private fun parseSaint(lines: List<String>, all: String): JSONObject {
        val coverage = linkedMapOf(
            "Lista de Cavaleiros" to has(all, "cavaleiro", "saint", "knight"),
            "Detalhe do Cavaleiro" to has(all, "poder", "power", "nível", "nivel", "level"),
            "Skills" to has(all, "skill", "habilidade"),
            "Cosmos" to has(all, "cosmo", "cosmos"),
            "Oitavo Sentido" to has(all, "oitavo", "eighth sense"),
            "Armadura" to has(all, "armadura", "armor", "cloth"),
            "Recursos/Eventos" to has(all, "gema", "gem", "evento", "event", "stamina", "diamante", "diamond")
        )
        val entities = candidateEntities(lines, setOf("cosmo","skill","poder","power","nível","nivel","level","armadura","armor"))
        val metrics = linkedMapOf<String, String>()
        extractFirst(lines, Regex("(?i)(?:poder|power)\\s*[: -]?\\s*([0-9.,]+)"))?.let { metrics["Poder"] = it }
        extractFirst(lines, Regex("(?i)(?:nível|nivel|level)\\s*[: -]?\\s*(\\d{1,3})"))?.let { metrics["Nível"] = it }
        val recommendations = JSONArray().apply {
            if (!coverage.getValue("Lista de Cavaleiros")) put("Passe pela lista de Cavaleiros durante o jogo para o Coach construir seu roster sem vídeo.")
            if (!coverage.getValue("Cosmos")) put("Abra Cosmos apenas dos Cavaleiros que você usa ou que estiver evoluindo; o Coach mantém os demais dados antigos.")
            if (!coverage.getValue("Skills")) put("Ao evoluir uma habilidade, deixe a tela de Skills visível por alguns segundos para registrar a mudança.")
            put("O Coach vai comparar as próximas sessões para identificar quais Cavaleiros realmente mudaram e evitar revisar todos diariamente.")
        }
        return buildResult("Saint Seiya Awakening", lines, coverage, entities, metrics, recommendations)
    }

    private fun parseF1(lines: List<String>, all: String): JSONObject {
        val coverage = linkedMapOf(
            "Pilotos" to has(all, "driver", "piloto"),
            "Componentes" to has(all, "component", "componente", "power unit", "aero"),
            "Carro" to has(all, "car", "carro", "chassis", "engine", "motor"),
            "Moedas/Bucks" to has(all, "coins", "moedas", "bucks", "cash"),
            "Séries" to has(all, "series", "série", "serie"),
            "Eventos" to has(all, "event", "evento"),
            "Corridas" to has(all, "race", "corrida", "lap", "volta")
        )
        val entities = candidateEntities(lines, setOf("driver","piloto","component","componente","level","nível","nivel","series","série","serie"))
        val metrics = linkedMapOf<String, String>()
        extractFirst(lines, Regex("(?i)(?:nível|nivel|level)\\s*[: -]?\\s*(\\d{1,3})"))?.let { metrics["Nível"] = it }
        val recommendations = JSONArray().apply {
            if (!coverage.getValue("Pilotos")) put("Passe pela lista de pilotos durante o uso normal para registrar os níveis e opções atuais.")
            if (!coverage.getValue("Componentes")) put("Abra Componentes quando fizer upgrade; o Coach usará essas mudanças para sugerir onde gastar moedas.")
            if (!coverage.getValue("Séries")) put("Abra a tela da série atual para contextualizar as recomendações de pilotos e componentes.")
            put("Como a pista é aleatória, o Coach deve focar em evolução de conta, eficiência de upgrades e preparação de duplas, não em prever uma pista específica.")
        }
        return buildResult("F1 Clash", lines, coverage, entities, metrics, recommendations)
    }

    private fun buildResult(game: String, lines: List<String>, coverage: LinkedHashMap<String, Boolean>, entities: List<String>, metrics: Map<String,String>, recommendations: JSONArray): JSONObject {
        val covered = coverage.values.count { it }
        val percent = if (coverage.isEmpty()) 0 else (covered * 100 / coverage.size)
        return JSONObject().apply {
            put("game", game)
            put("uniqueLines", lines.distinct().size)
            put("coveragePercent", percent)
            put("coverage", JSONObject().apply { coverage.forEach { (k,v) -> put(k,v) } })
            put("entities", JSONArray(entities.take(80)))
            put("metrics", JSONObject(metrics))
            put("recommendations", recommendations)
            put("status", when {
                percent >= 80 -> "Conta bem mapeada"
                percent >= 50 -> "Mapeamento parcial útil"
                else -> "Aprendendo sua conta"
            })
        }
    }

    private fun has(all: String, vararg words: String): Boolean = words.any { all.contains(it.lowercase()) }

    private fun candidateEntities(lines: List<String>, contextWords: Set<String>): List<String> {
        val out = linkedSetOf<String>()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (!looksLikeName(line)) return@forEachIndexed
            val neighborhood = lines.subList(maxOf(0,index-2), minOf(lines.size,index+3)).joinToString(" ").lowercase()
            if (contextWords.any { neighborhood.contains(it) }) out.add(line)
        }
        return out.toList()
    }

    private fun looksLikeName(line: String): Boolean {
        val l = line.lowercase()
        if (line.length !in 3..32) return false
        if (l in genericUi) return false
        if (line.count { it.isDigit() } > 2) return false
        if (!line.any { it.isLetter() }) return false
        if (line.contains("http", true)) return false
        return line.matches(Regex("[A-Za-zÀ-ÿ0-9 .&'’_-]+"))
    }

    private fun extractFirst(lines: List<String>, regex: Regex): String? {
        lines.forEach { line -> regex.find(line)?.groups?.get(1)?.value?.let { return it } }
        return null
    }
}
