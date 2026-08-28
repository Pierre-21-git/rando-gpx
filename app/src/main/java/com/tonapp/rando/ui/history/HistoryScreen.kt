package com.tonapp.rando.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tonapp.rando.R
import com.tonapp.rando.RandoApplication
import com.tonapp.rando.data.Hike
import com.tonapp.rando.ui.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onStartNewHike: () -> Unit,
    onOpenHike: (Long) -> Unit,
    onOpenOfflineAreas: () -> Unit,
    viewModel: HistoryViewModel = viewModel(
        factory = ViewModelFactory.from(
            androidx.compose.ui.platform.LocalContext.current.applicationContext as RandoApplication
        )
    )
) {
    val hikes by viewModel.hikes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResourceCompat(R.string.screen_history)) },
                actions = {
                    IconButton(onClick = onOpenOfflineAreas) {
                        Icon(Icons.Default.Map, contentDescription = "Cartes hors ligne")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onStartNewHike) {
                Icon(Icons.Default.Add, contentDescription = stringResourceCompat(R.string.action_start))
            }
        }
    ) { padding ->
        if (hikes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Aucune randonnée enregistrée pour l'instant.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hikes, key = { it.id }) { hike ->
                    HikeCard(hike = hike, onClick = { onOpenHike(hike.id) })
                }
            }
        }
    }
}

@Composable
private fun HikeCard(hike: Hike, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(hike.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                formatDate(hike.startTime),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("%.1f km".format(hike.distanceMeters / 1000.0))
                Text("+%.0f m".format(hike.elevationGainMeters))
                Text("-%.0f m".format(hike.elevationLossMeters))
            }
        }
    }
}

private fun formatDate(timestampMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestampMillis))

// Petit wrapper pour éviter d'importer stringResource partout de façon répétitive
@Composable
private fun stringResourceCompat(id: Int): String =
    androidx.compose.ui.res.stringResource(id)
