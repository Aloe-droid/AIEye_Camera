package com.example.detection_k.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

//데이터 액세스 객체(DAO) Data Access Object
@Dao
interface UserDAO {
    //ID 란 이름을 가진 테이블로부터 모든 값(*)을 가져오란 의미.(Select)
    @Query("SELECT * FROM ID")
    fun getAll(): List<ID?>?

    //id 삽입
    @Insert
    fun insert(id: ID?)

    //기존의 id 삭제
    @Delete
    fun delete(id: ID?)
}
