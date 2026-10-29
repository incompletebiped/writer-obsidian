package app.writerobsidian.data

/**
 * Converts the HTML that the original app's rich-text editor (mthli/Knife) stored
 * into Obsidian-flavoured Markdown. Knife emits a small tag set: b, i, u, del/strike,
 * a, br, ul/li and blockquote, with runs of spaces as `&nbsp;`; Writer 4.7.0 adds
 * `<font color>` and `<img>`.
 *
 * The HTML is read into [RichText] and written by [MarkdownCodec], so imported notes
 * get exactly the Markdown the editor would save. Images, which the editor has no
 * format for, are kept as literal `![](src)` embeds.
 */
object HtmlToMarkdown {
    private val tag = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>""")
    private val entity = Regex("""&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);""")
    private val href = Regex("""href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""[ \t\r\n]+""")
    private val fontColor = Regex("""color\s*=\s*["']?#([0-9a-fA-F]{6})""", RegexOption.IGNORE_CASE)
    private val imgSrc = Regex("""src\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

    fun looksLikeHtml(text: String): Boolean = tag.containsMatchIn(text) || entity.containsMatchIn(text)

    fun convert(html: String): String = MarkdownCodec.serialize(read(html))

    /** Text without any markup is taken as plain text. */
    fun read(html: String): RichText {
        if (!looksLikeHtml(html)) return RichText(html.replace("\r\n", "\n"))
        val out = Builder()
        var pos = 0
        for (m in tag.findAll(html)) {
            out.text(decode(whitespace.replace(html.substring(pos, m.range.first), " ")))
            pos = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            val inline = when (name) {
                "b", "strong" -> RichText.Kind.Bold
                "i", "em" -> RichText.Kind.Italic
                "u" -> RichText.Kind.Underline
                "del", "strike", "s" -> RichText.Kind.Strike
                else -> null
            }
            when {
                inline != null -> if (closing) out.close(name) else out.open(name, inline)
                // Writer 4.7.0 stores text color as <font color='#rrggbb'>.
                name == "font" -> if (closing) {
                    out.close(name)
                } else {
                    val hex = fontColor.find(m.groupValues[3])?.groupValues?.get(1)?.lowercase()
                    out.open(name, if (hex != null) RichText.Kind.Color else null, hex?.let { "#$it" })
                }
                name == "a" -> if (closing) {
                    out.close(name)
                } else {
                    val url = attribute(href, m.groupValues[3])
                    out.open(name, if (url != null) RichText.Kind.Link else null, url)
                }
                name == "img" -> if (!closing) {
                    attribute(imgSrc, m.groupValues[3])?.let { out.content("![](" + it.replace(" ", "%20") + ")") }
                }
                name == "br" -> out.newline()
                name == "p" || name == "div" -> if (closing) out.newline()
                name == "ul" || name == "ol" -> out.endLine()
                name == "li" -> if (closing) out.endLine() else out.bullet()
                name == "blockquote" -> {
                    out.endLine()
                    out.quoteDepth = (out.quoteDepth + if (closing) -1 else 1).coerceAtLeast(0)
                }
            }
        }
        out.text(decode(whitespace.replace(html.substring(pos), " ")))
        return out.finish()
    }

    private fun attribute(pattern: Regex, attributes: String): String? =
        pattern.find(attributes)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.let(::decode)

    private class Builder {
        private val text = StringBuilder()
        private val spans = mutableListOf<RichText.Span>()
        /** Open tags, innermost last; [kind] is null for tags that format nothing. */
        private class Open(val tag: String, val kind: RichText.Kind?, val value: String?, val start: Int)
        private val open = ArrayList<Open>()
        private var lineStart = 0
        private var bullet = false
        var quoteDepth = 0

        private val lineEmpty get() = text.length == lineStart

        fun text(s: String) {
            // Collapsed HTML whitespace at the start of a line is not content.
            content(if (lineEmpty) s.trimStart(' ') else s)
        }

        fun content(s: String) {
            text.append(s)
        }

        fun open(tag: String, kind: RichText.Kind?, value: String? = null) {
            open += Open(tag, kind, value, text.length)
        }

        /** Closes the innermost open [tag]; a stray closing tag is ignored. */
        fun close(tag: String) {
            val index = open.indexOfLast { it.tag == tag }
            if (index < 0) return
            val o = open.removeAt(index)
            val start = minOf(o.start, text.length)
            if (o.kind != null && text.length > start) spans += RichText.Span(o.kind, start, text.length, o.value)
        }

        fun newline() {
            endBlock()
            text.append('\n')
            lineStart = text.length
        }

        /** Like [newline], but only if the current line has content. */
        fun endLine() {
            if (!lineEmpty) newline()
        }

        fun bullet() {
            endLine()
            bullet = true
        }

        fun finish(): RichText {
            endBlock()
            while (open.isNotEmpty()) close(open.last().tag)
            while (text.endsWith('\n')) text.setLength(text.length - 1)
            val end = text.length
            val kept = spans.mapNotNull { s -> s.copy(end = minOf(s.end, end)).takeIf { it.end > it.start } }
            return RichText(text.toString(), kept)
        }

        /** Ends the current line: trims trailing spaces and records its bullet/quote. */
        private fun endBlock() {
            while (text.length > lineStart && text.last() == ' ') text.setLength(text.length - 1)
            if (!lineEmpty) {
                if (quoteDepth > 0) spans += RichText.Span(RichText.Kind.Quote, lineStart, text.length)
                if (bullet) spans += RichText.Span(RichText.Kind.Bullet, lineStart, text.length)
            }
            bullet = false
        }
    }

    private fun decode(s: String): String = entity.replace(s) { m ->
        val name = m.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") ->
                name.drop(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
            else -> NAMED[name]
        } ?: m.value
    }

    /**
     * Text pasted into the original kept Windows line endings, stored as `&#13;<br>`.
     * The editor drew the carriage return as nothing, so it is dropped.
     */
    private fun codePoint(cp: Int): String? = when {
        cp == 13 -> ""
        Character.isValidCodePoint(cp) -> String(Character.toChars(cp))
        else -> null
    }

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "hellip" to "…", "mdash" to "—", "ndash" to "–",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
    )
}
