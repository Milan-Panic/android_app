package com.milan.nadanasnjidan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

object WidgetUpdater {

    private const val PREFS = "widget_state"
    private const val KEY_DATE = "date"
    private const val KEY_INDEX = "index"

    /** Poziva se iz aktivnosti: pokreće osvežavanje bez blokiranja UI niti. */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        Thread { updateAll(app) }.start()
    }

    /**
     * Osvežava sve widgete. Mora se zvati van glavne niti (radi upit nad galerijom).
     * @param advance true = prikaži sledeću sliku od današnjih.
     */
    fun updateAll(context: Context, advance: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, OnThisDayWidget::class.java))
        if (ids.isEmpty()) return

        val views = buildViews(context, advance)
        manager.updateAppWidget(ids, views)
    }

    private fun buildViews(context: Context, advance: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_on_this_day)

        // Tap na widget otvara aplikaciju
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openApp)

        // Dugme "sledeća"
        val next = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, OnThisDayWidget::class.java).setAction(OnThisDayWidget.ACTION_NEXT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widgetNext, next)

        if (!PhotoRepository.hasPermission(context)) {
            showMessage(views, context.getString(R.string.widget_need_permission))
            return views
        }

        val memories = PhotoRepository.findForToday(context)
        if (memories.isEmpty()) {
            showMessage(views, context.getString(R.string.widget_no_memories))
            return views
        }

        // Svakog novog dana krećemo od nasumične slike; "sledeća" ide redom.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        var index = if (prefs.getString(KEY_DATE, null) == todayKey) {
            prefs.getInt(KEY_INDEX, 0)
        } else {
            Random.nextInt(memories.size)
        }
        if (advance) index++
        index = ((index % memories.size) + memories.size) % memories.size
        prefs.edit().putString(KEY_DATE, todayKey).putInt(KEY_INDEX, index).apply()

        val memory = memories[index]
        // 640px je dovoljno oštro za widget, a ostaje ispod ograničenja veličine za RemoteViews
        val bitmap = PhotoRepository.loadThumbnail(context, memory.uri, 640)
        if (bitmap == null) {
            showMessage(views, context.getString(R.string.widget_no_memories))
            return views
        }

        val yearsAgo = Calendar.getInstance().get(Calendar.YEAR) - memory.year
        views.setViewVisibility(R.id.widgetImage, View.VISIBLE)
        views.setViewVisibility(R.id.widgetCaption, View.VISIBLE)
        views.setViewVisibility(R.id.widgetMessage, View.GONE)
        views.setViewVisibility(R.id.widgetNext, if (memories.size > 1) View.VISIBLE else View.GONE)
        views.setImageViewBitmap(R.id.widgetImage, bitmap)
        views.setTextViewText(R.id.widgetTitle, PhotoRepository.yearsAgoText(yearsAgo))
        views.setTextViewText(
            R.id.widgetSubtitle,
            "${memory.year} · ${index + 1}/${memories.size}"
        )
        return views
    }

    private fun showMessage(views: RemoteViews, text: String) {
        views.setViewVisibility(R.id.widgetImage, View.GONE)
        views.setViewVisibility(R.id.widgetCaption, View.GONE)
        views.setViewVisibility(R.id.widgetNext, View.GONE)
        views.setViewVisibility(R.id.widgetMessage, View.VISIBLE)
        views.setTextViewText(R.id.widgetMessage, text)
    }
}
