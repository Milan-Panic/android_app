package com.milan.nadanasnjidan

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** Widget za početni ekran. Sav posao radi [WidgetUpdater] u pozadinskoj niti. */
class OnThisDayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                WidgetUpdater.updateAll(context)
                MidnightScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onEnabled(context: Context) {
        MidnightScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        // Uklonjen je poslednji widget — nema potrebe za ponoćnim alarmom
        MidnightScheduler.cancel(context)
    }
}
