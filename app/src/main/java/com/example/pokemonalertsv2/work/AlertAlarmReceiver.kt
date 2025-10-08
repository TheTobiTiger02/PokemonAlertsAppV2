package com.example.pokemonalertsv2.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlertAlarmReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent?) {
		if (intent?.action != AlertAlarmScheduler.ACTION_POLL_ALERTS) {
			Log.w(TAG, "Ignoring broadcast with unexpected action: ${intent?.action}")
			return
		}

		Log.d(TAG, "Exact alarm fired; enqueueing immediate alert sync")
		AlertWorker.triggerImmediateSync(context)
		AlertAlarmScheduler.onWorkFinished(context)
	}

	companion object {
		private const val TAG = "AlertAlarmReceiver"
	}
}
