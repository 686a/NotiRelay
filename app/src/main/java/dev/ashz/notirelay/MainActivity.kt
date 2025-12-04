package dev.ashz.notirelay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices.PIXEL_7
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ashz.notirelay.ui.theme.NotiRelayTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = arrayListOf(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), 46590831)

        if (!NotificationListener.isPermissionGranted(this)) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            try {
                val shadowIntent = intent.clone() as Intent
                shadowIntent.data = "package:$packageName".toUri()
                startActivityForResult(shadowIntent, 458379)
            } catch (_: ActivityNotFoundException) {
                startActivityForResult(intent, 458379)
            }
        }

        val name = "Relay Success"
        val description = "Relay success"
        val importance = NotificationManager.IMPORTANCE_MIN
        val channel = NotificationChannel("NOTIRELAY-RELAY_SUCCESS", name, importance)
        channel.description = description
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        enableEdgeToEdge()
        setContent {
            NotiRelayTheme {
                MainUI()
            }
        }
    }
}

@Composable
fun MainUI() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(innerPadding)

        ) {
            Text(
                text = "NotiRelay",
                style = MaterialTheme.typography.headlineMedium
            )

            RelayPackageSettings()
            PhoneNumberSettings()
        }
    }
}

@Composable
fun RelayPackageSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefKeyTargetPackageName = stringPreferencesKey("target_package_name")
    val savedTargetPackageName by context.dataStore.data
        .map { it[prefKeyTargetPackageName] ?: "" }
        .collectAsState(initial = "")
    var targetPackageName by remember(savedTargetPackageName) { mutableStateOf(savedTargetPackageName) }

    Text(
        text = "Package name to relay"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextField(
            value = targetPackageName,
            onValueChange = { newValue ->
                targetPackageName = newValue
            },
            label = {
                Text("Package name")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone
            )
        )
        Button(onClick = {
            scope.launch {
                context.dataStore.edit { pref ->
                    pref[prefKeyTargetPackageName] = targetPackageName
                }
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Save")
        }
    }
}


@Composable
fun PhoneNumberSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefKeyRelayPhoneNumber = stringPreferencesKey("relay_phone_number")
    val savedRelayPhoneNumber by context.dataStore.data
        .map { it[prefKeyRelayPhoneNumber] ?: "" }
        .collectAsState(initial = "")
    var phoneNumber by remember(savedRelayPhoneNumber) { mutableStateOf(savedRelayPhoneNumber) }

    Text(
        text = "Relay Phone number"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextField(
            value = phoneNumber,
            onValueChange = { newValue ->
                phoneNumber = newValue
            },
            label = {
                Text("Phone number")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone
            )
        )
        Button(onClick = {
            scope.launch {
                context.dataStore.edit { pref ->
                    pref[prefKeyRelayPhoneNumber] = phoneNumber
                }
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Save")
        }
    }
}

@Preview(device = PIXEL_7, showSystemUi = true)
@Composable
fun GreetingPreview() {
    NotiRelayTheme {
        MainUI()
    }
}