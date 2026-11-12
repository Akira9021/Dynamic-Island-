package com.akira.island.core

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class GlassOpticsTest {
    @Test fun `optical density has a clear midrange but preserves the opaque endpoint`() {
        assertEquals(1f, glassBodyDensity(1f), 0f)
        assertEquals(0.4624f, glassBodyDensity(0.68f), 0.00001f)
        assertEquals(glassBodyDensity(0.68f), glassBodyDensity(Float.NaN), 0f)
        assertEquals(glassBodyDensity(0.4f), glassBodyDensity(-1f), 0f)
        var previous = 0f
        for (i in 40..100) { val density = glassBodyDensity(i / 100f); assertTrue(density > previous); previous = density }
    }
    @Test fun `capsules cards and circles retain finite bounded geometry`() {
        for ((w, h) in listOf(240f to 32f, 320f to 240f, 40f to 40f, 150f to 400f)) {
            val model = GlassOptics(w, h, 2f)
            assertTrue(model.valid)
            assertTrue(model.softRim >= model.innerRim && model.innerRim >= model.lip)
            for (x in -2..2) for (y in -2..2) {
                model.update(x / 2f, y / 2f, 0.5f)
                assertTrue(coordinates(model).all(Float::isFinite))
                assertTrue(model.reflectionRadius > 0f && model.causticRadius > 0f)
            }
        }
    }

    @Test fun `invalid sizes do not manufacture a drawable surface`() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val model = GlassOptics(bad, 40f)
            assertFalse(model.valid)
            model.update(1f, -1f, 0.5f)
            assertTrue(coordinates(model).all(Float::isFinite))
        }
    }

    @Test fun `invalid density falls back and extreme dimensions stay finite`() {
        val ordinary = GlassOptics(240f, 80f)
        for (unit in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val invalid = GlassOptics(240f, 80f, unit)
            assertEquals(ordinary.softRim, invalid.softRim, 0f)
            assertEquals(ordinary.lip, invalid.lip, 0f)
        }
        val huge = GlassOptics(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        huge.update(1f, -1f, 0.5f)
        assertTrue(coordinates(huge).all(Float::isFinite))
    }

    @Test fun `positive horizontal tilt moves the reflection and its opposite caustic coherently`() {
        val model = GlassOptics(240f, 80f)
        val neutralReflection = model.reflectionX
        val neutralCaustic = model.causticX
        model.update(1f, 0f, -1f)
        assertTrue(model.reflectionX > neutralReflection)
        assertTrue(model.causticX < neutralCaustic)
        assertTrue("Incoming rim light should come from the right", model.rimDx < 0f)
        model.update(-1f, 0f, -1f)
        assertTrue(model.reflectionX < neutralReflection)
        assertTrue(model.causticX > neutralCaustic)
        assertTrue(model.rimDx > 0f)
    }

    @Test fun `vertical tilt controls the second axis as well`() {
        val model = GlassOptics(240f, 80f)
        model.update(0f, -1f, -1f)
        val upperReflection = model.reflectionY
        val upperCaustic = model.causticY
        model.update(0f, 1f, -1f)
        assertTrue(model.reflectionY > upperReflection)
        assertTrue(model.causticY > upperCaustic)
    }

    @Test fun `extreme tilts clamp to the same physical endpoints`() {
        val model = GlassOptics(240f, 80f)
        val endpoint = GlassOptics(240f, 80f)
        model.update(1000f, -1000f, 0.5f)
        endpoint.update(1f, -1f, 0.5f)
        assertArrayEquals(coordinates(endpoint), coordinates(model), 0f)
    }

    @Test fun `bad sensor samples cannot poison subsequent frames`() {
        val model = GlassOptics(240f, 80f)
        model.update(1f, -1f, 0.5f)
        model.update(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN)
        assertArrayEquals(coordinates(GlassOptics(240f, 80f)), coordinates(model), 0f)
        model.update(0.5f, -0.25f, 0.5f)
        assertTrue(coordinates(model).all(Float::isFinite))
        assertTrue(model.sheenStrength > 0f)
    }

    @Test fun `the specular sweep starts ends and cancels without a flash`() {
        val model = GlassOptics(240f, 80f)
        for (phase in listOf(-1f, 0f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY)) {
            model.update(0f, 0f, phase)
            assertEquals(0f, model.sheenStrength, 0f)
        }
        model.update(0f, 0f, 0.5f)
        assertEquals(1f, model.sheenStrength, 0.00001f)
    }

    @Test fun `the sweep travels continuously across the whole surface`() {
        val model = GlassOptics(240f, 80f)
        var lastX = model.sheenX
        var lastStrength = 0f
        for (step in 0..100) {
            model.update(0f, 0f, step / 100f)
            assertTrue(model.sheenX >= lastX)
            assertTrue(abs(model.sheenStrength - lastStrength) < 0.033f)
            lastX = model.sheenX; lastStrength = model.sheenStrength
        }
        assertTrue(model.sheenX > model.width)
    }

    @Test fun `small light changes cannot jump between optical states`() {
        val model = GlassOptics(240f, 80f)
        model.update(-1f, 0f, -1f)
        var last = coordinates(model)
        for (step in 1..1000) {
            model.update(-1f + step / 500f, 0f, -1f)
            val current = coordinates(model)
            assertTrue(current.indices.all { abs(current[it] - last[it]) < 1f })
            last = current
        }
    }

    @Test fun `repeated unchanged lighting has no drift`() {
        val model = GlassOptics(240f, 80f)
        model.update(0.75f, -0.5f, 0.25f)
        val first = coordinates(model)
        repeat(1000) { model.update(0.75f, -0.5f, 0.25f) }
        assertArrayEquals(first, coordinates(model), 0f)
    }

    private fun coordinates(o: GlassOptics) = floatArrayOf(o.rimX, o.rimY, o.rimDx, o.rimDy,
        o.reflectionX, o.reflectionY, o.causticX, o.causticY, o.sheenX, o.sheenStrength,
        o.softRim, o.innerRim, o.lip, o.reflectionRadius, o.causticRadius)
}
