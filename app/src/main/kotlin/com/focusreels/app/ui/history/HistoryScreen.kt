package com.focusreels.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusreels.app.data.repository.HistoryRepository
import java.text.DateFormat
import java.util.Date

/** Historique illimité des tentatives bloquées, avec horodatage (§3.6). */
@Composable
fun HistoryScreen(
    packageName: String,
    repository: HistoryRepository,
    onBack: () -> Unit
) {
    val history by repository.observeHistory(packageName).collectAsStateWithLifecycle(initialValue = emptyList())
    val formatter = remember { DateFormat.getDateTimeInstance() }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Historique des tentatives bloquées")

            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(history) { attempt ->
                    Text(formatter.format(Date(attempt.timestampMillis)))
                }
            }

            Button(onClick = onBack) { Text("Retour") }
        }
    }
}
