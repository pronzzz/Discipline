# Discipline — functional spec

Discipline is a personal calorie & nutrition tracker for Android. This spec describes its
behaviour and its Health Connect integration, based on common calorie-tracking practice and the
public Health Connect developer documentation.

## 1. Core concepts

| Concept | Definition |
|---|---|
| Diary day | A local calendar date. All entries belong to exactly one day. |
| Meal | Breakfast, Lunch, Dinner, Snacks (fixed order; names user-editable). |
| Food | Name, optional brand, optional barcode, one or more serving sizes, nutrients per base serving. |
| Food entry | Food × servings × serving size, logged to a meal on a day. Nutrients are snapshotted at log time so later food edits don't rewrite history. |
| Quick add | Calories (+ optional macros) logged without a food. |
| Exercise entry | Name, duration, kcal burned, optional start time; manual or imported. |
| Water entry | Volume in ml. |
| Weight entry | kg value on a date (one per day, last write wins). |

### Energy equation
`Remaining = Goal − Food + Exercise` (exercise added back is a setting, default on).
Steps from Health Connect convert to "step calories" only above a baseline, and only when no
active-calories data exists for that day (avoid double counting).

## 2. Goals

- Profile: sex, birth date, height, current weight, activity level
  (Sedentary 1.2, Lightly active 1.375, Active 1.55, Very active 1.725).
- BMR: Mifflin–St Jeor. TDEE = BMR × activity factor.
- Weekly target: maintain, or lose/gain 0.25 / 0.5 / 0.75 / 1.0 kg per week
  (7700 kcal per kg → daily delta).
- Daily calorie goal = TDEE ± delta, floored at 1200 (female) / 1500 (male) unless the user
  overrides with a custom goal.
- Macro goal as % of calories (default 50 C / 20 P / 30 F); grams derived with 4/4/9 kcal/g.
- Water goal (default 2500 ml), step goal (default 10,000).

## 3. Food logging

Search screen tabs: **All** (local + online), **Recent**, **Frequent**, **My Foods**.
- Local search over the bundled generic-food database and user foods (prefix + token match).
- Online search: Open Food Facts public API (ODbL, attribution shown in Settings → About).
- Barcode: scan → lookup local barcode → Open Food Facts product endpoint → offer "Create food"
  prefilled with the barcode if not found.
- Food detail sheet: serving size picker, number of servings (decimal), meal picker, live
  macro ring + nutrient table, "Add" CTA.
- Create food: name, brand, serving description + grams, calories, fat, sat fat, carbs,
  fiber, sugar, protein, sodium, cholesterol, potassium (optional fields).
- Copy meal / copy yesterday's meal into today. Swipe-to-delete with undo. Tap to edit.

## 4. Screens (One UI-styled)

1. **Today** (dashboard): calorie ring (remaining), macros bars, steps/exercise/water tiles,
   streak count, weight sparkline.
2. **Diary**: date strip, per-meal cards with entries and per-meal kcal, Add food per meal,
   exercise card, water card, "Complete day" summary.
3. **Progress**: weight chart (30/90/365 days), calories-per-day bar chart vs goal, log weight.
4. **Settings**: Profile & goals, Units, Meals names, Health Connect, Data (export CSV,
   delete all), About/licences.

One UI language: large "viewing area" title occupying the top third that collapses into a
compact toolbar on scroll; content in the lower, reachable half; grouped rounded containers
(≈26dp radius) on a neutral background; bottom navigation with labels; pill buttons; blue
accent; full dark mode (true-black background, dark-grey containers); haptics on key actions.

## 5. Health Connect integration

### Availability & permissions
- Status via SDK: `SDK_AVAILABLE` → available; `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` →
  show "Update Health Connect" (Play Store deep link); `SDK_UNAVAILABLE` → hide feature.
- Manifest: `<queries>` for the HC provider; rationale activity for
  `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`; activity-alias for
  `VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS` guarded by `START_VIEW_PERMISSION_USAGE`.
- The rationale screen is a native in-app privacy explanation (no web view).
- Permission request via `PermissionController.createRequestPermissionResultContract()`.
  Connected state is always re-derived from `getGrantedPermissions()`, never only from a cached
  flag. After two denials, show a "Open Health Connect settings" button instead of re-requesting.
- Per-type toggles let the user opt out of individual data types.

### Writes (Discipline → Health Connect)
Discipline writes **one `NutritionRecord` per food entry** (rather than a per-meal aggregate), with the food name, real meal type, and a short interval
at the entry's logged time (meal default times: 08:00, 13:00, 19:00, 16:00 if the entry was
logged on another day).

All writes use `Metadata.manualEntry(clientRecordId = "discipline-<kind>-<localId>",
clientRecordVersion = <updatedAtMillis>)`. Health Connect upserts on matching client id +
higher version, so edits are idempotent and need no remote-id mapping table. Deletes use
`deleteRecords(type, idsList = [], clientRecordIdsList = [...])`.

| Local | HC record(s) |
|---|---|
| Food entry / quick add | `NutritionRecord` (energy, protein, carbs, fat, sat fat, fiber, sugar, sodium, cholesterol, potassium) |
| Water entry | `HydrationRecord` |
| Weight entry | `WeightRecord` (instant at 08:00 local of that day) |
| Manual exercise | `ExerciseSessionRecord` + `ActiveCaloriesBurnedRecord` (same client-id stem) |

Writes are enqueued as an outbox row in Room (kind, localId, op = UPSERT/DELETE) and drained
by a WorkManager job (network not required, retries with backoff), so logging never blocks on
Health Connect and survives process death.

### Reads (Health Connect → Discipline)
- Steps: `aggregate(StepsRecord.COUNT_TOTAL)` per day.
- Active calories: `aggregate(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)` per day,
  excluding Discipline's own data origin.
- Exercise from other apps: `readRecords(ExerciseSessionRecord)` per day, excluding own origin;
  shown read-only in the Diary exercise card.
- Weight from other apps (smart scales): `readRecords(WeightRecord)`, latest per day, imported
  if the user has no local entry that day.
- Windowed re-reads instead of change tokens: 30 days on first connect, 7 days on each app
  foreground, 3 days from the hourly WorkManager job. Reads are idempotent replacements of the
  local cache, which keeps the logic simple and self-healing.
- Triggers: app foreground + a 1-hour periodic WorkManager job (background reads need
  `READ_HEALTH_DATA_IN_BACKGROUND`, requested only when the device supports it).
- Backfill: when write permissions are first granted, the last 30 days of diary data are
  queued, so anything logged before connecting still reaches Health Connect.

### History
`READ_HEALTH_DATA_HISTORY` is requested so the initial import can go back 30 days.

## 6. Data & privacy
- All data local (Room). No account, no analytics, no ads.
- Export CSV (diary, weights). "Delete all data" also offers to delete Discipline-origin
  records from Health Connect.
