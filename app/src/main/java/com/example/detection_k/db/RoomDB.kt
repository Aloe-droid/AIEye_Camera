package com.example.detection_k.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase

@Database(entities = [ID::class], version = 1)
abstract class RoomDB : RoomDatabase() {

    abstract fun userDAO(): UserDAO

    companion object {
        private var INSTANCE: RoomDB? = null

        //db 생성
        fun getInstance(context: Context): RoomDB? {
            if (INSTANCE == null) {
                INSTANCE = databaseBuilder(
                    context.applicationContext,
                    RoomDB::class.java, "ID.db"
                ) //메인 쓰레드에서도 쿼리가가능하게 설정
                    .allowMainThreadQueries()
                    .build()
            }
            return INSTANCE
        }
    }
}
