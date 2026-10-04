package dev.angelic.angel.ui

import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import dev.angelic.angel.R
import dev.angelic.angel.data.AngelApplication
import dev.angelic.angel.data.Preferences
import dev.angelic.angel.stream.PushService
import dev.angelic.angel.stream.XClient

class SettingsDialog(private val activity: MainActivity) {
    fun show() {
        val app = activity.application as AngelApplication
        val prefs = Preferences(activity)
        val body = Ui.column(activity).apply { val pad = Ui.dp(context, 20); setPadding(pad, pad, pad, pad) }
        Ui.add(body, Ui.text(activity, "受信の準備", 20f))
        Ui.add(body, Ui.text(activity, "Xでフォロー中のユーザーの投稿通知をオンにしてください。auth_token と ct0 はログイン済みブラウザの x.com のCookieから取得します。端末内で暗号化保存します。", 14f, Ui.muted))
        Ui.add(body, Ui.text(activity, "初回登録・受信先が失効した際の再登録ではXのAPIを使います。受信中はMozilla Web Pushに接続します。スクレイピングは行いません。", 14f, Ui.muted))
        fun secret(label: String) = EditText(activity).apply {
            hint = label; isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }.also { Ui.add(body, it) }
        val token = secret("auth_token（空欄なら変更しない）")
        val csrf = secret("ct0（空欄なら変更しない）")
        val automatic = Switch(activity).apply { text = "驚き屋の自動非表示"; isChecked = prefs.automatic }
        Ui.add(body, automatic)
        Ui.add(body, Ui.text(activity, "AI関連の煽り・断定・拡散誘導が複合する投稿が直近10件中3件以上、かつ半数以上で非表示にします。事実の真偽や投稿者の意図は判定しません。誤判定は「非表示」画面から解除できます。", 13f, Ui.muted))
        val remote = Switch(activity).apply { setText(R.string.remote_block_label); isChecked = prefs.remoteBlock }
        Ui.add(body, remote)
        Ui.add(body, Ui.text(activity, "オンにすると、以後判定したアカウントをXのAPIでもブロックします。失敗時はアプリ内のみ非表示にし、結果を保存します。X上のブロック解除はXで操作してください。", 13f, Ui.muted))
        Ui.add(body, Ui.text(activity, "保存すると受信を停止します。別のauth_tokenに変更すると、混在を避けるため端末内の投稿・タブ・非表示履歴を消去します。", 13f, Ui.muted))
        val dialog = AlertDialog.Builder(activity).setTitle("設定").setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("保存", null).setNegativeButton("戻る", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newToken = token.text.toString().trim()
                val newCsrf = csrf.text.toString().trim()
                if (newToken.isNotEmpty() && !XClient.validCookie(newToken) || newCsrf.isNotEmpty() && !XClient.validCookie(newCsrf)) {
                    activity.toast("Cookieに空白・改行・区切り文字が含まれています"); return@setOnClickListener
                }
                activity.stopService(Intent(activity, PushService::class.java))
                val autoValue = automatic.isChecked
                val remoteValue = remote.isChecked
                app.worker.execute {
                    val saved = runCatching {
                        synchronized(app.accountLock) {
                            app.secrets.update { json ->
                                if (newToken.isNotEmpty() && newToken != json.optString("auth_token")) {
                                    require(newCsrf.isNotEmpty()) { "ct0も入力してください" }
                                    app.store.reset()
                                    prefs.clearBlockResults()
                                    json.remove("session")
                                }
                                if (newToken.isNotEmpty()) json.put("auth_token", newToken)
                                if (newCsrf.isNotEmpty()) json.put("ct0", newCsrf)
                                json.put("generation", System.nanoTime().toString())
                            }
                            prefs.automatic = autoValue
                            prefs.remoteBlock = remoteValue
                        }
                    }
                    activity.runOnUiThread {
                        if (saved.isSuccess) { dialog.dismiss(); app.status("停止中・設定を保存しました") }
                        else activity.toast("保存できませんでした。auth_token変更時はct0も入力してください")
                    }
                }
            }
        }
        dialog.show()
    }
}
