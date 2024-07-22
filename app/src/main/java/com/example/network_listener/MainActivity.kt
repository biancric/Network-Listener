package com.example.network_listener

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.*
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.example.network_listener.ui.cells.CellInfoViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // Variables for telephony and handlers
    private lateinit var telephonyManager: TelephonyManager
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L // 1 second interval for updates
    private val cellInfoViewModel: CellInfoViewModel by viewModels()
    private val cellsDictionary = mutableMapOf<String, MutableMap<String, String>>() // Dictionary to track cells
    private val firstSeenDictionary = mutableMapOf<String, String>() // Dictionary to track first seen timestamps
    private val lastSeenDictionary = mutableMapOf<String, String>() // Dictionary to track last seen timestamps

    // Variables for the easter egg
    private var clickCount = 0
    private val clickThreshold = 5
    private val clickTimeout = 1000L // 1 second
    private val handler2 = Handler(Looper.getMainLooper())
    private val resetClickCountRunnable = Runnable {
        clickCount = 0
    }

    // Permission request codes
    private val permissionRequestCode = 1
    private val backgroundLocationRequestCode = 2

    private lateinit var locationSettingsLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Load saved theme
        loadThemeFromPreferences()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createLog() // Create log file

        // Initialize TelephonyManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        // Set up BottomNavigationView and NavController
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_log, R.id.navigation_notifications)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Add an OnDestinationChangedListener to handle clicks on the "Settings" button
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_notifications) {
                clickCount++
                handler2.removeCallbacks(resetClickCountRunnable)
                handler2.postDelayed(resetClickCountRunnable, clickTimeout)

                if (clickCount == clickThreshold) {
                    easterEgg()
                    clickCount = 0
                }
            }
        }

        // Initialize the location settings launcher
        locationSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                checkPermissionsAndStartUpdatingCellInfo()
            } else {
                showLocationServicesDialog()
            }
        }

        // Check if location services are enabled and request permissions if necessary
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // Proceed with checking permissions and requesting cell info
            checkPermissionsAndStartUpdatingCellInfo()
        } else {
            // Prompt the user to enable location services
            showLocationServicesDialog()
        }

        // Apply always on display setting
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val alwaysOnDisplay = sharedPreferences.getBoolean("always_on_display", false)
        setDisplayOn(alwaysOnDisplay)
    }

    // Load the selected theme from preferences
    private fun loadThemeFromPreferences() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        when (sharedPreferences.getString("theme", "system")) {
            "light" -> setTheme(R.style.Theme_Light)
            "dark" -> setTheme(R.style.Theme_Dark)
            "system" -> setTheme(R.style.Theme_System)
        }
    }

    // Set the display to always on if selected in preferences
    fun setDisplayOn(alwaysOn: Boolean) {
        if (alwaysOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Check permissions and start updating cell info if granted
    private fun checkPermissionsAndStartUpdatingCellInfo() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        if (!hasPermissions(this, permissions.toTypedArray())) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), permissionRequestCode)
        } else {
            startUpdatingCellInfo()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationPermission()
            }
        }
    }

    // Helper function to check if the required permissions are granted
    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    // Request background location permission for Android Q and above
    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), backgroundLocationRequestCode)
        }
    }

    // Show a dialog to prompt the user to enable location services
    private fun showLocationServicesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Location Services")
            .setMessage("Location services are required to get cell information. Please enable them in settings.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                locationSettingsLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Handle the result of permission requests
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            permissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startUpdatingCellInfo()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        requestBackgroundLocationPermission()
                    }
                }
            }
            backgroundLocationRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Background location permission granted
                    startUpdatingCellInfo()
                }
            }
        }
    }

    // Start the periodic updates for cell information
    private fun startUpdatingCellInfo() {
        handler.post(updateCellInfoRunnable)
    }

    // Runnable to get cell info periodically
    private val updateCellInfoRunnable = object : Runnable {
        override fun run() {
            getCellInfo()
            handler.postDelayed(this, updateInterval)
        }
    }

    @SuppressLint("NewApi")
    private fun getCellInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            telephonyManager.requestCellInfoUpdate(Executors.newSingleThreadExecutor(), object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfoList: MutableList<CellInfo>) {
                    val cellInfoModels = mutableListOf<CellInfoModel>()

                    for (cellInfo in cellInfoList) {
                        when (cellInfo) {
                            is CellInfoLte -> {
                                // Handle LTE cell info
                                val cellIdentity = cellInfo.cellIdentity
                                val cellSignalStrength = cellInfo.cellSignalStrength

                                val cellId = if (cellIdentity.ci != Int.MAX_VALUE) cellIdentity.ci else -1
                                val tac = if (cellIdentity.tac != Int.MAX_VALUE) cellIdentity.tac else -1
                                val mcc = cellIdentity.mccString ?: "-1"
                                val mnc = cellIdentity.mncString ?: "-1"
                                val earfcn = cellIdentity.earfcn
                                val bandwidth = if (cellIdentity.bandwidth != Int.MAX_VALUE) cellIdentity.bandwidth else -1
                                val signalStrength = cellSignalStrength.dbm
                                val pci = if (cellIdentity.pci != Int.MAX_VALUE) cellIdentity.pci else -1
                                val cellInfoModel = CellInfoModel(
                                    type = "LTE",
                                    cellId = cellId.toString(),
                                    pci = pci.toString(),
                                    psc = "N/A",
                                    locationAreaCode = tac.toString(),
                                    mobileCountryCode = mcc,
                                    mobileNetworkCode = mnc,
                                    bandwidth = bandwidth.toString(),
                                    earfcn = earfcn.toString(),
                                    signalStrength = signalStrength.toString(),
                                    operator = getOperatorName(mcc, mnc),
                                    firstSeen = "",
                                    lastSeen = ""
                                )
                                val identifier = generateCellIdentifier(cellInfoModel, "LTE")
                                val firstSeen = getFirstSeenTimestamp(cellId.toString())
                                val lastSeen = getCurrentTimestamp()
                                if (cellsDictionary.containsKey(identifier)) {
                                    cellsDictionary[identifier]?.set("signalStrength", signalStrength.toString())
                                    cellsDictionary[identifier]?.set("lastSeen", lastSeen)
                                    lastSeenDictionary[identifier] = lastSeen
                                    updateLogFile(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                } else {
                                    cellsDictionary[identifier] = mutableMapOf(
                                        "type" to "LTE",
                                        "cellId" to cellId.toString(),
                                        "pci" to pci.toString(),
                                        "tac" to tac.toString(),
                                        "mcc" to mcc,
                                        "mnc" to mnc,
                                        "signalStrength" to signalStrength.toString(),
                                        "operator" to getOperatorName(mcc, mnc),
                                        "earfcn" to earfcn.toString(),
                                        "bandwidth" to bandwidth.toString(),
                                        "firstSeen" to firstSeen,
                                        "lastSeen" to lastSeen
                                    )
                                    firstSeenDictionary[identifier] = firstSeen
                                    lastSeenDictionary[identifier] = lastSeen
                                    cellInfoModels.add(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                    logCellInfo(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                }
                            }
                            is CellInfoGsm -> {
                                // Handle GSM cell info
                                val cellIdentity = cellInfo.cellIdentity
                                val cellSignalStrength = cellInfo.cellSignalStrength

                                val cellId = if (cellIdentity.cid != Int.MAX_VALUE) cellIdentity.cid else -1
                                val lac = if (cellIdentity.lac != Int.MAX_VALUE) cellIdentity.lac else -1
                                val mcc = cellIdentity.mccString ?: "-1"
                                val mnc = cellIdentity.mncString ?: "-1"
                                val signalStrength = cellSignalStrength.dbm
                                val cellInfoModel = CellInfoModel(
                                    type = "GSM",
                                    pci = "N/A",
                                    psc = "N/A",
                                    cellId = cellId.toString(),
                                    locationAreaCode = lac.toString(),
                                    mobileCountryCode = mcc,
                                    mobileNetworkCode = mnc,
                                    bandwidth = "N/A",
                                    earfcn = "N/A",
                                    signalStrength = signalStrength.toString(),
                                    operator = getOperatorName(mcc, mnc),
                                    firstSeen = "",
                                    lastSeen = ""
                                )
                                val identifier = generateCellIdentifier(cellInfoModel, "GSM")
                                val firstSeen = getFirstSeenTimestamp(cellId.toString())
                                val lastSeen = getCurrentTimestamp()
                                if (cellsDictionary.containsKey(identifier)) {
                                    cellsDictionary[identifier]?.set("signalStrength", signalStrength.toString())
                                    cellsDictionary[identifier]?.set("lastSeen", lastSeen)
                                    lastSeenDictionary[identifier] = lastSeen
                                    updateLogFile(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                } else {
                                    cellsDictionary[identifier] = mutableMapOf(
                                        "type" to "GSM",
                                        "cellId" to cellId.toString(),
                                        "lac" to lac.toString(),
                                        "mcc" to mcc,
                                        "mnc" to mnc,
                                        "signalStrength" to signalStrength.toString(),
                                        "operator" to getOperatorName(mcc, mnc),
                                        "firstSeen" to firstSeen,
                                        "lastSeen" to lastSeen
                                    )
                                    firstSeenDictionary[identifier] = firstSeen
                                    lastSeenDictionary[identifier] = lastSeen
                                    cellInfoModels.add(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                    logCellInfo(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                }
                            }
                            is CellInfoCdma -> {
                                // Handle CDMA cell info
                                val cellIdentity = cellInfo.cellIdentity
                                val cellSignalStrength = cellInfo.cellSignalStrength

                                val networkId = if (cellIdentity.networkId != Int.MAX_VALUE) cellIdentity.networkId else -1
                                val systemId = if (cellIdentity.systemId != Int.MAX_VALUE) cellIdentity.systemId else -1
                                val signalStrength = cellSignalStrength.dbm
                                val cellInfoModel = CellInfoModel(
                                    type = "CDMA",
                                    cellId = networkId.toString(),
                                    pci = "N/A",
                                    psc = "N/A",
                                    locationAreaCode = systemId.toString(),
                                    mobileCountryCode = "N/A", // CDMA does not use MCC/MNC
                                    mobileNetworkCode = "N/A",
                                    bandwidth = "N/A",
                                    earfcn = "N/A",
                                    signalStrength = signalStrength.toString(),
                                    operator = "CDMA Operator",
                                    firstSeen = "",
                                    lastSeen = ""
                                )
                                val identifier = generateCellIdentifier(cellInfoModel, "CDMA")
                                val lastSeen = getCurrentTimestamp()
                                val firstSeen = getFirstSeenTimestamp(networkId.toString())
                                if (cellsDictionary.containsKey(identifier)) {
                                    cellsDictionary[identifier]?.set("signalStrength", signalStrength.toString())
                                    cellsDictionary[identifier]?.set("lastSeen", lastSeen)
                                    lastSeenDictionary[identifier] = lastSeen
                                    updateLogFile(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                } else {
                                    cellsDictionary[identifier] = mutableMapOf(
                                        "type" to "CDMA",
                                        "networkId" to networkId.toString(),
                                        "systemId" to systemId.toString(),
                                        "signalStrength" to signalStrength.toString(),
                                        "operator" to "CDMA Operator",
                                        "firstSeen" to firstSeen,
                                        "lastSeen" to lastSeen
                                    )
                                    firstSeenDictionary[identifier] = firstSeen
                                    lastSeenDictionary[identifier] = lastSeen
                                    cellInfoModels.add(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                    logCellInfo(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                }
                            }
                            is CellInfoWcdma -> {
                                // Handle WCDMA cell info
                                val cellIdentity = cellInfo.cellIdentity
                                val cellSignalStrength = cellInfo.cellSignalStrength

                                val cellId = if (cellIdentity.cid != Int.MAX_VALUE) cellIdentity.cid else -1
                                val psc = if (cellIdentity.psc != Int.MAX_VALUE) cellIdentity.psc else -1
                                val lac = if (cellIdentity.lac != Int.MAX_VALUE) cellIdentity.lac else -1
                                val mcc = cellIdentity.mccString ?: "-1"
                                val mnc = cellIdentity.mncString ?: "-1"
                                val signalStrength = cellSignalStrength.dbm
                                val cellInfoModel = CellInfoModel(
                                    type = "WCDMA",
                                    cellId = cellId.toString(),
                                    psc = psc.toString(),
                                    pci = "N/A",
                                    locationAreaCode = lac.toString(),
                                    mobileCountryCode = mcc,
                                    mobileNetworkCode = mnc,
                                    bandwidth = "N/A",
                                    earfcn = "N/A",
                                    signalStrength = signalStrength.toString(),
                                    operator = getOperatorName(mcc, mnc),
                                    firstSeen = "",
                                    lastSeen = ""
                                )
                                val identifier = generateCellIdentifier(cellInfoModel, "WCDMA")
                                val lastSeen = getCurrentTimestamp()
                                val firstSeen = getFirstSeenTimestamp(cellId.toString())
                                if (cellsDictionary.containsKey(identifier)) {
                                    cellsDictionary[identifier]?.set("signalStrength", signalStrength.toString())
                                    cellsDictionary[identifier]?.set("lastSeen", lastSeen)
                                    lastSeenDictionary[identifier] = lastSeen
                                    updateLogFile(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                } else {
                                    cellsDictionary[identifier] = mutableMapOf(
                                        "type" to "WCDMA",
                                        "cellId" to cellId.toString(),
                                        "psc" to psc.toString(),
                                        "lac" to lac.toString(),
                                        "mcc" to mcc,
                                        "mnc" to mnc,
                                        "signalStrength" to signalStrength.toString(),
                                        "operator" to getOperatorName(mcc, mnc),
                                        "firstSeen" to firstSeen,
                                        "lastSeen" to lastSeen
                                    )
                                    firstSeenDictionary[identifier] = firstSeen
                                    lastSeenDictionary[identifier] = lastSeen
                                    cellInfoModels.add(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                    logCellInfo(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                }
                            }
                            is CellInfoNr -> {
                                // Handle NR (5G) cell info
                                val cellIdentity = cellInfo.cellIdentity as CellIdentityNr
                                val cellSignalStrength = cellInfo.cellSignalStrength as CellSignalStrengthNr

                                val nci = if (cellIdentity.nci != Long.MAX_VALUE) cellIdentity.nci else -1L
                                val tac = if (cellIdentity.tac != Int.MAX_VALUE) cellIdentity.tac else -1
                                val mcc = cellIdentity.mccString ?: "-1"
                                val mnc = cellIdentity.mncString ?: "-1"
                                val earfcn = cellIdentity.nrarfcn
                                val signalStrength = cellSignalStrength.dbm
                                val pci = if (cellIdentity.pci != Int.MAX_VALUE) cellIdentity.pci else -1
                                val cellInfoModel = CellInfoModel(
                                    type = "NR",
                                    cellId = nci.toString(),
                                    pci = pci.toString(),
                                    psc = "N/A",
                                    locationAreaCode = tac.toString(),
                                    mobileCountryCode = mcc,
                                    mobileNetworkCode = mnc,
                                    bandwidth = "N/A",
                                    earfcn = earfcn.toString(),
                                    signalStrength = signalStrength.toString(),
                                    operator = getOperatorName(mcc, mnc),
                                    firstSeen = "",
                                    lastSeen = ""
                                )
                                val identifier = generateCellIdentifier(cellInfoModel, "NR")
                                val lastSeen = getCurrentTimestamp()
                                val firstSeen = getFirstSeenTimestamp(nci.toString())
                                if (cellsDictionary.containsKey(identifier)) {
                                    cellsDictionary[identifier]?.set("signalStrength", signalStrength.toString())
                                    cellsDictionary[identifier]?.set("lastSeen", lastSeen)
                                    lastSeenDictionary[identifier] = lastSeen
                                    updateLogFile(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                } else {
                                    cellsDictionary[identifier] = mutableMapOf(
                                        "type" to "NR",
                                        "nci" to nci.toString(),
                                        "pci" to pci.toString(),
                                        "tac" to tac.toString(),
                                        "mcc" to mcc,
                                        "mnc" to mnc,
                                        "signalStrength" to signalStrength.toString(),
                                        "operator" to getOperatorName(mcc, mnc),
                                        "earfcn" to earfcn.toString(),
                                        "firstSeen" to firstSeen,
                                        "lastSeen" to lastSeen
                                    )
                                    firstSeenDictionary[identifier] = firstSeen
                                    lastSeenDictionary[identifier] = lastSeen
                                    cellInfoModels.add(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                    logCellInfo(cellInfoModel.copy(firstSeen = firstSeen, lastSeen = lastSeen))
                                }
                            }
                        }
                    }

                    // Update the view model with the new cell information
                    runOnUiThread {
                        cellInfoViewModel.updateCellInfo(cellInfoModels)
                        cellInfoViewModel.updateCellInfoDictionary(cellsDictionary)
                        cellInfoViewModel.updateFirstSeenDictionary(firstSeenDictionary)
                        cellInfoViewModel.updateLastSeenDictionary(lastSeenDictionary)
                    }
                }

                override fun onError(error: Int, message: Throwable?) {
                    Log.e("MainActivity", "Error getting cell info: $error, Message: ${message?.message}")
                }
            })
        }
    }

    // Get the operator name based on MCC and MNC codes
    private fun getOperatorName(mcc: String, mnc: String): String {
        return when (mcc) {
            "228" -> { // MCC for Switzerland
                when (mnc) {
                    "01" -> "Swisscom"
                    "02", "07", "08", "12", "60" -> "Sunrise"
                    "03" -> "Salt"
                    "05", "09" -> "Comfone"
                    "06" -> "SBB CFF FFS"
                    "54" -> "Lycamobile"
                    else -> "Unknown"
                }
            }
            "208" -> { // MCC for France
                when (mnc) {
                    "01", "02", "91" -> "Orange"
                    "08", "09", "10", "11", "13" -> "SFR"
                    "12" -> "Truphone"
                    "15", "16", "88" -> "Free Mobile"
                    "17" -> "Legos"
                    "20", "21", "35" -> "Bouygues Telecom"
                    "25" -> "Lycamobile"
                    else -> "Unknown"
                }
            }
            "262" -> { // MCC for Germany
                when (mnc) {
                    "01", "06", "13", "78" -> "T-mobile"
                    "02", "04", "09" -> "Vodafone"
                    "10" -> "DB Netz"
                    "12", "22" -> "sipgate"
                    "03", "05", "07", "08", "11", "16", "17", "20" -> "Telefonica / O2"
                    "14" -> "Lebara"
                    "21" -> "Multiconnect"
                    "23" -> "1&1"
                    "24" -> "Telcovillage"
                    "25" -> "Mtel"
                    "42" -> "Truphone"
                    "43" -> "Lycamobile"
                    else -> "Unknown"
                }
            }
            else -> "Unknown"
        }
    }

    // Get the first seen timestamp for a cell ID
    private fun getFirstSeenTimestamp(cellId: String): String {
        val sharedPref = getSharedPreferences("cellInfoPrefs", Context.MODE_PRIVATE)
        val firstSeenKey = "first_seen_$cellId"
        val firstSeen = sharedPref.getString(firstSeenKey, null)
        if (firstSeen == null) {
            val currentTimestamp = getCurrentTimestamp()
            with(sharedPref.edit()) {
                putString(firstSeenKey, currentTimestamp)
                apply()
            }
            return currentTimestamp
        } else {
            return firstSeen
        }
    }

    // Get the current timestamp
    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss 'UTC'XXX", Locale.getDefault())
        return sdf.format(Date())
    }

    // Create a log file for cell information
    private fun createLog() {
        val logDir = File(getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        // Get current date and time
        val dateFormat = SimpleDateFormat("dd.MM.yyyy_HH-mm-ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())

        // Create log file with the current date and time in its name
        val logFileName = "cell_log_$currentTime.csv"
        val logFile = File(logDir, logFileName)

        logFile.createNewFile()

        try {
            FileWriter(logFile, true).use { writer ->
                writer.append("first seen, last seen, type, CID, LAC, MCC, MNC, dBm, Operator, EARFCN, Bandwidth\n")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Log cell information to the log file
    private fun logCellInfo(cellInfo: CellInfoModel) {
        val logDir = File(getExternalFilesDir(null), "logs")
        val logFile = logDir.listFiles()?.maxByOrNull { it.lastModified() }

        try {
            FileWriter(logFile, true).use { writer ->
                writer.append("${cellInfo.firstSeen}, ${cellInfo.lastSeen}, ${cellInfo.type}, ${cellInfo.cellId}, ${cellInfo.locationAreaCode}, ${cellInfo.mobileCountryCode}, ${cellInfo.mobileNetworkCode}, ${cellInfo.signalStrength}, ${cellInfo.operator}, ${cellInfo.earfcn}, ${cellInfo.bandwidth}\n")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Update the log file with new cell information
    private fun updateLogFile(cellInfo: CellInfoModel) {
        val logDir = File(getExternalFilesDir(null), "logs")
        val logFile = logDir.listFiles()?.maxByOrNull { it.lastModified() }

        val lines = logFile?.readLines()?.toMutableList() ?: mutableListOf()

        val updatedLines = mutableListOf<String>()

        for (line in lines) {
            val lineElements = line.split(",").map { it.trim() }
            val lineCellId = lineElements.getOrNull(3)
            val lineType = lineElements.getOrNull(2)
            val lineEarfcn = lineElements.getOrNull(9)

            if (lineCellId == cellInfo.cellId && lineType == cellInfo.type && lineEarfcn == cellInfo.earfcn) {
                updatedLines.add("${cellInfo.firstSeen}, ${cellInfo.lastSeen}, ${cellInfo.type}, ${cellInfo.cellId}, ${cellInfo.locationAreaCode}, ${cellInfo.mobileCountryCode}, ${cellInfo.mobileNetworkCode},${cellInfo.signalStrength}, ${cellInfo.operator}, ${cellInfo.earfcn}, ${cellInfo.bandwidth}\n")
            } else {
                updatedLines.add(line + "\n")
            }
        }

        logFile?.writeText(updatedLines.joinToString(""))
    }

    // Generate a unique identifier for a cell
    private fun generateCellIdentifier(cellInfoModel: CellInfoModel, cellType: String): String {
        if (cellType == "LTE" || cellType == "NR") {  // the identifier will contain the PCI (Physical Identifier)
            return "${cellInfoModel.type}_${cellInfoModel.cellId}_${cellInfoModel.pci}_${cellInfoModel.locationAreaCode}_${cellInfoModel.mobileCountryCode}_${cellInfoModel.mobileNetworkCode}_${cellInfoModel.earfcn}"
        }
        if (cellType == "WCDMA") {  // the identifier will contain the PSC (Primary Scrambling Code)
            return "${cellInfoModel.type}_${cellInfoModel.cellId}_${cellInfoModel.psc}_${cellInfoModel.locationAreaCode}_${cellInfoModel.mobileCountryCode}_${cellInfoModel.mobileNetworkCode}_${cellInfoModel.earfcn}"
        }
        if (cellType == "GSM" || cellType == "CDMA") {  // there is no extra identifier for these cells
            return "${cellInfoModel.type}_${cellInfoModel.cellId}_${cellInfoModel.locationAreaCode}_${cellInfoModel.mobileCountryCode}_${cellInfoModel.mobileNetworkCode}_${cellInfoModel.earfcn}"
        }
        return "error"
    }

    private fun easterEgg() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("force_fullscreen", true)
        startActivity(intent)
    }
}
