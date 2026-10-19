# Discipline

A private, offline-first calorie and nutrition tracker for Android with a One UI-inspired
interface and two-way Health Connect sync. Personal project.

## Features

- **Diary**: Breakfast / Lunch / Dinner / Snacks (renameable), date navigation, swipe to delete
  with undo, copy a meal from yesterday or to today, quick-add calories.
- **Food search**: 164 bundled generic foods (offline), your own foods, recent, frequent and
  favourites, plus online search and barcode lookup through Open Food Facts.
- **Barcode scanning** with Google's code scanner (no camera permission needed).
- **Goals**: Mifflin–St Jeor BMR × activity, weekly lose/gain targets with safe minimums,
  custom calorie override, macro split presets, water and step goals, projected goal date.
- **Exercise**: 17 activities with MET-based calorie estimates, editable.
- **Water and weight** logging, with progress charts (1W / 1M / 3M / 1Y) for weight, calories
  and steps.
- **Health Connect**:
  - writes one `NutritionRecord` per food entry (name, meal type, 10 nutrients), plus
    hydration, weight, and exercise sessions with active calories.
  - reads steps, active calories, other apps' workouts and smart-scale weights.
  - outbox + WorkManager, with idempotent upserts and deletes keyed by client record id.
  - 30-day backfill on connect, per-type toggles, rationale screen and the Android 14+
    permission-usage entry point.
- **Data**: CSV export, delete everything (including Discipline's Health Connect records),
  light / dark / system theme, metric or imperial units.

## Build

Requires JDK 17 and the Android SDK (compileSdk 36). minSdk 28.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

A release build is minified with R8 and signed with the debug key so it installs directly:

```bash
./gradlew :app:assembleRelease
```

## Architecture

```
app/src/main/java/app/discipline/
  domain/        Nutrients, goal maths, units, exercise catalogue (pure Kotlin, unit-tested)
  data/db/       Room entities + DAOs (foods, entries, exercise, water, weight, HC outbox/cache)
  data/food/     FoodRepository (bundled seed, local search), Open Food Facts client
  data/          DiaryRepository (all writes + outbox in one transaction), DataStore settings
  health/        HealthConnectManager, HealthSyncWorker, HealthRationaleActivity
  ui/components  One UI kit: collapsing "viewing area" header with snap, rounded groups,
                 rows, switches, pill buttons, segmented control, rings, charts
  ui/screens     Today, Diary, Search, Food detail, Create food, Quick add, Exercise,
                 Nutrition, Progress, Goals, Health Connect, Settings, Onboarding
```

No DI framework, no network backend, no analytics.

## Docs

- `docs/SPEC.md`: the functional spec the app was built from, including the Health Connect
  sync design (per-entry nutrition records and client-record-id upserts).

## Data sources

Generic foods are approximate reference values compiled for this project. Packaged-food data
comes from [Open Food Facts](https://world.openfoodfacts.org) under the Open Database License.
Nutrition estimates are not medical advice.
