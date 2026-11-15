package io.github.sjos0.spritexp.data.pipeline

import android.graphics.Bitmap
import io.github.sjos0.spritexp.domain.usecase.PixelArtConverterUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adaptador Android do conversor: reduz o bitmap, delega ao [PixelArtCore] e devolve o ARGB.
 * Toda a lógica de pixel fica no núcleo puro, testável em JVM.
 */
class PixelArtConverterPipeline : PixelArtConverterUseCase {

    override suspend fun convert(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        chromaKeyEnabled: Boolean,
        chromaColorInt: Int,
        chromaTolerance: Float,
        contrast: Float,
        brightness: Float,
        edgeEnhance: Float,
        paletteColorsInt: List<Int>,
        ditherModeName: String,
        chromaModeDistance: Boolean,
        chromaHueTolerance: Float,
        chromaSatMin: Float,
        chromaValMin: Float,
        chromaFeather: Float,
        chromaDespill: Boolean,
        extraChromaColorsInt: IntArray,
        keyEdgeSoft: Boolean,
        despillStrength: Float,
        matteErode: Int,
        matteDilate: Int,
        matteView: Boolean
    ): Bitmap = withContext(Dispatchers.Default) {
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, false)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val chroma = ChromaKey.specFor(
            enabled = chromaKeyEnabled,
            keyArgb = chromaColorInt,
            extraKeyArgb = extraChromaColorsInt,
            modeDistance = chromaModeDistance,
            distanceTolerance = chromaTolerance,
            hueTolerance = chromaHueTolerance,
            satMin = chromaSatMin,
            valMin = chromaValMin,
            feather = chromaFeather,
            softEdges = keyEdgeSoft
        )
        val opts = RenderOptions(
            chroma = chroma,
            softEdges = keyEdgeSoft,
            despill = chromaDespill,
            despillKeyArgb = chromaColorInt,
            brightness = brightness,
            contrast = contrast,
            edgeEnhance = edgeEnhance,
            ditherModeName = ditherModeName,
            despillStrength = despillStrength,
            erode = matteErode,
            dilate = matteDilate,
            matteView = matteView
        )
        val palette = if (paletteColorsInt.isNotEmpty()) paletteColorsInt.toIntArray() else PixelArtCore.DEFAULT_PALETTE
        val out = PixelArtCore.render(pixels, w, h, opts, palette)

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(out, 0, w, 0, 0, w, h)
        outBitmap
    }
}
