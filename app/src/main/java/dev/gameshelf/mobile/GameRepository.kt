package dev.gameshelf.mobile

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The two stores are kept separate on purpose: there is no fuzzy title-based merging. */
enum class Store { STEAM, EPIC }

data class OwnedGame(
    val id: String,
    val title: String,
    val store: Store,
    val coverUrl: String? = null,
    val playtimeMinutes: Int? = null
)

data class SyncResult(val games: List<OwnedGame>, val errors: List<String>)

class GameRepository(context: Context) {
    private val secrets = SecureStore(context)
    private val cache = context.getSharedPreferences("local_library", Context.MODE_PRIVATE)

    fun steamId(): String = secrets.get("steam_id").orEmpty()
    fun hasSteamKey(): Boolean = !secrets.get("steam_key").isNullOrBlank()
    fun isEpicConnected(): Boolean = !secrets.get("epic_refresh").isNullOrBlank()
    fun epicDisplayName(): String = secrets.get("epic_name").orEmpty()

    fun saveSteam(id: String, key: String) {
        require(id.matches(Regex("[0-9]{17}"))) { "SteamID64は17桁の数字で入力してください" }
        require(key.isNotBlank() || hasSteamKey()) { "Steam APIキーを入力してください" }
        secrets.put("steam_id", id)
        if (key.isNotBlank()) secrets.put("steam_key", key.trim())
    }
    fun disconnectSteam() {
        secrets.put("steam_id", null)
        secrets.put("steam_key", null)
        removeStoreCache(Store.STEAM)
    }
    fun disconnectEpic() {
        secrets.put("epic_refresh", null)
        secrets.put("epic_access", null)
        secrets.put("epic_expiry", null)
        secrets.put("epic_name", null)
        removeStoreCache(Store.EPIC)
    }
    private fun removeStoreCache(store: Store) {
        saveCache(readCache().filterNot { it.store == store })
    }
    fun readCache(): List<OwnedGame> = runCatching {
        val a = JSONArray(cache.getString("games", "[]"))
        (0 until a.length()).map { i ->
            val obj = a.getJSONObject(i)
            OwnedGame(
                id = obj.getString("id"), title = obj.getString("title"),
                store = Store.valueOf(obj.getString("store")),
                coverUrl = obj.optString("cover").takeIf { it.startsWith("https://") },
                playtimeMinutes = if (obj.has("minutes") && !obj.isNull("minutes")) obj.getInt("minutes") else null
            )
        }
    }.getOrDefault(emptyList())

    private fun saveCache(games: List<OwnedGame>) {
        val arr = JSONArray()
        games.forEach { g ->
            arr.put(JSONObject().put("id", g.id).put("title", g.title).put("store", g.store.name)
                .put("cover", g.coverUrl ?: JSONObject.NULL).put("minutes", g.playtimeMinutes ?: JSONObject.NULL))
        }
        cache.edit().putString("games", arr.toString()).apply()
    }

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val current = readCache()
        var next = current
        val errors = mutableListOf<String>()
        if (steamId().isNotEmpty() && hasSteamKey()) {
            try {
                val steam = loadSteam()
                next = next.filterNot { it.store == Store.STEAM } + steam
            } catch (e: Exception) {
                errors += "Steam: ${e.safeError()}"
            }
        }
        if (isEpicConnected()) {
            try {
                val epic = loadEpic(current.filter { it.store == Store.EPIC })
                next = next.filterNot { it.store == Store.EPIC } + epic
            } catch (e: Exception) {
                errors += "Epic: ${e.safeError()}"
            }
        }
        next = next.distinctBy { it.store to it.id }
        saveCache(next)
        SyncResult(next, errors)
    }

    private fun loadSteam(): List<OwnedGame> {
        val params = "key=${encode(secrets.get("steam_key")!!)}&steamid=${encode(steamId())}" +
                "&include_appinfo=true&include_played_free_games=true&format=json&language=japanese"
        val root = httpJson("https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?$params")
        val response = root.optJSONObject("response") ?: throw IOException("取得できませんでした。公開設定とSteamIDを確認してください")
        val games = response.optJSONArray("games") ?: JSONArray()
        return (0 until games.length()).map { idx ->
            val g = games.getJSONObject(idx)
            val appid = g.getLong("appid").toString()
            OwnedGame(
                id = appid, title = g.optString("name", "App $appid"), store = Store.STEAM,
                coverUrl = "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/$appid/header.jpg",
                playtimeMinutes = g.optInt("playtime_forever", 0)
            )
        }
    }

    companion object {
        // Public Epic Launcher OAuth client credentials referenced by Legendary.
        // This is NOT a personal password or secret. These values may be revoked by Epic.
        private const val EPIC_CLIENT_ID = "34a02cf8f4414e29b15921876da36f9a"
        private const val EPIC_CLIENT_SECRET = "daafbccc737745039dffe53d94fc76cf"
        private const val EPIC_AGENT = "UELauncher/11.0.1-14907503+++Portal+Release-Live Windows/10.0.19041.1.256.64bit"
        val epicLoginUrl: String
            get() = "https://www.epicgames.com/id/login?redirectUrl=" +
                encode("https://www.epicgames.com/id/api/redirect?clientId=$EPIC_CLIENT_ID&responseType=code")
        private fun encode(str: String): String = URLEncoder.encode(str, "UTF-8")
    }

    /** Authorization code is pasted from Epic's first-party browser response; never ask for a password. */
    suspend fun authorizeEpic(rawCode: String): String = withContext(Dispatchers.IO) {
        val code = extractEpicCode(rawCode)
        val token = tokenRequest("grant_type=authorization_code&code=${encode(code)}&token_type=eg1")
        storeEpicToken(token)
        epicDisplayName().ifEmpty { "接続済み" }
    }

    private fun extractEpicCode(raw: String): String {
        val trimmed = raw.trim()
        val possibleJson = runCatching { JSONObject(trimmed) }.getOrNull()
        val code = possibleJson?.optString("authorizationCode")?.takeIf { it.isNotBlank() }
            ?: possibleJson?.optString("code")?.takeIf { it.isNotBlank() }
            ?: trimmed
        require(code.length in 8..4096 && !code.contains(' ') && !code.contains('\n')) {
            "Epicの認証コードを貼り付けてください"
        }
        return code
    }

    private fun storeEpicToken(token: JSONObject) {
        val refresh = token.optString("refresh_token")
        require(refresh.isNotEmpty()) { "Epicから更新トークンが返されませんでした" }
        secrets.put("epic_refresh", refresh)
        secrets.put("epic_access", token.getString("access_token"))
        val expiry = System.currentTimeMillis() + token.optLong("expires_in", 3600) * 1000L
        secrets.put("epic_expiry", expiry.toString())
        token.optString("displayName").takeIf { it.isNotBlank() }?.let { secrets.put("epic_name", it) }
    }

    private fun epicAccessToken(): String {
        val expiry = secrets.get("epic_expiry")?.toLongOrNull() ?: 0
        val access = secrets.get("epic_access")
        if (!access.isNullOrBlank() && System.currentTimeMillis() < expiry - 5 * 60 * 1000L) return access
        val refresh = secrets.get("epic_refresh") ?: throw IllegalStateException("Epicが未接続です")
        val token = tokenRequest("grant_type=refresh_token&refresh_token=${encode(refresh)}&token_type=eg1")
        storeEpicToken(token)
        return token.getString("access_token")
    }

    private fun tokenRequest(body: String): JSONObject {
        val auth = Base64.encodeToString("$EPIC_CLIENT_ID:$EPIC_CLIENT_SECRET".toByteArray(), Base64.NO_WRAP)
        return httpJson("https://account-public-service-prod03.ol.epicgames.com/account/api/oauth/token",
            method = "POST", body = body, extraHeaders = mapOf(
                "Authorization" to "Basic $auth",
                "Content-Type" to "application/x-www-form-urlencoded",
                "User-Agent" to EPIC_AGENT
            ))
    }

    private suspend fun loadEpic(previous: List<OwnedGame>): List<OwnedGame> = coroutineScope {
        val token = epicAccessToken()
        val headers = mapOf("Authorization" to "Bearer $token", "User-Agent" to EPIC_AGENT)
        val items = mutableListOf<JSONObject>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        var pages = 0
        while (true) {
            if (++pages > 100) throw IOException("Epicのページ上限に達しました")
            val url = "https://library-service.live.use1a.on.epicgames.com/library/api/public/items?includeMetadata=true" +
                (cursor?.let { "&cursor=${encode(it)}" } ?: "")
            val page = httpJson(url, extraHeaders = headers)
            val records = page.optJSONArray("records") ?: JSONArray()
            for (i in 0 until records.length()) items.add(records.getJSONObject(i))
            val next = page.optJSONObject("responseMetadata")?.optString("nextCursor")?.takeIf { it.isNotBlank() }
            if (next == null) break
            if (!seenCursors.add(next)) throw IOException("Epicのページングがループしています")
            cursor = next
        }
        val goodItems = items.filter { item ->
            item.optString("appName").isNotBlank() && item.optString("appName") != "1" &&
                item.optString("catalogItemId").isNotBlank() && item.optString("namespace") != "ue" &&
                item.optString("sandboxType") != "PRIVATE" &&
                item.optString("namespace") != "89efe5924d3d467c839449ab6ab52e7f"
        }.distinctBy { it.optString("namespace") + ":" + it.optString("catalogItemId") }
        val previousById = previous.associateBy { it.id }
        val limiter = Semaphore(5)
        goodItems.map { item ->
            async(Dispatchers.IO) {
                val catalogId = item.getString("catalogItemId")
                val namespace = item.getString("namespace")
                val uniqueId = "$namespace:$catalogId"
                previousById[uniqueId]?.let { return@async it }
                limiter.withPermit {
                    try {
                        val endpoint = "https://catalog-public-service-prod06.ol.epicgames.com/catalog/api/shared/namespace/" +
                            "${encode(namespace)}/bulk/items?id=${encode(catalogId)}&includeDLCDetails=true" +
                            "&includeMainGameDetails=true&country=JP&locale=ja"
                        val info = httpJson(endpoint, extraHeaders = headers).optJSONObject(catalogId)
                        if (info != null) {
                            if (info.has("mainGameItem")) return@withPermit null // DLC
                            val categories = info.optJSONArray("categories") ?: JSONArray()
                            for (i in 0 until categories.length()) {
                                if (categories.optJSONObject(i)?.optString("path") == "mods") return@withPermit null
                            }
                        }
                        val images = info?.optJSONArray("keyImages") ?: JSONArray()
                        var cover: String? = null
                        for (type in listOf("DieselGameBoxTall", "DieselGameBox", "OfferImageTall", "Thumbnail")) {
                            for (i in 0 until images.length()) {
                                val image = images.optJSONObject(i)
                                if (image?.optString("type") == type && image.optString("url").startsWith("https://")) {
                                    cover = image.optString("url")
                                    break
                                }
                            }
                            if (cover != null) break
                        }
                        OwnedGame(uniqueId, info?.optString("title")?.takeIf { it.isNotBlank() }
                            ?: item.optString("appName"), Store.EPIC, cover)
                    } catch (_: Exception) {
                        // Do not discard the entire library for one unavailable catalog entry.
                        OwnedGame(uniqueId, item.optString("appName"), Store.EPIC)
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun httpJson(
        url: String,
        method: String = "GET",
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        require(url.startsWith("https://"))
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false // Do not leak credentials to redirect targets
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            extraHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code（連携状態やAPI仕様を確認してください）")
            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}

private fun Throwable.safeError(): String = when (this) {
    is IOException -> message?.take(130) ?: "通信エラー"
    else -> "認証またはレスポンスの解析に失敗しました"
}
