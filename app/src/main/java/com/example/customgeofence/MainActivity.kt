package com.example.customgeofence

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_LOCATION_PERMISSION = 1001
    private val GEOFENCE_RADIUS_IN_METERS = 100.0f
    private val duration = 5000L // milliseconds

    private val _statusLiveData = MutableLiveData<GeofenceStatus>()
    private val statusLiveData: LiveData<GeofenceStatus> = _statusLiveData

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        val btn = findViewById<Button>(R.id.start_btn)

        btn.setOnClickListener {
            if (!isLocationPermissionsGranted()) {
                requestLocationPermission()
            } else {
                startLocationUpdates()
            }
        }

        statusLiveData.observe(this) { status ->
            when (status) {
                GeofenceStatus.GEOFENCE_CURRENT_ENTER -> {
                    Log.d("Geofence Enter Event", "지오펜스 영역을 들어왔습니다.")
                    checkDwellTime()
                }
                GeofenceStatus.GEOFENCE_CURRENT_DWELL -> {
                    //timer.cancel()
                    Log.d("Geofence DWELL Event", "지오펜스 영역에 머물렀습니다.")
                    Toast.makeText(this, "지정된 시간 동안 지오펜스 내부에 머물렀습니다.", Toast.LENGTH_LONG).show()
                }
                GeofenceStatus.GEOFENCE_CURRENT_EXIT -> {
                    //timer.cancel()
                    Log.d("Geofence Exit Event", "지오펜스 영역을 벗어났습니다.")
                }
            }
        }
    }

    private fun isLocationPermissionsGranted() =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_CODE_LOCATION_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult?.lastLocation?.let { location ->
                    Log.d("Geofence Location", location.toString())
                    checkGeofence(location)
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun checkGeofence(currentLocation: Location) {
        val geofenceLat = 37.5465
        val geofenceLng = 126.9497

        val distance = calculateDistance(
            currentLocation.latitude,
            currentLocation.longitude,
            geofenceLat,
            geofenceLng
        )

        if (distance <= GEOFENCE_RADIUS_IN_METERS) {
            val isNotEnterStatus = _statusLiveData.value != GeofenceStatus.GEOFENCE_CURRENT_ENTER && _statusLiveData.value != GeofenceStatus.GEOFENCE_CURRENT_DWELL
            if (isNotEnterStatus) {
                _statusLiveData.postValue(GeofenceStatus.GEOFENCE_CURRENT_ENTER)
            }
        } else {
            val isNotExitStatus = _statusLiveData.value != GeofenceStatus.GEOFENCE_CURRENT_EXIT
            if (isNotExitStatus) {
                _statusLiveData.postValue(GeofenceStatus.GEOFENCE_CURRENT_EXIT)
            }
        }
    }

    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val radius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = (Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2))

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return (radius * c).toFloat()
    }

    private fun checkDwellTime() {
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                _statusLiveData.postValue(GeofenceStatus.GEOFENCE_CURRENT_DWELL)
            }
        }, duration)
    }
}