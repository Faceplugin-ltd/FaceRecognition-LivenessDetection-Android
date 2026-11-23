package com.faceplugin.facerecognitionsdk.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A report is stored with every enrolled person, so it has to survive a round trip as JSON. */
class CallReportTest {

    private val report = CallReport(
        function = "enroll",
        steps = listOf(
            CallStep("faceDetection", 12.5, "1 face"),
            CallStep("templateExtraction", 30.0, "1040 bytes"),
            CallStep("duplicateCheck", 2.25, "gallery empty", ok = false),
        ),
        progress = "3 of 3 · saved",
        detail = "Saved Mina as ID 7",
        success = true,
        finishedAt = 1_700_000_000_000L,
    )

    @Test
    fun `total time is the sum of the steps`() {
        assertEquals(44.75, report.totalMs, 1e-9)
    }

    @Test
    fun `a report survives a round trip`() {
        val back = CallReport.fromJson(report.toJson())!!
        assertEquals(report.function, back.function)
        assertEquals(report.progress, back.progress)
        assertEquals(report.detail, back.detail)
        assertEquals(report.success, back.success)
        assertEquals(report.finishedAt, back.finishedAt)
        assertEquals(report.steps.size, back.steps.size)
    }

    @Test
    fun `a failed step stays failed`() {
        val back = CallReport.fromJson(report.toJson())!!
        assertTrue(back.steps[0].ok)
        assertEquals(false, back.steps[2].ok)
        assertEquals("gallery empty", back.steps[2].detail)
    }

    @Test
    fun `people enrolled before reports were stored read back as none`() {
        assertNull(CallReport.fromJson(null))
        assertNull(CallReport.fromJson(""))
        assertNull(CallReport.fromJson("not json"))
    }
}
