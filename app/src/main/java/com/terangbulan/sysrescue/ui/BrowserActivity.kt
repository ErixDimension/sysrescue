package com.terangbulan.sysrescue.ui

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.terangbulan.sysrescue.R

class BrowserActivity : AppCompatActivity() {
    private var isRemote = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser_simple)

        val btnLocal = findViewById<Button>(R.id.btnLocal)
        val btnRemote = findViewById<Button>(R.id.btnRemote)
        val container = findViewById<FrameLayout>(R.id.fragmentContainer)

        // Muat fragment LOCAL secara default
        loadFragment(false)

        btnLocal.setOnClickListener {
            if (isRemote) {
                isRemote = false
                loadFragment(false)
            }
        }

        btnRemote.setOnClickListener {
            if (!isRemote) {
                isRemote = true
                loadFragment(true)
            }
        }
    }

    private fun loadFragment(remote: Boolean) {
        val fragment = BrowserFragment().apply { setParams(remote) }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
