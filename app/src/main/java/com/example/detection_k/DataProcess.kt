package com.example.detection_k

import android.graphics.Bitmap
import android.os.SystemClock
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class DataProcess {

    fun diffTime(time1: Long?, time2: Long?, intervalTime: Float): Boolean {
        //초기에는 past_date 가 없다. 그냥 true 리턴하자
        if (time2 == null) {
            return true
        }

        //시간 차이 구하기
        val difference = abs(time1!! - time2)
        //time 초 이내라면 사진 전송을 하지 않는다.
        return difference >= (intervalTime * 1000L)
    }

    //현재  시간을 구하는 메소드
    fun saveTime(): String {
        //현재 시간 구하기
        val date = Date(SystemClock.currentThreadTimeMillis())
        //데이터의 형태를 지정한다. "년도-달-일 시.분.초" 형태이다.
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.KOREA)
        //형태에 맞게 시간을 String 형태로 변환한다.
        return simpleDateFormat.format(date)
    }

    //비트맵 -> base64 (문자열) 변환
    fun bitmapToString(bitmap: Bitmap): String {
        //바이트를 보낼 통로
        val byteArrayOutputStream = ByteArrayOutputStream()
        //비트맵을 jpg 로 압축. 비트맵 용량이 보내기엔 크다. 90%로 압축
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
        //바이트 배열로 받기
        val image = byteArrayOutputStream.toByteArray()
        //string 으로 변환
        return android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP)
    }

    //매트릭스 객체 -> 비트맵 변환
    fun matToBitmap(mat: Mat, scale: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return Bitmap.createScaledBitmap(bitmap, bitmap.width / scale, bitmap.height / scale, true)
    }

}