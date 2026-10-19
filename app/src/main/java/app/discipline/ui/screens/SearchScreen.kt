package app.discipline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.data.db.FoodEntity
import app.discipline.data.db.FoodSource
import app.discipline.data.food.FoodRepository
import app.discipline.data.food.OnlineResult
import app.discipline.domain.Units
import app.discipline.ui.LocalNavigator
import app.discipline.ui.LocalSnackbar
import app.discipline.ui.Route
import app.discipline.ui.components.SectionLabel
import app.discipline.ui.components.Segmented
import app.discipline.ui.theme.OneUi
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

private enum class SearchTab(val label: String) { ALL("All"), RECENT("Recent"), FREQUENT("Frequent"), MINE("My foods") }

private sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Done(val foods: List<FoodEntity>) : OnlineState
    data class Error(val message: String) : OnlineState
}

@Composable
fun SearchScreen(route: Route.Search) {
    val c = appContainer()
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val colors = OneUi.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val settings = rememberSettings()
    val day = LocalDate.ofEpochDay(route.day)

    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(SearchTab.ALL) }
    var meal by rememberSaveable { mutableStateOf(route.meal) }
    var local by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var online by remember { mutableStateOf<OnlineState>(OnlineState.Idle) }
    var retryKey by remember { mutableStateOf(0) }
    val added = remember { mutableStateMapOf<String, Boolean>() }

    val recent by c.foods.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    val frequent by c.foods.frequent.collectAsStateWithLifecycle(initialValue = emptyList())
    val mine by c.foods.userFoods.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by c.foods.favorites.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(query, retryKey) {
        val q = query.trim()
        if (q.isEmpty()) { local = emptyList(); online = OnlineState.Idle; return@LaunchedEffect }
        delay(200)
        local = c.foods.searchLocal(q)
        if (q.length < 3) { online = OnlineState.Idle; return@LaunchedEffect }
        delay(350)
        online = OnlineState.Loading
        online = when (val r = c.foods.searchOnline(q)) {
            is OnlineResult.Ok -> OnlineState.Done(r.foods.filter { o -> local.none { it.barcode != null && it.barcode == o.barcode } })
            is OnlineResult.Failed -> OnlineState.Error(r.message)
        }
    }

    fun open(food: FoodEntity) {
        scope.launch {
            val saved = c.foods.persist(food)
            nav.push(Route.FoodDetail(saved.id, route.day, meal))
        }
    }

    fun quickLog(food: FoodEntity) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val key = food.remoteId ?: food.id.toString()
        added[key] = true
        c.scope.launch {
            val saved = c.foods.persist(food)
            val servings = c.foods.lastServings(saved.id) ?: 1.0
            c.diary.logFood(day, meal, saved, servings)
        }
        scope.launch { snackbar.showSnackbar("Added ${food.name} to ${settings?.mealName(meal) ?: "meal"}") }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        scope.launch {
            val code = try {
                GmsBarcodeScanning.getClient(context, options).startScan().await()?.rawValue
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) snackbar.showSnackbar("Scanner unavailable: ${e.message ?: "unknown error"}")
                null
            } ?: return@launch
            when (val r = c.foods.lookupBarcode(code)) {
                is FoodRepository.BarcodeResult.Found -> nav.push(Route.FoodDetail(r.food.id, route.day, meal))
                FoodRepository.BarcodeResult.NotFound -> {
                    val res = snackbar.showSnackbar("No match for $code", actionLabel = "Create food")
                    if (res == SnackbarResult.ActionPerformed) nav.push(Route.CreateFood(route.day, meal, barcode = code))
                }
                is FoodRepository.BarcodeResult.Failed -> snackbar.showSnackbar("Lookup failed: ${r.message}")
            }
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        // ------------------------------------------------------------ search bar
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.textPrimary) }
            Row(
                Modifier.weight(1f).clip(OneUi.PillShape).background(colors.container).heightIn(min = 50.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search foods", style = MaterialTheme.typography.bodyLarge, color = colors.textTertiary)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; if (it.isNotEmpty()) tab = SearchTab.ALL },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "Clear", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            IconButton(onClick = ::scan) { Icon(Icons.Rounded.QrCodeScanner, "Scan barcode", tint = colors.accent) }
        }

        // ------------------------------------------------------------ meal + tabs
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            MealPicker(meal, settings?.mealNames.orEmpty(), onPick = { meal = it })
            Spacer(Modifier.weight(1f))
            Text(friendlyDate(day), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        if (query.isEmpty()) {
            Segmented(
                SearchTab.entries, tab, { it.label }, { tab = it },
                Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp),
            )
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        ) {
            if (query.isNotBlank()) {
                foodGroup("On this device", local, added, ::open, ::quickLog, emptyText = "No saved foods match")
                when (val o = online) {
                    OnlineState.Idle -> if (query.trim().length < 3) item { HintLine("Type 3+ letters to search Open Food Facts") }
                    OnlineState.Loading -> item {
                        SectionLabel("Open Food Facts")
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                    is OnlineState.Done -> foodGroup("Open Food Facts", o.foods, added, ::open, ::quickLog, emptyText = "No online results")
                    is OnlineState.Error -> item {
                        SectionLabel("Open Food Facts")
                        ActionCard(Icons.Rounded.CloudOff, "Couldn't reach Open Food Facts", "Tap to retry  ·  ${o.message}") { retryKey++ }
                    }
                }
            } else {
                when (tab) {
                    SearchTab.ALL -> {
                        item {
                            Spacer(Modifier.height(6.dp))
                            ActionCard(Icons.Rounded.QrCodeScanner, "Scan a barcode", "Look up packaged food instantly", onClick = ::scan)
                            ActionCard(Icons.Rounded.Bolt, "Quick add calories", "Log calories without a food") {
                                nav.push(Route.QuickAdd(route.day, meal))
                            }
                            ActionCard(Icons.Rounded.EditNote, "Create a food", "Add your own food with nutrition facts") {
                                nav.push(Route.CreateFood(route.day, meal))
                            }
                        }
                        if (favorites.isNotEmpty()) foodGroup("Favourites", favorites, added, ::open, ::quickLog)
                        foodGroup("Recent", recent.take(15), added, ::open, ::quickLog, emptyText = "Foods you log will show up here")
                    }
                    SearchTab.RECENT -> foodGroup("Recent", recent, added, ::open, ::quickLog, emptyText = "Nothing logged yet")
                    SearchTab.FREQUENT -> foodGroup("Most logged", frequent, added, ::open, ::quickLog, emptyText = "Nothing logged yet")
                    SearchTab.MINE -> {
                        item {
                            Spacer(Modifier.height(6.dp))
                            ActionCard(Icons.Rounded.EditNote, "Create a food", "Add your own food with nutrition facts") {
                                nav.push(Route.CreateFood(route.day, meal))
                            }
                        }
                        foodGroup("My foods", mine, added, ::open, ::quickLog, emptyText = "You haven't created any foods")
                    }
                }
            }
        }
    }
}

private fun LazyListScope.foodGroup(
    title: String,
    foods: List<FoodEntity>,
    added: Map<String, Boolean>,
    onOpen: (FoodEntity) -> Unit,
    onQuickLog: (FoodEntity) -> Unit,
    emptyText: String? = null,
) {
    if (foods.isEmpty()) {
        if (emptyText != null) item { SectionLabel(title); HintLine(emptyText) }
        return
    }
    item { SectionLabel(title) }
    items(foods.size, key = { "$title-${foods[it].remoteId ?: foods[it].id}-$it" }) { i ->
        val f = foods[i]
        val first = i == 0
        val last = i == foods.lastIndex
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = if (first) 26.dp else 0.dp, topEnd = if (first) 26.dp else 0.dp,
            bottomStart = if (last) 26.dp else 0.dp, bottomEnd = if (last) 26.dp else 0.dp,
        )
        FoodRow(
            f,
            added = added[f.remoteId ?: f.id.toString()] == true,
            modifier = Modifier.padding(horizontal = OneUi.ScreenPadding).clip(shape),
            showDivider = !last,
            onClick = { onOpen(f) },
            onAdd = { onQuickLog(f) },
        )
    }
}

@Composable
private fun FoodRow(f: FoodEntity, added: Boolean, modifier: Modifier, showDivider: Boolean, onClick: () -> Unit, onAdd: () -> Unit) {
    val colors = OneUi.colors
    Column(modifier.background(colors.container)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (f.favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Star, null, tint = colors.protein, modifier = Modifier.size(14.dp))
                    }
                }
                val detail = listOfNotNull(f.brand, f.servingLabel, "${Units.kcal(f.nutrients.calories)} kcal").joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (f.source == FoodSource.OPEN_FOOD_FACTS || f.source == FoodSource.USER) {
                    Text(
                        if (f.source == FoodSource.USER) "My food" else "Open Food Facts",
                        style = MaterialTheme.typography.labelSmall, color = colors.textTertiary,
                    )
                }
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (added) colors.success else colors.accentContainer).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (added) Icons.Rounded.Check else Icons.Rounded.Add, "Quick add", tint = if (added) androidx.compose.ui.graphics.Color.White else colors.accent, modifier = Modifier.size(20.dp))
            }
        }
        if (showDivider) app.discipline.ui.components.GroupDivider()
    }
}

@Composable
private fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val colors = OneUi.colors
    Row(
        Modifier
            .padding(horizontal = OneUi.ScreenPadding, vertical = 4.dp)
            .fillMaxWidth()
            .clip(OneUi.ContainerShape)
            .background(colors.container)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.accentContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HintLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = OneUi.colors.textTertiary, modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp))
}

@Composable
fun MealPicker(meal: Int, names: List<String>, onPick: (Int) -> Unit) {
    val colors = OneUi.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(OneUi.PillShape).background(colors.accentContainer).clickable { open = true }.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(names.getOrElse(meal) { "Meal" }, style = MaterialTheme.typography.labelLarge, color = colors.accent)
            Icon(Icons.Rounded.ExpandMore, null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = OneUi.InnerShape, containerColor = colors.container) {
            names.forEachIndexed { i, n ->
                DropdownMenuItem(text = { Text(n) }, onClick = { open = false; onPick(i) })
            }
        }
    }
}
