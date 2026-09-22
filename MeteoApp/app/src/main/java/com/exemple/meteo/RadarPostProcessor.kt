package com.exemple.meteo

import android.graphics.Bitmap
import android.graphics.Color

object RadarPostProcessor {

    /**
     * Convertit une grille 2D d'intensité de précipitation (mm/h) en un Bitmap transparent avec palette de couleurs.
     */
    fun convertGridToBitmap(
        gridData: Array<FloatArray>,
        width: Int,
        height: Int
    ): Bitmap {
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val precipitationRate = gridData[y][x]
                pixels[y * width + x] = mapPrecipitationToColor(precipitationRate)
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Conversion taux de précipitation (mm/h) -> Couleur RGBA
     */
    private fun mapPrecipitationToColor(rate: Float): Int {
        return when {
            rate < 0.1f -> Color.TRANSPARENT                      // Pas de pluie
            rate < 1.0f -> Color.argb(120, 102, 204, 255)         // Pluie très faible
            rate < 2.5f -> Color.argb(160, 0, 128, 255)           // Pluie faible
            rate < 5.0f -> Color.argb(180, 0, 204, 102)           // Pluie modérée
            rate < 10.0f -> Color.argb(200, 255, 204, 0)          // Pluie soutenue
            rate < 20.0f -> Color.argb(220, 255, 128, 0)          // Pluie forte
            else -> Color.argb(240, 255, 0, 0)                    // Pluie extrême / Grêle
        }
    }
}
