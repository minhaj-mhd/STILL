package app.still.launcher.read

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** A Word file that can't be turned into a book. */
class NotAWordDocument(message: String, cause: Throwable? = null) : IOException(message, cause)

/** A heading in a converted document, for the book's contents. */
data class WordHeading(val id: String, val text: String, val level: Int)

/** A Word document's body as XHTML, with its headings. */
data class WordBody(val html: String, val headings: List<WordHeading>)

private const val MAX_PART_BYTES = 32L * 1024 * 1024

/**
 * Turns the Word (.docx) document in [docx] into an EPUB at [epub] called [title], so it opens in
 * STILL Read's EPUB reader. Text, headings, bold and italic, lists, links and tables come across;
 * pictures don't.
 */
fun wordToEpub(docx: File, epub: File, title: String, language: String) = saveEpub(epub, title, readWord(docx), language)

/** The body of the Word (.docx) document in [docx], with its headings. */
fun readWord(docx: File): WordBody =
    try {
        ZipFile(docx).use { zip ->
            fun part(name: String): ByteArray? {
                val entry = zip.getEntry(name) ?: return null
                return zip.getInputStream(entry).use { input ->
                    val bytes = input.readNBytesLimited(MAX_PART_BYTES)
                    bytes ?: throw NotAWordDocument("$name is too big")
                }
            }
            val main = mainDocumentPath(part("_rels/.rels"))
            val document = part(main) ?: throw NotAWordDocument("No document in this file")
            val folder = main.substringBeforeLast('/', "")
            val prefix = if (folder.isEmpty()) "" else "$folder/"
            wordToXhtml(
                document = document,
                styles = part("${prefix}styles.xml"),
                relationships = part("${prefix}_rels/${main.substringAfterLast('/')}.rels"),
                numbering = part("${prefix}numbering.xml"),
            )
        }
    } catch (e: NotAWordDocument) {
        throw e
    } catch (e: Exception) {
        throw NotAWordDocument("Couldn't read this Word file", e)
    }

/** Saves [body] as an EPUB at [epub] called [title]. */
fun saveEpub(epub: File, title: String, body: WordBody, language: String) {
    val partial = File(epub.parentFile, epub.name + ".part")
    partial.outputStream().use { writeEpub(it, title, body, language) }
    if (!partial.renameTo(epub)) {
        partial.delete()
        throw IOException("Couldn't save ${epub.name}")
    }
}

/** Where the package says its main document is; nearly always word/document.xml. */
private fun mainDocumentPath(packageRelationships: ByteArray?): String {
    val fallback = "word/document.xml"
    val root = packageRelationships?.let { runCatching { parseXml(it) }.getOrNull() } ?: return fallback
    return root.kids().firstOrNull { it.attr("Type")?.endsWith("/officeDocument") == true }
        ?.attr("Target")?.trimStart('/')
        ?: fallback
}

/** A Word document's body as XHTML. The parts besides [document] add headings, links and list types. */
fun wordToXhtml(document: ByteArray, styles: ByteArray?, relationships: ByteArray?, numbering: ByteArray?): WordBody =
    WordConverter(
        styleNames = styles?.let(::styleNamesOf).orEmpty(),
        links = relationships?.let(::linksOf).orEmpty(),
        ordered = numbering?.let(::orderedListsOf).orEmpty(),
    ).convert(parseXml(document))

/** Style ids to their names, lowercased: "heading 1", "title". */
private fun styleNamesOf(bytes: ByteArray): Map<String, String> =
    parseXml(bytes).kids("style").mapNotNull { style ->
        val id = style.attr("styleId") ?: return@mapNotNull null
        val name = style.kids("name").firstOrNull()?.attr("val") ?: id
        id to name.lowercase()
    }.toMap()

/** Relationship ids to external link targets. */
private fun linksOf(bytes: ByteArray): Map<String, String> =
    parseXml(bytes).kids().mapNotNull { relationship ->
        val id = relationship.attr("Id") ?: return@mapNotNull null
        val target = relationship.attr("Target") ?: return@mapNotNull null
        if (relationship.attr("Type")?.endsWith("/hyperlink") != true) return@mapNotNull null
        id to target
    }.toMap()

/** Which list numbering (by num id and level) counts rather than bullets. */
private fun orderedListsOf(bytes: ByteArray): Map<Pair<String, Int>, Boolean> {
    val root = parseXml(bytes)
    val abstracts = root.kids("abstractNum").associate { abstract ->
        abstract.attr("abstractNumId").orEmpty() to abstract.kids("lvl").associate { level ->
            (level.attr("ilvl")?.toIntOrNull() ?: 0) to (level.kids("numFmt").firstOrNull()?.attr("val") ?: "bullet")
        }
    }
    return buildMap {
        root.kids("num").forEach { num ->
            val numId = num.attr("numId") ?: return@forEach
            val abstractId = num.kids("abstractNumId").firstOrNull()?.attr("val") ?: return@forEach
            abstracts[abstractId]?.forEach { (level, format) -> put(numId to level, format != "bullet" && format != "none") }
        }
    }
}

private class WordConverter(
    private val styleNames: Map<String, String>,
    private val links: Map<String, String>,
    private val ordered: Map<Pair<String, Int>, Boolean>,
) {
    private val html = StringBuilder()
    private val headings = mutableListOf<WordHeading>()

    private class OpenList(val ordered: Boolean, var itemOpen: Boolean = false)

    private val lists = ArrayDeque<OpenList>()

    fun convert(document: Element): WordBody {
        val body = document.kids("body").firstOrNull() ?: throw NotAWordDocument("No body in this document")
        blocks(body)
        closeLists(0)
        return WordBody(html.toString(), headings)
    }

    private fun blocks(parent: Element) {
        parent.kids().forEach { child ->
            when (child.name) {
                "p" -> paragraph(child)
                "tbl" -> {
                    closeLists(0)
                    table(child)
                }
                "sdt", "sdtContent", "customXml", "ins" -> blocks(child)
            }
        }
    }

    private fun paragraph(p: Element) {
        val properties = p.kids("pPr").firstOrNull()
        val style = properties?.kids("pStyle")?.firstOrNull()?.attr("val")
        val styleName = style?.let { styleNames[it] ?: it.lowercase() }.orEmpty()
        val (inline, text) = inline(p)
        if (text.isBlank()) return
        val heading = headingLevel(styleName, properties)
        val list = properties?.kids("numPr")?.firstOrNull()
        val numId = list?.kids("numId")?.firstOrNull()?.attr("val")
        when {
            heading != null -> {
                closeLists(0)
                val id = "h-${headings.size + 1}"
                headings += WordHeading(id, text.trim(), heading)
                html.append("<h").append(heading).append(" id=\"").append(id).append("\">").append(inline).append("</h").append(heading).append(">\n")
            }
            numId != null && numId != "0" -> {
                val level = list?.kids("ilvl")?.firstOrNull()?.attr("val")?.toIntOrNull()?.coerceIn(0, MAX_LIST_DEPTH - 1) ?: 0
                listItem(level, ordered[numId to level] ?: false, inline)
            }
            styleName.contains("list") -> listItem(0, ordered = false, inline)
            styleName.contains("quote") -> {
                closeLists(0)
                html.append("<blockquote><p>").append(inline).append("</p></blockquote>\n")
            }
            else -> {
                closeLists(0)
                html.append("<p>").append(inline).append("</p>\n")
            }
        }
    }

    private fun headingLevel(styleName: String, properties: Element?): Int? {
        if (styleName == "title") return 1
        if (styleName == "subtitle") return 2
        Regex("""heading\s*(\d)""").find(styleName)?.let { return it.groupValues[1].toInt().coerceIn(1, 6) }
        val outline = properties?.kids("outlineLvl")?.firstOrNull()?.attr("val")?.toIntOrNull()
        return outline?.takeIf { it in 0..5 }?.plus(1)
    }

    private fun listItem(level: Int, ordered: Boolean, inline: String) {
        val depth = level + 1
        while (lists.size > depth) closeTop()
        if (lists.size == depth && lists.last().ordered != ordered) closeTop()
        if (lists.size == depth && lists.last().itemOpen) {
            html.append("</li>\n")
            lists.last().itemOpen = false
        }
        while (lists.size < depth) {
            html.append(if (ordered) "<ol>\n" else "<ul>\n")
            lists.addLast(OpenList(ordered))
        }
        html.append("<li>").append(inline)
        lists.last().itemOpen = true
    }

    private fun closeLists(depth: Int) {
        while (lists.size > depth) closeTop()
    }

    private fun closeTop() {
        val top = lists.removeLast()
        if (top.itemOpen) html.append("</li>\n")
        html.append(if (top.ordered) "</ol>\n" else "</ul>\n")
        // The list it was inside stays on its item, so what comes next carries on there.
    }

    private fun table(table: Element) {
        html.append("<table>\n")
        table.kids("tr").forEach { row ->
            html.append("<tr>")
            row.kids("tc").forEach { cell ->
                val lines = cell.kids("p").map { inline(it).first }.filter { it.isNotBlank() }
                html.append("<td>").append(lines.joinToString("<br/>")).append("</td>")
            }
            html.append("</tr>\n")
        }
        html.append("</table>\n")
    }

    /** A paragraph's runs as XHTML, and as plain text. */
    private fun inline(parent: Element): Pair<String, String> {
        val out = StringBuilder()
        val text = StringBuilder()
        fun walk(element: Element) {
            element.kids().forEach { child ->
                when (child.name) {
                    "r" -> run(child, out, text)
                    "hyperlink" -> {
                        val target = child.attr("id")?.let(links::get)?.takeIf { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("mailto:") }
                        if (target != null) out.append("<a href=\"").append(escapeXml(target)).append("\">")
                        walk(child)
                        if (target != null) out.append("</a>")
                    }
                    "ins", "smartTag", "sdt", "sdtContent", "customXml", "fldSimple" -> walk(child)
                }
            }
        }
        walk(parent)
        return out.toString() to text.toString()
    }

    private fun run(run: Element, out: StringBuilder, text: StringBuilder) {
        val properties = run.kids("rPr").firstOrNull()
        val characterStyle = properties?.kids("rStyle")?.firstOrNull()?.attr("val")?.let { styleNames[it] ?: it.lowercase() }.orEmpty()
        val bold = properties.isOn("b") || characterStyle == "strong"
        val italic = properties.isOn("i") || characterStyle == "emphasis"
        val underline = properties?.kids("u")?.firstOrNull()?.attr("val")?.let { it != "none" } ?: false
        val struck = properties.isOn("strike")
        val content = StringBuilder()
        run.kids().forEach { child ->
            when (child.name) {
                "t" -> {
                    content.append(escapeXml(child.textContent))
                    text.append(child.textContent)
                }
                "tab" -> {
                    content.append(' ')
                    text.append(' ')
                }
                "br", "cr" -> {
                    content.append("<br/>")
                    text.append(' ')
                }
                "noBreakHyphen" -> {
                    content.append('-')
                    text.append('-')
                }
            }
        }
        if (content.isEmpty()) return
        var wrapped = content.toString()
        if (struck) wrapped = "<s>$wrapped</s>"
        if (underline) wrapped = "<u>$wrapped</u>"
        if (italic) wrapped = "<em>$wrapped</em>"
        if (bold) wrapped = "<strong>$wrapped</strong>"
        out.append(wrapped)
    }

    private fun Element?.isOn(property: String): Boolean {
        val element = this?.kids(property)?.firstOrNull() ?: return false
        return element.attr("val")?.lowercase() !in setOf("0", "false", "off", "none")
    }

    private companion object {
        const val MAX_LIST_DEPTH = 6
    }
}

/** Writes an EPUB 3 with one chapter, [body], and contents from its headings. */
fun writeEpub(out: OutputStream, title: String, body: WordBody, language: String) {
    val lang = escapeXml(language.ifBlank { "en" })
    val name = escapeXml(title.ifBlank { "Untitled" })
    val modified = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    ZipOutputStream(out).use { zip ->
        // The EPUB's type comes first and uncompressed, as readers look for it there.
        val type = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        zip.putNextEntry(
            ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = type.size.toLong()
                compressedSize = type.size.toLong()
                crc = CRC32().apply { update(type) }.value
            },
        )
        zip.write(type)
        zip.closeEntry()
        fun put(path: String, content: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        put(
            "META-INF/container.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
""",
        )
        put(
            "OEBPS/content.opf",
            """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="$lang">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="book-id">urn:uuid:${UUID.randomUUID()}</dc:identifier>
<dc:title>$name</dc:title>
<dc:language>$lang</dc:language>
<meta property="dcterms:modified">$modified</meta>
</metadata>
<manifest>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="text" href="text.xhtml" media-type="application/xhtml+xml"/>
</manifest>
<spine><itemref idref="text"/></spine>
</package>
""",
        )
        val contents = buildString {
            append("<li><a href=\"text.xhtml\">").append(name).append("</a>")
            val listed = body.headings.filter { it.level <= 3 }
            if (listed.isNotEmpty()) {
                append("<ol>")
                listed.forEach { append("<li><a href=\"text.xhtml#").append(it.id).append("\">").append(escapeXml(it.text)).append("</a></li>") }
                append("</ol>")
            }
            append("</li>")
        }
        put(
            "OEBPS/nav.xhtml",
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="$lang" xml:lang="$lang">
<head><meta charset="UTF-8"/><title>$name</title></head>
<body><nav epub:type="toc" id="toc"><ol>$contents</ol></nav></body>
</html>
""",
        )
        put(
            "OEBPS/text.xhtml",
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="$lang" xml:lang="$lang">
<head><meta charset="UTF-8"/><title>$name</title></head>
<body>
${body.html}</body>
</html>
""",
        )
    }
}

private fun escapeXml(text: String): String = buildString(text.length) {
    text.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> if (char >= ' ' || char == '\n' || char == '\t') append(char)
        }
    }
}

private fun parseXml(bytes: ByteArray): Element {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        // Word never needs a DTD; refusing them keeps entity tricks out.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }
    return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
}

private val Element.name: String get() = localName ?: nodeName.substringAfter(':')

private fun Element.kids(name: String? = null): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { index ->
        (nodes.item(index) as? Element)?.takeIf { it.nodeType == Node.ELEMENT_NODE && (name == null || it.name == name) }
    }
}

/** An attribute by its local name, whatever its namespace. */
private fun Element.attr(name: String): String? {
    val all = attributes
    for (index in 0 until all.length) {
        val attribute = all.item(index)
        if ((attribute.localName ?: attribute.nodeName.substringAfter(':')) == name) return attribute.nodeValue
    }
    return null
}

/** Reads everything, or null if there's more than [limit] bytes. */
private fun java.io.InputStream.readNBytesLimited(limit: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
