package com.faceplugin.facerecognitionsdk.shell

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.faceplugin.facerecognitionsdk.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Bottom tabs: Identity · People · Attribute · Liveness · Settings. */
class MainActivity : AppCompatActivity() {

    private val tags = mapOf(
        R.id.nav_identify to "identify",
        R.id.nav_people to "people",
        R.id.nav_attribute to "attribute",
        R.id.nav_liveness to "liveness",
        R.id.nav_settings to "settings",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { show(it.itemId); true }
        if (savedInstanceState == null) {
            show(R.id.nav_identify)
        }
    }

    fun selectTab(itemId: Int) {
        findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = itemId
    }

    private fun create(itemId: Int): Fragment = when (itemId) {
        R.id.nav_people -> PeopleFragment()
        R.id.nav_attribute -> IdentifyFragment.attribute()
        R.id.nav_liveness -> LivenessFragment()
        R.id.nav_settings -> SettingsFragment()
        else -> IdentifyFragment()
    }

    private fun show(itemId: Int) {
        val fm = supportFragmentManager
        // Hide first, then show. Identity and Attribute both own the camera, and a hide
        // that runs after the next tab has bound calls unbindAll and clears that preview.
        val tx = fm.beginTransaction().setReorderingAllowed(false)
        tags.forEach { (id, tag) ->
            if (id == itemId) return@forEach
            val f = fm.findFragmentByTag(tag)
            if (f != null) tx.hide(f)
        }
        val tag = tags.getValue(itemId)
        val current = fm.findFragmentByTag(tag)
        if (current == null) tx.add(R.id.fragment_container, create(itemId), tag) else tx.show(current)
        tx.commitNow()
    }
}
