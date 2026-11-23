package com.faceplugin.facerecognitionsdk.shell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.shell.data.AppSettings
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.faceplugin.facerecognitionsdk.kit.LicenseStatus
import com.google.android.material.slider.Slider

/** Match, liveness and pose thresholds. Reset is on the header. */
class SettingsFragment : Fragment() {

    private val onEngine: (FaceEngine.State) -> Unit = { refreshLicense() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bind(view)

        val ctx = requireContext()
        val liveness = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.liveness_switch)
        liveness.isChecked = AppSettings.liveness(ctx)
        liveness.setOnCheckedChangeListener { _, on -> AppSettings.setLiveness(ctx, on) }
        view.findViewById<View>(R.id.liveness_row).setOnClickListener { liveness.toggle() }

        val skipReview = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.skip_review_switch)
        skipReview.isChecked = AppSettings.skipReview(ctx)
        skipReview.setOnCheckedChangeListener { _, on -> AppSettings.setSkipReview(ctx, on) }
        view.findViewById<View>(R.id.skip_review_row).setOnClickListener { skipReview.toggle() }

        view.findViewById<View>(R.id.reset_settings).setOnClickListener {
            AppSettings.reset(ctx)
            bind(view)
            view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.liveness_switch).isChecked = true
        }
        view.findViewById<TextView>(R.id.sdk_version).text = FaceEngine.impl.sdkVersion
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (_: Exception) {
            ""
        }
        view.findViewById<TextView>(R.id.app_version).text = version
        // The company mark closes the screen, now that About is no longer a tab.
        view.findViewById<View>(R.id.company_logo).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.company_website_url))))
        }
    }

    override fun onStart() {
        super.onStart()
        FaceEngine.addListener(onEngine)
        refreshLicense()
    }

    override fun onStop() {
        FaceEngine.removeListener(onEngine)
        super.onStop()
    }

    private fun refreshLicense() {
        val label = view?.findViewById<TextView>(R.id.license_status) ?: return
        FaceEngine.worker.execute {
            val status = FaceEngine.licenseStatus()
            label.post { if (isAdded) label.text = licenseText(status) }
        }
    }

    private fun licenseText(status: LicenseStatus): String {
        val parts = ArrayList<String>()
        parts += status.label.ifBlank { if (status.licensed) "Licensed" else "Not licensed" }
        if (status.licensed && status.levelName.isNotBlank() && !status.levelName.equals("None", ignoreCase = true)) {
            parts += status.levelName
        }
        if (status.recognition) parts += "Recognition"
        if (status.liveness) parts += "Liveness"
        return getString(R.string.about_license_fmt, parts.joinToString(" · "))
    }

    private fun bind(view: View) {
        val ctx = requireContext()
        bindUnit(view, R.id.threshold_slider, R.id.threshold_value, AppSettings.threshold(ctx), AppSettings.MIN_THRESHOLD, AppSettings.MAX_THRESHOLD) {
            AppSettings.setThreshold(ctx, it)
        }
        bindUnit(view, R.id.liveness_slider, R.id.liveness_value, AppSettings.livenessThreshold(ctx), 0f, 1f) {
            AppSettings.setLivenessThreshold(ctx, it)
        }
        bindAngle(view, R.id.yaw_slider, R.id.yaw_value, AppSettings.yaw(ctx)) { AppSettings.setYaw(ctx, it) }
        bindAngle(view, R.id.roll_slider, R.id.roll_value, AppSettings.roll(ctx)) { AppSettings.setRoll(ctx, it) }
        bindAngle(view, R.id.pitch_slider, R.id.pitch_value, AppSettings.pitch(ctx)) { AppSettings.setPitch(ctx, it) }
    }

    private fun bindUnit(
        view: View,
        sliderId: Int,
        valueId: Int,
        current: Float,
        from: Float,
        to: Float,
        onChange: (Float) -> Unit,
    ) {
        val slider = view.findViewById<Slider>(sliderId)
        val label = view.findViewById<TextView>(valueId)
        slider.valueFrom = from
        slider.valueTo = to
        slider.stepSize = 0.01f
        slider.clearOnChangeListeners()
        slider.value = current.coerceIn(from, to)
        label.text = Ui.score(slider.value)
        slider.addOnChangeListener { _, v, _ ->
            label.text = Ui.score(v)
            onChange(v)
        }
    }

    private fun bindAngle(view: View, sliderId: Int, valueId: Int, current: Float, onChange: (Float) -> Unit) {
        val slider = view.findViewById<Slider>(sliderId)
        val label = view.findViewById<TextView>(valueId)
        slider.valueFrom = 0f
        slider.valueTo = 90f
        slider.stepSize = 1f
        slider.clearOnChangeListeners()
        slider.value = current.coerceIn(0f, 90f)
        label.text = slider.value.toInt().toString()
        slider.addOnChangeListener { _, v, _ ->
            label.text = v.toInt().toString()
            onChange(v)
        }
    }
}
