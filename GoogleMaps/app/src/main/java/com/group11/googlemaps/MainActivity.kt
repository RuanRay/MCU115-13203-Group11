package com.group11.googlemaps

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import android.util.Log
import com.google.maps.model.EncodedPolyline
import com.google.maps.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val taipei101 = LatLng(25.033611, 121.565000)
    private val taipeiMainStation = LatLng(25.047924, 121.517081)

    private var map: GoogleMap? = null
    private var route: Polyline? = null
    private var apiKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 從 AndroidManifest 的 meta-data 取得金鑰（實際值來自 local.properties）
        apiKey = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY")

        findViewById<Button>(R.id.btnDriving).setOnClickListener { drawRoute(TravelMode.DRIVING) }
        findViewById<Button>(R.id.btnWalking).setOnClickListener { drawRoute(TravelMode.WALKING) }
        findViewById<Button>(R.id.btnBicycling).setOnClickListener { drawRoute(TravelMode.BICYCLING) }

        loadMap()
    }

    private fun loadMap() {
        (supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment)
            .getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        val fine = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine || !coarse) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                0
            )
            return
        }

        this.map = map
        map.isMyLocationEnabled = true
        map.addMarker(MarkerOptions().position(taipei101).title("台北101"))
        map.addMarker(MarkerOptions().position(taipeiMainStation).title("台北車站"))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(25.04, 121.54), 13f))

        drawRoute(TravelMode.WALKING) // 預設：走路
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadMap()
        }
    }

    private fun drawRoute(mode: TravelMode) {
        val map = map ?: return
        val key = apiKey
        if (key.isNullOrEmpty()) {
            Log.e(TAG, "MAPS_API_KEY 未設定（local.properties）")
            return showNoRoute()
        }

        lifecycleScope.launch {
            val path = try {
                withContext(Dispatchers.IO) { fetchRoute(key, mode) }
            } catch (e: Exception) {
                Log.e(TAG, "查詢路線失敗", e)
                null
            }

            route?.remove()
            route = null
            if (path.isNullOrEmpty()) {
                showNoRoute()
                return@launch
            }
            route = map.addPolyline(
                PolylineOptions().addAll(path).color(Color.RED).width(15f)
            )
        }
    }

    /**
     * 老師範例用的 Directions API 已被 Google 列為 Legacy，新專案無法啟用（回傳 REQUEST_DENIED），
     * 因此改呼叫 Routes API。回傳的 encoded polyline 格式相同，仍用 maps-services 的 EncodedPolyline 解碼。
     */
    private fun fetchRoute(key: String, mode: TravelMode): List<LatLng> {
        val travelMode = when (mode) {
            TravelMode.DRIVING -> "DRIVE"
            TravelMode.BICYCLING -> "BICYCLE"
            else -> "WALK"
        }
        val body = JSONObject()
            .put("origin", waypoint(taipei101))
            .put("destination", waypoint(taipeiMainStation))
            .put("travelMode", travelMode)

        val conn = URL("https://routes.googleapis.com/directions/v2:computeRoutes")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Goog-Api-Key", key)
            conn.setRequestProperty("X-Goog-FieldMask", "routes.polyline.encodedPolyline")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.readText()}")
            }
            val routes = JSONObject(conn.inputStream.bufferedReader().readText())
                .optJSONArray("routes") ?: return emptyList() // 該交通方式無路線
            val encoded = routes.getJSONObject(0)
                .getJSONObject("polyline").getString("encodedPolyline")
            return EncodedPolyline(encoded).decodePath().map { LatLng(it.lat, it.lng) }
        } finally {
            conn.disconnect()
        }
    }

    private fun waypoint(p: LatLng) = JSONObject().put(
        "location", JSONObject().put(
            "latLng", JSONObject().put("latitude", p.latitude).put("longitude", p.longitude)
        )
    )

    private fun showNoRoute() {
        Toast.makeText(this, R.string.no_route, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
