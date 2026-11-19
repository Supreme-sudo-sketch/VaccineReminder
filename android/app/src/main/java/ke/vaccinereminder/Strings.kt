package ke.vaccinereminder

enum class Lang(val code: String) {
    EN("en"),
    SW("sw");

    companion object {
        fun of(code: String) = if (code == "sw") SW else EN
    }
}

class Strings(val lang: Lang) {
    private fun p(en: String, sw: String) = if (lang == Lang.EN) en else sw

    // General
    val appName = p("Vaccine Reminder", "Kumbusho la Chanjo")
    val tagline = p("Never miss a child's vaccine date", "Usikose tarehe ya chanjo ya mtoto")
    val back = p("Back", "Rudi")
    val settings = p("Settings", "Mipangilio")
    val language = p("Language", "Lugha")
    val english = "English"
    val kiswahili = "Kiswahili"
    val ok = p("OK", "Sawa")
    val cancel = p("Cancel", "Ghairi")
    val save = p("Save", "Hifadhi")
    val saved = p("Saved", "Imehifadhiwa")
    val retry = p("Try again", "Jaribu tena")
    val loading = p("Loading", "Inapakia")
    val edit = p("Edit", "Hariri")
    val delete = p("Delete", "Futa")
    val choose = p("Choose", "Chagua")
    val clear = p("Clear", "Futa")
    val more = p("More", "Zaidi")

    // Welcome
    val caregiverTitle = p("Parent or caregiver", "Mzazi au mlezi")
    val caregiverSubtitle = p("See your child's vaccine dates and next visit", "Angalia tarehe za chanjo za mtoto wako na ziara inayofuata")
    val workerTitle = p("Health worker", "Mhudumu wa afya")
    val workerSubtitle = p("Register children, record vaccines and follow up", "Sajili watoto, rekodi chanjo na fuatilia")
    fun continueAs(name: String) = p("Continue as $name", "Endelea kama $name")
    val welcomeNote = p(
        "Free SMS reminders 3 days before, on the day, and if a visit is missed. Works on any phone.",
        "Vikumbusho vya SMS bila malipo: siku 3 kabla, siku yenyewe, na ziara ikikosekana. Vinafanya kazi kwenye simu yoyote.",
    )

    // Sign in and sign up
    val signInTitle = p("Health worker sign in", "Kuingia kwa mhudumu wa afya")
    val signInHeading = p("Welcome back", "Karibu tena")
    val signInSubtitle = p(
        "Sign in with the phone number and password you registered with.",
        "Ingia kwa namba ya simu na nenosiri ulilojisajili nalo.",
    )
    val password = p("Password", "Nenosiri")
    val showPassword = p("Show password", "Onyesha nenosiri")
    val hidePassword = p("Hide password", "Ficha nenosiri")
    val signIn = p("Sign in", "Ingia")
    val noAccount = p("New health worker?", "Mhudumu mpya wa afya?")
    val createAccount = p("Create an account", "Fungua akaunti")
    val signUpTitle = p("Create account", "Fungua akaunti")
    val signUpHeading = p("Join your facility", "Jiunge na kituo chako")
    val signUpSubtitle = p(
        "You need the registration code from your facility in-charge.",
        "Unahitaji msimbo wa usajili kutoka kwa msimamizi wa kituo chako.",
    )
    val fullName = p("Full name", "Jina kamili")
    val yourPhone = p("Your phone number", "Namba yako ya simu")
    val confirmPassword = p("Confirm password", "Thibitisha nenosiri")
    val registrationCode = p("Registration code", "Msimbo wa usajili")
    val registrationCodeHint = p("Ask your facility in-charge for this code", "Omba msimbo huu kwa msimamizi wa kituo")
    val passwordHint = p("At least 8 characters, with letters and numbers", "Angalau herufi 8, zenye herufi na namba")
    fun passwordStrengthLabel(level: Int) = when (level) {
        0 -> p("Too weak", "Dhaifu mno")
        1 -> p("Good", "Nzuri")
        else -> p("Strong", "Imara")
    }
    val haveAccount = p("Already have an account?", "Una akaunti tayari?")
    fun welcomeUser(name: String) = p("Welcome, $name", "Karibu, $name")
    val sessionExpired = p("Your session has expired. Please sign in again.", "Muda wako wa kuingia umeisha. Tafadhali ingia tena.")
    val loggedOut = p("You have signed out", "Umetoka")
    val serverSettings = p("Server settings", "Mipangilio ya seva")

    // Validation
    val errName = p("Enter a name", "Weka jina")
    val errPhone = p("Enter a valid Kenyan phone number, e.g. 0712 345 678", "Weka namba sahihi ya simu ya Kenya, mfano 0712 345 678")
    val errPasswordRequired = p("Enter your password", "Weka nenosiri lako")
    val errPasswordMatch = p("Passwords do not match", "Manenosiri hayalingani")
    val errDob = p("Choose the date of birth", "Chagua tarehe ya kuzaliwa")
    val errConsent = p("The caregiver must agree to receive SMS reminders", "Mzazi au mlezi lazima akubali kupokea vikumbusho vya SMS")
    val errUrl = p("Address must start with http:// or https://", "Anwani lazima ianze na http:// au https://")
    val errCode = p("Enter the registration code", "Weka msimbo wa usajili")
    val errFacility = p("Enter the health facility", "Weka kituo cha afya")

    // Caregiver
    val myChildTitle = p("My child's vaccines", "Chanjo za mtoto wangu")
    val lookupIntro = p(
        "Your child is registered by the clinic. Enter the phone number you gave there, your child's name and date of birth.",
        "Mtoto wako husajiliwa na kliniki. Weka namba ya simu uliyotoa huko, jina la mtoto wako na tarehe ya kuzaliwa.",
    )
    val phone = p("Phone number", "Namba ya simu")
    val phoneHint = "0712 345 678"
    val childDob = p("Child's date of birth", "Tarehe ya kuzaliwa ya mtoto")
    val rememberMe = p("Remember on this phone", "Kumbuka kwenye simu hii")
    val find = p("Find my child", "Tafuta mtoto wangu")
    val addAnother = p("Find another of my children", "Tafuta mtoto wangu mwingine")
    val childNameSignInHint = p("As registered at the clinic, e.g. Amani", "Kama lilivyosajiliwa kliniki, mfano Amani")
    val forgetChild = p("Remove from this phone", "Ondoa kwenye simu hii")
    val lookupNotFound = p(
        "No child found with that phone number, name and date of birth. Check them, or ask the clinic to register your child.",
        "Hakuna mtoto aliyepatikana kwa namba hiyo ya simu, jina na tarehe ya kuzaliwa. Viangalie, au uombe kliniki imsajili mtoto wako.",
    )
    val nextVisit = p("Next visit", "Ziara inayofuata")
    val visitToday = p("Visit the clinic today", "Tembelea kliniki leo")
    val visitMissed = p(
        "Missed. Please visit the clinic as soon as you can. It is not too late.",
        "Imekosekana. Tafadhali tembelea kliniki haraka iwezekanavyo. Bado hujachelewa.",
    )
    val allDone = p("All vaccines given. Well done!", "Chanjo zote zimetolewa. Hongera!")
    fun vaccinesGiven(given: Int, total: Int) = p("$given of $total vaccines given", "Chanjo $given kati ya $total zimetolewa")
    val fullSchedule = p("Show full schedule", "Onyesha ratiba kamili")
    val hideSchedule = p("Hide schedule", "Ficha ratiba")
    val smsOn = p("SMS reminders on", "Vikumbusho vya SMS vimewashwa")
    val smsOff = p("SMS reminders off", "Vikumbusho vya SMS vimezimwa")
    val bringCard = p("Remember to bring the immunisation card.", "Kumbuka kuleta kadi ya chanjo.")

    val nearbyTitle = p("Nearby clinics", "Kliniki za karibu")
    val clinicsNearMe = p("Clinics near me", "Kliniki karibu nami")
    val nearbyIntro = p(
        "Away from home? Find clinics near where you are now.",
        "Uko mbali na nyumbani? Tafuta kliniki zilizo karibu na ulipo sasa.",
    )
    val useMyLocation = p("Use my location", "Tumia mahali nilipo")
    val searchTown = p("Search a town", "Tafuta mji")
    val searchTownHint = p("e.g. Mombasa", "mfano Mombasa")
    val locating = p("Finding your location. This can take a minute.", "Inatafuta mahali ulipo. Inaweza kuchukua dakika moja.")
    val locationDenied = p(
        "Location permission was not given. You can search a town instead.",
        "Ruhusa ya mahali haikutolewa. Unaweza kutafuta mji badala yake.",
    )
    val locationOff = p(
        "Location is turned off on this phone. Turn it on, or search a town.",
        "Mahali pamezimwa kwenye simu hii. Pawashe, au tafuta mji.",
    )
    val locationUnavailable = p(
        "Could not find your location. Try again near a window or outdoors, or search a town.",
        "Imeshindwa kupata mahali ulipo. Jaribu tena karibu na dirisha au nje, au tafuta mji.",
    )
    val openSettings = p("Open settings", "Fungua mipangilio")
    val noPlaces = p("No place with that name was found in Kenya.", "Hakuna mahali palipopatikana kwa jina hilo nchini Kenya.")
    val choosePlace = p("Which place did you mean?", "Ulimaanisha mahali gani?")
    val myLocation = p("My location", "Mahali nilipo")
    fun nearArea(label: String, kind: String) =
        if (kind == "hospital") p("Hospitals near $label", "Hospitali karibu na $label") else p("Clinics near $label", "Kliniki karibu na $label")
    fun noHospitalFallback(km: Int) = p(
        "OpenStreetMap shows no hospital within $km km, so these are the nearest smaller clinics and health centres.",
        "OpenStreetMap haionyeshi hospitali yoyote ndani ya km $km, kwa hivyo hizi ni kliniki na vituo vidogo vya afya vilivyo karibu zaidi.",
    )
    val nearestTag = p("Nearest", "Iliyo karibu zaidi")
    val chooseAreaPrompt = p(
        "Use your location or search a town to see clinics on the map.",
        "Tumia mahali ulipo au tafuta mji ili kuona kliniki kwenye ramani.",
    )
    val noClinics = p(
        "OpenStreetMap has no named hospitals or clinics in Kenya within 50 km of this place.",
        "OpenStreetMap haina hospitali wala kliniki zenye jina nchini Kenya ndani ya km 50 kutoka mahali hapa.",
    )
    val callToConfirm = p("Call to confirm that the vaccine is available.", "Piga simu kuthibitisha kuwa chanjo inapatikana.")
    val osmNote = p(
        "Clinic details come from OpenStreetMap, which does not show which vaccines a clinic has.",
        "Taarifa za kliniki zinatoka OpenStreetMap, ambayo haionyeshi chanjo zilizopo kwenye kliniki.",
    )
    fun distanceKm(km: Double) = String.format(java.util.Locale.US, "%.1f", km).let { p("$it km", "km $it") }
    fun facilityType(type: String) = when (type) {
        "hospital" -> p("Hospital", "Hospitali")
        "health_centre" -> p("Health centre", "Kituo cha afya")
        else -> p("Clinic", "Kliniki")
    }
    // Choosing a facility from the list
    val chooseFacility = p("Choose health facility", "Chagua kituo cha afya")
    val chooseFromList = p("Tap to choose from the list", "Gusa ili kuchagua kutoka kwenye orodha")
    val searchFacility = p("Search by name or county", "Tafuta kwa jina au kaunti")
    val facilitySearchHint = p(
        "Type at least 2 letters of the facility's name or county, e.g. \"Kenyatta\" or \"Riruta Nairobi\".",
        "Andika angalau herufi 2 za jina la kituo au kaunti, mfano \"Kenyatta\" au \"Riruta Nairobi\".",
    )
    val noFacilityFound = p("No facility found with that name.", "Hakuna kituo kilichopatikana kwa jina hilo.")
    val keepTyping = p("More facilities match. Keep typing to narrow the list.", "Vituo zaidi vinalingana. Endelea kuandika ili kupunguza orodha.")
    fun useTypedFacility(name: String) = p("Not in the list? Use \"$name\"", "Hakipo kwenye orodha? Tumia \"$name\"")
    val facilityInUse = p("Already used in this system", "Tayari kinatumika kwenye mfumo huu")
    fun county(name: String) = p("$name County", "Kaunti ya $name")
    val facilityListCredit = p(
        "Facility list from OpenStreetMap (© OpenStreetMap contributors). Some facilities may be missing.",
        "Orodha ya vituo kutoka OpenStreetMap (© wachangiaji wa OpenStreetMap). Baadhi ya vituo huenda havipo.",
    )
    val noPhoneOnMap = p("No phone number on OpenStreetMap", "Hakuna namba ya simu kwenye OpenStreetMap")
    fun openingHours(hours: String) = p("Hours: $hours", "Saa: $hours")
    val directions = p("Directions", "Maelekezo")
    val youAreHere = p("You are here", "Uko hapa")
    val showAllOnMap = p("Show all on the map", "Onyesha zote kwenye ramani")
    val useForReminders = p("Use this area for my reminders", "Tumia eneo hili kwa vikumbusho vyangu")
    val useForRemindersBody = p(
        "Your SMS reminders will list the 2 clinics nearest to this area. The area is shared only for this, only the latest one is kept, and it is deleted after 14 days. You can turn it off at any time.",
        "Vikumbusho vyako vya SMS vitaorodhesha kliniki 2 zilizo karibu zaidi na eneo hili. Eneo hili linatumika kwa kusudi hili pekee, eneo la mwisho tu ndilo huhifadhiwa, na hufutwa baada ya siku 14. Unaweza kulizima wakati wowote.",
    )
    fun areaInUse(label: String, until: String) = p("Reminders use: $label, until $until", "Vikumbusho vinatumia: $label, hadi $until")
    fun replaceArea(label: String) = p("Use $label instead", "Tumia $label badala yake")
    val areaSaved = p("Area saved for your reminders", "Eneo limehifadhiwa kwa vikumbusho vyako")
    val areaDeleted = p("Area deleted from your reminders", "Eneo limefutwa kwenye vikumbusho vyako")
    val needChildFirst = p(
        "Find your child on the previous screen first, so the area can be linked to their reminders.",
        "Tafuta mtoto wako kwenye skrini iliyotangulia kwanza, ili eneo liunganishwe na vikumbusho vyake.",
    )
    val chooseAreaFirst = p("Choose an area first: use your location or search a town.", "Chagua eneo kwanza: tumia mahali ulipo au tafuta mji.")
    fun areaOnCard(label: String) = p("SMS lists clinics near $label", "SMS inaorodhesha kliniki karibu na $label")
    val alertsTitle = p("Vaccine alerts on this phone", "Arifa za chanjo kwenye simu hii")
    val alertsBody = p(
        "You get a notification when a vaccine is due within 3 days or missed (checked about every 12 hours), and when the clinic sends you a message (checked every 15 minutes and when you open the app).",
        "Utapata arifa chanjo inapotarajiwa ndani ya siku 3 au imekosekana (huangaliwa takriban kila saa 12), na kliniki inapokutumia ujumbe (huangaliwa kila dakika 15 na unapofungua programu).",
    )
    val messagesChannel = p("Messages from the clinic", "Ujumbe kutoka kliniki")
    fun messageFrom(clinic: String) = p("Message from $clinic", "Ujumbe kutoka $clinic")
    val messageFromClinic = p("Message from the clinic", "Ujumbe kutoka kliniki")
    val alertsNeedPermission = p("Allow notifications to get these alerts.", "Ruhusu arifa ili upate arifa hizi.")
    val allowNotifications = p("Allow notifications", "Ruhusu arifa")
    val checkNow = p("Check now", "Angalia sasa")
    val checkStarted = p("Checking. A notification will show if a vaccine is due.", "Inaangalia. Arifa itaonekana kama chanjo inatarajiwa.")
    val alertsChannel = p("Vaccine alerts", "Arifa za chanjo")
    fun alertTitle(child: String, missed: Boolean) =
        if (missed) p("$child: vaccine missed", "$child: chanjo imekosekana") else p("$child: vaccine due", "$child: chanjo inatarajiwa")
    fun nearestFacility(type: String, name: String, km: Double, phone: String?) =
        when (type) {
            "hospital" -> p("Nearest hospital: $name", "Hospitali iliyo karibu zaidi: $name")
            "health_centre" -> p("Nearest health centre: $name", "Kituo cha afya kilicho karibu zaidi: $name")
            else -> p("Nearest clinic: $name", "Kliniki iliyo karibu zaidi: $name")
        } + " (" + distanceKm(km) + (if (phone != null) ", $phone" else "") + ")."
    fun registeredClinic(name: String) = p("Clinic: $name.", "Kliniki: $name.")

    // Health worker home
    val home = p("Home", "Nyumbani")
    val children = p("Children", "Watoto")
    val followUp = p("Follow-up", "Ufuatiliaji")
    fun hello(name: String) = p("Hello, $name", "Habari, $name")
    val statChildren = p("Children registered", "Watoto waliosajiliwa")
    val statOverdue = p("Have missed vaccines", "Wamekosa chanjo")
    val statDueWeek = p("Due in 7 days", "Wanatarajiwa siku 7")
    val statSms = p("SMS sent, 7 days", "SMS zilizotumwa, siku 7")
    val remindersTitle = p("SMS reminders", "Vikumbusho vya SMS")
    val remindersManual = p(
        "Automatic SMS are off: a reminder goes out only when you send it, here for every child due or late, or from a child's record.",
        "SMS za moja kwa moja zimezimwa: kumbusho linatumwa tu unapolituma, hapa kwa kila mtoto anayehitaji au aliyechelewa, au kutoka kwenye rekodi ya mtoto.",
    )
    val confirmSmsTitle = p("Send SMS reminder?", "Tuma kumbusho la SMS?")
    fun confirmSmsBody(parent: String, phone: String) = p(
        "An SMS about the next vaccines will go to $parent ($phone). It is paid from the clinic's SMS phone.",
        "SMS kuhusu chanjo zinazofuata itatumwa kwa $parent ($phone). Inalipiwa kutoka kwa simu ya SMS ya kliniki.",
    )
    val confirmRunTitle = p("Send today's SMS reminders?", "Tuma vikumbusho vya SMS vya leo?")
    val confirmRunBody = p(
        "Every child with a vaccine due in the next 3 days, due today or missed gets one SMS, unless they already got it. Demo children are skipped. Each SMS is paid from the clinic's SMS phone.",
        "Kila mtoto mwenye chanjo inayohitajika ndani ya siku 3, leo au iliyokosekana atapata SMS moja, isipokuwa kama tayari ameipata. Watoto wa mfano wanarukwa. Kila SMS inalipiwa kutoka kwa simu ya SMS ya kliniki.",
    )
    val remindersAuto = p(
        "Reminders go out automatically every morning after 8:00.",
        "Vikumbusho hutumwa kila asubuhi baada ya saa 2:00.",
    )
    val smsModeTest = p("Test mode: messages are logged, not sent.", "Hali ya majaribio: ujumbe unahifadhiwa, hautumwi.")
    val smsModeLive = p("Live: messages are sent by SMS.", "Moja kwa moja: ujumbe unatumwa kwa SMS.")
    val runReminders = p("Send today's reminders now", "Tuma vikumbusho vya leo sasa")
    fun remindersProcessed(n: Int, sent: Int, waiting: Int = 0, failed: Int = 0): String {
        if (n == 0) return p("No reminders were due today", "Hakuna vikumbusho vilivyohitajika leo")
        if (sent == n) return p("SMS reminders sent: $n", "Vikumbusho vya SMS vilivyotumwa: $n")
        val recorded = n - sent - waiting - failed
        val parts = listOfNotNull(
            p("$sent sent by SMS", "$sent vimetumwa kwa SMS"),
            if (waiting > 0) p("$waiting waiting for the SMS phone", "$waiting vinasubiri simu ya SMS") else null,
            if (failed > 0) p("$failed failed", "$failed vimeshindwa") else null,
            if (recorded > 0) p("$recorded only recorded (demo or test numbers)", "$recorded vimehifadhiwa tu (namba za majaribio)") else null,
        )
        return p("$n reminders: ", "Vikumbusho $n: ") + parts.joinToString(", ")
    }
    val dueThisWeek = p("Due in the next 7 days", "Wanatarajiwa ndani ya siku 7")
    val noneDueThisWeek = p("No visits due this week", "Hakuna ziara wiki hii")
    val registerChild = p("Register child", "Sajili mtoto")
    val search = p("Search name or phone", "Tafuta jina au simu")
    val filterAll = p("All", "Wote")
    val filterOverdue = p("Overdue", "Wamechelewa")
    val filterDue = p("Due this week", "Wiki hii")
    val noChildren = p("No children registered yet", "Bado hakuna watoto waliosajiliwa")
    val noChildrenBody = p("Register a child to start sending reminders.", "Sajili mtoto ili kuanza kutuma vikumbusho.")
    val noMatches = p("No children match", "Hakuna mtoto anayelingana")
    val noMatchesBody = p("Try another name or phone number, or change the filter.", "Jaribu jina au namba nyingine, au badilisha kichujio.")
    val noMissed = p("No missed vaccines", "Hakuna chanjo zilizokosekana")
    val noMissedBody = p("Every registered child is up to date. Great work!", "Kila mtoto aliyesajiliwa yuko sawa. Kazi nzuri!")
    val followUpIntro = p(
        "Children with vaccines past their due date, longest first. Call or send an SMS.",
        "Watoto wenye chanjo zilizopita tarehe, waliochelewa zaidi kwanza. Piga simu au tuma SMS.",
    )
    val call = p("Call", "Piga simu")
    val sendSms = p("Send SMS", "Tuma SMS")
    val noConsent = p("No SMS consent", "Hakuna idhini ya SMS")
    fun lastSms(time: String) = p("Last SMS: $time", "SMS ya mwisho: $time")
    val noSmsYet = p("No SMS sent yet", "Bado hakuna SMS iliyotumwa")
    fun daysOverdue(n: Int) = if (n == 1) p("1 day overdue", "Siku 1 imepita") else p("$n days overdue", "Siku $n zimepita")
    fun missedCount(n: Int) = if (n == 1) p("1 missed", "1 imekosekana") else p("$n missed", "$n zimekosekana")
    val next = p("Next", "Inayofuata")

    // Child form
    val registerTitle = p("Register a child", "Sajili mtoto")
    val editTitle = p("Edit details", "Hariri taarifa")
    val sectionChild = p("Child", "Mtoto")
    val sectionCaregiver = p("Parent or caregiver", "Mzazi au mlezi")
    val sectionClinic = p("Clinic and SMS", "Kliniki na SMS")
    val childName = p("Child's name", "Jina la mtoto")
    val caregiverName = p("Parent or caregiver's name", "Jina la mzazi au mlezi")
    val facility = p("Health facility", "Kituo cha afya")
    val smsLanguage = p("SMS language", "Lugha ya SMS")
    val consent = p(
        "The caregiver agrees to receive SMS reminders about this child's vaccines",
        "Mzazi au mlezi amekubali kupokea vikumbusho vya SMS kuhusu chanjo za mtoto huyu",
    )
    val consentDetail = p(
        "Messages are sent 3 days before each visit, on the day, and if a visit is missed. The caregiver can stop them at any time.",
        "Ujumbe hutumwa siku 3 kabla ya kila ziara, siku yenyewe, na ziara ikikosekana. Mzazi anaweza kuusitisha wakati wowote.",
    )
    val smsReminders = p("Send SMS reminders", "Tuma vikumbusho vya SMS")
    val dobChangeWarning = p(
        "Changing the date of birth recalculates every due date.",
        "Kubadilisha tarehe ya kuzaliwa kunabadilisha tarehe zote za chanjo.",
    )
    val saveChanges = p("Save changes", "Hifadhi mabadiliko")
    val registered = p("Child registered. Reminders are scheduled.", "Mtoto amesajiliwa. Vikumbusho vimepangwa.")
    val changesSaved = p("Changes saved", "Mabadiliko yamehifadhiwa")

    // Child detail
    fun bornOn(date: String) = p("Born $date", "Alizaliwa $date")
    val sendReminder = p("Send SMS reminder", "Tuma kumbusho la SMS")
    val reminderSent = p("Reminder sent", "Kumbusho limetumwa")
    val offlineTitle = p("Offline: saved copy", "Nje ya mtandao: nakala iliyohifadhiwa")
    fun offlineCopy(time: String) = p(
        "The server cannot be reached, so this is the copy saved on this phone ($time). Changes, SMS and proof need the server.",
        "Seva haipatikani, kwa hiyo hii ni nakala iliyohifadhiwa kwenye simu hii ($time). Mabadiliko, SMS na ushahidi vinahitaji seva.",
    )
    val reminderTest = p("Reminder logged (test mode, no SMS sent)", "Kumbusho limehifadhiwa (majaribio, SMS haikutumwa)")
    val reminderDemo = p(
        "Demo child: the reminder was recorded but no SMS was sent (made-up number)",
        "Mtoto wa mfano: kumbusho limehifadhiwa lakini SMS haikutumwa (namba ya kubuni)",
    )
    val reminderQueued = p(
        "The SMS phone has the reminder but has not sent it yet. It goes out as soon as that phone is on with TextBee open, so do not send it again.",
        "Simu ya SMS imepokea kumbusho lakini bado haijalituma. Litatumwa mara simu hiyo ikiwashwa na TextBee ikiwa wazi, kwa hiyo usilitume tena.",
    )
    fun reminderOutcome(status: String) = when (status) {
        "sent" -> reminderSent
        "queued" -> reminderQueued
        "demo" -> reminderDemo
        else -> reminderTest
    }
    val schedule = p("Vaccination schedule", "Ratiba ya chanjo")
    val record = p("Record", "Rekodi")
    val undo = p("Undo", "Tengua")
    fun recordTitle(name: String) = p("Record $name", "Rekodi $name")
    val dateGiven = p("Date given", "Tarehe aliyopewa")
    val undoTitle = p("Undo this vaccine?", "Tengua chanjo hii?")
    fun undoBody(name: String) = p("$name will be marked as not given.", "$name itawekwa kama haijatolewa.")
    fun pastDueTitle(n: Int) = if (n == 1) {
        p("1 vaccine is past its due date", "Chanjo 1 imepita tarehe yake")
    } else {
        p("$n vaccines are past their due date", "Chanjo $n zimepita tarehe zake")
    }
    val pastDueBody = p(
        "If the child already received them, check the immunisation card and record them so the caregiver is not reminded.",
        "Kama mtoto alishazipata, angalia kadi ya chanjo na uzirekodi ili mzazi asitumiwe kumbusho.",
    )
    val recordPast = p("Record past vaccines", "Rekodi chanjo zilizopita")
    val whenGiven = p("When were they given?", "Zilitolewa lini?")
    val onDueDates = p("On their due dates", "Kwenye tarehe zake")
    val onOneDate = p("On one date", "Kwenye tarehe moja")
    val smsHistory = p("SMS history", "Historia ya SMS")

    // Notifications a health worker sends to the caregiver's app
    val sendNotification = p("Send notification", "Tuma arifa")
    fun notifyTitle(child: String) = p("Notify ${child}'s caregiver", "Mjulishe mlezi wa $child")
    val notifyBody = p(
        "It shows on the caregiver's phone if they found this child in the app and ticked \"Remember on this phone\". It arrives within about 15 minutes, sooner if they open the app.",
        "Itaonekana kwenye simu ya mlezi kama alimtafuta mtoto huyu kwenye programu na kuchagua \"Kumbuka kwenye simu hii\". Inafika ndani ya takriban dakika 15, mapema zaidi akifungua programu.",
    )
    fun appLastSeen(time: String) = p("Their app last checked in $time.", "Programu yao iliangalia mara ya mwisho $time.")
    val appNeverSeen = p(
        "No caregiver's app has checked in for this child yet, so it may not arrive. An SMS reaches any phone.",
        "Bado hakuna programu ya mlezi iliyoangalia kwa ajili ya mtoto huyu, kwa hiyo huenda isifike. SMS hufika kwenye simu yoyote.",
    )
    val notificationMessage = p("Message (optional)", "Ujumbe (si lazima)")
    val notificationMessageHint = p("Leave empty to send the usual vaccine reminder", "Acha tupu ili kutuma kumbusho la kawaida la chanjo")
    val send = p("Send", "Tuma")
    val notificationSent = p(
        "Notification sent. It shows on the caregiver's phone within about 15 minutes.",
        "Arifa imetumwa. Itaonekana kwenye simu ya mlezi ndani ya takriban dakika 15.",
    )
    val appNotifications = p("App notifications", "Arifa za programu")
    val delivered = p("Delivered", "Imefika")
    val waiting = p("Waiting", "Inasubiri")
    fun deliveredAt(time: String) = p("Reached the phone $time", "Ilifika kwenye simu $time")
    val deleteRecord = p("Delete record", "Futa rekodi")
    val deleteTitle = p("Delete this record?", "Futa rekodi hii?")
    fun deleteBody(name: String) = p(
        "All of $name's vaccine records and reminders will be removed. This cannot be undone.",
        "Rekodi zote za chanjo na vikumbusho vya $name zitafutwa. Hili haliwezi kutenduliwa.",
    )
    val deleted = p("Record deleted", "Rekodi imefutwa")
    val consentOffNote = p("This caregiver has not agreed to SMS reminders.", "Mzazi huyu hajakubali vikumbusho vya SMS.")
    fun givenOn(date: String) = p("Given $date", "Alipewa $date")
    fun dueOn(date: String) = p("Due $date", "Tarehe $date")

    fun smsKind(kind: String) = when (kind) {
        "before" -> p("Reminder before visit", "Kumbusho kabla ya ziara")
        "due" -> p("Visit day", "Siku ya ziara")
        "overdue" -> p("Missed visit", "Ziara iliyokosekana")
        "followup" -> p("Second reminder", "Kumbusho la pili")
        "manual" -> p("Sent by health worker", "Imetumwa na mhudumu")
        else -> "SMS"
    }

    fun smsStatus(status: String) = when (status) {
        "sent" -> p("Sent", "Imetumwa")
        "queued" -> p("Waiting for SMS phone", "Inasubiri simu ya SMS")
        "dry-run" -> p("Not sent (test mode)", "Haikutumwa (majaribio)")
        "demo" -> p("Not sent (demo child)", "Haikutumwa (mtoto wa mfano)")
        else -> p("Failed", "Imeshindwa")
    }

    // Settings
    val settingsServer = p("Server", "Seva")
    val serverAddress = p("Server address", "Anwani ya seva")
    val serverHint = p(
        "Emulator: http://10.0.2.2:3000. Phone on a USB cable: http://127.0.0.1:3000 after running \"adb reverse tcp:3000 tcp:3000\" on the computer. Phone on Wi-Fi: the computer's address, e.g. http://192.168.0.20:3000.",
        "Emulator: http://10.0.2.2:3000. Simu kwa kebo ya USB: http://127.0.0.1:3000 baada ya kuendesha \"adb reverse tcp:3000 tcp:3000\" kwenye kompyuta. Simu kwa Wi-Fi: anwani ya kompyuta, mfano http://192.168.0.20:3000.",
    )
    val testConnection = p("Save and test", "Hifadhi na jaribu")
    fun connected(date: String, mode: String) =
        p("Connected. Server date $date. SMS: ${smsModeName(mode)}.", "Imeunganishwa. Tarehe ya seva $date. SMS: ${smsModeName(mode)}.")
    fun smsModeName(mode: String) = if (mode == "live") p("live", "moja kwa moja") else p("test mode", "majaribio")
    val appearance = p("Appearance", "Mwonekano")
    val themeSystem = p("Phone setting", "Kama simu")
    val themeLight = p("Light", "Mwanga")
    val themeDark = p("Dark", "Giza")
    val account = p("Account", "Akaunti")
    val changePassword = p("Change password", "Badilisha nenosiri")
    val currentPassword = p("Current password", "Nenosiri la sasa")
    val newPassword = p("New password", "Nenosiri jipya")
    val passwordChanged = p("Password changed. Other phones were signed out.", "Nenosiri limebadilishwa. Simu nyingine zimetolewa.")
    val logout = p("Sign out", "Toka")
    val logoutConfirm = p("Sign out of this phone?", "Toka kwenye simu hii?")
    val thisPhone = p("Saved on this phone", "Vilivyohifadhiwa kwenye simu hii")
    fun savedChildren(n: Int) = if (n == 1) p("1 saved child lookup", "Utafutaji 1 umehifadhiwa") else p("$n saved child lookups", "Utafutaji $n umehifadhiwa")
    val noSaved = p("Nothing saved", "Hakuna kilichohifadhiwa")
    val forgetAll = p("Forget saved children", "Sahau watoto waliohifadhiwa")
    val forgotten = p("Saved children removed", "Watoto waliohifadhiwa wameondolewa")
    val about = p("About", "Kuhusu")
    fun version(v: String) = p("Version $v", "Toleo $v")
    val aboutBody = p(
        "Prototype for an SMS-based childhood vaccination reminder system in Kenya. Check the schedule against current Ministry of Health guidance.",
        "Mfano wa mfumo wa vikumbusho vya chanjo za watoto kwa SMS nchini Kenya. Linganisha ratiba na mwongozo wa sasa wa Wizara ya Afya.",
    )

    fun milestone(code: String): String = when {
        code == "birth" -> p("At birth", "Wakati wa kuzaliwa")
        code.endsWith("w") -> code.dropLast(1).let { p("$it weeks", "Wiki $it") }
        code.endsWith("m") -> code.dropLast(1).let { p("$it months", "Miezi $it") }
        else -> code
    }

    fun relativeDays(days: Long): String {
        val n = kotlin.math.abs(days)
        return when {
            days == 0L -> p("Today", "Leo")
            days == 1L -> p("Tomorrow", "Kesho")
            days == -1L -> p("Yesterday", "Jana")
            n < 14 -> if (days > 0) p("In $n days", "Baada ya siku $n") else p("$n days ago", "Siku $n zilizopita")
            n < 8 * 7 -> (n / 7).let { w -> if (days > 0) p("In $w weeks", "Baada ya wiki $w") else p("$w weeks ago", "Wiki $w zilizopita") }
            else -> (n / 30).let { m -> if (days > 0) p("In $m months", "Baada ya miezi $m") else p("$m months ago", "Miezi $m iliyopita") }
        }
    }

    fun ageDays(n: Int) = if (n == 1) p("1 day old", "Siku 1") else p("$n days old", "Siku $n")
    fun ageWeeks(n: Int) = p("$n weeks old", "Wiki $n")
    fun ageMonths(n: Int) = if (n == 1) p("1 month old", "Mwezi 1") else p("$n months old", "Miezi $n")
    fun ageYears(y: Int, m: Int): String {
        val years = if (y == 1) p("1 year", "Mwaka 1") else p("$y years", "Miaka $y")
        if (m == 0) return years
        return years + p(if (m == 1) ", 1 month" else ", $m months", if (m == 1) " na mwezi 1" else " na miezi $m")
    }

    // Vaccination verification
    val verificationTitle = p("Vaccination verification", "Uthibitisho wa chanjo")
    val verificationRequests = p("Vaccination verification requests", "Maombi ya uthibitisho wa chanjo")
    val verificationRequestsShort = p("Verification requests", "Maombi ya uthibitisho")
    fun verificationStatus(status: String) = when (status) {
        "pending" -> p("Pending verification", "Inasubiri uthibitisho")
        "verified" -> p("Verified", "Imethibitishwa")
        "rejected" -> p("Rejected", "Imekataliwa")
        else -> p("Not submitted", "Haijatumwa")
    }
    val verificationIntro = p(
        "Was your child vaccinated at another hospital or clinic? Send a photo of the vaccination card or a signed doctor's note. A health worker checks it before the vaccine is added to your child's record.",
        "Je, mtoto wako alichanjwa katika hospitali au kliniki nyingine? Tuma picha ya kadi ya chanjo au barua ya daktari iliyotiwa sahihi. Mhudumu wa afya ataikagua kabla chanjo haijaongezwa kwenye rekodi ya mtoto wako.",
    )
    val newEvidence = p("Send new evidence", "Tuma ushahidi mpya")
    val myRequests = p("My requests", "Maombi yangu")
    val noRequestsTitle = p("No requests yet", "Bado hakuna maombi")
    val noRequestsBody = p("Evidence you send appears here with its status.", "Ushahidi unaotuma utaonekana hapa pamoja na hali yake.")
    val findChildFirst = p("Find your child first", "Tafuta mtoto wako kwanza")
    val findChildFirstBody = p(
        "Go back and enter your phone number and your child's date of birth.",
        "Rudi na uweke namba yako ya simu na tarehe ya kuzaliwa ya mtoto wako.",
    )
    fun givenAt(date: String, hospital: String) = p("Given $date at $hospital", "Ilitolewa $date katika $hospital")
    fun byDoctor(name: String) = p("By $name", "Na $name")
    fun sentOn(time: String) = p("Sent $time", "Ilitumwa $time")
    val pendingNote = p(
        "A health worker will check this. You will get a notification when it has been reviewed.",
        "Mhudumu wa afya ataikagua. Utapata arifa ikishakaguliwa.",
    )
    val verifiedNote = p("Added to your child's vaccination record.", "Imeongezwa kwenye rekodi ya chanjo ya mtoto wako.")
    val notAccepted = p("Not accepted", "Haikukubaliwa")
    fun reasonLine(reason: String) = p("Reason: $reason", "Sababu: $reason")
    val sendCorrected = p("Send corrected evidence", "Tuma ushahidi uliorekebishwa")
    fun notAcceptedCount(n: Int) =
        if (n == 1) p("1 request was not accepted", "Ombi 1 halikukubaliwa") else p("$n requests were not accepted", "Maombi $n hayakukubaliwa")
    fun waitingReview(n: Int) = when (n) {
        0 -> p("No requests waiting", "Hakuna maombi yanayosubiri")
        else -> p("$n waiting for review", "$n yanasubiri ukaguzi")
    }

    val newEvidenceTitle = p("New evidence", "Ushahidi mpya")
    val resubmitTitle = p("Correct and send again", "Rekebisha na utume tena")
    val childLabel = p("Child", "Mtoto")
    val vaccineDose = p("Vaccine and dose", "Chanjo na dozi")
    val noDosesLeft = p(
        "Every vaccine is already recorded or waiting for review.",
        "Chanjo zote tayari zimerekodiwa au zinasubiri ukaguzi.",
    )
    val vaccinationDate = p("Date of vaccination", "Tarehe ya chanjo")
    val hospitalLabel = p("Hospital or clinic", "Hospitali au kliniki")
    val doctorLabel = p("Doctor's or health worker's name", "Jina la daktari au mhudumu wa afya")
    val evidenceHeader = p("Supporting documents", "Nyaraka za ushahidi")
    val evidenceHint = p(
        "Add at least one. Make sure the child's name, the vaccine, the date and the stamp can be read.",
        "Ongeza angalau moja. Hakikisha jina la mtoto, chanjo, tarehe na muhuri vinasomeka.",
    )
    val cardPhoto = p("Vaccination card", "Kadi ya chanjo")
    val notePhoto = p("Signed doctor's note", "Barua ya daktari iliyotiwa sahihi")
    val takePhoto = p("Take photo", "Piga picha")
    val gallery = p("Gallery", "Picha zilizopo")
    val replace = p("Replace", "Badilisha")
    val remove = p("Remove", "Ondoa")
    val preparingPhoto = p("Preparing photo", "Inaandaa picha")
    fun uploading(percent: Int) = p("Uploading $percent%", "Inapakia $percent%")
    val uploaded = p("Uploaded", "Imepakiwa")
    val tapToEnlarge = p("Tap to enlarge", "Gusa kukuza")
    val errDose = p("Choose the vaccine and dose", "Chagua chanjo na dozi")
    val errVaccinationDate = p("Choose the date the vaccine was given", "Chagua tarehe chanjo ilipotolewa")
    val errHospital = p("Enter the hospital or clinic", "Weka hospitali au kliniki")
    val errDoctor = p("Enter the doctor's or health worker's name", "Weka jina la daktari au mhudumu wa afya")
    val errNoDocument = p("Add a photo of the vaccination card or the doctor's note", "Ongeza picha ya kadi ya chanjo au barua ya daktari")
    fun badImage(problem: EvidenceImages.Problem) = when (problem) {
        EvidenceImages.Problem.NOT_A_PICTURE -> p("This file is not a picture that can be used. Choose a photo.", "Faili hii si picha inayoweza kutumika. Chagua picha.")
        EvidenceImages.Problem.TOO_SMALL -> p("This photo is too small to read. Take it closer.", "Picha hii ni ndogo mno kusomeka. Ipige karibu zaidi.")
        EvidenceImages.Problem.TOO_LARGE -> p("This photo is too large. Choose another one.", "Picha hii ni kubwa mno. Chagua nyingine.")
    }
    val noCameraApp = p("No camera app was found on this phone.", "Hakuna programu ya kamera kwenye simu hii.")
    val submitEvidence = p("Send for verification", "Tuma kwa uthibitisho")
    val confirmSendTitle = p("Send for verification?", "Tuma kwa uthibitisho?")
    fun confirmSendBody(child: String, dose: String) = p(
        "A health worker will check the evidence for $child's $dose. It is added to the record only after they approve it.",
        "Mhudumu wa afya atakagua ushahidi wa chanjo ya $dose ya $child. Itaongezwa kwenye rekodi baada ya kuidhinishwa tu.",
    )
    val evidenceSent = p("Sent. A health worker will review it.", "Imetumwa. Mhudumu wa afya ataikagua.")
    val uploadFailed = p("The evidence was not sent", "Ushahidi haukutumwa")

    val pendingTab = p("Pending", "Zinazosubiri")
    val reviewedTab = p("Reviewed", "Zilizokaguliwa")
    val noPendingTitle = p("No requests waiting", "Hakuna maombi yanayosubiri")
    val noPendingBody = p(
        "When parents send evidence of vaccines given elsewhere, it appears here for review.",
        "Wazazi wakituma ushahidi wa chanjo zilizotolewa kwingine, utaonekana hapa kwa ukaguzi.",
    )
    val noReviewedTitle = p("Nothing reviewed yet", "Bado hakuna kilichokaguliwa")
    val noReviewedBody = p("Requests that have been approved or rejected are listed here.", "Maombi yaliyoidhinishwa au kukataliwa yataonekana hapa.")
    fun reviewedBy(name: String, time: String) = p("Reviewed by $name · $time", "Imekaguliwa na $name · $time")
    fun photoCount(n: Int) = if (n == 1) p("1 photo", "Picha 1") else p("$n photos", "Picha $n")
    fun sentTimes(n: Int) = p("Sent $n times", "Imetumwa mara $n")
    val sectionVaccination = p("Vaccination", "Chanjo")
    val sectionEvidence = p("Evidence", "Ushahidi")
    val sectionHistory = p("History", "Historia")
    val dateGivenLabel = p("Date given", "Tarehe iliyotolewa")
    val inRecordNow = p("In the record now", "Kwenye rekodi sasa")
    fun recordGiven(date: String) = p("Given $date", "Imetolewa $date")
    fun recordNotGiven(due: String) = p("Not given yet, due $due", "Bado haijatolewa, tarehe $due")
    val openChildRecord = p("Open child's record", "Fungua rekodi ya mtoto")
    val imageLoadFailed = p("Could not load the picture", "Imeshindwa kupakia picha")
    val verifyAdvice = p(
        "Check that the picture shows this child's name, the vaccine, the date and the facility stamp. If in doubt, call the facility to confirm before approving: a photo alone is not proof.",
        "Hakikisha picha inaonyesha jina la mtoto huyu, chanjo, tarehe na muhuri wa kituo. Ukiwa na shaka, piga simu kituo kuthibitisha kabla ya kuidhinisha: picha pekee si uthibitisho.",
    )
    val approve = p("Approve", "Idhinisha")
    val reject = p("Reject", "Kataa")
    val approveTitle = p("Approve this evidence?", "Idhinisha ushahidi huu?")
    fun approveBody(dose: String, date: String, child: String) = p(
        "$dose will be recorded as given to $child on $date, and the next vaccine dates will be updated. The caregiver will be notified.",
        "$dose itarekodiwa kuwa imetolewa kwa $child tarehe $date, na tarehe za chanjo zinazofuata zitasasishwa. Mzazi atajulishwa.",
    )
    val rejectTitle = p("Reject this evidence?", "Kataa ushahidi huu?")
    val rejectBody = p(
        "Tell the caregiver what is wrong so they can send corrected evidence. The vaccine record is not changed.",
        "Mweleze mzazi kilichokosewa ili atume ushahidi uliorekebishwa. Rekodi ya chanjo haibadilishwi.",
    )
    val rejectReason = p("Reason", "Sababu")
    val errReason = p("Write the reason (at least 5 characters)", "Andika sababu (angalau herufi 5)")
    val reasonUnclear = p("The photo is not clear enough to read.", "Picha haiko wazi kiasi cha kusomeka.")
    val reasonMismatch = p("The details do not match the card.", "Maelezo hayalingani na kadi.")
    val reasonUnconfirmed = p("The facility could not confirm this vaccination.", "Kituo hakikuweza kuthibitisha chanjo hii.")
    val approvedDone = p("Approved and added to the record", "Imeidhinishwa na kuongezwa kwenye rekodi")
    val rejectedDone = p("Rejected. The caregiver has been notified.", "Imekataliwa. Mzazi amejulishwa.")
    fun verifiedBy(name: String, time: String) = p(
        "Verified by $name, $time. The vaccine is in the child's record.",
        "Imethibitishwa na $name, $time. Chanjo imo kwenye rekodi ya mtoto.",
    )
    fun rejectedBy(name: String, time: String) = p("Rejected by $name, $time", "Imekataliwa na $name, $time")
    fun eventLabel(action: String) = when (action) {
        "submitted" -> p("Sent by the caregiver", "Imetumwa na mzazi")
        "resubmitted" -> p("Sent again by the caregiver", "Imetumwa tena na mzazi")
        "approved" -> p("Approved", "Imeidhinishwa")
        "rejected" -> p("Rejected", "Imekataliwa")
        else -> action
    }
    fun warning(w: VerificationWarning) = when (w.code) {
        "own_child" -> p(
            "This child is registered with your own phone number, so another health worker must review it.",
            "Mtoto huyu amesajiliwa kwa namba yako ya simu, kwa hiyo mhudumu mwingine wa afya lazima akague.",
        )
        "already_given" -> p(w.text, "Chanjo hii tayari imerekodiwa kuwa imetolewa. Kataa ombi hili.")
        "early" -> p("The date is before the recommended date for this dose.", "Tarehe ni kabla ya tarehe inayopendekezwa kwa dozi hii.")
        "reused_document" -> p("The same picture was also sent for another child.", "Picha hiyo hiyo ilitumwa pia kwa mtoto mwingine.")
        "date_conflict" -> p(w.text, "Tarehe hii haiendani na chanjo nyingine zilizorekodiwa: ${w.text}")
        else -> w.text
    }
    fun evidenceWaiting(n: Int) = if (n == 1) {
        p("1 vaccination proof waiting for review", "Uthibitisho 1 wa chanjo unasubiri ukaguzi")
    } else {
        p("$n vaccination proofs waiting for review", "Uthibitisho $n wa chanjo unasubiri ukaguzi")
    }
    val evidenceWaitingBody = p(
        "The caregiver sent evidence of vaccines given elsewhere.",
        "Mzazi ametuma ushahidi wa chanjo zilizotolewa kwingine.",
    )
    val review = p("Review", "Kagua")

    // Child or parent, and families
    fun parentLine(name: String, phone: String) = p("Parent: $name · $phone", "Mzazi: $name · $phone")
    val nearestHospitalLabel = p("Nearest hospital to home", "Hospitali iliyo karibu na nyumbani")
    val nearestHospitalHint = p(
        "Ask the parent which hospital is closest to their home. The SMS names it next to the clinic above.",
        "Muulize mzazi hospitali iliyo karibu zaidi na nyumbani kwake. SMS itaitaja pamoja na kliniki iliyo juu.",
    )
    val parentPhone = p("Parent's phone number", "Namba ya simu ya mzazi")
    val parentPhoneHint = p("SMS reminders go to this number", "Vikumbusho vya SMS vinatumwa kwa namba hii")
    val caregiverHint = p(
        "The person who brings the child to the clinic and receives the reminders.",
        "Mtu anayemleta mtoto kliniki na kupokea vikumbusho.",
    )
    fun familyTitle(parent: String) = p("$parent's children", "Watoto wa $parent")
    val familyEmpty = p(
        "Does this parent have another child? Add them with the parent's details already filled in.",
        "Je, mzazi huyu ana mtoto mwingine? Mwongeze, maelezo ya mzazi yakiwa tayari yamejazwa.",
    )
    val twin = p("Twin", "Pacha")
    val addChild = p("Add child", "Ongeza mtoto")
    fun addingTwinTitle(name: String) = p("Registering a twin of $name", "Kusajili pacha wa $name")
    fun addingSiblingTitle(parent: String) = p("Registering another child of $parent", "Kusajili mtoto mwingine wa $parent")
    val twinPrefilledNote = p(
        "The parent and the date of birth are filled in. Enter the twin's name and tick consent.",
        "Mzazi na tarehe ya kuzaliwa vimejazwa. Weka jina la pacha na uweke alama ya idhini.",
    )
    val siblingPrefilledNote = p(
        "The parent's details are filled in. Enter the child's name and date of birth, and tick consent.",
        "Maelezo ya mzazi yamejazwa. Weka jina na tarehe ya kuzaliwa ya mtoto, na uweke alama ya idhini.",
    )
    val addAnotherHint = p(
        "Another child? Enter that child's name and date of birth with the same phone number. The clinic registers children; here you only find them.",
        "Mtoto mwingine? Weka jina na tarehe ya kuzaliwa ya mtoto huyo kwa namba ile ile ya simu. Kliniki ndiyo husajili watoto; hapa unawatafuta tu.",
    )
    val proofLabel = p("Proof", "Ushahidi")
    val sendProof = p("Send proof", "Tuma ushahidi")
    val sendAgain = p("Send again", "Tuma tena")
    val viewProof = p("View proof", "Angalia ushahidi")
    val cardShort = p("Card photo", "Picha ya kadi")
    val noteShort = p("Doctor's note", "Barua ya daktari")
    val noProofWorker = p(
        "No proof from the caregiver yet. If you gave this vaccine, tap Record.",
        "Mzazi bado hajatuma ushahidi. Kama ulitoa chanjo hii, gusa Rekodi.",
    )
    val noProofYet = p("No proof sent. Vaccinated elsewhere? Send a photo of the card.", "Hakuna ushahidi. Alichanjwa kwingine? Tuma picha ya kadi.")

    fun status(code: String) = when (code) {
        "given" -> p("Given", "Amepata")
        "due" -> p("Due today", "Leo")
        "overdue" -> p("Overdue", "Amechelewa")
        else -> p("Upcoming", "Inakuja")
    }

    // Turns an API failure into a message for this language. English server messages are used
    // as they are; for Kiswahili the error code picks a translation.
    fun error(e: ApiException): String = when (e.kind) {
        ApiException.Kind.NETWORK -> (e.address ?: "").let { at ->
            p(
                "Cannot reach the server at $at. Check that the server is running, that the server address in Settings is right, and that you are online.",
                "Imeshindwa kufikia seva kwenye $at. Hakikisha seva inafanya kazi, anwani ya seva kwenye Mipangilio ni sahihi, na uko mtandaoni.",
            )
        }
        ApiException.Kind.BAD_ADDRESS -> p(
            "The server address is not valid. Fix it in Settings.",
            "Anwani ya seva si sahihi. Irekebishe kwenye Mipangilio.",
        )
        ApiException.Kind.BAD_REPLY -> p(
            "Unexpected reply from the server. Is the server address correct?",
            "Seva imejibu kwa njia isiyotarajiwa. Je, anwani ya seva ni sahihi?",
        )
        ApiException.Kind.SERVER -> if (lang == Lang.EN) e.message ?: generic else swahiliError(e) ?: e.message ?: generic
    }

    private val generic = p("Something went wrong. Please try again.", "Hitilafu imetokea. Tafadhali jaribu tena.")

    private fun swahiliError(e: ApiException): String? = when (e.code) {
        "invalid_credentials" -> if (e.field == "currentPassword") "Nenosiri la sasa si sahihi" else "Namba ya simu au nenosiri si sahihi"
        "locked" -> "Majaribio mengi yasiyo sahihi. Subiri dakika 15 kisha ujaribu tena"
        "rate_limited" -> "Maombi mengi mno. Jaribu tena baadaye"
        "bad_registration_code" -> "Msimbo wa usajili si sahihi. Muulize msimamizi wa kituo chako"
        "phone_taken" -> "Akaunti yenye namba hii tayari ipo. Ingia badala yake"
        "weak_password" -> passwordHint
        "session_expired", "auth_required" -> sessionExpired
        "not_found" -> "Haikupatikana"
        "duplicate_child" -> "Mtoto huyu tayari amesajiliwa"
        "no_consent" -> consentOffNote
        "all_given" -> if (e.field == "message") "Chanjo zote tayari zimetolewa. Andika ujumbe ili kutuma badala yake" else "Chanjo zote tayari zimetolewa"
        "recently_notified" -> "Arifa hii imetumwa sasa hivi"
        "recently_reminded" -> "Kumbusho tayari limetumwa kwa mzazi huyu ndani ya saa 12 zilizopita"
        "sms_failed" -> "SMS haikuweza kutumwa. Hakikisha simu ya SMS imewashwa, ina mtandao na programu ya TextBee iko wazi, kisha ujaribu tena"
        "no_document" -> errNoDocument
        "bad_document" -> "Picha iliyopakiwa haikupatikana. Ipakie tena"
        "already_pending" -> "Ushahidi wa chanjo hii tayari unasubiri ukaguzi"
        "already_given" -> "Chanjo hii tayari imerekodiwa kuwa imetolewa"
        "not_pending" -> "Ombi hili tayari limekaguliwa"
        "not_rejected" -> "Ni ombi lililokataliwa pekee linaloweza kutumwa tena"
        "bad_file" -> "Ni picha za JPEG au PNG zinazosomeka pekee zinazoweza kupakiwa"
        "file_too_large" -> "Faili ni kubwa mno"
        "too_many_uploads" -> "Picha nyingi mno zimepakiwa bila kutuma ombi. Jaribu tena kesho"
        "own_child" -> "Mtoto huyu amesajiliwa kwa namba yako ya simu. Mhudumu mwingine wa afya lazima akague"
        "reason_required" -> errReason
        "document_unreadable" -> "Hati hii haiwezi kusomwa tena"
        "no_storage" -> "Seva haijawekwa kuhifadhi nyaraka"
        "invalid" -> when (e.field) {
            "vaccinationDate" -> errVaccinationDate
            "hospital" -> errHospital
            "doctorName" -> errDoctor
            "doseCode" -> errDose
            "phone" -> errPhone
            "dob", "childDob" -> "Tarehe ya kuzaliwa si sahihi"
            "date" -> "Tarehe si sahihi"
            "consent" -> errConsent
            "name", "childName", "caregiverName" -> errName
            "facility" -> errFacility
            else -> null
        }
        else -> null
    }
}
