package fr.pierre.randogpx.ui.massif

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.pierre.randogpx.R
import fr.pierre.randogpx.RandoApplication
import fr.pierre.randogpx.data.Massif
import fr.pierre.randogpx.ui.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageMassifsScreen(
    onBack: () -> Unit,
    viewModel: MassifViewModel = viewModel(
        factory = ViewModelFactory.from(LocalContext.current.applicationContext as RandoApplication)
    )
) {
    val massifs by viewModel.massifs.collectAsState()
    var newMassifName by remember { mutableStateOf("") }
    var massifBeingRenamed by remember { mutableStateOf<Massif?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteBlockedMessage = stringResource(R.string.massif_delete_blocked)

    LaunchedEffect(Unit) {
        viewModel.deleteBlocked.collect {
            snackbarHostState.showSnackbar(deleteBlockedMessage)
        }
    }

    massifBeingRenamed?.let { massif ->
        RenameMassifDialog(
            initialName = massif.name,
            onDismiss = { massifBeingRenamed = null },
            onConfirm = {
                viewModel.renameMassif(massif.id, it)
                massifBeingRenamed = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_manage_massifs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newMassifName,
                    onValueChange = { newMassifName = it },
                    label = { Text(stringResource(R.string.massif_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        viewModel.addMassif(newMassifName)
                        newMassifName = ""
                    },
                    enabled = newMassifName.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }

            if (massifs.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.manage_massifs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(massifs, key = { it.id }) { massif ->
                        MassifRow(
                            massif = massif,
                            onEdit = { massifBeingRenamed = massif },
                            onDelete = { viewModel.deleteMassif(massif.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MassifRow(massif: Massif, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(massif.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.content_desc_edit_massif))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.content_desc_delete_massif),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun RenameMassifDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_rename_massif_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.massif_name_hint)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
