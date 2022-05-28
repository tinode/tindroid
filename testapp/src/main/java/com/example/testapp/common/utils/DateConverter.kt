package com.example.testapp.common.utils

//import org.threeten.bp.Instant
//import org.threeten.bp.LocalDateTime
//import org.threeten.bp.LocalTime
//import org.threeten.bp.ZoneId
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

internal object DateConverter {

    fun toLocalDateTime(date: Date): LocalDateTime {
        return Instant.ofEpochMilli(date.time)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }

    fun toLocalTime(date: Date): LocalTime {
        return Instant.ofEpochMilli(date.time)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
    }

    fun toDate(localDateTime: LocalDateTime): Date {
        return Date(
            localDateTime.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
    }
}
