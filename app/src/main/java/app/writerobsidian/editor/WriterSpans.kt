package app.writerobsidian.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan

/** A bullet drawn in the theme's accent color, like the original editor's KnifeBulletSpan. */
class WriterBulletSpan(private val color: Int, private val radius: Int, private val gap: Int) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean) = 2 * radius + gap

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
    ) {
        if (!first) return
        val style = p.style
        val oldColor = p.color
        p.color = color
        p.style = Paint.Style.FILL
        val centerY = (top + bottom) / 2f
        c.drawCircle(x + dir * radius.toFloat(), centerY, radius.toFloat(), p)
        p.color = oldColor
        p.style = style
    }
}

/** A quote stripe in the theme's accent color, like the original editor's KnifeQuoteSpan. */
class WriterQuoteSpan(private val color: Int, private val stripe: Int, private val gap: Int) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean) = stripe + gap

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
    ) {
        val style = p.style
        val oldColor = p.color
        p.color = color
        p.style = Paint.Style.FILL
        c.drawRect(x.toFloat(), top.toFloat(), (x + dir * stripe).toFloat(), bottom.toFloat(), p)
        p.color = oldColor
        p.style = style
    }
}
