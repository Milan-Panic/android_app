package com.milan.nadanasnjidan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Mreža slika grupisanih po godinama, sa naslovom "2023 · Pre 3 godine". */
class MemoriesAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onClick: (Memory) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class Header(val year: Int, val count: Int) : Item()
        data class Photo(val memory: Memory) : Item()
    }

    private var items: List<Item> = emptyList()
    private val thumbSize = (context.resources.displayMetrics.widthPixels / 3).coerceAtMost(512)

    // Keš umanjenih slika: 1/8 dostupne memorije
    private val cache = object : LruCache<Uri, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: Uri, value: Bitmap) = value.byteCount / 1024
    }

    fun submit(memories: List<Memory>) {
        val list = ArrayList<Item>()
        memories.groupBy { it.year }.toSortedMap(reverseOrder()).forEach { (year, photos) ->
            list += Item.Header(year, photos.size)
            photos.forEach { list += Item.Photo(it) }
        }
        items = list
        notifyDataSetChanged()
    }

    fun isHeader(position: Int) = items.getOrNull(position) is Item.Header

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = if (isHeader(position)) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderVH(inflater.inflate(R.layout.item_year_header, parent, false))
        } else {
            PhotoVH(inflater.inflate(R.layout.item_photo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderVH).bind(item)
            is Item.Photo -> (holder as PhotoVH).bind(item.memory)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? PhotoVH)?.job?.cancel()
    }

    private class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val year: TextView = view.findViewById(R.id.yearText)
        private val ago: TextView = view.findViewById(R.id.agoText)
        fun bind(h: Item.Header) {
            val yearsAgo = Calendar.getInstance().get(Calendar.YEAR) - h.year
            year.text = h.year.toString()
            ago.text = "${PhotoRepository.yearsAgoText(yearsAgo)} · ${h.count}"
        }
    }

    private inner class PhotoVH(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.photo)
        var job: Job? = null

        fun bind(memory: Memory) {
            itemView.setOnClickListener { onClick(memory) }
            job?.cancel()
            val cached = cache.get(memory.uri)
            if (cached != null) {
                image.setImageBitmap(cached)
                return
            }
            image.setImageDrawable(null)
            job = scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    PhotoRepository.loadThumbnail(context, memory.uri, thumbSize)
                }
                if (bmp != null) {
                    cache.put(memory.uri, bmp)
                    image.setImageBitmap(bmp)
                }
            }
        }
    }
}
