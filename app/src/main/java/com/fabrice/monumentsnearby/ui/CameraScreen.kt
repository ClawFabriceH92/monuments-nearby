package com.fabrice.monumentsnearby.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fabrice.monumentsnearby.data.Monument
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Écran caméra deux modes :
 * - AR : boussole (capteurs) + étiquette du monument visé (nom + distance)
 * - QR : scanner de QR codes — si le QR contient un QID Wikidata → fiche
 */
@Composable
fun CameraScreen(
    monuments: List<Monument>,
    lat: Double,
    lon: Double,
    onClose: () -> Unit,
    onQrFound: (String) -> Unit
) {
    val context = LocalContext.current
    var qrMode by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    // Use case de capture photo, partagé avec CameraPreview (bindé avec Preview)
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Azimut de la boussole
    var azimuth by remember { mutableStateOf(0f) }

    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensorListener = remember {
        object : SensorEventListener {
            val rotation = FloatArray(9)
            val orientation = FloatArray(3)

            // getRotationMatrix() exige gravité ET champ magnétique simultanés :
            // on mémorise la dernière valeur de chaque capteur avant de combiner.
            val gravity = FloatArray(3)
            val geomagnetic = FloatArray(3)
            var hasGravity = false
            var hasGeomagnetic = false

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotation, event.values)
                        SensorManager.getOrientation(rotation, orientation)
                        azimuth = (Math.toDegrees(orientation[0].toDouble()) + 360).toFloat() % 360
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        event.values.copyInto(gravity)
                        hasGravity = true
                        updateFromGravityAndMagnetic()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        event.values.copyInto(geomagnetic)
                        hasGeomagnetic = true
                        updateFromGravityAndMagnetic()
                    }
                }
            }

            fun updateFromGravityAndMagnetic() {
                if (!hasGravity || !hasGeomagnetic) return
                if (SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) {
                    SensorManager.getOrientation(rotation, orientation)
                    azimuth = (Math.toDegrees(orientation[0].toDouble()) + 360).toFloat() % 360
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    DisposableEffect(Unit) {
        // Capteur de rotation fusionné (plus précis) quand il existe,
        // sinon combinaison accéléromètre + magnétomètre.
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sensorManager.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(
                sensorListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI
            )
            sensorManager.registerListener(
                sensorListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_UI
            )
        }
        onDispose { sensorManager.unregisterListener(sensorListener) }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("La caméra est nécessaire pour le mode AR et le scanner QR.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Autoriser la caméra")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) { Text("← Fermer") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { qrMode = !qrMode }) {
                    Text(if (qrMode) "📷 Mode AR" else "🔳 Mode QR")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            CameraPreview(
                qrMode = qrMode,
                onQrFound = onQrFound,
                context = context,
                imageCapture = imageCapture
            )

            // Prise de photo → identification de l'œuvre/du monument via
            // Google Lens (ou toute app d'images) — partage de la capture.
            Button(
                onClick = {
                    capturing = true
                    captureError = null
                    capturePhotoAndIdentify(context, imageCapture) { error ->
                        capturing = false
                        captureError = error
                    }
                },
                enabled = !capturing,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) { Text(if (capturing) "…" else "📸 Identifier") }
            captureError?.let { msg ->
                Text(
                    msg,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (qrMode) {
                // Viseur QR
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(48.dp)
                ) {
                    Text(
                        "Place le QR code dans le cadre",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                // Overlay AR
                val target = remember(azimuth) {
                    findTarget(monuments, lat, lon, azimuth)
                }
                val bearingText = remember(azimuth) { "Cap : ${azimuth.roundToInt()}°" }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        bearingText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(Color(0x88000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (target != null) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 12.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    target.first.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "À ${formatDistance(target.second)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Text(
                            "Tourne-toi vers un monument…",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .background(Color(0x88000000), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    qrMode: Boolean,
    onQrFound: (String) -> Unit,
    context: Context,
    imageCapture: ImageCapture
) {
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    // (Re)bind de la caméra à chaque bascule AR ↔ QR : la factory d'AndroidView
    // ne s'exécute qu'une fois, le bind doit donc vivre dans un effet à part.
    DisposableEffect(qrMode) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                if (qrMode) {
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage, imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val raw = barcode.rawValue ?: continue
                                        val qid = Regex("Q\\d+").find(raw)?.value
                                        if (qid != null) {
                                            onQrFound(qid)
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                    provider.bindToLifecycle(
                        lifecycleOwner, selector, preview, analysis, imageCapture
                    )
                } else {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                }
            } catch (e: Exception) {
                Log.e("CameraScreen", "Erreur caméra", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { }
    }

    // À la fermeture de l'écran : libérer la caméra (sinon elle reste allumée,
    // le bind est attaché au lifecycle de l'activité) et les ressources.
    DisposableEffect(Unit) {
        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) {
            }
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Capture une photo puis l'envoie à Google Lens pour identifier l'œuvre ou le
 * monument. Il n'existe pas d'API publique de recherche d'image inversée : on
 * partage la photo (ACTION_SEND) à l'app Google — qui l'ouvre dans Lens — avec
 * repli sur le sélecteur d'apps si elle est absente. [onDone] reçoit un message
 * d'erreur, ou null si le partage est parti.
 */
private fun capturePhotoAndIdentify(
    context: Context,
    imageCapture: ImageCapture,
    onDone: (String?) -> Unit
) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val photo = File(dir, "identification.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(photo).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = try {
                    FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", photo
                    )
                } catch (e: Exception) {
                    onDone("Photo enregistrée mais partage impossible.")
                    return
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // 1 tap : l'app Google ouvre directement Lens sur l'image ;
                // sinon, sélecteur générique (déclarée dans <queries> du manifest)
                val lens = Intent(send).setPackage(GOOGLE_APP_PACKAGE)
                val intent = if (lens.resolveActivity(context.packageManager) != null) {
                    lens
                } else {
                    Intent.createChooser(send, "Identifier l'image avec…")
                }
                try {
                    context.startActivity(intent)
                    onDone(null)
                } catch (e: Exception) {
                    onDone("Aucune application ne peut analyser la photo.")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraScreen", "Capture échouée", exception)
                onDone("La capture a échoué, réessaie.")
            }
        }
    )
}

/** App Google (hôte de Google Lens) — cible du partage « Identifier ». */
private const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"

/** Monument visé : celui dont l'angle de visée est le plus proche du cap. */
private fun findTarget(
    monuments: List<Monument>,
    lat: Double,
    lon: Double,
    azimuth: Float
): Pair<Monument, Double>? {
    var best: Pair<Monument, Double>? = null
    var bestDiff = 15.0
    for (m in monuments) {
        val bearing = bearingTo(lat, lon, m.lat, m.lon)
        var diff = abs(bearing - azimuth)
        if (diff > 180) diff = 360 - diff
        if (diff < bestDiff) {
            bestDiff = diff
            best = m to distanceM(lat, lon, m.lat, m.lon)
        }
    }
    return best
}

private fun bearingTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = Math.toRadians(lon2 - lon1)
    val la1 = Math.toRadians(lat1)
    val la2 = Math.toRadians(lat2)
    val y = sin(dLon) * cos(la2)
    val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * Math.asin(Math.sqrt(a))
}
