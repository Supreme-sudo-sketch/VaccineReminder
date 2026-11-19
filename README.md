# SMS Vaccination Reminder System (prototype)

Prototype for the BBT 3203 mini-project "Design of an SMS-Based Vaccination Reminder System to Reduce Missed and Delayed Childhood Immunisation in Kenya".

Two parts:

- `backend/` Node.js server (no npm packages) with SQLite. It stores health worker accounts, children and the vaccine schedule, works out due dates, and sends SMS reminders through TextBee (an Android phone), Twilio or Africa's Talking.
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
npm start
```

Settings go in `backend/.env`, which `npm start`, `npm run seed` and `npm run check-sms` read automatically. Copy `backend/.env.example` to `.env` if it is not there; the example explains every setting. Keep `.env` private: it holds the SMS API key and is left out of version control. Settings in the environment still work and win over `.env`.

The server listens on port 3000. Health workers need the registration code (`REGISTRATION_CODE`) to create an account, so give it only to staff. (`HW_API_KEY` from version 1 is still accepted as the registration code.) Without a code set the server uses `demo-registration-code` and prints a warning.

### Sending real SMS

Without SMS keys the server runs in test mode: reminders are written to the `sms_log` table and nothing is sent. The startup message says which mode it is in. SMS can go through **TextBee**, **Twilio** or **Africa's Talking**, whichever has its keys in `backend/.env`. `SMS_PROVIDER` picks one when several are set, and `SMS_PROVIDER=none` switches SMS off while keeping the keys (handy for going back to the demo data).

TextBee (sends from your own Android phone's SIM; no sender approval or verified numbers needed):

1. Install the TextBee app on an Android phone with a SIM that has airtime or an SMS bundle. This phone sends every reminder, so it must stay on and online.
2. Sign up at textbee.dev. In the dashboard click "Add device" and scan the QR code with the app. Allow it to send SMS, and turn off battery optimisation for it so Android does not put it to sleep.
3. Copy the API key into `backend/.env`:
   ```
   SMS_PROVIDER=textbee
   TEXTBEE_API_KEY=...
   TEXTBEE_DEVICE_ID=...          (optional; npm run check-sms lists the phones and their ids)
   ```
Messages come from the phone's own number and cost its normal SMS rate. A message counts as sent when TextBee accepts it; the phone sends it as soon as it gets it, or when it is next online. TextBee's free plan limits how many SMS you can send; see its pricing page.

Twilio (a trial account only texts numbers you verify):

1. Sign up at twilio.com. In the console, copy the live Account SID and Auth Token (not the test credentials), and get a phone number (trial accounts get one).
2. Under Verified Caller IDs, verify each phone that should receive SMS during the trial, written as +254 7XX XXX XXX (no 0 after +254).
3. Put them in `backend/.env`:
   ```
   SMS_PROVIDER=twilio
   TWILIO_ACCOUNT_SID=AC...
   TWILIO_AUTH_TOKEN=...
   TWILIO_FROM=+1...              (your Twilio phone number)
   ```
Trial messages start with "Sent from your Twilio trial account". Upgrading the account removes that and the verified-number limit. If an SMS to Kenya is refused for the region, allow Kenya under Messaging > Settings > Geo permissions.

Africa's Talking (Kenyan provider, cheaper for real use):

1. Create an account. To try it free, use the Sandbox app; it only delivers to the Africa's Talking simulator.
2. In the dashboard, generate an API key (Settings > API Key) and put it in `backend/.env`:
   ```
   AT_USERNAME=sandbox            (or your live app's username)
   AT_API_KEY=your-key
   AT_SENDER_ID=YourName          (optional, needs approval from Africa's Talking)
   ```

Then, for any of them:

1. Run `npm run check-sms`. It says whether the keys work and shows the balance (for TextBee, the linked phones; for Twilio, the account's numbers and verified numbers), without sending anything. `npm run check-sms -- 0712345678` also sends one test SMS to that number (not saved in the database).
2. Restart the server (`npm start`).

Live SMS needs credit on the account (for TextBee, airtime on the phone's SIM). A message only counts as sent when the provider accepts it for the recipient; refused numbers and an empty balance are logged as failed and retried on the next run.

With live keys (anything but the Africa's Talking `sandbox`) the server refuses to start on a database that still holds the demo data, because the daily run would text its made-up numbers, which may belong to real people. Use a fresh database for real use: `DB_PATH=live.db` in `.env`.

Demo children never get real SMS, whatever the settings: their numbers are made up and may belong to real people. Their reminders are recorded with the status "Not sent (demo child)". Children registered in the app get real SMS as soon as live keys are set, so the demo data can stay loaded next to real children. To limit real SMS further, for example while testing, set `SMS_ONLY_TO` to your own number (several numbers separated by commas): only those numbers then get real SMS, and messages to every other number are recorded as in test mode. The startup message lists the numbers.

An existing `vaccine.db` from version 1 is upgraded in place the first time the new server opens it.

### Demo data

To try the app without typing in records, load made-up demo data:

```
cd backend
npm run seed                 (empty database)
npm run seed -- --add        (adds it next to the accounts and children already there)
npm run seed -- --reset      (deletes everything in the database first)
```

It creates four health worker accounts (`0700000001` to `0700000004`, password `Demo1234`, at Kangemi Health Centre, Riruta Health Centre, Kenyatta National Hospital and Pumwani Maternity Hospital) and 16 children, each showing one situation: a baby born today, a newborn, vaccines due today (at 6 and 14 weeks), due in 3 days, 5 days overdue, missed for 3 weeks with nothing recorded, a late Pentavalent series that moved later doses, fully up to date, Measles-Rubella 1 coming up at 9 months, Measles-Rubella 2 overdue at 18 months, SMS consent withdrawn, twins on one phone, a caregiver with a saved area (Mombasa), and a 2-year-old with everything given. Some children have past SMS in their history. The command prints each caregiver's phone number and child's date of birth for the caregiver lookup.

The dates are worked out from the day you run it, so run it again with `--reset` on another day. The demo children are flagged (`children.is_demo`) and never get real SMS, so the demo data is safe to load with live SMS keys set; databases seeded before the flag existed are flagged by their made-up numbers when the server starts.

If the server starts after 08:00, the daily run sends that day's test-mode reminders within a minute, so "Send today's reminders now" will then find nothing more to send; the messages show in each child's SMS history instead.

### How reminders work

Each SMS asks the caregiver to go to the hospital the child is registered at or the hospital nearest them, and to carry the immunisation card. When the health worker recorded the hospital nearest the family's home, the SMS names it ("Please go to Kenyatta National Hospital or Mbagathi County Hospital (the hospital nearest you) and carry the immunisation card."). If the caregiver shared their location in the app, the nearest facilities to that place are listed as well.

Set `AUTO_REMINDERS=off` in `backend/.env` to stop the daily automatic run: SMS then go out only when a health worker sends them, from a child's record (**Send SMS reminder**), the Follow-up list (**Send SMS**) or the dashboard (**Send today's reminders now**). Every one of those asks for confirmation first, since each SMS costs airtime. The rules below still decide what the dashboard button sends.

- Every morning after 08:00 Nairobi time (or when a health worker taps "Send today's reminders now"), the server looks at every dose not yet given for children whose caregiver agreed to SMS.
- Each reminder has a window: **before** 1 to 3 days ahead, **due** on the day, **overdue** 3 to 13 days late, **follow-up** 14 to 60 days late. If the server was off on the right day, the reminder still goes out on the next run inside the window.
- Each reminder is sent once per child and due date. A child gets at most one SMS a day; the most urgent one goes first.
- Health workers can send a reminder by hand as often as they choose; the app asks for confirmation each time. It is refused only when the caregiver has not agreed to SMS. The dashboard's "Send today's reminders now" skips a child who got a hand-sent SMS in the last 12 hours.
- Doses in a series (Polio, Pentavalent, Pneumococcal, Rotavirus, Measles-Rubella) are kept at least 4 weeks apart. If one is given late, the next one's due date moves back, so the caregiver is not called in too early.

### Main endpoints

Health worker endpoints need the header `Authorization: Bearer <token>` from login or registration.

- `POST /auth/register` name, phone, facility, password, registrationCode
- `POST /auth/login` phone, password. Five wrong passwords lock the account for 15 minutes
- `GET /auth/me`, `POST /auth/logout`, `POST /auth/password`
- `POST /caregiver/lookup` phone, `childName` and `childDob`, no login needed. Children are registered by health workers; caregivers only find them. The name is matched without regard to case or word order ("amani" finds "Amani Otieno"), so each twin is found by their own name
- `GET /stats` numbers for the dashboard
- `POST /children`, `GET /children?q=&filter=all|overdue|due`, `GET /children/:id`
- `PATCH /children/:id` edit details or consent (or `POST` with `X-HTTP-Method-Override: PATCH`), `DELETE /children/:id`
- `POST /children/:id/doses/:CODE/given` (optional `date`) and `/undo`
- `POST /children/:id/doses` record several past doses at once: `{ "given": [{ "code": "BCG", "date": "2026-09-13" }] }`
- `GET /missed`, `GET /due?days=7`
- `POST /children/:id/remind`, `POST /reminders/run`
- `POST /children/:id/notify` optional `message` (up to 500 characters): a notification for the caregiver's app, at any time. Without a message it carries the usual vaccine reminder. Refused only if the same message was sent for that child in the last minute. `GET /children/:id` lists them as `appNotifications`, each with `deliveredAt` once the app has collected it, and `appSeenAt` says when a caregiver's app last checked in for the child
- `POST /caregiver/notifications` phone, child name and date of birth (checked like the lookup, no login) and `after` (the highest id the app already has): the notifications from the last 7 days, marked delivered

Public endpoints for the nearby clinics feature (no login, rate limited to 30 requests a minute per address):

- `GET /facilities/nearby?lat=-4.0435&lon=39.6682` clinics, hospitals and health centres from OpenStreetMap, nearest first, with `name`, `type`, `lat`, `lon`, `distanceKm`, and `phone` and `openingHours` when OpenStreetMap has them
- `GET /geocode?q=Mombasa` places in Kenya matching a town name
- `POST /caregiver/location` with `phone`, `childDob`, `lat`, `lon`, `label` saves the area used for that child's reminders, checked the same way as the caregiver lookup. Send `"lat": null` to delete it

Errors come back as `{ "error": "message", "code": "...", "field": "..." }`, so the app can show the message next to the right field and translate it.

Also public, but searching a saved list on the server instead of asking OpenStreetMap (120 requests a minute per address, as the app searches while the health worker types):

- `GET /facilities/list?q=Kenyatta` facilities whose name or county has words starting with what was typed, as `{ "facilities": [{ "name", "type", "county" }], "more": true|false }`. At most 30, hospitals first. `type` is `hospital`, `health_centre`, `clinic`, or `in_use` for a name that is only known because a health worker or child in this system already has it

### Vaccination verification

When a child is vaccinated at another hospital or clinic, the caregiver can send evidence from the app: the vaccine and dose, the date, the hospital, the doctor's or health worker's name, and a photo of the vaccination card and/or a signed doctor's note (at least one). A health worker reviews it and approves or rejects it. Uploading a picture never records a vaccine by itself; only approval does.

- Statuses: **Not submitted** (grey, no request), **Pending verification** (orange), **Verified** (green), **Rejected** (red, with the health worker's reason; the caregiver corrects it and sends it again).
- Approval goes through the same date checks as recording a dose by hand (not in the future, not before birth, in order within a series). In one transaction it records the dose with the evidence's date, re-plans the later doses, closes the request, writes the audit entry and notifies the caregiver's app. Reminders for that dose stop because it is now given; while a request is only pending, reminders continue.
- One request per child and dose (`UNIQUE(child_id, dose_code)`): a second submission while one is pending is refused, and a dose already recorded needs no evidence. A rejected request is reopened by the resubmission rather than duplicated. `verification_events` keeps every step (submitted, rejected with reason, resubmitted, approved) with who did it and when.
- The health worker sees warnings: the date conflicts with other recorded doses, the date is before the recommended age, the dose was recorded meanwhile, or the same picture was also sent for another child. A health worker whose phone number is the child's caregiver number cannot review that child's request.
- Pictures: the server accepts only JPEG or PNG (checked from the file's content, not its name or declared type), between 1 KB and 5 MB and at least 200×200 pixels. Each file is encrypted with AES-256-GCM, stored under a random name in `backend/documents/`, and only returned to a logged-in health worker, with `Cache-Control: no-store`. Caregivers can upload but never download. Pictures uploaded but never sent with a request are deleted after 24 hours; deleting a child deletes their pictures. The key is `DOCUMENT_KEY` (64 hex characters) or, if that is not set, `backend/document.key`, created on first start. Keep it private and back it up with the database: without it the stored pictures cannot be read. Both `documents/` and `document.key` are in `.gitignore`.
- The app shrinks photos to at most 1600 pixels and about 1.5 MB, turns them upright, shows a preview that can be enlarged, and uploads each one with a progress bar. If the connection fails, **Try again** only sends what is missing.

Endpoints (caregiver ones are checked with phone number, child's name and date of birth, like the lookup; the upload sends them as `X-Caregiver-Phone`, `X-Child-Name` (URL-encoded) and `X-Child-Dob`):

- `POST /caregiver/verification-documents?childId=&kind=card|note` raw JPEG/PNG body, headers `Content-Type`, `X-Caregiver-Phone`, `X-Child-Dob`. Returns `{ "document": { "ref" } }`
- `POST /caregiver/verifications` phone, childDob, childId, doseCode, vaccinationDate, hospital, doctorName, cardDocument and/or noteDocument (refs)
- `POST /caregiver/verifications/:id/resubmit` the same fields, only for a rejected request
- `POST /caregiver/verifications/status` phone, childDob: the children's doses and every request with its status and rejection reason (never the pictures)
- `GET /verifications?status=pending|reviewed|verified|rejected|all` (health worker), with counts
- `GET /verifications/:id` details, documents, warnings and history; `GET /verifications/:id/documents/:documentId` the picture
- `POST /verifications/:id/approve`, `POST /verifications/:id/reject` with a `reason` of at least 5 characters
- `GET /stats` includes `pendingVerifications`, and each dose in `GET /children/:id` carries its `verification` (id, status, rejection reason) if there is one

To demonstrate: on the caregiver side, find a demo child (for example Zawadi Njeri: phone `0711000104` and the date of birth shown in the health worker's list, which depends on when the demo data was loaded), tap **Vaccination verification** on her card, choose one of her overdue 6-week vaccines, take or pick a photo and send it. Log in as a health worker (`0700000001` / `Demo1234`), open **Vaccination verification requests** on the dashboard, enlarge the photo, then approve (the dose turns green in the child's record and the caregiver's app is notified) or reject with a reason (the caregiver sees it and can send corrected evidence). Use pictures of made-up cards only, never real patients' documents.

### Choosing the health facility from a list

When a health worker creates an account or registers a child, the Health facility field opens a list to search and pick from, so the same clinic is spelt the same way everywhere.

- The list is every named hospital, clinic and health centre in Kenya on OpenStreetMap, with its county, saved in `backend/data/facilities-ke.json` (about 1,800 facilities). Refresh it now and then with `npm run update-facilities`, which takes a minute or two.
- OpenStreetMap is missing many smaller facilities (Kangemi Health Centre, for example). So the list also offers every facility name already saved in this system, and "Not in the list? Use ..." keeps whatever was typed. After one health worker enters a missing clinic, others can pick it.
- The server still accepts any facility name, so records saved before the list existed are unchanged.

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

1. In Android Studio (Ladybug or newer) choose File > Open and pick either the `vaccine-reminder` folder or the `android` folder inside it, then let Gradle sync. Both build the same app. Gradle 8.13 does not run on Java 25, which newer Android Studio versions bundle, so `gradle/gradle-daemon-jvm.properties` (in both folders) makes Gradle use Java 21 whatever Gradle JDK is set. It uses a Java 21 already on the computer (Android Studio's downloaded JDKs in `%USERPROFILE%\.jdks` count) or downloads one on the first sync. From a terminal, `gradlew assembleDebug` in either folder builds the app.
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

Notifications from the health worker: on a child's record, "Send notification" sends the caregiver a notification at any time, with the health worker's own message or, left empty, the usual vaccine reminder. It costs nothing, unlike SMS, and does not need SMS consent, because the caregiver chose to get it by saving the child in the app. There is no push service, so the caregiver's app collects notifications every 15 minutes and whenever it is opened. They show as "Message from <clinic>" in a high-priority channel, so they pop up on screen. The child's record shows each one as Waiting or Delivered, and the send dialog warns when no caregiver's app has ever checked in for that child, so the health worker knows to send an SMS instead. Instant delivery would need Firebase Cloud Messaging and a Firebase project.

Things to test on the phone or emulator:

1. Health worker account: create an account with a wrong registration code (refused), then the right one. Sign out from Settings and sign in again. Five wrong passwords should lock the account.
2. Register a child: leave fields empty and check each one shows its own message. Tap Health facility, search "Kenyatta" or a county such as "Mombasa", and pick a facility. Register a child born several weeks ago with consent ticked. The app opens the new child's record.
3. Child record: use "Record past vaccines" for the birth doses, record a dose with a date, undo it, edit the caregiver's phone number, switch SMS reminders off and check the Send SMS button disappears.
4. Late doses: record Pentavalent 1 two weeks late and check Pentavalent 2 moves to four weeks after it.
5. Caregiver: look up the same child with the phone number (any format, e.g. 0712 345 678) and date of birth. A wrong date of birth should show an error. With "Remember on this phone" ticked, the child shows straight away next time.
6. Follow-up tab: check overdue children appear with how late they are. Tap Send SMS (twice: the second should be refused), Call, and on the dashboard "Send today's reminders now". In test mode the message says the reminder was logged.
7. Notifications: on a phone where the caregiver saved a child, open that child's record as a health worker on another phone or the emulator and tap "Send notification", once empty and once with a message. Open the caregiver's app (or wait up to 15 minutes) and check the notification pops up, then that the child's record shows it as Delivered.
8. Switch language on the welcome screen or in Settings and check the screens change to Kiswahili. In Settings > Appearance choose Light or Dark, or Phone setting to follow the phone's dark mode. The choice is kept after the app is closed.

## Limits to state in the report

- The immunisation schedule and the 4-week spacing in `backend/src/schedule.js` are entered from memory of the Kenyan routine schedule. Check them against the current Ministry of Health schedule before using it. The system does not handle upper age limits (for example for rotavirus) or catch-up rules beyond the 4-week spacing.
- Any health worker with the registration code can create an account, and every account sees every child. A real system needs an administrator who approves accounts, can deactivate them, and limits each worker to their facility.
- Rate limits and login lockouts are kept in memory, so they reset when the server restarts, and behind a proxy all users share one address.
- Caregiver lookup uses the phone number, the child's name and date of birth as a light check, limited to 10 wrong tries per phone number per hour. It is not strong security. The same check protects sending vaccination evidence, so someone who knows all three could send false evidence; that is why every request needs a health worker's approval, and why caregivers can upload pictures but never download them. A real system should add a one-time SMS code before submissions.
- Vaccination evidence is a photo, which can be edited or belong to another child. Health workers should confirm with the issuing facility when in doubt; the app says so on every request. Requests cover one dose each, so a visit with four vaccines needs four requests (the same card photo can be sent each time). Old pictures from rejected attempts are kept for the audit trail with no automatic deletion yet; a real deployment needs a retention period. Any health worker can review any request, like the rest of the prototype; a real system would limit this to the child's facility or to designated staff.
- Data Protection Act 2019: this prototype stores names and phone numbers. A real deployment needs a data protection impact assessment, registration with the Data Commissioner, a retention policy, and HTTPS.
- OpenStreetMap coverage of health facilities in Kenya is incomplete and can be out of date: some clinics are missing, misplaced, closed, or have no phone number. It never shows vaccine stock or services. Caregivers must call to confirm that the vaccine is available.
- The public Overpass and Nominatim servers are free, shared and sometimes slow or busy; they are fine for a prototype but not for a national service, which should run its own instances or use the Kenya Master Health Facility List.
- Adding clinics makes the SMS longer, so each reminder can cost 2 or 3 SMS instead of 1.
- Android decides exactly when the 12-hour and 15-minute checks run. Battery saving can delay them by hours, and they stop if the app is force-stopped. A health worker's notification only reaches a phone that saved the child and can reach the server. The SMS reminders from the server are the reliable channel; the in-app alerts and notifications are extra.
- Per the proposal scope, this prototype is not a trial of effect on coverage.
