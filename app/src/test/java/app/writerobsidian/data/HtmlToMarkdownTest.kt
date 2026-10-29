package app.writerobsidian.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlToMarkdownTest {
    private fun md(html: String) = HtmlToMarkdown.convert(html)

    @Test
    fun plainTextIsUnchanged() {
        val text = "Line one\n\n  indented line two * not markup"
        assertEquals(text, md(text))
    }

    @Test
    fun inlineStyles() {
        assertEquals(
            "**bold** *italic* <u>under</u> ~~gone~~ ~~old~~",
            md("<b>bold</b> <i>italic</i> <u>under</u> <del>gone</del> <strike>old</strike>"),
        )
    }

    @Test
    fun breaksAndNonBreakingSpaces() {
        assertEquals("first\nsecond   spaced\n\nthird", md("first<br>second&nbsp;&nbsp; spaced<br><br>third"))
    }

    @Test
    fun rawNewlinesAreHtmlWhitespace() {
        assertEquals("one two\nthree", md("one\n  two<br>three"))
    }

    @Test
    fun pastedCarriageReturnsAreDropped() {
        assertEquals("“said.”\n\nNext", md("&#8220;said.&#8221; <br>&#13;<br>Next"))
    }

    @Test
    fun spacesInsideEmphasisMoveOutside() {
        assertEquals("he said *\"Why?\"* and **left**", md("he said<i> \"Why?\" </i>and<b> left</b>"))
    }

    @Test
    fun literalMarkupCharactersAreEscaped() {
        assertEquals("2\\*3, \\[a](b), \\<u>x", md("2*3, [a](b), &lt;u&gt;x<br>"))
        assertEquals("\\- not a bullet", md("- not a bullet<br>"))
    }

    @Test
    fun bulletsAndQuotes() {
        assertEquals(
            "intro\n- apples\n- pears\n> quoted\n> again\nafter",
            md("intro<ul><li>apples</li><li>pears</li></ul><blockquote>quoted<br>again</blockquote>after"),
        )
    }

    @Test
    fun links() {
        assertEquals(
            "see [the site](https://example.com/a%20b?x=1&y=2) now",
            md("""see <a href="https://example.com/a b?x=1&amp;y=2">the site</a> now"""),
        )
    }

    @Test
    fun anchorWithoutHrefKeepsText() {
        assertEquals("plain", md("<a name=\"x\">plain</a>"))
    }

    @Test
    fun entities() {
        assertEquals("a < b & c > d \"q\" é 😀", md("a &lt; b &amp; c &gt; d &quot;q&quot; &#233; &#x1F600;"))
    }

    @Test
    fun unknownEntityIsKept() {
        assertEquals("x &bogus; y", md("x &bogus; <b>y</b>").replace("**", ""))
    }

    @Test
    fun writer47ColorsAndImages() {
        assertEquals(
            "<span style=\"color:#ff1604\">red</span> text ![](/storage/WriterApp/Pictures/a%20b.jpg)",
            md("<font color ='#FF1604'>red</font> text <img width=\"100%\" src=\"/storage/WriterApp/Pictures/a b.jpg\">"),
        )
    }
}
