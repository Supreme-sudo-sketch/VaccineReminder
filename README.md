# SMS Vaccination Reminder System (prototype)

Prototype for the BBT 3203 mini-project "Design of an SMS-Based Vaccination Reminder System to Reduce Missed and Delayed Childhood Immunisation in Kenya".

Two parts:

- `backend/` Node.js server (no npm packages) with SQLite. It stores health worker accounts, children and the vaccine schedule, works out due dates, and sends SMS reminders through Africa's Talking.
- `android/` Kotlin + Jetpack Compose app for caregivers and health workers, in English and Kiswahili, with light and dark themes.

## How it maps to the proposal

| Objective | In the prototype |
| --- | --- |
| 1. Reasons for missed visits | Follow-up tab lists every child with overdue vaccines, how late they are, when the last SMS went out, and a Call button |
| 2. Phone and SMS access | Reminders go by SMS to any basic phone, no internet needed on the caregiver's side |
| 3. Features, timing, language | Reminders 3 days before, on the due day, 3 days after, and a second reminder two weeks after. Message language chosen per child (English or Kiswahili) |
| 4. Acceptance | Registration requires the caregiver's consent to receive SMS, and consent can be withdrawn at any time. Use the questionnaire to measure acceptance |

## Backend

Needs Node.js 22.13 or newer.

```
cd backend
npm test
REGISTRATION_CODE=choose-a-code npm start
```

The server listens on port 3000. Health workers need the registration code to create an account, so give it only to staff. (`HW_API_KEY` from version 1 is still accepted as the registration code, so the old start command works.) Without a code set the server uses `demo-registration-code` and prints a warning.

Without SMS keys the server runs in test mode: reminders are written to the `sms_log` table and nothing is sent. To send real SMS, create an Africa's Talking account and set:

```
AT_USERNAME=sandbox            (or your live username)
AT_API_KEY=your-key
AT_SENDER_ID=YourName          (optional, needs approval)
```

The sandbox only delivers to the Africa's Talking simulator. Live SMS needs a funded account. A message only counts as sent when Africa's Talking accepts it for the recipient; rejected numbers and an empty balance are logged as failed and retried on the next run.

An existing `vaccine.db` from version 1 is upgraded in place the first time the new server opens it.

### Demo data

To try the app without typing in records, load made-up demo data:

```
cd backend
npm run seed                 (empty database)
npm run seed -- --reset      (deletes everything in vaccine.db first)
```

It creates two health worker accounts (`0700000001` and `0700000002`, password `Demo1234`) and 12 children, each showing one situation: a newborn, vaccines due today, due in 3 days, 5 days overdue, missed for 3 weeks with nothing recorded, a late Pentavalent series that moved later doses, fully up to date, SMS consent withdrawn, twins on one phone, a caregiver with a saved area (Mombasa), and a 2-year-old with everything given. Some children have past SMS in their history. The command prints each caregiver's phone number and child's date of birth for the caregiver lookup.

The dates are worked out from the day you run it, so run it again with `--reset` on another day. It refuses to run when live SMS keys are set, because the server would then text these made-up numbers. Delete `vaccine.db` before going live.

If the server starts after 08:00, the daily run sends that day's test-mode reminders within a minute, so "Send today's reminders now" will then find nothing more to send; the messages show in each child's SMS history instead.

### How reminders work

- Every morning after 08:00 Nairobi time (or when a health worker taps "Send today's reminders now"), the server looks at every dose not yet given for children whose caregiver agreed to SMS.
- Each reminder has a window: **before** 1 to 3 days ahead, **due** on the day, **overdue** 3 to 13 days late, **follow-up** 14 to 60 days late. If the server was off on the right day, the reminder still goes out on the next run inside the window.
- Each reminder is sent once per child and due date. A child gets at most one SMS a day; the most urgent one goes first.
- Health workers can send a reminder by hand. It is refused when the caregiver has not agreed to SMS, or when a manual reminder already went out in the last 12 hours.
- Doses in a series (Polio, Pentavalent, Pneumococcal, Rotavirus, Measles-Rubella) are kept at least 4 weeks apart. If one is given late, the next one's due date moves back, so the caregiver is not called in too early.

### Main endpoints

Health worker endpoints need the header `Authorization: Bearer <token>` from login or registration.

- `POST /auth/register` name, phone, facility, password, registrationCode
- `POST /auth/login` phone, password. Five wrong passwords lock the account for 15 minutes
- `GET /auth/me`, `POST /auth/logout`, `POST /auth/password`
- `POST /caregiver/lookup` phone + child date of birth, no login needed
- `GET /stats` numbers for the dashboard
- `POST /children`, `GET /children?q=&filter=all|overdue|due`, `GET /children/:id`
- `PATCH /children/:id` edit details or consent (or `POST` with `X-HTTP-Method-Override: PATCH`), `DELETE /children/:id`
- `POST /children/:id/doses/:CODE/given` (optional `date`) and `/undo`
- `POST /children/:id/doses` record several past doses at once: `{ "given": [{ "code": "BCG", "date": "2026-09-13" }] }`
- `GET /missed`, `GET /due?days=7`
- `POST /children/:id/remind`, `POST /reminders/run`

Public endpoints for the nearby clinics feature (no login, rate limited to 30 requests a minute per address):

- `GET /facilities/nearby?lat=-4.0435&lon=39.6682` clinics, hospitals and health centres from OpenStreetMap, nearest first, with `name`, `type`, `lat`, `lon`, `distanceKm`, and `phone` and `openingHours` when OpenStreetMap has them
- `GET /geocode?q=Mombasa` places in Kenya matching a town name
- `POST /caregiver/location` with `phone`, `childDob`, `lat`, `lon`, `label` saves the area used for that child's reminders, checked the same way as the caregiver lookup. Send `"lat": null` to delete it

Errors come back as `{ "error": "message", "code": "...", "field": "..." }`, so the app can show the message next to the right field and translate it.

### Nearby clinics for travelling caregivers

A caregiver who is away from home can find clinics near where they are, and choose to have the nearest ones added to their SMS reminders.

- Facilities come from OpenStreetMap through the Overpass API: places tagged `amenity=hospital|clinic` or `healthcare=hospital|clinic|centre`, in Kenya only. Places without a name are skipped.
- **Hospitals come first.** The search starts at 3 km and widens to 8, 20 and 50 km until it finds at least 3 hospitals (`amenity=hospital` or `healthcare=hospital`). Smaller clinics and health centres are shown only when there is no hospital within 50 km; the app and the SMS then say "clinics" instead of "hospitals".
- If the main Overpass server is busy (it often answers "at capacity"), the server waits a moment and tries the next one in `OVERPASS_URLS`. If every Overpass server fails, it searches Nominatim instead for hospitals, then clinics, in the same widening areas. If that fails too, it shows the last results it found for that area, even if they are older than a day.
- Town search uses Nominatim, limited to Kenya. The server sends at most one Nominatim request per second and the app only searches when the caregiver presses search, as the Nominatim usage policy asks.
- Results are cached in memory for 24 hours (clinics by position rounded to about 1 km, towns by name), so repeated searches do not hit OpenStreetMap again. The cache is lost when the server restarts.
- If a caregiver saved an area, each reminder SMS for that child ends with the 2 nearest hospitals (or clinics, as above), their distance, and their phone number when OpenStreetMap has one, followed by "Call to confirm that the vaccine is available." If OpenStreetMap does not answer, the normal reminder is sent without the clinic list.

**OpenStreetMap does not say whether a clinic offers vaccination or has a vaccine in stock.** The app, the SMS and this README never claim that it does. Every clinic list says "Call to confirm that the vaccine is available" and shows the clinic's phone number when there is one.

Set a User-Agent with your contact email before running the server for others, as the OpenStreetMap services require:

```
OSM_USER_AGENT="VaccineReminderKE/2.0 (your-email@example.com)"
OVERPASS_URLS=https://overpass-api.de/api/interpreter,https://overpass.private.coffee/api/interpreter   (default, tried in order)
NOMINATIM_URL=https://nominatim.openstreetmap.org/search    (default)
```

Privacy (Kenya Data Protection Act 2019): a location is personal data.

- The area is saved only when the caregiver turns on "Use this area for my reminders" in the app.
- Only the latest area is kept, rounded to about 100 m, together with the place name the caregiver chose.
- It is deleted automatically 14 days after it was saved, and the caregiver can delete it at any time by turning the switch off.
- It is used only to pick the clinics in the SMS. Health workers do not see it: the clinic lines are removed from the SMS history shown in the app. The full message stays in the server's `sms_log` table.
- Overpass receives the position rounded to about 1 km; Nominatim receives only the town name typed. The app never asks for background location.

## Android app

1. In Android Studio (Ladybug or newer) choose File > Open and pick the `android` folder, not the `vaccine-reminder` folder above it, then let Gradle sync. Gradle 8.13 does not run on Java 25, which newer Android Studio versions bundle, so `android/gradle/gradle-daemon-jvm.properties` makes Gradle use Java 21 whatever Gradle JDK is set. It uses a Java 21 already on the computer (Android Studio's downloaded JDKs in `%USERPROFILE%\.jdks` count) or downloads one on the first sync.
2. Run the backend on your computer.
3. Run the app on the emulator. It already points to `http://10.0.2.2:3000`, which is your computer as seen from the emulator.
4. Tap Health worker, then Create an account, and enter the registration code you started the server with.

On a real phone the emulator address `10.0.2.2` does not work. The app uses `http://127.0.0.1:3000` by default on a phone, which works over the USB cable after linking the phone to the computer. Double-click `connect-phone.cmd` in the project folder, or run:

```
adb -d reverse tcp:3000 tcp:3000
```

The link is lost whenever the phone is unplugged or adb restarts (starting the emulator can do this), so run it again if the phone app says it cannot reach the server at `http://127.0.0.1:3000`. The emulator needs no link: it reaches the computer at `http://10.0.2.2:3000` by itself.

To use Wi-Fi instead, set the computer's address in Settings (for example `http://192.168.0.115:3000`), tap Save and test, and allow Node.js through Windows Firewall on private networks when Windows asks. Plain `http://` only works in debug builds; a release build needs the backend on HTTPS.

If the app says it cannot reach the server, check that `npm start` is still running in the `backend` folder and that the address shown in the message is right.

The session token and any caregiver details saved on the phone are encrypted with a key kept in the Android Keystore.

Nearby clinics (caregiver screen, hospital icon or "Clinics near me"):

- The map is OpenStreetMap through osmdroid, with the "© OpenStreetMap contributors" credit on the map. Map tiles need internet and are cached in the app's cache folder.
- "Use my location" asks for location permission when tapped and uses the phone's own location service (no Google Play services). "Search a town" works without any permission.
- Tapping a clinic centres the map on it; Directions opens a maps app, or the OpenStreetMap website if there is none.

Vaccine alerts: when a caregiver ticks "Remember on this phone", the app checks about every 12 hours (when online) for vaccines due within 3 days or missed, and shows a notification with the child, the vaccine and the nearest clinic (from the saved area, otherwise the registered clinic). It notifies at most once per vaccine per day. On Android 13 and newer it asks for permission to show notifications. "Check now" on the caregiver screen runs the check straight away.

Things to test on the phone or emulator:

1. Health worker account: create an account with a wrong registration code (refused), then the right one. Sign out from Settings and sign in again. Five wrong passwords should lock the account.
2. Register a child: leave fields empty and check each one shows its own message. Register a child born several weeks ago with consent ticked. The app opens the new child's record.
3. Child record: use "Record past vaccines" for the birth doses, record a dose with a date, undo it, edit the caregiver's phone number, switch SMS reminders off and check the Send SMS button disappears.
4. Late doses: record Pentavalent 1 two weeks late and check Pentavalent 2 moves to four weeks after it.
5. Caregiver: look up the same child with the phone number (any format, e.g. 0712 345 678) and date of birth. A wrong date of birth should show an error. With "Remember on this phone" ticked, the child shows straight away next time.
6. Follow-up tab: check overdue children appear with how late they are. Tap Send SMS (twice: the second should be refused), Call, and on the dashboard "Send today's reminders now". In test mode the message says the reminder was logged.
7. Switch language on the welcome screen or in Settings and check the screens change to Kiswahili. Switch the phone to dark mode.

## Limits to state in the report

- The immunisation schedule and the 4-week spacing in `backend/src/schedule.js` are entered from memory of the Kenyan routine schedule. Check them against the current Ministry of Health schedule before using it. The system does not handle upper age limits (for example for rotavirus) or catch-up rules beyond the 4-week spacing.
- Any health worker with the registration code can create an account, and every account sees every child. A real system needs an administrator who approves accounts, can deactivate them, and limits each worker to their facility.
- Rate limits and login lockouts are kept in memory, so they reset when the server restarts, and behind a proxy all users share one address.
- Caregiver lookup uses phone number plus the child's date of birth as a light check, limited to 10 wrong tries per phone number per hour. It is not strong security.
- Data Protection Act 2019: this prototype stores names and phone numbers. A real deployment needs a data protection impact assessment, registration with the Data Commissioner, a retention policy, and HTTPS.
- OpenStreetMap coverage of health facilities in Kenya is incomplete and can be out of date: some clinics are missing, misplaced, closed, or have no phone number. It never shows vaccine stock or services. Caregivers must call to confirm that the vaccine is available.
- The public Overpass and Nominatim servers are free, shared and sometimes slow or busy; they are fine for a prototype but not for a national service, which should run its own instances or use the Kenya Master Health Facility List.
- Adding clinics makes the SMS longer, so each reminder can cost 2 or 3 SMS instead of 1.
- Android decides exactly when the 12-hour check runs. Battery saving can delay it by hours, and it stops if the app is force-stopped. The SMS reminders from the server are the reliable channel; the in-app alert is extra.
- Per the proposal scope, this prototype is not a trial of effect on coverage.
