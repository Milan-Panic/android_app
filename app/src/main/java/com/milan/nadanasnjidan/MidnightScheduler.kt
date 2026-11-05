package com.milan.nadanasnjidan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Zakazuje osvežavanje widgeta svake noći malo posle ponoći.
 *
 * Koristi tačan alarm (setExactAndAllowWhileIdle) koji radi i u "Doze" režimu.
 * Ako sistem ne dozvoli tačne alarme, pada na približan (može kasniti par minuta).
 */
object MidnightScheduler {

    const val ACTION_MIDNIGHT = "com.milan.nadanasnjidan.ACTION_MIDNIGHT"

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 100,
            Intent(context, MidnightReceiver::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun schedule(context: Context) {
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)     // 00:01, da datum sigurno bude novi
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }
}

/** Prima ponoćni alarm, restart telefona i promenu vremena/zone. */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                WidgetUpdater.updateAll(context)
                // zakaži sledeću noć, ali samo ako na početnom ekranu postoji bar jedan widget
                val ids = android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(
                    android.content.ComponentName(context, OnThisDayWidget::class.java)
                )
                if (ids.isNotEmpty()) MidnightScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
