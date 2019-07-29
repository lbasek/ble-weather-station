package io.lbasek.weather.station

import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

class CustomValueFormatter : ValueFormatter() {

    override fun getPointLabel(entry: Entry?): String {
        return entry?.y.toString()
    }

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        val timestamp = System.currentTimeMillis()
        val formatter = SimpleDateFormat("HH:mm:ss")
        return formatter.format(Date(timestamp))
    }
}