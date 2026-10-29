package com.casacheck.app.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(time: Long) = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.forLanguageTag("es-SV")).format(Date(time))
