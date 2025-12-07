package dev.ashz.notirelay

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

class SendSMSWorker(context: Context, workerParams: WorkerParameters): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val relayPhoneNumber = inputData.getString("RELAY_PHONE_NUMBER")
        val title = inputData.getString("TITLE")
        val body = inputData.getString("BODY")

        if (relayPhoneNumber.isNullOrEmpty() || title.isNullOrEmpty() || body.isNullOrEmpty()) {
            return Result.failure(workDataOf("cause" to "input"))
        }

        return sendSMS(relayPhoneNumber, title, body)
    }

    private suspend fun sendSMS(phoneNumber: String, title: String, body: String): Result =
        suspendCancellableCoroutine { continuation ->
            val action = "dev.ashz.notirelay.worker.${UUID.randomUUID()}"

            val sentReceiver = object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?
                ) {
                    try {
                        // Prevent memory leak
                        applicationContext.unregisterReceiver(this)
                    } catch (_: Exception) {}

                    if (resultCode == Activity.RESULT_OK) {
                        continuation.resume(Result.success())
                    } else {
                        continuation.resume(Result.failure(workDataOf("cause" to "receiver", "result" to resultCode)))
                    }
                }
            }

            val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.RECEIVER_EXPORTED
            } else {
                0
            }
            applicationContext.registerReceiver(sentReceiver, IntentFilter(action), receiverFlags)

            continuation.invokeOnCancellation {
                try {
                    applicationContext.unregisterReceiver(sentReceiver)
                } catch (_: Exception) {}
            }

            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    applicationContext.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val sentIntent = Intent(action)
                val sentPendingIntent = PendingIntent.getBroadcast(applicationContext, 0, sentIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

                smsManager.sendTextMessage(phoneNumber, null, "[NotiRelay]\n$title\n$body", sentPendingIntent, null)
            } catch (_: Exception) {
                try {
                    applicationContext.unregisterReceiver(sentReceiver)
                } catch (_: Exception) {}

                continuation.resume(Result.failure(workDataOf("cause" to "sender")))
            }
        }
}