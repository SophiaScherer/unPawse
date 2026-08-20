package com.example.unpawse.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A category's identity is its hue; a theme flip may only move its luminance. Luminance *ordering*
 * is deliberately not asserted — the four slots sit at near-identical luminance within a theme, so
 * their order is noise a one-point tweak would flip.
 */
class ExtendedColorsTest {

    private val light = extendedColorsFor(darkTheme = false)
    private val dark = extendedColorsFor(darkTheme = true)

    private fun UnPawseExtendedColors.categories() = listOf(
        "Social" to categorySocial,
        "Productivity" to categoryProductivity,
        "Entertainment" to categoryEntertainment,
        "Other" to categoryOther,
    )

    /** The three the user actually chose between; Other is a neutral and has no hue to defend. */
    private fun UnPawseExtendedColors.brandedCategories() = categories().dropLast(1)

    private fun eachTheme(body: (String, UnPawseExtendedColors) -> Unit) {
        body("light", light)
        body("dark", dark)
    }

    private fun pairs(
        slots: List<Pair<String, Color>>,
        body: (String, Color, String, Color) -> Unit,
    ) {
        slots.forEachIndexed { i, (nameA, a) ->
            slots.drop(i + 1).forEach { (nameB, b) -> body(nameA, a, nameB, b) }
        }
    }

    @Test
    fun `every category keeps its hue when the theme flips`() {
        light.categories().zip(dark.categories()) { (name, lit), (_, drk) ->
            val drift = hueDistance(hueOf(lit), hueOf(drk))
            assertTrue("$name drifted $drift° between themes", drift <= 15f)
        }
    }

    /**
     * The bug this file was written for: Social and Entertainment were one hue at two lightnesses,
     * so inverting the schemes exchanged them. Measured as ΔE rather than hue angle because plum
     * and coral sit only 35° apart on the wheel yet are in no danger of being confused.
     */
    @Test
    fun `the branded categories are perceptually distinct in both themes`() {
        eachTheme { theme, colors ->
            pairs(colors.brandedCategories()) { nameA, a, nameB, b ->
                val apart = deltaE(a, b)
                assertTrue("$nameA and $nameB are only ΔE $apart apart in $theme", apart >= 35f)
            }
        }
    }

    /** Other is a neutral, so it sits nearer the muted plum than two brand hues ever would. */
    @Test
    fun `no category is confusable with another in either theme`() {
        eachTheme { theme, colors ->
            pairs(colors.categories()) { nameA, a, nameB, b ->
                val apart = deltaE(a, b)
                assertTrue("$nameA and $nameB are only ΔE $apart apart in $theme", apart >= 15f)
            }
        }
    }

    @Test
    fun `every category reads against the card it is drawn on`() {
        eachTheme { theme, colors ->
            colors.categories().forEach { (name, value) ->
                val ratio = contrastRatio(value, colors.cardSurface)
                assertTrue("$name is $ratio:1 on the $theme card", ratio >= 4.5f)
            }
        }
    }

    /**
     * Issue 23: the dark card was #120D0E on a #171213 page — *below* the ground it floats on, so
     * the 24dp rounded cards had no shadow to cast and effectively vanished.
     */
    @Test
    fun `cards sit above the page in both themes`() {
        assertTrue(luminance(light.cardSurface) > luminance(WarmWhite))
        assertTrue(luminance(dark.cardSurface) > luminance(DarkSurface))
    }

    @Test
    fun `the success container carries its own text`() {
        eachTheme { theme, colors ->
            val ratio = contrastRatio(colors.onSuccessContainer, colors.successContainer)
            assertTrue("success text is $ratio:1 in $theme", ratio >= 4.5f)
        }
    }
}

/** Hue in degrees. Computed here rather than pulled from a graphics helper so this stays pure JVM. */
private fun hueOf(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val delta = hi - lo
    if (delta == 0f) return 0f
    val hue = when (hi) {
        r -> ((g - b) / delta) % 6f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } * 60f
    return if (hue < 0f) hue + 360f else hue
}

/** Shortest way round the colour wheel — 350° and 10° are 20° apart, not 340°. */
private fun hueDistance(a: Float, b: Float): Float {
    val raw = abs(a - b)
    return min(raw, 360f - raw)
}

private fun luminance(color: Color): Float {
    fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

/** CIE76 ΔE — enough to answer "can these two dots be told apart?" without a colour library. */
private fun deltaE(a: Color, b: Color): Float {
    val (l1, a1, b1) = toLab(a)
    val (l2, a2, b2) = toLab(b)
    return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
}

private fun toLab(color: Color): Triple<Float, Float, Float> {
    fun linear(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    val r = linear(color.red)
    val g = linear(color.green)
    val b = linear(color.blue)
    // sRGB → XYZ (D65), then XYZ → Lab against the D65 white point.
    val x = (r * 0.4124f + g * 0.3576f + b * 0.1805f) / 0.95047f
    val y = r * 0.2126f + g * 0.7152f + b * 0.0722f
    val z = (r * 0.0193f + g * 0.1192f + b * 0.9505f) / 1.08883f
    fun f(t: Float) = if (t > 0.008856f) t.pow(1f / 3f) else 7.787f * t + 16f / 116f
    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    return Triple(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
}

private fun contrastRatio(a: Color, b: Color): Float {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}
