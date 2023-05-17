package com.example.detection_k

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.detection_k.activity.CVmotionActivity
import com.example.detection_k.activity.CameraActivity
import com.example.detection_k.bluetooth.ConnectBluetooth
import com.example.detection_k.db.RoomDB
import com.example.detection_k.mqtt.MqttClass
import com.example.detection_k.retrofit.Event
import com.example.detection_k.retrofit.EventService
import com.example.detection_k.supportYolo.Result
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.core.Mat
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SupportCoroutine(var context: Context, var activity: Activity) {
    private val mqttScope = CoroutineScope(Dispatchers.IO)
    private val postScope = CoroutineScope(Dispatchers.IO)
    private val mqttClass: MqttClass = MqttClass(context, activity)
    private val dataProcess = DataProcess()
    private val id = RoomDB.getInstance(context)!!.userDAO().getAll()!![0]
    private val sendToVideo = ArrayList<Int>() // 사진을 모아 영상으로 변환할 ID 값들의 모임
    private var next = true          //시간을 비교해서 mqtt 전송
    private var date1: Long? = null //시간을 비교해서 mqtt 전송
    private var date2: Long? = null //시간을 비교해서 mqtt 전송
    private val retrofit: Retrofit
    private val service: EventService
    private val connectBluetooth: ConnectBluetooth

    init {
        //mqtt 전송 (썸넬, 각종 메시지)
        mqttClass.connectMqtt()

        //http post 전송 (이벤트 발생 시)
        retrofit = Retrofit.Builder().baseUrl("https://" + MqttClass.SERVER_ADDRESS + ":8097/")
            .addConverterFactory(GsonConverterFactory.create()).build()
        service = retrofit.create(EventService::class.java)

        connectBluetooth = ConnectBluetooth(context)
        try {
            connectBluetooth.bluetoothConnect()
            mqttClass.setBluetoothConnect(connectBluetooth)
        } catch (e: java.lang.IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    suspend fun sendThumbnail(topic: String, bitmap: Bitmap, intervalTime: Float) {
        val thumbnailJob = mqttScope.launch {
            val jsonObject = JSONObject()

            // 시간 비교
            if (intervalTime != 0f) {
                if (next) {
                    date1 = SystemClock.elapsedRealtime()
                } else {
                    date2 = SystemClock.elapsedRealtime()
                }

                val ok = dataProcess.diffTime(date1, date2, intervalTime)
                if (!ok) {
                    return@launch
                }

                //특정 초를 넘겼다면 각종 정보들을 json 에 담는다.
                jsonObject.put(
                    "CameraId",
                    RoomDB.getInstance(context)!!.userDAO().getAll()!![0]!!.cameraId!!.toInt()
                )
                jsonObject.put(
                    "Thumbnail",
                    "data:image/jpeg;base64," + dataProcess.bitmapToString(bitmap)
                )

                //전송
                mqttClass.publish(topic, jsonObject)
                //전송이 끝나면 next 변경
                next = !next
            }
        }
        thumbnailJob.join()
    }

    //http post 요청 (이벤트 발생 시)
    suspend fun sendPost(
        mat: Mat?,
        bitmap: Bitmap?,
        results: ArrayList<Result>?,
        classes: Array<String>?
    ) {
        val postJob = postScope.launch {
            // 코루틴을 이용한 post
            try {
                val event = Event()
                val header = Event.EventHeader()
                header.mCameraId = id!!.cameraId!!.toInt()
                header.mCreated = dataProcess.saveTime()

                if (mat != null) {
                    val sendMat = mat.clone()
                    header.mPath = dataProcess.bitmapToString(dataProcess.matToBitmap(sendMat, 4))
                    header.mIsRequiredObjectDetection = false

                    sendMat.release()

                } else {
                    header.mPath = dataProcess.bitmapToString(bitmap!!)
                    header.mIsRequiredObjectDetection = true

                    val bodies = ArrayList<Event.EventBody>()
                    results!!.forEach {
                        val body = Event.EventBody(
                            it.rectF.left.toInt(),
                            it.rectF.top.toInt(),
                            it.rectF.right.toInt(),
                            it.rectF.bottom.toInt(),
                            classes!![it.classIndex]
                        )
                        bodies.add(body)
                    }
                    event.setEventBodies(bodies)
                }

                event.setEventHeader(header)

                val response = withContext(Dispatchers.IO) {
                    service.sendEvent(event).execute()
                }
                if (response.isSuccessful && response.body() != null) {
                    if (response.body()!!.toInt() != 0) {
                        sendToVideo.add(response.body()!!.toInt())
                    }
                    Log.d("response", response.body()!!)
                } else {
                    Log.e("response", "HTTP error: ${response.code()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        postJob.join()
    }

    //10개가 쌓이면 영상으로 변환한다.(시간이 지남에 따라 n배로 증가한다.) 혹은 어플 종료시 남은 사진을 영상으로 변환한다.
    fun sendMakeVideo(isSend: Boolean, isLast: Boolean, size: Int, who: String?): Boolean {
        if ((sendToVideo.size >= (10 * size) && isSend) || (sendToVideo.size > 0 && isLast)) {
            val jsonObject = JSONObject()
            val eventIds = JSONArray()

            sendToVideo.forEach {
                eventIds.put(it)
            }
            sendToVideo.clear()
            jsonObject.put("EventHeaderIds", eventIds)
            mqttClass.publish(MqttClass.TOPIC_MAKE_VIDEO, jsonObject)
            Log.d("Lets make Video!", "Lets make Video  $jsonObject")

            if (who == "motion") {
                CVmotionActivity.size++
            } else {
                CameraActivity.size++
            }
            return false
        } else if (sendToVideo.size < (10 * size) && !isSend) {
            return true
        }
        return isSend
    }

    fun receiveMQTT(vararg topics: String) {
        mqttClass.receive(*topics)
    }

    fun cancelScope() {
        mqttScope.cancel()
        postScope.cancel()
        connectBluetooth.close()
        mqttClass.closeMqtt()
    }
}