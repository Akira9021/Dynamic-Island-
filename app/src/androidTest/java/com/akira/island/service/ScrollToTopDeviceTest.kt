package com.akira.island.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.island.data.SettingsRepo
import com.akira.island.ui.MainActivity
import com.akira.island.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeoutException

/** Uses real Android accessibility nodes, not coordinate gestures or a simulated scroll result. */
@RunWith(AndroidJUnit4::class)
class ScrollToTopDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun await(description: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(40)
        assertTrue(description, condition())
    }

    private fun fixtureStatus(stage: String, metadata: String) {
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "\nScrollToTop fixture $stage: $metadata\n") })
    }

    private data class FixtureState(val ready: Boolean, val metadata: String)

    /** Read only our fixture's structure/actions; never include node text in diagnostics. */
    @Suppress("DEPRECATION")
    private fun fixtureState(automation: UiAutomation, pkg: String, expectList: Boolean): FixtureState {
        val windows = automation.windows
        try {
            val blocked = windows.any { (it.isActive || it.isFocused) &&
                (it.type == AccessibilityWindowInfo.TYPE_SYSTEM || it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) }
            val window = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
                .maxByOrNull { it.layer } ?: return FixtureState(false, "fixtureWindow=false; blocked=$blocked")
            val root = window.root ?: return FixtureState(false, "fixtureRoot=false; blocked=$blocked")
            try {
                if (!root.refresh() || root.packageName?.toString() != pkg) return FixtureState(false, "fixtureActive=false; blocked=$blocked")
                val windowMeta = "window=${window.id}; active=${window.isActive}; focused=${window.isFocused}; rootVisible=${root.isVisibleToUser}; blocked=$blocked"
                val nodes = java.util.ArrayDeque<AccessibilityNodeInfo>()
                nodes.add(AccessibilityNodeInfo.obtain(root))
                var checked = 0
                var sawList = false
                var sawTextView = false
                var listMeta = "list=false"
                val deadline = SystemClock.uptimeMillis() + 500
                try {
                    while (nodes.isNotEmpty() && checked++ < 64 && SystemClock.uptimeMillis() < deadline) {
                        val node = nodes.removeFirst()
                        try {
                            val cls = node.className?.toString()
                            if (cls == "android.widget.ListView") {
                                sawList = true
                                val fresh = node.refresh()
                                val actions = node.actionList.map { it.id }
                                val collection = node.collectionInfo
                                val position = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id in actions
                                listMeta = "list=true; fresh=$fresh; visible=${node.isVisibleToUser}; enabled=${node.isEnabled}; scrollable=${node.isScrollable}; rows=${collection?.rowCount}; columns=${collection?.columnCount}; actions=${actions.joinToString(",")}"
                                if (expectList && !blocked && root.isVisibleToUser && fresh && node.isVisibleToUser && node.isEnabled &&
                                    node.isScrollable && node.windowId == window.id && node.packageName?.toString() == pkg &&
                                    position) {
                                    return FixtureState(true, "$windowMeta; $listMeta; checked=$checked")
                                }
                            }
                            if (cls == "android.widget.TextView" && node.isVisibleToUser) sawTextView = true
                            for (index in 0 until minOf(node.childCount, 32, 64 - checked - nodes.size)) {
                                if (SystemClock.uptimeMillis() >= deadline) break
                                node.getChild(index)?.let(nodes::add)
                            }
                        } finally { node.recycle() }
                    }
                    return FixtureState(!expectList && !blocked && root.isVisibleToUser && sawTextView && !sawList && nodes.isEmpty(),
                        "$windowMeta; $listMeta; textView=$sawTextView; checked=$checked; remaining=${nodes.size}")
                } finally { nodes.forEach { it.recycle() } }
            } finally { root.recycle() }
        } finally { windows.forEach { it.recycle() } }
    }

    @Test fun aRealAccessibleListReturnsToItsFirstRowAndAnUnsupportedScreenIsReported() {
        val context = instrumentation.targetContext
        lateinit var saved: com.akira.island.data.IslandSettings
        instrumentation.runOnMainSync {
            SettingsRepo.init(context)
            saved = SettingsRepo.current
            SettingsRepo.update { copy(enabled = false, autoUpdateCheck = false, lastSeenVersion = UpdateChecker.currentVersion(context)) }
        }
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        info.flags = originalFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val result = AtomicReference<ScrollTopResult?>(null)
        var fixtureMetadata = "not prepared"
        var lastFirstRow = -1
        var firstRowAfterAccepted = -1
        val windowLookupOnMain = AtomicBoolean(false)
        val reportOffMain = AtomicBoolean(false)
        // The activity belongs to our own test fixture; production excludes the island's package.
        val controller = ScrollToTop(scope, {
            if (Looper.myLooper() == Looper.getMainLooper()) windowLookupOnMain.set(true)
            automation.windows
        }, { true }, { _, _ -> false }) {
            if (Looper.myLooper() != Looper.getMainLooper()) reportOffMain.set(true)
            result.set(it)
        }
        fun awaitFixture(expectList: Boolean) {
            val idle = try { automation.waitForIdle(200, 5_000); true } catch (_: TimeoutException) { false }
            await("The accessibility fixture must expose the expected active window and node actions") {
                val state = fixtureState(automation, context.packageName, expectList)
                fixtureMetadata = "eventIdle=$idle; ${state.metadata}"
                state.ready
            }
            fixtureStatus(if (expectList) "list-ready" else "plain-ready", fixtureMetadata)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var list: ListView
                scenario.onActivity { activity ->
                    list = ListView(activity).apply {
                        adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, (0..300).map { "Row $it" })
                    }
                    activity.setContentView(list)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { list.setSelection(180) }
                instrumentation.waitForIdleSync()
                fun firstRow(): Int {
                    var first = -1
                    instrumentation.runOnMainSync { first = list.firstVisiblePosition }
                    lastFirstRow = first
                    return first
                }
                await("The fixture must be scrolled away from its first row") { firstRow() >= 170 }
                awaitFixture(expectList = true)
                assertTrue("Accessibility setup must not scroll the fixture", firstRow() >= 170)
                instrumentation.runOnMainSync { controller.request() }
                await("A real scroll action should be accepted") { result.get() != null }
                assertEquals(ScrollTopResult.REQUESTED, result.get())
                firstRowAfterAccepted = firstRow()
                fixtureStatus("action-accepted", "firstRow=$firstRowAfterAccepted; result=${result.get()}")
                // Native ListView positioning can continue asynchronously after accepting the action.
                await("The supported list should actually reach row zero", timeoutMs = 30_000) { firstRow() == 0 }

                scenario.onActivity { activity -> activity.setContentView(TextView(activity).apply { text = "No scroll actions" }) }
                instrumentation.waitForIdleSync()
                awaitFixture(expectList = false)
                result.set(null)
                instrumentation.runOnMainSync { controller.request() }
                await("Unsupported screens must produce an honest result") { result.get() != null }
                assertEquals(ScrollTopResult.UNSUPPORTED, result.get())
                assertFalse("Accessibility window lookups must stay off the main thread", windowLookupOnMain.get())
                assertFalse("Feedback callbacks must run on the main thread", reportOffMain.get())
            }
        } catch (failure: Throwable) {
            fixtureStatus("failure", "result=${result.get()}; firstRowAfterAccepted=$firstRowAfterAccepted; lastFirstRow=$lastFirstRow; windowLookupOnMain=${windowLookupOnMain.get()}; reportOffMain=${reportOffMain.get()}; $fixtureMetadata")
            throw failure
        } finally {
            instrumentation.runOnMainSync { controller.cancel(); scope.cancel(); SettingsRepo.update { saved } }
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }
}
