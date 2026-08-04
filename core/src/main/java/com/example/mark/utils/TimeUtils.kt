package com.example.mark.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toChatTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))