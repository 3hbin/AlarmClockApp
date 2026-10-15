package com.example.alarmclock

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Định dạng tin nhắn AI: in đậm, nghiêng, công thức toán đơn giản và nguyên tố hóa học.
 */
object ChatMarkdown {

    private val SUPER = charArrayOf('⁰','¹','²','³','⁴','⁵','⁶','⁷','⁸','⁹','⁺','⁻')
    private val SUB = charArrayOf('₀','₁','₂','₃','₄','₅','₆','₇','₈','₉')

    private val GREEK = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "theta" to "θ", "lambda" to "λ", "mu" to "μ",
        "pi" to "π", "sigma" to "σ", "phi" to "φ", "omega" to "ω",
        "Delta" to "Δ", "Sigma" to "Σ", "Omega" to "Ω", "Pi" to "Π"
    )

    fun format(raw: String): CharSequence {
        val pre = chemistryAndMath(raw)
        return applyStyles(pre)
    }

    fun chemistryAndMath(src: String): String {
        var s = src
        s = s.replace(Regex("""\$\$([\s\S]+?)\$\$""")) { math(it.groupValues[1]) }
        s = s.replace(Regex("""\$([^$\n]+?)\$""")) { math(it.groupValues[1]) }
        s = s.replace(Regex("""\\\((.+?)\\\)""")) { math(it.groupValues[1]) }
        s = s.replace(Regex("""\\\[(.+?)\\]""")) { math(it.groupValues[1]) }
        s = s.replace(Regex("""(?<![\w])([A-Z][a-z]?)(\d+)((?:[A-Z][a-z]?\d*)*)""")) { m ->
            val rest = m.groupValues[3].replace(Regex("""(\d+)""")) { subDigits(it.groupValues[1]) }
            m.groupValues[1] + subDigits(m.groupValues[2]) + rest
        }
        return s
    }

    private fun math(expr: String): String {
        var e = expr.trim()
        GREEK.forEach { (k, v) ->
            e = e.replace(Regex("""\\$k\b"""), v)
        }
        e = e.replace(Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")) {
            "(${it.groupValues[1]})/(${it.groupValues[2]})"
        }
        e = e.replace(Regex("""\\sqrt\{([^{}]+)\}""")) { "√(${it.groupValues[1]})" }
        e = e.replace(Regex("""\\sqrt\s*([A-Za-z0-9]+)""")) { "√${it.groupValues[1]}" }
        e = e.replace("\\times", "×")
            .replace("\\cdot", "·")
            .replace("\\div", "÷")
            .replace("\\pm", "±")
            .replace("\\leq", "≤")
            .replace("\\geq", "≥")
            .replace("\\neq", "≠")
            .replace("\\approx", "≈")
            .replace("\\infty", "∞")
            .replace("\\rightarrow", "→")
            .replace("\\to", "→")
            .replace("\\left", "")
            .replace("\\right", "")
        e = e.replace(Regex("""\^\{([0-9+\-]+)\}""")) { superDigits(it.groupValues[1]) }
        e = e.replace(Regex("""\^([0-9+\-])""")) { superDigits(it.groupValues[1]) }
        e = e.replace(Regex("""_\{([0-9+\-]+)\}""")) { subDigits(it.groupValues[1]) }
        e = e.replace(Regex("""_([0-9+\-])""")) { subDigits(it.groupValues[1]) }
        return e
    }

    private fun superDigits(n: String): String = buildString {
        n.forEach { c ->
            append(
                when (c) {
                    in '0'..'9' -> SUPER[c - '0']
                    '+' -> SUPER[10]
                    '-' -> SUPER[11]
                    else -> c
                }
            )
        }
    }

    private fun subDigits(n: String): String = buildString {
        n.forEach { c ->
            append(if (c in '0'..'9') SUB[c - '0'] else c)
        }
    }

    private fun applyStyles(src: String): SpannableStringBuilder {
        val out = SpannableStringBuilder()
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("***", i) || src.startsWith("___", i) -> {
                    val token = src.substring(i, i + 3)
                    val end = src.indexOf(token, i + 3)
                    if (end > i) {
                        val start = out.length
                        out.append(src.substring(i + 3, end))
                        out.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 3
                    } else {
                        out.append(src[i]); i++
                    }
                }
                src.startsWith("**", i) || src.startsWith("__", i) -> {
                    val token = src.substring(i, i + 2)
                    val end = src.indexOf(token, i + 2)
                    if (end > i) {
                        val start = out.length
                        out.append(src.substring(i + 2, end))
                        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 2
                    } else {
                        out.append(src[i]); i++
                    }
                }
                src.startsWith("*", i) || src.startsWith("_", i) -> {
                    val token = src[i].toString()
                    val end = src.indexOf(token, i + 1)
                    if (end > i && !src[i + 1].isWhitespace()) {
                        val start = out.length
                        out.append(src.substring(i + 1, end))
                        out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else {
                        out.append(src[i]); i++
                    }
                }
                src.startsWith("`", i) -> {
                    val end = src.indexOf('`', i + 1)
                    if (end > i) {
                        val start = out.length
                        out.append(src.substring(i + 1, end))
                        out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        out.setSpan(RelativeSizeSpan(0.92f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        out.setSpan(ForegroundColorSpan(Color.parseColor("#1A73E8")), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else {
                        out.append(src[i]); i++
                    }
                }
                else -> {
                    out.append(src[i]); i++
                }
            }
        }
        return out
    }
}
