package io.github.sjos0.spritexp.data.pipeline


/**
 * Extração automática de paleta por median cut.
 *
 * Trabalha só com inteiros ARGB e não depende de android.graphics,
 * então pode ser testado em JVM puro.
 */
object PaletteExtractor {

    /** Limite de amostras para manter a extração rápida em imagens grandes. */
    private const val MAX_SAMPLE = 16_384

    /**
     * Retorna até [maxColors] cores representativas.
     * Pixels transparentes e, se [chromaKeyArgb] for informado, pixels da cor de fundo são ignorados.
     */
    fun extract(
        argbPixels: IntArray,
        maxColors: Int,
        chromaKeyArgb: Int? = null,
        chromaTolerancePct: Float = 0f
    ): List<Int> = extractFor(
        argbPixels,
        maxColors,
        chromaKeyArgb?.let { ChromaSpec(keyArgb = it, distanceTolerance = chromaTolerancePct) }
    )

    /**
     * Mesmo critério de fundo do conversor: a chave é a mesma [ChromaSpec] usada em [PixelArtCore].
     * Assim a paleta automática nunca reintroduz uma cor que a chave já removeu.
     */
    fun extractFor(argbPixels: IntArray, maxColors: Int, chroma: ChromaSpec?): List<Int> {
        val wanted = maxColors.coerceIn(2, 256)
        val step = maxOf(1, argbPixels.size / MAX_SAMPLE)

        val samples = ArrayList<Int>()
        var i = 0
        while (i < argbPixels.size) {
            val p = argbPixels[i]
            i += step
            if (alphaOf(p) < 128) continue
            if (chroma != null && chroma.isKeyed(p)) continue
            samples.add(p)
        }
        if (samples.isEmpty()) return emptyList()

        val boxes = mutableListOf(Box(samples.toIntArray()))
        while (boxes.size < wanted) {
            val target = boxes.maxByOrNull { it.range() } ?: break
            if (target.range() == 0) break
            boxes.remove(target)
            val (a, b) = target.split()
            boxes.add(a)
            boxes.add(b)
        }
        return boxes.map { it.average() }.distinct()
    }

    /** Compatibilidade: delega ao mesmo critério de [ChromaSpec] (distância RGB). */
    fun isChroma(pixel: Int, key: Int, tolerancePct: Float): Boolean =
        ChromaSpec(keyArgb = key, distanceTolerance = tolerancePct).isKeyed(pixel)

    private fun alphaOf(p: Int): Int = (p ushr 24) and 0xFF

    /** c = 0 vermelho, 1 verde, 2 azul. */
    private fun channel(p: Int, c: Int): Int = (p shr (16 - 8 * c)) and 0xFF

    /** Caixa de cores usada pelo median cut. */
    private class Box(val pixels: IntArray) {

        /** Retorna [amplitude, canal] do canal com maior variação. */
        private fun widestChannel(): IntArray {
            var best = 0
            var bestChannel = 0
            for (c in 0..2) {
                var min = 255
                var max = 0
                for (p in pixels) {
                    val v = channel(p, c)
                    if (v < min) min = v
                    if (v > max) max = v
                }
                if (max - min > best) {
                    best = max - min
                    bestChannel = c
                }
            }
            return intArrayOf(best, bestChannel)
        }

        fun range(): Int = widestChannel()[0]

        fun split(): Pair<Box, Box> {
            val ch = widestChannel()[1]
            val sorted = pixels.sortedBy { channel(it, ch) }
            val mid = sorted.size / 2
            return Box(sorted.subList(0, mid).toIntArray()) to
                Box(sorted.subList(mid, sorted.size).toIntArray())
        }

        fun average(): Int {
            var r = 0L
            var g = 0L
            var b = 0L
            for (p in pixels) {
                r += channel(p, 0)
                g += channel(p, 1)
                b += channel(p, 2)
            }
            val n = pixels.size.toLong()
            return (0xFF shl 24) or
                ((r / n).toInt() shl 16) or
                ((g / n).toInt() shl 8) or
                (b / n).toInt()
        }
    }
}
