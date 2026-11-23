package com.chrogecko.browser.feature.userscripts

class UserScriptParseException(message: String) : IllegalArgumentException(message)

/** Parsed `==UserScript==` metadata block, Tampermonkey/Violentmonkey compatible. */
data class ParsedUserScript(
    val name: String,
    val namespace: String,
    val version: String,
    val description: String,
    val matchPatterns: List<String>,
    val includePatterns: List<String>,
    val excludePatterns: List<String>,
    val runAt: String,
    val grants: List<String>,
    val requires: List<String>,
    val resources: Map<String, String>,
    val updateUrl: String?,
    val downloadUrl: String?,
    val source: String,
)

/**
 * Parses the userscript metadata block. Deliberately strict: a script without a name or without
 * any `@match`/`@include` would never run, so it is rejected at install time instead of silently
 * sitting in the database.
 */
object UserScriptParser {

    private val METADATA_LINE =
        Regex("""^\s*(?://+|/\*+|\*+)?\s*@([A-Za-z0-9_:-]+)\s*(.*?)\s*(?:\*/)?\s*$""")
    private val FILE_NAME_REGEX = Regex("""//\s*@name\s+(.+)""")
    private val INVALID_FILE_CHARS = Regex("""[\\/:*?"<>|]""")

    private const val START = "==UserScript=="
    private const val END = "==/UserScript=="

    fun parse(rawSource: String): ParsedUserScript {
        val source = rawSource.removePrefix("\uFEFF")
        val block = metadataBlock(source)
            ?: throw UserScriptParseException(
                "Missing ==UserScript== metadata block",
            )

        var name = ""
        var namespace = ""
        var version = ""
        var description = ""
        var runAt = ""
        var updateUrl: String? = null
        var downloadUrl: String? = null
        val matches = mutableListOf<String>()
        val includes = mutableListOf<String>()
        val excludes = mutableListOf<String>()
        val grants = mutableListOf<String>()
        val requires = mutableListOf<String>()
        val resources = linkedMapOf<String, String>()

        block.lineSequence().forEach { line ->
            val result = METADATA_LINE.matchEntire(line) ?: return@forEach
            val key = result.groupValues[1].lowercase()
            val value = result.groupValues[2].trim()
            if (value.isEmpty()) return@forEach
            when (key) {
                "name" -> if (name.isEmpty()) name = value
                "namespace" -> if (namespace.isEmpty()) namespace = value
                "version" -> if (version.isEmpty()) version = value
                "description" -> if (description.isEmpty()) description = value
                "match" -> matches += value
                "include" -> includes += value
                "exclude" -> excludes += value
                "run-at" -> if (runAt.isEmpty()) runAt = value
                "grant" -> if (!value.equals("none", ignoreCase = true)) grants += value
                "require" -> requires += value
                "resource" -> {
                    val separator = value.indexOfFirst { it.isWhitespace() }
                    if (separator > 0) {
                        val resourceName = value.substring(0, separator)
                        val resourceUrl = value.substring(separator + 1).trim()
                        if (resourceUrl.isNotEmpty()) resources[resourceName] = resourceUrl
                    }
                }
                "updateurl" -> if (updateUrl == null) updateUrl = value
                "downloadurl" -> if (downloadUrl == null) downloadUrl = value
            }
        }

        if (name.isBlank()) throw UserScriptParseException("Missing @name in metadata block")
        if (matches.isEmpty() && includes.isEmpty()) {
            throw UserScriptParseException("Script declares no @match or @include patterns")
        }

        return ParsedUserScript(
            name = name,
            namespace = namespace.ifBlank { name },
            version = version,
            description = description,
            matchPatterns = matches.distinct(),
            includePatterns = includes.distinct(),
            excludePatterns = excludes.distinct(),
            runAt = normalizeRunAt(runAt),
            grants = grants.distinct(),
            requires = requires.distinct(),
            resources = resources,
            updateUrl = updateUrl,
            downloadUrl = downloadUrl,
            source = source,
        )
    }

    /** Returns the text between the marker lines, marker lines included. */
    fun metadataBlock(source: String): String? {
        val lines = source.lineSequence().toList()
        val start = lines.indexOfFirst { it.contains(START) && !it.contains(END) }
        if (start < 0) return null
        val end = (start + 1 until lines.size).firstOrNull { lines[it].contains(END) } ?: return null
        return lines.subList(start, end + 1).joinToString("\n")
    }

    fun normalizeRunAt(value: String): String = when {
        value.isEmpty() -> "document-idle"
        value.contains("start", ignoreCase = true) -> "document-start"
        value.contains("body", ignoreCase = true) ||
            value.contains("end", ignoreCase = true) -> "document-end"
        else -> "document-idle"
    }

    fun extractFileName(source: String): String? {
        val match = FILE_NAME_REGEX.find(source) ?: return null
        return match.groupValues[1].trim()
            .replace(INVALID_FILE_CHARS, "_")
            .take(80)
            .ifBlank { null }
    }
}
