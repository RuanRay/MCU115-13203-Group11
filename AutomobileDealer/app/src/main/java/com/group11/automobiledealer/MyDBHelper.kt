package com.group11.automobiledealer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// 舊車商資料庫：carTable(廠牌 PK, 車型, 年份, 價格)，全部 NOT NULL
class MyDBHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, VERSION) {
    companion object {
        private const val DB_NAME = "carDatabase"
        private const val VERSION = 1
        const val TABLE = "carTable"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE(" +
                "brand TEXT PRIMARY KEY NOT NULL, " +
                "model TEXT NOT NULL, " +
                "year INTEGER NOT NULL, " +
                "price INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }
}
