package com.example.detection_k.activity

import android.os.Bundle
import android.os.SystemClock
import android.view.SurfaceView
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.detection_k.DataProcess
import com.example.detection_k.R
import com.example.detection_k.SupportCoroutine
import com.example.detection_k.mqtt.MqttClass
import kotlinx.coroutines.launch
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.util.*

class CVmotionActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private lateinit var openCameraView: CameraBridgeViewBase
    private lateinit var imgA: Mat
    private lateinit var imgB: Mat
    private lateinit var output: Mat
    private lateinit var supportCoroutine: SupportCoroutine
    private var timeCount1: Long? = 0 //시간 측정을 위한 값
    private var motionCount = 0 //측정 count
    private var isSendMakeMotionVideo = true
    private val dataProcess = DataProcess() // 각종 데이터 처리 클래스
    private var isMotion = false //모션 감지 확인
    private var isFirst = true

    // 콜백 메서드
    private val loaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                openCameraView.enableView()
            } else {
                super.onManagerConnected(status)
            }
        }
    }

    companion object {
        var size = 1  //사진 -> 영상으로 변하는 n의 배수 ex) 1이면 10장 -> 영상, 2이면 20장 -> 영상 이다.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cvmotion)

        // 화면꺼짐 해제
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        supportCoroutine = SupportCoroutine(this, this).also {
            it.receiveMQTT( //mqtt 수신
                MqttClass.TOPIC_MOTOR,
                MqttClass.TOPIC_WEBRTC,
                MqttClass.TOPIC_WEBRTC_FIN
            )
        }

        // 뷰 설정
        openCameraView = findViewById(R.id.activity_surface_view)
        openCameraView.visibility = SurfaceView.VISIBLE
        openCameraView.setCvCameraViewListener(this)
        openCameraView.setCameraIndex(1) //front == 1, back == 0

        checkCvPermissionCheck()

    }


    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (isFirst) {
            imgA = inputFrame!!.gray()
            imgB = inputFrame.gray()
            isFirst = false
        }
        val imgC = inputFrame!!.gray()
        output = inputFrame.rgba()

        //썸넬 전송
        val sendImage = output.clone()
        lifecycleScope.launch {
            if (sendImage.width() > 0) {
                supportCoroutine.sendThumbnail(
                    MqttClass.TOPIC_PREVIEW,
                    dataProcess.matToBitmap(sendImage, 5),
                    0.5f
                )
            }
        }

        Core.flip(output, output, 1)

        val diff1 = Mat()
        val diff2 = Mat()
        Core.absdiff(imgA, imgB, diff1)
        Core.absdiff(imgB, imgC, diff2)

        val diff1Thresh = Mat()
        val diff2Thresh = Mat()
        val thresholdMove = 50.0
        Imgproc.threshold(diff1, diff1Thresh, thresholdMove, 255.0, Imgproc.THRESH_BINARY)
        Imgproc.threshold(diff2, diff2Thresh, thresholdMove, 255.0, Imgproc.THRESH_BINARY)

        val diff = Mat()
        Core.bitwise_and(diff1Thresh, diff2Thresh, diff)

        val diffCount = Core.countNonZero(diff)

        val diffCompare = 10000
        if (diffCount > diffCompare) {
            val nonZero = Mat()
            Core.findNonZero(diff, nonZero)
            val rect = Imgproc.boundingRect(nonZero)
            //좌우 반전이라 좌표도 반전
            // 사각형 객체
            Imgproc.rectangle(
                output, Point((output.cols() - rect.x).toDouble(), rect.y.toDouble()), Point(
                    (output.cols() - rect.x - rect.width).toDouble(),
                    (rect.y + rect.height).toDouble()
                ), Scalar(0.0, 255.0, 0.0), 7
            )
            // 왼쪽 위의 글자
            Imgproc.putText(
                output,
                dataProcess.saveTime(),
                Point(10.0, 50.0),
                Imgproc.FONT_HERSHEY_COMPLEX,
                2.0,
                Scalar(0.0, 0.0, 255.0)
            )
            nonZero.release()

            //시간 측정 및 갯수 세기
            timeCount1 = SystemClock.elapsedRealtime()
            motionCount++
            isMotion = true
        }
        //시간 비교
        val timeCount2 = SystemClock.elapsedRealtime()
        //만약 30초간 객체가 감지되지 않았다면 남아있는 모든 사진을 영상으로 변환 후 count 를 0으로 수정한다
        if (dataProcess.diffTime(timeCount1, timeCount2, 30f)) {
            supportCoroutine.sendMakeVideo(false, isLast = true, 1, null)
            motionCount = 0
            size = 1
        }

        //count 가 5 이상이면 전송을 해당 사진을 전송한다.
        if (motionCount >= 5 && output.width() > 0 && isMotion) {
            lifecycleScope.launch {
                supportCoroutine.sendPost(output, null, null, null)
                isMotion = false
                //일정 갯수가 넘으면 사진 -> 영상으로 변환
                isSendMakeMotionVideo =
                    supportCoroutine.sendMakeVideo(isSendMakeMotionVideo, false, size, "motion")
            }
        }

        imgA.release()
        imgA = imgB.clone()
        imgB.release()
        imgB = imgC.clone()
        imgC.release()
        diff1.release()
        diff2.release()
        diff1Thresh.release()
        diff2Thresh.release()
        diff.release()
        sendImage.release()
        return output
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, loaderCallback)
        } else {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        //만약 앱이 종료된다면, 현재 남아있는 사진 list 를 영상으로 만들라하고 종료한다.
        supportCoroutine.sendMakeVideo(false, isLast = true, 1, null)
        openCameraView.disableView()
        supportCoroutine.cancelScope()
    }

    //권한 허용
    private fun checkCvPermissionCheck() {
        val cameraViews = Collections.singletonList(openCameraView)
        cameraViews.forEach {
            it.setCameraPermissionGranted()
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
    }

    override fun onCameraViewStopped() {
        output.release()
    }

}