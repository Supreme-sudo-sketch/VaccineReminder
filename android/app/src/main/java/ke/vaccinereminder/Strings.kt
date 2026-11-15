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
        "Enter the phone number you gave at the clinic and your child's date of birth.",
        "Weka namba ya simu uliyotoa kliniki na tarehe ya kuzaliwa ya mtoto wako.",
    )
    val phone = p("Phone number", "Namba ya simu")
    val phoneHint = "0712 345 678"
    val childDob = p("Child's date of birth", "Tarehe ya kuzaliwa ya mtoto")
    val rememberMe = p("Remember on this phone", "Kumbuka kwenye simu hii")
    val find = p("Find my child", "Tafuta mtoto wangu")
    val addAnother = p("Add another child", "Ongeza mtoto mwingine")
    val forgetChild = p("Remove from this phone", "Ondoa kwenye simu hii")
    val lookupNotFound = p(
        "No child found with that phone number and date of birth. Check both and try again.",
        "Hakuna mtoto aliyepatikana kwa namba hiyo ya simu na tarehe ya kuzaliwa. Angalia zote mbili ujaribu tena.",
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
        "About every 12 hours the app checks for vaccines due within 3 days or missed, and shows a notification.",
        "Takriban kila saa 12 programu huangalia chanjo zinazotarajiwa ndani ya siku 3 au zilizokosekana, na kuonyesha arifa.",
    )
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
    val remindersAuto = p(
        "Reminders go out automatically every morning after 8:00.",
        "Vikumbusho hutumwa kila asubuhi baada ya saa 2:00.",
    )
    val smsModeTest = p("Test mode: messages are logged, not sent.", "Hali ya majaribio: ujumbe unahifadhiwa, hautumwi.")
    val smsModeLive = p("Live: messages are sent by SMS.", "Moja kwa moja: ujumbe unatumwa kwa SMS.")
    val runReminders = p("Send today's reminders now", "Tuma vikumbusho vya leo sasa")
    fun remindersProcessed(n: Int) = if (n == 0) {
        p("No reminders were due today", "Hakuna vikumbusho vilivyohitajika leo")
    } else {
        p("Reminders sent: $n", "Vikumbusho vilivyotumwa: $n")
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
    val reminderTest = p("Reminder logged (test mode, no SMS sent)", "Kumbusho limehifadhiwa (majaribio, SMS haikutumwa)")
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
        "dry-run" -> p("Test mode", "Majaribio")
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
        "all_given" -> "Chanjo zote tayari zimetolewa"
        "recently_reminded" -> "Kumbusho tayari limetumwa kwa mzazi huyu ndani ya saa 12 zilizopita"
        "sms_failed" -> "SMS haikuweza kutumwa. Jaribu tena baadaye"
        "invalid" -> when (e.field) {
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
