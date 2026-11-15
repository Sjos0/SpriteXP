package io.github.sjos0.spritexp

import io.github.sjos0.spritexp.data.pipeline.ChromaKey
import io.github.sjos0.spritexp.data.pipeline.PixelArtCore
import io.github.sjos0.spritexp.data.pipeline.RenderOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testa o núcleo puro do conversor: alpha de saída, paleta e chave. */
class PixelArtCoreTest {

    private val green = 0xFF00FF00.toInt()
    private val palette = intArrayOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()
    )

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun alphaOf(p: Int): Int = (p ushr 24) and 0xFF

    /** Linha de 3 pixels: sujeito vermelho, borda da faixa de feather, fundo verde puro. */
    private val row = intArrayOf(argb(255, 0, 0), argb(40, 255, 40), argb(0, 255, 0))

    private fun options(soft: Boolean, dither: String = "NONE") = RenderOptions(
        chroma = ChromaKey.specFor(
            enabled = true, keyArgb = green, extraKeyArgb = IntArray(0), modeDistance = true,
            distanceTolerance = 10f, hueTolerance = 25f, satMin = 0.35f, valMin = 0.12f,
            feather = 20f, softEdges = soft
        ),
        softEdges = soft,
        despill = false,
        despillKeyArgb = green,
        brightness = 0f,
        contrast = 0f,
        edgeEnhance = 0f,
        ditherModeName = dither
    )

    @Test
    fun softMode_keepsIntermediateAlphaAfterPalette() {
        val out = PixelArtCore.render(row, 3, 1, options(soft = true), palette)
        assertEquals(255, alphaOf(out[0]))
        val edge = alphaOf(out[1])
        assertTrue("borda suave deve ficar semitransparente, veio $edge", edge in 1..254)
        // A cor vem da paleta (verde mais próximo), com o alpha da borda.
        assertEquals(0x00FF00, out[1] and 0xFFFFFF)
        assertEquals(0, alphaOf(out[2]))
    }

    @Test
    fun hardMode_flattensTheSameEdgeToOpaque() {
        val out = PixelArtCore.render(row, 3, 1, options(soft = false), palette)
        assertEquals(255, alphaOf(out[0]))
        assertEquals(255, alphaOf(out[1]))
        assertEquals(0, alphaOf(out[2]))
    }

    @Test
    fun softMode_survivesFloydSteinbergDither() {
        val out = PixelArtCore.render(row, 3, 1, options(soft = true, dither = "FLOYD_STEINBERG"), palette)
        assertTrue(alphaOf(out[1]) in 1..254)
        assertEquals(0, alphaOf(out[2]))
    }

    @Test
    fun hardAndNoKey_opaquePixelPicksNearestPaletteColor() {
        val opts = RenderOptions(
            chroma = null, softEdges = false, despill = false, despillKeyArgb = green,
            brightness = 0f, contrast = 0f, edgeEnhance = 0f, ditherModeName = "NONE"
        )
        val out = PixelArtCore.render(intArrayOf(argb(250, 10, 10)), 1, 1, opts, palette)
        assertEquals(0xFFFF0000.toInt(), out[0])
    }

    @Test
    fun semiTransparentSource_isCutInHardMode() {
        // Alpha de origem 20 é praticamente invisível: não pode virar cor opaca.
        val opts = RenderOptions(
            chroma = null, softEdges = false, despill = false, despillKeyArgb = green,
            brightness = 0f, contrast = 0f, edgeEnhance = 0f, ditherModeName = "NONE"
        )
        val out = PixelArtCore.render(intArrayOf(argb(10, 10, 10, a = 20)), 1, 1, opts, palette)
        assertEquals(0, alphaOf(out[0]))
    }

    @Test
    fun softMode_preservesSourceAlphaWithoutKey() {
        val opts = RenderOptions(
            chroma = null, softEdges = true, despill = false, despillKeyArgb = green,
            brightness = 0f, contrast = 0f, edgeEnhance = 0f, ditherModeName = "NONE"
        )
        val out = PixelArtCore.render(intArrayOf(argb(250, 10, 10, a = 140)), 1, 1, opts, palette)
        assertEquals(140, alphaOf(out[0]))
    }
}
