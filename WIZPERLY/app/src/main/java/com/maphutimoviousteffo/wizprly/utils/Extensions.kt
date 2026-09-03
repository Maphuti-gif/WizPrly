package com.maphutimoviousteffo.wizprly.utils

import java.text.SimpleDateFormat
import java.util.*

fun Long.formatTime(): String {
    val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return format.format(Date(this))
}

fun Long.formatDate(): String {
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return format.format(Date(this))
}