package app.writerobsidian.data

/** Renders a note as standalone HTML for sharing as .html or .doc (Word opens HTML .doc files). */
object HtmlExport {
    fun render(title: String, doc: RichText): String = page(title, body(doc), dark = false)

    /**
     * Several notes for printing, laid out like the original's print page: each note's name
     * as a heading, a rule, then its text, one note per page. [dark] prints light on black.
     */
    fun renderForPrint(notes: List<Pair<String, RichText>>, dark: Boolean): String =
        page(
            "Writer",
            notes.joinToString("") { (title, doc) ->
                "<section><h2>${escape(title)}</h2><hr>${body(doc)}</section>"
            },
            dark,
        )

    private fun page(title: String, body: String, dark: Boolean): String {
        val colors = if (dark) "background:#000;color:#f1f1f1" else "background:#fff;color:#000"
        return """<!DOCTYPE html><html><head><meta charset="utf-8"><title>${escape(title)}</title>""" +
            "<style>body{font-family:serif;font-size:19px;$colors} p{margin:0} h2{margin:0 0 8px}" +
            " section{page-break-after:always} section:last-child{page-break-after:auto}" +
            " blockquote{margin:0;padding-left:10px;border-left:2px solid #888}</style>" +
            "</head><body>$body</body></html>"
    }

    private fun body(doc: RichText): String {
        val body = StringBuilder()
        var lineStart = 0
        var inList = false
        for (line in doc.text.split('\n')) {
            val lineEnd = lineStart + line.length
            fun has(kind: RichText.Kind) = doc.spans.any { it.kind == kind && it.start < maxOf(lineEnd, lineStart + 1) && it.end > lineStart }
            val bullet = has(RichText.Kind.Bullet)
            if (bullet != inList) {
                body.append(if (bullet) "<ul>" else "</ul>")
                inList = bullet
            }
            val inline = inline(doc, lineStart, lineEnd)
            body.append(
                when {
                    bullet && has(RichText.Kind.Quote) -> "<li><blockquote>$inline</blockquote></li>"
                    bullet -> "<li>$inline</li>"
                    has(RichText.Kind.Quote) -> "<blockquote>$inline</blockquote>"
                    line.isEmpty() -> "<p>&nbsp;</p>"
                    else -> "<p>$inline</p>"
                }
            )
            lineStart = lineEnd + 1
        }
        if (inList) body.append("</ul>")
        return body.toString()
    }

    private fun inline(doc: RichText, start: Int, end: Int): String {
        val out = StringBuilder()
        val tags = listOf(
            RichText.Kind.Link to "a", RichText.Kind.Color to "span", RichText.Kind.Underline to "u", RichText.Kind.Bold to "b",
            RichText.Kind.Italic to "i", RichText.Kind.Strike to "s",
        )
        val open = ArrayList<RichText.Span>()
        for (i in start..end) {
            val want = if (i < end) doc.spans.filter { s -> tags.any { it.first == s.kind } && s.start <= i && s.end > i } else emptyList()
            var keep = 0
            while (keep < open.size && open[keep] in want) keep++
            while (open.size > keep) out.append("</").append(tagOf(open.removeAt(open.lastIndex).kind, tags)).append('>')
            for (span in want.sortedBy { s -> tags.indexOfFirst { it.first == s.kind } }) {
                if (span in open) continue
                val tag = tagOf(span.kind, tags)
                out.append(
                    when (tag) {
                        "a" -> "<a href=\"${escape(span.value.orEmpty())}\">"
                        "span" -> "<span style=\"color:${escape(span.value.orEmpty())}\">"
                        else -> "<$tag>"
                    }
                )
                open += span
            }
            if (i < end) out.append(escape(doc.text[i].toString()))
        }
        return out.toString()
    }

    private fun tagOf(kind: RichText.Kind, tags: List<Pair<RichText.Kind, String>>) = tags.first { it.first == kind }.second

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
