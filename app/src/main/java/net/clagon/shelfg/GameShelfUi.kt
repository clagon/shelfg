package net.clagon.shelfg

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.Locale

// Restraint is intentional: game artwork is the primary accent, not the app chrome.
private val Background = Color(0xFF101319)
private val Surface = Color(0xFF1B2029)
private val Raised = Color(0xFF282E39)
private val Accent = Color(0xFFDFE6F2)
private val OnBase = Color(0xFFF1F2F5)
private val Muted = Color(0xFF9EA7B7)
private val Hairline = Color(0xFF343B48)

@Composable
fun GameShelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Background,
            background = Background, onBackground = OnBase,
            surface = Surface, onSurface = OnBase,
            surfaceContainer = Surface, surfaceContainerHigh = Raised,
            onSurfaceVariant = Muted, outline = Hairline,
        ),
        content = content
    )
}

@Composable
fun GameShelfApp(
    games: List<OwnedGame>,
    syncing: Boolean,
    message: String?,
    sampleMode: Boolean,
    steamConnected: Boolean,
    epicConnected: Boolean,
    epicName: String,
    steamId: String,
    onRefresh: () -> Unit,
    onSample: () -> Unit,
    onDismissMessage: () -> Unit,
    onSteamSave: (String, String) -> Unit,
    onSteamDisconnect: () -> Unit,
    onEpicLogin: () -> Unit,
    onEpicAuthorize: (String) -> Unit,
    onEpicDisconnect: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        containerColor = Background,
        snackbarHost = {
            if (!message.isNullOrBlank()) {
                Snackbar(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = if (keyboardVisible) 0.dp else 84.dp),
                    action = { TextButton(onClick = onDismissMessage) { Text("閉じる") } },
                    containerColor = Raised,
                    contentColor = OnBase
                ) { Text(message, maxLines = 3) }
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            if (tab == 0) {
                LibraryScreen(
                    games = games,
                    syncing = syncing,
                    isSample = sampleMode,
                    hasConnections = steamConnected || epicConnected,
                    onRefresh = onRefresh,
                    onSample = onSample,
                    onSettings = { tab = 1 },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SettingsScreen(
                    steamConnected = steamConnected,
                    steamId = steamId,
                    epicConnected = epicConnected,
                    epicName = epicName,
                    syncing = syncing,
                    onSteamSave = onSteamSave,
                    onSteamDisconnect = onSteamDisconnect,
                    onEpicLogin = onEpicLogin,
                    onEpicAuthorize = onEpicAuthorize,
                    onEpicDisconnect = onEpicDisconnect,
                    onOpenLink = onOpenLink,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (!keyboardVisible) {
                FloatingNavigation(
                    selectedTab = tab,
                    onTabSelected = { tab = it },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(252.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xF4151922))
            .border(1.dp, Hairline, RoundedCornerShape(32.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationIcon(
            selected = selectedTab == 0,
            icon = Icons.Outlined.GridView,
            label = "ライブラリ",
            onClick = { onTabSelected(0) },
            modifier = Modifier.weight(1f)
        )
        NavigationIcon(
            selected = selectedTab == 1,
            icon = Icons.Outlined.Settings,
            label = "設定",
            onClick = { onTabSelected(1) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NavigationIcon(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.fillMaxHeight().selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) OnBase else Muted.copy(alpha = 0.75f)
        )
    }
}

private enum class SortOrder(val label: String, val shortLabel: String) {
    TITLE_ASC("タイトル A → Z", "名前順"),
    TITLE_DESC("タイトル Z → A", "名前（逆順）"),
    PLAYTIME("プレイ時間の長い順", "プレイ時間"),
    STORE("ストア別", "ストア順")
}

@Composable
private fun LibraryScreen(
    games: List<OwnedGame>,
    syncing: Boolean,
    isSample: Boolean,
    hasConnections: Boolean,
    onRefresh: () -> Unit,
    onSample: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedStore by remember { mutableStateOf<Store?>(null) }
    var gridMode by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf(SortOrder.TITLE_ASC) }
    var showSort by remember { mutableStateOf(false) }
    val filtered = remember(games, query, selectedStore, sort) {
        val items = games.filter {
            (selectedStore == null || it.store == selectedStore) &&
                it.title.contains(query.trim(), ignoreCase = true)
        }
        when (sort) {
            SortOrder.TITLE_ASC -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortOrder.TITLE_DESC -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortOrder.PLAYTIME -> items.sortedWith(compareByDescending<OwnedGame> { it.playtimeMinutes ?: -1 }.thenBy { it.title })
            SortOrder.STORE -> items.sortedWith(compareBy<OwnedGame> { it.store.name }.thenBy { it.title })
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            // A quiet center title instead of a tall branded header.
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                Text(
                    "ライブラリ",
                    color = OnBase,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !syncing && hasConnections,
                    modifier = Modifier.align(Alignment.CenterEnd).size(44.dp)
                ) {
                    Icon(
                        Icons.Outlined.Sync,
                        contentDescription = "所有ゲームを同期",
                        tint = if (syncing || !hasConnections) Muted else OnBase,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            StoreSearchBar(
                query = query,
                onQueryChange = { query = it },
                selectedStore = selectedStore,
                onSelectStore = { selectedStore = it }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (query.isNotBlank() || selectedStore != null) "${filtered.size} / ${games.size} 件"
                    else "${games.size} 件",
                    fontSize = 12.sp,
                    color = Muted
                )
                if (isSample) {
                    Spacer(Modifier.width(9.dp))
                    Text("DEMO", fontSize = 10.sp, color = Muted)
                }
                Spacer(Modifier.weight(1f))
                Box {
                    TextButton(
                        onClick = { showSort = true },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(sort.shortLabel, fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Muted,
                            modifier = Modifier.size(17.dp))
                    }
                    DropdownMenu(
                        expanded = showSort,
                        onDismissRequest = { showSort = false },
                        containerColor = Surface
                    ) {
                        SortOrder.entries.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choice.label, color = OnBase, fontSize = 14.sp) },
                                onClick = { sort = choice; showSort = false },
                                trailingIcon = {
                                    if (choice == sort) Icon(Icons.Outlined.Check, null,
                                        tint = Accent, modifier = Modifier.size(17.dp))
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { gridMode = !gridMode }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (gridMode) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                        contentDescription = if (gridMode) "リスト表示へ" else "グリッド表示へ",
                        tint = OnBase,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
        if (syncing) LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Background
        )
        if (filtered.isEmpty()) {
            EmptyLibrary(
                hasGames = games.isNotEmpty(), hasConnections = hasConnections,
                onSettings = onSettings, onSample = onSample, isSample = isSample,
                modifier = Modifier.weight(1f).padding(bottom = 88.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = if (gridMode) GridCells.Fixed(2) else GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(if (gridMode) 21.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { "${it.store}:${it.id}" }) { game ->
                    if (gridMode) GridGameCard(game) else ListGameRow(game)
                }
            }
        }
    }
}

@Composable
private fun StoreSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedStore: Store?,
    onSelectStore: (Store?) -> Unit
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) focusRequester.requestFocus()
    }

    Row(
        Modifier.fillMaxWidth().height(58.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(Surface)
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(
            targetState = searchExpanded,
            label = "検索とフィルター切替",
            modifier = Modifier.fillMaxSize()
        ) { expanded ->
            if (expanded) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, contentDescription = null,
                        tint = Muted, modifier = Modifier.padding(start = 13.dp).size(20.dp))
                    Spacer(Modifier.width(11.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        cursorBrush = SolidColor(Accent),
                        textStyle = TextStyle(fontSize = 15.sp, color = OnBase),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text("ゲームを検索", color = Muted, fontSize = 14.sp)
                                innerTextField()
                            }
                        }
                    )
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            searchExpanded = false
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(Icons.Outlined.Close, "検索を閉じる", tint = Muted,
                            modifier = Modifier.size(21.dp))
                    }
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { searchExpanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = if (query.isEmpty()) "ゲームを検索" else "検索: $query",
                            modifier = Modifier.size(23.dp),
                            tint = if (query.isEmpty()) Muted else OnBase
                        )
                    }
                    Box(Modifier.width(1.dp).height(22.dp).background(Hairline))
                    Spacer(Modifier.width(5.dp))
                    StoreSegment("すべて", selectedStore == null, { onSelectStore(null) },
                        modifier = Modifier.weight(1f))
                    StoreSegment("Steam", selectedStore == Store.STEAM, { onSelectStore(Store.STEAM) },
                        modifier = Modifier.weight(1f))
                    StoreSegment("Epic", selectedStore == Store.EPIC, { onSelectStore(Store.EPIC) },
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StoreSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(25.dp))
            .background(if (selected) Raised else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) OnBase else Muted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun GameArtwork(game: OwnedGame, poster: Boolean, modifier: Modifier = Modifier) {
    // Steam offers vertical library art on most games. Use it for the reference-style poster
    // layout, but fall back to the existing cached banner if no vertical asset exists.
    val steamPosterUrl = if (poster && game.store == Store.STEAM && game.id.all { it.isDigit() }) {
        "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/${game.id}/library_600x900.jpg"
    } else null
    var posterFailed by remember(game.store, game.id, poster) { mutableStateOf(false) }
    val imageUrl = if (steamPosterUrl != null && !posterFailed) steamPosterUrl else game.coverUrl
    val isBannerFallback = poster && game.store == Store.STEAM && posterFailed
    Box(
        modifier.clip(RoundedCornerShape(11.dp)).background(Surface),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "${game.title} の画像",
                contentScale = if (isBannerFallback) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { if (steamPosterUrl != null && !posterFailed) posterFailed = true }
            )
        } else {
            Icon(Icons.Outlined.VideogameAsset, null, tint = Muted.copy(alpha = 0.55f),
                modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun GridGameCard(game: OwnedGame) {
    Column(Modifier.fillMaxWidth()) {
        GameArtwork(game, poster = true, modifier = Modifier.fillMaxWidth().aspectRatio(0.73f))
        Spacer(Modifier.height(9.dp))
        Text(
            game.title,
            maxLines = 2,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = OnBase,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            gameMetadata(game),
            color = Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ListGameRow(game: OwnedGame) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameArtwork(game, poster = false, modifier = Modifier.size(width = 96.dp, height = 58.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(game.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = OnBase, fontSize = 14.sp, lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium)
                Text(gameMetadata(game), color = Muted, fontSize = 11.sp, maxLines = 1)
            }
        }
        HorizontalDivider(color = Hairline.copy(alpha = 0.6f), thickness = 0.5.dp)
    }
}

private fun gameMetadata(game: OwnedGame): String {
    val store = if (game.store == Store.STEAM) "Steam" else "Epic Games"
    return game.playtimeMinutes?.let { minutes ->
        "$store  ·  ${if (minutes == 0) "未プレイ" else String.format(Locale.JAPAN, "%.1f時間", minutes / 60.0)}"
    } ?: store
}

@Composable
private fun EmptyLibrary(
    hasGames: Boolean,
    hasConnections: Boolean,
    onSettings: () -> Unit,
    onSample: () -> Unit,
    isSample: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (hasGames) "該当するゲームがありません" else "ライブラリは空です",
            color = OnBase, fontSize = 16.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                hasGames -> "検索条件を変更してください"
                hasConnections -> "右上の更新ボタンから同期してください"
                else -> "設定からSteamまたはEpicに接続してください"
            },
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        if (!hasConnections && !hasGames) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onSettings) { Text("アカウントを接続") }
            TextButton(onClick = onSample) {
                Text(if (isSample) "サンプルを閉じる" else "サンプルを見る", color = Muted)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    steamConnected: Boolean,
    steamId: String,
    epicConnected: Boolean,
    epicName: String,
    syncing: Boolean,
    onSteamSave: (String, String) -> Unit,
    onSteamDisconnect: () -> Unit,
    onEpicLogin: () -> Unit,
    onEpicAuthorize: (String) -> Unit,
    onEpicDisconnect: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredSteamId by remember(steamId) { mutableStateOf(steamId) }
    var enteredSteamKey by remember { mutableStateOf("") }
    var enteredEpicCode by remember { mutableStateOf("") }
    var editingSteam by remember { mutableStateOf(!steamConnected) }
    LaunchedEffect(steamConnected) { editingSteam = !steamConnected }

    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 108.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Text("設定", color = OnBase, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text("アカウント", color = Muted, fontSize = 12.sp,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        AccountCard {
            AccountHeading("Steam", steamConnected)
            Spacer(Modifier.height(12.dp))
            if (steamConnected && !editingSteam) {
                Text("SteamID  $steamId", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { editingSteam = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("変更", fontSize = 13.sp)
                    }
                    TextButton(onClick = onSteamDisconnect, contentPadding = PaddingValues(0.dp)) {
                        Text("連携を解除", color = Muted, fontSize = 13.sp)
                    }
                }
            } else {
                Text("SteamID64", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                ShelfTextField(enteredSteamId, { enteredSteamId = it }, "7656119...", isNumber = true)
                Spacer(Modifier.height(12.dp))
                Text("Web API Key", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                ShelfTextField(enteredSteamKey, { enteredSteamKey = it },
                    if (steamConnected) "変更しない場合は空欄" else "APIキー", secret = true)
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onOpenLink("https://steamcommunity.com/dev/apikey") },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("SteamでAPIキーを確認", fontSize = 13.sp)
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSteamSave(enteredSteamId.trim(), enteredSteamKey.trim())
                        enteredSteamKey = ""
                        if (steamConnected) editingSteam = false
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                ) { Text("保存") }
                if (steamConnected) {
                    TextButton(onClick = { editingSteam = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("キャンセル", color = Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AccountCard {
            AccountHeading("Epic Games", epicConnected)
            Spacer(Modifier.height(12.dp))
            if (epicConnected) {
                Text("接続先  ${epicName.ifEmpty { "Epicアカウント" }}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(9.dp))
                TextButton(onClick = onEpicDisconnect, contentPadding = PaddingValues(0.dp)) {
                    Text("連携を解除", color = Muted, fontSize = 13.sp)
                }
            } else {
                Text("公式サイトでログイン後、表示された認証コードを貼り付けます。",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onEpicLogin, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Epicのログインページを開く")
                }
                Spacer(Modifier.height(12.dp))
                ShelfTextField(enteredEpicCode, { enteredEpicCode = it },
                    "authorizationCode または JSON")
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    onEpicAuthorize(enteredEpicCode)
                    enteredEpicCode = ""
                }, enabled = enteredEpicCode.isNotBlank() && !syncing,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("接続する")
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("認証情報は端末内で暗号化して保存されます。Epic連携には非公開APIを使用します。",
            color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(10.dp))
        Text("Game Shelf  ·  0.4", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AccountCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Surface).padding(18.dp),
        content = content
    )
}

@Composable
private fun AccountHeading(name: String, connected: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = OnBase, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(if (connected) "接続済み" else "未接続", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ShelfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    secret: Boolean = false,
    isNumber: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isNumber) KeyboardType.Number else KeyboardType.Text),
        placeholder = { Text(hint, color = Muted, fontSize = 13.sp) },
        textStyle = TextStyle(color = OnBase, fontSize = 14.sp),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Surface, unfocusedContainerColor = Surface,
            focusedBorderColor = Accent, unfocusedBorderColor = Hairline
        )
    )
}
