package com.example.gramayatri

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.cardview.widget.CardView

import com.google.firebase.database.PropertyName

// Data Models
data class BusStop(
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("lat") @set:PropertyName("lat") var lat: Double = 0.0,
    @get:PropertyName("lng") @set:PropertyName("lng") var lng: Double = 0.0,
    @get:PropertyName("time_to_next") @set:PropertyName("time_to_next") var timeToNext: Int = 0,
    @get:PropertyName("distance") @set:PropertyName("distance") var distance: Int = 0
)

data class ActiveBus(
    @get:PropertyName("last_stop_index") @set:PropertyName("last_stop_index") var lastStopIndex: Int = -1,
    @get:PropertyName("reported_by") @set:PropertyName("reported_by") var reportedBy: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = 0,
    @get:PropertyName("is_on_board") @set:PropertyName("is_on_board") var isOnBoard: Boolean = false,
    @get:PropertyName("reporter_points") @set:PropertyName("reporter_points") var reporterPoints: Int = 0,
    @get:PropertyName("lat") @set:PropertyName("lat") var lat: Double = 0.0,
    @get:PropertyName("lng") @set:PropertyName("lng") var lng: Double = 0.0
)

data class UserAlert(
    @get:PropertyName("type") @set:PropertyName("type") var type: String = "", // LATE, CANCELED
    @get:PropertyName("message") @set:PropertyName("message") var message: String = "",
    @get:PropertyName("reported_by") @set:PropertyName("reported_by") var reportedBy: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = 0,
    @get:PropertyName("reporter_points") @set:PropertyName("reporter_points") var reporterPoints: Int = 0
)

data class Route(
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("stops") @set:PropertyName("stops") var stops: List<BusStop> = emptyList(),
    @get:PropertyName("active_bus") @set:PropertyName("active_bus") var activeBus: ActiveBus? = null,
    @get:PropertyName("alerts") @set:PropertyName("alerts") var alerts: Map<String, UserAlert> = emptyMap()
)

class MainActivity : AppCompatActivity() {
    private lateinit var rootDb: DatabaseReference
    private lateinit var currentDb: DatabaseReference
    private lateinit var usersDb: DatabaseReference
    private lateinit var prefs: SharedPreferences

    private var currentUserName: String = ""
    private var currentStopIndex: Int = -1
    private var myDestinationIndex: Int = 28 // Default to Chikkamagaluru
    private var isAlertEnabled: Boolean = true
    private var stopsList = mutableListOf<BusStop>()
    private var userNearestStopIndex: Int = -1
    private lateinit var llStops: LinearLayout
    private lateinit var llAlerts: LinearLayout
    private var routeListener: ValueEventListener? = null
    private var alertShown = false
    private var lastNotifiedStopIndex = -1
    private var lastActiveBus: ActiveBus? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // UI View handles
    private lateinit var layoutRegister: View
    private lateinit var layoutLogin: View
    private lateinit var layoutForgotPass: View
    private lateinit var layoutMainApp: View
    private lateinit var viewRoute: View
    private lateinit var viewPing: View
    private lateinit var viewAlerts: View
    private lateinit var progressBar: ProgressBar
    private lateinit var btnProfile: ImageButton
    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var cbAlertEnabled: CheckBox

    private val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

    private fun getTripStartTimes(): List<Int> {
        return listOf(
            6 * 60,       // 06:00 AM
            9 * 60 + 30,  // 09:30 AM
            11 * 60 + 30, // 11:30 AM
            13 * 60 + 30, // 01:30 PM
            15 * 60 + 30, // 03:30 PM
            17 * 60,      // 05:00 PM
            19 * 60 + 30  // 07:30 PM
        )
    }

    private fun getScheduledTimesForStop(stopIdx: Int): List<Int> {
        if (stopsList.isEmpty() || stopIdx < 0) return emptyList()
        val offset = (0 until stopIdx).sumOf { stopsList[it].timeToNext }
        return getTripStartTimes().map { it + offset }
    }

    private fun getNextScheduledBusMins(): Int {
        val calendar = java.util.Calendar.getInstance()
        val currentMins = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)

        val scheduledTimes = getScheduledTimesForStop(userNearestStopIndex.coerceAtLeast(0))
        val nextTime = scheduledTimes.firstOrNull { it > currentMins }

        return if (nextTime != null) {
            nextTime - currentMins
        } else {
            // Tomorrow's first bus or some other logic. 
            // For now, let's just return -1 if no more buses today.
            -1
        }
    }

    private fun showFullTimetableDialog() {
        val stopIdx = userNearestStopIndex.coerceAtLeast(0)
        val stopName = if (stopsList.isNotEmpty()) stopsList[stopIdx].name else "selected stop"
        val scheduledTimes = getScheduledTimesForStop(stopIdx)

        val timeStrings = scheduledTimes.map { mins ->
            val h = (mins / 60) % 24
            val m = mins % 60
            val ampm = if (h < 12) "AM" else "PM"
            val displayH = if (h % 12 == 0) 12 else h % 12
            String.format(java.util.Locale.getDefault(), "%02d:%02d %s", displayH, m, ampm)
        }

        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, timeStrings)
        listView.adapter = adapter

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.timetable_title, stopName))
            .setView(listView)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force Light Mode programmatically
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase Initialization
        val database = FirebaseDatabase.getInstance("https://gramayatri-d641b-default-rtdb.europe-west1.firebasedatabase.app/")
        rootDb = database.getReference("routes")
        usersDb = database.getReference("users")
        prefs = getSharedPreferences("GramaYatriPrefs", MODE_PRIVATE)

        // Bind Views
        layoutRegister = findViewById(R.id.layoutRegister)
        layoutLogin = findViewById(R.id.layoutLogin)
        layoutForgotPass = findViewById(R.id.layoutForgotPass)
        layoutMainApp = findViewById(R.id.layoutMainApp)
        viewRoute = findViewById(R.id.viewRoute)
        viewPing = findViewById(R.id.viewPing)
        viewAlerts = findViewById(R.id.viewAlerts)
        progressBar = findViewById(R.id.progressBar)
        btnProfile = findViewById(R.id.btnProfile)
        tvDate = findViewById(R.id.tvDate)
        tvTime = findViewById(R.id.tvTime)
        llStops = findViewById(R.id.llStops)
        llAlerts = findViewById(R.id.llAlerts)
        cbAlertEnabled = findViewById(R.id.cbAlertEnabled)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupAuthLogic()
        setupProfileLogic()
        setupTabNavigation()
        createNotificationChannel()
        requestNotificationPermission()
        startClock()

        cbAlertEnabled.setOnCheckedChangeListener { _, isChecked ->
            isAlertEnabled = isChecked
        }

        // Session Check
        val savedName = prefs.getString("user_name", null)
        if (savedName != null) {
            currentUserName = savedName
            currentDb = rootDb.child("route_tarikere_cmg")
            seedDatabaseIfEmpty()
            initDashboard()
            detectCurrentStop() // Auto-detect if permission exists
            navigateTo("DASHBOARD")
        } else {
            navigateTo("REGISTER")
        }
    }

    private fun seedDatabaseIfEmpty() {
        currentDb.get().addOnSuccessListener { snapshot ->
            val route = snapshot.getValue(Route::class.java)
            if (route == null) {
                val stops = listOf(
                    BusStop("Tarikere", 13.7126, 75.8143, 10),
                    BusStop("galihalli", 13.6986, 75.8123, 10),
                    BusStop("Nandi Hosahalli", 13.6846, 75.8103, 10),
                    BusStop("Kenchapura gate", 13.6706, 75.8083, 15),
                    BusStop("Lingadahaalli(Hosur)", 13.6566, 75.8063, 10),
                    BusStop("Udeva", 13.6426, 75.8043, 10),
                    BusStop("Beetekhan Estate", 13.6286, 75.8023, 10),
                    BusStop("Boothappana gudi", 13.6146, 75.8003, 10),
                    BusStop("Hanumasagar", 13.6006, 75.7983, 12),
                    BusStop("Santaveri", 13.5866, 75.7963, 10),
                    BusStop("Siddarabylu", 13.5726, 75.7943, 10),
                    BusStop("Kumarakattigere", 13.5586, 75.7923, 10),
                    BusStop("Attigiri", 13.5446, 75.7903, 10),
                    BusStop("Shanitipura", 13.5306, 75.7883, 10),
                    BusStop("Hospet", 13.5166, 75.7863, 10),
                    BusStop("Galipooje", 13.5026, 75.7843, 10),
                    BusStop("Kumaragiri", 13.4886, 75.7823, 10),
                    BusStop("Mallenahalli", 13.4746, 75.7803, 10),
                    BusStop("Hebballi", 13.4606, 75.7783, 10),
                    BusStop("Chinnamakki", 13.4466, 75.7763, 10),
                    BusStop("Arasinaguppe", 13.4326, 75.7743, 10),
                    BusStop("Yarehalli", 13.4186, 75.7723, 10),
                    BusStop("Dasarahalli", 13.4046, 75.7703, 10),
                    BusStop("Mavinahalli", 13.3906, 75.7683, 10),
                    BusStop("Kaimara", 13.3766, 75.7663, 15),
                    BusStop("Aralaguppe", 13.3626, 75.7643, 10),
                    BusStop("Allampura", 13.3486, 75.7623, 10),
                    BusStop("Halenahalli", 13.3346, 75.7603, 10),
                    BusStop("Chikkamagaluru", 13.3153, 75.7754, 0)
                )
                currentDb.setValue(Route("Tarikere to Chikkamagaluru", stops))
            }
        }
    }

    private fun setupAuthLogic() {
        findViewById<TextView>(R.id.btnGoToLogin).setOnClickListener { navigateTo("LOGIN") }
        findViewById<TextView>(R.id.btnGoToRegister).setOnClickListener { navigateTo("REGISTER") }
        findViewById<TextView>(R.id.tvForgotPass).setOnClickListener { navigateTo("FORGOT") }
        findViewById<TextView>(R.id.btnBackToLogin).setOnClickListener { navigateTo("LOGIN") }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            val name = findViewById<EditText>(R.id.etRegName).text.toString()
            val email = findViewById<EditText>(R.id.etRegEmail).text.toString()
            val pass = findViewById<EditText>(R.id.etRegPass).text.toString()
            val confirm = findViewById<EditText>(R.id.etRegConfirmPass).text.toString()

            if (name.isNotEmpty() && email.isNotEmpty() && pass.isNotEmpty()) {
                if (pass != confirm) {
                    Toast.makeText(this, R.string.error_passwords_mismatch, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                progressBar.visibility = View.VISIBLE
                val userId = email.replace(".", "_")
                usersDb.child(userId).setValue(mapOf("name" to name, "email" to email, "password" to pass, "points" to 0))
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        prefs.edit {
                            putString("user_name", name)
                            putString("user_email", email)
                        }
                        currentUserName = name
                        currentDb = rootDb.child("route_tarikere_cmg")
                        seedDatabaseIfEmpty()
                        initDashboard()
                        navigateTo("DASHBOARD")
                    }
            }
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val email = findViewById<EditText>(R.id.etLogEmail).text.toString()
            val pass = findViewById<EditText>(R.id.etLogPass).text.toString()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                val userId = email.replace(".", "_")
                usersDb.child(userId).get().addOnSuccessListener { snapshot ->
                    progressBar.visibility = View.GONE
                    val userData = snapshot.value as? Map<*, *>
                    if (userData != null && userData["password"] == pass) {
                        val name = userData["name"].toString()
                        val email = userData["email"].toString()
                        prefs.edit {
                            putString("user_name", name)
                            putString("user_email", email)
                        }
                        currentUserName = name
                        currentDb = rootDb.child("route_tarikere_cmg")
                        seedDatabaseIfEmpty()
                        initDashboard()
                        navigateTo("DASHBOARD")
                    } else {
                        Toast.makeText(this, R.string.error_invalid_credentials, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnResetPass).setOnClickListener {
            val email = findViewById<EditText>(R.id.etForgotEmail).text.toString()
            val newPass = findViewById<EditText>(R.id.etNewPass).text.toString()
            val confirm = findViewById<EditText>(R.id.etForgotConfirmPass).text.toString()

            if (email.isNotEmpty() && newPass.isNotEmpty()) {
                if (newPass != confirm) {
                    Toast.makeText(this, R.string.error_passwords_mismatch, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                progressBar.visibility = View.VISIBLE
                val userId = email.replace(".", "_")
                usersDb.child(userId).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        usersDb.child(userId).child("password").setValue(newPass).addOnSuccessListener {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, R.string.msg_password_updated, Toast.LENGTH_SHORT).show()
                            navigateTo("LOGIN")
                        }
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, R.string.error_email_not_found, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupTabNavigation() {
        val btnRoute = findViewById<LinearLayout>(R.id.btnTabRoute)
        val btnPing = findViewById<LinearLayout>(R.id.btnTabPing)
        val btnAlerts = findViewById<LinearLayout>(R.id.btnTabAlerts)

        btnRoute.setOnClickListener { switchTab(0) }
        btnPing.setOnClickListener { switchTab(1) }
        btnAlerts.setOnClickListener { switchTab(2) }
    }

    private fun switchTab(index: Int) {
        viewRoute.visibility = if (index == 0) View.VISIBLE else View.GONE
        viewPing.visibility = if (index == 1) View.VISIBLE else View.GONE
        viewAlerts.visibility = if (index == 2) View.VISIBLE else View.GONE

        // Color update for active tab icons/text (Oceanic Theme)
        val activeColor = resources.getColor(R.color.primary, theme)
        val inactiveColor = resources.getColor(R.color.text_sub, theme)

        findViewById<ImageView>(R.id.ivTabRoute).imageTintList = ColorStateList.valueOf(if (index == 0) activeColor else inactiveColor)
        findViewById<TextView>(R.id.tvTabRoute).setTextColor(if (index == 0) activeColor else inactiveColor)
        findViewById<TextView>(R.id.tvTabRoute).typeface = if (index == 0) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT

        findViewById<ImageView>(R.id.ivTabPing).imageTintList = ColorStateList.valueOf(if (index == 1) activeColor else inactiveColor)
        findViewById<TextView>(R.id.tvTabPing).setTextColor(if (index == 1) activeColor else inactiveColor)
        findViewById<TextView>(R.id.tvTabPing).typeface = if (index == 1) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT

        findViewById<ImageView>(R.id.ivTabAlerts).imageTintList = ColorStateList.valueOf(if (index == 2) activeColor else inactiveColor)
        findViewById<TextView>(R.id.tvTabAlerts).setTextColor(if (index == 2) activeColor else inactiveColor)
        findViewById<TextView>(R.id.tvTabAlerts).typeface = if (index == 2) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    private fun initDashboard() {
        findViewById<TextView>(R.id.tvWelcome).text = getString(R.string.welcome_user, currentUserName)

        findViewById<Button>(R.id.btnOnBoardNow).setOnClickListener {
            val name = findViewById<EditText>(R.id.etPingName).text.toString()
            val stopIdx = findViewById<Spinner>(R.id.spinnerPingStop).selectedItemPosition
            if (name.isNotEmpty()) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        val lat = location?.latitude ?: 0.0
                        val lng = location?.longitude ?: 0.0
                        incrementUserPoints { points ->
                            currentDb.child("active_bus").setValue(ActiveBus(stopIdx, name, System.currentTimeMillis(), true, points, lat, lng))
                            switchTab(0)
                        }
                    }
                } else {
                    incrementUserPoints { points ->
                        currentDb.child("active_bus").setValue(ActiveBus(stopIdx, name, System.currentTimeMillis(), true, points))
                        switchTab(0)
                    }
                }
            } else {
                Toast.makeText(this, R.string.hint_your_name, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnJustPassed).setOnClickListener {
            val name = findViewById<EditText>(R.id.etPingName).text.toString()
            val stopIdx = findViewById<Spinner>(R.id.spinnerPingStop).selectedItemPosition
            if (name.isNotEmpty()) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        val lat = location?.latitude ?: 0.0
                        val lng = location?.longitude ?: 0.0
                        incrementUserPoints { points ->
                            currentDb.child("active_bus").setValue(ActiveBus(stopIdx, name, System.currentTimeMillis(), false, points, lat, lng))
                            switchTab(0)
                        }
                    }
                } else {
                    incrementUserPoints { points ->
                        currentDb.child("active_bus").setValue(ActiveBus(stopIdx, name, System.currentTimeMillis(), false, points))
                        switchTab(0)
                    }
                }
            } else {
                Toast.makeText(this, R.string.hint_your_name, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnCanceled).setOnClickListener {
            val name = findViewById<EditText>(R.id.etPingName).text.toString()
            if (name.isNotEmpty()) {
                incrementUserPoints { points ->
                    val alert = UserAlert("CANCELED", "The bus is reported canceled today", name, System.currentTimeMillis(), points)
                    currentDb.child("alerts").push().setValue(alert)
                    Toast.makeText(this, R.string.cancellation_reported_thanks, Toast.LENGTH_LONG).show()
                    switchTab(2)
                }
            } else {
                Toast.makeText(this, R.string.hint_your_name, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnLate).setOnClickListener {
            val name = findViewById<EditText>(R.id.etPingName).text.toString()
            if (name.isNotEmpty()) {
                showLateTimePickerDialog(name)
            } else {
                Toast.makeText(this, R.string.hint_your_name, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnFull).setOnClickListener {
            val name = findViewById<EditText>(R.id.etPingName).text.toString()
            if (name.isNotEmpty()) {
                incrementUserPoints { points ->
                    val alert = UserAlert("FULL", getString(R.string.alert_bus_full), name, System.currentTimeMillis(), points)
                    currentDb.child("alerts").push().setValue(alert)
                    Toast.makeText(this, R.string.rush_status_reported, Toast.LENGTH_SHORT).show()
                    switchTab(2)
                }
            } else {
                Toast.makeText(this, R.string.hint_your_name, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnUseCurrentLoc).setOnClickListener {
            detectCurrentStop()
        }

        findViewById<Button>(R.id.btnTrackMap).setOnClickListener {
            openGoogleMaps()
        }

        findViewById<Button>(R.id.btnViewTimetable).setOnClickListener {
            showFullTimetableDialog()
        }

        routeListener?.let { currentDb.removeEventListener(it) }
        routeListener = object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                val route = snapshot.getValue(Route::class.java) ?: return
                findViewById<TextView>(R.id.tvRouteName).text = getString(R.string.route_title)
                findViewById<TextView>(R.id.tvLiveRouteHeader).text = getString(R.string.live_route_banner, route.name)

                currentStopIndex = route.activeBus?.lastStopIndex ?: -1

                if (currentStopIndex != -1 && currentStopIndex != lastNotifiedStopIndex) {
                    // Alert for Tarikere (index 0) departure
                    if (currentStopIndex == 0) {
                        val currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                        sendTarikereDepartureNotification(currentTime)
                    }

                    val nextIdx = currentStopIndex + 1
                    if (nextIdx < route.stops.size) {
                        sendNextStopNotification(route.stops[nextIdx].name)
                    }
                    lastNotifiedStopIndex = currentStopIndex
                }

                stopsList.clear()
                stopsList.addAll(route.stops)
                updateStopsUI()
                updateAlertsUI(route.alerts.values.toList())

                val stopNames = route.stops.map { it.name }
                val spinnerPing = findViewById<Spinner>(R.id.spinnerPingStop)
                val spinnerDest = findViewById<Spinner>(R.id.spinnerDestStop)

                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, stopNames)
                spinnerPing.adapter = adapter
                spinnerDest.adapter = adapter

                if (myDestinationIndex >= stopNames.size) {
                    myDestinationIndex = stopNames.size - 1
                }
                spinnerDest.setSelection(myDestinationIndex.coerceAtLeast(0))

                spinnerDest.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (myDestinationIndex != position) {
                            myDestinationIndex = position
                            alertShown = false
                            updateStopsUI()
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }

                lastActiveBus = route.activeBus

                val lastUpdate = route.activeBus?.timestamp ?: 0L
                val diffMins = if (lastUpdate > 0) (System.currentTimeMillis() - lastUpdate) / 60000 else -1
                val timeAgo = when {
                    diffMins < 0L -> ""
                    diffMins < 1L -> getString(R.string.just_now)
                    else -> getString(R.string.mins_ago, diffMins.toInt())
                }

                val currentLocStr = if (currentStopIndex != -1 && currentStopIndex < route.stops.size) {
                    "${route.stops[currentStopIndex].name} — $timeAgo"
                } else {
                    "--"
                }
                findViewById<TextView>(R.id.tvCurrentLoc).text = currentLocStr

                val nextStopIndex = currentStopIndex + 1
                if (nextStopIndex >= 0 && nextStopIndex < route.stops.size) {
                    val nextStop = route.stops[nextStopIndex]
                    findViewById<TextView>(R.id.tvNextStopInfo).text = getString(R.string.next_stop_info, nextStop.name, nextStop.timeToNext)
                } else {
                    findViewById<TextView>(R.id.tvNextStopInfo).text = getString(R.string.no_next_stop)
                }

                findViewById<TextView>(R.id.tvRouteSub).text = getString(R.string.route_sub_text)

                val tvEtaNum = findViewById<TextView>(R.id.tvEtaNum)
                val tvEtaLabel = findViewById<TextView>(R.id.tvEtaLabel)

                if (currentStopIndex != -1 && currentStopIndex < myDestinationIndex) {
                    var totalEta = 0
                    for (i in currentStopIndex until myDestinationIndex) {
                        if (i < stopsList.size) totalEta += stopsList[i].timeToNext
                    }
                    tvEtaNum.text = getString(R.string.eta_val_format, totalEta)
                    tvEtaLabel.text = getString(R.string.min_to_your_stop)

                    // GenAI Insights
                    val prediction = getGenAIPrediction(totalEta)
                    findViewById<TextView>(R.id.tvRecentPingInfo).text = getString(R.string.genai_prediction, prediction)

                    if (isAlertEnabled && totalEta <= 10 && !alertShown) {
                        alertShown = true
                        val destName = route.stops[myDestinationIndex].name
                        sendArrivalNotification(destName, totalEta)
                        androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle(R.string.alert_title)
                            .setMessage(getString(R.string.hurry_alert, totalEta))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                } else if (currentStopIndex >= myDestinationIndex && currentStopIndex != -1) {
                    tvEtaNum.text = getString(R.string.here_text)
                    tvEtaLabel.text = getString(R.string.min_to_your_stop)
                    alertShown = false
                } else {
                    val nextMins = getNextScheduledBusMins()
                    if (nextMins != -1) {
                        tvEtaNum.text = nextMins.toString()
                        tvEtaLabel.text = getString(R.string.min_to_scheduled_bus)
                    } else {
                        tvEtaNum.text = getString(R.string.placeholder_dash)
                        tvEtaLabel.text = getString(R.string.next_scheduled_bus)
                    }
                    alertShown = false
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        currentDb.addValueEventListener(routeListener!!)
    }

    private fun navigateTo(page: String) {
        layoutRegister.visibility = View.GONE
        layoutLogin.visibility = View.GONE
        layoutForgotPass.visibility = View.GONE
        layoutMainApp.visibility = View.GONE
        btnProfile.visibility = View.GONE

        when (page) {
            "REGISTER" -> layoutRegister.visibility = View.VISIBLE
            "LOGIN" -> layoutLogin.visibility = View.VISIBLE
            "FORGOT" -> layoutForgotPass.visibility = View.VISIBLE
            "DASHBOARD" -> {
                layoutMainApp.visibility = View.VISIBLE
                btnProfile.visibility = View.VISIBLE
                switchTab(0)
            }
        }
    }

    private fun getGenAIPrediction(totalEta: Int): String {
        // Simulation of GenAI logic based on hour of day and cumulative pings
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val currentTimeInMins = hour * 60 + minute

        // Morning Rush: 8:00 AM to 9:15 AM
        val morningStart = 8 * 60
        val morningEnd = 9 * 60 + 15

        // Afternoon Rush: 1:30 PM to 3:00 PM
        val afternoonStart = 13 * 60 + 30
        val afternoonEnd = 15 * 60

        // Evening Rush: 4:00 PM to 5:30 PM
        val eveningStart = 16 * 60
        val eveningEnd = 17 * 60 + 30

        return when {
            currentTimeInMins in morningStart..morningEnd -> {
                getString(R.string.genai_student_rush)
            }
            currentTimeInMins in afternoonStart..afternoonEnd -> {
                getString(R.string.genai_afternoon_rush)
            }
            currentTimeInMins in eveningStart..eveningEnd -> {
                getString(R.string.genai_evening_rush)
            }
            totalEta > 60 -> {
                getString(R.string.traffic_normal)
            }
            else -> {
                val saving = (totalEta * 0.1).toInt().coerceAtLeast(1)
                getString(R.string.traffic_light, saving)
            }
        }
    }

    private fun updateStopsUI() {
        llStops.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (i in stopsList.indices) {
            val stop = stopsList[i]
            val view = inflater.inflate(R.layout.item_stop_card, llStops, false)

            val dot = view.findViewById<View>(R.id.stopDot)
            val homeIcon = view.findViewById<ImageView>(R.id.ivHomeIcon)
            val name = view.findViewById<TextView>(R.id.tvStopName)
            val status = view.findViewById<TextView>(R.id.tvStopStatus)
            val eta = view.findViewById<TextView>(R.id.tvStopEta)
            val card = view.findViewById<LinearLayout>(R.id.stopCard)
            val details = view.findViewById<TextView>(R.id.tvStopDetails)
            val lineTop = view.findViewById<View>(R.id.lineTop)
            val lineBottom = view.findViewById<View>(R.id.lineBottom)

            name.text = stop.name
            lineTop.visibility = if (i == 0) View.INVISIBLE else View.VISIBLE
            lineBottom.visibility = if (i == stopsList.size - 1) View.INVISIBLE else View.VISIBLE

            val isDest = (i == myDestinationIndex)
            val isUserLoc = (i == userNearestStopIndex)

            homeIcon.visibility = if (isDest) View.VISIBLE else View.GONE
            dot.visibility = if (isDest) View.GONE else View.VISIBLE

            if (isDest || isUserLoc) {
                card.setBackgroundTintList(ColorStateList.valueOf(resources.getColor(R.color.highlight, theme)))
            } else {
                card.setBackgroundTintList(null)
            }

            when {
                i < currentStopIndex -> {
                    dot.setBackgroundResource(R.drawable.dot_passed)
                    status.text = if (isUserLoc) "${getString(R.string.near_you)} · ${getString(R.string.status_passed)}" else getString(R.string.status_passed)
                    status.setTextColor(Color.GRAY)
                    eta.visibility = View.GONE
                    details.visibility = View.GONE
                }
                i == currentStopIndex -> {
                    dot.setBackgroundResource(R.drawable.dot_current)
                    status.text = if (isUserLoc) "${getString(R.string.near_you)} · ${getString(R.string.status_here_now)}" else getString(R.string.status_here_now)
                    status.setTextColor(resources.getColor(R.color.status_green, theme))
                    card.setBackgroundTintList(ColorStateList.valueOf(resources.getColor(R.color.highlight, theme)))
                    eta.visibility = View.GONE

                    val lastUpdate = lastActiveBus?.timestamp ?: 0L
                    val reporter = lastActiveBus?.reportedBy ?: "System"
                    val diffMins = if (lastUpdate > 0) (System.currentTimeMillis() - lastUpdate) / 60000 else -1
                    val timeAgo = when {
                        diffMins < 0L -> ""
                        diffMins < 1L -> getString(R.string.just_now)
                        else -> getString(R.string.mins_ago, diffMins.toInt())
                    }

                    if (diffMins > 1) {
                        status.text = if (isUserLoc) "${getString(R.string.near_you)} · ${getString(R.string.status_just_passed)}" else getString(R.string.status_just_passed)
                        status.setTextColor(resources.getColor(R.color.status_red, theme))
                    } else {
                        status.text = if (isUserLoc) "${getString(R.string.near_you)} · ${getString(R.string.status_here_now)}" else getString(R.string.status_here_now)
                        status.setTextColor(resources.getColor(R.color.status_green, theme))
                    }
                    val badge = getReputationBadge(lastActiveBus?.reporterPoints ?: 0)
                    details.text = getString(R.string.reported_with_badge, badge, reporter, timeAgo)
                    details.visibility = View.VISIBLE
                }
                else -> {
                    dot.setBackgroundResource(R.drawable.dot_upcoming)
                    status.text = if (isUserLoc) getString(R.string.near_you) else ""
                    if (isUserLoc) status.setTextColor(resources.getColor(R.color.primary, theme))

                    details.visibility = View.GONE

                    if (currentStopIndex != -1) {
                        var cumEta = 0
                        for (j in currentStopIndex until i) {
                            if (j < stopsList.size) cumEta += stopsList[j].timeToNext
                        }
                        eta.text = getString(R.string.eta_min_format, cumEta)
                        eta.visibility = View.VISIBLE
                    } else {
                        eta.visibility = View.GONE
                    }
                }
            }
            llStops.addView(view)
        }
    }

    private fun updateAlertsUI(alerts: List<UserAlert>) {
        llAlerts.removeAllViews()

        if (alerts.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.setText(R.string.no_active_alerts)
            emptyText.setPadding(16, 16, 16, 16)
            emptyText.setTextColor(Color.GRAY)
            llAlerts.addView(emptyText)
            return
        }

        val sortedAlerts = alerts.sortedByDescending { it.timestamp }

        for (alert in sortedAlerts) {
            val card = CardView(this)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 24)
            card.layoutParams = params
            card.radius = 24f
            card.cardElevation = 4f

            val isCanceled = alert.type == "CANCELED"
            card.setCardBackgroundColor(if (isCanceled) resources.getColor(R.color.accent_light, theme) else resources.getColor(R.color.highlight, theme))

            val content = LinearLayout(this)
            content.orientation = LinearLayout.VERTICAL
            content.setPadding(32, 32, 32, 32)

            val title = TextView(this)
            title.text = if (isCanceled) getString(R.string.alert_format_canceled, alert.message) else getString(R.string.alert_format_info, alert.message)
            title.typeface = android.graphics.Typeface.DEFAULT_BOLD
            title.setTextColor(if (isCanceled) resources.getColor(R.color.status_red, theme) else resources.getColor(R.color.status_blue, theme))

            val reporter = TextView(this)
            val timeAgo = (System.currentTimeMillis() - alert.timestamp) / 60000
            val badge = getReputationBadge(alert.reporterPoints)
            reporter.text = if (timeAgo < 1) {
                getString(R.string.reporter_format_now, badge, alert.reportedBy)
            } else {
                getString(R.string.reporter_format_mins, badge, alert.reportedBy, timeAgo.toInt())
            }
            reporter.textSize = 12f
            reporter.setTextColor(resources.getColor(R.color.text_sub, theme))

            content.addView(title)
            content.addView(reporter)
            card.addView(content)
            llAlerts.addView(card)
        }
    }

    private fun showLateTimePickerDialog(userName: String) {
        val options = arrayOf(
            getString(R.string.mins_late, 10),
            getString(R.string.mins_late, 20),
            getString(R.string.mins_late, 30),
            getString(R.string.mins_late, 45),
            getString(R.string.sixty_plus_mins_late)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.how_late_title)
            .setItems(options) { _, which ->
                val delay = options[which]
                incrementUserPoints { points ->
                    val alert = UserAlert("LATE", "Bus is running $delay", userName, System.currentTimeMillis(), points)
                    currentDb.child("alerts").push().setValue(alert)
                    Toast.makeText(this, R.string.delay_reported_thanks, Toast.LENGTH_SHORT).show()
                    switchTab(2)
                }
            }
            .show()
    }

    private fun getReputationBadge(points: Int): String {
        return when {
            points >= 100 -> "🏆"
            points >= 50 -> "⭐"
            points >= 20 -> "🏅"
            else -> "👤"
        }
    }

    private fun incrementUserPoints(callback: (Int) -> Unit) {
        val userId = prefs.getString("user_email", "anonymous")?.replace(".", "_") ?: "anonymous"
        val userRef = usersDb.child(userId).child("points")
        userRef.get().addOnSuccessListener { snapshot ->
            val currentPoints = (snapshot.value as? Long)?.toInt() ?: 0
            val newPoints = currentPoints + 10
            userRef.setValue(newPoints).addOnSuccessListener {
                callback(newPoints)
            }
        }.addOnFailureListener {
            callback(0)
        }
    }

    @SuppressLint("MissingPermission")
    private fun detectCurrentStop() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 102)
            return
        }

        Toast.makeText(this, R.string.detecting_location, Toast.LENGTH_SHORT).show()
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                var closestStopIdx = -1
                var minDistance = Double.MAX_VALUE

                for (i in stopsList.indices) {
                    val stop = stopsList[i]
                    val dist = calculateDistance(location.latitude, location.longitude, stop.lat, stop.lng)
                    if (dist < minDistance) {
                        minDistance = dist
                        closestStopIdx = i
                    }
                }

                if (closestStopIdx != -1 && minDistance < 5000) { // Within 5km
                    userNearestStopIndex = closestStopIdx
                    findViewById<Spinner>(R.id.spinnerPingStop).setSelection(closestStopIdx)
                    updateStopsUI()
                    Toast.makeText(this, getString(R.string.stop_detected, stopsList[closestStopIdx].name), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.no_stop_near, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openGoogleMaps() {
        val busLat = lastActiveBus?.lat ?: 0.0
        val busLng = lastActiveBus?.lng ?: 0.0

        val uri = if (busLat != 0.0) {
            "google.navigation:q=$busLat,$busLng"
        } else {
            "google.navigation:q=Chikkamagaluru,Karnataka"
        }

        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            val webUri = if (busLat != 0.0) "https://www.google.com/maps/dir/?api=1&destination=$busLat,$busLng" else "https://www.google.com/maps/dir/Tarikere/Chikkamagaluru"
            val webIntent = Intent(Intent.ACTION_VIEW, webUri.toUri())
            startActivity(webIntent)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = lat1 * PI / 180
        val phi2 = lat2 * PI / 180
        val deltaPhi = (lat2 - lat1) * PI / 180
        val deltaLambda = (lon2 - lon1) * PI / 180
        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun setupProfileLogic() {
        btnProfile.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("User: $currentUserName").isEnabled = false
            popup.menu.add("Logout")
            popup.setOnMenuItemClickListener { item ->
                if (item.title == "Logout") {
                    prefs.edit { clear() }
                    navigateTo("LOGIN")
                }
                true
            }
            popup.show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Bus Arrival Alerts"
            val descriptionText = "Notifications for when the bus is near your destination"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("BUS_ALERTS", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendArrivalNotification(stopName: String, minutes: Int) {
        val builder = NotificationCompat.Builder(this, "BUS_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.alert_title))
            .setContentText(getString(R.string.alert_msg, stopName, minutes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNextStopNotification(nextStopName: String) {
        val builder = NotificationCompat.Builder(this, "BUS_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.next_stop_title, nextStopName))
            .setContentText(getString(R.string.next_stop_msg, nextStopName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(2, builder.build())
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendTarikereDepartureNotification(time: String) {
        val builder = NotificationCompat.Builder(this, "BUS_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.tarikere_departure_title))
            .setContentText(getString(R.string.tarikere_departure_msg, time))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(3, builder.build())
        }
    }

    private fun startClock() {
        clockHandler.post(clockRunnable)
    }

    private fun updateClock() {
        val sdfDate = java.text.SimpleDateFormat("EEE, dd MMM", java.util.Locale.getDefault())
        val sdfTime = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
        val now = java.util.Date()
        tvDate.text = sdfDate.format(now)
        tvTime.text = sdfTime.format(now)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        routeListener?.let { currentDb.removeEventListener(it) }
    }
}