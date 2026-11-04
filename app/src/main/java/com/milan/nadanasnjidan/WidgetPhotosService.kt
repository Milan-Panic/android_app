package com.milan.nadanasnjidan

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Calendar

/** Daje widgetu listu slika koje flipper smenjuje. */
class WidgetPhotosService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        PhotosFactory(applicationContext)
}

private class PhotosFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<Memory> = emptyList()

    override fun onCreate() {}

    // Poziva se pri dodavanju widgeta i posle ponoći (notifyAppWidgetViewDataChanged)
    override fun onDataSetChanged() {
        items = WidgetUpdater.widgetMemories(context)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount() = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        val memory = items.getOrNull(position) ?: return views

        PhotoRepository.loadThumbnail(context, memory.uri, 640)?.let {
            views.setImageViewBitmap(R.id.widgetImage, it)
        }
        val yearsAgo = Calendar.getInstance().get(Calendar.YEAR) - memory.year
        views.setTextViewText(R.id.widgetTitle, PhotoRepository.yearsAgoText(yearsAgo))
        views.setTextViewText(R.id.widgetSubtitle, "${memory.year} · ${position + 1}/${items.size}")

        // Tap na sliku otvara baš ovu sliku u pregledniku aplikacije
        views.setOnClickFillInIntent(
            R.id.widgetItemRoot,
            Intent().putExtra(ViewerActivity.EXTRA_URI, memory.uri.toString())
        )
        return views
    }

    override fun getLoadingView() = RemoteViews(context.packageName, R.layout.widget_item_loading)
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = true
}
