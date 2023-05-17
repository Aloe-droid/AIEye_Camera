package com.example.detection_k

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat

class PermissionSupport(private val context: Context, private val activity: Activity) {
    private val multiplePermissions: Int = 1
    private val permissions: Array<String>

    init {
        //권한 -> 동적 할당
        val permission = ArrayList<String>()
        permission.add(android.Manifest.permission.CAMERA)   // YOLO 를 사용하기 위한 카메라
        permission.add(android.Manifest.permission.INTERNET) // 서버와 소통을 위한 인터넷
        permission.add(android.Manifest.permission.RECORD_AUDIO) // webRTC 에서 사용될 오디오
        permission.add(android.Manifest.permission.ACCESS_COARSE_LOCATION) // BLE 기기찾기
        permission.add(android.Manifest.permission.ACCESS_FINE_LOCATION) // BLE
        permission.add(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permission.add(android.Manifest.permission.BLUETOOTH)
            permission.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            permission.add(android.Manifest.permission.BLUETOOTH_SCAN)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permission.add(android.Manifest.permission.BLUETOOTH)
        }

        //String[] 로 변환
        permissions = permission.toArray(arrayOf<String>())
    }

    //권한 확인 메서드
    fun checkPermissions() {
        permissions.forEach {
            if (ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED){
                if(ActivityCompat.shouldShowRequestPermissionRationale(activity, it)) {
                    Toast.makeText(context,"권한 허용", Toast.LENGTH_SHORT).show()
                }else{
                    ActivityCompat.requestPermissions(activity, permissions, multiplePermissions)
                }
            }
        }
    }
}