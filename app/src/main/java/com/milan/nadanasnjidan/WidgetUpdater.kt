package com.milan.nadanasnjidan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

object WidgetUpdater {

    /** Najviše slika u widgetu (zbog memorije početnog ekrana). */
    private const val MAX_WIDGET_PHOTOS = 60

    /** Poziva se iz aktivnosti: pokreće osvežavanje bez blokiranja UI niti. */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        Thread { updateAll(app) }.start()
    }

    /**
     * Današnje slike za widget, izmešane. Redosled je isti tokom celog dana
     * (seme je datum), a sutra je drugačiji.
     */
    fun widgetMemories(context: Context): List<Memory> {
        val seed = SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time).toInt()
        return PhotoRepository.findForToday(context)
            .shuffled(Random(seed))
            .take(MAX_WIDGET_PHOTOS)
    }

    /** Osvežava sve widgete (okvir + listu slika). */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, OnThisDayWidget::class.java))
        if (ids.isEmpty()) return

        for (id in ids) {
            manager.updateAppWidget(id, buildViews(context, id))
        }
        // Natera flipper da ponovo učita današnje slike
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetFlipper)
    }

    @Suppress("DEPRECATION")
    private fun buildViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_on_this_day)

        // Izvor slika za flipper (data je jedinstven po widgetu)
        val serviceIntent = Intent(context, WidgetPhotosService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widgetFlipper, serviceIntent)
        views.setEmptyView(R.id.widgetFlipper, R.id.widgetMessage)

        val message = if (PhotoRepository.hasPermission(context)) {
            R.string.widget_no_memories
        } else {
            R.string.widget_need_permission
        }
        views.setTextViewText(R.id.widgetMessage, context.getString(message))

        // Tap na sliku: otvara preglednik; koja slika, dopunjava svaka stavka (fill-in intent)
        val viewerTemplate = PendingIntent.getActivity(
            context, widgetId,
            Intent(context, ViewerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setPendingIntentTemplate(R.id.widgetFlipper, viewerTemplate)

        // Tap na poruku (nema slika / nema dozvole) otvara aplikaciju
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widgetMessage, openApp)
        return views
    }
}
