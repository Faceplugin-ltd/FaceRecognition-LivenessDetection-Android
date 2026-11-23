package com.faceplugin.facerecognitionsdk.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.faceplugin.facerecognitionsdk.kit.DetectedFace
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList

/** One timed SDK call. [detail] is the short result of that call; [ok] is false when this step stopped the action. */
data class CallStep(val name: String, val ms: Double, val detail: String, val ok: Boolean = true)

/** Shown after a customer action finishes: total time, each call, progress, detail and overall outcome. */
data class CallReport(
    val function: String,
    val steps: List<CallStep>,
    val progress: String,
    val detail: String,
    val success: Boolean = true,
    /** Wall-clock time the action finished (epoch ms). */
    val finishedAt: Long = System.currentTimeMillis(),
) {
    val totalMs: Double get() = steps.sumOf { it.ms }

    fun toJson(): String = JSONObject().apply {
        put("function", function); put("progress", progress); put("detail", detail)
        put("success", success); put("finishedAt", finishedAt)
        put("steps", JSONArray().apply {
            steps.forEach {
                put(JSONObject().put("name", it.name).put("ms", it.ms).put("detail", it.detail).put("ok", it.ok))
            }
        })
    }.toString()

    companion object {
        fun fromJson(raw: String?): CallReport? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                val arr = o.optJSONArray("steps") ?: JSONArray()
                CallReport(
                    o.optString("function"),
                    (0 until arr.length()).map {
                        val st = arr.getJSONObject(it)
                        CallStep(st.optString("name"), st.optDouble("ms", 0.0), st.optString("detail"), st.optBoolean("ok", true))
                    },
                    o.optString("progress"),
                    o.optString("detail"),
                    o.optBoolean("success", true),
                    o.optLong("finishedAt", 0L),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** One gallery hit for a probe face. */
data class Candidate(val id: Long, val name: String, val note: String, val similarity: Float)

data class ProbeFace(
    val index: Int,
    val box: Rect,
    /** Top 100 by similarity, highest first. */
    val ranked: List<Candidate>,
) {
    val best: Candidate? get() = ranked.firstOrNull()
    fun matches(threshold: Float) = ranked.filter { it.similarity >= threshold }
    fun matched(threshold: Float) = (best?.similarity ?: -1f) >= threshold
}

data class IdentifyResult(
    val probe: Bitmap,
    val faces: List<ProbeFace>,
    val threshold: Float,
    val extractMs: Double,
    val searchMs: Double,
    val gallerySize: Int,
    val report: CallReport,
) {
    /** The face whose best candidate scores highest (drives the headline and Rank 1 tile). */
    val primary: ProbeFace get() = faces.maxByOrNull { it.best?.similarity ?: -1f } ?: faces.first()
    val matched: Boolean get() = faces.any { it.matched(threshold) }

    /** Matches at/above the threshold across all probe faces, best score per person, highest first. */
    val matches: List<Candidate>
        get() = faces.flatMap { it.matches(threshold) }
            .groupBy { it.id }.map { (_, v) -> v.maxBy { it.similarity } }
            .sortedByDescending { it.similarity }
}

sealed class EnrollOutcome {
    /**
     * [duplicates] holds every already-enrolled person scoring at/above the identify
     * threshold, highest first — not just the closest. One new face can match several
     * existing ones, and hiding the rest makes the gallery look cleaner than it is.
     */
    data class Enrolled(
        val record: FaceRecord,
        val report: CallReport,
        val duplicates: List<Candidate> = emptyList(),
    ) : EnrollOutcome() {
        /** The strongest match, for callers that only show one. */
        val duplicate: Candidate? get() = duplicates.firstOrNull()
    }

    data class Rejected(val reason: String, val report: CallReport? = null) : EnrollOutcome()
}

/** One detected face on the Attribute page. [closest] is the best gallery hit (null when the gallery is empty or no template). */
data class FaceAttributes(
    val index: Int,
    val box: com.faceplugin.facerecognitionsdk.kit.FaceBox,
    val templateBytes: Int,
    val closest: Candidate?,
)

data class AttributeResult(
    val probe: Bitmap,
    val report: CallReport,
    /** Every detected face, largest first. Empty when detection found nobody. */
    val faces: List<FaceAttributes> = emptyList(),
    val threshold: Float = 0f,
    val gallerySize: Int = 0,
)

sealed class IdentifyOutcome {
    data class Done(val result: IdentifyResult) : IdentifyOutcome()
    data class Rejected(val reason: String, val report: CallReport? = null) : IdentifyOutcome()
}

/**
 * On-device gallery (spec §3). Every engine call is serialized through [FaceEngine.lock];
 * call these from a background thread.
 */
class FaceGallery private constructor(context: Context) {
    private val app = context.applicationContext
    private val db = GalleryDb(app)
    private val imagesDir = File(app.filesDir, "gallery").apply { mkdirs() }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    private var cache: List<GalleryDb.Template>? = null

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    private fun changed() {
        cache = null
        main.post { listeners.forEach { it() } }
    }

    private fun templates(): List<GalleryDb.Template> =
        cache ?: synchronized(this) { cache ?: db.allTemplates().also { cache = it } }

    // ---- read ----

    fun count(query: String = ""): Int = db.count(query)
    fun get(id: Long): FaceRecord? = db.get(id)
    fun page(query: String, limit: Int, offset: Int): List<FaceRecord> = db.page(query, limit, offset)

    // ---- enroll ----

    /**
     * Exactly-one-face enrollment. [photo] must be upright with long side <= 1280.
     * Steps: detection, template, duplicate check (1:N against the gallery), save.
     */
    fun enroll(
        name: String,
        note: String,
        photo: Bitmap,
        limits: FaceLimits = FaceLimits.DEFAULT,
    ): EnrollOutcome {
        val cleanName = name.trim()
        if (!FaceEngine.isReady) return EnrollOutcome.Rejected(FaceEngine.stateMessage())
        val steps = ArrayList<CallStep>()
        var faces = emptyList<DetectedFace>()
        var feature: ByteArray? = null
        synchronized(FaceEngine.lock) {
            FaceEngine.impl.use(photo) { frame ->
                val (found, detectStep) = timed("faceDetection", { facesDetail(it.size) }, { it.size == 1 }) {
                    frame.detect(liveness = false, eyes = false)
                }
                steps += detectStep
                faces = found
                if (found.size == 1) {
                    val (bytes, templateStep) = timed(
                        "templateExtraction",
                        { if (it == null || it.isEmpty()) "no template" else "${it.size} bytes" },
                        { it != null && it.isNotEmpty() },
                    ) { frame.template(found[0]) }
                    steps += templateStep
                    feature = bytes
                }
            }
        }
        if (faces.isEmpty()) return EnrollOutcome.Rejected(NO_FACE, report("enroll", steps, "No face", success = false))
        if (faces.size > 1) {
            return EnrollOutcome.Rejected(
                "${faces.size} faces found. Use a photo that shows only one person.",
                report("enroll", steps, "${faces.size} faces", success = false),
            )
        }
        val saved = feature
        if (saved == null || saved.isEmpty()) {
            return EnrollOutcome.Rejected(
                "Could not read this face. Use a clear, front-facing photo.",
                report("enroll", steps, "template failed", success = false),
            )
        }

        val threshold = limits.match
        val gallery = templates()
        val (ranked, dupStep) = timed("duplicateCheck", { list: List<Candidate> ->
            val over = list.filter { it.similarity >= threshold }
            when {
                list.isEmpty() -> "gallery empty"
                over.isEmpty() ->
                    "closest ${list[0].name} · ID ${list[0].id} · ${fmt(list[0].similarity)}"
                else -> over.joinToString(", ") { "${it.name} · ID ${it.id} · ${fmt(it.similarity)}" } +
                    " · possible duplicate" + if (over.size > 1) "s" else ""
            }
        }) {
            synchronized(FaceEngine.lock) {
                gallery.map { t -> Candidate(t.id, t.name, t.note, FaceEngine.impl.similarity(saved, t.feature)) }
            }.sortedByDescending { it.similarity }
        }
        steps += dupStep
        val closest = ranked.firstOrNull()

        val crop = ImageUtils.cropFace(photo, faces[0].box) ?: photo
        val (id, saveStep) = timed("save", { newId: Long -> "ID $newId · ${gallery.size + 1} enrolled" }) {
            val newId = db.insert(cleanName, note.trim(), isoNow(), saved)
            val cropFile = File(imagesDir, "${newId}_crop.jpg")
            val originalFile = File(imagesDir, "${newId}_original.jpg")
            cropFile.writeBytes(ImageUtils.jpeg(crop))
            originalFile.writeBytes(ImageUtils.jpeg(photo, 90))
            db.setPaths(newId, cropFile.absolutePath, originalFile.absolutePath)
            newId
        }
        steps += saveStep
        // A blank name is named after the id it was just given, so the People list and the
        // identify headline never show an empty space where a name belongs.
        val finalName = cleanName.ifEmpty { autoName(id) }
        if (finalName != cleanName) db.update(id, finalName, note.trim())
        val duplicates = ranked.filter { it.similarity >= threshold }
        val report = report(
            "enroll",
            steps,
            if (duplicates.isEmpty()) "Saved $finalName as ID $id"
            else "Saved $finalName as ID $id · looks like " +
                duplicates.joinToString(", ") { "${it.name} (ID ${it.id})" },
            "${steps.size} of ${steps.size} · saved",
        )
        db.setReport(id, report.toJson())
        changed()
        return EnrollOutcome.Enrolled(db.get(id)!!, report, duplicates)
    }

    // ---- identify ----

    fun identify(probe: Bitmap, limits: FaceLimits, livenessOn: Boolean): IdentifyOutcome {
        val threshold = limits.match
        if (!FaceEngine.isReady) return IdentifyOutcome.Rejected(FaceEngine.stateMessage())
        val steps = ArrayList<CallStep>()
        val extracted = ArrayList<Pair<DetectedFace, ByteArray>>()
        var rejected: IdentifyOutcome.Rejected? = null
        synchronized(FaceEngine.lock) {
            FaceEngine.impl.use(probe) { frame ->
                val (faces, detectStep) = timed("faceDetection", { facesDetail(it.size) }, { it.isNotEmpty() }) {
                    frame.detect(livenessOn)
                }
                steps += detectStep
                if (faces.isEmpty()) {
                    rejected = IdentifyOutcome.Rejected(NO_FACE, report("identify", steps, "No face", success = false))
                    return@use
                }
                if (livenessOn) {
                    val limit = limits.liveness
                    val live = faces.count { limits.isLive(it.liveness) }
                    val lowest = faces.minOf { it.liveness }
                    // Liveness is scored during detection; this step applies the threshold.
                    steps += CallStep(
                        "livenessCheck", 0.0,
                        "$live of ${faces.size} live · lowest ${fmt(lowest)} / ${fmt(limit)}",
                        live == faces.size,
                    )
                    if (live < faces.size) {
                        rejected = IdentifyOutcome.Rejected(
                            LIVENESS_FAILED,
                            report("identify", steps, "Liveness failed", success = false),
                        )
                        return@use
                    }
                }
                val (_, templateStep) = timed("templateExtraction", { "${extracted.size} templates" }, { extracted.isNotEmpty() }) {
                    faces.forEach { face ->
                        frame.template(face)?.takeIf { it.isNotEmpty() }?.let { extracted += face to it }
                    }
                }
                steps += templateStep
            }
        }
        rejected?.let { return it }
        if (extracted.isEmpty()) {
            return IdentifyOutcome.Rejected(
                "Could not read this face. Use a clear, front-facing photo.",
                report("identify", steps, "template failed", success = false),
            )
        }

        val gallery = templates()
        val (probeFaces, searchStep) = timed("similarity", { "${gallery.size} enrolled · ${extracted.size} probes" }) {
            extracted.mapIndexed { index, (face, feature) ->
                val scored = synchronized(FaceEngine.lock) {
                    gallery.map { t -> Candidate(t.id, t.name, t.note, FaceEngine.impl.similarity(feature, t.feature)) }
                }
                ProbeFace(index, face.box, scored.sortedByDescending { it.similarity }.take(TOP_K))
            }
        }
        steps += searchStep
        val best = probeFaces.mapNotNull { it.best?.similarity }.maxOrNull()
        val report = report(
            "identify",
            steps,
            if (best == null) "Gallery empty" else "Best similarity ${fmt(best)} · threshold ${fmt(threshold)}",
            "${steps.size} of ${steps.size} · similarity",
        )
        val extractMs = steps.filter { it.name != "similarity" }.sumOf { it.ms }
        val searchMs = steps.filter { it.name == "similarity" }.sumOf { it.ms }
        return IdentifyOutcome.Done(
            IdentifyResult(probe, probeFaces, threshold, extractMs, searchMs, gallery.size, report)
        )
    }

    /**
     * Attribute detection on every face, then a template and a 1:N gallery search per face
     * (up to [MAX_ATTRIBUTE_FACES]), timing each call.
     */
    fun attributes(probe: Bitmap, limits: FaceLimits = FaceLimits.DEFAULT): AttributeResult {
        val steps = ArrayList<CallStep>()
        val threshold = limits.match
        if (!FaceEngine.isReady) {
            return AttributeResult(probe, report("attribute", steps, FaceEngine.stateMessage(), success = false), emptyList(), threshold, 0)
        }
        val gallery = templates()
        val boxes = ArrayList<com.faceplugin.facerecognitionsdk.kit.FaceBox>()
        val features = ArrayList<ByteArray?>()
        synchronized(FaceEngine.lock) {
            FaceEngine.impl.use(probe) { frame ->
                val (found, detectStep) = timed("faceDetection", { "${facesDetail(it.size)} · attributes" }, { it.isNotEmpty() }) {
                    frame.detectAttributes()
                }
                steps += detectStep
                val ordered = found.sortedByDescending { it.box.width() * it.box.height() }
                    .filter { it.handle is com.faceplugin.facerecognitionsdk.FaceRecognitionSDK.Face }
                ordered.forEach { f ->
                    boxes += (f.handle as com.faceplugin.facerecognitionsdk.FaceRecognitionSDK.Face).toFaceBox()
                }
                if (ordered.isNotEmpty()) {
                    val (list, templateStep) = timed(
                        "templateExtraction",
                        { l: List<ByteArray?> -> "${l.count { it != null && it.isNotEmpty() }} of ${l.size} templates" },
                        { l: List<ByteArray?> -> l.any { it != null && it.isNotEmpty() } },
                    ) {
                        ordered.take(MAX_ATTRIBUTE_FACES).map { frame.template(it) }
                    }
                    features += list
                    steps += templateStep
                }
            }
        }
        val closest: List<Candidate?> = if (features.any { it != null && it.isNotEmpty() }) {
            val (hits, simStep) = timed("similarity", { _: List<Candidate?> ->
                "${gallery.size} enrolled · ${features.count { it != null && it.isNotEmpty() }} probes"
            }) {
                features.map { feat ->
                    if (feat == null || feat.isEmpty() || gallery.isEmpty()) null
                    else synchronized(FaceEngine.lock) {
                        gallery.map { t -> Candidate(t.id, t.name, t.note, FaceEngine.impl.similarity(feat, t.feature)) }
                    }.maxByOrNull { it.similarity }
                }
            }
            steps += simStep
            hits
        } else {
            emptyList()
        }
        val faces = boxes.mapIndexed { i, b ->
            FaceAttributes(i, b, features.getOrNull(i)?.size ?: 0, closest.getOrNull(i))
        }
        val bestHit = faces.mapNotNull { it.closest }.maxByOrNull { it.similarity }
        val detail = if (faces.isEmpty()) "No face"
        else facesDetail(faces.size) + (bestHit?.let { " · closest ${it.name} ${fmt(it.similarity)}" } ?: "")
        return AttributeResult(
            probe,
            report("attribute", steps, detail, success = faces.isNotEmpty()),
            faces,
            threshold,
            gallery.size,
        )
    }

    private fun <T> timed(
        name: String,
        detail: (T) -> String,
        ok: (T) -> Boolean = { true },
        block: () -> T,
    ): Pair<T, CallStep> {
        val started = SystemClock.elapsedRealtimeNanos()
        val value = block()
        val ms = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
        return value to CallStep(name, ms, detail(value), ok(value))
    }

    private fun facesDetail(n: Int) = if (n == 1) "1 face" else "$n faces"

    private fun fmt(v: Float) = String.format(Locale.US, "%.2f", v)

    private fun report(
        function: String,
        steps: List<CallStep>,
        detail: String,
        progress: String = "",
        success: Boolean = true,
    ): CallReport {
        val shown = progress.ifBlank {
            if (success) "${steps.size} of ${steps.size} · ${steps.lastOrNull()?.name ?: "done"}"
            else "${steps.count { it.ok }} of ${steps.size} · stopped at ${steps.firstOrNull { !it.ok }?.name ?: "start"}"
        }
        return CallReport(function, steps.toList(), shown, detail, success)
    }


    // ---- edit / delete ----

    fun update(id: Long, name: String, note: String): Boolean =
        db.update(id, name.trim(), note.trim()).also { if (it) changed() }

    fun delete(id: Long): Boolean {
        val rec = db.get(id) ?: return false
        val ok = db.delete(id)
        if (ok) {
            listOf(rec.cropPath, rec.originalPath).filter { it.isNotEmpty() }.forEach { File(it).delete() }
            changed()
        }
        return ok
    }

    /** Removes every record and every stored crop/original. Returns the number of faces deleted. */
    fun deleteAll(): Int {
        val n = db.deleteAll()
        imagesDir.listFiles()?.forEach { it.delete() }
        changed()
        return n
    }

    companion object {
        /** The name a face gets when none was typed: it matches the ID shown beside it. */
        fun autoName(id: Long): String = "Person $id"

        const val TOP_K = 100
        const val MAX_ATTRIBUTE_FACES = 10
        const val NO_FACE = "No face found. Use a clear, front-facing photo."
        /** Rejection reason when a probe fails the liveness threshold. */
        const val LIVENESS_FAILED =
            "Liveness check failed. Use a live face, not a photo or screen."

        @Volatile
        private var instance: FaceGallery? = null

        fun get(context: Context): FaceGallery =
            instance ?: synchronized(this) { instance ?: FaceGallery(context).also { instance = it } }

        fun isoNow(): String {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.format(Date())
        }

        fun parseIso(iso: String): Date? = try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)
        } catch (_: Exception) {
            null
        }
    }
}
