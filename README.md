# Discipline

A private, offline-first calorie and nutrition tracker for Android. It has a One UI-inspired
design, barcode and photo logging, and two-way sync with every Health Connect data type.
There's no account, server, ads or analytics. Your diary lives in a database on your phone.

**[Download the latest APK](https://github.com/pronzzz/Discipline/releases/latest)**: Android 9 or later. Once installed, the app updates itself.

## Contents

- [Features](#features)
- [Install and update](#install-and-update)
- [Health Connect](#health-connect)
- [Food photos](#food-photos)
- [Privacy](#privacy)
- [Build from source](#build-from-source)
- [Releasing](#releasing)
- [Architecture](#architecture)
- [Data sources and licences](#data-sources-and-licences)

## Features

### Food diary
- Four meals (Breakfast, Lunch, Dinner and Snacks) that you can rename, each with calorie and macro totals.
- **Remaining = Goal − Food + Exercise** on the Today ring and in the diary.
- Swipe to delete with undo. Edit servings or move an entry to another meal. Copy a meal from yesterday or to today.
- Quick add calories, with calories worked out from macros if you prefer.

### Finding food
- **Search** 164 built-in generic foods (these work offline), your own foods, and Open Food Facts.
- **Barcode scanner** in the app for EAN-13, EAN-8, UPC-A and UPC-E codes, with torch and manual entry. UPC-E, UPC-A and EAN-13 forms of the same product all match. Products Open Food Facts knows but has no nutrition for open Create food pre-filled.
- **Food photos**: an on-device model recognises around 2,000 dishes. Add a description and amount ("two eggs", "about 350 g") and log it with one tap. See [Food photos](#food-photos).
- Recent, Frequent, My foods and Favourites. One-tap **+** logs your last-used serving.
- Amounts as servings or grams, full nutrition facts and a macro donut for every food.

### Goals and progress
- Guided onboarding: goal, profile, activity level, then your plan.
- Mifflin–St Jeor BMR × activity factor, weekly lose or gain targets from 0.25 to 1 kg, safe calorie minimums and a projected goal date.
- Custom calorie goal and macro presets (Balanced, High protein, Low carb, Keto).
- Water, weight (with optional body fat %) and exercise (17 activities with MET-based calorie estimates and optional distance).
- Charts for weight, calories and steps over 1W, 1M, 3M or 1Y.

### Everything else
- Light, dark or system theme. Metric or imperial units. Haptics.
- CSV export of your diary and weights.
- Delete all data, including the records Discipline wrote to Health Connect.

## Install and update

1. Download `Discipline-<version>.apk` from [Releases](https://github.com/pronzzz/Discipline/releases/latest) and open it. Android will ask you to allow installs from your browser or file manager.
2. Discipline checks GitHub for a new version when you open it, at most twice a day. When there is one, it shows the release notes. Tap **Update** and it downloads the APK and verifies its SHA-256 against the `.apk.sha256` file published with the release. Android then asks you to confirm the install. Your diary is kept.
3. **Settings › App updates** lets you turn the check off, check now, or skip a version.
4. The first time, Android asks you to allow Discipline to install apps. Google Play Protect
   may also offer to scan the app, because it isn't from the Play Store. You can scan it, or
   choose **Install without scanning**.

Every release is signed with the same certificate:

```
SHA-256 65:45:90:B3:1F:96:B1:3E:4F:2F:0A:F0:8B:1C:46:C2:7B:3B:62:19:FB:E5:D6:3A:C9:C3:E1:8D:E5:6A:0B:3F
```

To check a download yourself, run `apksigner verify --print-certs Discipline-<version>.apk` and `shasum -a 256 Discipline-<version>.apk`.

## Health Connect

Discipline can read and write **all 37 Health Connect data types** that apps can use, grouped
the way Health Connect groups them:

| Category | Types |
|---|---|
| Activity | Steps, distance, active and total calories, floors, elevation, wheelchair pushes, exercise, VO2 max, power, speed, cycling and steps cadence |
| Body measurements | Weight, height, body fat, basal metabolic rate, lean, bone and body water mass |
| Cycle tracking | Menstruation flow and period, ovulation test, cervical mucus, spotting, sexual activity |
| Nutrition | Nutrition, hydration |
| Sleep | Sleep sessions |
| Vitals | Heart rate, resting heart rate, HRV, blood pressure, blood glucose, oxygen saturation, respiratory rate, body, basal and skin temperature |
| Wellness | Mindfulness |

Skin temperature and mindfulness appear only when the Health Connect version on your phone
supports them. Exercise routes, training plans and medical records are not included. Each of
those needs its own consent flow.

**What Discipline writes**
- One nutrition record for each food you log, with its name, meal type and 10 nutrients. It doesn't write one combined record per meal.
- Hydration, weight, body fat, height, and workouts with active calories and distance.
- Anything you add in **Log health data**, such as blood pressure, sleep or temperature.
- Edits and deletes are mirrored. Every record carries a client record ID (`discipline-<kind>-<id>`), so writes are idempotent. When you first connect, the last 30 days are backfilled.

**What Discipline reads**
- Steps and active calories, credited to your calorie goal if you choose.
- Workouts from other apps, and weights from smart scales.
- The latest value of every other type you allow. These appear under **Health data** and **Health today**.
- Food and water from other apps, if you turn on counting them. Food is off by default, because apps such as Samsung Health often re-share what they read from Discipline, which would count it twice.

**Permissions.** In **Settings › Health Connect**, each category has its own Read and Write
switch, plus a list showing which types are allowed. **Permissions** opens Discipline's page
in Health Connect, where you can choose individual types. Reads refresh when you open the app
and about once an hour in the background.

## Food photos

1. In **Add food**, tap **Snap a photo**. The first time, Discipline downloads the 21 MB model from this repository's v1.1.0 release and checks it against a pinned SHA-256.
2. Take a photo, or choose one from your gallery.
3. The model lists its best guesses. Tap one, or type a description. Words you type re-rank the guesses and become the search. Amounts such as `350 g`, `1.2 kg`, `8 oz`, `2 slices`, `two eggs`, `x3`, `small` or `large` set the serving size.
4. Matches come from your foods and Open Food Facts. Tap **+** to log, or tap a food to adjust it first.

The model is Google's [AIY vision classifier food V1](https://www.kaggle.com/models/google/aiy/tfLite/vision-classifier-food-v1)
(Apache 2.0), run through ML Kit custom image labeling. It names dishes, not portions, and
it can be wrong, so check before you log. Photos are processed in memory and never saved
or uploaded.

## Privacy

- Your diary, foods, settings and cached Health Connect values are stored in a local Room database and in DataStore.
- Network use is limited to:
  - Open Food Facts searches and barcode lookups (only the search text or barcode is sent)
  - the GitHub release check and downloads
  - the one-time model download
- Health data is never uploaded, sold or used for advertising. Revoke access at any time in Health Connect.

## Build from source

You need JDK 17 and the Android SDK (compileSdk 36). The minimum Android version is 9 (API 28).

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Release builds are minified with R8:

```bash
./gradlew :app:assembleRelease
```

A release build is signed with the key in `keystore.properties` (not committed) or the
`DISCIPLINE_*` environment variables. Without either, it uses your debug key. A build signed
with a different key installs only after you uninstall the app, which erases its data.

`keystore.properties`:

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, and add a section to `CHANGELOG.md`.
2. Either:
   - **From a laptop:** `scripts/release.sh --publish`. It runs the tests, builds and checks the APK's certificate, writes the `.sha256`, and creates the GitHub release with the notes from the changelog.
   - **From CI:** push a tag `v<version>`. The **Release** workflow does the same, provided these repository secrets are set:

     | Secret | Value |
     |---|---|
     | `DISCIPLINE_KEYSTORE_BASE64` | `base64 -i release.keystore` |
     | `DISCIPLINE_KEYSTORE_PASSWORD` | keystore password |
     | `DISCIPLINE_KEY_ALIAS` | key alias |
     | `DISCIPLINE_KEY_PASSWORD` | key password |

   Both refuse to publish an APK whose certificate doesn't match the one above. CI also runs the tests and a debug build on every push to `main` and on every pull request.

## Architecture

```
app/src/main/java/app/discipline/
  domain/        Nutrients, goal maths, units, exercise catalogue (pure Kotlin, unit-tested)
  data/db/       Room entities and DAOs: diary, foods, Health Connect outbox and caches (schema v2)
  data/food/     FoodRepository (bundled foods, local search), Open Food Facts client, barcode forms
  data/          DiaryRepository (every write and its outbox row in one transaction), DataStore settings
  health/        HcType catalogue of all types, record builders and readers, HealthConnectManager,
                 sync worker, rationale screen
  vision/        FoodModel (download, verify, ML Kit labeling), FoodHints (description parser)
  update/        Updater (GitHub releases, SHA-256 check, PackageInstaller), install receiver
  ui/components  One UI kit: collapsing header, rounded groups, rows, switches, pills, charts
  ui/screens     Today, Diary, Search, Barcode scanner, Food photo, Food detail, Create food,
                 Quick add, Exercise, Nutrition, Progress, Goals, Health Connect, Health data,
                 Log health data, Settings, Onboarding
```

The stack is Kotlin, Jetpack Compose, Room, DataStore, WorkManager, CameraX, ML Kit and Health
Connect 1.1. There's no DI framework and no backend.

Writes to Health Connect go through an **outbox**. Each diary change and its outbox row are
saved in one Room transaction. A WorkManager job then drains the outbox with upserts keyed by
client record ID. Logging never waits on Health Connect, and survives the app being killed.
Reads replace windows of cached days, and are idempotent: 30 days on first connect, 7 days
each time the app opens, 3 days every hour. [`docs/SPEC.md`](docs/SPEC.md) has the full design.

## Data sources and licences

- Generic foods are approximate reference values compiled for this project.
- Packaged-food data comes from [Open Food Facts](https://world.openfoodfacts.org) under the Open Database License.
- The food recognition model is © Google, licensed under Apache 2.0.

Calorie and nutrient estimates are for general wellness and aren't medical advice.
