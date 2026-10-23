package com.terangbulan.sysrescue.util

import com.terangbulan.sysrescue.data.FileEntry

object FileCache {
    // Menyimpan daftar file berdasarkan path
    private val cache = mutableMapOf<String, List<FileEntry>>()

    fun get(path: String): List<FileEntry>? = cache[path]
    fun put(path: String, items: List<FileEntry>) { cache[path] = items }
    fun clear() { cache.clear() }
}
