package app.discipline.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.data.db.FoodEntity
import app.discipline.data.food.OnlineResult
import app.discipline.domain.Units
import app.discipline.ui.LocalNavigator
import app.discipline.ui.LocalSnackbar
import app.discipline.ui.Route
import app.discipline.ui.components.AccentTextButton
import app.discipline.ui.components.EmptyHint
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.OneUiField
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.components.TonalPillButton
import app.discipline.ui.theme.OneUi
import app.discipline.vision.FoodGuess
import app.discipline.vision.FoodHints
import app.discipline.vision.FoodModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max

/** Snap or pick a photo, let the on-device model guess the dish, refine with text, log it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodPhotoScreen(route: Route.FoodPhoto) {
    val c = appContainer()
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = OneUi.colors
    val settings = rememberSettings()
    val day = LocalDate.ofEpochDay(route.day)
    val modelState by c.foodModel.state.collectAsStateWithLifecycle()

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var guesses by remember { mutableStateOf<List<FoodGuess>>(emptyList()) }
    var analysing by remember { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var hint by rememberSaveable { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val added = remember { mutableStateMapOf<String, Boolean>() }

    val hints = remember(hint) { FoodHints.parse(hint) }
    val ranked = remember(guesses, hints) { hints.rerank(guesses) }
    val chosen = picked?.let { p -> ranked.firstOrNull { it.label == p } } ?: ranked.firstOrNull()
    // What to search for: the typed description when there is one, else the chosen guess.
    val query = if (picked != null && hints.words.isEmpty()) picked!! else hints.query(chosen)

    fun analyse(bmp: Bitmap) {
        photo = bmp; guesses = emptyList(); picked = null; matches = emptyList(); added.clear()
        analysing = true
        scope.launch {
            guesses = runCatching { c.foodModel.classify(bmp) }.getOrElse {
                snackbar.showSnackbar("Couldn't analyse the photo: ${it.message}"); emptyList()
            }
            analysing = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { decode(context, uri) }.onSuccess(::analyse).onFailure { snackbar.showSnackbar("Couldn't open that photo") }
        }
    }

    LaunchedEffect(query, photo) {
        if (photo == null || query.isBlank()) { matches = emptyList(); return@LaunchedEffect }
        delay(400)
        searching = true
        // Search the full description and its main noun, then rank: dishes over sauces and
        // seasonings, foods on this device first when they fit equally well.
        val found = mutableListOf<Pair<FoodEntity, Double>>()
        for ((i, q) in FoodHints.searches(query).withIndex()) {
            val local = c.foods.searchLocal(q)
            val online = (c.foods.searchOnline(q) as? OnlineResult.Ok)?.foods.orEmpty()
            (local.map { it to 0.15 } + online.map { it to 0.0 }).forEach { (f, bonus) ->
                found += f to FoodHints.score(query, f.name) + bonus - i * 0.05
            }
        }
        matches = found.sortedByDescending { it.second }
            .distinctBy { (f, _) -> f.barcode ?: f.remoteId ?: "${f.source}-${f.id}-${f.name.lowercase()}" }
            .map { it.first }
            .take(12)
        searching = false
    }

    fun quickLog(food: FoodEntity) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val servings = hints.servingsFor(food.servingGrams)
        added[food.remoteId ?: food.id.toString()] = true
        c.scope.launch {
            val saved = c.foods.persist(food)
            c.diary.logFood(day, route.meal, saved, servings)
        }
        scope.launch {
            snackbar.showSnackbar("Added ${servings.fmt()} × ${food.name} to ${settings?.mealName(route.meal) ?: "meal"}")
        }
    }

    OneUiScreen(
        title = "Food photo",
        subtitle = "${settings?.mealName(route.meal) ?: ""} · ${friendlyDate(day)}",
        onBack = { nav.pop() },
        headerFraction = 0.20f,
    ) {
        // ------------------------------------------------------------------ model
        if (modelState != FoodModel.State.Ready) {
            item { ModelCard(modelState, onDownload = { c.scope.launch { c.foodModel.download() } }) }
            return@OneUiScreen
        }

        // ------------------------------------------------------------------ photo
        item {
            val bmp = photo
            Box(
                Modifier.padding(horizontal = OneUi.ScreenPadding).padding(top = 12.dp).fillMaxWidth().aspectRatio(1f)
                    .clip(OneUi.ContainerShape).background(Color.Black),
            ) {
                if (bmp == null) {
                    PhotoCapture(onPhoto = ::analyse, onError = { scope.launch { snackbar.showSnackbar(it) } }, onGallery = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                } else {
                    Image(bmp.asImageBitmap(), "Your photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundIcon(Icons.Rounded.PhotoLibrary, "Choose another photo") {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        RoundIcon(Icons.Rounded.Replay, "Retake") { photo = null; guesses = emptyList(); matches = emptyList() }
                    }
                }
            }
        }
        if (photo == null) {
            item {
                Text(
                    "Recognition runs entirely on this phone. Your photo isn't saved or uploaded.",
                    style = MaterialTheme.typography.bodySmall, color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
            return@OneUiScreen
        }

        // ------------------------------------------------------------------ guesses + hint
        item {
            Group(label = "What's in the photo", contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        analysing -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Analysing…", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                        }
                        ranked.isEmpty() -> Text(
                            "No confident guess. Describe the food below and Discipline will search for it.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                        )
                        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ranked.forEach { g ->
                                val sel = g == chosen && (picked != null || hints.words.isEmpty())
                                Text(
                                    "${g.label}  ${(g.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sel) Color.White else colors.textPrimary,
                                    modifier = Modifier.clip(OneUi.PillShape)
                                        .background(if (sel) colors.accent else colors.containerHigh)
                                        .clickable { picked = g.label; if (hints.words.isNotEmpty()) hint = g.label }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                    OneUiField(
                        hint, { hint = it.take(120) }, "Describe it (optional)",
                        placeholder = "e.g. chicken curry with rice, about 350 g",
                        singleLine = false,
                    )
                    val qty = listOfNotNull(
                        hints.grams?.let { "${it.toInt()} g" },
                        hints.servings?.let { "${it.fmt()} servings" },
                        hints.sizeFactor.takeIf { it != 1.0 }?.let { if (it < 1) "small" else "large" },
                    )
                    Text(
                        if (qty.isEmpty()) "Add what it is, how much (g, servings) or its size to improve matches."
                        else "Amount: ${qty.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }

        // ------------------------------------------------------------------ matches
        item {
            Group(label = if (query.isBlank()) "Matches" else "Matches for “$query”") {
                when {
                    searching && matches.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    }
                    matches.isEmpty() && !analysing -> EmptyHint("No foods found. Try describing it differently.")
                }
                matches.forEachIndexed { i, f ->
                    if (i > 0) GroupDivider()
                    val servings = hints.servingsFor(f.servingGrams)
                    val isAdded = added[f.remoteId ?: f.id.toString()] == true
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch { val saved = c.foods.persist(f); nav.push(Route.FoodDetail(saved.id, route.day, route.meal)) }
                        }.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(f.brand, "${servings.fmt()} × ${f.servingLabel}", "${Units.kcal(f.nutrients.calories * servings)} kcal").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (isAdded) colors.success else colors.accentContainer).clickable { quickLog(f) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (isAdded) Icons.Rounded.Check else Icons.Rounded.Add, "Quick add", tint = if (isAdded) Color.White else colors.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 6.dp)) {
                    AccentTextButton("Quick add calories instead", onClick = { nav.replace(Route.QuickAdd(route.day, route.meal)) })
                }
            }
        }
        item {
            Text(
                "Guesses come from Google's AIY food classifier and can be wrong. Check the food and amount before logging.",
                style = MaterialTheme.typography.bodySmall, color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun ModelCard(state: FoodModel.State, onDownload: () -> Unit) {
    val colors = OneUi.colors
    Group(label = "One-time setup", contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Download the food recognition model", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                "Discipline recognises about 2,000 dishes with a 21 MB model that runs on this phone, so photos are never uploaded. " +
                    "It's Google's AIY food classifier (Apache 2.0), downloaded once from Discipline's GitHub releases.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
            )
            when (state) {
                is FoodModel.State.Downloading -> {
                    LinearProgressIndicator(progress = { state.progress }, color = colors.accent, trackColor = colors.containerHigh, modifier = Modifier.fillMaxWidth())
                    Text("${(state.progress * 21).toInt()} of 21 MB", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                is FoodModel.State.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                    PillButton("Try again", onClick = onDownload, leadingIcon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth())
                }
                else -> PillButton("Download (21 MB)", onClick = onDownload, leadingIcon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun PhotoCapture(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit, onGallery: () -> Unit) {
    val context = LocalContext.current
    val (granted, askCamera) = rememberCameraPermission()
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!granted) askCamera() }

    Box(Modifier.fillMaxSize()) {
        if (granted) {
            val controller = rememberCameraController(CameraController.IMAGE_CAPTURE)
            CameraPreview(controller, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(68.dp).clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape).padding(6.dp).clip(CircleShape)
                    .background(if (busy) Color.White.copy(alpha = 0.5f) else Color.White)
                    .clickable(enabled = !busy) {
                        busy = true
                        controller.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bmp = runCatching { image.toUprightBitmap() }.getOrNull()
                                    image.close()
                                    busy = false
                                    if (bmp != null) onPhoto(bmp) else onError("Couldn't read the photo")
                                }

                                override fun onError(e: ImageCaptureException) {
                                    busy = false
                                    onError("Camera error: ${e.message}")
                                }
                            },
                        )
                    },
            )
        } else {
            CameraUnavailable("Allow the camera to take a photo, or choose one from your gallery.", askCamera, Modifier.fillMaxSize())
        }
        Box(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            RoundIcon(Icons.Rounded.PhotoLibrary, "Choose from gallery", onGallery)
        }
    }
}

private const val MAX_SIDE = 720

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val src = toBitmap()
    val scale = MAX_SIDE.toFloat() / max(src.width, src.height)
    val m = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        postRotate(imageInfo.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun decode(context: android.content.Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val s = MAX_SIDE.toFloat() / max(info.size.width, info.size.height)
        if (s < 1f) decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}
