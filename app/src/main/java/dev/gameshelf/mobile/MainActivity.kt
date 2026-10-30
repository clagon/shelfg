package dev.gameshelf.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = GameRepository(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            var games by remember { mutableStateOf(repository.readCache()) }
            var syncing by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf<String?>(null) }
            var steamConnected by remember { mutableStateOf(repository.steamId().isNotEmpty() && repository.hasSteamKey()) }
            var epicConnected by remember { mutableStateOf(repository.isEpicConnected()) }
            var epicName by remember { mutableStateOf(repository.epicDisplayName()) }
            var previewMode by remember { mutableStateOf(false) }

            fun refresh() {
                if (syncing) return
                scope.launch {
                    syncing = true
                    message = null
                    try {
                        val result = repository.sync()
                        games = result.games
                        previewMode = false
                        message = if (result.errors.isEmpty()) "同期が完了しました" else result.errors.joinToString(" / ")
                    } catch (_: Exception) {
                        message = "同期に失敗しました。接続設定を確認してください"
                    } finally {
                        syncing = false
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (steamConnected || epicConnected) refresh()
            }

            GameShelfTheme {
                GameShelfApp(
                    games = if (previewMode) sampleGames else games,
                    syncing = syncing,
                    message = message,
                    sampleMode = previewMode,
                    steamConnected = steamConnected,
                    epicConnected = epicConnected,
                    epicName = epicName,
                    steamId = repository.steamId(),
                    onRefresh = ::refresh,
                    onSample = { previewMode = !previewMode },
                    onDismissMessage = { message = null },
                    onSteamSave = { id, key ->
                        try {
                            repository.saveSteam(id, key)
                            steamConnected = true
                            message = "Steamの設定を保存しました"
                            refresh()
                        } catch (e: IllegalArgumentException) {
                            message = e.message ?: "入力内容を確認してください"
                        }
                    },
                    onSteamDisconnect = {
                        repository.disconnectSteam()
                        steamConnected = false
                        games = repository.readCache()
                        message = "Steamの認証情報を削除しました"
                    },
                    onEpicLogin = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GameRepository.epicLoginUrl)))
                    },
                    onEpicAuthorize = { code ->
                        scope.launch {
                            syncing = true
                            message = "Epicに接続しています…"
                            try {
                                val name = repository.authorizeEpic(code)
                                epicConnected = true
                                epicName = name
                                message = "Epicに接続しました"
                            } catch (e: Exception) {
                                message = "Epic認証に失敗しました。コードが有効か確認してください"
                            } finally {
                                syncing = false
                            }
                            if (epicConnected) refresh()
                        }
                    },
                    onEpicDisconnect = {
                        repository.disconnectEpic()
                        epicConnected = false
                        epicName = ""
                        games = repository.readCache()
                        message = "Epicの認証情報を削除しました"
                    },
                    onOpenLink = { link -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                )
            }
        }
    }
}

private val sampleGames = listOf(
    OwnedGame("620", "Portal 2", Store.STEAM,
        "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/620/header.jpg", 2540),
    OwnedGame("1091500", "Cyberpunk 2077", Store.STEAM,
        "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/1091500/header.jpg", 7220),
    OwnedGame("1190460", "DEATH STRANDING", Store.STEAM,
        "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/1190460/header.jpg", 320),
    OwnedGame("sample:1", "Control", Store.EPIC),
    OwnedGame("sample:2", "Hades", Store.EPIC),
    OwnedGame("sample:3", "Celeste", Store.EPIC)
)
