package com.codotype.jarvis.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ln

/** One retrievable passage of local knowledge. */
data class Chunk(
    val source: String,
    val text: String,
    val tokens: List<String>
)

/**
 * A tiny, dependency-free retrieval index (BM25) over plain text knowledge
 * packs stored in the app's private storage. No embeddings model, no network —
 * it works on any device and keeps the APK small.
 *
 * Knowledge packs are produced by exporting a Hugging Face dataset to JSONL
 * (one {"text": "..."} per line) and dropping it in the knowledge folder. See
 * docs/KNOWLEDGE.md for the export recipe.
 */
class KnowledgeBase(private val dir: File) {

    private var chunks: List<Chunk> = emptyList()
    private var docFreq: MutableMap<String, Int> = mutableMapOf()
    private var avgLen: Double = 1.0

    val size: Int get() = chunks.size

    suspend fun load(): Int = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val loaded = ArrayList<Chunk>()
        dir.listFiles()?.forEach { file ->
            when (file.extension.lowercase()) {
                "jsonl" -> loaded += parseJsonl(file)
                "txt", "md" -> loaded += parseText(file)
            }
        }
        buildIndex(loaded)
        loaded.size
    }

    private fun parseJsonl(file: File): List<Chunk> {
        val out = ArrayList<Chunk>()
        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachLine
            val text = extractTextField(trimmed)
            if (text.isNotBlank()) out += toChunks(file.name, text)
        }
        return out
    }

    /** Minimal, forgiving extraction of a "text"/"content" field from a JSONL row. */
    private fun extractTextField(line: String): String {
        val keys = listOf("\"text\"", "\"content\"", "\"body\"", "\"passage\"")
        for (k in keys) {
            val idx = line.indexOf(k)
            if (idx >= 0) {
                val colon = line.indexOf(':', idx + k.length)
                if (colon >= 0) {
                    val start = line.indexOf('"', colon + 1)
                    if (start >= 0) {
                        val sb = StringBuilder()
                        var i = start + 1
                        while (i < line.length) {
                            val c = line[i]
                            if (c == '\\' && i + 1 < line.length) {
                                val n = line[i + 1]
                                sb.append(
                                    when (n) {
                                        'n' -> '\n'; 't' -> '\t'; '"' -> '"'; '\\' -> '\\'
                                        else -> n
                                    }
                                )
                                i += 2
                                continue
                            }
                            if (c == '"') break
                            sb.append(c); i++
                        }
                        return sb.toString()
                    }
                }
            }
        }
        return line
    }

    private fun parseText(file: File): List<Chunk> {
        val out = ArrayList<Chunk>()
        file.readText().split("\n\n").forEach { para ->
            val t = para.trim()
            if (t.length >= 40) out += toChunks(file.name, t)
        }
        return out
    }

    /** Splits long passages into ~800-char windows with a small overlap. */
    private fun toChunks(source: String, text: String): List<Chunk> {
        val maxLen = 800
        if (text.length <= maxLen) return listOf(Chunk(source, text, tokenize(text)))
        val out = ArrayList<Chunk>()
        var start = 0
        while (start < text.length) {
            val end = (start + maxLen).coerceAtMost(text.length)
            val slice = text.substring(start, end)
            out += Chunk(source, slice, tokenize(slice))
            if (end == text.length) break
            start = end - 80
        }
        return out
    }

    private fun buildIndex(loaded: List<Chunk>) {
        chunks = loaded
        docFreq = mutableMapOf()
        for (c in chunks) {
            for (term in c.tokens.toHashSet()) {
                docFreq[term] = (docFreq[term] ?: 0) + 1
            }
        }
        avgLen = if (chunks.isEmpty()) 1.0 else chunks.sumOf { it.tokens.size }.toDouble() / chunks.size
    }

    /** Returns the top-[k] passages most relevant to [query]. */
    fun retrieve(query: String, k: Int = 3): List<Chunk> {
        if (chunks.isEmpty()) return emptyList()
        val qTerms = tokenize(query).distinct()
        if (qTerms.isEmpty()) return emptyList()

        val n = chunks.size
        val k1 = 1.5
        val b = 0.75
        val scored = chunks.map { c ->
            val tf = c.tokens.groupingBy { it }.eachCount()
            var score = 0.0
            for (term in qTerms) {
                val f = tf[term] ?: continue
                val df = docFreq[term] ?: 0
                val idf = ln(1 + (n - df + 0.5) / (df + 0.5))
                val denom = f + k1 * (1 - b + b * c.tokens.size / avgLen)
                score += idf * (f * (k1 + 1)) / denom
            }
            c to score
        }
        return scored.filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9\\u0900-\\u097F]+"))
            .filter { it.length >= 2 }

    fun clear() {
        chunks = emptyList()
        docFreq.clear()
        avgLen = 1.0
    }
}
