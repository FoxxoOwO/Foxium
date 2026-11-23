package com.chrogecko.browser.feature.discover

import com.chrogecko.browser.core.network.NetworkTargetPolicy
import com.chrogecko.browser.feature.favicons.faviconHost
import java.io.StringReader
import java.net.InetAddress
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** Parses RSS 2.0 / 1.0 and Atom feeds into [DiscoverItem] cards without touching network or local DTDs. */
internal object RssFeedParser {

    fun normalizeFeedUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return null
        val rawHost = uri.host?.trim()?.lowercase()?.trimEnd('.') ?: return null
        val host = rawHost.removePrefix("[").removeSuffix("]")
        if (host.isEmpty() || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            return null
        }
        if (isIpLiteral(host)) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
            if (NetworkTargetPolicy.isNonPublic(address)) return null
        } else if (!host.contains('.')) {
            return null
        }
        return runCatching { uri.toASCIIString() }.getOrNull()
    }

    fun parse(xml: String, feedUrl: String, limit: Int = 24): List<DiscoverItem> {
        if (xml.isBlank() || limit <= 0) return emptyList()
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                setSecureFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setSecureFeature("http://xml.org/sax/features/external-general-entities", false)
                setSecureFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setSecureFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        }.getOrNull() ?: return emptyList()

        val root = document.documentElement ?: return emptyList()
        val baseUri = runCatching { URI(feedUrl) }.getOrNull()
        val entries = collectFeedElements(root)
        val seenUrls = HashSet<String>()
        val results = ArrayList<DiscoverItem>()
        for (entry in entries) {
            val item = toDiscoverItem(entry, baseUri) ?: continue
            if (seenUrls.add(item.url)) {
                results.add(item)
                if (results.size >= limit) break
            }
        }
        return results
    }

    private fun collectFeedElements(root: Element): List<Element> {
        val result = ArrayList<Element>()
        val all = root.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val element = all.item(i) as? Element ?: continue
            val name = localNameOf(element)
            if (name == "item" || name == "entry") {
                result.add(element)
            }
        }
        return result
    }

    private fun toDiscoverItem(entry: Element, baseUri: URI?): DiscoverItem? {
        val title = cleanTitle(firstChildText(entry, "title").orEmpty())
        if (title.isBlank()) return null
        val rawLink = extractLink(entry) ?: return null
        val resolved = resolveHttpUrl(rawLink, baseUri) ?: return null
        val host = faviconHost(resolved) ?: return null
        return DiscoverItem(title = title, url = resolved, host = host)
    }

    private fun extractLink(entry: Element): String? {
        val links = childElements(entry, "link")
        val alternateHref = links.firstOrNull { linkEl ->
            val rel = linkEl.getAttribute("rel").trim()
            val href = linkEl.getAttribute("href").trim()
            href.isNotEmpty() && (rel.isEmpty() || rel.equals("alternate", ignoreCase = true))
        }?.getAttribute("href")?.trim()
        if (!alternateHref.isNullOrEmpty()) return alternateHref

        val anyHref = links.firstNotNullOfOrNull { linkEl ->
            linkEl.getAttribute("href").trim().takeIf { it.isNotEmpty() }
        }
        if (!anyHref.isNullOrEmpty()) return anyHref

        val textLink = links.firstNotNullOfOrNull { linkEl ->
            linkEl.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }
        if (!textLink.isNullOrEmpty()) return textLink

        val guid = childElements(entry, "guid").firstOrNull { el ->
            !el.getAttribute("isPermaLink").equals("false", ignoreCase = true)
        }?.textContent?.trim()
        if (!guid.isNullOrEmpty() && (guid.startsWith("http://", true) || guid.startsWith("https://", true))) {
            return guid
        }

        val atomId = firstChildText(entry, "id")?.trim()
        if (!atomId.isNullOrEmpty() && (atomId.startsWith("http://", true) || atomId.startsWith("https://", true))) {
            return atomId
        }
        return null
    }

    private fun resolveHttpUrl(raw: String, baseUri: URI?): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val resolved = runCatching {
            val uri = URI(trimmed)
            if (uri.isAbsolute) uri else baseUri?.resolve(uri) ?: uri
        }.getOrNull() ?: return null
        val scheme = resolved.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        return runCatching { resolved.toASCIIString() }.getOrNull()
    }

    private fun childElements(parent: Element, localName: String): List<Element> {
        val matches = ArrayList<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && localNameOf(node as Element) == localName) {
                matches.add(node)
            }
        }
        return matches
    }

    private fun firstChildText(parent: Element, localName: String): String? =
        childElements(parent, localName).firstOrNull()?.textContent

    private fun localNameOf(element: Element): String =
        (element.localName ?: element.tagName).substringAfter(':').lowercase()

    private fun cleanTitle(raw: String): String {
        val unescaped = decodeEntities(raw)
        val stripped = HTML_TAG.replace(unescaped, " ")
        return WHITESPACE.replace(stripped, " ").trim()
    }

    private fun decodeEntities(input: String): String {
        var text = input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        text = NUMERIC_ENTITY.replace(text) { match ->
            val hex = match.groupValues[1].isNotEmpty()
            val digits = if (hex) match.groupValues[1] else match.groupValues[2]
            val code = digits.toIntOrNull(if (hex) 16 else 10)
            if (code != null && code in 1..0x10FFFF && Character.isValidCodePoint(code)) {
                String(Character.toChars(code))
            } else {
                match.value
            }
        }
        return text
    }

    private fun DocumentBuilderFactory.setSecureFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun isIpLiteral(host: String): Boolean =
        ':' in host || host.all { it in '0'..'9' || it == '.' }

    private val HTML_TAG = Regex("<[^>]+>")
    private val WHITESPACE = Regex("\\s+")
    private val NUMERIC_ENTITY = Regex("&#(?:[xX]([0-9a-fA-F]+)|([0-9]+));")
}
