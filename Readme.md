# DriveSync — Android to Google Drive Backup

An Android app that automatically backs up folders from your Android device to Google Drive on a configurable schedule. You can define multiple folder-pair mappings, e.g.:

| Android (phone) | Google Drive |
|---|---|
| `DCIM/Camera` | `phone_data/camera` |
| `Downloads` | `phone_data/random_files` |

---

## Features

- **Multiple folder mappings** — configure as many Android → Drive pairs as you need
- **Google Sign-In** — secure OAuth2 authentication with Drive scope
- **In-app Drive folder browser** — navigate, create, and select Drive folders without leaving the app
- **Android folder picker** — uses the Storage Access Framework (SAF) for safe, permission-respecting folder access
- **Scheduled sync** — powered by WorkManager; options from every hour to once a week
- **Wi-Fi only mode** — avoid mobile data charges
- **Background sync** — runs as a foreground service with a persistent notification
- **Per-mapping status** — see last sync time and file count for each mapping
- **Delete sync** — optionally delete Drive files when they are removed locally
- **Notifications** — get notified when sync completes or encounters an error
- **Survives reboots** — a BroadcastReceiver reschedules work after device restart

---

## Project Structure

```
app/src/main/java/com/drivesync/app/
├── DriveSyncApplication.kt       # App class: WorkManager config, notification channels
├── MainActivity.kt               # Single-activity host
├── auth/
│   └── GoogleAuthManager.kt      # Google Sign-In + OAuth2 token management
├── data/
│   ├── local/
│   │   ├── FolderMapping.kt      # Room entity: Android URI ↔ Drive folder ID
│   │   ├── FolderMappingDao.kt   # Room DAO
│   │   └── AppDatabase.kt        # Room database singleton
│   ├── model/
│   │   └── SyncResult.kt         # SyncResult, DriveFolder, SyncState
│   └── repository/
│       └── FolderMappingRepository.kt
├── sync/
│   ├── DriveApiHelper.kt          # Drive API: list/upload/update/delete files & folders
│   ├── SyncManager.kt             # Core sync logic (local → Drive diffing)
│   ├── SyncWorker.kt              # WorkManager worker; handles scheduling
│   ├── SyncForegroundService.kt   # Foreground service stub for WorkManager
│   ├── BootReceiver.kt            # Reschedules on boot/update
│   ├── NotificationHelper.kt      # Notification channel creation + builders
│   └── ProgressTrackingInputStreamContent.kt  # Upload progress tracking
└── ui/
    ├── navigation/
    │   └── AppNavigation.kt       # Compose Navigation graph
    ├── screens/
    │   ├── HomeScreen.kt          # Mapping list, sync button, sign-in
    │   ├── AddMappingScreen.kt    # Add/edit a folder mapping
    │   ├── DriveFolderPickerScreen.kt  # Browse & select Drive folders
    │   └── SettingsScreen.kt      # Schedule, Wi-Fi, notification settings
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    └── viewmodel/
        ├── HomeViewModel.kt
        ├── AddMappingViewModel.kt
        ├── DriveFolderPickerViewModel.kt
        └── SettingsViewModel.kt
```

---

## Setup Instructions

### 1. Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project (or use an existing one)
3. Enable the **Google Drive API**:
   - Go to **APIs & Services → Library**
   - Search for "Google Drive API" and enable it

### 2. Configure OAuth 2.0

1. Go to **APIs & Services → Credentials**
2. Click **Create Credentials → OAuth client ID**
3. Select **Android** as the application type
4. Enter your app's **package name**: `com.drivesync.app`
5. Enter your app's **SHA-1 signing certificate fingerprint**:
   ```bash
   # For debug keystore:
   keytool -keystore ~/.android/debug.keystore \
           -list -v -alias androiddebugkey -storepass android
   ```
6. Click **Create** and download the resulting `google-services.json`

### 3. Add google-services.json

Place the downloaded `google-services.json` in the `app/` directory:
```
app/
├── google-services.json   ← place here
├── build.gradle.kts
└── src/
```

Then add the Google Services plugin to your build files:

**`build.gradle.kts` (project level)** — add:
```kotlin
id("com.google.gms.google-services") version "4.4.0" apply false
```

**`app/build.gradle.kts`** — add at the top:
```kotlin
id("com.google.gms.google-services")
```

### 4. Configure OAuth Consent Screen

1. Go to **APIs & Services → OAuth consent screen**
2. Select **External** user type
3. Fill in app name, support email, developer contact
4. Add scopes: `../auth/drive.file` and `../auth/drive`
5. Add your Google account as a test user (while in testing mode)

### 5. Build and Run

```bash
./gradlew assembleDebug
# or open in Android Studio and click Run
```

---

## How It Works

### Sync Logic

For each enabled folder mapping:
1. **List local files** via SAF `DocumentFile.listFiles()`
2. **List Drive files** in the target folder via Drive API
3. **Compare**: upload new files, re-upload modified files (by last-modified timestamp)
4. **Optionally delete** Drive files that no longer exist locally (if "delete on Drive" is enabled)

### Scheduling

Uses `WorkManager` with `PeriodicWorkRequest`. The minimum interval is 15 minutes (Android OS restriction). The worker:
- Runs as a foreground service with a progress notification
- Retries with exponential backoff on failure
- Is rescheduled after device reboot via `BootReceiver`

### Storage Access

Android folder selection uses the Storage Access Framework (SAF) via `ACTION_OPEN_DOCUMENT_TREE`. Persistent URI permissions are taken so the app can access the folder across reboots without requesting permission again.

---

## Permissions Required

| Permission | Purpose |
|---|---|
| `INTERNET` | Upload to Google Drive |
| `READ_MEDIA_IMAGES/VIDEO/AUDIO` | Read media files (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Read files (Android ≤ 12) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Background sync notification |
| `POST_NOTIFICATIONS` | Sync result notifications (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Reschedule sync after reboot |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | Wi-Fi-only mode check |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose |
| Database | Room |
| Background work | WorkManager |
| Auth | Google Sign-In (`play-services-auth`) |
| Drive API | `google-api-services-drive` v3 |
| HTTP | `google-http-client-android` |
| Async | Coroutines + Flow |
| Storage | Storage Access Framework (SAF) |
