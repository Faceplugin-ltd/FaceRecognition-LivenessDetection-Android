package com.faceplugin.facerecognitionsdk.shell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.faceplugin.facerecognitionsdk.R

/** Company and product text. License status is not shown; activation is compiled into the app. */
class AboutFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_about, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val openSite = {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.company_website_url))))
        }
        view.findViewById<ImageView>(R.id.imgAboutLogo).setOnClickListener { openSite() }
        view.findViewById<TextView>(R.id.txtAboutWebsite).setOnClickListener { openSite() }
    }
}
