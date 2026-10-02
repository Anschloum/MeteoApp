package com.exemple.meteo

import android.graphics.Color
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

class RadarMapOverlayManager(private val mapView: MapView) {

    private val overlaysByTimestamp = mutableMapOf<Long, TilesOverlay>()
    private var currentOverlay: TilesOverlay? = null

    /**
     * Affiche une trame RainViewer. Les images sont de vraies tuiles Web Mercator :
     * elles restent donc alignées avec OpenStreetMap à tous les niveaux de zoom.
     */
    fun showFrame(host: String, frame: RadarFrame) {
        val overlay = overlaysByTimestamp.getOrPut(frame.time) {
            createOverlay(host, frame)
        }

        currentOverlay?.isEnabled = false
        overlay.isEnabled = true

        if (!mapView.overlays.contains(overlay)) {
            // La couche radar doit rester sous les marqueurs et autres overlays.
            mapView.overlays.add(0, overlay)
        }

        currentOverlay = overlay
        mapView.invalidate()
    }

    fun clearOverlays() {
        overlaysByTimestamp.values.forEach { overlay ->
            mapView.overlays.remove(overlay)
            overlay.onDetach(mapView)
        }
        overlaysByTimestamp.clear()
        currentOverlay = null
        mapView.invalidate()
    }

    private fun createOverlay(host: String, frame: RadarFrame): TilesOverlay {
        val normalizedHost = host.trimEnd('/')
        val normalizedPath = if (frame.path.startsWith('/')) frame.path else "/" + frame.path
        val tileBaseUrl = normalizedHost + normalizedPath + "/256/"

        val tileSource = object : OnlineTileSourceBase(
            "RainViewer-" + frame.time,
            0,
            20,
            256,
            ".png",
            arrayOf(tileBaseUrl),
            "© RainViewer"
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return getBaseUrl() +
                    MapTileIndex.getZoom(pMapTileIndex) + "/" +
                    MapTileIndex.getX(pMapTileIndex) + "/" +
                    MapTileIndex.getY(pMapTileIndex) +
                    "/2/1_1.png"
            }
        }

        val tileProvider = MapTileProviderBasic(mapView.context.applicationContext, tileSource)
        return TilesOverlay(tileProvider, mapView.context).apply {
            setLoadingBackgroundColor(Color.TRANSPARENT)
            setLoadingLineColor(Color.TRANSPARENT)
            isEnabled = false
        }
    }
}
