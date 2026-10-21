package com.evan.voicesrs

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.widget.TextView

/** Applies [MarkdownRender] to a TextView. Raw `**` and backticks are not shown. */
object MarkdownSpans {
    fun apply(view: TextView, markdown: String) {
        val doc = MarkdownRender.render(markdown)
        val styled = SpannableString(doc.text)
        for (span in doc.spans) {
            val start = span.start.coerceAtLeast(0)
            val end = span.end.coerceAtMost(styled.length)
            if (start >= end) continue
            when (span.kind) {
                MdSpan.Kind.BOLD -> styled.setSpan(
                    StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                MdSpan.Kind.HEADING -> {
                    styled.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    styled.setSpan(RelativeSizeSpan(1.15f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                MdSpan.Kind.CODE -> styled.setSpan(
                    TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        view.text = styled
    }
}
