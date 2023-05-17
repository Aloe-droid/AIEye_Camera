package com.example.detection_k.bluetooth

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import java.util.*

//블루투스 연결 클래스
class SupportBluetooth(private val context: Context) {
    var address: String? = null
    private lateinit var pairedBluetoothDevices: Set<BluetoothDevice>
    private lateinit var leScanner: BluetoothLeScanner

    private val bluetoothAdapter: BluetoothAdapter =  // bluetooth 객체
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanCallback: ScanCallback
    private val bluetoothDeviceSet = HashSet<String>()
    private val newDeviceSet = HashSet<BluetoothDevice>()
    private val uUidService: UUID = //블루투스 범용 고유 식별자
        UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")

    init {
        //스캔 콜백 객체
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                val serviceUuids = result?.scanRecord?.serviceUuids
                serviceUuids?.forEach {
                    //그중 HM10의 디폴트 UUID 가 보이면 주소를 저장한다.
                    if (it.uuid.equals(uUidService)) {
                        newDeviceSet.add(result.device)
                    }
                }
            }
        }
    }

    //블루투스 켜기
    @Throws(SecurityException::class)
    fun bluetoothOn() {
        //블루투스 켜져 있는지 확인
        if (!bluetoothAdapter.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            context.startActivity(enableIntent)
        }
    }


    //페어링된 기기 list 에 저장
    @Throws(SecurityException::class)
    fun listPairedDevices() {
        val setBluetoothDevices = HashSet<String>()
        //페어링이 가능한 기기 목록 저장
        pairedBluetoothDevices = bluetoothAdapter.bondedDevices
        if (pairedBluetoothDevices.isNotEmpty()) {
            pairedBluetoothDevices.forEach {
                setBluetoothDevices.add(it.name)
            }
        }
        setBluetoothDevices.add("새로운 기기 찾기")
        alertDialogBluetooth(setBluetoothDevices, false)
    }

    //리스트 형태의 블루투스 목록을 화면에 보여주기
    private fun alertDialogBluetooth(setBluetoothDevices: HashSet<String>, isNew: Boolean) {
        //메시지 띄우기
        val builder = AlertDialog.Builder(context)
        builder.setTitle("장치 선택")
        //동적으로 할당한 set 을 다시 배열로 변환
        val items = setBluetoothDevices.toTypedArray<CharSequence>()
        //기기이름을 클릭하면 해당 기기를 연결하는 메소드로 연결
        builder.setItems(items) { _, which ->
            connectSelectDevice(items[which].toString(), isNew)
        }
        //화면에 보여주기
        val dialog = builder.create()
        dialog.show()
    }

    @Throws(SecurityException::class)
    private fun connectSelectDevice(selectedDeviceName: String, isNewDevice: Boolean) {
        if (selectedDeviceName == "새로운 기기 찾기") {
            //새로운 블루투스 기기 찾기
            searchExtraDevice()
            //로딩창 객체 생성
            val progressDialog = ProgressDialog(context)
            // 로딩부분 투명하게
            progressDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            progressDialog.show()

            //3초뒤에 다시 알람 전송
            object : CountDownTimer(3000, 3000) {
                override fun onTick(millisUntilFinished: Long) {
                }

                @Throws(SecurityException::class)
                override fun onFinish() {
                    //로딩창 그만 보여주기
                    progressDialog.cancel()
                    // BLE 스캔 멈추기
                    leScanner.stopScan(scanCallback)

                    // 이어서 이름만 따로 HashSet 으로 저장한다. 이후의 Dialog 에 표출될 이름.
                    if (newDeviceSet.isNotEmpty()) {
                        newDeviceSet.forEach {
                            bluetoothDeviceSet.add(it.name)
                        }
                    }
                    // Dialog 표출
                    alertDialogBluetooth(bluetoothDeviceSet, true)
                }
            }.start()
        } else if (!isNewDevice) {
            //선택된 디바이스의 이름과 페어링 된 기기목록과 이름이 일치하면 연결 (기존에 페어링된)
            pairedBluetoothDevices.forEach {
                if (selectedDeviceName == it.name) {
                    //BluetoothDevice 의 주소 저장
                    address = it.address
                }
            }
        } else {
            // 새로운페어링 생성
            newDeviceSet.forEach {
                if (selectedDeviceName == it.name) {
                    address = it.address
                }
            }
        }
    }

    @Throws(SecurityException::class)
    private fun searchExtraDevice() {
        leScanner = bluetoothAdapter.bluetoothLeScanner
        leScanner.startScan(scanCallback)
    }
}