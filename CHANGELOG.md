# Changelog

## [1.1.0] - 2026-11-03

### New
- **Barcode scanning that works.** A new in-app scanner reads EAN-13, EAN-8, UPC-A and UPC-E codes and looks them up on Open Food Facts. You can also type the number by hand, and turn on the torch. The same product printed in UPC-E, UPC-A or EAN-13 form is now matched too. If Open Food Facts knows a product but has no nutrition facts for it, Create food opens with its name and brand already filled in.
- **Food photos.** Snap or pick a photo of a meal. An on-device model suggests what it is (Google's AIY food classifier, about 2,000 dishes). You can add a description such as "chicken curry with rice, about 350 g" to steer the matches and set the amount, then log it with one tap. The 21 MB model downloads once, the first time you use the feature, and photos never leave the phone.
- **Every Health Connect data type, read and write.** This covers activity, body measurements, cycle tracking, nutrition, sleep, vitals and mindfulness: 37 types in all, matching what Samsung Health and Zepp share.
  - **Health data** shows the latest value of each type and which app it came from.
  - **Log health data** lets you add blood pressure, sleep, heart rate, body temperature, cycle tracking, VO2 max and more. Entries are saved to Health Connect and can be deleted again.
  - **Health today** on the Today screen shows today's distance, total calories, floors, sleep, heart rate and other values.
  - Read and Write switches for each category, with a per-type list of what's allowed.
  - Optionally count food and water logged in other apps.
  - Weigh-ins take an optional body fat %, and workouts take an optional distance. Both are shared with Health Connect, and so is your height.
- **Automatic updates from GitHub.** Discipline checks for new releases. It shows the release notes, then downloads the APK, verifies it against the published SHA-256 and installs it. You can turn the check off, check now or skip a version in Settings › App updates.

### Fixed
- Text fields are no longer hidden behind the keyboard. The layout was subtracting the keyboard height twice.
- Tapping **Permissions** or **Open Health Connect** no longer crashes the app on Android 14 and later. It now opens Discipline's own page in Health Connect, with safe fallbacks.

### Under the hood
- Database version 2, with an automatic migration that keeps your existing diary.
- GitHub Actions: CI on every push. Tagged releases are signed, checked against the release certificate and published with their checksums.
- New unit tests for barcode normalisation, photo description parsing, version comparison and Health Connect permissions.

## [1.0.0] - 2026-10-19

First release: food diary, goals, progress charts, Open Food Facts search, and two-way Health Connect sync for nutrition, hydration, weight, exercise, steps and active calories.
