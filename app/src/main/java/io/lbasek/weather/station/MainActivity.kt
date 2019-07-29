package io.lbasek.weather.station

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.jakewharton.rx.ReplayingShare
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*


class MainActivity : AppCompatActivity() {

    companion object {
        const val DEVICE_ADDRESS = "CC:50:E3:80:BD:A6"

        val TEMPERATURE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
        val HUMIDITY_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a6f-0000-1000-8000-00805f9b34fb")
        val temperatureGraphColor = Color.parseColor("#173F5F")
        val humidityGraphColor = Color.parseColor("#3CAEA3")
    }

    private val compositeDisposable = CompositeDisposable()

    private var disposable: Disposable? = null

    private lateinit var bleDevice: RxBleDevice

    private lateinit var rxBleClient: RxBleClient

    private lateinit var connectionObservable: Observable<RxBleConnection>

    private val disconnectTriggerSubject = PublishSubject.create<Unit>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createSet(chartTemperature, resources.getString(R.string.temperature), temperatureGraphColor)
        createSet(chartHumidity, resources.getString(R.string.humidity), humidityGraphColor)

        rxBleClient = RxBleClient.create(this)
    }

    override fun onResume() {
        super.onResume()

        bleDevice = rxBleClient.getBleDevice(DEVICE_ADDRESS)
        connectionObservable = prepareConnectionObservable()
        status.text = getString(R.string.status_string_format).format(bleDevice.connectionState.name)

        bleDevice.observeConnectionStateChanges()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                if (it == RxBleConnection.RxBleConnectionState.CONNECTED) {
                    connect_button.text = resources.getString(R.string.disconnect)
                } else {
                    connect_button.text = resources.getString(R.string.connect)
                }

                status.text = getString(R.string.status_string_format).format(bleDevice.connectionState.name)
            }
            .subscribe({}, { Timber.e(it) })
            .let { compositeDisposable.add(it) }

        rxBleClient.observeStateChanges()
            .subscribeOn(Schedulers.io())
            .startWith(rxBleClient.state)
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                ble_status.text = """BLE Environment Status: ${it.name}"""
                when (it) {
                    RxBleClient.State.READY -> {
                        connect_button.isEnabled = true
                    }
                    RxBleClient.State.BLUETOOTH_NOT_ENABLED -> {
                        connect_button.isEnabled = false
                    }
                    RxBleClient.State.BLUETOOTH_NOT_AVAILABLE -> {
                        connect_button.isEnabled = false
                    }
                    RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED -> {
                        connect_button.isEnabled = false
                    }
                    RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED -> {
                        connect_button.isEnabled = false
                    }
                }
            }
            .subscribe({}, { Timber.e(it) })
            .let { compositeDisposable.add(it) }


        connect_button.setOnClickListener {
            if (bleDevice.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) {
                triggerDisconnect()
            } else {
                disposable?.dispose()
                disposable = connectionObservable
                    .flatMap { it.setupNotification(TEMPERATURE_CHARACTERISTIC_UUID) }
                    .flatMap { it }
                    .observeOn(AndroidSchedulers.mainThread())
                    .map {
                        val temperature = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).float
                        Timber.d("Temperature: %.2f".format(temperature))
                        temperature_text.text = getString(R.string.temperature_string_format).format(temperature)
                        addEntry(chartTemperature, temperature)
                    }
                    .flatMap { connectionObservable }
                    .flatMap { it.setupNotification(HUMIDITY_CHARACTERISTIC_UUID) }
                    .flatMap { it }
                    .observeOn(AndroidSchedulers.mainThread())
                    .map {
                        val humidity = ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).float
                        Timber.d("Humidity: %.2f".format(humidity))
                        humidity_text.text = getString(R.string.humidity_string_format).format(humidity)
                        addEntry(chartHumidity, humidity)
                    }
                    .subscribe({}, {
                        Toast.makeText(applicationContext, it.localizedMessage, Toast.LENGTH_SHORT).show()
                    })
            }
        }
    }

    private fun triggerDisconnect() = disconnectTriggerSubject.onNext(Unit)

    private fun prepareConnectionObservable(): Observable<RxBleConnection> = bleDevice
        .establishConnection(false)
        .takeUntil(disconnectTriggerSubject)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .compose(ReplayingShare.instance())
        .doOnError { Toast.makeText(applicationContext, it.localizedMessage, Toast.LENGTH_SHORT).show() }

    private fun addEntry(chart: LineChart, value: Float) {
        val data = chart.data
        val set: ILineDataSet = data.getDataSetByIndex(0)
        data.addEntry(Entry(set.entryCount.toFloat(), value), 0)
        data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(120f)
        chart.moveViewToX(set.entryCount.toFloat())
    }

    override fun onPause() {
        super.onPause()
        compositeDisposable.clear()
    }

    private fun createSet(chart: LineChart, title: String, @ColorInt color: Int) {
        chart.setBackgroundColor(Color.WHITE)
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.description.isEnabled = false
        chart.xAxis.valueFormatter = CustomValueFormatter()

        val set = LineDataSet(null, title)
        set.color = color
        set.lineWidth = 4f
        set.setDrawCircles(false)
        set.fillAlpha = 50
        set.mode = LineDataSet.Mode.CUBIC_BEZIER
        set.fillColor = color
        set.valueTextSize = 9f
        set.setDrawValues(false)
        set.setDrawCircleHole(false)
        set.setDrawFilled(true)

        chart.data = LineData(set)
    }

}

