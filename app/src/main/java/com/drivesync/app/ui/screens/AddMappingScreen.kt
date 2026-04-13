package com.drivesync.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivesync.app.ui.viewmodel.AddMappingViewModel
import com.drivesync.app.ui.viewmodel.extractDisplayPath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMappingScreen(
    existingMappingId: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDrivePicker: (currentId: String, currentPath: String) -> Unit,
    onSaved: () -> Unit,
    driveFolderIdFromPicker: String? = null,
    driveFolderPathFromPicker: String? = null,
    viewModel: AddMappingViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(existingMappingId) {
        if (existingMappingId != null) {
            viewModel.loadExistingMapping(existingMappingId)
        }
    }

    // Apply Drive folder selection result from picker
    LaunchedEffect(driveFolderIdFromPicker, driveFolderPathFromPicker) {
        if (!driveFolderIdFromPicker.isNullOrBlank() && !driveFolderPathFromPicker.isNullOrBlank()) {
            viewModel.setDriveFolder(driveFolderIdFromPicker, driveFolderPathFromPicker)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect { onSaved() }
    }

    // SAF folder picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val displayPath = extractDisplayPath(context, it)
            viewModel.setLocalFolder(it, displayPath)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (existingMappingId != null) "Edit mapping" else "Add folder mapping")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Error message
            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ---- Android folder section ----
            SectionHeader(
                icon = Icons.Filled.PhoneAndroid,
                title = "Android Folder",
                subtitle = "Select the folder on your device to back up"
            )

            FolderPickerButton(
                label = if (uiState.localUri.isBlank()) "Tap to choose a folder…" else uiState.localDisplayPath,
                isEmpty = uiState.localUri.isBlank(),
                icon = Icons.Filled.Folder,
                onClick = { folderPickerLauncher.launch(null) }
            )

            // ---- Drive folder section ----
            SectionHeader(
                icon = Icons.Filled.CloudUpload,
                title = "Google Drive Folder",
                subtitle = "Choose where to upload files on Drive"
            )

            FolderPickerButton(
                label = if (uiState.driveFolderId.isBlank()) "Tap to choose a Drive folder…" else uiState.driveDisplayPath,
                isEmpty = uiState.driveFolderId.isBlank(),
                icon = Icons.Filled.Cloud,
                onClick = {
                    onNavigateToDrivePicker(uiState.driveFolderId, uiState.driveDisplayPath)
                }
            )

            // ---- Options ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Options",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Delete from Drive if deleted locally",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Files removed from your device will also be removed from Drive",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.deleteOnDrive,
                            onCheckedChange = viewModel::setDeleteOnDrive
                        )
                    }
                }
            }

            // ---- Preview ----
            if (uiState.localUri.isNotBlank() && uiState.driveFolderId.isNotBlank()) {
                MappingPreviewCard(
                    localPath = uiState.localDisplayPath,
                    drivePath = uiState.driveDisplayPath
                )
            }

            Spacer(Modifier.height(8.dp))

            // Save button
            Button(
                onClick = { viewModel.saveMapping(existingMappingId) },
                enabled = uiState.isValid && !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (existingMappingId != null) "Update mapping" else "Save mapping")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}

@Composable
private fun FolderPickerButton(
    label: String,
    isEmpty: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val containerColor = if (isEmpty)
        MaterialTheme.colorScheme.surfaceVariant
    else
        MaterialTheme.colorScheme.primaryContainer

    val contentColor = if (isEmpty)
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    else
        MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MappingPreviewCard(localPath: String, drivePath: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Mapping preview",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Phone: $localPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Drive: $drivePath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}
