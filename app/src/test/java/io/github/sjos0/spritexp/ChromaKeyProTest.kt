package io.github.sjos0.spritexp

import io.github.sjos0.spritexp.data.pipeline.ChromaKey
import io.github.sjos0.spritexp.data.pipeline.ChromaSpec
import io.github.sjos0.spritexp.data.pipeline.PaletteExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaKeyProTest {

    private val green = 0xFF00FF00.toInt()
    private val magenta = 0xFFFF00FF.toInt()

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    // ---------- Distância ----------

    @Test
    fun distanceMode_keysPureKeyColorAndKeepsRed() {
        val spec = ChromaSpec(keyArgb = green, distanceTolerance = 35f)
        assertEquals(0, spec.alphaOf(argb(0, 255, 0)))
        assertEquals(255, spec.alphaOf(argb(255, 0, 0)))
    }

    @Test
    fun distanceMode_toleranceIsAPercentageOfColorDistance() {
        // tolerância 10% ≈ 44 de distância RGB: a 42 some, a 70 fica.
        val spec = ChromaSpec(keyArgb = green, distanceTolerance = 10f)
        assertEquals(0, spec.alphaOf(argb(30, 255, 30)))
        assertEquals(255, spec.alphaOf(argb(50, 255, 50)))
    }

    // ---------- Feather (borda suave) ----------

    @Test
    fun feather_producesIntermediateAlphaInTheBand() {
        // distância ≈ 56,6: entre 44,2 (limite) e 64,2 (fim do feather).
        val spec = ChromaSpec(keyArgb = green, distanceTolerance = 10f, feather = 20f)
        val a = spec.alphaOf(argb(40, 255, 40))
        assertTrue("alpha intermediário esperado, veio $a", a in 100..220)
    }

    @Test
    fun hardEdge_ignoresFeather_soItIsBinary() {
        // specFor com softEdges=false zera o feather: mesmo pixel de borda vira 0 ou 255.
        val spec = ChromaKey.specFor(
            enabled = true, keyArgb = green, extraKeyArgb = IntArray(0), modeDistance = true,
            distanceTolerance = 10f, hueTolerance = 25f, satMin = 0.35f, valMin = 0.12f,
            feather = 20f, softEdges = false
        )!!
        assertEquals(255, spec.alphaOf(argb(40, 255, 40)))
    }

    @Test
    fun softEdge_keepsFeatherWhenRequested() {
        val spec = ChromaKey.specFor(
            enabled = true, keyArgb = green, extraKeyArgb = IntArray(0), modeDistance = true,
            distanceTolerance = 10f, hueTolerance = 25f, satMin = 0.35f, valMin = 0.12f,
            feather = 20f, softEdges = true
        )!!
        val a = spec.alphaOf(argb(40, 255, 40))
        assertTrue(a in 1..254)
    }

    @Test
    fun specFor_disabledReturnsNull() {
        assertNull(
            ChromaKey.specFor(
                enabled = false, keyArgb = green, extraKeyArgb = IntArray(0), modeDistance = true,
                distanceTolerance = 35f, hueTolerance = 25f, satMin = 0.35f, valMin = 0.12f,
                feather = 0f, softEdges = false
            )
        )
    }

    // ---------- Multi-chave ----------

    @Test
    fun extraKey_removesSecondBackgroundColor() {
        val withExtra = ChromaSpec(keyArgb = green, extraKeyArgb = intArrayOf(magenta), distanceTolerance = 35f)
        val onlyGreen = ChromaSpec(keyArgb = green, distanceTolerance = 35f)
        assertEquals(0, withExtra.alphaOf(argb(255, 0, 255)))
        assertEquals(255, onlyGreen.alphaOf(argb(255, 0, 255)))
    }

    // ---------- Modo Matiz ----------

    @Test
    fun hueMode_keysGreenToneThatDistanceWouldMiss() {
        // Matiz ~134°, perto do verde (120°): a chave por matiz remove; RGB puro teria margem diferente.
        val spec = ChromaSpec(keyArgb = green, modeDistance = false, hueTolerance = 25f)
        assertEquals(0, spec.alphaOf(argb(10, 161, 46)))
        assertEquals(255, spec.alphaOf(argb(200, 30, 30)))
    }

    // ---------- Paleta automática segue a mesma chave ----------

    @Test
    fun autoPalette_ignoresExtraKeyBackground() {
        val pixels = intArrayOf(argb(255, 0, 255), argb(255, 0, 255), argb(200, 30, 30))
        val spec = ChromaSpec(keyArgb = green, extraKeyArgb = intArrayOf(magenta), distanceTolerance = 35f)
        assertEquals(listOf(argb(200, 30, 30)), PaletteExtractor.extractFor(pixels, 4, spec))
    }

    @Test
    fun autoPalette_hueModeMatchesConverterKey() {
        // Antes a paleta usava só RGB: o verde em modo Matiz voltava para a paleta.
        val pixels = intArrayOf(argb(10, 161, 46), argb(200, 30, 30))
        val spec = ChromaSpec(keyArgb = green, modeDistance = false, hueTolerance = 25f)
        assertEquals(listOf(argb(200, 30, 30)), PaletteExtractor.extractFor(pixels, 4, spec))
    }
}
