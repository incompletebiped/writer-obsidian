package app.writerobsidian.data

import app.writerobsidian.data.RichText.Kind
import app.writerobsidian.data.RichText.Span
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownCodecTest {
    private fun md(text: String, vararg spans: Span) = MarkdownCodec.serialize(RichText(text, spans.toList()))

    /** Parsing then serializing must give back the same Markdown. */
    private fun assertRoundTrip(markdown: String) {
        assertEquals(markdown, MarkdownCodec.serialize(MarkdownCodec.parse(markdown)))
    }

    /** Serializing then parsing must give back the same text and formatting. */
    private fun assertStable(doc: RichText) {
        val parsed = MarkdownCodec.parse(MarkdownCodec.serialize(doc))
        assertEquals(doc.text, parsed.text)
        assertEquals(charStyles(doc), charStyles(parsed))
    }

    private fun charStyles(doc: RichText) = doc.text.indices.map { i ->
        doc.spans.filter { i in it.start until it.end }.map { it.kind to it.value }.toSet()
    }

    @Test
    fun inlineStyles() {
        assertEquals("**bold** and *italic*", md("bold and italic", Span(Kind.Bold, 0, 4), Span(Kind.Italic, 9, 15)))
        assertEquals("<u>under</u> ~~gone~~", md("under gone", Span(Kind.Underline, 0, 5), Span(Kind.Strike, 6, 10)))
    }

    @Test
    fun whitespaceAtStyleEdgesStaysOutsideMarkers() {
        assertEquals("a **bold** b", md("a bold b", Span(Kind.Bold, 1, 7)))
    }

    @Test
    fun overlappingStylesNestAndReopen() {
        val doc = RichText("abc", listOf(Span(Kind.Bold, 0, 2), Span(Kind.Italic, 1, 3)))
        assertStable(doc)
    }

    @Test
    fun adjacentDifferentStyles() {
        assertStable(RichText("ab", listOf(Span(Kind.Bold, 0, 1), Span(Kind.Italic, 1, 2))))
        assertStable(RichText("ab", listOf(Span(Kind.Italic, 0, 1), Span(Kind.Bold, 1, 2))))
        assertStable(RichText("abc", listOf(Span(Kind.Bold, 0, 3), Span(Kind.Italic, 0, 3))))
        assertStable(RichText("abc", listOf(Span(Kind.Bold, 0, 3), Span(Kind.Italic, 1, 2), Span(Kind.Strike, 0, 2))))
    }

    @Test
    fun bulletsAndQuotes() {
        val text = "shopping\nmilk\neggs\nwise words"
        val doc = RichText(text, listOf(Span(Kind.Bullet, 9, 13), Span(Kind.Bullet, 14, 18), Span(Kind.Quote, 19, 29)))
        assertEquals("shopping\n- milk\n- eggs\n> wise words", MarkdownCodec.serialize(doc))
        assertStable(doc)
    }

    @Test
    fun bulletTypedAtLineStartStillCountsForTheLine() {
        // The span no longer starts at the line start after typing before it.
        assertEquals("- xmilk", md("xmilk", Span(Kind.Bullet, 1, 5)))
    }

    @Test
    fun literalMarkupIsEscaped() {
        val text = "- not a list\n> not a quote\n2*3 and a~~b and [x](y) and <u>tag\\*"
        val doc = RichText(text)
        assertEquals(
            "\\- not a list\n\\> not a quote\n2\\*3 and a\\~\\~b and \\[x](y) and \\<u>tag\\\\\\*",
            MarkdownCodec.serialize(doc),
        )
        assertStable(doc)
    }

    @Test
    fun spacedAsteriskIsNotEscaped() {
        assertEquals("2 * 3", md("2 * 3"))
    }

    @Test
    fun links() {
        val doc = RichText("see site", listOf(Span(Kind.Link, 4, 8, "https://a.b/c d")))
        assertEquals("see [site](https://a.b/c%20d)", MarkdownCodec.serialize(doc))
        assertEquals(
            listOf(Span(Kind.Link, 4, 8, "https://a.b/c%20d")),
            MarkdownCodec.parse("see [site](https://a.b/c%20d)").spans,
        )
    }

    @Test
    fun obsidianMarkdownWithoutEditorFormatsRoundTrips() {
        assertRoundTrip("# Heading\n\nSome [[Wiki Link]] and #tag, `code` and __under__score_ok.\n1. first\n| a | b |\n- [ ] task")
        assertRoundTrip("a * b, 3 * 4")
        assertRoundTrip("**bold** *it* ~~s~~ <u>u</u> [l](http://x)")
        assertRoundTrip("- item\n> quote\n> - quoted item")
    }

    @Test
    fun unmatchedMarkersStayLiteral() {
        assertEquals("**open", MarkdownCodec.parse("**open").text)
        assertEquals("a ~~b", MarkdownCodec.parse("a ~~b").text)
        assertEquals("<u>x", MarkdownCodec.parse("<u>x").text)
        assertEquals(emptyList<Span>(), MarkdownCodec.parse("**open").spans)
    }

    @Test
    fun parsesStyles() {
        val doc = MarkdownCodec.parse("***both*** **b** *i*")
        assertEquals("both b i", doc.text)
        assertEquals(
            setOf(Span(Kind.Italic, 0, 4), Span(Kind.Bold, 0, 4), Span(Kind.Bold, 5, 6), Span(Kind.Italic, 7, 8)),
            doc.spans.toSet(),
        )
    }

    @Test
    fun imageEmbedsStayLiteral() {
        assertRoundTrip("before ![](Pictures/a%20b.png) and ![alt](x.jpg) after [link](https://x.y)")
        assertEquals("![](x.png)", MarkdownCodec.parse("![](x.png)").text)
        assertStable(RichText("say!", listOf(Span(Kind.Link, 3, 4, "https://x.y"))))
        assertEquals("say\\![!](https://x.y)", md("say!!", Span(Kind.Link, 4, 5, "https://x.y")))
    }

    @Test
    fun randomDocumentsSurviveSaveAndReload() {
        val random = kotlin.random.Random(42)
        val alphabet = "ab  *~[]()<>u/\\-+#_!\n"
        repeat(5000) {
            val text = String(CharArray(random.nextInt(0, 24)) { alphabet[random.nextInt(alphabet.length)] })
            val spans = mutableListOf<Span>()
            repeat(random.nextInt(0, 5)) {
                if (text.isEmpty()) return@repeat
                val a = random.nextInt(text.length)
                val b = random.nextInt(a, text.length) + 1
                val kind = listOf(Kind.Bold, Kind.Italic, Kind.Underline, Kind.Strike, Kind.Link, Kind.Color).random(random)
                val value = when (kind) {
                    Kind.Link -> "https://x.y/" + random.nextInt(2)
                    Kind.Color -> listOf("#ff1604", "#4caf50").random(random)
                    else -> null
                }
                spans += Span(kind, a, b, value)
            }
            // Block formats cover whole non-empty lines, as the editor applies them.
            var lineStart = 0
            for (line in text.split('\n')) {
                if (line.isNotEmpty() && random.nextInt(4) == 0) spans += Span(Kind.Bullet, lineStart, lineStart + line.length)
                if (line.isNotEmpty() && random.nextInt(4) == 0) spans += Span(Kind.Quote, lineStart, lineStart + line.length)
                lineStart += line.length + 1
            }
            val doc = RichText(text, spans)
            try {
                assertStable(withoutEdgeWhitespaceStyles(doc))
            } catch (e: AssertionError) {
                throw AssertionError("doc=$doc\nmarkdown=${MarkdownCodec.serialize(doc)}", e)
            }
        }
    }

    /**
     * Markdown can't style line breaks or the leading/trailing whitespace of a run;
     * the serializer drops those by design.
     */
    private fun withoutEdgeWhitespaceStyles(doc: RichText): RichText {
        val styles = charStyles(doc).map { it.toMutableSet() }
        doc.text.indices.filter { doc.text[it] == '\n' }.forEach { styles[it].clear() }
        // A character has one link and one color; the first one applied wins.
        for (set in styles) {
            for (kind in listOf(Kind.Link, Kind.Color)) set.filter { it.first == kind }.drop(1).forEach { set.remove(it) }
        }
        for (kind in listOf(Kind.Bold, Kind.Italic, Kind.Strike)) {
            var i = 0
            while (i < doc.text.length) {
                if ((kind to null) !in styles[i] || doc.text[i] == '\n') { i++; continue }
                var j = i
                while (j < doc.text.length && (kind to null) in styles[j] && doc.text[j] != '\n') j++
                var a = i
                while (a < j && doc.text[a].isWhitespace()) styles[a++].remove(kind to null)
                var b = j - 1
                while (b >= a && doc.text[b].isWhitespace()) styles[b--].remove(kind to null)
                i = j
            }
        }
        val spans = doc.text.indices.flatMap { i -> styles[i].map { (kind, url) -> Span(kind, i, i + 1, url) } }
        return RichText(doc.text, spans)
    }

    @Test
    fun textColor() {
        val doc = RichText("red and plain", listOf(Span(Kind.Color, 0, 3, "#ff1604")))
        assertEquals("<span style=\"color:#ff1604\">red</span> and plain", MarkdownCodec.serialize(doc))
        assertEquals(doc, MarkdownCodec.parse(MarkdownCodec.serialize(doc)))
        assertEquals(listOf(Span(Kind.Color, 0, 1, "#00ff00")), MarkdownCodec.parse("<font color=\"#00FF00\">g</font>").spans)
        assertRoundTrip("<span style=\"color:#4caf50\">**green**</span> text")
    }

    @Test
    fun literalColorTagIsEscaped() {
        assertStable(RichText("<span style=\"color:#ffffff\">x</span>"))
    }

    @Test
    fun crlfIsNormalized() {
        assertEquals("a\nb", MarkdownCodec.parse("a\r\nb").text)
    }
}
