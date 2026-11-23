package com.faceplugin.facerecognitionsdk.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The thresholds decide what the capture guide, identify and the attribute screen all report,
 * so the boundaries are pinned here.
 */
class FaceLimitsTest {

    private val limits = FaceLimits(match = 0.80f, liveness = 0.50f, yaw = 20f, roll = 20f, pitch = 20f)

    @Test
    fun `a score on the liveness threshold is live`() {
        assertTrue(limits.isLive(0.50f))
        assertTrue(limits.isLive(0.51f))
        assertFalse(limits.isLive(0.49f))
    }

    @Test
    fun `liveness that was never scored is not live`() {
        assertFalse(limits.isLive(Float.NaN))
    }

    @Test
    fun `a score on the match threshold is the same person`() {
        assertTrue(limits.matches(0.80f))
        assertFalse(limits.matches(0.79f))
    }

    @Test
    fun `pose is checked in both directions`() {
        assertTrue(limits.poseOk(20f, -20f, 0f))
        assertFalse(limits.poseOk(-20.5f, 0f, 0f))
        assertFalse(limits.poseOk(0f, 0f, 25f))
    }

    @Test
    fun `each angle has its own limit`() {
        val narrowYaw = FaceLimits(yaw = 5f, roll = 40f, pitch = 40f)
        assertFalse(narrowYaw.poseOk(10f, 0f, 0f))
        assertTrue(narrowYaw.poseOk(0f, 30f, 30f))
    }

    @Test
    fun `the default is the engine operating point`() {
        assertEquals(0.80f, FaceLimits.DEFAULT.match, 0f)
        assertEquals(0.50f, FaceLimits.DEFAULT.liveness, 0f)
    }
}
