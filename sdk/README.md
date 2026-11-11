# Scanning SDK — integration guide

The `:sdk` module contains all the background logic — scanning installed apps, uploading the report,
the system repeat, and boot recovery. It has **no GUI, no Activity, and no manifest entries of its own**.
An integrating app ships its own UI and wires the SDK through two things only: its **own manifest** and a
**single call** to `ScanningSdk`.

## What ships in the SDK vs. what the client writes

| Concern | Lives in the SDK (you export it) | The client must write it |
| --- | --- | --- |
| Background service `ScanJobService` (JobScheduler, API 21+) | Code ✔ | Manifest `<service>` entry ✔ |
| `AlarmReceiver` (AlarmManager fallback, API 19–20) | Code ✔ | Manifest `<receiver>` entry ✔ |
| `BootReceiver` (re-arm the API 19–20 alarm after reboot) | Code ✔ | Manifest `<receiver>` + `BOOT_COMPLETED` filter ✔ |
| Scheduling (`ScanJobSchedulerContract`), scanning, networking/TLS, storage, data models | Code ✔ | — |
| Permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `QUERY_ALL_PACKAGES`) | — | `<uses-permission>` entries ✔ |
| UI / Activities / strings / layouts | — (SDK has none) | All of it ✔ |
| Entry call to start scanning | `ScanningSdk` facade ✔ | One `ScanningSdk.scheduleScan(...)` call ✔ |

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
<!-- Not declared on purpose: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS would let an app open the
     system "always run in the background" dialog for itself directly, the most reliable fix for
     Doze/OEM delays. This integration adds no permissions beyond the four above, so the host app
     sends the user to its own App info screen instead (battery item -> "Unrestricted").
     The SDK needs no exact-alarm permission either: it schedules through JobScheduler. -->

<application ...>

    <!-- API 21+: runs the scan when the app is closed, after a swipe and after a reboot. -->
    <service
        android:name="com.biocatch.poc.scanningbackground.scanjobscheduler.ScanJobService"
        android:exported="false"
        android:permission="android.permission.BIND_JOB_SERVICE" />

    <!-- API 19-20: the AlarmManager fallback. -->
    <receiver
        android:name="com.biocatch.poc.scanningbackground.scanjobschedulerbelow21.AlarmReceiver"
        android:exported="false" />

    <!-- Re-arms the schedule after a reboot without the user opening the app. -->
    <receiver
        android:name="com.biocatch.poc.scanningbackground.scanjobschedulerbelow21.BootReceiver"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>

</application>
```

## 3. Start it from code (one call)

```kotlin
import com.biocatch.poc.scanningbackground.sdk.ScanningSdk

// e.g. in Application.onCreate() or your launcher Activity.onCreate()
ScanningSdk.scheduleScan(
    context,
    intervals = 1,
    backendDetectionUrl = "https://your-endpoint.example/collect",
)
```

That is the entire integration. From here the SDK scans, uploads, records each run, and repeats.
API 19–20 calls `setInexactRepeating` at the `scheduleScan` call and passes that call plus one stored offset as the first fire. That delivery is the scan. The offset stays until the count changes. API 21+ registers the periodic job at that call, with no offset wait, and the scan may start anywhere in the period. After boot, API 19–20 makes the same call at the boot and passes the boot plus the stored offset as the first fire. API 21+ leaves the persisted job in place. This stays off when `cancelScheduledScan` was called. The boot receiver does not scan, and a finished run does not register the next one. `setPeriodic` and `setInexactRepeating` own the repeat.

## Public API (`com.biocatch.poc.scanningbackground.sdk.ScanningSdk`)

| Method | Purpose |
| --- | --- |
| `scheduleScan(context, intervals, backendDetectionUrl?)` | Arm 1, 2, or 3 runs in 24 hours. The same count leaves the registration. A different count replaces it. A blank or non-http URL returns without changing the registration, and the scan does not run. |
| `cancelScheduledScan(context)` | Remove the repeat. Boot does not arm it again until `scheduleScan`. |

## Constraints kept by this module

- No AndroidX / zero third-party dependencies — Android framework APIs only (minSdk 19 … compileSdk 37).
- No GUI, no Activity, no component or permission declarations in the SDK manifest.
