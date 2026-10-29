package app.writerobsidian.data

/**
 * Formatted text as the editor sees it, independent of Android spans so the
 * Markdown conversion can be unit-tested on the JVM.
 *
 * The formats are exactly the original Writer editor's (mthli/Knife): bold, italic,
 * underline, strikethrough, text color, bullet lines and quote lines, plus links that
 * imported notes may carry.
 */
data class RichText(val text: String, val spans: List<Span> = emptyList()) {
    enum class Kind { Bold, Italic, Underline, Strike, Color, Link, Bullet, Quote }

    /**
     * [start, end) in [text]. Bullet and Quote cover whole lines. [value] is the URL of a
     * link or the `#rrggbb` of a color.
     */
    data class Span(val kind: Kind, val start: Int, val end: Int, val value: String? = null)
}

/**
 * Converts [RichText] to and from Markdown that Obsidian renders the same way:
 * `**bold**`, `*italic*`, `<u>underline</u>`, `~~strike~~`, `[text](url)`,
 * `<span style="color:#rrggbb">colored</span>`,
 * `- ` bullets and `> ` quotes. One text line is one Markdown line.
 *
 * Markdown the editor has no format for (headings, wikilinks, tags, tables, …) is kept
 * as literal text, so notes edited in Obsidian round-trip unchanged; `![alt](src)` image
 * embeds are literal text too, never read as a link. Literal characters
 * that would otherwise be read back as formatting are backslash-escaped.
 */
object MarkdownCodec {
    private val INLINE = listOf(RichText.Kind.Link, RichText.Kind.Color, RichText.Kind.Underline, RichText.Kind.Bold, RichText.Kind.Italic, RichText.Kind.Strike)
    private const val ESCAPABLE = "\\*~[]<>-+!"
    private val LINE_MARKER = Regex("""^(>|[-+*](\s|$))""")
    private val TAG = Regex("""</?([bisu])>""", RegexOption.IGNORE_CASE)
    private val TAG_KINDS = mapOf(
        'b' to RichText.Kind.Bold, 'i' to RichText.Kind.Italic, 's' to RichText.Kind.Strike, 'u' to RichText.Kind.Underline,
    )
    private val COLOR_OPEN = Regex(
        """<(?:span\s+style\s*=\s*["']\s*color\s*:\s*(#[0-9a-fA-F]{6})\s*;?\s*["']|font\s+color\s*=\s*["']?(#[0-9a-fA-F]{6})["']?)\s*>""",
        RegexOption.IGNORE_CASE,
    )
    private val COLOR_CLOSE = Regex("""</(?:span|font)>""", RegexOption.IGNORE_CASE)
    private val LINK = Regex("""\[((?:\\.|[^\[\]\\])*)]\(([^()\s]*)\)""")

    // ---- RichText -> Markdown ----

    fun serialize(doc: RichText): String {
        val out = StringBuilder()
        var lineStart = 0
        for ((index, line) in doc.text.split('\n').withIndex()) {
            if (index > 0) out.append('\n')
            val lineEnd = lineStart + line.length
            serializeLine(doc, lineStart, lineEnd, out)
            lineStart = lineEnd + 1
        }
        return out.toString()
    }

    private fun serializeLine(doc: RichText, start: Int, end: Int, out: StringBuilder) {
        fun hasBlock(kind: RichText.Kind) = doc.spans.any {
            it.kind == kind && it.start < maxOf(end, start + 1) && it.end > start
        }
        if (hasBlock(RichText.Kind.Quote)) out.append("> ")
        if (hasBlock(RichText.Kind.Bullet)) out.append("- ")
        if (start == end) return
        val text = doc.text.substring(start, end)

        // Per-character style sets. Styles compare by kind (and URL), so formatting
        // applied in adjacent pieces serializes as one run.
        val styles = Array(text.length) { mutableListOf<Style>() }
        for (span in doc.spans) {
            if (span.kind !in INLINE) continue
            val style = Style(span.kind, styleValue(span.kind, span.value))
            for (i in maxOf(span.start, start) until minOf(span.end, end)) {
                val list = styles[i - start]
                if (list.none { it.kind == span.kind }) list += style
            }
        }

        // Markdown can't open `**`/`*`/`~~` before whitespace or close after it, so
        // whitespace at the edges of those runs is left unstyled (it looks the same).
        val trimmed = styles.map { it.toMutableList() }.toTypedArray()
        for (kind in listOf(RichText.Kind.Bold, RichText.Kind.Italic, RichText.Kind.Strike)) {
            var i = 0
            while (i < text.length) {
                if (trimmed[i].none { it.kind == kind }) { i++; continue }
                var j = i
                while (j < text.length && trimmed[j].any { it.kind == kind }) j++
                var a = i
                while (a < j && text[a].isWhitespace()) trimmed[a++].removeAll { it.kind == kind }
                var b = j - 1
                while (b >= a && text[b].isWhitespace()) trimmed[b--].removeAll { it.kind == kind }
                i = j
            }
        }
        // Some overlapping bold/italic combinations produce asterisk runs that read back
        // differently; such a line is written with HTML tags, which Obsidian renders the same.
        val markdown = inlineMarkup(text, trimmed, html = false)
        val inline = if (readsBackAs(markdown, text, trimmed)) markdown else inlineMarkup(text, styles, html = true)
        // Literal text that starts like a list item or quote must not become one.
        if (LINE_MARKER.containsMatchIn(inline)) out.append('\\')
        out.append(inline)
    }

    private fun inlineMarkup(text: String, styles: Array<out List<Style>>, html: Boolean): String {
        val out = StringBuilder()
        val open = ArrayList<Style>()
        for (i in 0..text.length) {
            val want = if (i < text.length) styles[i] else emptyList()
            // Close back to the longest prefix of the stack that is still wanted.
            var keep = 0
            while (keep < open.size && open[keep] in want) keep++
            while (open.size > keep) out.append(closer(open.removeAt(open.lastIndex), html))
            for (style in want.sortedBy { INLINE.indexOf(it.kind) }) {
                if (style !in open) {
                    out.append(opener(style, html))
                    open += style
                }
            }
            if (i < text.length) {
                // A marker right after a backslash would read as an escape.
                val markerFollows = styles[i] != (styles.getOrNull(i + 1) ?: emptyList<Style>())
                val markerPrecedes = styles[i] != (styles.getOrNull(i - 1) ?: emptyList<Style>())
                val inLink = styles[i].any { it.kind == RichText.Kind.Link }
                out.append(
                    when {
                        text[i] == '\\' && markerFollows -> "\\\\"
                        // `!` right before a link would turn it into an image embed.
                        text[i] == '!' && opensLink(styles, i + 1) -> "\\!"
                        // `*` or `~` touching a marker would merge into its delimiter run.
                        text[i] in "*~" && (markerFollows || markerPrecedes) -> "\\" + text[i]
                        else -> escape(text, i, inLink)
                    }
                )
            }
        }
        return out.toString()
    }

    private fun opensLink(styles: Array<out List<Style>>, i: Int): Boolean {
        val link = styles.getOrNull(i)?.firstOrNull { it.kind == RichText.Kind.Link } ?: return false
        return link !in styles[i - 1]
    }

    private fun readsBackAs(markup: String, text: String, styles: Array<out List<Style>>): Boolean {
        val parsedText = StringBuilder()
        val parsedSpans = mutableListOf<RichText.Span>()
        parseInline(markup, parsedText, parsedSpans)
        if (parsedText.toString() != text) return false
        return text.indices.all { i ->
            val got = parsedSpans.filter { i >= it.start && i < it.end }
                .map { Style(it.kind, styleValue(it.kind, it.value)) }
                .toSet()
            got == styles[i].toSet()
        }
    }

    /** An inline format as the serializer tracks it; [value] is set for links and colors. */
    private data class Style(val kind: RichText.Kind, val value: String?)

    private fun styleValue(kind: RichText.Kind, value: String?) = when (kind) {
        RichText.Kind.Link -> value.orEmpty()
        RichText.Kind.Color -> value.orEmpty().lowercase()
        else -> null
    }

    private fun opener(style: Style, html: Boolean) = when (style.kind) {
        RichText.Kind.Bold -> if (html) "<b>" else "**"
        RichText.Kind.Italic -> if (html) "<i>" else "*"
        RichText.Kind.Strike -> if (html) "<s>" else "~~"
        RichText.Kind.Underline -> "<u>"
        RichText.Kind.Color -> "<span style=\"color:${style.value}\">"
        RichText.Kind.Link -> "["
        else -> ""
    }

    private fun closer(style: Style, html: Boolean) = when (style.kind) {
        RichText.Kind.Color -> "</span>"
        RichText.Kind.Link -> "](" + style.value.orEmpty().replace(" ", "%20").replace("(", "%28").replace(")", "%29") + ")"
        RichText.Kind.Bold, RichText.Kind.Italic, RichText.Kind.Strike -> if (html) opener(style, true).replace("<", "</") else opener(style, false)
        else -> opener(style, html).replace("<", "</")
    }

    /** Escapes [text][i] only where the parser would otherwise read it as markup. */
    private fun escape(text: String, i: Int, inLink: Boolean): String {
        val c = text[i]
        val prev = text.getOrNull(i - 1)
        val next = text.getOrNull(i + 1)
        val escape = when (c) {
            '\\' -> next != null && next in ESCAPABLE
            '*' -> !(isSpace(prev) && isSpace(next))
            '~' -> prev == '~' || next == '~'
            '[' -> inLink || (prev != '!' && text.indexOf("](", i) >= 0)
            ']' -> inLink
            '<' -> TAG.matchesAt(text, i) || COLOR_OPEN.matchesAt(text, i) || COLOR_CLOSE.matchesAt(text, i)
            else -> false
        }
        return if (escape) "\\$c" else c.toString()
    }

    private fun isSpace(c: Char?) = c == null || c.isWhitespace()

    // ---- Markdown -> RichText ----

    fun parse(markdown: String): RichText {
        val text = StringBuilder()
        val spans = mutableListOf<RichText.Span>()
        for ((index, rawLine) in markdown.replace("\r\n", "\n").split('\n').withIndex()) {
            if (index > 0) text.append('\n')
            var line = rawLine
            var quote = false
            var bullet = false
            if (line.startsWith(">")) {
                quote = true
                line = line.removePrefix(">").removePrefix(" ")
            }
            if (line.length >= 2 && line[0] in "-+*" && line[1] == ' ') {
                bullet = true
                line = line.substring(2)
            } else if (line.length == 1 && line[0] in "-+*") {
                bullet = true
                line = ""
            }
            val lineStart = text.length
            parseInline(line, text, spans)
            if (text.length > lineStart) {
                if (quote) spans += RichText.Span(RichText.Kind.Quote, lineStart, text.length)
                if (bullet) spans += RichText.Span(RichText.Kind.Bullet, lineStart, text.length)
            }
        }
        return RichText(text.toString(), spans)
    }

    /** An opening marker waiting for its closer; unmatched ones turn back into literal text. */
    private class Opener(val kind: RichText.Kind, val literal: String, val value: String? = null) {
        var start = -1
        var matched = false
    }

    private sealed interface Piece {
        data class Literal(val text: String) : Piece
        data class Open(val opener: Opener) : Piece
        data class Close(val opener: Opener) : Piece
    }

    private fun parseInline(line: String, out: StringBuilder, spans: MutableList<RichText.Span>) {
        val pieces = mutableListOf<Piece>()
        val stack = ArrayList<Opener>()
        val linkCloses = HashMap<Int, Pair<Int, Opener>>() // index of "](" -> (end index, opener)

        fun top() = stack.lastOrNull()
        fun close(opener: Opener) {
            opener.matched = true
            stack.remove(opener)
            pieces += Piece.Close(opener)
        }
        fun open(opener: Opener) {
            stack += opener
            pieces += Piece.Open(opener)
        }

        var i = 0
        var escapedAt = -1
        while (i < line.length) {
            val c = line[i]
            val linkClose = linkCloses.remove(i)
            if (linkClose != null) {
                val (endIndex, opener) = linkClose
                // Anything still open inside the link text can no longer match.
                while (top() != null && top() !== opener) stack.removeAt(stack.lastIndex)
                close(opener)
                i = endIndex
                continue
            }

            when {
                c == '\\' && i + 1 < line.length && line[i + 1] in ESCAPABLE -> {
                    pieces += Piece.Literal(line[i + 1].toString())
                    escapedAt = i + 1
                    i += 2
                }
                c == '<' && COLOR_OPEN.matchesAt(line, i) -> {
                    val m = COLOR_OPEN.matchAt(line, i)!!
                    val hex = m.groupValues[1].ifEmpty { m.groupValues[2] }.lowercase()
                    open(Opener(RichText.Kind.Color, m.value, value = hex))
                    i += m.value.length
                }
                c == '<' && COLOR_CLOSE.matchesAt(line, i) -> {
                    val m = COLOR_CLOSE.matchAt(line, i)!!
                    if (top()?.kind == RichText.Kind.Color) close(top()!!) else pieces += Piece.Literal(m.value)
                    i += m.value.length
                }
                c == '<' && TAG.matchesAt(line, i) -> {
                    val m = TAG.matchAt(line, i)!!
                    val kind = TAG_KINDS.getValue(m.groupValues[1].lowercase()[0])
                    if (line[i + 1] != '/') {
                        open(Opener(kind, m.value))
                    } else if (top()?.kind == kind) {
                        close(top()!!)
                    } else {
                        pieces += Piece.Literal(m.value)
                    }
                    i += m.value.length
                }
                // `![alt](src)` is an image embed, kept as literal text.
                c == '[' && (i == 0 || line[i - 1] != '!' || escapedAt == i - 1) && LINK.matchesAt(line, i) -> {
                    val m = LINK.matchAt(line, i)!!
                    val opener = Opener(RichText.Kind.Link, "[", value = m.groupValues[2])
                    val closeAt = i + 1 + m.groupValues[1].length
                    linkCloses[closeAt] = (m.range.last + 1) to opener
                    open(opener)
                    i++
                }
                c == '*' || (c == '~' && line.getOrNull(i + 1) == '~' && line.getOrNull(i + 2) != '~' && line.getOrNull(i - 1) != '~') -> {
                    var n = 1
                    while (i + n < line.length && line[i + n] == c) n++
                    val canOpen = i + n < line.length && !line[i + n].isWhitespace()
                    val canClose = i > 0 && !line[i - 1].isWhitespace()
                    if (c == '~') {
                        when {
                            canClose && top()?.kind == RichText.Kind.Strike -> close(top()!!)
                            canOpen -> open(Opener(RichText.Kind.Strike, "~~"))
                            else -> pieces += Piece.Literal("~~")
                        }
                    } else {
                        var remaining = n
                        if (canClose) {
                            while (remaining > 0) {
                                val t = top() ?: break
                                val need = when (t.kind) {
                                    RichText.Kind.Italic -> 1
                                    RichText.Kind.Bold -> 2
                                    else -> break
                                }
                                if (need > remaining) break
                                close(t)
                                remaining -= need
                            }
                        }
                        if (remaining > 0 && canOpen) {
                            if (remaining > 3) pieces += Piece.Literal("*".repeat(remaining - 3)).also { remaining = 3 }
                            if (remaining >= 2) open(Opener(RichText.Kind.Bold, "**"))
                            if (remaining % 2 == 1) open(Opener(RichText.Kind.Italic, "*"))
                        } else if (remaining > 0) {
                            pieces += Piece.Literal("*".repeat(remaining))
                        }
                    }
                    i += n
                }
                else -> {
                    pieces += Piece.Literal(c.toString())
                    i++
                }
            }
        }

        for (piece in pieces) {
            when (piece) {
                is Piece.Literal -> out.append(piece.text)
                is Piece.Open -> if (piece.opener.matched) piece.opener.start = out.length else out.append(piece.opener.literal)
                is Piece.Close -> {
                    val o = piece.opener
                    if (out.length > o.start) spans += RichText.Span(o.kind, o.start, out.length, o.value)
                }
            }
        }
    }

    private fun Regex.matchesAt(input: CharSequence, index: Int) = matchAt(input, index) != null
}
