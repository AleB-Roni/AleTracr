package com.aleb.aletracr.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aleb.aletracr.ui.theme.AleTracrTheme

/** Shown by Health Connect when the user asks why AleTracr wants access. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AleTracrTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Warum AleTracr Health Connect nutzt", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "AleTracr liest nur deine Schritte und aktiven Kalorien von heute, " +
                                "um sie deinem Kalorienbudget gutzuschreiben. Die Daten bleiben auf deinem Gerät " +
                                "und werden nicht weitergegeben oder geschrieben."
                        )
                        Button(onClick = { finish() }) { Text("Verstanden") }
                    }
                }
            }
        }
    }
}
