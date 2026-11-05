package com.milan.nadanasnjidan

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Prikaz slike preko celog ekrana, unutar same aplikacije.
 * Prevlačenjem levo/desno prelazi se na ostale današnje uspomene.
 */
class ViewerActivity : AppCompatActivity() {

    private lateinit var pager: RecyclerView
    private lateinit var caption: TextView
    private var memories: List<Memory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)
        pager = findViewById(R.id.pager)
        caption = findViewById(R.id.viewerCaption)
        findViewById<View>(R.id.viewerClose).setOnClickListener { finish() }

        val layout = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        pager.layoutManager = layout
        PagerSnapHelper().attachToRecyclerView(pager)
        pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateCaption(layout.findFirstCompletelyVisibleItemPosition())
                }
            }
        })

        val startUri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        // Dan za koji je napravljen spisak (widget može da prikazuje jučerašnji spisak
        // ako ponoćno osvežavanje kasni), da bi se prikazao isti spisak kao na widgetu.
        val day = Calendar.getInstance().apply {
            timeInMillis = intent.getLongExtra(EXTRA_DAY, System.currentTimeMillis())
        }

        lifecycleScope.launch {
            memories = withContext(Dispatchers.IO) {
                val list = PhotoRepository.findForToday(this@ViewerActivity, day)
                // Ako tapnuta slika ipak nije na spisku, prikaži je svejedno (prvu).
                if (startUri != null && list.none { it.uri == startUri }) {
                    listOfNotNull(PhotoRepository.memoryFor(this@ViewerActivity, startUri)) + list
                } else {
                    list
                }
            }
            if (memories.isEmpty()) {
                finish()
                return@launch
            }
            pager.adapter = PageAdapter(this@ViewerActivity, memories, lifecycleScope)
            val start = memories.indexOfFirst { it.uri == startUri }.coerceAtLeast(0)
            pager.scrollToPosition(start)
            updateCaption(start)
        }
    }

    private fun updateCaption(position: Int) {
        val m = memories.getOrNull(position) ?: return
        val yearsAgo = Calendar.getInstance().get(Calendar.YEAR) - m.year
        val fmt = SimpleDateFormat("d. MMMM yyyy. · HH:mm", Locale.forLanguageTag("sr-Latn-RS"))
        caption.text = "${PhotoRepository.yearsAgoText(yearsAgo)}\n${fmt.format(m.takenMillis)}" +
            "   (${position + 1}/${memories.size})"
    }

    private class PageAdapter(
        private val context: Context,
        private val items: List<Memory>,
        private val scope: CoroutineScope
    ) : RecyclerView.Adapter<PageAdapter.VH>() {

        private val maxSide = context.resources.displayMetrics.let {
            maxOf(it.widthPixels, it.heightPixels)
        }
        private val cache = object : LruCache<Uri, Bitmap>((Runtime.getRuntime().maxMemory() / 6 / 1024).toInt()) {
            override fun sizeOf(key: Uri, value: Bitmap) = value.byteCount / 1024
        }

        inner class VH(val image: ImageView) : RecyclerView.ViewHolder(image) {
            var job: Job? = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_viewer_page, parent, false) as ImageView)

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val uri = items[position].uri
            holder.job?.cancel()
            cache.get(uri)?.let { holder.image.setImageBitmap(it); return }
            holder.image.setImageDrawable(null)
            holder.job = scope.launch {
                val bmp = withContext(Dispatchers.IO) { decode(uri) }
                if (bmp != null) {
                    cache.put(uri, bmp)
                    holder.image.setImageBitmap(bmp)
                }
            }
        }

        override fun onViewRecycled(holder: VH) {
            holder.job?.cancel()
        }

        /** Učitava sliku umanjenu na veličinu ekrana (pravilno okrenutu po EXIF-u). */
        private fun decode(uri: Uri): Bitmap? = try {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxSide.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            }
        } catch (e: Exception) {
            PhotoRepository.loadThumbnail(context, uri, maxSide)
        }
    }

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_DAY = "day"

        fun intent(context: Context, uri: Uri) =
            Intent(context, ViewerActivity::class.java).putExtra(EXTRA_URI, uri.toString())
    }
}
