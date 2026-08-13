package com.fabrice.monumentsnearby

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.fabrice.monumentsnearby.location.LocationHelper
import com.fabrice.monumentsnearby.ui.MonumentsScreen
import com.fabrice.monumentsnearby.ui.MonumentsViewModel
import com.fabrice.monumentsnearby.ui.UiState
import com.fabrice.monumentsnearby.ui.theme.MonumentsNearbyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MonumentsViewModel by viewModels()
    private lateinit var locationHelper: LocationHelper

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) {
                locateAndLoad()
            } else {
                viewModel.onPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationHelper = LocationHelper(this)

        setContent {
            MonumentsNearbyTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MonumentsScreen(
                    state = state,
                    onLocate = { locateAndLoad() }
                )
            }
        }

        // Ne pas re-localiser à chaque rotation : seulement si on n'a pas déjà des résultats
        if (viewModel.state.value is UiState.Idle || viewModel.state.value is UiState.Error) {
            if (hasLocationPermission()) {
                locateAndLoad()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun locateAndLoad() {
        if (!hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        lifecycleScope.launch {
            val location = locationHelper.currentLocation()
            if (location != null) {
                viewModel.load(location.latitude, location.longitude)
            } else {
                viewModel.onLocationUnavailable()
            }
        }
    }
}
