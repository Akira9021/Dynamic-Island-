package com.akira.island.service

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.MainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque

internal enum class ScrollTopResult { REQUESTED, UNSUPPORTED, LIMIT_REACHED, CANCELLED }

private val verticalScrollClasses = setOf(
    "android.widget.ScrollView",
    "android.widget.ListView",
    "android.widget.ExpandableListView",
    "androidx.core.widget.NestedScrollView",
    "android.support.v4.widget.NestedScrollView",
)

private inline fun <T> accessibilityOrNull(block: () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

/** A single, already selected list; never switch to another list midway through a gesture. */
internal interface ScrollTopTarget : AutoCloseable {
    fun isCurrent(): Boolean
    fun jumpToStart(): Boolean
    fun scrollUp(): Boolean
}

/** Action-driven work only. A true action result means accepted, not proof that the top was reached. */
internal suspend fun scrollToTop(
    target: ScrollTopTarget,
    maxSteps: Int = 16,
    pause: suspend () -> Unit = { delay(120) },
): ScrollTopResult {
    try {
        currentCoroutineContext().ensureActive()
        if (!target.isCurrent()) return ScrollTopResult.CANCELLED
        if (target.jumpToStart()) return ScrollTopResult.REQUESTED
        var moved = false
        val steps = maxSteps.coerceIn(1, 16)
        repeat(steps) { step ->
            currentCoroutineContext().ensureActive()
            if (!target.isCurrent()) return ScrollTopResult.CANCELLED
            if (!target.scrollUp()) {
                return if (moved) ScrollTopResult.REQUESTED else ScrollTopResult.UNSUPPORTED
            }
            moved = true
            if (step < steps - 1) pause()
        }
        return ScrollTopResult.LIMIT_REACHED
    } finally {
        target.close()
    }
}

/** Horizontal carousels with only generic backward/forward actions must remain untouched. */
internal fun isVerticalScroll(
    className: String,
    rows: Int,
    columns: Int,
    hasUp: Boolean,
    hasDown: Boolean,
): Boolean = hasUp || hasDown || (rows > 1 && columns == 1) || className in verticalScrollClasses

/**
 * Only an explicit island gesture starts this controller. It inspects visibility, geometry,
 * collection metadata and advertised actions; it never reads text, descriptions or screenshots.
 */
internal class ScrollToTop(
    private val scope: CoroutineScope,
    private val readWindows: () -> List<AccessibilityWindowInfo>,
    private val canRun: () -> Boolean,
    private val isExcluded: (packageName: String, className: String) -> Boolean,
    private val report: (ScrollTopResult) -> Unit,
) {
    private var job: Job? = null
    @Volatile private var activeRequest: Request? = null

    @MainThread
    fun request() {
        cancel()
        val request = Request()
        activeRequest = request
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                val result = withTimeoutOrNull(3_000) {
                    withContext(Dispatchers.Default) {
                        val context = currentCoroutineContext()
                        val checkActive: () -> Unit = {
                            context.ensureActive()
                            if (activeRequest !== request) throw CancellationException()
                        }
                        checkActive()
                        if (!canRun()) return@withContext null
                        val window = currentWindow(checkActive) ?: return@withContext ScrollTopResult.UNSUPPORTED
                        withContext(Dispatchers.Main.immediate) { request.window = window }
                        checkActive()
                        val search = findTarget(window, checkActive)
                        val outcome = when {
                            search.target != null -> scrollToTop(search.target)
                            search.limited -> ScrollTopResult.LIMIT_REACHED
                            else -> ScrollTopResult.UNSUPPORTED
                        }
                        checkActive()
                        if (currentWindow(checkActive) == window) outcome else null
                    }
                }
                // App/window changes are silent: do not put a stale explanation over the next app.
                // A hard timeout is also silent if verification could not finish within the budget.
                if (activeRequest === request && result != null) report(result)
            } finally {
                if (activeRequest === request) {
                    activeRequest = null
                    job = null
                }
            }
        }
    }

    @MainThread
    fun cancel() {
        activeRequest = null
        job?.cancel()
        job = null
    }

    @MainThread
    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val request = activeRequest ?: return
        when (event?.eventType) {
            // Even same-package transitions can reuse the old window ID. No tree RPC on Main.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> cancel()
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val window = request.window
                if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    cancel()
                    return
                }
                val changes = event.windowChanges
                val focusChanges = AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or AccessibilityEvent.WINDOWS_CHANGE_FOCUSED or
                    AccessibilityEvent.WINDOWS_CHANGE_ADDED or AccessibilityEvent.WINDOWS_CHANGE_REMOVED
                if (changes == 0 || changes and focusChanges != 0 ||
                    event.windowId == window.id && changes and AccessibilityEvent.WINDOWS_CHANGE_BOUNDS != 0) cancel()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentWindow(checkActive: () -> Unit): ScrollWindow? {
        checkActive()
        if (!canRun()) return null
        return accessibilityOrNull {
            checkActive()
            val windows = readWindows()
            try {
                checkActive()
                if (windows.any { (it.isActive || it.isFocused) &&
                        (it.type == AccessibilityWindowInfo.TYPE_SYSTEM || it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) }) {
                    return@accessibilityOrNull null
                }
                val window = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
                    .maxByOrNull { it.layer } ?: return@accessibilityOrNull null
                checkActive()
                val root = window.root ?: return@accessibilityOrNull null
                try {
                    checkActive()
                    val pkg = root.packageName?.toString() ?: return@accessibilityOrNull null
                    if (!root.isVisibleToUser || isExcluded(pkg, root.className?.toString().orEmpty())) return@accessibilityOrNull null
                    checkActive()
                    val bounds = Rect().also(window::getBoundsInScreen)
                    if (bounds.isEmpty) return@accessibilityOrNull null
                    ScrollWindow(window.id, pkg, bounds)
                } finally {
                    root.recycle()
                }
            } finally {
                windows.forEach { it.recycle() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun findTarget(window: ScrollWindow, checkActive: () -> Unit): TargetSearch {
        if (currentWindow(checkActive) != window) return TargetSearch()
        val root = accessibilityOrNull {
            checkActive()
            val windows = readWindows()
            try {
                checkActive()
                windows.firstOrNull { it.id == window.id && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }?.root
            }
            finally { windows.forEach { it.recycle() } }
        } ?: return TargetSearch()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        var checked = 0
        var limited = false
        val deadline = SystemClock.uptimeMillis() + 180
        try {
            while (queue.isNotEmpty() && checked < 160 && SystemClock.uptimeMillis() < deadline) {
                checkActive()
                val (node, depth) = queue.removeFirst()
                checked++
                try {
                    if (!node.isVisibleToUser) continue
                    if (isUsable(node, window)) {
                        val bounds = Rect().also(node::getBoundsInScreen)
                        val area = if (bounds.intersect(window.bounds)) bounds.width().toLong() * bounds.height() else 0L
                        if (area > bestArea) {
                            best?.recycle()
                            best = AccessibilityNodeInfo.obtain(node)
                            bestArea = area
                        }
                    }
                    if (depth < 12) {
                        val count = minOf(node.childCount, 32, 160 - checked - queue.size)
                        if (count < node.childCount) limited = true
                        for (index in 0 until count) {
                            checkActive()
                            if (SystemClock.uptimeMillis() >= deadline) {
                                limited = true
                                break
                            }
                            node.getChild(index)?.let { queue.add(it to depth + 1) }
                        }
                    } else if (node.childCount > 0) limited = true
                } finally {
                    node.recycle()
                }
            }
            checkActive()
            return TargetSearch(best?.let { AndroidScrollTarget(it, window, checkActive) }, limited || queue.isNotEmpty())
                .also { best = null }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return TargetSearch()
        } finally {
            best?.recycle()
            queue.forEach { (node, _) -> node.recycle() }
        }
    }

    private fun isUsable(node: AccessibilityNodeInfo, window: ScrollWindow): Boolean {
        if (node.windowId != window.id || node.packageName?.toString() != window.packageName ||
            !node.isVisibleToUser || !node.isEnabled || !node.isScrollable) return false
        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds.isEmpty || !Rect.intersects(bounds, window.bounds)) return false
        val actions = node.actionList.map { it.id }.toSet()
        val collection = node.collectionInfo
        if (!isVerticalScroll(node.className?.toString().orEmpty(), collection?.rowCount ?: 0,
                collection?.columnCount ?: 0, AccessibilityAction.ACTION_SCROLL_UP.id in actions,
                AccessibilityAction.ACTION_SCROLL_DOWN.id in actions)) return false
        // A verified vertical ListView may advertise this action with unknown collection counts.
        val position = AccessibilityAction.ACTION_SCROLL_TO_POSITION.id in actions
        return position || AccessibilityAction.ACTION_SCROLL_UP.id in actions || AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD in actions
    }

    private inner class AndroidScrollTarget(
        private val node: AccessibilityNodeInfo,
        private val window: ScrollWindow,
        private val checkActive: () -> Unit,
    ) : ScrollTopTarget {
        override fun isCurrent(): Boolean = accessibilityOrNull {
            if (currentWindow(checkActive) != window) return@accessibilityOrNull false
            checkActive()
            val refreshed = node.refresh()
            checkActive()
            refreshed && isUsable(node, window)
        } ?: false

        override fun jumpToStart(): Boolean = accessibilityOrNull {
            if (!isCurrent()) return@accessibilityOrNull false
            if (node.actionList.none { it.id == AccessibilityAction.ACTION_SCROLL_TO_POSITION.id }) return@accessibilityOrNull false
            val arguments = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0)
            }
            checkActive()
            node.performAction(AccessibilityAction.ACTION_SCROLL_TO_POSITION.id, arguments).also { checkActive() }
        } ?: false

        override fun scrollUp(): Boolean = accessibilityOrNull {
            if (!isCurrent()) return@accessibilityOrNull false
            if (node.actionList.any { it.id == AccessibilityAction.ACTION_SCROLL_UP.id }) {
                checkActive()
                val accepted = node.performAction(AccessibilityAction.ACTION_SCROLL_UP.id)
                checkActive()
                if (accepted) return@accessibilityOrNull true
            }
            if (!isCurrent()) return@accessibilityOrNull false
            checkActive()
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD } &&
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD).also { checkActive() }
        } ?: false

        @Suppress("DEPRECATION")
        override fun close() = node.recycle()
    }

    private class Request {
        @Volatile var window: ScrollWindow? = null
    }

    private data class TargetSearch(val target: ScrollTopTarget? = null, val limited: Boolean = false)

    private data class ScrollWindow(val id: Int, val packageName: String, val bounds: Rect)
}
