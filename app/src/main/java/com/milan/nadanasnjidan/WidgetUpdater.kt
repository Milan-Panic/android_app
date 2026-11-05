package com.milan.nadanasnjidan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

object WidgetUpdater {

    /** Najviše slika u widgetu: sve slike idu u jednu poruku početnom ekranu, pa ograničavamo. */
    private const val MAX_WIDGET_PHOTOS = 20

    /** Stranica umanjene slike u widgetu (px). */
    private const val THUMB_SIZE = 400

    /** Poziva se iz aktivnosti: pokreće osvežavanje bez blokiranja UI niti. */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        Thread { updateAll(app) }.start()
    }

    /**
     * Današnje slike za widget, izmešane. Redosled je isti tokom celog dana
     * (seme je datum), a sutra je drugačiji.
     */
    private fun widgetMemories(context: Context): List<Memory> {
        val seed = SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time).toInt()
        return PhotoRepository.findForToday(context)
            .shuffled(Random(seed))
            .take(MAX_WIDGET_PHOTOS)
    }

    /** Osvežava sve widgete. Mora se zvati van glavne niti (učitava slike). */
    @Synchronized
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, OnThisDayWidget::class.java))
        if (ids.isEmpty()) return

        val hasPermission = PhotoRepository.hasPermission(context)
        val memories = if (hasPermission) widgetMemories(context) else emptyList()

        // Učitaj umanjene slike jednom, za sve widgete
        val day = System.currentTimeMillis()
        // Android ograničava ukupnu memoriju slika u widgetu (~ekran × 6 bajtova);
        // koristimo najviše trećinu toga, da bude sigurno.
        val dm = context.resources.displayMetrics
        val budget = dm.widthPixels.toLong() * dm.heightPixels * 6 / 3
        var used = 0L
        val thumbs = ArrayList<Pair<Memory, Bitmap>>()
        for (m in memories) {
            val bmp = PhotoRepository.loadThumbnail(context, m.uri, THUMB_SIZE)?.let(::shrink) ?: continue
            if (used + bmp.byteCount > budget) break
            used += bmp.byteCount
            thumbs += m to bmp
        }

        for (id in ids) {
            manager.updateAppWidget(id, buildViews(context, id, thumbs, hasPermission, day))
        }
    }

    private fun buildViews(
        context: Context,
        widgetId: Int,
        thumbs: List<Pair<Memory, Bitmap>>,
        hasPermission: Boolean,
        day: Long
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_on_this_day)
        views.removeAllViews(R.id.widgetFlipper)

        if (thumbs.isEmpty()) {
            val message = if (hasPermission) R.string.widget_no_memories else R.string.widget_need_permission
            views.setTextViewText(R.id.widgetMessage, context.getString(message))
            views.setViewVisibility(R.id.widgetMessage, View.VISIBLE)
            views.setViewVisibility(R.id.widgetFlipper, View.GONE)
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetMessage, openApp)
            return views
        }

        views.setViewVisibility(R.id.widgetMessage, View.GONE)
        views.setViewVisibility(R.id.widgetFlipper, View.VISIBLE)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        thumbs.forEachIndexed { index, (memory, bitmap) ->
            val item = RemoteViews(context.packageName, R.layout.widget_item)
            item.setImageViewBitmap(R.id.widgetImage, bitmap)
            item.setTextViewText(R.id.widgetTitle, PhotoRepository.yearsAgoText(currentYear - memory.year))
            item.setTextViewText(R.id.widgetSubtitle, "${memory.year} · ${index + 1}/${thumbs.size}")

            // Direktan klik baš na ovu sliku. Svaka slika ima svoj PendingIntent
            // (jedinstven requestCode + data), da se ne bi međusobno prepisivali.
            val open = Intent(context, ViewerActivity::class.java).apply {
                data = memory.uri
                putExtra(ViewerActivity.EXTRA_URI, memory.uri.toString())
                putExtra(ViewerActivity.EXTRA_DAY, day)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            val pi = PendingIntent.getActivity(
                context, widgetId * 1000 + index, open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            item.setOnClickPendingIntent(R.id.widgetItemRoot, pi)

            views.addView(R.id.widgetFlipper, item)
        }
        return views
    }

    /** RGB_565 troši upola manje memorije; za fotografiju u widgetu razlika se ne vidi. */
    private fun shrink(src: Bitmap): Bitmap =
        if (src.config == Bitmap.Config.RGB_565) src
        else src.copy(Bitmap.Config.RGB_565, false)?.also { src.recycle() } ?: src
}
