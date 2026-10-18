package com.example.pokemonalertsv2.catchroutes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.pokemonalertsv2.R

/** The "Route finished" card: the walk's numbers, opening its summary. */
internal object CatchRouteNotifications {
    private const val CHANNEL = "catch_routes_finished"
    internal const val FINISHED_ID = 7351
    internal const val EXTRA_SUMMARY_ID = "catch_route_summary_id"

    fun postFinished(context: Context, summary: CatchRouteSummary) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Finished catch routes", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 72,
            Intent(context, CatchRoutesActivity::class.java).putExtra(EXTRA_SUMMARY_ID, summary.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val detail = "${summary.headline()} · ${formatCatchSpeed(summary.averageKmh)}"
        manager.notify(FINISHED_ID, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Route finished: ${summary.name}")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\n${summary.missed} not reached of ${summary.planned} planned"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}
