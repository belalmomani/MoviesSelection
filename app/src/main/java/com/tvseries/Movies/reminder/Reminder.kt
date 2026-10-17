package com.tvseries.Movies.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.tvseries.Movies.AppContainer
import com.tvseries.Movies.MainActivity
import com.tvseries.Movies.R
import com.tvseries.Movies.data.Prefs
import com.tvseries.Movies.data.SharedPrefsStore
import java.util.Calendar

/** Optional weekly reminder (off by default). Local only: no server, no account. */
object ReminderScheduler {
    private const val REQUEST = 4401

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, REQUEST, Intent(ctx, WeeklyReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC, cal.timeInMillis, AlarmManager.INTERVAL_DAY * 7, pending(ctx))
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
    }
}

private fun prefsOf(ctx: Context) =
    Prefs(SharedPrefsStore(ctx.getSharedPreferences(AppContainer.PREFS_NAME, Context.MODE_PRIVATE)))

class WeeklyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = prefsOf(ctx)
        if (!prefs.weeklyReminder.value) return
        val watched = prefs.watched.value
        val left = prefs.watchlist.value.count { it.key !in watched }
        if (left == 0) return // nothing to remind about -> stay silent
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val ar = prefs.language.value == "ar"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, if (ar) "تذكير أسبوعي" else "Weekly reminder", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("pick", true)
        }
        val pi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (ar) {
            (if (left == 1) "لديك عمل واحد" else "لديك $left أعمال") + " في قائمة «شاهد لاحقًا» لم تشاهدها بعد. هل نختار لك واحدًا منها اليوم؟"
        } else {
            "You have $left unwatched title" + (if (left == 1) "" else "s") + " in your watch list. Shall we pick one for you today?"
        }
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Movies Selection")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
    }

    companion object {
        const val CHANNEL = "weekly_pick"
    }
}

/** Alarms are cleared on reboot, so re-schedule when the user had the reminder enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && prefsOf(ctx).weeklyReminder.value) ReminderScheduler.schedule(ctx)
    }
}
