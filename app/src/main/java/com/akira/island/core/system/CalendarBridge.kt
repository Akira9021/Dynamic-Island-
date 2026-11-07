package com.akira.island.core.system

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract

/**
 * Event read from the calendar: identifier, title, start and end.
 */
data class CalendarEvent(
    val id: Long,
    val title: String?,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * Bridge to the system Calendar: read upcoming events and create new ones.
 * Reads require the runtime READ_CALENDAR permission; creation goes through the
 * public CalendarContract provider intent (without WRITE_CALENDAR).
 */
interface CalendarBridge {
    fun canReadCalendar(context: Context): Boolean
    fun readNextEvents(context: Context, fromMillis: Long, windowMillis: Long): List<CalendarEvent>
    fun createEvent(context: Context, title: String?, startMillis: Long, endMillis: Long): Boolean
}

class SystemCalendarBridge(private val context: Context) : CalendarBridge {
    override fun canReadCalendar(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun readNextEvents(context: Context, fromMillis: Long, windowMillis: Long): List<CalendarEvent> {
        val resolver = context.contentResolver
        val uri = CalendarContract.Instances.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_ID,
        )
        val selection = "${CalendarContract.Instances.BEGIN} >= ? AND ${CalendarContract.Instances.END} >= ?"
        val selectionArgs = arrayOf(fromMillis.toString(), fromMillis.toString())
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
        val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder, null) ?: return emptyList()
        val out = mutableListOf<CalendarEvent>()
        try {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID))
                val start = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
                val end = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END))
                out.add(CalendarEvent(id, null, start, end))
            }
        } finally {
            cursor.close()
        }
        return out
    }

    override fun createEvent(context: Context, title: String?, startMillis: Long, endMillis: Long): Boolean {
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title ?: "")
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
        }
        val uri: Uri? = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri != null && !uri.toString().isNullOrEmpty()
    }
}
