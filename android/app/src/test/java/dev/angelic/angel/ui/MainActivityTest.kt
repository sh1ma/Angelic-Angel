package dev.angelic.angel.ui

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import dev.angelic.angel.data.AngelApplication
import dev.angelic.angel.data.Preferences
import dev.angelic.angel.model.Post
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], application = AngelApplication::class)
class MainActivityTest {
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun drain(app: AngelApplication) {
        app.worker.submit {}.get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun switchingTabsAndBlockingUpdateTheVisibleTimeline() {
        val app = RuntimeEnvironment.getApplication() as AngelApplication
        app.store.reset()
        app.store.addTab("alice"); app.store.addTab("bob")
        app.store.ingest(Post("1", "alice", "Alice", "alice unique body", 1), false)
        app.store.ingest(Post("2", "bob", "Bob", "bob unique body", 2), false)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drain(app)
        fun text() = views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(text().contains("alice unique body"))
        assertTrue(text().contains("bob unique body"))
        views(activity.window.decorView).filterIsInstance<Button>().first { it.text == "@alice" }.performClick()
        drain(app)
        assertTrue(text().contains("alice unique body"))
        assertFalse(text().contains("bob unique body"))
        app.store.block("alice", "test"); app.changed()
        Shadows.shadowOf(Looper.getMainLooper()).idle(); drain(app)
        assertFalse(text().contains("alice unique body"))
        assertTrue(Preferences(activity).selected == "alice")
        controller.pause().stop().destroy()
        app.store.close()
    }
}
