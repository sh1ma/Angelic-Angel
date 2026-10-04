package dev.angelic.angel.data

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

class AngelApplication : Application() {
    val accountLock = Any()
    val store: TimelineStore by lazy { TimelineStore(this) }
    val secrets: SecretStore by lazy { SecretStore(this) }
    val worker = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var connectionStatus = "停止中"
        private set

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun unobserve(listener: () -> Unit) { listeners.remove(listener) }
    fun changed() { main.post { listeners.forEach { it() } } }
    fun status(value: String) { connectionStatus = value; changed() }
}

class Preferences(context: Context) {
    private val values = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    var automatic: Boolean
        get() = values.getBoolean("automatic", true)
        set(value) { values.edit().putBoolean("automatic", value).apply() }
    var selected: String?
        get() = values.getString("selected", null)
        set(value) { values.edit().putString("selected", value).apply() }
    var remoteBlock: Boolean
        get() = values.getBoolean("remoteBlock", false)
        set(value) { values.edit().putBoolean("remoteBlock", value).apply() }
    fun blockResult(handle: String): String = values.getString("block.$handle", "アプリ内で非表示").orEmpty()
    fun saveBlockResult(handle: String, result: String) {
        check(values.edit().putString("block.$handle", result).commit()) { "Block status storage failed" }
    }
    fun clearBlockResults() {
        val edit = values.edit().remove("selected")
        values.all.keys.filter { it.startsWith("block.") }.forEach { edit.remove(it) }
        check(edit.commit()) { "Account state storage failed" }
    }
}
