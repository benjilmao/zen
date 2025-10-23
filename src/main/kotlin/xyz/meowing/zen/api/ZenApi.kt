package xyz.meowing.zen.api

import xyz.meowing.zen.Zen
import xyz.meowing.zen.Zen.Companion.LOGGER
import xyz.meowing.zen.utils.LoopUtils
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Zen.Module
object ZenAPI {
    private var ws: WebSocket? = null
    private var reconnectAttempts = 0
    private const val MAX_RECONNECT_ATTEMPTS = 5
    private const val BASE_RECONNECT_DELAY = 10_000L // 10 seconds

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            LOGGER.warn("Max reconnection attempts ($MAX_RECONNECT_ATTEMPTS) reached. Stopping reconnection.")
            return
        }
        val delayMillis = (BASE_RECONNECT_DELAY * 2.0.pow(reconnectAttempts.toDouble())).toLong()
        reconnectAttempts++
        LOGGER.info("Reconnecting in ${delayMillis / 1000} seconds...")
        LoopUtils.setTimeout(delayMillis) {
            connectToWebsocket()
        }
    }

    init {
        connectToWebsocket()
    }

    fun connectToWebsocket() {
        LOGGER.info("Connecting to Zen WebSocket...")
        try {
            val uuid = Zen.mc.session.profile.id.toString()
            val hashedUUID = hashUUID(uuid)

            val debug = mapOf(
                "version" to Zen.modInfo.version,
                "gameVersion" to Zen.modInfo.mcVersion,
                "hashedUUID" to hashedUUID,
                "jarName" to zenJarName
            )

            val debugOptions = debug.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }

            val url = "ws://zen.mrfast-developer.com:1515/ws?$debugOptions"

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .build()

            ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    LOGGER.info("WebSocket connected!")
                    ws = webSocket
                    reconnectAttempts = 0 // Reset on successful connection
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    LOGGER.info("Received message: $text")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    LOGGER.error("WebSocket error: $t")
                    scheduleReconnect()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    LOGGER.info("WebSocket closing: $code $reason")
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    LOGGER.info("WebSocket closed: $code $reason")
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            LOGGER.error("Error initializing ZenAPI", e)
        }
    }

    @Throws(Exception::class)
    private fun hashUUID(uuid: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(uuid.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private val zenJarName: String
        get() {
            val modsDir = File(Zen.mc.mcDataDir, "mods")
            val files = modsDir.listFiles { _, name ->
                name.lowercase().endsWith(".jar") && name.lowercase().contains("zen")
            }
            return files?.joinToString(",") { it.name } ?: "Zen-DevAuthClient.jar"
        }
}