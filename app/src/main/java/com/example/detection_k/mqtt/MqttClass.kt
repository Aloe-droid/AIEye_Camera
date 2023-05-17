package com.example.detection_k.mqtt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.detection_k.activity.WebVIewActivity
import com.example.detection_k.bluetooth.ConnectBluetooth
import com.example.detection_k.db.ID
import com.example.detection_k.db.RoomDB
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.*

class MqttClass(private val context: Context, private val activity: Activity) {

    companion object {
        const val SERVER_ADDRESS = "**************************"

        const val TOPIC_MOTOR = "camera/update/degree/syn"
        const val TOPIC_PREVIEW = "camera/update/thumbnail"
        const val TOPIC_MOTOR_ACK = "camera/update/degree/ack"
        const val TOPIC_WEBRTC = "call/start"
        const val TOPIC_WEBRTC_FIN = "call/stop"
        const val TOPIC_MAKE_VIDEO = "video/create"
    }

    private var clientId: String
    private var id: ID
    lateinit var bluetooth: ConnectBluetooth
    private lateinit var mqttClient: MqttClient

    init {
        val random = Random()
        val randomInts = mutableListOf<Int>()
        repeat(10) {
            randomInts.add(random.nextInt())
        }
        clientId = "android" + randomInts.joinToString(separator = "")
        id = RoomDB.getInstance(context)!!.userDAO().getAll()!![0]!!
    }

    //블루투스 객체 가져오기
    fun setBluetoothConnect(connectBluetooth: ConnectBluetooth) {
        this.bluetooth = connectBluetooth
    }

    //mqtt 연결
    fun connectMqtt() {
        val options = MqttConnectOptions()
        //만약 끊겼다가 재연결 될 때 이전의 상태를 유지할 것인지 다시 새로운 연결을 시도 할 것인지 결정.
        options.isCleanSession = false
        //만약 특정 시간동안 보내는 게 없으면 끊어진다. 그 특정시간을 최대로 설정.
        options.keepAliveInterval = Int.MAX_VALUE

        val persistence = object : MqttDefaultFilePersistence(activity.filesDir.absolutePath) {}
        mqttClient = MqttClient("tcp://$SERVER_ADDRESS:8085", clientId, persistence)

        val token = mqttClient.connectWithResult(options)
        token.waitForCompletion() // 연결이 완료될 때까지 대기

        if (mqttClient.isConnected) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "mqtt 연결 성공!", Toast.LENGTH_SHORT).show()
                Log.d("연결","성공")
            }
        }

        //mqttClient 클래스 전송
        SupportMqtt.getInstance().setMqttClient(mqttClient)
    }

    //전송하는 메소드
    fun publish(topic: String, jsonObject: JSONObject) {
        val message = MqttMessage(jsonObject.toString().toByteArray(StandardCharsets.UTF_8))
        try {
            mqttClient.publish(topic, message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //수신 메서드 (콜백)
    fun receive(vararg topics: String) {
        mqttClient.subscribe(topics)
        mqttClient.setCallback(object : MqttCallback {

            override fun connectionLost(cause: Throwable?) {
                cause?.printStackTrace()
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                try {
                    val jsonObject = JSONObject(String(message!!.payload))

                    //TOPIC 이 모터제어라면
                    if (topic == TOPIC_MOTOR) {
                        if (jsonObject.get("CameraId").toString() == id.cameraId) {
                            val msg = jsonObject.get("Degree").toString()
                            //블루투스로 각도값 전송
                            bluetooth.write(msg)
                        }
                    }
                    // webRTC 를 하자고 신청이 오면
                    else if (topic == TOPIC_WEBRTC) {
                        //문자열을 읽어서 현재 내 아이디가 맞는지 확인하고 맞다면 전송을한다.
                        val cameraId = jsonObject.get("CameraId").toString()
                        //만약 카메라 ID가 동일하다면 웹사이트 접속
                        if (cameraId == id.cameraId) {
                            //해당 웹사이트 주소
                            val url =
                                "https://$SERVER_ADDRESS:8101/camera/$cameraId/register"
                            val intent = Intent(activity, WebVIewActivity::class.java)
                            intent.putExtra("url", url)
                            activity.startActivity(intent)
                        }
                    }
                    // webRTC 종료 요청
                    else if (topic == TOPIC_WEBRTC_FIN) {
                        val cameraId = jsonObject.get("CameraId").toString()
                        if (id.cameraId == cameraId) {
                            val webVIewActivity = WebVIewActivity.webViewActivity
                            webVIewActivity.finish()
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }
        })
    }

    fun closeMqtt() {
        mqttClient.disconnect()
        mqttClient.close()
    }

}