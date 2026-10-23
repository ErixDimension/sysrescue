package com.terangbulan.sysrescue.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.terangbulan.sysrescue.databinding.ActivityBrowserBinding
import com.google.android.material.tabs.TabLayoutMediator

class BrowserActivity : AppCompatActivity() {
    private lateinit var b: ActivityBrowserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(b.root)
        setTitle("SYSrescue Browser")

        b.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment {
                return BrowserFragment().apply { setParams(position == 1) }
            }
        }
        TabLayoutMediator(b.tabs, b.pager) { tab, pos ->
            tab.text = if (pos == 0) "📱 LOCAL" else "🔧 REMOTE"
        }.attach()
    }
}
