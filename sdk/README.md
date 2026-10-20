# Scanning SDK — integration guide

The `:sdk` module contains all the background logic — scanning installed apps, uploading the report,
self-rescheduling, and boot recovery. It has **no GUI, no Activity, and no manifest entries of its own**.
An integrating app ships its own UI and wires the SDK through two things only: its **own manifest** and a
**single call** to `ScanningSdk`.

## What ships in the SDK vs. what the client writes

| Concern | Lives in the SDK (you export it) | The client must write it |
| --- | --- | --- |
| Background service `ScanJobService` (JobScheduler, API 21+) | Code ✔ | Manifest `<service>` entry ✔ |
| `AlarmReceiver` (AlarmManager fallback, API 19–20) | Code ✔ | Manifest `<receiver>` entry ✔ |
| `BootReceiver` (re-arm after reboot) | Code ✔ | Manifest `<receiver>` + `BOOT_COMPLETED` filter ✔ |
| Scheduling (`ScanScheduling`, `BackgroundScheduler`), scanning, networking/TLS, storage, data models | Code ✔ | — |
| Permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `QUERY_ALL_PACKAGES`) | — | `<uses-permission>` entries ✔ |
| UI / Activities / strings / layouts | — (SDK has none) | All of it ✔ |
| Entry call to start scanning | `ScanningSdk` facade ✔ | One `ScanningSdk.initialize(...)` call ✔ |

**Why the manifest is the client's job:** component and permission declarations only take effect in the
installed app's *merged* manifest. The SDK deliberately declares none, so the integrator stays in full
control of what their app requests and exposes. The SDK's own `AndroidManifest.xml` is intentionally empty.

## 1. Add the dependency

```kotlin
// settings.gradle.kts
include(":sdk")

// app/build.gradle.kts
dependencies {
    implementation(project(":sdk"))
}
```

## 2. Declare the components and permissions in YOUR manifest (mandatory)

Use the SDK's **fully-qualified class names** (do not use leading-dot relative names unless your
`applicationId` equals the SDK package).

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<!-- Only if you need to scan every installed app; Google Play restricts this permission. -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

<application ...>

    <!-- API 21+: runs the scan when the app is closed, after a swipe and after a reboot. -->
    <service
        android:name="com.biocatch.poc.scanningbackground.services.ScanJobService"
        android:exported="false"
        android:permission="android.permission.BIND_JOB_SERVICE" />

    <!-- API 19-20: the AlarmManager fallback. -->
    <receiver
        android:name="com.biocatch.poc.scanningbackground.receivers.AlarmReceiver"
        android:exported="false" />

    <!-- Re-arms the schedule after a reboot without the user opening the app. -->
    <receiver
        android:name="com.biocatch.poc.scanningbackground.receivers.BootReceiver"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>

</application>
```

## 3. Start it from code (one call)

```kotlin
import com.biocatch.poc.scanningbackground.sdk.ScanConfig
import com.biocatch.poc.scanningbackground.sdk.ScanningSdk

// e.g. in Application.onCreate() or your launcher Activity.onCreate()
ScanningSdk.initialize(
    context,
    ScanConfig(
        serverUrl = "https://your-endpoint.example/collect",
        intervalMinutes = 60,
        autoRenew = true,
    ),
)
```

That is the entire integration. From here the SDK scans, uploads, records each run, re-arms the next run,
and recovers after a reboot — all with no UI.

## Public API (`com.biocatch.poc.scanningbackground.sdk.ScanningSdk`)

| Method | Purpose |
| --- | --- |
| `initialize(context, config?)` | Apply optional config, then arm the schedule if nothing is armed. Call once on start. |
| `start(context)` | Arm the schedule if nothing is armed (idempotent). |
| `stop(context)` | Cancel the armed run (revives on next `start`/boot). |
| `runNow(context): Boolean` | Run one scan now, off-cycle. |
| `reschedule(context, reason): LogEntry` | Re-arm with the current interval. |
| `setServerUrl` / `setIntervalMinutes` / `setAutoRenew` | Update settings. |
| `history(context): List<LogEntry>` | Recorded runs, newest first. |
| `clearHistory(context)` | Clear the history and reset the missed-runs count to start from now. |
| `missedRuns(context, now?): MissedRunsSummary` | Total scheduled runs missed over the whole history (every gap longer than this Android version's tolerance, plus the open one), the number of such gaps, and the longest gap. |
| `state(context): ScanState` | Snapshot: scheduler, origin, next-run time, interval, server URL, QAP status, last run. |

`ScanConfig` and `ScanState` are plain data classes; `LogEntry` (and its `RunStatus`/`Outcome`/`Trigger`
enums) are the public data model the host reads to render its own UI.

## Constraints kept by this module

- No AndroidX / zero third-party dependencies — Android framework APIs only (minSdk 19 … compileSdk 37).
- No GUI, no Activity, no component or permission declarations in the SDK manifest.
