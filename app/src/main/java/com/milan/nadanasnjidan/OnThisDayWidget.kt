package com.milan.nadanasnjidan

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** Widget za početni ekran. Sav posao radi [WidgetUpdater] u pozadinskoj niti. */
class OnThisDayWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            // Korisnik je tapnuo "sledeća" na widgetu
            val pending = goAsync()
            Thread {
                try {
                    WidgetUpdater.updateAll(context, advance = true)
                } finally {
                    pending.finish()
                }
            }.start()
            return
        }
        super.onReceive(context, intent)
    }

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

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle
    ) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onEnabled(context: Context) {
        MidnightScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        // Uklonjen je poslednji widget — nema potrebe za ponoćnim alarmom
        MidnightScheduler.cancel(context)
    }

    companion object {
        const val ACTION_NEXT = "com.milan.nadanasnjidan.ACTION_NEXT"
    }
}
