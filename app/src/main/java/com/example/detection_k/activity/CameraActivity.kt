package com.example.detection_k.activity

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.example.detection_k.DataProcess
import com.example.detection_k.R
import com.example.detection_k.SupportCoroutine
import com.example.detection_k.mqtt.MqttClass
import com.example.detection_k.supportYolo.ProcessOnnx
import com.example.detection_k.supportYolo.RectView
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class CameraActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var rectView: RectView
    private lateinit var supportCoroutine: SupportCoroutine
    private lateinit var processOnnx: ProcessOnnx
    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var session: OrtSession

    //이벤트 발송을 위한 측정
    private val dataProcess = DataProcess()
    private var timeCount1: Long? = 0
    private var objectCount = 0 //측정 count
    private var isSendMakeObjectVideo = true
    private var isObject = false //객체 감지 확인

    companion object {
        var size = 1    //사진 -> 영상으로 변하는 n의 배수 ex) 1이면 10장 -> 영상, 2이면 20장 -> 영상 이다.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        previewView = findViewById(R.id.previewView)
        rectView = findViewById(R.id.rectView)

        //자동꺼짐 해제
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        supportCoroutine = SupportCoroutine(this, this).also {
            it.receiveMQTT(  //mqtt 수신
                MqttClass.TOPIC_MOTOR,
                MqttClass.TOPIC_WEBRTC,
                MqttClass.TOPIC_WEBRTC_FIN
            )
        }

        processOnnx = ProcessOnnx(this)

        load() //모델 불러오기

        startCamera() //카메라 설정 && 카메라 켜기
    }


    private fun startCamera() {
        //카메라 제공 객체
        val processCameraProvider = ProcessCameraProvider.getInstance(this).get()

        //전체화면
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        val cameraSelector =    //전면 카메라
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        val preview =           // 16:9 화면
            Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build()

        preview.setSurfaceProvider(previewView.surfaceProvider) //preview 가 화면을 받아와서 previewView 에 보여줌

        //분석 중이면 그 다음 화면이 대기중인 것이 아니라 계속 받아오는 화면으로 새로고침 함. 분석이 끝나면 그 최신 사진을 다시 분석
        val analysis = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) {
            imageProcess(it) //이미지 처리 (객체 검출)
            it.close()
        }
        rectView.classes = processOnnx.classes //그림을 그릴 rectView 클래스에 label 정보 (화재, 연기) 배열을 전달한다.
        //생명주기는 이 클래스에 귀속
        processCameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun imageProcess(imageProxy: ImageProxy) {
        try {
            //이미지 받아오기
            val image = imageProxy.image

            if (image != null) {
                val bitmap = processOnnx.imageToBitmap(image)

                lifecycleScope.launch {
                    val serverBitmap = Bitmap.createScaledBitmap(   //서버로 전송할 이미지는 크기를 줄여야한다.
                        bitmap,
                        (bitmap.width / 3f).roundToInt(),
                        (bitmap.height / 3f).roundToInt(),
                        true
                    )
                    supportCoroutine.sendThumbnail(MqttClass.TOPIC_PREVIEW, serverBitmap, 0.5f)
                }

                val bitmap640 = processOnnx.rescaledBitmap(bitmap)
                val imgDateFloat = processOnnx.bitmapToFloatBuffer(bitmap640)

                val inputName = session.inputNames.iterator().next() //OrtSession 의 이름
                //모델의 요구 입력값 배열 설정 [1,3,640,640] 모델마다 상이할수 있음.
                val shape = longArrayOf(
                    ProcessOnnx.BATCH_SIZE.toLong(),
                    ProcessOnnx.PIXEL_SIZE.toLong(),
                    ProcessOnnx.INPUT_SIZE.toLong(),
                    ProcessOnnx.INPUT_SIZE.toLong()
                )
                val inputTensor = OnnxTensor.createTensor(ortEnvironment, imgDateFloat, shape)
                val result = session.run(Collections.singletonMap(inputName, inputTensor))
                //yolo v8 모델의 출력 크기는 [1][6][8400]이다.
                //v5와 달리 좌표값 4개 + alpha (label 속 데이터의 갯수)이다. 이제 정확도(confidence)가 사라지고
                //해당 label 의 배열안에서 최대 값을 구하면 된다.
                val outputs = result.get(0).value as Array<*>
                var results = processOnnx.outputsToNPMSPredictions(outputs)

                //화면에 출력하게 결과값을 rectView 에 전달한다.
                results = rectView.transFormRect(results)
                rectView.clear()
                rectView.resultToList(results)
                rectView.invalidate()

                //만약 유의미한 결과가 나왔다면시간을 측정한다. 그리고 objectCount 를 증가한다.
                if (results.size > 0) {
                    timeCount1 = SystemClock.elapsedRealtime()
                    objectCount++
                    isObject = true
                }

                //시간 비교
                val timeCount2 = SystemClock.elapsedRealtime()
                //만약 30초간 객체가 감지되지 않았다면 남아있는 모든 사진을 영상으로 변환 후 count 를 0으로 수정한다
                if (dataProcess.diffTime(timeCount1, timeCount2, 30f)) {
                    supportCoroutine.sendMakeVideo(false, isLast = true, 1, null)
                    objectCount = 0
                    size = 1
                }

                //count 가 5 이상이면 전송을 해당 사진을 전송한다. 단, 원본 사진을 보내야 하므로 크기를 키운다.
                if (objectCount >= 5 && isObject) {
                    lifecycleScope.launch {
                        supportCoroutine.sendPost(null, bitmap, results, processOnnx.classes)
                        isObject = false
                        isSendMakeObjectVideo = //일정 갯수가 넘으면 사진 -> 영상으로 변환
                            supportCoroutine.sendMakeVideo(
                                isSendMakeObjectVideo,
                                false,
                                size,
                                "object"
                            )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun load() {
        processOnnx.loadModel()
        processOnnx.loadLabel()
        //객체 검출을 위한 onnxRuntime 클래스
        ortEnvironment = OrtEnvironment.getEnvironment()
        session = ortEnvironment.createSession(
            this.filesDir.absolutePath.toString() + "/" +
                    ProcessOnnx.FILE_NAME, OrtSession.SessionOptions()
        )
    }

    override fun onStop() {
        session.endProfiling()
        super.onStop()
    }

    override fun onDestroy() {
        //만약 앱이 종료된다면, 현재 남아있는 사진 list 를 영상으로 만들라하고 종료한다.
        supportCoroutine.sendMakeVideo(false, isLast = true, 1, null)
        supportCoroutine.cancelScope()
        super.onDestroy()
    }
}
