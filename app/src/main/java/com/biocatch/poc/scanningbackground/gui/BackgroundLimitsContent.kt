package com.biocatch.poc.scanningbackground.gui

import android.os.Build

/** Accent color of a guide card. */
enum class Tone { INFO, OK, WARN, BAD }

enum class ActionType { APP_DETAILS, BATTERY_OPTIMIZATION, BATTERY_SAVER, EXACT_ALARM, OPEN_URL }

data class GuideAction(val label: String, val type: ActionType, val url: String? = null)

/** One card in the guide: a title, bullet lines, optional numbered steps, and optional buttons. */
data class GuideItem(
    val title: String,
    val tone: Tone = Tone.INFO,
    val lines: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val actions: List<GuideAction> = emptyList(),
)

/**
 * All the guide text, in Hebrew and English. Each builder takes [heb] and returns the matching
 * language, so the content follows the language toggle together with the string resources.
 */
object BackgroundLimitsContent {

    private const val DKMA_BASE = "https://dontkillmyapp.com"
    private const val DOC = "https://developer.android.com"

    private fun tr(heb: Boolean, he: String, en: String): String = if (heb) he else en

    private fun docSource(
        url: String,
        heb: Boolean,
        he: String = "מקור רשמי (developer.android.com)",
        en: String = "Official source (developer.android.com)",
    ) = GuideAction(if (heb) he else en, ActionType.OPEN_URL, url)

    /** Our components and exactly what each one can and cannot do. */
    fun components(heb: Boolean): List<GuideItem> = listOf(
        GuideItem(
            title = tr(heb, "ScanJobService — דרך JobScheduler (API 21+)", "ScanJobService — via JobScheduler (API 21+)"),
            tone = Tone.OK,
            lines = if (heb) listOf(
                "משך ריצה ברקע: עד כ-10 דקות לכל ריצה. מ-Android 14 חסימה של ה-main thread גורמת ל-ANR, ולכן העבודה רצה ב-thread נפרד.",
                "שליחה לשרת: כן.",
                "האזנה לרשת ותזמון שליחה מוצלחת: כן. מוגדר תנאי רשת, כך שהריצה מתחילה רק כשיש אינטרנט ומחכה לו אם אין.",
                "דיוק זמן: משוער, \"לא לפני\". מ-Android 15 ייתכן עיכוב של עד כ-31 דקות עקב קיבוץ משימות רשת.",
                "תצוגה ללקוח: רץ ברקע ללא כל תצוגה, גם כשהאפליקציה סגורה.",
                "מצב חיסכון סוללה: הריצה עשויה להתעכב.",
                "Doze עמוק: נדחה לחלון התחזוקה הבא.",
                "Force stop: המשימה המתוזמנת מבוטלת, ותיווצר מחדש רק בפתיחה הבאה או אחרי הדלקה.",
            ) else listOf(
                "Background runtime: up to about 10 minutes per run. From Android 14, blocking the main thread causes an ANR, so the work runs on a separate thread.",
                "Sends to the server: yes.",
                "Listens for network and schedules a guaranteed send: yes. A network constraint is set, so the run starts only when there is internet and waits for it otherwise.",
                "Timing: approximate, \"not before\". From Android 15 it may be delayed up to about 31 minutes due to network-task batching.",
                "Shown to the user: runs in the background with no UI at all, even when the app is closed.",
                "Battery saver: the run may be delayed.",
                "Deep Doze: deferred to the next maintenance window.",
                "Force stop: the scheduled task is cancelled and re-created only on the next app open or after a boot.",
            ),
        ),
        GuideItem(
            title = tr(heb, "AlarmReceiver — דרך AlarmManager (API 19-20)", "AlarmReceiver — via AlarmManager (API 19-20)"),
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "משך ריצה ברקע: חלון ה-broadcast בלבד (כ-10 עד 60 שניות) בעזרת goAsync, ולכן העבודה חייבת להיות קצרה.",
                "שליחה לשרת: כן.",
                "האזנה לרשת: אין תנאי רשת מובנה. אם אין חיבור, הריצה נרשמת ככישלון והריצה הבאה מנסה שוב.",
                "דיוק זמן: מדויק (setExact) ב-API 19 עד 30.",
                "תצוגה ללקוח: רץ ברקע ללא תצוגה.",
                "מצב חיסכון סוללה / Doze: ב-19-22 אין Doze. מ-23 ומעלה setExact נדחה לחלון התחזוקה.",
                "Force stop: האזעקה מבוטלת, ותיווצר מחדש רק בפתיחה או אחרי הדלקה.",
            ) else listOf(
                "Background runtime: only the broadcast window (about 10 to 60 seconds) via goAsync, so the work must be short.",
                "Sends to the server: yes.",
                "Listens for network: no built-in network constraint. If there is no connection, the run is recorded as a failure and the next run tries again.",
                "Timing: exact (setExact) on API 19 to 30.",
                "Shown to the user: runs in the background with no UI.",
                "Battery saver / Doze: on 19-22 there is no Doze. From 23, setExact is deferred to the maintenance window.",
                "Force stop: the alarm is cancelled and re-created only on the next open or after a boot.",
            ),
        ),
        GuideItem(
            title = tr(heb, "BootReceiver — האזנה להדלקת המכשיר", "BootReceiver — listens for device boot"),
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "משך ריצה ברקע: קצר. רק מתעד את ההדלקה ומתזמן מחדש.",
                "שליחה לשרת: לא. הוא לא סורק ולא שולח, רק מחזיר את התזמון לפעולה.",
                "תצוגה ללקוח: רץ ברקע ללא תצוגה.",
                "מגבלה: Android מעביר BOOT_COMPLETED רק לאפליקציה שנפתחה לפחות פעם אחת ולא עברה force stop.",
            ) else listOf(
                "Background runtime: short. It only records the boot and re-schedules.",
                "Sends to the server: no. It does not scan or send, only brings the schedule back.",
                "Shown to the user: runs in the background with no UI.",
                "Limitation: Android delivers BOOT_COMPLETED only to an app opened at least once and not force-stopped.",
            ),
        ),
        GuideItem(
            title = tr(heb, "ScanRunner — העבודה עצמה", "ScanRunner — the work itself"),
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "לא רכיב מערכת, אלא הקוד המשותף שכל מתזמן קורא לו: סורק את האפליקציות, שולח את המערך, ומתעד את התוצאה ואת משך הזמן.",
                "חוסם (blocking), ולכן מורץ תמיד מחוץ ל-main thread.",
            ) else listOf(
                "Not a system component, but the shared code every scheduler calls: it scans the apps, sends the array, and records the result and the duration.",
                "Blocking, so it always runs off the main thread.",
            ),
        ),
        GuideItem(
            title = tr(heb, "MainActivity — המסך", "MainActivity — the screen"),
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "רץ רק כשהאפליקציה פתוחה. אחראי להצגה בלבד.",
                "בכל פתיחה מוודא שהתזמון קיים, ואם לא, יוצר אותו מחדש.",
                "ריצת הרקע לא תלויה בו: היא ממשיכה גם כשהמסך סגור.",
            ) else listOf(
                "Runs only while the app is open. Responsible for display only.",
                "On every open it makes sure the schedule exists, and if not, re-creates it.",
                "The background run does not depend on it: it continues even when the screen is closed.",
            ),
        ),
    )

    /** What changed in each Android version, how it affects this app, and the official source. */
    fun androidVersions(heb: Boolean): List<GuideItem> = listOf(
        GuideItem(
            title = "Android 4.4–5.1 (API 19-22)",
            tone = Tone.OK,
            lines = if (heb) listOf(
                "אין Doze ואין הגבלות רקע. אזעקות מדויקות עובדות ללא הרשאה.",
                "השפעה: הסביבה המתירנית ביותר. ב-19-20 האפליקציה משתמשת ב-AlarmManager מדויק.",
            ) else listOf(
                "No Doze and no background limits. Exact alarms work without a permission.",
                "Effect: the most permissive environment. On 19-20 the app uses an exact AlarmManager.",
            ),
            actions = listOf(docSource("$DOC/develop/background-work/background-tasks", heb)),
        ),
        GuideItem(
            title = tr(heb, "Android 6 (API 23) — Doze ו-App Standby", "Android 6 (API 23) — Doze and App Standby"),
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "כשהמכשיר נייח, מנותק ומסך כבוי, הוא נכנס ל-Doze: משימות, אזעקות וגישה לרשת נדחות לחלונות תחזוקה.",
                "נוספה רשימת פטור מאופטימיזציית סוללה.",
                "השפעה: ריצות מתוזמנות עלולות להתעכב כשהמכשיר במנוחה.",
            ) else listOf(
                "When the device is stationary, unplugged and the screen is off, it enters Doze: tasks, alarms and network access are deferred to maintenance windows.",
                "A battery-optimization exemption list was added.",
                "Effect: scheduled runs may be delayed when the device is at rest.",
            ),
            actions = listOf(docSource("$DOC/training/monitoring-device-state/doze-standby", heb)),
        ),
        GuideItem(
            title = tr(heb, "Android 7 (API 24-25) — Doze גם בתנועה", "Android 7 (API 24-25) — Doze even while moving"),
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "Doze קל מופעל גם כשהמכשיר בתנועה, אחרי זמן קצר של מסך כבוי.",
                "חלק מה-broadcasts המשתמעים (כמו שינוי חיבור רשת) כבר לא מגיעים ל-receiver במניפסט.",
                "השפעה: עיכובים נפוצים יותר.",
            ) else listOf(
                "Light Doze is triggered even while the device is moving, after a short time with the screen off.",
                "Some implicit broadcasts (such as a network-connection change) no longer reach a manifest receiver.",
                "Effect: delays are more common.",
            ),
            actions = listOf(docSource("$DOC/topic/performance/background-optimization", heb)),
        ),
        GuideItem(
            title = tr(heb, "Android 8 (API 26) — הגבלות רקע", "Android 8 (API 26) — background limits"),
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "אפליקציה ברקע לא יכולה להפעיל שירותים חופשית, ורוב ה-broadcasts המשתמעים במניפסט בוטלו (BOOT_COMPLETED עדיין מותר).",
                "השפעה: חובה לעבוד דרך JobScheduler. אצלנו זה ממילא כך, וה-BootReceiver מותר.",
            ) else listOf(
                "A background app cannot start services freely, and most implicit manifest broadcasts were removed (BOOT_COMPLETED is still allowed).",
                "Effect: work must go through JobScheduler. That is already our case, and the BootReceiver is allowed.",
            ),
            actions = listOf(docSource("$DOC/about/versions/oreo/background", heb)),
        ),
        GuideItem(
            title = tr(heb, "Android 9 (API 28) — דליי סטנדבי (Buckets)", "Android 9 (API 28) — standby buckets"),
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "האפליקציות מחולקות לדליים לפי תדירות שימוש, ומשימות ואזעקות מווסתות לפי הדלי.",
                "השפעה: אפליקציה שלא בשימוש תרוץ בתדירות נמוכה יותר, והמרווח בפועל עלול להתארך.",
            ) else listOf(
                "Apps are split into buckets by usage frequency, and tasks and alarms are throttled by the bucket.",
                "Effect: an app that is not used runs less often, and the actual interval may stretch.",
            ),
            actions = listOf(docSource("$DOC/topic/performance/appstandby", heb)),
        ),
        GuideItem(
            title = "Android 10-11 (API 29-30)",
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "API 29: הגבלה על הפעלת מסך מהרקע (אנחנו לא עושים זאת).",
                "API 30: כדי לראות את כל האפליקציות צריך QUERY_ALL_PACKAGES (קיים אצלנו). Google Play מגבילה את ההרשאה לשימושים מאושרים.",
                "השפעה: הסריקה המלאה תלויה ב-QAP.",
            ) else listOf(
                "API 29: a limit on starting a screen from the background (we do not do that).",
                "API 30: to see all apps you need QUERY_ALL_PACKAGES (we have it). Google Play restricts the permission to approved uses.",
                "Effect: the full scan depends on QAP.",
            ),
            actions = listOf(
                docSource("$DOC/guide/components/activities/background-starts", heb, "מקור: הפעלה מהרקע (API 29)", "Source: background starts (API 29)"),
                docSource("$DOC/training/package-visibility", heb, "מקור: QUERY_ALL_PACKAGES (API 30)", "Source: QUERY_ALL_PACKAGES (API 30)"),
            ),
        ),
        GuideItem(
            title = tr(heb, "Android 12 (API 31) — אזעקות מדויקות", "Android 12 (API 31) — exact alarms"),
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "אזעקה מדויקת דורשת את ההרשאה SCHEDULE_EXACT_ALARM. גם הפעלת foreground service מהרקע הוגבלה.",
                "השפעה: רק אם נפעיל תזמון מדויק נצטרך את ההרשאה. ברירת המחדל שלנו לא מדויקת.",
            ) else listOf(
                "An exact alarm requires the SCHEDULE_EXACT_ALARM permission. Starting a foreground service from the background was also restricted.",
                "Effect: only if we enable exact scheduling do we need the permission. Our default is not exact.",
            ),
            actions = listOf(docSource("$DOC/develop/background-work/services/alarms/schedule", heb)),
        ),
        GuideItem(
            title = "Android 13 (API 33)",
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "התראות דורשות הרשאת POST_NOTIFICATIONS (אנחנו לא מציגים התראות).",
                "השפעה: זניחה על אפליקציה זו.",
            ) else listOf(
                "Notifications require the POST_NOTIFICATIONS permission (we do not show notifications).",
                "Effect: negligible for this app.",
            ),
            actions = listOf(docSource("$DOC/develop/ui/views/notifications/notification-permission", heb)),
        ),
        GuideItem(
            title = "Android 14 (API 34)",
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "אזעקות מדויקות אסורות כברירת מחדל להתקנה חדשה, והמשתמש צריך לאשר ידנית.",
                "משימה שחוסמת את ה-main thread גורמת ל-ANR.",
                "משימה עם תנאי רשת מחייבת את ההרשאה ACCESS_NETWORK_STATE (מוצהרת אצלנו).",
            ) else listOf(
                "Exact alarms are off by default for a new install, and the user must allow them manually.",
                "A task that blocks the main thread causes an ANR.",
                "A task with a network constraint requires the ACCESS_NETWORK_STATE permission (declared in our app).",
            ),
            actions = listOf(
                docSource("$DOC/about/versions/14/behavior-changes-14", heb, "מקור: שינויי Android 14", "Source: Android 14 changes"),
                docSource("$DOC/about/versions/14/changes/schedule-exact-alarms", heb, "מקור: אזעקות מדויקות ב-Android 14", "Source: exact alarms in Android 14"),
            ),
        ),
        GuideItem(
            title = tr(heb, "Android 15 ומעלה (API 35+)", "Android 15 and up (API 35+)"),
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "המערכת מקבצת משימות רקע שצורכות רשת, כך שריצה מוכנה יכולה להתעכב עד כ-31 דקות, וכשהמסך דלוק להמתין עד שהמסך ייכבה.",
                "השפעה: טווח הריצה הבאה מתרחב. זו הסיבה שהמסך הראשי מציג טווח ולא זמן יחיד.",
            ) else listOf(
                "The system batches background network tasks, so a ready run may be delayed up to about 31 minutes, and while the screen is on it waits until the screen turns off.",
                "Effect: the next-run range widens. That is why the main screen shows a range, not a single time.",
            ),
            actions = listOf(docSource("$DOC/about/versions/15/behavior-changes-all", heb)),
        ),
    )

    /** How often background runs can happen and how late they can be, with official sources. */
    fun frequency(heb: Boolean): List<GuideItem> = listOf(
        GuideItem(
            title = tr(heb, "מרווח מינימלי — אין אצלנו", "Minimum interval — none for us"),
            tone = Tone.OK,
            lines = if (heb) listOf(
                "המינימום הידוע של 15 דקות חל רק על משימות מחזוריות (PeriodicWork ב-WorkManager / JobScheduler מחזורי).",
                "אנחנו משתמשים במשימות חד-פעמיות שמתזמנות את עצמן מחדש, ולכן אין להן מינימום — אפשר לתזמן כל מספר דקות, והאפליקציה לא חוסמת שום ערך.",
            ) else listOf(
                "The well-known 15-minute minimum applies only to periodic work (WorkManager PeriodicWork / periodic JobScheduler).",
                "We use one-time, self-rescheduling jobs, which have no such minimum — any number of minutes is allowed, and the app blocks no value.",
            ),
            actions = listOf(docSource("$DOC/reference/androidx/work/PeriodicWorkRequest", heb, "מקור: מינימום מחזורי (15 דקות)", "Source: periodic minimum (15 min)")),
        ),
        GuideItem(
            title = tr(heb, "עיכוב מקסימלי — לא מובטח", "Maximum delay — not guaranteed"),
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "Android לא מבטיח מתי משימה נדחית תרוץ; היא רצה לפי שיקול המערכת.",
                "מה שמעכב: Doze דוחה לחלונות תחזוקה, דליי הסטנדבי מגבילים את זמן הריצה, ומ-Android 15 משימות רשת מקובצות (עד כ-31 דקות).",
                "לכן אין מספר רשמי ל\"עיכוב מקסימלי\". ספירת הפספוסים במסך הראשי משתמשת בטווח סובלנות לפי הגרסה, לא בהבטחה.",
            ) else listOf(
                "Android does not promise when a deferrable job runs; it runs at the system's discretion.",
                "What delays it: Doze defers to maintenance windows, App Standby buckets cap run time, and from Android 15 network jobs are batched (up to ~31 min).",
                "So there is no official \"maximum delay\" number. The missed-runs counter on the main screen uses a per-version tolerance, not a guarantee.",
            ),
            actions = listOf(docSource("$DOC/reference/android/app/job/JobScheduler", heb, "מקור: JobScheduler", "Source: JobScheduler")),
        ),
        GuideItem(
            title = tr(heb, "דליי סטנדבי (מ-API 28) — המגבלה האמיתית על התדירות", "App Standby buckets (from API 28) — the real frequency limit"),
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "הדלי קובע כמה זמן ריצה מצטבר מותר למשימות (לא מרווח מינימלי), והמגבלות חלות רק כשלא בטעינה:",
                "פעיל: עד 20 דקות בכל 60 דקות (מ-Android 16; לפני כן ללא הגבלה).",
                "קבוצת עבודה: עד 10 דקות בכל 4 שעות.",
                "תכוף: עד 10 דקות בכל 12 שעות.",
                "נדיר: עד 10 דקות בכל 24 שעות.",
                "מוגבל: פעם ביום עד 10 דקות.",
                "טעינה מבטלת את ההגבלות. הערכים אינם הבטחה ועשויים להשתנות בין גרסאות.",
            ) else listOf(
                "The bucket caps total job run time (not a minimum interval), and the limits apply only off charger:",
                "Active: up to 20 minutes per rolling 60 minutes (from Android 16; before that, no limit).",
                "Working set: up to 10 minutes per rolling 4 hours.",
                "Frequent: up to 10 minutes per rolling 12 hours.",
                "Rare: up to 10 minutes per rolling 24 hours.",
                "Restricted: once a day for up to 10 minutes.",
                "Charging removes the limits. The values are not a guarantee and may change between releases.",
            ),
            actions = listOf(docSource("$DOC/topic/performance/power/power-details", heb, "מקור: מגבלות לפי דלי", "Source: per-bucket limits")),
        ),
    )

    /** Screen-off specifically: where it matters, where it does not, and what can be done. */
    fun screenOff(heb: Boolean): List<GuideItem> = listOf(
        GuideItem(
            title = tr(heb, "מסך כבוי לבדו — לא חוסם באף גרסה", "Screen off alone — blocks on no version"),
            tone = Tone.OK,
            lines = if (heb) listOf(
                "אין אף גרסת Android שבה כיבוי המסך לבדו עוצר ריצת רקע. JobScheduler (API 21+) ו-AlarmManager (API 19-20) תוכננו בדיוק כדי לרוץ כשהמסך כבוי.",
                "בפועל הריצות המתוזמנות שלנו רצות גם כשהמסך כבוי. מה שמשפיע הוא לא המסך עצמו, אלא Doze, דליי הסטנדבי, והיצרן.",
            ) else listOf(
                "There is no Android version where turning the screen off by itself stops a background run. JobScheduler (API 21+) and AlarmManager (API 19-20) were built to run with the screen off.",
                "In practice our scheduled runs run with the screen off too. What matters is not the screen itself, but Doze, standby buckets, and the manufacturer.",
            ),
        ),
        GuideItem(
            title = tr(heb, "Doze (מ-Android 6 / API 23) — מסך כבוי הוא רק תנאי אחד", "Doze (from Android 6 / API 23) — screen off is only one condition"),
            tone = Tone.WARN,
            lines = if (heb) listOf(
                "Doze נכנס כשהמסך כבוי והמכשיר מנותק מהטעינה לאורך זמן. ב-Android 6 נדרש גם שהמכשיר יהיה נייח; מ-Android 7 הוא נכנס גם בתנועה. מסך כבוי לבדו לא מספיק.",
                "כשהוא פעיל, ריצות וגישה לרשת נדחות לחלונות תחזוקה. מכאן העיכוב של הריצה המתוזמנת כשהמכשיר מונח ללא שימוש.",
                "מה אפשר לעשות: להוציא את האפליקציה מאופטימיזציית סוללה (כפתור בקיצורי הדרך למטה). חיבור לטעינה מבטל את Doze.",
            ) else listOf(
                "Doze starts when the screen is off and the device is unplugged for a while. On Android 6 the device must also be stationary; from Android 7 it starts even while moving. Screen off alone is not enough.",
                "When active, runs and network access are deferred to maintenance windows. That is where the delay of a scheduled run comes from when the device is left at rest.",
                "What to do: remove the app from battery optimization (button in the shortcuts below). Plugging in to charge disables Doze.",
            ),
        ),
        GuideItem(
            title = tr(heb, "Android 15+ (API 35) — מסך כבוי דווקא עוזר", "Android 15+ (API 35) — screen off actually helps"),
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "כאן קיבוץ משימות הרשת עלול לעכב ריצה דווקא כשהמסך דלוק, ולשחרר אותה כשהמסך נכבה.",
                "כלומר במכשירים חדשים כיבוי המסך משחק לטובתנו: זה לרוב הרגע שבו הריצה המקובצת יוצאת לפועל.",
            ) else listOf(
                "Here the network-task batching may delay a run precisely while the screen is on, and release it when the screen turns off.",
                "So on new devices turning the screen off works in our favor: that is usually the moment the batched run fires.",
            ),
        ),
        GuideItem(
            title = tr(heb, "יצרנים — כאן הסכנה האמיתית של מסך כבוי", "Manufacturers — the real screen-off danger"),
            tone = Tone.BAD,
            lines = if (heb) listOf(
                "ההשפעה החמורה ביותר של מסך כבוי מגיעה מהיצרן, לא מ-Android. יצרנים כמו Xiaomi, Huawei, Oppo ו-Vivo, ובמידה פחותה Samsung, סוגרים אפליקציות רקע זמן קצר אחרי שהמסך נכבה או המכשיר ננעל, אם האפליקציה לא ברשימת ההיתר.",
                "אם התזמון נסגר כך, הוא יתחדש רק בפתיחה הבאה של האפליקציה או אחרי הדלקה.",
                "מה אפשר לעשות: להגדיר סוללה \"ללא הגבלה\", ובמכשירים שיש בהם \"הפעלה אוטומטית\" (Xiaomi, Oppo, Realme, Vivo) גם לאשר אותה, לפי ההוראות בחלק היצרנים שבהמשך.",
            ) else listOf(
                "The most severe screen-off effect comes from the manufacturer, not from Android. Manufacturers such as Xiaomi, Huawei, Oppo and Vivo, and to a lesser extent Samsung, close background apps shortly after the screen turns off or the device locks, if the app is not allowlisted.",
                "If the schedule is closed this way, it is restored only on the next app open or after a boot.",
                "What to do: set the battery to \"Unrestricted\", and on devices that have \"Autostart\" (Xiaomi, Oppo, Realme, Vivo) also enable it, per the instructions in the manufacturers section below.",
            ),
        ),
        GuideItem(
            title = tr(heb, "הרכיבים שלנו כשהמסך כבוי", "Our components with the screen off"),
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "ScanJobService ו-AlarmReceiver: רצים כשהמסך כבוי, בכפוף ל-Doze וליצרן.",
                "אין לנו foreground service. זו הדרך היחידה להבטיח ריצה מיידית במסך כבוי, אבל היא דורשת התראה קבועה וסוג שירות מוצהר מ-Android 14, ולכן היא מחוץ לגבולות ה-POC.",
                "דרכים נוספות לריצה מיידית במסך כבוי, גם הן מחוץ ל-POC: הודעת דחיפה בעדיפות גבוהה (FCM) שמעירה את האפליקציה גם ב-Doze, או אזעקה מדויקת allow-while-idle (מוגבלת לכ-פעם ב-9 דקות ב-Doze, ודורשת הרשאה מ-Android 12).",
            ) else listOf(
                "ScanJobService and AlarmReceiver: run with the screen off, subject to Doze and the manufacturer.",
                "We have no foreground service. That is the only way to guarantee an immediate run with the screen off, but it requires a persistent notification and a declared service type from Android 14, so it is outside the POC scope.",
                "Other ways for an immediate run with the screen off, also outside the POC: a high-priority push message (FCM) that wakes the app even in Doze, or an allow-while-idle exact alarm (limited to about once per 9 minutes in Doze, and requiring a permission from Android 12).",
            ),
        ),
    )

    private data class Oem(
        val keys: List<String>,
        val display: String,
        val slug: String,
        val officialUrl: String?,
        val aggressive: Boolean,
        /** Whether the manufacturer itself officially documents the background-kill behavior (not just the setting). */
        val documented: Boolean,
        val summaryHe: String,
        val summaryEn: String,
        val stepsHe: List<String>,
        val stepsEn: List<String>,
    )

    // Official links verified reachable. Samsung/Xiaomi/Huawei officially document the behavior itself;
    // ColorOS/Vivo document the settings, while the aggressive killing is community-observed (dontkillmyapp).
    private val oems = listOf(
        Oem(
            keys = listOf("google"), display = "Google (Pixel)", slug = "google", officialUrl = null,
            aggressive = false, documented = false,
            summaryHe = "Android נקי. אין סגירת רקע אגרסיבית של היצרן; פועלות רק המגבלות הסטנדרטיות של Android (Doze, App Standby, אופטימיזציית סוללה). אין כאן \"הפעלה אוטומטית\" (Autostart), ואין צורך בה.",
            summaryEn = "Clean Android. No aggressive background killing by the manufacturer; only the standard Android limits apply (Doze, App Standby, battery optimization). There is no \"Autostart\" here, and none is needed.",
            stepsHe = listOf("לרוב לא נדרשת פעולה. ליתר ביטחון: פתח הגדרות, עבור לאפליקציות, בחר את האפליקציה, פתח סוללה ובחר \"ללא הגבלה\"."),
            stepsEn = listOf("Usually no action is needed. For extra safety: open Settings, go to Apps, pick the app, open Battery and choose \"Unrestricted\"."),
        ),
        Oem(
            keys = listOf("samsung"), display = "Samsung (One UI)", slug = "samsung",
            officialUrl = "https://www.samsung.com/us/support/answer/ANS10003442/",
            aggressive = true, documented = true,
            summaryHe = "One UI מעביר אפליקציות שלא בשימוש ל\"שינה\" או \"שינה עמוקה\" וסוגר אותן. אין כאן Autostart; הפתרון הוא רשימת ההחרגה ומצב סוללה לא מוגבל. סמסונג מתעדת רשמית ש\"אפליקציות בשינה עמוקה\" לעולם לא רצות ברקע.",
            summaryEn = "One UI moves unused apps to \"sleeping\" or \"deep sleeping\" and stops them. There is no Autostart here; the fix is the exclusion list and an unrestricted battery setting. Samsung officially documents that \"deep sleeping apps\" never run in the background.",
            stepsHe = listOf(
                "פתח הגדרות, עבור לאפליקציות, בחר את האפליקציה, פתח סוללה ובחר \"ללא הגבלה\".",
                "בהגדרות, עבור לסוללה ואז למגבלות שימוש ברקע, והסר את האפליקציה מ\"אפליקציות בשינה\" ו\"אפליקציות בשינה עמוקה\".",
                "כבה את \"הסרת הרשאות אם האפליקציה לא בשימוש\" עבור האפליקציה.",
            ),
            stepsEn = listOf(
                "Open Settings, go to Apps, pick the app, open Battery and choose \"Unrestricted\".",
                "In Settings, go to Battery then Background usage limits, and remove the app from \"Sleeping apps\" and \"Deep sleeping apps\".",
                "Turn off \"Remove permissions if app isn't used\" for the app.",
            ),
        ),
        Oem(
            keys = listOf("xiaomi", "redmi", "poco"), display = "Xiaomi (MIUI / HyperOS)", slug = "xiaomi",
            officialUrl = "https://www.mi.com/global/support/faq/details/KA-507608/",
            aggressive = true, documented = true,
            summaryHe = "MIUI / HyperOS אגרסיבי. דורש \"הפעלה אוטומטית\" (Autostart) באפליקציית האבטחה, ומצב סוללה \"ללא הגבלות\". התיעוד הרשמי של Xiaomi מציין שהפעלה אוטומטית חסומה כברירת מחדל.",
            summaryEn = "MIUI / HyperOS is aggressive. It requires \"Autostart\" in the Security app, and a battery mode of \"No restrictions\". Xiaomi's official docs state that autostart is denied by default.",
            stepsHe = listOf(
                "פתח את אפליקציית האבטחה (Security), עבור להרשאות, פתח \"הפעלה אוטומטית\" (Autostart) והפעל עבור האפליקציה.",
                "בהגדרות האפליקציה בחר חיסכון סוללה ובחר \"ללא הגבלות\".",
                "במסך האפליקציות האחרונות החזק את האפליקציה ונעל אותה.",
            ),
            stepsEn = listOf(
                "Open the Security app, go to Permissions, open \"Autostart\" and enable it for the app.",
                "In the app's settings choose Battery saver and pick \"No restrictions\".",
                "In Recent apps, hold the app and lock it.",
            ),
        ),
        Oem(
            keys = listOf("huawei", "honor"), display = "Huawei / Honor (EMUI)", slug = "huawei",
            officialUrl = "https://consumer.huawei.com/en/support/content/en-us00420068/",
            aggressive = true, documented = true,
            summaryHe = "EMUI אגרסיבי. במקום Autostart רגיל יש \"הפעלת אפליקציות\" שצריך לנהל ידנית. Huawei מתעדת רשמית שהאפליקציה \"עלולה להיסגר על ידי הטלפון כשהיא ברקע\".",
            summaryEn = "EMUI is aggressive. Instead of a regular Autostart there is \"App launch\" that must be managed manually. Huawei officially documents that the app \"may have been cleared by your phone when it was running in the background\".",
            stepsHe = listOf(
                "פתח הגדרות, עבור לסוללה, היכנס ל\"הפעלת אפליקציות\" (App launch), בחר את האפליקציה, עבור לניהול ידני והפעל את כל האפשרויות (הפעלה אוטומטית, הפעלה משנית, פעילות ברקע).",
            ),
            stepsEn = listOf(
                "Open Settings, go to Battery, open \"App launch\", pick the app, switch to manual management and enable all options (Auto-launch, Secondary launch, Run in background).",
            ),
        ),
        Oem(
            keys = listOf("oneplus", "oppo", "realme"), display = "OnePlus / Oppo / Realme (ColorOS)", slug = "oppo",
            officialUrl = "https://support.oppo.com/en/answer/?aid=2020322",
            aggressive = true, documented = false,
            summaryHe = "ColorOS / OxygenOS אגרסיבי. דורש \"הפעלה אוטומטית\" (Auto-launch), אישור פעילות ברקע, וכיבוי אופטימיזציית שינה. היצרן מתעד את ההגדרות; סגירת הרקע האגרסיבית מבוססת על בדיקות הקהילה.",
            summaryEn = "ColorOS / OxygenOS is aggressive. It requires \"Auto-launch\", allowing background activity, and turning off sleep optimization. The vendor documents the settings; the aggressive killing is based on community testing.",
            stepsHe = listOf(
                "פתח הגדרות, עבור לסוללה, בחר את האפליקציה ואפשר פעילות ברקע.",
                "בהגדרות, פתח את ניהול ההפעלה האוטומטית (Auto-launch / Startup manager) והפעל עבור האפליקציה.",
                "במסך האחרונות נעל את האפליקציה.",
            ),
            stepsEn = listOf(
                "Open Settings, go to Battery, pick the app and allow background activity.",
                "In Settings, open Auto-launch / Startup manager and enable it for the app.",
                "In Recent apps, lock the app.",
            ),
        ),
        Oem(
            keys = listOf("vivo", "iqoo"), display = "Vivo / iQOO (Funtouch / OriginOS)", slug = "vivo",
            officialUrl = "https://www.vivo.com/en/support",
            aggressive = true, documented = false,
            summaryHe = "Funtouch / OriginOS אגרסיבי. דורש \"הפעלה אוטומטית\" (Autostart) ואישור צריכת חשמל גבוהה ברקע. היצרן מתעד את ההגדרות; סגירת הרקע האגרסיבית מבוססת על בדיקות הקהילה.",
            summaryEn = "Funtouch / OriginOS is aggressive. It requires \"Autostart\" and allowing high background power consumption. The vendor documents the settings; the aggressive killing is based on community testing.",
            stepsHe = listOf(
                "פתח הגדרות, עבור לסוללה, אפשר \"צריכת חשמל גבוהה ברקע\" עבור האפליקציה.",
                "בהגדרות, פתח \"הפעלה אוטומטית\" (Autostart) והפעל עבור האפליקציה.",
            ),
            stepsEn = listOf(
                "Open Settings, go to Battery, allow \"High background power consumption\" for the app.",
                "In Settings, open \"Autostart\" and enable it for the app.",
            ),
        ),
    )

    /** OEM cards, with the current device's manufacturer first and highlighted. */
    fun manufacturers(heb: Boolean, manufacturer: String): List<GuideItem> {
        val key = manufacturer.lowercase()
        val detected = oems.firstOrNull { oem -> oem.keys.any { key.contains(it) } }
        val items = mutableListOf(whatEachSettingDoes(heb))

        if (detected != null) {
            items += oemItem(detected, detectedHere = true, manufacturer = manufacturer, heb = heb)
        } else {
            items += GuideItem(
                title = tr(heb, "המכשיר שלך: $manufacturer", "Your device: $manufacturer"),
                tone = Tone.WARN,
                lines = if (heb) listOf(
                    "אין כאן כרטיס ייעודי ליצרן הזה. חלק מהיצרנים (Pixel של Google, וגם Motorola ו-Sony) קרובים ל-Android נקי וכמעט לא סוגרים רקע; אחרים (Xiaomi, Oppo, Vivo) אגרסיביים ודורשים אישורים ידניים.",
                    "פתח את המדריך עם התמונות לדגם שלך כדי לבדוק אם נדרשת פעולה.",
                ) else listOf(
                    "There is no dedicated card for this manufacturer. Some manufacturers (Google Pixel, and also Motorola and Sony) are close to clean Android and barely kill background apps; others (Xiaomi, Oppo, Vivo) are aggressive and require manual approvals.",
                    "Open the guide with screenshots for your model to check whether action is needed.",
                ),
                steps = if (heb) listOf(
                    "פתח הגדרות, עבור לאפליקציות, בחר את האפליקציה, פתח סוללה ואפשר פעילות ברקע / \"ללא הגבלה\".",
                    "הוצא את האפליקציה מאופטימיזציית סוללה.",
                    "רק אם למכשיר יש \"הפעלה אוטומטית\" (Autostart) — קיים ב-Xiaomi, Oppo, Realme ו-Vivo אך לא ב-Pixel — הפעל אותה עבור האפליקציה.",
                ) else listOf(
                    "Open Settings, go to Apps, pick the app, open Battery and allow background activity / \"Unrestricted\".",
                    "Remove the app from battery optimization.",
                    "Only if the device has \"Autostart\" (present on Xiaomi, Oppo, Realme and Vivo but not Pixel) — enable it for the app.",
                ),
                actions = listOf(dkmaAction(heb, "$DKMA_BASE/${dkmaSlug(key)}")),
            )
        }

        oems.filter { it != detected }.forEach { items += oemItem(it, detectedHere = false, manufacturer = manufacturer, heb = heb) }

        items += GuideItem(
            title = tr(heb, "כל היצרנים", "All manufacturers"),
            tone = Tone.INFO,
            lines = listOf(tr(heb,
                "פרויקט \"Don't kill my app\" מרכז הוראות מעודכנות עם תמונות לכל יצרן ודגם.",
                "The \"Don't kill my app\" project gathers up-to-date instructions with screenshots for every manufacturer and model.")),
            actions = listOf(GuideAction(tr(heb, "פתח את dontkillmyapp.com", "Open dontkillmyapp.com"), ActionType.OPEN_URL, DKMA_BASE)),
        )
        return items
    }

    /** Explains the purpose of each dontkillmyapp setting, whether it affects our code, and the cost of skipping it. */
    private fun whatEachSettingDoes(heb: Boolean): GuideItem = GuideItem(
        title = tr(heb, "מה כל הגדרה עושה, והאם זה נוגע לקוד שלנו", "What each setting does, and whether it touches our code"),
        tone = Tone.INFO,
        lines = if (heb) listOf(
            "ההגדרות שב-\"Don't kill my app\" לא מופיעות בתיעוד הרשמי של Android, כי הן תוספות של היצרנים מעל Android. הן נוגעות לנו ישירות, כי הריצה ברקע שלנו (JobScheduler/AlarmManager) וה-BootReceiver תלויים בכך שהמערכת תרשה לאפליקציה לפעול ברקע.",
            "הפעלה אוטומטית (Autostart / Auto-launch): מתירה לאפליקציה ולרכיביה להיפתח ברקע בלי שהמשתמש יפתח אותה. אם לא מפעילים: אחרי שהיצרן סוגר את האפליקציה או אחרי הדלקה, הסריקה המתוזמנת וה-BootReceiver עלולים לא לרוץ עד שתפתח את האפליקציה ידנית.",
            "סוללה \"ללא הגבלה\" / הוצאה מאופטימיזציה: מונעת מהמערכת להרוג את התהליך ולדחות משימות. אם לא מפעילים: הריצות יתעכבו, ולעיתים התהליך ייסגר זמן קצר אחרי כיבוי המסך.",
            "הוצאה מ\"אפליקציות בשינה\" (Samsung): מונעת העברה ל\"שינה עמוקה\" שבה משימות הרקע כלל לא רצות. אם לא מפעילים: אחרי יום-יומיים ללא שימוש, הסריקה תפסיק לרוץ.",
            "נעילת האפליקציה במסך האחרונות: מונעת סגירה בניקוי זיכרון או ב-swipe. אם לא מפעילים: סגירה ידנית או לחץ זיכרון יעצרו את הרקע עד הפתיחה הבאה.",
            "ההשלכה המשותפת: בכל אלה, כשהיצרן עוצר אותנו, המצב במסך לא יתקדם (זמן אחרון/הבא), ולפי הלוגיקה שלנו התזמון יתחדש רק בפתיחה הבאה או אחרי הדלקה. אין לנו שומר רקע רציף שיחזיר ריצה שהיצרן הרג בזמן שהאפליקציה סגורה.",
            "סטטוס המידע: מגבלות הפלטפורמה (Doze, סטנדבי) מתועדות רשמית על ידי Google (קישורים בכרטיסי הגרסאות). התנהגות היצרנים מתועדת חלקית על ידם (ההגדרות); סגירת הרקע האגרסיבית מבוססת בעיקר על בדיקות הקהילה (dontkillmyapp) ומשתנה בין דגמים, גרסאות ו-ROM.",
        ) else listOf(
            "The settings in \"Don't kill my app\" do not appear in the official Android documentation, because they are manufacturer additions on top of Android. They touch us directly, because our background run (JobScheduler/AlarmManager) and the BootReceiver depend on the system allowing the app to run in the background.",
            "Autostart / Auto-launch: lets the app and its components open in the background without the user opening them. If not enabled: after the manufacturer closes the app or after a boot, the scheduled scan and the BootReceiver may not run until you open the app manually.",
            "Battery \"Unrestricted\" / remove from optimization: prevents the system from killing the process and deferring tasks. If not enabled: runs are delayed, and sometimes the process is closed shortly after the screen turns off.",
            "Remove from \"sleeping apps\" (Samsung): prevents moving to \"deep sleep\" where background tasks do not run at all. If not enabled: after a day or two without use, the scan stops running.",
            "Lock the app in Recent apps: prevents closing by memory cleanup or swipe. If not enabled: a manual close or memory pressure stop the background until the next open.",
            "The shared consequence: in all of these, when the manufacturer stops us, the on-screen state does not advance (last/next time), and per our logic the schedule revives only on the next app open or after a boot. We have no continuous background watchdog that can revive a run the manufacturer killed while the app was closed.",
            "Information status: the platform limits (Doze, standby) are officially documented by Google (links on the version cards). Manufacturer behavior is partly documented by them (the settings); the aggressive background killing is based mainly on community testing (dontkillmyapp) and varies by model, version and ROM.",
        ),
        actions = listOf(docSource("$DOC/topic/performance/background-optimization", heb, "רקע ב-Android (מקור רשמי)", "Android background (official source)")),
    )

    private fun oemItem(oem: Oem, detectedHere: Boolean, manufacturer: String, heb: Boolean): GuideItem {
        val tone = when {
            !oem.aggressive -> Tone.OK
            detectedHere -> Tone.BAD
            else -> Tone.WARN
        }
        val lines = mutableListOf(if (heb) oem.summaryHe else oem.summaryEn)
        lines += statusLine(oem, heb)
        if (oem.aggressive) {
            lines += tr(heb,
                "אם לא תבצע את השלבים: היצרן יסגור את האפליקציה כשהיא ברקע, הריצות המתוזמנות ייפסקו, וה-BootReceiver עלול לא לרוץ אחרי הדלקה — עד שתפתח שוב את האפליקציה.",
                "If you don't do the steps: the manufacturer will close the app when it is in the background, the scheduled runs will stop, and the BootReceiver may not run after a boot — until you open the app again.")
        }
        if (detectedHere && oem.aggressive) {
            lines += tr(heb,
                "זהו היצרן של המכשיר הזה, לכן סביר שתצטרך לאשר ידנית פעילות ברקע כדי שהריצות לא ייעצרו.",
                "This is the manufacturer of this device, so you will likely need to allow background activity manually so the runs are not stopped.")
        }

        val actions = mutableListOf<GuideAction>()
        oem.officialUrl?.let {
            actions += GuideAction(tr(heb, "מקור רשמי של היצרן", "Official manufacturer source"), ActionType.OPEN_URL, it)
        }
        val slug = if (detectedHere) dkmaSlug(manufacturer.lowercase()) else oem.slug
        actions += dkmaAction(heb, "$DKMA_BASE/$slug")

        return GuideItem(
            title = if (detectedHere) tr(heb, "המכשיר שלך: ${oem.display}", "Your device: ${oem.display}") else oem.display,
            tone = tone,
            lines = lines,
            steps = if (heb) oem.stepsHe else oem.stepsEn,
            actions = actions,
        )
    }

    private fun statusLine(oem: Oem, heb: Boolean): String = when {
        !oem.aggressive -> tr(heb,
            "סטטוס: לא אגרסיבי. פועלות רק מגבלות הפלטפורמה של Android (מתועדות רשמית).",
            "Status: not aggressive. Only the Android platform limits apply (officially documented).")
        oem.documented -> tr(heb,
            "סטטוס: ההתנהגות מתועדת רשמית על ידי היצרן (קישור למטה).",
            "Status: the behavior is officially documented by the manufacturer (link below).")
        else -> tr(heb,
            "סטטוס: ההגדרות מתועדות רשמית, אך סגירת הרקע עצמה מבוססת בעיקר על בדיקות הקהילה (dontkillmyapp) ומשתנה בין דגמים, גרסאות ו-ROM.",
            "Status: the settings are officially documented, but the background killing itself is based mainly on community testing (dontkillmyapp) and varies by model, version and ROM.")
    }

    private fun dkmaAction(heb: Boolean, url: String) =
        GuideAction(tr(heb, "מדריך עם תמונות (dontkillmyapp.com)", "Guide with screenshots (dontkillmyapp.com)"), ActionType.OPEN_URL, url)

    private fun dkmaSlug(key: String): String = when {
        key.contains("samsung") -> "samsung"
        key.contains("xiaomi") || key.contains("redmi") || key.contains("poco") -> "xiaomi"
        key.contains("honor") -> "honor"
        key.contains("huawei") -> "huawei"
        key.contains("oneplus") -> "oneplus"
        key.contains("oppo") -> "oppo"
        key.contains("realme") -> "realme"
        key.contains("vivo") || key.contains("iqoo") -> "vivo"
        key.contains("google") -> "google"
        key.contains("sony") -> "sony"
        key.contains("nokia") || key.contains("hmd") -> "nokia"
        else -> "general"
    }

    /** Glossary of every term used in the guide. */
    fun glossary(heb: Boolean): List<GuideItem> = listOf(
        GuideItem(
            title = tr(heb, "מילון — מנגנוני תזמון ורקע", "Glossary — scheduling and background mechanisms"),
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "JobScheduler — שירות מערכת (מ-API 21) שמריץ משימות רקע לפי תנאים כמו רשת או זמן, גם כשהאפליקציה סגורה.",
                "AlarmManager — שירות מערכת שמפעיל פעולה בזמן שנקבע. בגרסאות 19-20 זו דרך התזמון שלנו; מ-API 23 אזעקות נדחות ב-Doze.",
                "WorkManager — ספריית AndroidX שעוטפת את JobScheduler. בחרנו לא להשתמש בה אלא לממש ישירות על מנגנוני Android.",
                "Foreground Service — שירות עם התראה קבועה גלויה למשתמש. הדרך היחידה להבטיח ריצה מיידית וממושכת ברקע, גם במסך כבוי. לא בשימוש ב-POC.",
                "FCM — הודעת דחיפה מהשרת שיכולה להעיר את האפליקציה גם ב-Doze. דורשת שרת ו-Google Play. לא בשימוש ב-POC.",
                "BootReceiver — רכיב שמאזין לשידור של Android על סיום הדלקת המכשיר (BOOT_COMPLETED) כדי לתזמן מחדש אחרי ריסטארט.",
                "PendingIntent — אסימון שמאפשר למערכת לבצע פעולה בשם האפליקציה מאוחר יותר, למשל להפעיל את ה-AlarmReceiver.",
            ) else listOf(
                "JobScheduler — a system service (from API 21) that runs background tasks under conditions such as network or time, even when the app is closed.",
                "AlarmManager — a system service that triggers an action at a set time. On 19-20 it is our scheduling path; from API 23 alarms are deferred in Doze.",
                "WorkManager — an AndroidX library that wraps JobScheduler. We chose not to use it and to build directly on the Android mechanisms.",
                "Foreground Service — a service with a persistent notification visible to the user. The only way to guarantee an immediate, long background run, even with the screen off. Not used in the POC.",
                "FCM — a push message from the server that can wake the app even in Doze. Requires a server and Google Play. Not used in the POC.",
                "BootReceiver — a component that listens for Android's broadcast that boot finished (BOOT_COMPLETED) in order to re-schedule after a restart.",
                "PendingIntent — a token that lets the system perform an action on the app's behalf later, for example to trigger the AlarmReceiver.",
            ),
        ),
        GuideItem(
            title = tr(heb, "מילון — מצבי חיסכון של Android", "Glossary — Android power-saving states"),
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "Doze — מצב חיסכון (מ-Android 6) שנכנס כשהמכשיר מונח ללא שימוש, מסך כבוי ומנותק מהטעינה. בו משימות ורשת נדחות לחלונות תחזוקה.",
                "App Standby — מצב שבו Android מגביל אפליקציות שלא היו בשימוש לאחרונה.",
                "דליי סטנדבי (Standby Buckets) — חלוקה (מ-API 28) לפי תדירות שימוש. ככל שהדלי נמוך יותר, הריצות ברקע מווסתות יותר.",
                "אופטימיזציית סוללה — ההגבלה שברירת המחדל מפעילה על רקע של אפליקציה. הוצאה מרשימת ההגבלה מפחיתה אותה.",
                "מצב חיסכון סוללה — מצב של המשתמש שמגביל עוד יותר פעילות רקע.",
                "חלון תחזוקה — פרק זמן קצר שבו Android משחרר משימות ורשת שנצברו בזמן Doze.",
            ) else listOf(
                "Doze — a power-saving state (from Android 6) that starts when the device is left unused, screen off and unplugged. In it, tasks and network are deferred to maintenance windows.",
                "App Standby — a state where Android limits apps that have not been used recently.",
                "Standby Buckets — a split (from API 28) by usage frequency. The lower the bucket, the more background runs are throttled.",
                "Battery optimization — the limit the default applies to an app's background. Removing it from the limit list reduces it.",
                "Battery saver — a user mode that further limits background activity.",
                "Maintenance window — a short period in which Android releases tasks and network that accumulated during Doze.",
            ),
        ),
        GuideItem(
            title = tr(heb, "מילון — מושגים נוספים", "Glossary — other terms"),
            tone = Tone.INFO,
            lines = if (heb) listOf(
                "QAP (QUERY_ALL_PACKAGES) — הרשאה (מ-API 30) לראות את כל האפליקציות המותקנות. בלעדיה הרשימה חלקית.",
                "אזעקה מדויקת (setExactAndAllowWhileIdle) — אזעקה בזמן מדויק שפועלת גם ב-Doze. מ-API 31 דורשת הרשאת SCHEDULE_EXACT_ALARM.",
                "setMinimumLatency — הגדרה ב-JobScheduler של \"לא להריץ לפני\" פרק זמן. זה מה שמאפשר מרווח בדקות.",
                "קיבוץ רשת (Connectivity batching) — קיבוץ משימות רשת (מ-Android 15) לחיסכון בסוללה, שעלול לעכב ריצה עד כ-31 דקות.",
                "Force stop — עצירה יזומה מההגדרות. מבטלת משימות ואזעקות עד שהמשתמש פותח את האפליקציה שוב.",
                "Autostart — הגדרה של יצרנים מסוימים (Xiaomi, Oppo, Realme, Vivo) שמתירה לאפליקציה להיפתח ברקע לבד. לא קיימת ב-Android נקי (Pixel).",
                "ANR — \"האפליקציה לא מגיבה\": כשל רך כשרכיב חוסם את ה-main thread. מ-Android 14, JobService חוסם גורם ל-ANR.",
            ) else listOf(
                "QAP (QUERY_ALL_PACKAGES) — a permission (from API 30) to see all installed apps. Without it the list is partial.",
                "Exact alarm (setExactAndAllowWhileIdle) — an alarm at an exact time that fires even in Doze. From API 31 it requires the SCHEDULE_EXACT_ALARM permission.",
                "setMinimumLatency — a JobScheduler setting of \"do not run before\" a period. That is what enables an interval in minutes.",
                "Connectivity batching — batching of network tasks (from Android 15) to save battery, which may delay a run up to about 31 minutes.",
                "Force stop — a deliberate stop from Settings. It cancels tasks and alarms until the user opens the app again.",
                "Autostart — a setting of certain manufacturers (Xiaomi, Oppo, Realme, Vivo) that lets an app open in the background on its own. It does not exist on clean Android (Pixel).",
                "ANR — \"Application Not Responding\": a soft failure when a component blocks the main thread. From Android 14, a JobService that blocks causes an ANR.",
            ),
        ),
    )

    /** Buttons that jump straight to the relevant system settings. */
    fun quickActions(heb: Boolean, sdkInt: Int): GuideItem {
        val actions = mutableListOf(
            GuideAction(tr(heb, "אופטימיזציית סוללה", "Battery optimization"), ActionType.BATTERY_OPTIMIZATION),
            GuideAction(tr(heb, "מצב חיסכון סוללה", "Battery saver"), ActionType.BATTERY_SAVER),
            GuideAction(tr(heb, "פרטי האפליקציה", "App details"), ActionType.APP_DETAILS),
        )
        if (sdkInt >= Build.VERSION_CODES.S) {
            actions += GuideAction(tr(heb, "הרשאת אזעקות מדויקות (למצב מדויק)", "Exact-alarm permission (for exact mode)"), ActionType.EXACT_ALARM)
        }
        return GuideItem(
            title = tr(heb, "קיצורי דרך להגדרות", "Settings shortcuts"),
            tone = Tone.INFO,
            lines = listOf(tr(heb,
                "הכפתורים פותחים ישירות את מסכי ההגדרות הרלוונטיים במכשיר.",
                "The buttons open the relevant system settings screens directly.")),
            actions = actions,
        )
    }
}
