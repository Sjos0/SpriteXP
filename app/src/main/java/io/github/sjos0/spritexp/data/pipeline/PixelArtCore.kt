package io.github.sjos0.spritexp.data.pipeline

import kotlin.math.max
import kotlin.math.min

/** Opções de renderização. Sem dependência de android.graphics, então roda em teste JVM. */
class RenderOptions(
    val chroma: ChromaSpec?,
    /** true = alpha intermediário da chave sobrevive à paleta (modo Suave). */
    val softEdges: Boolean,
    val despill: Boolean,
    val despillKeyArgb: Int,
    val brightness: Float,
    val contrast: Float,
    val edgeEnhance: Float,
    val ditherModeName: String
)

/**
 * Núcleo do conversor de pixel art: chave → despill → ajustes → realce → paleta.
 * Entrada e saída são ARGB inteiros (w * h, linha a linha).
 */
object PixelArtCore {

    private val bayerMatrix4x4 = arrayOf(
        intArrayOf(0, 8, 2, 10),
        intArrayOf(12, 4, 14, 6),
        intArrayOf(3, 11, 1, 9),
        intArrayOf(15, 7, 13, 5)
    )

    /** Mesma paleta de fallback que o Android usa para as cores básicas. */
    val DEFAULT_PALETTE = intArrayOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF888888.toInt(), 0xFFFF0000.toInt(),
        0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt()
    )

    /**
     * Alpha do pixel de origem depois da chave, antes da paleta.
     * Modo Duro: 0 ou 255. Modo Suave: mantém o alpha intermediário da borda.
     */
    fun keyAlpha(argb: Int, chroma: ChromaSpec?, softEdges: Boolean): Int {
        val a = (argb ushr 24) and 0xFF
        if (a <= 30) return 0
        if (chroma == null) {
            return if (softEdges) a else if (a < 128) 0 else 255
        }
        val keyed = chroma.alphaOf(argb)
        return if (softEdges) {
            min(a, keyed)
        } else {
            if (keyed < 128 || a < 128) 0 else 255
        }
    }

    fun render(pixels: IntArray, w: Int, h: Int, opts: RenderOptions, palette: IntArray): IntArray {
        val n = w * h
        val rBuf = FloatArray(n)
        val gBuf = FloatArray(n)
        val bBuf = FloatArray(n)
        val aBuf = IntArray(n)

        val contrastFactor = if (opts.contrast != 0f) {
            (259f * (opts.contrast + 255f)) / (255f * (259f - opts.contrast))
        } else 1.0f

        for (i in 0 until n) {
            val color = pixels[i]
            val keyA = keyAlpha(color, opts.chroma, opts.softEdges)
            aBuf[i] = keyA
            if (keyA == 0) continue

            var r = ((color shr 16) and 0xFF).toFloat()
            var g = ((color shr 8) and 0xFF).toFloat()
            var b = (color and 0xFF).toFloat()

            if (opts.despill) {
                val ds = ChromaKey.despill(r, g, b)
                r = ds[0]; g = ds[1]; b = ds[2]
            }

            if (opts.brightness != 0f) {
                r += opts.brightness
                g += opts.brightness
                b += opts.brightness
            }

            if (opts.contrast != 0f) {
                r = contrastFactor * (r - 128f) + 128f
                g = contrastFactor * (g - 128f) + 128f
                b = contrastFactor * (b - 128f) + 128f
            }

            rBuf[i] = min(255f, max(0f, r))
            gBuf[i] = min(255f, max(0f, g))
            bBuf[i] = min(255f, max(0f, b))
        }

        if (opts.edgeEnhance > 0f) {
            val factor = (opts.edgeEnhance / 100f) * 0.4f
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    if (aBuf[idx] == 0) continue
                    val up = (y - 1) * w + x
                    val down = (y + 1) * w + x
                    val left = y * w + (x - 1)
                    val right = y * w + (x + 1)

                    val edgeR = (4 * rBuf[idx] - rBuf[up] - rBuf[down] - rBuf[left] - rBuf[right]) * factor
                    val edgeG = (4 * gBuf[idx] - gBuf[up] - gBuf[down] - gBuf[left] - gBuf[right]) * factor
                    val edgeB = (4 * bBuf[idx] - bBuf[up] - bBuf[down] - bBuf[left] - bBuf[right]) * factor

                    rBuf[idx] = min(255f, max(0f, rBuf[idx] + edgeR))
                    gBuf[idx] = min(255f, max(0f, gBuf[idx] + edgeG))
                    bBuf[idx] = min(255f, max(0f, bBuf[idx] + edgeB))
                }
            }
        }

        val result = IntArray(n)
        when (opts.ditherModeName) {
            "ORDERED" -> {
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val i = y * w + x
                        if (aBuf[i] == 0) {
                            result[i] = 0
                            continue
                        }
                        val threshold = (bayerMatrix4x4[y % 4][x % 4] - 8) * 4f
                        val dr = min(255f, max(0f, rBuf[i] + threshold))
                        val dg = min(255f, max(0f, gBuf[i] + threshold))
                        val db = min(255f, max(0f, bBuf[i] + threshold))
                        result[i] = withAlpha(findNearestColor(dr, dg, db, palette), aBuf[i])
                    }
                }
            }
            "FLOYD_STEINBERG" -> {
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val i = y * w + x
                        if (aBuf[i] == 0) {
                            result[i] = 0
                            continue
                        }
                        val oldR = rBuf[i]
                        val oldG = gBuf[i]
                        val oldB = bBuf[i]
                        val newColor = findNearestColor(oldR, oldG, oldB, palette)
                        result[i] = withAlpha(newColor, aBuf[i])

                        val nr = ((newColor shr 16) and 0xFF).toFloat()
                        val ng = ((newColor shr 8) and 0xFF).toFloat()
                        val nb = (newColor and 0xFF).toFloat()
                        val errR = oldR - nr
                        val errG = oldG - ng
                        val errB = oldB - nb

                        if (x + 1 < w) {
                            val rightIdx = y * w + (x + 1)
                            rBuf[rightIdx] = min(255f, max(0f, rBuf[rightIdx] + errR * 7f / 16f))
                            gBuf[rightIdx] = min(255f, max(0f, gBuf[rightIdx] + errG * 7f / 16f))
                            bBuf[rightIdx] = min(255f, max(0f, bBuf[rightIdx] + errB * 7f / 16f))
                        }
                        if (y + 1 < h) {
                            if (x - 1 >= 0) {
                                val dlIdx = (y + 1) * w + (x - 1)
                                rBuf[dlIdx] = min(255f, max(0f, rBuf[dlIdx] + errR * 3f / 16f))
                                gBuf[dlIdx] = min(255f, max(0f, gBuf[dlIdx] + errG * 3f / 16f))
                                bBuf[dlIdx] = min(255f, max(0f, bBuf[dlIdx] + errB * 3f / 16f))
                            }
                            val downIdx = (y + 1) * w + x
                            rBuf[downIdx] = min(255f, max(0f, rBuf[downIdx] + errR * 5f / 16f))
                            gBuf[downIdx] = min(255f, max(0f, gBuf[downIdx] + errG * 5f / 16f))
                            bBuf[downIdx] = min(255f, max(0f, bBuf[downIdx] + errB * 5f / 16f))

                            if (x + 1 < w) {
                                val drIdx = (y + 1) * w + (x + 1)
                                rBuf[drIdx] = min(255f, max(0f, rBuf[drIdx] + errR * 1f / 16f))
                                gBuf[drIdx] = min(255f, max(0f, gBuf[drIdx] + errG * 1f / 16f))
                                bBuf[drIdx] = min(255f, max(0f, bBuf[drIdx] + errB * 1f / 16f))
                            }
                        }
                    }
                }
            }
            else -> {
                for (i in 0 until n) {
                    result[i] = if (aBuf[i] == 0) 0 else withAlpha(findNearestColor(rBuf[i], gBuf[i], bBuf[i], palette), aBuf[i])
                }
            }
        }
        return result
    }

    /** Cor da paleta com o alpha do pixel. Em modo Duro o alpha é 255, então a cor fica opaca como antes. */
    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun findNearestColor(r: Float, g: Float, b: Float, palette: IntArray): Int {
        var closest = palette[0]
        var minDist = Float.MAX_VALUE
        for (color in palette) {
            val pr = ((color shr 16) and 0xFF).toFloat()
            val pg = ((color shr 8) and 0xFF).toFloat()
            val pb = (color and 0xFF).toFloat()
            val dist = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            if (dist < minDist) {
                minDist = dist
                closest = color
            }
        }
        return closest
    }
}
