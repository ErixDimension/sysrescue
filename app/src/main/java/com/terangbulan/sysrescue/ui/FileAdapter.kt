package com.terangbulan.sysrescue.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.TextView
import com.terangbulan.sysrescue.data.FileEntry
import com.terangbulan.sysrescue.util.FormatUtil

class FileAdapter(private val onFolderClick: (FileEntry) -> Unit) : BaseAdapter() {

    private var items: List<FileEntry> = emptyList()
    private var isLoading = false

    fun setItems(newItems: List<FileEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setLoading(loading: Boolean) {
        if (isLoading == loading) return
        isLoading = loading
        notifyDataSetChanged()
    }

    override fun getCount(): Int = if (isLoading && items.isEmpty()) 1 else items.size
    override fun getItem(position: Int): FileEntry = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val context = parent.context
        val tv = (convertView as? TextView) ?: TextView(context).apply {
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 52)
            )
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
            gravity = Gravity.CENTER_VERTICAL
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0B0F14"))
        }

        if (isLoading && items.isEmpty()) {
            tv.text = "⏳ Memuat data..."
            tv.setTextColor(Color.GRAY)
            return tv
        }

        tv.setTextColor(Color.WHITE)
        val entry = items[position]
        val icon = if (entry.isDir) "📁 " else "📄 "
        val size = if (entry.isDir) "" else " — ${FormatUtil.formatBytes(entry.size)}"
        tv.text = "$icon${entry.name}$size"
        tv.setOnClickListener {
            if (entry.isDir) onFolderClick(entry)
        }
        return tv
    }

    private fun dp(context: Context, dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}
