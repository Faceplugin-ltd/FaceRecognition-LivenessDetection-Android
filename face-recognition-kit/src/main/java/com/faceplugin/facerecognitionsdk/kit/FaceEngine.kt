package com.faceplugin.facerecognitionsdk.kit

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Process-wide engine owner: activation + init on a background thread, a lock that serializes
 * every SDK call, and a small state observable for the UI.
 */
object FaceEngine {
    enum class State { LOADING, READY, NOT_ACTIVATED, LICENSE_INVALID, LICENSE_EXPIRED, FAILED, SDK_MISSING }

    val impl: IdentityEngine by lazy { EngineFactory.create() }

    /** All SDK calls go through this lock (camera analyzer, identify, enroll, import). */
    val lock = Any()

    /** Background worker for identify / enroll / import. */
    val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "face-identity-worker").apply { isDaemon = true }
    }

    @Volatile
    var state: State = State.LOADING
        private set

    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    val isReady: Boolean get() = state == State.READY

    fun addListener(l: (State) -> Unit) { listeners += l }
    fun removeListener(l: (State) -> Unit) { listeners -= l }

    private fun publish(s: State) {
        state = s
        main.post { listeners.forEach { it(s) } }
    }

    /**
     * Activate with [license], then init. Call once per process, e.g. from Application.onCreate;
     * the work runs on [worker] and the result arrives on [onDone] and through [addListener].
     */
    @JvmStatic
    @JvmOverloads
    fun start(context: Context, license: String, onDone: ((Int) -> Unit)? = null) {
        val app = context.applicationContext
        if (!impl.available) {
            publish(State.SDK_MISSING)
            onDone?.let { cb -> main.post { cb(IdentityEngine.SDK_MISSING) } }
            return
        }
        publish(State.LOADING)
        worker.execute {
            val code = synchronized(lock) {
                try {
                    impl.machineCode(app)
                    var ret = impl.activate(app, license)
                    if (ret == IdentityEngine.SUCCESS) ret = impl.init(app)
                    ret
                } catch (_: Throwable) {
                    IdentityEngine.INIT_FAILED
                }
            }
            publish(
                when (code) {
                    IdentityEngine.SUCCESS -> State.READY
                    IdentityEngine.NOT_ACTIVATED -> State.NOT_ACTIVATED
                    IdentityEngine.LICENSE_INVALID -> State.LICENSE_INVALID
                    IdentityEngine.LICENSE_EXPIRED -> State.LICENSE_EXPIRED
                    IdentityEngine.SDK_MISSING -> State.SDK_MISSING
                    else -> State.FAILED
                }
            )
            onDone?.let { cb -> main.post { cb(code) } }
        }
    }

    fun machineCode(context: Context): String = try {
        synchronized(lock) { impl.machineCode(context.applicationContext) }
    } catch (_: Throwable) {
        ""
    }

    fun licenseStatus(): LicenseStatus = try {
        LicenseStatus.fromJson(synchronized(lock) { impl.licenseStatusJson() })
    } catch (_: Throwable) {
        LicenseStatus.fromJson(null)
    }

    fun stateMessage(s: State = state): String = when (s) {
        State.LOADING -> "Loading engine"
        State.READY -> "Ready"
        State.NOT_ACTIVATED -> "Not activated"
        State.LICENSE_INVALID -> "License key is invalid"
        State.LICENSE_EXPIRED -> "License expired"
        State.FAILED -> "Engine failed to start"
        State.SDK_MISSING -> "Recognition SDK missing"
    }
}
