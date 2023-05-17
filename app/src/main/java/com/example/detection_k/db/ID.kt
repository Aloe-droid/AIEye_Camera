package com.example.detection_k.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

//데이터 항목
@Entity
class ID : Serializable {
    @PrimaryKey(autoGenerate = true)
    var uid = 0

    @ColumnInfo(name = "camera_id")
    var cameraId: String? = null

    @ColumnInfo(name = "bluetooth_address")
    var address: String? = null
}
