package com.example.detection_k.activity

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.example.detection_k.PermissionSupport
import com.example.detection_k.R
import com.example.detection_k.bluetooth.SupportBluetooth
import com.example.detection_k.db.ID
import com.example.detection_k.db.RoomDB
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {
    private lateinit var cameraId: String
    private lateinit var editText: EditText
    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        editText = findViewById(R.id.EditText)
        val motionCheck = findViewById<CheckBox>(R.id.Motion)
        val objectCheck = findViewById<CheckBox>(R.id.Object)
        val button = findViewById<Button>(R.id.button)
        val buttonQR = findViewById<Button>(R.id.buttonQR)
        val buttonBT = findViewById<Button>(R.id.buttonBT)

        //권한 확인
        checkPermission()

        //자동꺼짐 해제
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        //블루투스 클래스
        val bluetoothConnect = SupportBluetooth(this)
        //블루투스 켜기
        bluetoothConnect.bluetoothOn()

        //DB 생성
        val roomDB = RoomDB.getInstance(this)
        if (roomDB?.userDAO()?.getAll()?.size!! > 0) {
            //기존의 id를 유지하려면 그냥 버튼만 누르면 되게 text 에 기존의 id를 넣는다.
            editText.setText(roomDB.userDAO().getAll()?.get(0)?.cameraId)
        }

        //스캔 콜백 
        scanBarcode()

        button.setOnClickListener {
            val id = ID()
            try {
                //QR 코드를 통해 새로운 cameraID를 받는 경우
                id.cameraId = cameraId.trim()
                //페어링 된 기기 주소
                if(bluetoothConnect.address != null) {
                    id.address = bluetoothConnect.address!!.trim()
                }

            //QR 코드를 통해 cameraID를 받지 않고 기존의 ID를 쓰는 경우
            } catch (e: UninitializedPropertyAccessException) {
                try {
                    val beforeId = roomDB.userDAO().getAll()!![0]!!
                    id.cameraId = beforeId.cameraId
                    id.address = beforeId.address

                // id 값이 없는 경우 qr 등록을 위해 다시 대기
                }catch (e : java.lang.IndexOutOfBoundsException){
                    return@setOnClickListener
                }

            } catch (e: java.lang.NullPointerException) {
                id.address = null
            }

            //기존의 데이터를 삭제한다. 카메라 id 를 1개로 유지하기 위해서 이다.
            if (roomDB.userDAO().getAll()?.size!! > 0) {
                roomDB.userDAO().delete(roomDB.userDAO().getAll()!![0]!!)
            }

            //다시 id를 저장한다.
            roomDB.userDAO().insert(id)

            //id를 저장한 상태로 카메라 액티비티를 실행한다.
            if (objectCheck.isChecked) {
                val intent = Intent(this@MainActivity, CameraActivity::class.java)
                startActivity(intent)
            }else if(motionCheck.isChecked){
                val intent = Intent(this@MainActivity, CVmotionActivity::class.java)
                startActivity(intent)
            //아무것도 누르지 않고 실행한 경우 다시 클릭 리스너로 대기
            }else if(!objectCheck.isChecked && !motionCheck.isChecked){
                return@setOnClickListener
            }
            this.finish()
        }

        //QR 코드 인식 버튼 여기서 카메라 id를 지정한다.
        buttonQR.setOnClickListener {
            // qr 코드 스캔
            barcodeLauncher.launch(ScanOptions())
        }

        //블루투스 페어링 된 기기목록을 불러와 저장한다.
        buttonBT.setOnClickListener {
            bluetoothConnect.listPairedDevices()
        }

    }

    //스캔 콜백 함수
    private fun scanBarcode() {
        barcodeLauncher = registerForActivityResult(ScanContract()) {
            if (it.contents != null) {
                // UserId 와 CameraID를 받는다. ex) https://ictrobot.hknu.ac.kr:8101//camera/1/thumbnail
                val url = it.contents.toString()
                val urlCameraId = "/camera/"
                val cameraIdSlash = url.indexOf(urlCameraId)
                val register = url.indexOf("/thumbnail")
                //cameraId 받아오기
                cameraId = url.substring(cameraIdSlash + urlCameraId.length, register)
                editText.setText(cameraId)
            }
        }
    }

    //권한 허용
    private fun checkPermission() {
        val permissionSupport = PermissionSupport(this, this)
        permissionSupport.checkPermissions()
    }
}