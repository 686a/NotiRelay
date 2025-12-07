package dev.ashz.notirelay

import android.Manifest
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {
    companion object {
        fun isPermissionGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            var found = false
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":")
                for (name in names) {
                    val componentName = ComponentName.unflattenFromString(name)
                    if (componentName != null) {
                        if (context.packageName == componentName.packageName) {
                            found = true
                            break
                        }
                    }
                }
            }

            return found
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_NOT_STICKY
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?
    ) {
        super.onNotificationPosted(sbn, rankingMap)

        sbn ?: return

        if (sbn.packageName.equals(packageName)) return

        scope.launch {
            val targetPackageName =
                dataStore.data.map { it[stringPreferencesKey("target_package_name")] }.first()

            if (sbn.packageName != targetPackageName) return@launch

            Log.d("NotificationListener", "group:${sbn.notification.group}")
            Log.d("NotificationListener", "channelId:${sbn.notification.channelId}")
            Log.d("NotificationListener", "category:${sbn.notification.category}")
            Log.d("NotificationListener", "settingsText:${sbn.notification.settingsText}")

            val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
            val body = sbn.notification.extras.getString(Notification.EXTRA_TEXT)
            Log.d("NotificationListener", "title:${title}")
            Log.d("NotificationListener", "body:${body}")

            rankingMap ?: return@launch
            if (body.isNullOrEmpty()) return@launch

            var channelName: String? = null
            val ranking = Ranking()
            if (rankingMap.getRanking(sbn.key, ranking)) {
                val channel = ranking.channel
                channelName = channel.name.toString()
                Log.d("NotificationListener", "channel:${channel.name}")
            }

            val packageInfo = packageManager.getApplicationInfo(
                sbn.packageName,
                PackageManager.GET_META_DATA
            )

            val relayPhoneNumber =
                dataStore.data.map { it[stringPreferencesKey("relay_phone_number")] }.first()
            relayPhoneNumber ?: return@launch

            val notificationBuilder =
                NotificationCompat.Builder(this@NotificationListener, "NOTIRELAY-RELAY_SUCCESS")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Relay..")
                    .setContentText("Relayed $channelName from ${packageInfo.name} to $relayPhoneNumber")
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(
                                "Package:${sbn.packageName}\n" +
                                        "App:${packageInfo.name}\n" +
                                        "ChannelID:${sbn.notification.channelId}\n" +
                                        "Category:${sbn.notification.category}\n" +
                                        "Relay to:$relayPhoneNumber\n\n" +
                                        "Title:$title\n" +
                                        body
                            )
                    )

            val smsData = workDataOf(
                "RELAY_PHONE_NUMBER" to relayPhoneNumber,
                "TITLE" to title,
                "BODY" to body,
            )

            val smsWorkRequest = OneTimeWorkRequestBuilder<SendSMSWorker>()
                .setInputData(smsData)
                .build()

            WorkManager.getInstance(this@NotificationListener).enqueue(smsWorkRequest)

            WorkManager.getInstance(this@NotificationListener)
                .getWorkInfoByIdFlow(smsWorkRequest.id)
                .collect { workInfo ->
                    if (workInfo != null && workInfo.state.isFinished) {
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                            with(NotificationManagerCompat.from(this@NotificationListener)) {
                                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    return@with
                                }

                                notificationBuilder.setContentTitle("Relay Success")

                                notify(53426, notificationBuilder.build())
                            }
                        } else {
                            if (workInfo.state == WorkInfo.State.FAILED) {
                                val cause = workInfo.outputData.getString("cause")
                                val result = workInfo.outputData.getInt("result", -1)

                                notificationBuilder
                                    .setContentTitle("Relay Failed")
                                    .setStyle(
                                        NotificationCompat.BigTextStyle()
                                            .bigText(
                                                "Package:${sbn.packageName}\n" +
                                                        "App:${packageInfo.name}\n" +
                                                        "ChannelID:${sbn.notification.channelId}\n" +
                                                        "Category:${sbn.notification.category}\n" +
                                                        "Cause:$cause Result:$result\n" +
                                                        "Relay to:$relayPhoneNumber\n\n" +
                                                        "Title:$title\n" +
                                                        body
                                            )
                                    )
                            }
                        }
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        scope.cancel()
    }
}