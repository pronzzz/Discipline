package app.discipline.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.discipline.data.food.Barcodes
import app.discipline.data.food.FoodRepository
import app.discipline.ui.LocalNavigator
import app.discipline.ui.Route
import app.discipline.ui.components.AccentTextButton
import app.discipline.ui.components.OneUiField
import app.discipline.ui.components.PillButton
import app.discipline.ui.components.TonalPillButton
import app.discipline.ui.theme.OneUi
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch

// ================================================================================ camera

private fun Context.hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Camera permission state plus a function that asks for it. */
@Composable
fun rememberCameraPermission(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCamera()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    return granted to { launcher.launch(Manifest.permission.CAMERA) }
}

/** A CameraX controller bound to this screen's lifecycle. */
@Composable
fun rememberCameraController(useCases: Int): LifecycleCameraController {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(useCases) } }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }
    return controller
}

@Composable
fun CameraPreview(controller: LifecycleCameraController, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = controller
            }
        },
        modifier = modifier,
    )
}

/** Shown in place of the preview when the camera can't be used. */
@Composable
fun CameraUnavailable(text: String, onAllow: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.Black).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.NoPhotography, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onAllow != null) {
            Spacer(Modifier.height(16.dp))
            PillButton("Allow camera", onClick = onAllow)
        }
    }
}

// ============================================================================== barcode

private sealed interface ScanState {
    data object Scanning : ScanState
    data class Looking(val code: String) : ScanState
    data class NotFound(val code: String) : ScanState
    data class Failed(val code: String, val message: String) : ScanState
}

@Composable
fun BarcodeScanScreen(route: Route.ScanBarcode) {
    val c = appContainer()
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = OneUi.colors
    val (granted, askCamera) = rememberCameraPermission()
    var state by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    var manual by rememberSaveable { mutableStateOf("") }
    var torch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (!granted) askCamera() }

    fun lookup(raw: String) {
        val code = raw.filter(Char::isDigit)
        if (code.length < 6 || state is ScanState.Looking) return
        state = ScanState.Looking(code)
        scope.launch {
            when (val r = c.foods.lookupBarcode(code)) {
                is FoodRepository.BarcodeResult.Found -> nav.replace(Route.FoodDetail(r.food.id, route.day, route.meal))
                is FoodRepository.BarcodeResult.Incomplete ->
                    nav.replace(Route.CreateFood(route.day, route.meal, barcode = r.code, name = r.name, brand = r.brand))
                FoodRepository.BarcodeResult.NotFound -> state = ScanState.NotFound(code)
                is FoodRepository.BarcodeResult.Failed -> state = ScanState.Failed(code, r.message)
            }
        }
    }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    DisposableEffect(Unit) { onDispose { scanner.close() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            val controller = rememberCameraController(CameraController.IMAGE_ANALYSIS)
            DisposableEffect(controller) {
                val executor = ContextCompat.getMainExecutor(context)
                // Require two identical reads in a row so a partly visible code isn't accepted.
                var last: String? = null
                controller.setImageAnalysisAnalyzer(
                    executor,
                    MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
                        val raw = result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue } ?: return@MlKitAnalyzer
                        if (state !is ScanState.Scanning) return@MlKitAnalyzer
                        val valid = Barcodes.checksumOk(raw) || raw.length == 8
                        if (valid && raw == last) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            lookup(raw)
                        }
                        last = raw
                    },
                )
                onDispose { controller.clearImageAnalysisAnalyzer() }
            }
            LaunchedEffect(torch) { runCatching { controller.enableTorch(torch) } }
            CameraPreview(controller, Modifier.fillMaxSize())
            // Viewfinder
            Box(
                Modifier.align(Alignment.Center).padding(horizontal = 40.dp).fillMaxWidth().aspectRatio(1.7f)
                    .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp)),
            )
        } else {
            CameraUnavailable("Discipline needs the camera to scan barcodes. You can also type the number below.", askCamera, Modifier.fillMaxSize())
        }

        // Top bar
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Text("Scan barcode", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (granted) {
                IconButton(onClick = { torch = !torch }) {
                    Icon(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, if (torch) "Turn off light" else "Turn on light", tint = Color.White)
                }
            }
        }

        // Bottom sheet: status + manual entry
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(colors.background)
                .navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                ScanState.Scanning -> Text(
                    "Point the camera at the barcode on the pack.",
                    style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary,
                )
                is ScanState.Looking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Looking up ${s.code} on Open Food Facts…", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                }
                is ScanState.NotFound -> {
                    Text("No product found for ${s.code}", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text("Add it yourself and it'll be found next time you scan it.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Create food", onClick = { nav.replace(Route.CreateFood(route.day, route.meal, barcode = s.code)) }, modifier = Modifier.weight(1f))
                        TonalPillButton("Scan again", onClick = { state = ScanState.Scanning }, modifier = Modifier.weight(1f))
                    }
                }
                is ScanState.Failed -> {
                    Text("Couldn't reach Open Food Facts", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Try again", onClick = { state = ScanState.Scanning; lookup(s.code) }, modifier = Modifier.weight(1f))
                        TonalPillButton("Create food", onClick = { nav.replace(Route.CreateFood(route.day, route.meal, barcode = s.code)) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (state is ScanState.Scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneUiField(
                        manual, { manual = it.filter(Char::isDigit).take(14) }, "Or type the barcode",
                        Modifier.weight(1f), keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.width(8.dp))
                    AccentTextButton("Look up", onClick = { lookup(manual) })
                }
            }
        }
    }
}
