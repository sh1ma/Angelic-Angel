package dev.angelic.angel.stream

import dev.angelic.angel.data.AngelApplication
import dev.angelic.angel.data.Preferences
import org.json.JSONObject

object BlockSync {
    private val client = XClient()

    /** No blind retries after ambiguous responses; local hiding survives every API failure. */
    fun sync(
        app: AngelApplication,
        handle: String,
        retry: Boolean = false,
        x: XClient = client,
        readSecrets: () -> JSONObject = { app.secrets.read() },
    ) = synchronized(app.accountLock) {
        val prefs = Preferences(app)
        if (!prefs.remoteBlock || app.store.blocks().none { it.handle == handle }) return
        if (!retry && prefs.blockResult(handle) != "アプリ内で非表示") return
        prefs.saveBlockResult(handle, "X上のブロックを確認中")
        app.changed()
        val result = runCatching { x.block(readSecrets(), handle) }.getOrNull()
        prefs.saveBlockResult(handle, if (result?.success == true) "X上のブロック確認済み" else result?.message ?: "X上の操作に失敗。アプリ内は非表示")
        app.changed()
    }
}
