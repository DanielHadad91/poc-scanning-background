# App install scans HLD
Chat exported from the js-tool research workspace on 1 Nov 2026.
Confluence: https://biocatch.atlassian.net/wiki/spaces/DCS/pages/7553089614/Android+SDK+App+Install+Scans+HLD

## User

*Sunday, Nov 1, 2026, 11:29 AM (UTC+2)*

create HLD to the app install scans, according to the this repo code.
consider all aspects of bg approches in android versions

## Assistant

I'll base the high-level design on how this repo actually implements app-install scans, including how background collection changes across Android versions. First I'm reading the design-artifact guidance and locating the scan code.

## Assistant

The current workspace is the JS scanner. Installed-app collection lives in the Android SDK next to it, so I’m reading those collectors and how background execution is constrained by Android version.

## Assistant

The install scan lives in the Android SDK, not in this JS research repo. I’m writing the high-level design from that code, with one row per Android background-execution generation.

## Assistant

The install scan never asks Android to run the host in the background. It runs inside a live SDK session, on one process-local thread, after the first activity exists. A dynamic package broadcast is only a “something changed” bit, so `getInstalledPackages` can be skipped.

The design is in [App install scans](/Users/yoav.paskaro/.cursor/projects/Users-yoav-paskaro-dev-tools-js-tool-reserch/canvases/app-install-scans-hld.canvas.tsx). A canvas is a live view you can open beside the chat. I used one because the version matrix is the design, and it is easier to scan there than as a long write-up. This JS research workspace has no install-scan code. The source is the Android SDK (`ApplicationsService`, `DeviceApplicationsCollector`, `AppsChecksumCollector`, `CollectorsController`, `SessionScheduler`).

**When it runs.** `DeviceApplications` is `ContinuousPerSession` with `QUERY_PACKAGES` consent. It does not start from `Application.onCreate` while the process is still in the background. `BioCatchSdkInitializer` arms `SessionScheduler` before any activity, and a session started from the background waits for the first activity. On session start, and again on resume, the collector posts one scan on `BC_ApplicationsThread` after `deviceAppBackgroundDelayMillis` (default 100 ms). The first pass of a session reports every visible non-system app as installed. Later passes emit only the diff.

**What each Android generation changes.**

| API | Platform limit | What the SDK does |
|---|---|---|
| 19–25 | No Oreo background limits. One installer string, 32-bit `versionCode`. | Same path: dynamic receiver, `getInstallerPackageName`, `versionCode` widened to long. |
| 26–27 | Manifest receivers are not woken for `PACKAGE_ADDED` / `REMOVED` / `REPLACED`. Background services are limited. | No manifest receiver, no `Service`, no `JobScheduler`. Dynamic receiver while the process is alive, including pause. Unregister only on SDK stop. |
| 28–29 | Standby buckets can freeze a cached process. `longVersionCode`, `SigningInfo`. Background activity starts blocked. | `longVersionCode` and signing certificates. The scan never starts an activity. Standby does not defer this executor; it can still freeze the process. |
| 30 | Package visibility filters queries and package broadcasts. Install source splits into initiating and installing. | `InstallSourceInfo`. The SDK manifest does not declare `QUERY_ALL_PACKAGES`; the host must. Checksum installer priority uses `initiatingPackage` only, because `installingPackage` can be rewritten. |
| 31–32 | Foreground-service and exact-alarm restrictions. `requestChecksums`. | No foreground service and no alarm. Checksums, off by default, try `requestChecksums` and fall back to hashing the base APK. |
| 33 | `registerReceiver` must say exported or not. | `RECEIVER_NOT_EXPORTED`. System broadcasts still arrive. |
| 34 | Foreground-service types. Implicit intents cannot hit a non-exported receiver from another app. | No new branch. The API 33 flag stays correct. |
| 35–36 | Private Space hides apps until that space is unlocked. Stricter standby. | No code branch. Those apps are simply missing from the snapshot. Compile and target SDK of this tree are 36. |

**Approaches that are not used:** manifest receivers, `JobScheduler` / WorkManager, `AlarmManager`, and foreground services. Each one is an OS background API that a later Android release restricted, and the scan is session-scoped, so those APIs would add policy surface without seeing installs after the process is killed. Polling is documented only for a host-supplied `DeviceApplicationsCollectorEx`. DeviceIQ risky apps are a separate probe of a fixed package list, not this inventory.

**Limit of the live session.** A package broadcast during an already-started session only sets the pending bit. The diff runs on the next restart, which is resume after pause or the next session. A frozen or killed process sees no installs until the host is in a session again.

## User

*Sunday, Nov 1, 2026, 11:35 AM (UTC+2)*

remove from the hld any ref to the js-tool

## Assistant

I'll strip every mention of the JS research workspace from the high-level design so it reads as an Android SDK document only.

## Assistant

The high-level design no longer mentions the JS research workspace. [App install scans](/Users/yoav.paskaro/.cursor/projects/Users-yoav-paskaro-dev-tools-js-tool-reserch/canvases/app-install-scans-hld.canvas.tsx) now describes only the Android SDK inventory scan.

## User

*Sunday, Nov 1, 2026, 11:44 AM (UTC+2)*

what do u mean by: t runs inside a live SDK session
it should be decopules form the sdk lifecycle

## Assistant

The “live SDK session” line is about when the scan is started in the current code. I’ll re-check that against the lifecycle so I can say exactly what is still tied to the session and what is already independent of it.

## Assistant

That sentence described when rows are enqueued, not when installs are watched. The watch is a separate component, and the design should not follow session start, pause, or resume. I’ll correct the HLD to that boundary.

## Assistant

“Inside a live SDK session” meant the moment rows are copied into the data queue. `DeviceApplicationsCollector.start()` is what calls `getApplicationChanges()`, and the collector controller calls that on session start and again on resume. That is delivery. It is not the lifetime of the watch.

The watch is `ApplicationsService` plus `BC_ApplicationsThread`. Both are created once, when the QAP module loads. The receiver is registered there, and pause does not unregister it. The collector is not allowed to start or stop that thread. A package broadcast only sets a pending bit.

The high-level design now uses that split. [App install scans](/Users/yoav.paskaro/.cursor/projects/Users-yoav-paskaro-dev-tools-js-tool-reserch/canvases/app-install-scans-hld.canvas.tsx) treats the watch as decoupled from session start, pause, and resume. Two SDK events still touch it: `State.STOPPED` unregisters the receiver, and a new session clears the in-memory snapshot. Neither event is the scan’s clock.

## User

*Sunday, Nov 1, 2026, 11:51 AM (UTC+2)*

add also flowchart diagram, and create confluence page

## Assistant

I’ll add a flowchart to the high-level design and publish the same design as a Confluence page. First I’m checking how this workspace creates Confluence pages and how the canvas draws a flow.

## Assistant

The SDK docs live in the Data Collection space. I’ll add the flow to the design and publish the page under Android SDK Features.

## Assistant

The install-watch flowchart is in the high-level design, and the same design is published in Confluence.

The page is [Android SDK — App Install Scans (HLD)](https://biocatch.atlassian.net/wiki/spaces/DCS/pages/7553089614/Android+SDK+App+Install+Scans+HLD), under Android SDK Features in Data Collection & SDKs. The flowchart sits in the “Install watch flow” section: module load, the receiver, the pending bit, the package query, and the stop branch. The image is still rendering, so it can take a moment to appear. The step table under it is the same flow in text.

The same diagram is also on [App install scans](/Users/yoav.paskaro/.cursor/projects/Users-yoav-paskaro-dev-tools-js-tool-reserch/canvases/app-install-scans-hld.canvas.tsx).

## User

*Sunday, Nov 1, 2026, 12:02 PM (UTC+2)*

what is the pending bit

## Assistant

The pending bit is the Boolean `pendingPackageChanges` on `ApplicationsService`. It records whether an install or uninstall may have happened since the last package query, so the next read can skip `getInstalledPackages` when nothing has changed.

```47:47:/Users/yoav.paskaro/dev/android-sdk/sdk/src/main/java/com/biocatch/client/android/sdk/collection/collectors/qap/deviceApplication/ApplicationsService.kt
    private var pendingPackageChanges = true
```

It starts `true`, so the first read always queries PackageManager. A `PACKAGE_ADDED`, `PACKAGE_REMOVED`, or `PACKAGE_REPLACED` broadcast sets it back to `true` and does nothing else. `getApplicationChanges()` checks it first:

- **`false`:** return an empty change map and do not call `getInstalledPackages`.
- **`true`:** query the current packages, diff them against the stored snapshot, write the new snapshot, then set the flag back to `false`.

A new session also forces it to `true` and clears the snapshot, so the next read reports the full visible inventory again.

## User

*Sunday, Nov 1, 2026, 12:03 PM (UTC+2)*

move this chat to the poc-scanning-background workspace

## Assistant

I'll find the `poc-scanning-background` workspace and how this chat can be moved there.

## Assistant

This chat stays bound to the `js-tool reserch` workspace. Cursor has no way to move an existing chat into `/Users/yoav.paskaro/dev/poc-scanning-background`.

To keep working there, open that folder in Cursor and start a new chat. This conversation will still be listed under the original workspace. The HLD page is already independent of either folder: [Android SDK — App Install Scans (HLD)](https://biocatch.atlassian.net/wiki/spaces/DCS/pages/7553089614/Android+SDK+App+Install+Scans+HLD).

## User

*Sunday, Nov 1, 2026, 12:09 PM (UTC+2)*

save this chat in md file and move it to the /Users/yoav.paskaro/dev/poc-scanning-background

## Assistant

Saved this chat as `chat-app-install-scans-hld-2026-11-01.md` in `/Users/yoav.paskaro/dev/poc-scanning-background`. Tool calls are omitted. The messages are the user and assistant text from the original chat.
