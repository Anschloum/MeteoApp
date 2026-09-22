package com.exemple.meteo

import android.graphics.Bitmap
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.GroundOverlay

class RadarMapOverlayManager(private val mapView: MapView) {

    // Emprise géographique approximative de la mosaïque radar France Métropole Météo-France
    private val franceRadarBounds = BoundingBox(
        51.1,  // Latitude Nord
        9.6,   // Longitude Est
        41.3,  // Latitude Sud
        -5.2   // Longitude Ouest
    )

    private var currentOverlay: GroundOverlay? = null

    /**
     * Applique ou met à jour l'image radar sur la carte.
     */
    fun updateRadarOverlay(radarBitmap: Bitmap) {
        currentOverlay?.let { mapView.overlays.remove(it) }

        val groundOverlay = GroundOverlay().apply {
            setImage(radarBitmap)
            setPosition(
                GeoPoint(franceRadarBounds.latNorth, franceRadarBounds.lonWest),
                GeoPoint(franceRadarBounds.latSouth, franceRadarBounds.lonEast)
            )
            setTransparency(0.15f)
        }

        mapView.overlays.add(groundOverlay)
        currentOverlay = groundOverlay
        mapView.invalidate()
    }

    /**
     * Supprime l'overlay de la carte.
     */
    fun clearOverlay() {
        currentOverlay?.let {
            mapView.overlays.remove(it)
            currentOverlay = null
            mapView.invalidate()
        }
    }
}
