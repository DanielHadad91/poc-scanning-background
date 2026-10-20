# Background limits by version and manufacturer

> Full English text of the in-app guide screen (`LimitsActivity`), including every card, every bullet,
> every "How to enable" step, and every source/button link.
> Screen action-bar title: **Background limits**.

**Intro:** This screen gathers the background limits: what each component in our implementation can do, what changes in each Android version, and what each manufacturer blocks. Where needed, there are buttons that open the relevant settings screen.

---

## Current schedule state

> This card is live and reflects the current device state; the dynamic values are shown below in `{ }`.

### Does the schedule survive and self-heal

- Current schedule origin: `{one of:}`
  - Not set yet
  - Created on first open
  - Continued automatically after the previous run (healthy state)
  - Re-created after device boot
  - Updated after an interval change
  - Re-created on app entry: the previous schedule was not found, likely stopped by the system or a force stop
  - Schedule existed and kept running in the background
- Next run scheduled for `{date-time}`  — or, when there is none: No active schedule right now
- Mechanism: `{JobScheduler | AlarmManager}`
- On every open the app checks whether the schedule exists, and if not, re-creates it and records that. After the device boots, the BootReceiver re-creates it. A failed run still schedules the next one. There is no continuous background watchdog: if the system stops the schedule while the app is closed and there is no boot, it is restored only on the next open.

---

## Our implementation: components

### ScanJobService — via JobScheduler (API 21+)

- Background runtime: up to about 10 minutes per run. From Android 14, blocking the main thread causes an ANR, so the work runs on a separate thread.
- Sends to the server: yes.
- Listens for network and schedules a guaranteed send: yes. A network constraint is set, so the run starts only when there is internet and waits for it otherwise.
- Timing: approximate, "not before". From Android 15 it may be delayed up to about 31 minutes due to network-task batching.
- Shown to the user: runs in the background with no UI at all, even when the app is closed.
- Battery saver: the run may be delayed.
- Deep Doze: deferred to the next maintenance window.
- Force stop: the scheduled task is cancelled and re-created only on the next app open or after a boot.

### AlarmReceiver — via AlarmManager (API 19-20)

- Background runtime: only the broadcast window (about 10 to 60 seconds) via goAsync, so the work must be short.
- Sends to the server: yes.
- Listens for network: no built-in network constraint. If there is no connection, the run is recorded as a failure and the next run tries again.
- Timing: exact (setExact) on API 19 to 30.
- Shown to the user: runs in the background with no UI.
- Battery saver / Doze: on 19-22 there is no Doze. From 23, setExact is deferred to the maintenance window.
- Force stop: the alarm is cancelled and re-created only on the next open or after a boot.

### BootReceiver — listens for device boot

- Background runtime: short. It only records the boot and re-schedules.
- Sends to the server: no. It does not scan or send, only brings the schedule back.
- Shown to the user: runs in the background with no UI.
- Limitation: Android delivers BOOT_COMPLETED only to an app opened at least once and not force-stopped.

### ScanRunner — the work itself

- Not a system component, but the shared code every scheduler calls: it scans the apps, sends the array, and records the result and the duration.
- Blocking, so it always runs off the main thread.

### MainActivity — the screen

- Runs only while the app is open. Responsible for display only.
- On every open it makes sure the schedule exists, and if not, re-creates it.
- The background run does not depend on it: it continues even when the screen is closed.

---

## Limits by Android version

### Android 4.4–5.1 (API 19-22)

- No Doze and no background limits. Exact alarms work without a permission.
- Effect: the most permissive environment. On 19-20 the app uses an exact AlarmManager.

Source: [Official source (developer.android.com)](https://developer.android.com/develop/background-work/background-tasks)

### Android 6 (API 23) — Doze and App Standby

- When the device is stationary, unplugged and the screen is off, it enters Doze: tasks, alarms and network access are deferred to maintenance windows.
- A battery-optimization exemption list was added.
- Effect: scheduled runs may be delayed when the device is at rest.

Source: [Official source (developer.android.com)](https://developer.android.com/training/monitoring-device-state/doze-standby)

### Android 7 (API 24-25) — Doze even while moving

- Light Doze is triggered even while the device is moving, after a short time with the screen off.
- Some implicit broadcasts (such as a network-connection change) no longer reach a manifest receiver.
- Effect: delays are more common.

Source: [Official source (developer.android.com)](https://developer.android.com/topic/performance/background-optimization)

### Android 8 (API 26) — background limits

- A background app cannot start services freely, and most implicit manifest broadcasts were removed (BOOT_COMPLETED is still allowed).
- Effect: work must go through JobScheduler. That is already our case, and the BootReceiver is allowed.

Source: [Official source (developer.android.com)](https://developer.android.com/about/versions/oreo/background)

### Android 9 (API 28) — standby buckets

- Apps are split into buckets by usage frequency, and tasks and alarms are throttled by the bucket.
- Effect: an app that is not used runs less often, and the actual interval may stretch.

Source: [Official source (developer.android.com)](https://developer.android.com/topic/performance/appstandby)

### Android 10-11 (API 29-30)

- API 29: a limit on starting a screen from the background (we do not do that).
- API 30: to see all apps you need QUERY_ALL_PACKAGES (we have it). Google Play restricts the permission to approved uses.
- Effect: the full scan depends on QAP.

Sources:
- [Source: background starts (API 29)](https://developer.android.com/guide/components/activities/background-starts)
- [Source: QUERY_ALL_PACKAGES (API 30)](https://developer.android.com/training/package-visibility)

### Android 12 (API 31) — exact alarms

- An exact alarm requires the SCHEDULE_EXACT_ALARM permission. Starting a foreground service from the background was also restricted.
- Effect: none. The app does not use exact alarms from Android 12 and does not declare SCHEDULE_EXACT_ALARM (no added permissions), so nothing needs approving. Without that declaration, the "Alarms & reminders" screen shows the app with a locked switch.

Source: [Official source (developer.android.com)](https://developer.android.com/develop/background-work/services/alarms/schedule)

### Android 13 (API 33)

- Notifications require the POST_NOTIFICATIONS permission (we do not show notifications).
- Effect: negligible for this app.

Source: [Official source (developer.android.com)](https://developer.android.com/develop/ui/views/notifications/notification-permission)

### Android 14 (API 34)

- Exact alarms are off by default for a new install, and the user must allow them manually (not relevant to us: we do not use them).
- A task that blocks the main thread causes an ANR.
- A task with a network constraint requires the ACCESS_NETWORK_STATE permission (declared in our app).

Sources:
- [Source: Android 14 changes](https://developer.android.com/about/versions/14/behavior-changes-14)
- [Source: exact alarms in Android 14](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)

### Android 15 and up (API 35+)

- The system batches background network tasks, so a ready run may be delayed up to about 31 minutes, and while the screen is on it waits until the screen turns off.
- Effect: the next-run range widens. That is why the main screen shows a range, not a single time.

Source: [Official source (developer.android.com)](https://developer.android.com/about/versions/15/behavior-changes-all)

---

## Run frequency and delay

### Minimum interval — none for us

- The well-known 15-minute minimum applies only to periodic work (WorkManager PeriodicWork / periodic JobScheduler).
- We use one-time, self-rescheduling jobs, which have no such minimum — any number of minutes is allowed, and the app blocks no value.

Source: [Source: periodic minimum (15 min)](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)

### Maximum delay — not guaranteed

- Android does not promise when a deferrable job runs; it runs at the system's discretion.
- What delays it: Doze defers to maintenance windows, App Standby buckets cap run time, and from Android 15 network jobs are batched (up to ~31 min).
- So there is no official "maximum delay" number. The missed-runs counter on the main screen uses a per-version tolerance, not a guarantee.

Source: [Source: JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler)

### App Standby buckets (from API 28) — the real frequency limit

- The bucket caps total job run time (not a minimum interval), and the limits apply only off charger:
- Active: up to 20 minutes per rolling 60 minutes (from Android 16; before that, no limit).
- Working set: up to 10 minutes per rolling 4 hours.
- Frequent: up to 10 minutes per rolling 12 hours.
- Rare: up to 10 minutes per rolling 24 hours.
- Restricted: once a day for up to 10 minutes.
- Charging removes the limits. The values are not a guarantee and may change between releases.

Source: [Source: per-bucket limits](https://developer.android.com/topic/performance/power/power-details)

---

## Screen off: where it matters

### Screen off alone — blocks on no version

- There is no Android version where turning the screen off by itself stops a background run. JobScheduler (API 21+) and AlarmManager (API 19-20) were built to run with the screen off.
- In practice our scheduled runs run with the screen off too. What matters is not the screen itself, but Doze, standby buckets, and the manufacturer.

### Doze (from Android 6 / API 23) — screen off is only one condition

- Doze starts when the screen is off and the device is unplugged for a while. On Android 6 the device must also be stationary; from Android 7 it starts even while moving. Screen off alone is not enough.
- When active, runs and network access are deferred to maintenance windows. That is where the delay of a scheduled run comes from when the device is left at rest.
- What to do: set the app's battery to "Unrestricted" (the button in the shortcuts below opens this app's settings; tap the battery item there). Plugging in to charge disables Doze.

### Android 15+ (API 35) — screen off actually helps

- Here the network-task batching may delay a run precisely while the screen is on, and release it when the screen turns off.
- So on new devices turning the screen off works in our favor: that is usually the moment the batched run fires.

### Manufacturers — the real screen-off danger

- The most severe screen-off effect comes from the manufacturer, not from Android. Manufacturers such as Xiaomi, Huawei, Oppo and Vivo, and to a lesser extent Samsung, close background apps shortly after the screen turns off or the device locks, if the app is not allowlisted.
- If the schedule is closed this way, it is restored only on the next app open or after a boot.
- What to do: set the battery to "Unrestricted", and on devices that have "Autostart" (Xiaomi, Oppo, Realme, Vivo) also enable it, per the instructions in the manufacturers section below.

### Our components with the screen off

- ScanJobService and AlarmReceiver: run with the screen off, subject to Doze and the manufacturer.
- We have no foreground service. That is the only way to guarantee an immediate run with the screen off, but it requires a persistent notification and a declared service type from Android 14, so it is outside the POC scope.
- Other ways for an immediate run with the screen off, also outside the POC: a high-priority push message (FCM) that wakes the app even in Doze, or an allow-while-idle exact alarm (limited to about once per 9 minutes in Doze, and requiring a permission from Android 12).

---

## Limits by manufacturer (Don't kill my app)

> On the device, the card matching the current manufacturer is shown first, titled "Your device: …", and
> an aggressive match adds the note: "This is the manufacturer of this device, so you will likely need to
> allow background activity manually so the runs are not stopped." All manufacturer cards are listed below.

### What each setting does, and whether it touches our code

- The settings in "Don't kill my app" do not appear in the official Android documentation, because they are manufacturer additions on top of Android. They touch us directly, because our background run (JobScheduler/AlarmManager) and the BootReceiver depend on the system allowing the app to run in the background.
- Autostart / Auto-launch: lets the app and its components open in the background without the user opening them. If not enabled: after the manufacturer closes the app or after a boot, the scheduled scan and the BootReceiver may not run until you open the app manually.
- Battery "Unrestricted" / remove from optimization: prevents the system from killing the process and deferring tasks. If not enabled: runs are delayed, and sometimes the process is closed shortly after the screen turns off.
- Remove from "sleeping apps" (Samsung): prevents moving to "deep sleep" where background tasks do not run at all. If not enabled: after a day or two without use, the scan stops running.
- Lock the app in Recent apps: prevents closing by memory cleanup or swipe. If not enabled: a manual close or memory pressure stop the background until the next open.
- The shared consequence: in all of these, when the manufacturer stops us, the on-screen state does not advance (last/next time), and per our logic the schedule revives only on the next app open or after a boot. We have no continuous background watchdog that can revive a run the manufacturer killed while the app was closed.
- Information status: the platform limits (Doze, standby) are officially documented by Google (links on the version cards). Manufacturer behavior is partly documented by them (the settings); the aggressive background killing is based mainly on community testing (dontkillmyapp) and varies by model, version and ROM.

Source: [Android background (official source)](https://developer.android.com/topic/performance/background-optimization)

### Google (Pixel)

- Clean Android. No aggressive background killing by the manufacturer; only the standard Android limits apply (Doze, App Standby, battery optimization). There is no "Autostart" here, and none is needed.
- Status: not aggressive. Only the Android platform limits apply (officially documented).

How to enable:
1. Usually no action is needed. For extra safety: open Settings, go to Apps, pick the app, open Battery and choose "Unrestricted".

Sources:
- [Guide with screenshots (dontkillmyapp.com)](https://dontkillmyapp.com/google)

### Samsung (One UI)

- One UI moves unused apps to "sleeping" or "deep sleeping" and stops them. There is no Autostart here; the fix is the exclusion list and an unrestricted battery setting. Samsung officially documents that "deep sleeping apps" never run in the background.
- Status: the behavior is officially documented by the manufacturer (link below).
- If you don't do the steps: the manufacturer will close the app when it is in the background, the scheduled runs will stop, and the BootReceiver may not run after a boot — until you open the app again.

How to enable:
1. Open Settings, go to Apps, pick the app, open Battery and choose "Unrestricted".
2. In Settings, go to Battery then Background usage limits, and remove the app from "Sleeping apps" and "Deep sleeping apps".
3. Turn off "Remove permissions if app isn't used" for the app.

Sources:
- [Official manufacturer source](https://www.samsung.com/us/support/answer/ANS10003442/)
- [Guide with screenshots (dontkillmyapp.com)](https://dontkillmyapp.com/samsung)

### Xiaomi (MIUI / HyperOS)

- MIUI / HyperOS is aggressive. It requires "Autostart" in the Security app, and a battery mode of "No restrictions". Xiaomi's official docs state that autostart is denied by default.
- Status: the behavior is officially documented by the manufacturer (link below).
- If you don't do the steps: the manufacturer will close the app when it is in the background, the scheduled runs will stop, and the BootReceiver may not run after a boot — until you open the app again.

How to enable:
1. Open the Security app, go to Permissions, open "Autostart" and enable it for the app.
2. In the app's settings choose Battery saver and pick "No restrictions".
3. In Recent apps, hold the app and lock it.

Sources:
- [Official manufacturer source](https://www.mi.com/global/support/faq/details/KA-507608/)
- [Guide with screenshots (dontkillmyapp.com)](https://dontkillmyapp.com/xiaomi)

### Huawei / Honor (EMUI)

- EMUI is aggressive. Instead of a regular Autostart there is "App launch" that must be managed manually. Huawei officially documents that the app "may have been cleared by your phone when it was running in the background".
- Status: the behavior is officially documented by the manufacturer (link below).
- If you don't do the steps: the manufacturer will close the app when it is in the background, the scheduled runs will stop, and the BootReceiver may not run after a boot — until you open the app again.

How to enable:
1. Open Settings, go to Battery, open "App launch", pick the app, switch to manual management and enable all options (Auto-launch, Secondary launch, Run in background).

Sources:
- [Official manufacturer source](https://consumer.huawei.com/en/support/content/en-us00420068/)
- [Guide with screenshots (dontkillmyapp.com)](https://dontkillmyapp.com/huawei)

### OnePlus / Oppo / Realme (ColorOS)

- ColorOS / OxygenOS is aggressive. It requires "Auto-launch", allowing background activity, and turning off sleep optimization. The vendor documents the settings; the aggressive killing is based on community testing.
- Status: the settings are officially documented, but the background killing itself is based mainly on community testing (dontkillmyapp) and varies by model, version and ROM.
- If you don't do the steps: the manufacturer will close the app when it is in the background, the scheduled runs will stop, and the BootReceiver may not run after a boot — until you open the app again.

How to enable:
1. Open Settings, go to Battery, pick the app and allow background activity.
2. In Settings, open Auto-launch / Startup manager and enable it for the app.
3. In Recent apps, lock the app.

Sources:
- [Official manufacturer source](https://support.oppo.com/en/answer/?aid=2020322)
- [Guide with screenshots (dontkillmyapp.com)](https://dontkillmyapp.com/oppo)

### Vivo / iQOO (Funtouch / OriginOS)

- Funtouch / OriginOS is aggressive. It requires "Autostart" and allowing high background power consumption. The vendor documents the settings; the aggressive killing is based on community testing.
- Status: the settings are officially documented, but the background killing itself is based mainly on community testing (dontkillmyapp) and varies by model, version and ROM.
- If you don't do the steps: the manufacturer will close the app when it is in the background, the scheduled runs will stop, and the BootReceiver may not run after a boot — until you open the app again.

How to enable:
1. Open Settings, go to Battery, allow "High background power consumption" for the app.
2. In Settings, open "Autostart" and enable it for the app.

Sources:
- [Official manufacturer source](https://www.vivo.com/en/support)
- [Guide with screenshots (dontkillmyapp.com)](https://dontkillmyapp.com/vivo)

### All manufacturers

- The "Don't kill my app" project gathers up-to-date instructions with screenshots for every manufacturer and model.

Sources:
- [Open dontkillmyapp.com](https://dontkillmyapp.com)

> Note: when the current device's manufacturer has no dedicated card, a fallback card "Your device: {manufacturer}" is shown instead, with generic battery / optimization / Autostart steps and a [Guide with screenshots (dontkillmyapp.com)](https://dontkillmyapp.com/general) link (the slug matches the detected brand when known).

---

## Settings shortcuts

- The buttons open the relevant system settings screens directly.
- There is no exact-alarm button: the app does not use exact alarms and does not declare their permission (SCHEDULE_EXACT_ALARM), so there is nothing to turn on there.

Buttons (open system settings, not web links):
- This app's settings (battery item, then "Unrestricted")
- Battery saver

---

## Glossary

### Glossary — scheduling and background mechanisms

- JobScheduler — a system service (from API 21) that runs background tasks under conditions such as network or time, even when the app is closed.
- AlarmManager — a system service that triggers an action at a set time. On 19-20 it is our scheduling path; from API 23 alarms are deferred in Doze.
- WorkManager — an AndroidX library that wraps JobScheduler. We chose not to use it and to build directly on the Android mechanisms.
- Foreground Service — a service with a persistent notification visible to the user. The only way to guarantee an immediate, long background run, even with the screen off. Not used in the POC.
- FCM — a push message from the server that can wake the app even in Doze. Requires a server and Google Play. Not used in the POC.
- BootReceiver — a component that listens for Android's broadcast that boot finished (BOOT_COMPLETED) in order to re-schedule after a restart.
- PendingIntent — a token that lets the system perform an action on the app's behalf later, for example to trigger the AlarmReceiver.

### Glossary — Android power-saving states

- Doze — a power-saving state (from Android 6) that starts when the device is left unused, screen off and unplugged. In it, tasks and network are deferred to maintenance windows.
- App Standby — a state where Android limits apps that have not been used recently.
- Standby Buckets — a split (from API 28) by usage frequency. The lower the bucket, the more background runs are throttled.
- Battery optimization — the limit the default applies to an app's background. Removing it from the limit list reduces it.
- Battery saver — a user mode that further limits background activity.
- Maintenance window — a short period in which Android releases tasks and network that accumulated during Doze.

### Glossary — other terms

- QAP (QUERY_ALL_PACKAGES) — a permission (from API 30) to see all installed apps. Without it the list is partial.
- Exact alarm (setExactAndAllowWhileIdle) — an alarm at an exact time that fires even in Doze. From API 31 it requires the SCHEDULE_EXACT_ALARM permission.
- setMinimumLatency — a JobScheduler setting of "do not run before" a period. That is what enables an interval in minutes.
- Connectivity batching — batching of network tasks (from Android 15) to save battery, which may delay a run up to about 31 minutes.
- Force stop — a deliberate stop from Settings. It cancels tasks and alarms until the user opens the app again.
- Autostart — a setting of certain manufacturers (Xiaomi, Oppo, Realme, Vivo) that lets an app open in the background on its own. It does not exist on clean Android (Pixel).
- ANR — "Application Not Responding": a soft failure when a component blocks the main thread. From Android 14, a JobService that blocks causes an ANR.
