package dev.gameshelf.mobile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.Locale

private val Background = Color(0xFF090D16)
private val Surface = Color(0xFF151B29)
private val Raised = Color(0xFF1E2638)
private val Accent = Color(0xFFA9B8FF)
private val OnBase = Color(0xFFF1F3FF)
private val Muted = Color(0xFF97A1B7)
private val EpicPurple = Color(0xFFC4A4FF)

@Composable
fun GameShelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color(0xFF10182C), background = Background,
            onBackground = OnBase, surface = Surface, onSurface = OnBase,
            surfaceContainer = Surface, surfaceContainerHigh = Raised,
            onSurfaceVariant = Muted, outline = Color(0xFF394258),
            secondary = EpicPurple, onSecondary = Background
        ),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(
                fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp
            ),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
                    label = { Text("ライブラリ") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Background, selectedTextColor = Accent,
                        indicatorColor = Accent, unselectedIconColor = Muted, unselectedTextColor = Muted
                    )
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("設定") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Background, selectedTextColor = Accent,
                        indicatorColor = Accent, unselectedIconColor = Muted, unselectedTextColor = Muted
                    )
                )
            }
        },
        snackbarHost = {
            if (!message.isNullOrBlank()) {
                Snackbar(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    action = { TextButton(onClick = onDismissMessage) { Text("閉じる") } },
                    containerColor = Raised, contentColor = OnBase
                ) { Text(message, maxLines = 3) }
            }
        }
    ) { insets ->
        if (tab == 0) {
            LibraryScreen(
                games = games, syncing = syncing, isSample = sampleMode,
                hasConnections = steamConnected || epicConnected,
                onRefresh = onRefresh, onSample = onSample,
                onSettings = { tab = 1 },
                modifier = Modifier.padding(insets)
            )
        } else {
            SettingsScreen(
                steamConnected = steamConnected, steamId = steamId,
                epicConnected = epicConnected, epicName = epicName,
                syncing = syncing, onSteamSave = onSteamSave, onSteamDisconnect = onSteamDisconnect,
                onEpicLogin = onEpicLogin, onEpicAuthorize = onEpicAuthorize,
                onEpicDisconnect = onEpicDisconnect, onOpenLink = onOpenLink,
                modifier = Modifier.padding(insets)
            )
        }
    }
}

private enum class SortOrder(val label: String) {
    TITLE_ASC("タイトル A → Z"), TITLE_DESC("タイトル Z → A"),
    PLAYTIME("プレイ時間の長い順"), STORE("ストア別")
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
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(29.dp).clip(RoundedCornerShape(9.dp)).background(Accent),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.SportsEsports, null, tint = Background,
                            modifier = Modifier.size(20.dp))
                    }
                    Text("GAME SHELF", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Accent)
                }
                IconButton(onClick = onRefresh, enabled = !syncing && hasConnections) {
                    Icon(Icons.Outlined.Sync, contentDescription = "所有ゲームを同期", tint = if (syncing) Muted else OnBase)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("マイライブラリ", style = MaterialTheme.typography.headlineLarge, color = OnBase)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${games.size} タイトル", color = Muted, style = MaterialTheme.typography.bodyMedium)
                if (isSample) {
                    Badge(containerColor = Color(0xFF504471), contentColor = Color.White) { Text("DEMO") }
                }
            }
            Spacer(Modifier.height(21.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Muted) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, "検索をクリア", tint = Muted)
                    }
                },
                placeholder = { Text("ゲームタイトルを検索", color = Muted) },
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                    focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF283043),
                    focusedTextColor = OnBase, unfocusedTextColor = OnBase
                )
            )
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                StoreChip("すべて", selectedStore == null) { selectedStore = null }
                StoreChip("Steam", selectedStore == Store.STEAM) { selectedStore = Store.STEAM }
                StoreChip("Epic Games", selectedStore == Store.EPIC) { selectedStore = Store.EPIC }
            }
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Box {
                    TextButton(onClick = { showSort = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(19.dp), tint = Muted)
                        Spacer(Modifier.width(7.dp))
                        Text(sort.label, color = Muted, style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Muted, modifier = Modifier.size(17.dp))
                    }
                    DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false },
                        containerColor = Raised) {
                        SortOrder.entries.forEach { choice ->
                            DropdownMenuItem(text = { Text(choice.label) }, onClick = { sort = choice; showSort = false })
                        }
                    }
                }
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Surface).padding(3.dp)) {
                    ModeButton(Icons.Outlined.GridView, "グリッド表示", gridMode) { gridMode = true }
                    ModeButton(Icons.Outlined.ViewList, "リスト表示", !gridMode) { gridMode = false }
                }
            }
        }
        if (syncing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Surface)
        if (filtered.isEmpty()) {
            EmptyLibrary(
                hasGames = games.isNotEmpty(), hasConnections = hasConnections,
                onSettings = onSettings, onSample = onSample, isSample = isSample,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = if (gridMode) GridCells.Adaptive(152.dp) else GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 3.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { "${it.store}:${it.id}" }) { game ->
                    if (gridMode) GridGameCard(game) else ListGameCard(game)
                }
            }
        }
    }
}

@Composable
private fun StoreChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label, fontWeight = FontWeight.SemiBold) },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) Accent else Color(0xFF343C4C)),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Surface, labelColor = Muted,
            selectedContainerColor = Color(0xFF303C59), selectedLabelColor = OnBase
        )
    )
}

@Composable
private fun ModeButton(icon: androidx.compose.ui.graphics.vector.ImageVector,
                       desc: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
        .background(if (selected) Raised else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(20.dp),
            tint = if (selected) Accent else Muted)
    }
}

@Composable
private fun GridGameCard(game: OwnedGame) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(Surface).border(1.dp, Color(0xFF252D3E), RoundedCornerShape(18.dp))) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.97f)
            .background(Brush.linearGradient(listOf(Color(0xFF364469), Color(0xFF1C263A), Color(0xFF1A1C36))))) {
            if (!game.coverUrl.isNullOrBlank()) {
                AsyncImage(model = game.coverUrl, contentDescription = "${game.title} の画像",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Outlined.SportsEsports, null, tint = Color.White.copy(alpha = .45f),
                    modifier = Modifier.size(52.dp).align(Alignment.Center))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(9.dp)) { PlatformPill(game.store, compact = true) }
        }
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(game.title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                color = OnBase, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
            Text(playtimeLabel(game), color = Muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ListGameCard(game: OwnedGame) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(Surface).border(1.dp, Color(0xFF252D3E), RoundedCornerShape(18.dp))
        .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(91.dp, 79.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF39456C), Raised))),
            contentAlignment = Alignment.Center) {
            if (game.coverUrl != null) {
                AsyncImage(model = game.coverUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else Icon(Icons.Outlined.SportsEsports, null, tint = Muted, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(game.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformPill(game.store, compact = false)
                if (game.playtimeMinutes != null) Text(playtimeLabel(game), color = Muted, fontSize = 11.sp)
            }
        }
    }
}

private fun playtimeLabel(game: OwnedGame): String = game.playtimeMinutes?.let {
    if (it == 0) "未プレイ" else String.format(Locale.JAPAN, "%.1f 時間プレイ", it / 60.0)
} ?: "ライブラリに所有"

@Composable
private fun PlatformPill(store: Store, compact: Boolean) {
    val color = if (store == Store.STEAM) Color(0xFFAAD2FA) else EpicPurple
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xE0101725))
        .padding(horizontal = if (compact) 8.dp else 7.dp, vertical = 4.dp)) {
        Text(if (store == Store.STEAM) "STEAM" else "EPIC", color = color,
            fontSize = if (compact) 10.sp else 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp)
    }
}

@Composable
private fun EmptyLibrary(
    hasGames: Boolean, hasConnections: Boolean, onSettings: () -> Unit,
    onSample: () -> Unit, isSample: Boolean, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(95.dp).clip(RoundedCornerShape(28.dp)).background(Surface),
            contentAlignment = Alignment.Center) {
            Icon(if (hasGames) Icons.Outlined.SearchOff else Icons.Outlined.SportsEsports,
                null, modifier = Modifier.size(43.dp), tint = Accent)
        }
        Spacer(Modifier.height(22.dp))
        Text(if (hasGames) "一致するゲームはありません" else "ゲームをまとめて、ここに。",
            color = OnBase, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(when {
            hasGames -> "検索キーワードやストアの絞り込みを変更してください"
            hasConnections -> "右上の同期アイコンからゲーム一覧を取得してください"
            else -> "SteamとEpicの所有ゲームを一か所で検索できます"
        }, color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (!hasConnections && !hasGames) {
            Spacer(Modifier.height(22.dp))
            Button(onClick = onSettings, shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(
                containerColor = Accent, contentColor = Background)) {
                Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("アカウントを接続")
            }
            TextButton(onClick = onSample) { Text(if (isSample) "サンプルを閉じる" else "サンプルでUIを確認", color = Muted) }
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
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 22.dp, vertical = 23.dp)) {
        Text("設定", style = MaterialTheme.typography.headlineLarge, color = OnBase)
        Spacer(Modifier.height(7.dp))
        Text("接続するプラットフォームと認証情報を管理します", color = Muted,
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(25.dp))
        AccountPanel("Steam", "Steam Web API", steamConnected) {
            Text("SteamID64", color = Muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(7.dp))
            ShelfTextField(enteredSteamId, { enteredSteamId = it }, "7656119...", isNumber = true)
            Spacer(Modifier.height(15.dp))
            Text("Web API Key", color = Muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(7.dp))
            ShelfTextField(enteredSteamKey, { enteredSteamKey = it },
                if (steamConnected) "保存済み・変更する場合のみ入力" else "APIキー", secret = true)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onOpenLink("https://steamcommunity.com/dev/apikey") },
                contentPadding = PaddingValues(0.dp)) {
                Text("SteamでAPIキーを確認", color = Accent)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp), tint = Accent)
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = {
                onSteamSave(enteredSteamId.trim(), enteredSteamKey.trim())
                enteredSteamKey = ""
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("Steamの設定を保存")
            }
            if (steamConnected) {
                TextButton(onClick = onSteamDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("認証情報を削除", color = Color(0xFFFFA7B2))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        AccountPanel("Epic Games", "Epic非公開API / Legendary方式", epicConnected) {
            if (epicConnected) {
                Text("接続先: ${epicName.ifEmpty { "Epicアカウント" }}", color = Muted,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onEpicDisconnect, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)) { Text("Epicの認証情報を削除") }
            } else {
                Text("Epic公式サイトでログインし、表示されるJSONの authorizationCode を貼り付けます。パスワードは入力しません。",
                    color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
                Spacer(Modifier.height(13.dp))
                OutlinedButton(onClick = onEpicLogin, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Epic公式ログインを開く")
                }
                Spacer(Modifier.height(13.dp))
                ShelfTextField(enteredEpicCode, { enteredEpicCode = it },
                    "authorizationCode またはJSONを貼り付け", secret = false)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    onEpicAuthorize(enteredEpicCode)
                    enteredEpicCode = ""
                }, enabled = enteredEpicCode.isNotBlank() && !syncing,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Epicに接続")
                }
            }
        }
        Spacer(Modifier.height(23.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface)
            .padding(16.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = Accent)
            Column {
                Text("この端末だけで認証情報を管理", color = OnBase,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(5.dp))
                Text("トークンとAPIキーはAndroid Keystoreで暗号化。独自サーバー、解析SDK、広告SDKは使用しません。Epicの非公開APIは仕様変更で利用できなくなる場合があります。",
                    color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Game Shelf  •  v0.1", color = Muted, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountPanel(name: String, subtitle: String, connected: Boolean,
                         content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Surface).border(1.dp, Color(0xFF293247), RoundedCornerShape(20.dp))
        .padding(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(Raised),
                contentAlignment = Alignment.Center) {
                Text(if (name == "Steam") "S" else "E", fontWeight = FontWeight.Black,
                    color = if (name == "Steam") Color(0xFFAAD2FA) else EpicPurple,
                    fontSize = 20.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(name, color = OnBase, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Text(if (connected) "接続済み" else "未接続", fontSize = 11.sp,
                color = if (connected) Color(0xFF88DBBC) else Muted)
        }
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
private fun ShelfTextField(value: String, onValueChange: (String) -> Unit,
                           hint: String, secret: Boolean = false, isNumber: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isNumber) KeyboardType.Number else KeyboardType.Text),
        placeholder = { Text(hint, color = Muted, fontSize = 12.sp) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Raised, unfocusedContainerColor = Raised,
            focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF353F55)
        )
    )
}
