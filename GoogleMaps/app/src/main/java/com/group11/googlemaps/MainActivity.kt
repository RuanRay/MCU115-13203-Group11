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
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val taipei101 = LatLng(25.033611, 121.565000)
    private val taipeiMainStation = LatLng(25.047924, 121.517081)

    private var map: GoogleMap? = null
    private var route: Polyline? = null
    private var geoApiContext: GeoApiContext? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 從 AndroidManifest 的 meta-data 取得金鑰（實際值來自 local.properties）
        val apiKey = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY")
        if (!apiKey.isNullOrEmpty()) {
            geoApiContext = GeoApiContext.Builder().apiKey(apiKey).build()
        }

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
        val context = geoApiContext ?: return showNoRoute()

        lifecycleScope.launch {
            val path = try {
                withContext(Dispatchers.IO) {
                    DirectionsApi.newRequest(context)
                        .origin(com.google.maps.model.LatLng(taipei101.latitude, taipei101.longitude))
                        .destination(
                            com.google.maps.model.LatLng(
                                taipeiMainStation.latitude, taipeiMainStation.longitude
                            )
                        )
                        .mode(mode)
                        .await()
                        .routes.firstOrNull()
                        ?.overviewPolyline?.decodePath()
                        ?.map { LatLng(it.lat, it.lng) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

    private fun showNoRoute() {
        Toast.makeText(this, R.string.no_route, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        geoApiContext?.shutdown()
        super.onDestroy()
    }
}
