package dev.angelic.angel.ui

import android.app.AlertDialog
import android.widget.ScrollView
import dev.angelic.angel.data.AngelApplication
import dev.angelic.angel.data.Preferences
import dev.angelic.angel.stream.BlockSync

class BlocksDialog(private val activity: MainActivity) {
    fun show() {
        val app = activity.application as AngelApplication
        val prefs = Preferences(activity)
        val body = Ui.column(activity).apply { val pad = Ui.dp(context, 20); setPadding(pad, pad, pad, pad) }
        val dialog = AlertDialog.Builder(activity).setTitle("非表示のアカウント")
            .setView(ScrollView(activity).apply { addView(body) }).setPositiveButton("閉じる", null).create()
        app.worker.execute {
            val blocks = app.store.blocks()
            activity.runOnUiThread {
                if (activity.isDestroyed) return@runOnUiThread
                if (blocks.isEmpty()) Ui.add(body, Ui.text(activity, "非表示にしたアカウントはありません。"))
                blocks.forEach { block ->
                    Ui.add(body, Ui.text(activity, "@${block.handle}", 18f, Ui.mint))
                    Ui.add(body, Ui.text(activity, block.reason, 13f))
                    Ui.add(body, Ui.text(activity, prefs.blockResult(block.handle), 13f, Ui.muted))
                    Ui.add(body, Ui.button(activity, "アプリ内の非表示を解除・自動判定の対象外にする") {
                        app.worker.execute {
                            app.store.unblock(block.handle); app.changed()
                            activity.runOnUiThread { dialog.dismiss(); activity.toast("アプリ内で解除しました。X上の解除はXで操作してください") }
                        }
                    })
                    if (prefs.remoteBlock) Ui.add(body, Ui.button(activity, "X上のブロックを実行・再確認") {
                        app.worker.execute { BlockSync.sync(app, block.handle, true); activity.runOnUiThread { dialog.dismiss(); show() } }
                    })
                }
                dialog.show()
            }
        }
    }
}
