package io.github.sjos0.spritexp

import io.github.sjos0.spritexp.data.pipeline.AlphaMorph
import io.github.sjos0.spritexp.data.pipeline.ChromaKey
import io.github.sjos0.spritexp.data.pipeline.PixelArtCore
import io.github.sjos0.spritexp.data.pipeline.RenderOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaMorphAndDespillTest {

    private val green = 0xFF00FF00.toInt()
    private val magenta = 0xFFFF00FF.toInt()

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    // ---------- Despill ----------

    @Test
    fun despill_greenKeyFullStrength_matchesOldRule() {
        // G = min(G, max(R, B)): comportamento de antes com chave verde.
        val out = ChromaKey.despill(100f, 200f, 50f, green, 1f)
        assertEquals(100f, out[1], 0.01f)
        assertEquals(100f, out[0], 0.01f)
        assertEquals(50f, out[2], 0.01f)
    }

    @Test
    fun despill_strengthIsProportional() {
        val out = ChromaKey.despill(100f, 200f, 50f, green, 0.5f)
        assertEquals(150f, out[1], 0.01f)
    }

    @Test
    fun despill_zeroStrengthChangesNothing() {
        val out = ChromaKey.despill(100f, 200f, 50f, green, 0f)
        assertEquals(listOf(100f, 200f, 50f), out.toList())
    }

    @Test
    fun despill_followsMagentaKeyDirection() {
        // Magenta espalha em R e B: os dois são limitados ao G (que não é spill).
        val out = ChromaKey.despill(200f, 50f, 180f, magenta, 1f)
        assertEquals(50f, out[0], 0.01f)
        assertEquals(50f, out[2], 0.01f)
        assertEquals(50f, out[1], 0.01f)
    }

    @Test
    fun despill_doesNotTouchPixelsBelowLimit() {
        val out = ChromaKey.despill(180f, 40f, 30f, green, 1f)
        assertEquals(listOf(180f, 40f, 30f), out.toList())
    }

    // ---------- Erode / dilate ----------

    @Test
    fun erode_removesIsolatedPixel() {
        // 3x3: só o centro é opaco; erode 1 zera.
        val a = intArrayOf(0, 0, 0, 0, 255, 0, 0, 0, 0)
        AlphaMorph.erode(a, 3, 3, 1)
        assertEquals(0, a[4])
    }

    @Test
    fun erode_doesNotEatTheImageBorder() {
        // Sprite cheio até a borda da imagem não perde a borda.
        val a = IntArray(9) { 255 }
        AlphaMorph.erode(a, 3, 3, 1)
        assertTrue(a.all { it == 255 })
    }

    @Test
    fun dilate_fillsHoleWithNeighbourColor() {
        val a = IntArray(9) { if (it == 4) 255 else 0 }
        val r = FloatArray(9) { if (it == 4) 200f else 0f }
        val g = FloatArray(9) { if (it == 4) 30f else 0f }
        val b = FloatArray(9) { if (it == 4) 30f else 0f }
        AlphaMorph.dilate(a, r, g, b, 3, 3, 1)
        // Vizinho em cruz (1) recebe alpha e a cor do centro, não preto.
        assertEquals(255, a[1])
        assertEquals(200f, r[1], 0.01f)
        assertEquals(30f, g[1], 0.01f)
        // Canto (0) fica de fora da cruz.
        assertEquals(0, a[0])
    }

    // ---------- Matte view e morfologia no conversor ----------

    @Test
    fun matteView_showsSubjectWhiteAndRemovedBlack() {
        val opts = RenderOptions(
            chroma = ChromaKey.specFor(
                enabled = true, keyArgb = green, extraKeyArgb = IntArray(0), modeDistance = true,
                distanceTolerance = 35f, hueTolerance = 25f, satMin = 0.35f, valMin = 0.12f,
                feather = 0f, softEdges = false
            ),
            softEdges = false, despill = false, despillKeyArgb = green,
            brightness = 0f, contrast = 0f, edgeEnhance = 0f, ditherModeName = "NONE",
            matteView = true
        )
        val out = PixelArtCore.render(intArrayOf(green, argb(200, 30, 30)), 2, 1, opts, PixelArtCore.DEFAULT_PALETTE)
        assertEquals(0xFF000000.toInt(), out[0])
        assertEquals(0xFFFFFFFF.toInt(), out[1])
    }

    @Test
    fun conversion_erodeRemovesOnePixelFringe() {
        // Sujeito de 3x3 numa faixa de 5 px: erode 1 corta a camada externa.
        val w = 5
        val h = 3
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x in 1..3 && y in 0..2) argb(200, 30, 30) else green
        }
        val opts = RenderOptions(
            chroma = ChromaKey.specFor(
                enabled = true, keyArgb = green, extraKeyArgb = IntArray(0), modeDistance = true,
                distanceTolerance = 35f, hueTolerance = 25f, satMin = 0.35f, valMin = 0.12f,
                feather = 0f, softEdges = false
            ),
            softEdges = false, despill = false, despillKeyArgb = green,
            brightness = 0f, contrast = 0f, edgeEnhance = 0f, ditherModeName = "NONE",
            erode = 1
        )
        val out = PixelArtCore.render(px, w, h, opts, PixelArtCore.DEFAULT_PALETTE)
        // Coluna x=1 está na borda do sujeito: some. Centro (x=2,y=1) continua.
        assertEquals(0, (out[1 * w + 1] ushr 24) and 0xFF)
        assertEquals(255, (out[1 * w + 2] ushr 24) and 0xFF)
    }

    // ---------- Cor dominante da borda (Detectar fundo) ----------

    @Test
    fun detectBackground_findsUniformGreenBorder() {
        val w = 4
        val h = 4
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) green else argb(200, 30, 30)
        }
        val result = ChromaKey.dominantBorderColor(px, w, h, ring = 1)
        assertEquals(green, result!!.first)
        assertEquals(1f, result.second, 0.001f)
    }

    @Test
    fun detectBackground_returnsNullWhenBorderHasNoDominantColor() {
        val w = 4
        val h = 4
        // 12 cores de borda, todas em bins diferentes: nenhuma chega a 25%.
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            // Canal limitado a 0..255: (i % 12) * 20 nunca estoura o byte do canal.
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) argb((i % 12) * 20, 0, 0) else argb(0, 0, 0)
        }
        assertNull(ChromaKey.dominantBorderColor(px, w, h, ring = 1))
    }
}
