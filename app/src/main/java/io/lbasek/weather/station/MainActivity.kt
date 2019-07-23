package io.lbasek.weather.station

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*


class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "BLE_EXAMPLE"
        const val SCAN_PERIOD = 10000

        val SENSOR_SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val TEMPERATURE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
        val HUMIDITY_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a6f-0000-1000-8000-00805f9b34fb")
        val SENSOR_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var temperatureCharacteristic: BluetoothGattCharacteristic? = null
    private var humidityCharacteristic: BluetoothGattCharacteristic? = null
    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createSet(chartTemperature, "Temperature", "#173F5F")
        createSet(chartHumidity, "Humidity", "#3CAEA3")

        scan_button.setOnClickListener {
            bluetoothAdapter?.bluetoothLeScanner?.startScan(
                mutableListOf(
                    ScanFilter.Builder()
                        .setDeviceName("CC50E380BDA6")
//                        .setDeviceAddress("CC:50:E3:80:BD:A6")
                        .build()
                ),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
                scannerCallback
            )
            Handler().postDelayed({
                stopScan()
            }, SCAN_PERIOD.toLong())
        }

        switch_temp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enableNotification(temperatureCharacteristic)
            } else {
                disableNotification(temperatureCharacteristic)
            }
        }
        switch_humidity.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enableNotification(humidityCharacteristic)
            } else {
                disableNotification(humidityCharacteristic)
            }
        }

        connect_button.setOnClickListener {
            if (connect_button.text.toString() == "Disconnect") {
                gatt?.disconnect()
                connect_button.text = "Connect"
                connect_button.isEnabled = false
                switch_humidity.isChecked = false
                switch_temp.isChecked = false
            } else {
                device?.connectGatt(applicationContext, false, gattCallback)
            }

        }
    }

    private fun enableNotification(characteristic: BluetoothGattCharacteristic?) {
        characteristic?.getDescriptor(SENSOR_DESCRIPTOR_UUID)?.run {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(this)
            gatt?.setCharacteristicNotification(characteristic, true)
        }
    }

    private fun disableNotification(characteristic: BluetoothGattCharacteristic?) {
        characteristic?.getDescriptor(SENSOR_DESCRIPTOR_UUID)?.run {
            value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(this)
            gatt?.setCharacteristicNotification(characteristic, false)

        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val scannerCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            Log.i("TAG", result?.device?.address + result?.scanRecord!!.bytes.toString())
            if (result.device?.name == device_name.text.toString()) {
                connect_button.isEnabled = true
//                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                Log.i(TAG, "BLE Device: " + result.device?.run { "$name $address" })
                Toast.makeText(this@MainActivity, "Device Found", Toast.LENGTH_SHORT).show()
                this@MainActivity.device = result.device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Toast.makeText(
                this@MainActivity, when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature not supported"
                    SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                    else -> "Unknown error"
                }, Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scannerCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.e(TAG, "STATE_CONNECTED")
                    connect_button.text = "Disconnect"
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.e(TAG, "STATE_DISCONNECTED")
                    this@MainActivity.gatt?.close()
                    this@MainActivity.gatt = null
                }
                else -> Log.e(TAG, "STATE UNKNOWN")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.i(TAG, "onServicesDiscovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                this@MainActivity.gatt = gatt
                for (service in gatt!!.services) {
                    if (service.uuid == SENSOR_SERVICE_UUID) {
                        temperatureCharacteristic = service.getCharacteristic(TEMPERATURE_CHARACTERISTIC_UUID)
                        humidityCharacteristic = service.getCharacteristic(HUMIDITY_CHARACTERISTIC_UUID)
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            Log.d(TAG, "onDescriptorWrite")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            Log.d(TAG, "onCharacteristicChanged")
            val timestamp = System.currentTimeMillis() / 1000
            when (characteristic?.uuid) {
                TEMPERATURE_CHARACTERISTIC_UUID -> {
                    val temperature = ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN).float
                    Log.d(TAG, "Temperature: %.2f".format(temperature))
                    temperature_text.text = "%.2f".format(temperature)
                    addEntry(chartTemperature, timestamp, temperature)
                }
                HUMIDITY_CHARACTERISTIC_UUID -> {
                    val humidity = ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN).float
                    Log.d(TAG, "Humidity: %.2f".format(humidity))
                    humidity_text.text = "%.2f".format(humidity)
                    addEntry(chartHumidity, timestamp, humidity)
                }
            }

        }

    }

    private fun addEntry(chart: LineChart, timestamp: Long, value: Float) {

        val data = chart.data
        val set: ILineDataSet? = data!!.getDataSetByIndex(0)
        data.addEntry(Entry(set!!.entryCount.toFloat(), value), 0)
        data.notifyDataChanged()

        // let the chart know it's data has changed
        chart.notifyDataSetChanged()

        // limit the number of visible entries
        chart.setVisibleXRangeMaximum(120f)
        // chart.setVisibleYRange(30, AxisDependency.LEFT);

        // move to the latest entry
        chart.moveViewToX(set.entryCount.toFloat())

        // this automatically refreshes the chart (calls invalidate())
        // chart.moveViewTo(data.getXValCount()-7, 55f,
        // AxisDependency.LEFT);
    }

    private fun createSet(chart: LineChart, title: String, fillColorHex: String) {

        chart.setBackgroundColor(Color.WHITE)
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.description.isEnabled = false

        val set = LineDataSet(null, title)
        set.color = Color.parseColor(fillColorHex)
        set.lineWidth = 2f
        set.setDrawCircles(false)
        set.fillAlpha = 200
        set.mode = LineDataSet.Mode.CUBIC_BEZIER
        set.fillColor = Color.parseColor(fillColorHex)
        set.valueTextSize = 9f
        set.setDrawValues(false)
        set.setDrawCircleHole(false)
        set.setDrawFilled(true)

        chart.data = LineData(set)
    }

}

