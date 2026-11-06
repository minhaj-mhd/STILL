package app.still.launcher.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class WordBooksTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun document(body: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="$w" xmlns:r="$r"><w:body>$body<w:sectPr/></w:body></w:document>"""

    private val styles = """<?xml version="1.0"?><w:styles xmlns:w="$w">
        <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/></w:style>
        <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/></w:style>
        </w:styles>"""

    private val numbering = """<?xml version="1.0"?><w:numbering xmlns:w="$w">
        <w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/></w:lvl></w:abstractNum>
        <w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/></w:lvl></w:abstractNum>
        <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
        <w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
        </w:numbering>"""

    private val relationships = """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId9" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.com/a?b=1&amp;c=2" TargetMode="External"/>
        </Relationships>"""

    private fun paragraph(text: String, style: String? = null, list: Pair<Int, Int>? = null, bold: Boolean = false) = buildString {
        append("<w:p><w:pPr>")
        if (style != null) append("""<w:pStyle w:val="$style"/>""")
        if (list != null) append("""<w:numPr><w:ilvl w:val="${list.second}"/><w:numId w:val="${list.first}"/></w:numPr>""")
        append("</w:pPr><w:r>")
        if (bold) append("<w:rPr><w:b/></w:rPr>")
        append("""<w:t xml:space="preserve">$text</w:t></w:r></w:p>""")
    }

    private fun docx(body: String): File {
        val file = folder.newFile("read.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>")
            put(
                "_rels/.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            )
            put("word/document.xml", document(body))
            put("word/styles.xml", styles)
            put("word/numbering.xml", numbering)
            put("word/_rels/document.xml.rels", relationships)
        }
        return file
    }

    @Test
    fun `a Word read becomes an EPUB readers accept`() {
        val body = paragraph("The law of attraction", style = "Title") +
            paragraph("Thought is a force &amp; a power.") +
            paragraph("Waves", style = "Heading1") +
            paragraph("First", list = 1 to 0) +
            paragraph("Second", list = 1 to 0, bold = true) +
            paragraph("One", list = 2 to 0) +
            """<w:p><w:hyperlink r:id="rId9"><w:r><w:t>a link</w:t></w:r></w:hyperlink></w:p>""" +
            "<w:p/>" +
            """<w:tbl><w:tr><w:tc><w:p><w:r><w:t>cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"""
        val epub = folder.root.resolve("read.epub")
        wordToEpub(docx(body), epub, "The law of attraction", "en")

        ZipFile(epub).use { zip ->
            val first = zip.entries().nextElement()
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            val text = zip.getInputStream(zip.getEntry("OEBPS/text.xhtml")).bufferedReader().readText()
            assertTrue(text, text.contains("""<h1 id="h-1">The law of attraction</h1>"""))
            assertTrue(text, text.contains("<p>Thought is a force &amp; a power.</p>"))
            assertTrue(text, text.contains("""<h1 id="h-2">Waves</h1>"""))
            assertTrue(text, text.contains("<ul>\n<li>First</li>\n<li><strong>Second</strong></li>\n</ul>\n<ol>\n<li>One</li>\n</ol>"))
            assertTrue(text, text.contains("""<a href="https://example.com/a?b=1&amp;c=2">a link</a>"""))
            assertTrue(text, text.contains("<td>cell</td>"))
            assertFalse(text, text.contains("<p></p>"))
            val nav = zip.getInputStream(zip.getEntry("OEBPS/nav.xhtml")).bufferedReader().readText()
            assertTrue(nav, nav.contains("""<a href="text.xhtml#h-2">Waves</a>"""))
            // Every page is well-formed XML.
            listOf("OEBPS/text.xhtml", "OEBPS/nav.xhtml", "OEBPS/content.opf", "META-INF/container.xml").forEach { path ->
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(zip.getInputStream(zip.getEntry(path)))
            }
        }
        assertFalse(folder.root.resolve("read.epub.part").exists())
    }

    @Test
    fun `nested lists stay inside their item`() {
        val html = wordToXhtml(
            document = document(paragraph("Outer", list = 1 to 0) + paragraph("Inner", list = 1 to 1) + paragraph("Back", list = 1 to 0)).toByteArray(),
            styles = null,
            relationships = null,
            numbering = numbering.toByteArray(),
        ).html
        assertEquals("<ul>\n<li>Outer<ul>\n<li>Inner</li>\n</ul>\n</li>\n<li>Back</li>\n</ul>\n", html)
    }

    @Test(expected = NotAWordDocument::class)
    fun `a file that isn't Word says so`() {
        val file = folder.newFile("not.docx").apply { writeText("hello") }
        wordToEpub(file, folder.root.resolve("not.epub"), "Not", "en")
    }
}
