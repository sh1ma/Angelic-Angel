package dev.angelic.angel.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import dev.angelic.angel.data.AngelApplication
import dev.angelic.angel.data.Preferences
import dev.angelic.angel.model.Post
import dev.angelic.angel.stream.PushService
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private val app get() = application as AngelApplication
    private val prefs by lazy { Preferences(this) }
    private lateinit var root: LinearLayout
    private lateinit var timeline: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var status: android.widget.TextView
    private val observer: () -> Unit = { refresh() }
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = Ui.column(this).apply {
            setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), 0)
            setBackgroundColor(Ui.background)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(Ui.dp(context, 20) + insets.systemWindowInsetLeft, Ui.dp(context, 16) + insets.systemWindowInsetTop,
                    Ui.dp(context, 20) + insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        Ui.add(root, Ui.text(this, "ANGELIC ANGEL  /  LIVE", 12f, Ui.mint))
        Ui.add(root, Ui.heading(this, "静かなタイムライン"))
        status = Ui.text(this, "停止中", 13f, Ui.muted)
        Ui.add(root, status)
        val actions = Ui.row(this)
        listOf("＋ タブ" to { addTab() }, "設定" to { SettingsDialog(this).show() }, "非表示" to { BlocksDialog(this).show() }).forEach { (label, action) ->
            actions.addView(Ui.button(this, label, action), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        Ui.add(root, actions)
        tabs = Ui.row(this)
        Ui.add(root, HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs) })
        val controls = Ui.row(this)
        listOf("受信開始" to { startReceiving() }, "停止" to { stopService(Intent(this, PushService::class.java)); app.status("停止中") }, "再表示" to { refresh() }).forEach { (label, action) ->
            controls.addView(Ui.button(this, label, action), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        Ui.add(root, controls)
        timeline = Ui.column(this)
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(timeline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onStart() { super.onStart(); app.observe(observer); refresh() }
    override fun onStop() { generation++; app.unobserve(observer); super.onStop() }

    private fun refresh() {
        val requested = ++generation
        status.text = app.connectionStatus
        app.worker.execute {
            val handles = app.store.tabs()
            val selected = prefs.selected?.takeIf { it in handles }
            val posts = app.store.posts(selected)
            val hidden = app.store.blocks().size
            runOnUiThread {
                if (requested != generation || isFinishing || isDestroyed) return@runOnUiThread
                tabs.removeAllViews()
                (listOf<String?>(null) + handles).forEach { handle ->
                    val button = Ui.button(this, handle?.let { "@$it" } ?: "すべて") { prefs.selected = handle; refresh() }
                    if (handle == selected) button.backgroundTintList = android.content.res.ColorStateList.valueOf(Ui.surface)
                    button.setOnLongClickListener {
                        if (handle != null) AlertDialog.Builder(this).setMessage("@$handle のタブを閉じますか？ 投稿履歴は残ります。")
                            .setPositiveButton("閉じる") { _, _ -> app.worker.execute { app.store.removeTab(handle); app.changed() } }.setNegativeButton("戻る", null).show()
                        true
                    }
                    tabs.addView(button)
                }
                timeline.removeAllViews()
                Ui.add(timeline, Ui.text(this, "${posts.size} 件表示  ·  $hidden アカウント非表示", 12f, Ui.muted))
                if (posts.isEmpty()) emptyState() else posts.forEach { Ui.add(timeline, postCard(it), 12) }
            }
        }
    }

    private fun emptyState() {
        Ui.add(timeline, Ui.heading(this, "次の投稿を、ここで。"), 16)
        Ui.add(timeline, Ui.text(this, "設定で通知の受信を準備し、Xでフォロー中の相手の投稿通知をオンにしてください。\n\n受信した投稿がユーザー別のタブに届きます。過去の投稿・フォロー一覧は取得しません。通知に含まれない本文や画像は表示できません。", 15f, Ui.muted))
    }

    private fun postCard(post: Post): LinearLayout = Ui.column(this).apply {
        background = Ui.shape(Ui.surface)
        val padding = Ui.dp(context, 16); setPadding(padding, padding, padding, padding)
        Ui.add(this, Ui.text(context, "@${post.handle}", 15f, Ui.mint))
        Ui.add(this, Ui.text(context, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(post.receivedAt)) + "  受信", 11f, Ui.muted))
        Ui.add(this, Ui.text(context, post.text).apply { setTextIsSelectable(true) }, 12)
        val actions = Ui.row(context)
        actions.addView(Ui.button(context, "Xで開く") { openPost(post.url) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Ui.button(context, "非表示") {
            app.worker.execute { app.store.block(post.handle, "手動で非表示"); app.changed() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(actions)
    }

    private fun openPost(url: String) {
        if (!url.startsWith("https://x.com/")) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast("ブラウザが見つかりません") }
    }

    private fun addTab() {
        val field = EditText(this).apply { hint = "@ユーザーID"; isSingleLine = true; maxLines = 1 }
        val dialog = AlertDialog.Builder(this).setTitle("ユーザーのタブを追加").setView(field).setPositiveButton("追加", null).setNegativeButton("戻る", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = field.text.toString()
                app.worker.execute {
                    val added = app.store.addTab(value)
                    runOnUiThread { if (added) { dialog.dismiss(); refresh() } else field.error = "英数字・_で1〜15文字。重複なし、最大30タブ" }
                }
            }
        }
        dialog.show()
    }

    private fun startReceiving() {
        val configured = runCatching { app.secrets.read().optString("auth_token").isNotEmpty() }.getOrDefault(false)
        if (!configured) { SettingsDialog(this).show(); return }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            toast("通知の許可後、もう一度「受信開始」を押してください")
            return
        }
        startForegroundService(Intent(this, PushService::class.java))
    }

    fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
