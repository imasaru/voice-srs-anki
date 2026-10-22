package com.evan.voicesrs

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.HtmlCompat
/**
 * Binds a question card, the answer/rating pills, and an explanation's sections.
 * The rules live in [QuestionCard] and [SectionFormat].
 */
object CardFaceBinder {
    fun bindQuestion(
        label: TextView?,
        stem: TextView,
        options: LinearLayout?,
        source: String,
        number: Int,
        revealed: Boolean,
        answerPlain: String
    ): Boolean {
        val plain = BubbleModel.fullQuestion(source).ifBlank { AnkiText.stripHtml(source) }
        val answer = AnkiText.stripHtml(answerPlain).ifBlank { answerPlain.trim() }
        val parsed = QuestionCard.withCorrect(QuestionCard.parse(plain), answer, revealed)
        if (!parsed.mcq || options == null) {
            label?.visibility = View.GONE
            options?.visibility = View.GONE
            options?.removeAllViews()
            return false
        }
        if (label != null) {
            label.visibility = View.VISIBLE
            label.text = QuestionCard.label(number.coerceAtLeast(1))
        }
        setMarkup(stem, parsed.stem)
        options.visibility = View.VISIBLE
        options.removeAllViews()
        val density = options.resources.displayMetrics.density
        for (option in parsed.options) {
            val row = TextView(options.context)
            setMarkup(row, QuestionCard.optionRow(option))
            row.typeface = Typeface.MONOSPACE
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            row.setTextColor(
                options.context.getColor(
                    if (option.correct) R.color.option_correct_text else R.color.option_text
                )
            )
            row.setBackgroundResource(
                if (option.correct) R.drawable.bg_option_correct else R.drawable.bg_option
            )
            val padH = (8 * density).toInt()
            val padV = (6 * density).toInt()
            row.setPadding(padH, padV, padH, padV)
            if (option.correct) row.contentDescription = "Correct answer ${option.letter}"
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4 * density).toInt()
            row.layoutParams = lp
            options.addView(row)
        }
        return true
    }

    fun bindPills(spoken: TextView, rating: TextView, transcript: String, ease: Int?) {
        val (quote, answered) = QuestionCard.quotedAnswer(transcript)
        spoken.text = quote
        spoken.setTypeface(null, if (answered) Typeface.NORMAL else Typeface.ITALIC)
        spoken.setBackgroundResource(R.drawable.bg_spoken_pill)
        spoken.setTextColor(spoken.context.getColor(R.color.spoken_pill_text))
        val bubble = BubbleModel.userBubble(transcript, ease)
        val colors = RatingPill.of(bubble.ease)
        if (colors == null || bubble.rating.isBlank()) {
            rating.visibility = View.GONE
            spoken.contentDescription = "Your answer ${bubble.answer}"
            return
        }
        rating.visibility = View.VISIBLE
        rating.text = bubble.rating
        rating.setTextColor(colors.text)
        val bg = GradientDrawable()
        bg.cornerRadius = 12f * rating.resources.displayMetrics.density
        bg.setColor(colors.background)
        rating.background = bg
        spoken.contentDescription = "Your answer ${bubble.answer}. Rating ${bubble.rating}."
    }

    /** Section blocks for the explanation. The footer view is the grey note. */
    fun fillSections(host: LinearLayout, footerView: TextView, body: String, extraFooter: String) {
        host.removeAllViews()
        val doc = if (body.isBlank()) {
            SectionFormat.Doc(emptyList(), "", "")
        } else {
            SectionFormat.parse(body)
        }
        val density = host.resources.displayMetrics.density
        var shown = 0
        for (block in doc.blocks) {
            if (block.kind == SectionFormat.Kind.FOOTER) continue
            val view = TextView(host.context)
            when (block.kind) {
                SectionFormat.Kind.HEADING -> {
                    applyRuns(view, block.runs, heading = true)
                    view.setTextColor(host.context.getColor(R.color.section_heading))
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    view.setTypeface(null, Typeface.BOLD)
                }
                SectionFormat.Kind.CODE -> {
                    view.text = block.text
                    view.typeface = Typeface.MONOSPACE
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    view.setTextColor(host.context.getColor(R.color.code_block_text))
                    view.setBackgroundResource(R.drawable.bg_code_block)
                    val pad = (8 * density).toInt()
                    view.setPadding(pad, pad, pad, pad)
                }
                SectionFormat.Kind.BULLET -> {
                    applyRuns(view, listOf(SectionFormat.Run("• ", code = false)) + block.runs, heading = false)
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                }
                else -> {
                    applyRuns(view, block.runs, heading = false)
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = if (shown == 0) 0 else (6 * density).toInt()
            view.layoutParams = lp
            host.addView(view)
            shown++
        }
        host.visibility = if (shown == 0) View.GONE else View.VISIBLE
        val foot = listOf(doc.footer, extraFooter.trim()).filter { it.isNotBlank() }.joinToString("\n")
        if (foot.isBlank()) {
            footerView.visibility = View.GONE
            footerView.text = ""
        } else {
            footerView.visibility = View.VISIBLE
            footerView.text = foot
            footerView.setTextColor(host.context.getColor(R.color.note_footer))
            footerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }
    }

    /**
     * Card face text. Plain strings are set as-is. HTML keeps bold and breaks,
     * and `<code>` / `<pre>` become the chip and code-block styles.
     */
    fun setMarkup(view: TextView, markup: String) {
        if (markup.isEmpty()) {
            view.text = ""
            return
        }
        if (!markup.contains('<') && !markup.contains('&')) {
            view.text = markup
            return
        }
        view.text = HtmlCompat.fromHtml(
            CodeMarkup.prepareForHtml(markup),
            HtmlCompat.FROM_HTML_MODE_COMPACT,
            null,
            ChipTagHandler(view)
        )
    }

    private class ChipMark(val block: Boolean)

    private class ChipTagHandler(private val view: TextView) : Html.TagHandler {
        override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: org.xml.sax.XMLReader) {
            val block = when (tag.lowercase()) {
                "vsrspre" -> true
                "vsrscode" -> false
                else -> return
            }
            if (opening) {
                output.setSpan(ChipMark(block), output.length, output.length, Spanned.SPAN_MARK_MARK)
                return
            }
            val marks = output.getSpans(0, output.length, ChipMark::class.java)
            val mark = marks.lastOrNull { output.getSpanFlags(it) and Spanned.SPAN_MARK_MARK != 0 } ?: return
            val start = output.getSpanStart(mark)
            output.removeSpan(mark)
            val end = output.length
            if (start < 0 || start >= end) return
            val bg = view.context.getColor(if (mark.block) R.color.code_block else R.color.code_chip)
            val fg = view.context.getColor(if (mark.block) R.color.code_block_text else R.color.code_chip_text)
            output.setSpan(BackgroundColorSpan(bg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            output.setSpan(ForegroundColorSpan(fg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            output.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun applyRuns(view: TextView, runs: List<SectionFormat.Run>, heading: Boolean) {
        val sb = SpannableStringBuilder()
        appendRuns(sb, runs, view)
        if (heading && sb.isNotEmpty()) {
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(
                ForegroundColorSpan(view.context.getColor(R.color.section_heading)),
                0,
                sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        view.text = sb
    }

    /** One text view: headings, chips, a code block, and a grey footer. */
    fun applySections(view: TextView, body: String) {
        val doc = if (body.isBlank()) {
            SectionFormat.Doc(emptyList(), "", "")
        } else {
            SectionFormat.parse(body)
        }
        val sb = SpannableStringBuilder()
        for (block in doc.blocks) {
            if (block.kind == SectionFormat.Kind.FOOTER) continue
            if (sb.isNotEmpty()) sb.append('\n')
            val start = sb.length
            when (block.kind) {
                SectionFormat.Kind.BULLET -> {
                    sb.append("• ")
                    appendRuns(sb, block.runs, view)
                }
                SectionFormat.Kind.HEADING -> {
                    appendRuns(sb, block.runs, view)
                    if (sb.length > start) {
                        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(
                            ForegroundColorSpan(view.context.getColor(R.color.section_heading)),
                            start,
                            sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                SectionFormat.Kind.CODE -> {
                    sb.append(block.text)
                    if (sb.length > start) {
                        sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(
                            BackgroundColorSpan(view.context.getColor(R.color.code_block)),
                            start,
                            sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        sb.setSpan(
                            ForegroundColorSpan(view.context.getColor(R.color.code_block_text)),
                            start,
                            sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                else -> appendRuns(sb, block.runs, view)
            }
        }
        if (doc.footer.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append('\n')
            val start = sb.length
            sb.append(doc.footer)
            sb.setSpan(
                ForegroundColorSpan(view.context.getColor(R.color.note_footer)),
                start,
                sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(RelativeSizeSpan(0.85f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        view.text = sb
    }

    private fun appendRuns(sb: SpannableStringBuilder, runs: List<SectionFormat.Run>, view: TextView) {
        for (run in runs) {
            if (run.code) {
                paintRun(sb, run.text, CodeMarkup.Kind.CODE, view)
            } else if (CodeMarkup.hasCode(run.text)) {
                for (piece in CodeMarkup.runs(run.text)) paintRun(sb, piece.text, piece.kind, view)
            } else {
                sb.append(run.text)
            }
        }
    }

    private fun paintRun(sb: SpannableStringBuilder, text: String, kind: CodeMarkup.Kind, view: TextView) {
        val start = sb.length
        sb.append(text)
        if (kind == CodeMarkup.Kind.TEXT || sb.length <= start) return
        val block = kind == CodeMarkup.Kind.PRE
        val bg = view.context.getColor(if (block) R.color.code_block else R.color.code_chip)
        val fg = view.context.getColor(if (block) R.color.code_block_text else R.color.code_chip_text)
        sb.setSpan(BackgroundColorSpan(bg), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(fg), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
