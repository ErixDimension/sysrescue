package com.terangbulan.sysrescue.ui

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.util.FormatUtil

class FileAdapter(private val onFolderClick: (FileEntry) -> Unit) : ListAdapter<FileEntry, FileAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FileEntry>() {
            override fun areItemsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem.name == newItem.name
            override fun areContentsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean = oldItem == newItem
        }
    }

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // 🔥 BUAT TEXTVIEW MURNI DARI KODE KOTLIN (TANPA XML)
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                140 // Tinggi baris diperbesar agar mudah disentuh
            )
            setPadding(32, 0, 32, 0)
            gravity = Gravity.CENTER_VERTICAL
            textSize = 16f
            setTextColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            
            // Efek klik sederhana
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        val icon = if (entry.isDir) "📁 " else "📄 "
        val size = if (entry.isDir) "" else " — ${FormatUtil.formatBytes(entry.size)}"
        
        holder.tv.text = "$icon${entry.name}$size"
        
        holder.tv.setOnClickListener {
            if (entry.isDir) onFolderClick(entry)
        }
    }
}
