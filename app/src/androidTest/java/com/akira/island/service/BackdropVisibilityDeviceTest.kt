package com.akira.island.service

import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akira.island.ui.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackdropVisibilityDeviceTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test fun unchangedGeometryCanReturnAfterZeroStrengthAndExplicitHide() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        activity.scenario.onActivity { a ->
            assumeTrue(a.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled)
            val blur = IslandBackdropBlur(a, a.window.decorView)
            val area = Rect(20, 100, 260, 220)
            try {
                blur.update(area, 30f, true, strengthPercent = 50)
                assertTrue(blur.showing)
                blur.update(area, 30f, true, strengthPercent = 0)
                assertFalse("Zero strength hides the real window", blur.showing)
                blur.update(area, 30f, true, strengthPercent = 100)
                assertTrue("A hidden Dialog still reports isShowing; its decor must return", blur.showing)
                blur.hide()
                assertFalse(blur.showing)
                blur.update(area, 30f, true, strengthPercent = 100)
                assertTrue("Unchanged geometry must not prevent reopening", blur.showing)
            } finally {
                blur.close()
            }
            assertFalse(blur.showing)
        }
    }
}
