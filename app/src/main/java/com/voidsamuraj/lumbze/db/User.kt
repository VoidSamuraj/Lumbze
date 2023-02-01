package com.voidsamuraj.lumbze.db

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

class TypeConventer {
    @TypeConverter
    fun fromArrayListOfInt(list: ArrayList<Int>?): String {
        return list?.joinToString(separator = ";") { it.toString() } ?: ""
    }

    @TypeConverter
    fun toArrayListOfInt(string: String?): ArrayList<Int> {
        return ArrayList(string?.split(";")?.mapNotNull { it.toIntOrNull() } ?: emptyList())
    }
}


@Keep
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id") val id:String,
    @ColumnInfo(name = "name")val name:String,
    @ColumnInfo(name = "points")val points:Int,
    @ColumnInfo(name = "unlocked_balls", defaultValue = "" )val unlocked_balls:ArrayList<Int>
    )
