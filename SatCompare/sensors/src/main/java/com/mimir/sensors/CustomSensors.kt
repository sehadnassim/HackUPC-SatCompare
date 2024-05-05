package com.mimir.sensors

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.*
import android.os.SystemClock
import android.text.BoringLayout
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.*
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import java.net.URL
import java.net.HttpURLConnection
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.Locale
// =================================================================================================
val serverIp = "195.148.31.126"
val serverPort = 5000
val apiUrl = "/api/submit"
val serverUrl = "http://$serverIp:$serverPort$apiUrl"
val mURL = URL(serverUrl)

abstract class CustomSensor(
    _context: Context,
    _fileHandler: FileHandler,
    _type : SensorType,
    _typeTag : String,
    _sampling : Int,
    _mvalues: MutableList<Any>)
    : SensorEventListener {

    var isAvailable : Boolean = false  // Check if sensor available on platform
    var isRegistered : Boolean = false // Check if sensor correctly registered
    var isReceived : Boolean = false   // Check if sensor have sent any event yet
    protected val context  : Context    = _context.applicationContext
    val type     : SensorType = _type
    protected val typeTag  : String     = _typeTag
    protected val sampling : Int        = _sampling

    lateinit var sensor : Sensor
    lateinit var sensorSummary : String
    private var sensorSampling : String = "1"

    val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    protected val fileHandler = _fileHandler

    var mvalues = _mvalues

    // ---------------------------------------------------------------------------------------------

    init {
        checkSensorAvailable()
    }

    // ---------------------------------------------------------------------------------------------

    private fun checkSensorAvailable(){
        val sensorList: List<Sensor> = sensorManager.getSensorList(type.value)

        if (sensorList.isNotEmpty()) {
            isAvailable = true
        } else {
            isAvailable = false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Override methods

    override fun onSensorChanged(event: SensorEvent) {
        logSensor(event)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        return
    }

    // ---------------------------------------------------------------------------------------------
    // Methods

    open fun logSensor(event : SensorEvent){
        isReceived = true
       // Log.d("sensor here -------", "here-------")

      //  Log.d("%s".format(sensor.stringType), event.toString())
        // To be override
    }

    // ---------------------------------------------------------------------------------------------

    open fun registerSensor(){

        if (sensorManager.getDefaultSensor(type.value) != null) {
            sensor = sensorManager.getDefaultSensor(type.value)!!
            sensorManager.registerListener(this, sensor, sampling)
            sensorSampling = sampling.toString()
            sensorSummary = sensor.toString()
            isRegistered = true
            Log.i("Sensor", "$typeTag sensor registered")
        }
        else {
            Log.i("Sensor", "Does not have sensor for %s".format(typeTag))
            isRegistered = false
        }

        // Create file header
        fileHandler.obtainMessage().also { msg ->
            msg.obj = getHeader()
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    open fun unregisterSensor(){
        sensorManager.unregisterListener(this)
        isRegistered = false
    }

    // ---------------------------------------------------------------------------------------------

    open fun getLogLine() : String{
        return ""
    }

    // ---------------------------------------------------------------------------------------------

    open fun getHeader() : String{
        val str : String

        if(!isRegistered){
            str = "# Sensor $typeTag disabled"
        } else{
            str = "# Sensor $typeTag enabled, Sampling Period: $sensorSampling, $sensorSummary\n"
        }

        return str
    }
}

// =================================================================================================

class MotionSensor(
    context: Context,
    _fileHandler: FileHandler,
    _type: SensorType,
    _typeTag: String,
    _samplingFrequency: Int,
    _mvalues: MutableList<Any>)
    : CustomSensor(context, _fileHandler, _type, _typeTag, _samplingFrequency, _mvalues) {

    // ---------------------------------------------------------------------------------------------

    override fun logSensor(event: SensorEvent) {
        super.logSensor(event)

        // Log the values
        mvalues.add(event)
        //Log.d(typeTag, e.toString())

        // Send the values to the file
        fileHandler.obtainMessage().also { msg ->
            msg.obj = getLogLine(event)
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun getLogLine(event : SensorEvent): String {
        return String.format(
            Locale.US,
            "$typeTag,%s,%s,%s,%s,%s,%s",
            System.currentTimeMillis(),
            event.timestamp,
            event.values[0],
            event.values[1],
            event.values[2],
            event.accuracy)
    }

    // ---------------------------------------------------------------------------------------------

    override fun getHeader(): String {
        var str : String =  super.getHeader()

        str += String.format("# ${typeTag},utcTimeMillis,elapsedRealtime_nanosecond,")
        when(type){
            SensorType.TYPE_ACCELEROMETER
            -> str += String.format(
                "%s,%s,%s,%s",
                "x_meterPerSecond2",
                "y_meterPerSecond2",
                "z_meterPerSecond2",
                "accuracy")
            SensorType.TYPE_GYROSCOPE
            ->  str += String.format(
                "%s,%s,%s,%s",
                "x_radPerSecond",
                "y_radPerSecond",
                "z_radPerSecond",
                "accuracy")
            SensorType.TYPE_MAGNETIC_FIELD
            ->  str += String.format(
                "%s,%s,%s,%s",
                "x_microTesla",
                "y_microTesla",
                "z_microTesla",
                "accuracy")
            else -> Log.e("Sensors", "Invalid value $type")
        }
        str += "\n#"
        return str




    }
}

// =================================================================================================

class UncalibratedMotionSensor(
    context: Context,
    _fileHandler: FileHandler,
    _type: SensorType,
    _typeTag: String,
    _samplingFrequency: Int,
    _mvalues: MutableList<Any>)
    : CustomSensor(context, _fileHandler, _type, _typeTag, _samplingFrequency, _mvalues) {

    // ---------------------------------------------------------------------------------------------

    override fun logSensor(event: SensorEvent) {
        super.logSensor(event)

        // Log the values
        mvalues.add(event)
        //Log.d(typeTag, event.toString())

        fileHandler.obtainMessage().also { msg ->
            msg.obj = getLogLine(event)
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun getLogLine(event : SensorEvent): String {
        return String.format(
            Locale.US,
            "$typeTag,%s,%s,%s,%s,%s,%s,%s,%s,%s",
            System.currentTimeMillis(),
            event.timestamp,
            event.values[0],
            event.values[1],
            event.values[2],
            event.values[3],
            event.values[4],
            event.values[5],
            event.accuracy)
    }

    // ---------------------------------------------------------------------------------------------

    override fun getHeader(): String {
        var str : String =  super.getHeader()

        str += String.format("# ${typeTag},utcTimeMillis,elapsedRealtime_nanosecond,")
        when(type){
            SensorType.TYPE_ACCELEROMETER_UNCALIBRATED
            -> str += String.format(
                "%s,%s,%s,%s,%s,%s,%s",
                "x_uncalibrated_meterPerSecond2",
                "y_uncalibrated_meterPerSecond2",
                "z_uncalibrated_meterPerSecond2",
                "x_bias_meterPerSecond2",
                "y_bias_meterPerSecond2",
                "z_bias_meterPerSecond2",
                "accuracy")
            SensorType.TYPE_GYROSCOPE_UNCALIBRATED
            ->  str += String.format(
                "%s,%s,%s,%s,%s,%s,%s",
                "x_uncalibrated_radPerSecond",
                "y_uncalibrated_radPerSecond",
                "z_uncalibrated_radPerSecond",
                "x_bias_radPerSecond",
                "y_bias_radPerSecond",
                "z_bias_radPerSecond",
                "accuracy")
            SensorType.TYPE_MAGNETIC_FIELD_UNCALIBRATED
            ->  str += String.format(
                "%s,%s,%s,%s,%s,%s,%s",
                "x_uncalibrated_microTesla",
                "y_uncalibrated_microTesla",
                "z_uncalibrated_microTesla",
                "x_bias_microTesla",
                "y_bias_microTesla",
                "z_bias_microTesla",
                "accuracy")
            else -> Log.e("Sensors", "Invalid value $type")
        }
        str += "\n#"

        return str
    }
}

// =================================================================================================

class EnvironmentSensor(
    context: Context,
    _fileHandler: FileHandler,
    _type: SensorType,
    _typeTag: String,
    _samplingFrequency: Int,
    _mvalues: MutableList<Any>)
    : CustomSensor(context, _fileHandler, _type, _typeTag, _samplingFrequency, _mvalues) {

    // ---------------------------------------------------------------------------------------------

    override fun logSensor(event: SensorEvent) {
        super.logSensor(event)

        // Log the values
        mvalues.add(event)
        //Log.d(typeTag, event.toString())

        fileHandler.obtainMessage().also { msg ->
            msg.obj = getLogLine(event)

            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun getLogLine(event : SensorEvent): String {
        return String.format(
            Locale.US,
            "$typeTag,%s,%s,%s,%s",
            System.currentTimeMillis(),
            event.timestamp,
            event.values[0],
            event.accuracy)
    }

    // ---------------------------------------------------------------------------------------------

    override fun getHeader(): String {
        var str : String =  super.getHeader()

        str += String.format("# ${typeTag},utcTimeMillis,elapsedRealtime_nanosecond,")
        when(type){
            SensorType.TYPE_PRESSURE
            -> str += String.format(
                "%s,%s",
                "pressure_hPa",
                "accuracy")
            else -> Log.e("Sensors", "Invalid value $type")
        }
        str += "\n#"

        return str
    }
}

// =================================================================================================

class GnssLocationSensor(
    context: Context,
    _fileHandler: FileHandler,
    _mvalues: MutableList<Any>,
    _provider: String)
    : CustomSensor(context, _fileHandler, SensorType.TYPE_GNSS_LOCATION, "Fix", 1000, _mvalues) {

    private var mLocationManager : LocationManager = context.getSystemService(Activity.LOCATION_SERVICE) as LocationManager
    private lateinit var mLocationListener : LocationListener

    private val _provider = _provider

    init {

    }

    // ---------------------------------------------------------------------------------------------

    override fun registerSensor() {

        mLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                logSensor(location)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            mLocationManager.requestLocationUpdates(
                _provider,
                1000,
                0.0F,
                mLocationListener,
                null
            );
            sensorSummary = "{Receiver: ${mLocationManager.gnssHardwareModelName}}"
            isRegistered = true
            Log.i("Sensor", "GNSS Location sensor registered")
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Create file header
        fileHandler.obtainMessage().also { msg ->
            msg.obj = getHeader()
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun unregisterSensor() {
        mLocationManager.removeUpdates(mLocationListener)
    }

    // ---------------------------------------------------------------------------------------------

    fun logSensor(location: Location) {

        isReceived = true

        // Log the values
        mvalues.add(location)
        //Log.d(typeTag, event.toString())

        fileHandler.obtainMessage().also { msg ->
            msg.obj = getLogLine(location)
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun getLogLine(location: Location): String {
        // Based on GNSS logger app logging
        val logLine = String.format(
            Locale.US,
            "$typeTag,%s,%.8f,%.8f,%.3f,%.3f,%.3f,%f,%d,%f,%f,%d,%f,%f",
            location.provider.toString().uppercase(),
            location.latitude,
            location.longitude,
            location.altitude,
            location.speed,
            location.accuracy,
            location.bearing,
            location.time,
            location.speedAccuracyMetersPerSecond,
            location.bearingAccuracyDegrees,
            location.elapsedRealtimeNanos,
            location.verticalAccuracyMeters,
            location.elapsedRealtimeUncertaintyNanos
        )

        val latitudeLog = String.format(Locale.US, "%.8f", location.latitude)
        val longitudeLog = String.format(Locale.US, "%.8f", location.longitude)

        Log.i("LocationInfo", "Latitude: $latitudeLog")
        var reqParam =URLEncoder.encode("latitude", "UTF-8") + "=" + URLEncoder.encode(latitudeLog, "UTF-8")
        reqParam += "&" + URLEncoder.encode("longitude", "UTF-8") + "=" + URLEncoder.encode(longitudeLog, "UTF-8")

        Log.i("RequestData", "Request Parameters: $reqParam")

        // Assuming mURL is properly initialized
        with(mURL.openConnection() as HttpURLConnection) {
            // Set connection and read timeouts
            connectTimeout = 5000 // 5 seconds
            readTimeout = 10000 // 10 seconds

            // Set other properties and send data
            requestMethod = "POST"
            // Set the Content-Type header
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            // Send data
            outputStream.use { stream ->
                stream.write(reqParam.toByteArray(Charsets.UTF_8))
            }

            // Log request details
            Log.i("Request", url.toString())
            Log.i("Response", responseCode.toString())

            disconnect() // Disconnect the connection after use
        }
        return logLine
    }

//--------------------




    //------------------

    // ---------------------------------------------------------------------------------------------

    override fun getHeader(): String {

        var str : String =  super.getHeader()

        str += String.format(
            "# $typeTag,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
            "Provider",
            "Latitude_decimalDegree",
            "Longitude_decimalDegree",
            "Altitude_meter",
            "Speed_meterPerSecond",
            "Accuracy_meter",
            "Bearing_degree",
            "UnixTime_millisecond",
            "SpeedAccuracy_meterPerSecond",
            "BearingAccuracy_degree",
            "ElapsedRealtime_nanosecond",
            "VerticalAccuracy_meter",
            "ElapsedRealtimeUncertainty_nanosecond")
        str += "\n#"

        return str
    }
}

// =================================================================================================



// =================================================================================================

class GnssMeasurementSensor(
    context: Context,
    _fileHandler: FileHandler,
    _mvalues: MutableList<Any>)
    : CustomSensor(context, _fileHandler, SensorType.TYPE_GNSS_MEASUREMENTS, "Raw", 1000, _mvalues) {

    private var mLocationManager : LocationManager = context.getSystemService(Activity.LOCATION_SERVICE) as LocationManager
    private lateinit var mGnssMeasurementsEventCallback : GnssMeasurementsEvent.Callback



    // ---------------------------------------------------------------------------------------------

    override fun registerSensor() {
        mGnssMeasurementsEventCallback = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                super.onGnssMeasurementsReceived(event)
                logSensor(event)
            }
        }

        // Register callback
        try {
            mGnssMeasurementsEventCallback.let {
                mLocationManager.registerGnssMeasurementsCallback(context.mainExecutor, it)
            }
            sensorSummary = "{Receiver: ${mLocationManager.gnssHardwareModelName}}"
            isRegistered = true
            Log.i("Sensor", "GNSS Measurement sensor registered")
        }
        catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Create file header
        fileHandler.obtainMessage().also { msg ->
            msg.obj = getHeader()
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun unregisterSensor() {
        mLocationManager.unregisterGnssMeasurementsCallback(mGnssMeasurementsEventCallback)
    }

    // ---------------------------------------------------------------------------------------------

    fun logSensor(event: GnssMeasurementsEvent) {

        isReceived = true

        // Log the values
        mvalues.add(event)
        //Log.d(typeTag, event.toString())

        event.measurements.forEach {
            fileHandler.obtainMessage().also { msg ->
                msg.obj = getLogLine(event.clock, it)
                fileHandler.sendMessage(msg)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun getLogLine(gnssClock: GnssClock, measurement : GnssMeasurement): String {
        return String.format(
            Locale.US,
            "$typeTag,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
            System.currentTimeMillis(),
            gnssClock.timeNanos,
            if (gnssClock.hasLeapSecond()) gnssClock.leapSecond else "",
            if (gnssClock.hasTimeUncertaintyNanos()) gnssClock.timeUncertaintyNanos else "",
            gnssClock.fullBiasNanos,
            if (gnssClock.hasBiasNanos()) gnssClock.biasNanos else "",
            if (gnssClock.hasBiasUncertaintyNanos()) gnssClock.biasUncertaintyNanos else "",
            if (gnssClock.hasDriftNanosPerSecond()) gnssClock.driftNanosPerSecond else "",
            if (gnssClock.hasDriftUncertaintyNanosPerSecond()) gnssClock.driftUncertaintyNanosPerSecond else "",
            gnssClock.hardwareClockDiscontinuityCount.toString(),
            measurement.svid,
            measurement.timeOffsetNanos,
            measurement.state,
            measurement.receivedSvTimeNanos,
            measurement.receivedSvTimeUncertaintyNanos,
            measurement.cn0DbHz,
            measurement.pseudorangeRateMetersPerSecond,
            measurement.pseudorangeRateUncertaintyMetersPerSecond,
            measurement.accumulatedDeltaRangeState,
            measurement.accumulatedDeltaRangeMeters,
            measurement.accumulatedDeltaRangeUncertaintyMeters,
            if (measurement.hasCarrierFrequencyHz()) measurement.carrierFrequencyHz else "",
            if (measurement.hasCarrierCycles()) measurement.carrierCycles else "",
            if (measurement.hasCarrierPhase()) measurement.carrierPhase else "",
            if (measurement.hasCarrierPhaseUncertainty()) measurement.carrierPhaseUncertainty else "",
            measurement.multipathIndicator,
            if (measurement.hasSnrInDb()) measurement.snrInDb else "",
            measurement.constellationType,
            if (measurement.hasAutomaticGainControlLevelDb()) measurement.automaticGainControlLevelDb else "",
            if (measurement.hasBasebandCn0DbHz()) measurement.basebandCn0DbHz else "",
            if (measurement.hasFullInterSignalBiasNanos()) measurement.fullInterSignalBiasNanos else "",
            if (measurement.hasFullInterSignalBiasUncertaintyNanos()) measurement.fullInterSignalBiasUncertaintyNanos else "",
            if (measurement.hasSatelliteInterSignalBiasNanos()) measurement.satelliteInterSignalBiasNanos else "",
            if (measurement.hasSatelliteInterSignalBiasUncertaintyNanos()) measurement.satelliteInterSignalBiasUncertaintyNanos else "",
            if (measurement.hasCodeType()) measurement.codeType else "",
            if (gnssClock.hasElapsedRealtimeNanos()) gnssClock.elapsedRealtimeNanos else ""
        )
    }

    // ---------------------------------------------------------------------------------------------

    override fun getHeader(): String {
        var str : String =  super.getHeader()

        str += String.format(
            "# $typeTag,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
            "utcTimeMillis",
            "TimeNanos",
            "LeapSecond",
            "TimeUncertaintyNanos",
            "FullBiasNanos",
            "BiasNanos",
            "BiasUncertaintyNanos",
            "DriftNanosPerSecond",
            "DriftUncertaintyNanosPerSecond",
            "HardwareClockDiscontinuityCount",
            "Svid",
            "TimeOffsetNanos",
            "State",
            "ReceivedSvTimeNanos",
            "ReceivedSvTimeUncertaintyNanos",
            "Cn0DbHz",
            "PseudorangeRateMetersPerSecond",
            "PseudorangeRateUncertaintyMetersPerSecond",
            "AccumulatedDeltaRangeState",
            "AccumulatedDeltaRangeMeters",
            "AccumulatedDeltaRangeUncertaintyMeters",
            "CarrierFrequencyHz",
            "CarrierCycles",
            "CarrierPhase",
            "CarrierPhaseUncertainty",
            "MultipathIndicator",
            "SnrInDb",
            "ConstellationType",
            "AgcDb",
            "BasebandCn0DbHz",
            "FullInterSignalBiasNanos",
            "FullInterSignalBiasUncertaintyNanos",
            "SatelliteInterSignalBiasNanos",
            "SatelliteInterSignalBiasUncertaintyNanos",
            "CodeType",
            "ChipsetElapsedRealtimeNanos")
        str += "\n#"

        return str
    }
}

// =================================================================================================

class GnssNavigationMessageSensor(
    context: Context,
    _fileHandler: FileHandler,
    _mvalues: MutableList<Any>)
    : CustomSensor(context, _fileHandler, SensorType.TYPE_GNSS_MESSAGES, "Nav", 0, _mvalues) {

    private var mLocationManager : LocationManager = context.getSystemService(Activity.LOCATION_SERVICE) as LocationManager
    private lateinit var mGnssNavigationMessageCallback : GnssNavigationMessage.Callback


    init {

    }

    // ---------------------------------------------------------------------------------------------

    override fun registerSensor() {
        mGnssNavigationMessageCallback = object : GnssNavigationMessage.Callback(){
            override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
                super.onGnssNavigationMessageReceived(event)
                logSensor(event)
            }
        }

        // Register callback
        try {
            mGnssNavigationMessageCallback.let {
                mLocationManager.registerGnssNavigationMessageCallback(context.mainExecutor, it)
            }
            sensorSummary = "{Receiver: ${mLocationManager.gnssHardwareModelName}}"
            isRegistered = true
            Log.i("Sensor", "GNSS Navigation Message sensor registered")
        }
        catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Create file header
        fileHandler.obtainMessage().also { msg ->
            msg.obj = getHeader()
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun unregisterSensor() {
        mLocationManager.unregisterGnssNavigationMessageCallback(mGnssNavigationMessageCallback)
    }

    // ---------------------------------------------------------------------------------------------

    fun logSensor(event : GnssNavigationMessage){

        isReceived = true

        // Log the values
        mvalues.add(event)
        //Log.d(typeTag, event.toString())

        fileHandler.obtainMessage().also { msg ->
            msg.obj = getLogLine(event)
            fileHandler.sendMessage(msg)
            //Log.d(typeTag, msg.obj.toString())
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun getLogLine(event : GnssNavigationMessage): String {
        return String.format(
            Locale.US,
            "$typeTag,%s,%s,%s,%s,%s,%s,%s",
            System.currentTimeMillis(),
            event.svid,
            event.type,
            event.status,
            event.messageId,
            event.submessageId,
            event.data.joinToString(separator = ","))
    }

    // ---------------------------------------------------------------------------------------------

    override fun getHeader(): String {

        var str : String =  super.getHeader()

        str += String.format(
            "# $typeTag,%s,%s,%s,%s,%s,%s,%s",
            "utcTimeMillis",
            "Svid",
            "Type",
            "Status",
            "MessageId",
            "Sub-messageId",
            "Data(Bytes)")
        str += "\n#"

        return str
    }
}

// =================================================================================================

class BluetoothSensor(
    context: Context,
    _fileHandler: FileHandler,
    _mvalues: MutableList<Any>)
    : CustomSensor(context, _fileHandler, SensorType.TYPE_BLUETOOTH, "BLE", 0, _mvalues) {

    private var mBluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var mBluetoothLeScanner: BluetoothLeScanner = mBluetoothAdapter.bluetoothLeScanner
    private lateinit var mBluetoothScanCallback: ScanCallback

    init {

    }

    // ---------------------------------------------------------------------------------------------

    override fun registerSensor() {
        mBluetoothScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                    logSensor(result)
                }
            }

        // Start
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mBluetoothLeScanner.startScan(mBluetoothScanCallback)
        Log.i("Sensor", "Bluetooth sensor registered")
    }

    // ---------------------------------------------------------------------------------------------

    override fun unregisterSensor() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mBluetoothLeScanner.stopScan(mBluetoothScanCallback)
    }

    // ---------------------------------------------------------------------------------------------

    fun logSensor(scan: ScanResult) {

        // Log the values
        mvalues.add(scan)
        //Log.d(typeTag, event.toString())

        fileHandler.obtainMessage().also { msg ->
            msg.obj = getLogLine(scan)
            fileHandler.sendMessage(msg)
            Log.d(typeTag, msg.obj.toString())
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun getLogLine(scan: ScanResult) : String{

        return String.format(
            Locale.US,
            "$typeTag,%s,%s,%s,%s,%s,%s,%s,%s",
            SystemClock.currentGnssTimeClock().millis(),
            scan.timestampNanos,
            scan.device,
            scan.rssi,
            scan.advertisingSid,
            scan.txPower,
            scan.dataStatus,
            scan.scanRecord?.bytes.contentToString())
    }
}

// =================================================================================================

class HeartRateSensor(
    context: Context,
    _fileHandler: FileHandler,
    _type: SensorType,
    _typeTag: String,
    _samplingFrequency: Int,
    _mvalues: MutableList<Any>)
    : CustomSensor(context, _fileHandler, _type, _typeTag, 1000, _mvalues) {

    // ---------------------------------------------------------------------------------------------

    override fun logSensor(event: SensorEvent) {
        super.logSensor(event)

        // Log the values
        mvalues.add(event)
        //Log.d(typeTag, event.toString())

        fileHandler.obtainMessage().also { msg ->
            msg.obj = getLogLine(event)
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun getLogLine(event : SensorEvent): String {
        return String.format(
            Locale.US,
            "$typeTag,%s,%s,%s,%s",
            System.currentTimeMillis(),
            event.timestamp,
            event.values[0],
            event.accuracy)
    }

    // ---------------------------------------------------------------------------------------------

    override fun getHeader(): String {
        var str : String =  super.getHeader()

        str += String.format("# ${typeTag},utcTimeMillis,elapsedRealtime_nanosecond,")
        when(type){
            SensorType.TYPE_HEART_RATE
            -> str += String.format(
                "%s,%s",
                "rate_beatPerSecond",
                "accuracy")
            else -> Log.e("Sensors", "Invalid value $type")
        }
        str += "\n#"

        return str
    }
}

// =================================================================================================

class SpecificSensor(
    context: Context,
    _fileHandler: FileHandler,
    _name: String,
    _type: SensorType,
    _typeTag: String,
    _samplingFrequency: Int,
    _mvalues: MutableList<Any>)
    : CustomSensor(context, _fileHandler, _type, _typeTag, _samplingFrequency, _mvalues){

    private val name : String = _name

    // ---------------------------------------------------------------------------------------------

    override fun registerSensor() {
        val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)

        // Loop through the devices sensors to find the right one
        // TODO Maybe there is a better way to request a specific sensor by name?
        deviceSensors.forEach  {
            if (it.name == name) {
                sensor = it
                sensorManager.registerListener(this, sensor, sampling)
                //sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST
                isRegistered = true
            }
        }

        if(isRegistered){
            sensorSummary = sensor.toString()
            Log.i("Sensor", "$typeTag sensor registered")

            // Create file header
            fileHandler.obtainMessage().also { msg ->
                msg.obj = getHeader()
                fileHandler.sendMessage(msg)
            }
        }
        else {
            Log.i("Sensor", "Does not have sensor for %s".format(typeTag))
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun logSensor(event: SensorEvent) {
        super.logSensor(event)

        // Log the values
        mvalues.add(event)
        // Log.d(typeTag, event.toString())

        fileHandler.obtainMessage().also { msg ->
            msg.obj = getLogLine(event)
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun getLogLine(event : SensorEvent): String {
        return String.format(
            Locale.US,
            "$typeTag,%s,%s,%s,%s",
            System.currentTimeMillis(),
            event.timestamp,
            event.accuracy,
            event.values.joinToString(separator = ","))
    }

    // ---------------------------------------------------------------------------------------------

    override fun getHeader(): String {
        var str : String =  super.getHeader()

        str += String.format("# ${typeTag},utcTimeMillis,elapsedRealtime_nanosecond,")
        str += String.format(
                "%s,%s",
                "accuracy",
                "values")
        str += "\n#"

        return str
    }
}

// =================================================================================================

class StepSensor(
    context: Context,
    _fileHandler: FileHandler,
    _type: SensorType,
    _typeTag: String,
    _samplingFrequency: Int,
    _mvalues: MutableList<Any>)
    : CustomSensor(context, _fileHandler, _type, _typeTag, _samplingFrequency, _mvalues) {

    // ---------------------------------------------------------------------------------------------

    override fun logSensor(event: SensorEvent) {
        super.logSensor(event)

        // Log the values
        mvalues.add(event)
        Log.d(typeTag, event.toString())

        fileHandler.obtainMessage().also { msg ->
            msg.obj = getLogLine(event)
            fileHandler.sendMessage(msg)
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun getLogLine(event : SensorEvent): String {
        return String.format(
            Locale.US,
            "$typeTag,%s,%s,%s,%s",
            System.currentTimeMillis(),
            event.timestamp,
            event.values[0],
            event.accuracy)
    }

    // ---------------------------------------------------------------------------------------------

    override fun getHeader(): String {
        var str : String =  super.getHeader()

        str += String.format("# ${typeTag},utcTimeMillis,elapsedRealtime_nanosecond,")
        when(type){
            SensorType.TYPE_STEP_COUNTER
            -> str += String.format(
                "%s,%s",
                "steps_count",
                "accuracy")
            SensorType.TYPE_STEP_DETECTOR
            -> str += String.format(
                "%s,%s",
                "step_detected",
                "accuracy")
            else -> Log.e("Sensors", "Invalid value $type")
        }
        str += "\n#"

        return str
    }
}

// =================================================================================================

















