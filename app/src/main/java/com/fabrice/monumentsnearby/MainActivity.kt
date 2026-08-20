package com.fabrice.monumentsnearby
import com.fabrice.monumentsnearby.update.UpdateManager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UpdateManager.start(this)
        locationHelper = LocationHelper(this)

        // Android 13+ : demander la permission de notifications pour le géofencing
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MonumentsNearbyTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MonumentsScreen(
                    state = state,
                    viewModel = viewModel,
                    onLocate = { locateAndLoad() },
                    onToggleGeofences = { toggleGeofences() }
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

    // FINE ou COARSE : sur Android 12+, l'utilisateur peut n'accorder que la
    // position approximative — la refuser relancerait la demande en boucle.
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Active/désactive l'alerte de proximité. Les geofences ne se déclenchent
     * en arrière-plan qu'avec ACCESS_BACKGROUND_LOCATION : on la demande à
     * l'activation (l'alerte fonctionne quand même app ouverte en attendant).
     */
    private fun toggleGeofences() {
        val enabling = !viewModel.geofencesActive.value
        if (enabling &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        viewModel.toggleGeofences()
    }

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
