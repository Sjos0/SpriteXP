package io.github.sjos0.spritexp.data.pipeline

import kotlin.math.min

/**
 * Morfologia do matte (alpha), com vizinhança em cruz (4 vizinhos). Puro, sem Bitmap.
 * Erode limpa franjas e halos; dilate fecha buracos de 1 px. Operam nos próprios arrays,
 * para não criar bitmaps extras no loop do conversor.
 */
object AlphaMorph {

    /**
     * Erode: cada pixel fica com o menor alpha entre ele e os vizinhos.
     * A borda da imagem não é corroída: vizinho fora da imagem é ignorado.
     */
    fun erode(alpha: IntArray, w: Int, h: Int, iterations: Int) {
        repeat(iterations) {
            val out = alpha.copyOf()
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    var m = alpha[i]
                    if (x > 0) m = min(m, alpha[i - 1])
                    if (x < w - 1) m = min(m, alpha[i + 1])
                    if (y > 0) m = min(m, alpha[i - w])
                    if (y < h - 1) m = min(m, alpha[i + w])
                    out[i] = m
                }
            }
            out.copyInto(alpha)
        }
    }

    /**
     * Dilate: pixel transparente herda o maior alpha vizinho e a cor desse vizinho.
     * Copiar a cor evita que o buraco preenchido saia preto.
     */
    fun dilate(alpha: IntArray, r: FloatArray, g: FloatArray, b: FloatArray, w: Int, h: Int, iterations: Int) {
        repeat(iterations) {
            val a2 = alpha.copyOf()
            val r2 = r.copyOf()
            val g2 = g.copyOf()
            val b2 = b.copyOf()
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    if (alpha[i] > 0) continue
                    var best = 0
                    var from = -1
                    if (x > 0 && alpha[i - 1] > best) { best = alpha[i - 1]; from = i - 1 }
                    if (x < w - 1 && alpha[i + 1] > best) { best = alpha[i + 1]; from = i + 1 }
                    if (y > 0 && alpha[i - w] > best) { best = alpha[i - w]; from = i - w }
                    if (y < h - 1 && alpha[i + w] > best) { best = alpha[i + w]; from = i + w }
                    if (from >= 0) {
                        a2[i] = best
                        r2[i] = r[from]
                        g2[i] = g[from]
                        b2[i] = b[from]
                    }
                }
            }
            a2.copyInto(alpha)
            r2.copyInto(r)
            g2.copyInto(g)
            b2.copyInto(b)
        }
    }
}
