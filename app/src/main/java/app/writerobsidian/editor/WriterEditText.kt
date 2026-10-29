package app.writerobsidian.editor

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import app.writerobsidian.data.RichText
import app.writerobsidian.data.RichText.Kind

/**
 * The note editor. It reproduces the original app's editor (mthli/Knife):
 * - bold/italic/underline/strikethrough toggle on the selected text; if the whole
 *   selection already has the style it is removed, otherwise applied;
 * - bullet/quote toggle on every line the selection touches; Enter does not continue them;
 * - "Clear" strips all formatting from the whole note;
 * - undo/redo over up to 99 snapshots, leaving the cursor at the end.
 *
 * Content is exchanged as [RichText] so Markdown conversion stays out of the view.
 */
class WriterEditText(context: Context) : EditText(context) {
    /** Called on any user change to text or formatting. */
    var onEdited: (() -> Unit)? = null
    var onSelectionChange: ((start: Int, end: Int) -> Unit)? = null
    var onHistoryChanged: (() -> Unit)? = null

    private var accent = 0
    private val undoStack = ArrayDeque<SpannableStringBuilder>()
    private val redoStack = ArrayDeque<SpannableStringBuilder>()
    private var beforeChange: SpannableStringBuilder? = null
    private var restoring = false

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        isSingleLine = false
        hint = "..."
        val dp = resources.displayMetrics.density
        setPadding((15 * dp).toInt(), (10 * dp).toInt(), (15 * dp).toInt(), (25 * dp).toInt())
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (!restoring) beforeChange = SpannableStringBuilder(s)
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable) {
                val before = beforeChange ?: return
                beforeChange = null
                if (restoring || before.toString() == s.toString()) return
                record(before)
                onEdited?.invoke()
            }
        })
    }

    fun applyLook(textColor: Int, accentColor: Int, typeface: Typeface, sizeSp: Int) {
        setTextColor(textColor)
        setHintTextColor(textColor and 0x00FFFFFF or 0x66000000)
        highlightColor = accentColor and 0x00FFFFFF or 0x44000000
        setTypeface(typeface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
        if (accent != accentColor) {
            accent = accentColor
            text?.let { recolorBlocks(it) }
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChange?.invoke(selStart, selEnd)
    }

    // ---- Content ----

    /** Loads a note; history starts empty and the cursor goes to the end, as in the original. */
    fun load(doc: RichText) {
        restoring = true
        setText(toSpannable(doc))
        restoring = false
        undoStack.clear()
        redoStack.clear()
        onHistoryChanged?.invoke()
        setSelection(length())
    }

    fun content(): RichText {
        val s = text ?: return RichText("")
        val spans = mutableListOf<RichText.Span>()
        fun add(kind: Kind, span: Any, url: String? = null) {
            val start = s.getSpanStart(span)
            val end = s.getSpanEnd(span)
            if (start < end) spans += RichText.Span(kind, start, end, url)
        }
        for (span in s.getSpans(0, s.length, Any::class.java)) {
            // The keyboard marks the word being typed with a composing underline; that isn't formatting.
            if (s.getSpanFlags(span) and Spanned.SPAN_COMPOSING != 0) continue
            when (span) {
                is StyleSpan -> {
                    if (span.style and Typeface.BOLD != 0) add(Kind.Bold, span)
                    if (span.style and Typeface.ITALIC != 0) add(Kind.Italic, span)
                }
                is URLSpan -> add(Kind.Link, span, span.url)
                is ForegroundColorSpan -> add(Kind.Color, span, colorHex(span.foregroundColor))
                is UnderlineSpan -> add(Kind.Underline, span)
                is StrikethroughSpan -> add(Kind.Strike, span)
                is WriterBulletSpan, is BulletSpan -> add(Kind.Bullet, span)
                is WriterQuoteSpan, is QuoteSpan -> add(Kind.Quote, span)
            }
        }
        return RichText(s.toString(), spans)
    }

    private fun toSpannable(doc: RichText): SpannableStringBuilder {
        val s = SpannableStringBuilder(doc.text)
        for (span in doc.spans) {
            val obj: Any = when (span.kind) {
                Kind.Bold -> StyleSpan(Typeface.BOLD)
                Kind.Italic -> StyleSpan(Typeface.ITALIC)
                Kind.Underline -> UnderlineSpan()
                Kind.Strike -> StrikethroughSpan()
                Kind.Link -> URLSpan(span.value.orEmpty())
                Kind.Color -> ForegroundColorSpan(parseColor(span.value))
                Kind.Bullet -> bulletSpan()
                Kind.Quote -> quoteSpan()
            }
            s.setSpan(obj, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    private fun bulletSpan(): WriterBulletSpan {
        val dp = resources.displayMetrics.density
        return WriterBulletSpan(accent, (3 * dp).toInt(), (5 * dp).toInt())
    }

    private fun quoteSpan(): WriterQuoteSpan {
        val dp = resources.displayMetrics.density
        return WriterQuoteSpan(accent, (2 * dp).toInt(), (10 * dp).toInt())
    }

    /** Block spans capture the accent color, so a theme change replaces them. */
    private fun recolorBlocks(s: Editable) {
        for (span in s.getSpans(0, s.length, WriterBulletSpan::class.java)) replaceSpan(s, span, bulletSpan())
        for (span in s.getSpans(0, s.length, WriterQuoteSpan::class.java)) replaceSpan(s, span, quoteSpan())
    }

    private fun replaceSpan(s: Editable, old: Any, new: Any) {
        val start = s.getSpanStart(old)
        val end = s.getSpanEnd(old)
        s.removeSpan(old)
        s.setSpan(new, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // ---- Formatting ----

    fun toggleBold() = toggleInline(Kind.Bold)
    fun toggleItalic() = toggleInline(Kind.Italic)
    fun toggleUnderline() = toggleInline(Kind.Underline)
    fun toggleStrikethrough() = toggleInline(Kind.Strike)
    fun toggleBullet() = toggleBlock(Kind.Bullet)
    fun toggleQuote() = toggleBlock(Kind.Quote)

    private fun toggleInline(kind: Kind) {
        val s = text ?: return
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || start >= end) return
        val snapshot = SpannableStringBuilder(s)
        if (coversRange(s, kind, start, end)) {
            removeInline(s, kind, start, end)
        } else {
            s.setSpan(newInline(kind), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        formatted(snapshot)
    }

    private fun newInline(kind: Kind): Any = when (kind) {
        Kind.Bold -> StyleSpan(Typeface.BOLD)
        Kind.Italic -> StyleSpan(Typeface.ITALIC)
        Kind.Underline -> UnderlineSpan()
        else -> StrikethroughSpan()
    }

    private fun inlineSpans(s: Spanned, kind: Kind, start: Int, end: Int): List<Any> =
        s.getSpans(start, end, Any::class.java).filter { span ->
            s.getSpanFlags(span) and Spanned.SPAN_COMPOSING == 0 && when (kind) {
                Kind.Bold -> span is StyleSpan && span.style and Typeface.BOLD != 0
                Kind.Italic -> span is StyleSpan && span.style and Typeface.ITALIC != 0
                Kind.Underline -> span is UnderlineSpan
                Kind.Strike -> span is StrikethroughSpan
                else -> false
            }
        }

    private fun coversRange(s: Spanned, kind: Kind, start: Int, end: Int): Boolean {
        val spans = inlineSpans(s, kind, start, end)
        return (start until end).all { i -> spans.any { s.getSpanStart(it) <= i && s.getSpanEnd(it) > i } }
    }

    /** Removes [kind] from [start, end), keeping it on the parts of each span outside the range. */
    private fun removeInline(s: Editable, kind: Kind, start: Int, end: Int) {
        for (span in inlineSpans(s, kind, start, end)) {
            val spanStart = s.getSpanStart(span)
            val spanEnd = s.getSpanEnd(span)
            s.removeSpan(span)
            // A bold-italic span loses only the requested half.
            val rest: Any? = if (span is StyleSpan && span.style == Typeface.BOLD_ITALIC) {
                StyleSpan(if (kind == Kind.Bold) Typeface.ITALIC else Typeface.BOLD)
            } else null
            if (rest != null) s.setSpan(rest, maxOf(spanStart, start), minOf(spanEnd, end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (spanStart < start) s.setSpan(copyOf(span), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (spanEnd > end) s.setSpan(copyOf(span), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun copyOf(span: Any): Any = when (span) {
        is StyleSpan -> StyleSpan(span.style)
        is UnderlineSpan -> UnderlineSpan()
        else -> StrikethroughSpan()
    }

    /** The [start, end) range of each line the selection touches, skipping empty lines. */
    private fun selectedLines(s: CharSequence): List<IntRange> {
        val selStart = selectionStart.coerceAtLeast(0)
        val selEnd = selectionEnd.coerceAtLeast(selStart)
        val lines = mutableListOf<IntRange>()
        var lineStart = 0
        while (lineStart <= s.length) {
            var lineEnd = lineStart
            while (lineEnd < s.length && s[lineEnd] != '\n') lineEnd++
            if (lineEnd > lineStart && lineStart <= selEnd && selStart <= lineEnd) lines += lineStart until lineEnd
            lineStart = lineEnd + 1
        }
        return lines
    }

    private fun blockSpans(s: Spanned, kind: Kind, line: IntRange): List<Any> =
        s.getSpans(line.first, line.last + 1, Any::class.java).filter {
            if (kind == Kind.Bullet) it is WriterBulletSpan || it is BulletSpan else it is WriterQuoteSpan || it is QuoteSpan
        }

    private fun toggleBlock(kind: Kind) {
        val s = text ?: return
        val lines = selectedLines(s)
        if (lines.isEmpty()) return
        val snapshot = SpannableStringBuilder(s)
        if (lines.all { blockSpans(s, kind, it).isNotEmpty() }) {
            lines.forEach { line -> blockSpans(s, kind, line).forEach(s::removeSpan) }
        } else {
            for (line in lines) {
                if (blockSpans(s, kind, line).isEmpty()) {
                    s.setSpan(if (kind == Kind.Bullet) bulletSpan() else quoteSpan(), line.first, line.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        formatted(snapshot)
    }

    /**
     * Colors the selected text, like Writer 4.7.0's text-color strip. Choosing the color the
     * whole selection already has removes it.
     */
    fun applyColor(color: Int) {
        val s = text ?: return
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || start >= end) return
        val snapshot = SpannableStringBuilder(s)
        val existing = s.getSpans(start, end, ForegroundColorSpan::class.java).filter { s.getSpanFlags(it) and Spanned.SPAN_COMPOSING == 0 }
        val sameColor = (start until end).all { i ->
            existing.any { s.getSpanStart(it) <= i && s.getSpanEnd(it) > i && it.foregroundColor == color }
        }
        for (span in existing) {
            val spanStart = s.getSpanStart(span)
            val spanEnd = s.getSpanEnd(span)
            s.removeSpan(span)
            if (spanStart < start) s.setSpan(ForegroundColorSpan(span.foregroundColor), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (spanEnd > end) s.setSpan(ForegroundColorSpan(span.foregroundColor), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (!sameColor) s.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        formatted(snapshot)
    }

    /** Removes all formatting from the whole note, as the original's "Clear" did. */
    fun clearFormats() {
        val s = text ?: return
        val snapshot = SpannableStringBuilder(s)
        restoring = true
        setText(s.toString())
        restoring = false
        setSelection(length())
        formatted(snapshot)
    }

    private fun formatted(snapshot: SpannableStringBuilder) {
        record(snapshot)
        onEdited?.invoke()
    }

    // ---- History ----

    private fun record(snapshot: SpannableStringBuilder) {
        undoStack.addLast(snapshot)
        while (undoStack.size > HISTORY_SIZE) undoStack.removeFirst()
        redoStack.clear()
        onHistoryChanged?.invoke()
    }

    fun undo() = step(undoStack, redoStack)

    fun redo() = step(redoStack, undoStack)

    private fun step(from: ArrayDeque<SpannableStringBuilder>, to: ArrayDeque<SpannableStringBuilder>) {
        val target = from.removeLastOrNull() ?: return
        text?.let { to.addLast(SpannableStringBuilder(it)) }
        restoring = true
        setText(SpannableStringBuilder(target))
        restoring = false
        setSelection(length())
        onHistoryChanged?.invoke()
        onEdited?.invoke()
    }

    private companion object {
        const val HISTORY_SIZE = 99

        fun colorHex(color: Int) = "#%06x".format(color and 0xFFFFFF)

        fun parseColor(hex: String?): Int =
            hex?.removePrefix("#")?.toLongOrNull(16)?.let { (it or 0xFF000000) .toInt() } ?: 0xFF000000.toInt()
    }
}
