package com.example.detection_k.bluetooth

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.detection_k.db.RoomDB
import com.example.detection_k.mqtt.MqttClass
import com.example.detection_k.mqtt.SupportMqtt
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.*

class ConnectBluetooth(var context: Context) {
    private var bluetoothGatt: BluetoothGatt? = null //gatt 객체
    lateinit var gattCharacteristic: BluetoothGattCharacteristic // gatt 서버 내부 특성
    private val bluetoothAdapter = //블루투스 객체
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val bluetoothGattCallback = object : BluetoothGattCallback() { //콜백 객체
        //연결 시도
        @Throws(SecurityException::class)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // 새로운 상태가 연결되었을 때, 서비스 검색
                gatt?.discoverServices()
                // 연결 성공 메시지
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Bluetooth 연결 성공!", Toast.LENGTH_SHORT).show()
                }
            }
        }


        //연결된 GATT 서버의 서비스에 대한 값 수신
        @Throws(SecurityException::class)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // GATT 서버 내부 서비스의 UUID, 그 서비스 내부 특성의 UUID. (이 특성에서 데이터의 송수신)
                gattCharacteristic =
                    gatt?.getService(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
                        ?.getCharacteristic(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))!!
                // 해당 특성의 알람 설정
                gatt.setCharacteristicNotification(gattCharacteristic, true)

                // 특성 내부의 descriptor 에서 알람 설정 확인
                val gattDescriptor =
                    gattCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    gatt.writeDescriptor(
                        gattDescriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                else {
                    gattDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(gattDescriptor)
                }
            }
        }

        //데이터 수신
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
        ) {
            try {
                val msg = Regex("""\d+/ack""").find(String(value, StandardCharsets.UTF_8))?.value
                if(msg != null) {
                    val degreeAndAck = msg.split("/")
                    val degree = degreeAndAck[0]
                    val ack = degreeAndAck[1]
                    sendServer(ack, degree.toInt())
                }
                super.onCharacteristicChanged(gatt, characteristic, value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @Deprecated(
            "Deprecated in Java", ReplaceWith(
                "super.onCharacteristicChanged(gatt, characteristic)",
                "android.bluetooth.BluetoothGattCallback"
            )
        )
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            try {
                if (characteristic != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    val bytes = characteristic.value
                    val value = String(bytes, StandardCharsets.UTF_8)
                    val msg = Regex("""\d+/ack""").find(value)?.value
                    if(msg != null) {
                        val degreeAndAck = msg.split("/")
                        val degree = degreeAndAck[0]
                        val ack = degreeAndAck[1]
                        sendServer(ack, degree.toInt())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            super.onCharacteristicChanged(gatt, characteristic)
        }
    }


    @Throws(SecurityException::class)
    fun bluetoothConnect() {
        val id = RoomDB.getInstance(context)!!.userDAO().getAll()!![0]
        if (id?.address != null) {
            bluetoothGatt = bluetoothAdapter.getRemoteDevice(id.address)
                .connectGatt(context, true, bluetoothGattCallback)
        }
    }

    @Throws(SecurityException::class)
    fun close() {
        bluetoothGatt?.close()
    }

    @Throws(JSONException::class)
    fun sendServer(ack: String, degree: Int) {
        //만약 아두이노로부터 각도제어가 끝난후 ack 가 오면 mqtt 로 제어가 끝났다고 서버에게 알림.
        if (ack == "ack") {
            val jsonObject = JSONObject()
            jsonObject.put(
                "CameraId",
                RoomDB.getInstance(context)?.userDAO()?.getAll()
                    ?.get(0)?.cameraId?.toInt()
            )
            jsonObject.put("Degree", degree)
            // 값을 다시 전송
            SupportMqtt.getInstance().getMqttClient().run {
                this?.publish(
                    MqttClass.TOPIC_MOTOR_ACK, MqttMessage(
                        jsonObject.toString().toByteArray(StandardCharsets.UTF_8)
                    )
                )
            }
        }
    }

    @Throws(SecurityException::class)
    fun write(message: String) {
        //해당 특성에 보낼 값을 저장한다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && bluetoothGatt != null) {
            bluetoothGatt?.writeCharacteristic(
                gattCharacteristic,
                ("$message.").toByteArray(StandardCharsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else if (bluetoothGatt != null) {
            gattCharacteristic.value = ("$message.").toByteArray(StandardCharsets.UTF_8)
            bluetoothGatt?.writeCharacteristic(gattCharacteristic)
        }
    }
}