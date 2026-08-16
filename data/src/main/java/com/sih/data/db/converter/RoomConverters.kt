package com.sih.data.db.converter

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Room TypeConverters for non-primitive types stored in the database.
 *
 * We use Moshi for JSON serialisation of:
 *  - List<String>  (medical_conditions, medications, allergies)
 *  - The medical_snapshot JSON string is stored raw — no converter needed
 *    since SosRequestEntity stores it as String already.
 *
 * The Moshi instance here uses reflection (KotlinJsonAdapterFactory) since
 * these are simple types and we don't need KSP codegen for converters.
 */
class RoomConverters {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    @TypeConverter
    fun fromStringList(list: List<String>): String =
        stringListAdapter.toJson(list)

    @TypeConverter
    fun toStringList(json: String): List<String> =
        runCatching { stringListAdapter.fromJson(json) ?: emptyList() }
            .getOrDefault(emptyList())
}