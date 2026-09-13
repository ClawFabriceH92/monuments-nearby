package com.fabrice.monumentsnearby.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteClientTest {

    @Test
    fun `decode polyline6 reproduit les coordonnees`() {
        // Encodage de (48.858000, 2.294500) → (48.858500, 2.295000) en précision 1e-6
        val encoded = encode(listOf(48.858000 to 2.294500, 48.858500 to 2.295000))
        val decoded = RouteClient.decodePolyline6(encoded)

        assertEquals(2, decoded.size)
        assertEquals(48.858000, decoded[0].first, 1e-6)
        assertEquals(2.294500, decoded[0].second, 1e-6)
        assertEquals(48.858500, decoded[1].first, 1e-6)
        assertEquals(2.295000, decoded[1].second, 1e-6)
    }

    @Test
    fun `chaine vide donne une liste vide`() {
        assertEquals(0, RouteClient.decodePolyline6("").size)
    }

    /** Encodeur de référence (algorithme Google, précision 1e-6). */
    private fun encode(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var prevLat = 0
        var prevLon = 0
        points.forEach { (lat, lon) ->
            val iLat = Math.round(lat * 1e6).toInt()
            val iLon = Math.round(lon * 1e6).toInt()
            encodeValue(iLat - prevLat, sb)
            encodeValue(iLon - prevLon, sb)
            prevLat = iLat
            prevLon = iLon
        }
        return sb.toString()
    }

    private fun encodeValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }
}
