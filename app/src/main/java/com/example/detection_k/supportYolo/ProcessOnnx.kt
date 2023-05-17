package com.example.detection_k.supportYolo

import android.content.Context
import android.graphics.*
import android.media.Image
import java.io.*
import java.nio.FloatBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class ProcessOnnx(val context: Context) {
    companion object {
        const val INPUT_SIZE = 640
        const val BATCH_SIZE = 1
        const val PIXEL_SIZE = 3
        const val FILE_NAME = "fire_640_v8.onnx"
        const val LABEL_NAME = "label_fire_v8.txt"
    }

    lateinit var classes: Array<String>

    // 모델 읽기 (.onnx)
    fun loadModel() {
        //asset 파일을 가져올 매니저 클래스
        val assetManager = context.assets
        val outputFile = File(context.filesDir.toString() + "/" + FILE_NAME)

        assetManager.open(FILE_NAME).use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
            }
        }
    }

    // 라벨 읽기 (.txt)
    fun loadLabel() {
        BufferedReader(InputStreamReader(context.assets.open(LABEL_NAME))).use { reader ->
            var line: String?
            val classList = ArrayList<String>()
            while (reader.readLine().also { line = it } != null) {
                classList.add(line!!)
            }
            classes = classList.toTypedArray()
        }
    }

    // 비트맵 -> FloatBuffer
    fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val imageMean = 0.0
        val imageSTD = 255.0f
        val buffer = FloatBuffer.allocate(BATCH_SIZE * PIXEL_SIZE * INPUT_SIZE * INPUT_SIZE)
        buffer.rewind() //position 을 0 으로
        val area = INPUT_SIZE * INPUT_SIZE
        val bitmapData = IntArray(area) //한 사진에서 한 색상 영역에 대한 정보 (640 * 640) size
        bitmap.getPixels(bitmapData, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in 0 until INPUT_SIZE - 1) {
            for (j in 0 until INPUT_SIZE - 1) {
                // 0~ 640*640 의 픽셀값에 대해 차례대로 하나씩 가져오기
                val idx = INPUT_SIZE * i + j
                val pixelValue = bitmapData[idx]
                // 위에서 부터 차례대로 R 값 추출, G 값 추출, B값 추출 -> 255로 나누어서 0~1 사이로 정규화
                buffer.put(idx, (((pixelValue shr 16 and 0xff) - imageMean) / imageSTD).toFloat())
                buffer.put(
                    idx + area, (((pixelValue shr 8 and 0xff) - imageMean) / imageSTD).toFloat()
                )
                buffer.put(
                    idx + area * 2, (((pixelValue and 0xff) - imageMean) / imageSTD).toFloat()
                )
                //위의 원리 ARGB 형태로 되어있는 32bit -> R값의 시작은 16bit 부터임 따라서 16비트를 쉬프트 (A값에 해당하는 bit 열 제거)
                //이후 8bit 를 and 연산으로 G값과 B값 0으로 변환 -> 남은 건 8bit R값만 남음 이후 float 형태로 변환
                //이후 동일한 원리로 G값, B값 추출

            }
        }
        buffer.rewind() //position 0으로 변환
        return buffer
    }

    // image -> bitmap
    fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes

        //yuv 형태의 색상 표현 RGB 색상 표현 방식은 색 전부를 표현하지만, 용량이 너무 크기에 yuv 형태로 표현한다.
        //y = 빛의 밝기를 나타내는 휘도, u,v = 색상신호,  u가 클수록 보라색,파란색 계열 ,v가 클수록 빨간색, 초록색 계열이다.
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        //buffer 에 담긴 데이터를 limit 까지 읽어들일 수 있는 데이터의 갯수를 리턴한다.
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        //버퍼 최대크기를 모아놓은 바이트 배열
        val yuvBytes = ByteArray(ySize + uSize + vSize)
        //buffer 의 offset 위치부터 length 길이 만큼 읽음.
        //각각 y,u,v 읽어오기
        yBuffer.get(yuvBytes, 0, ySize)
        vBuffer.get(yuvBytes, ySize, vSize)
        uBuffer.get(yuvBytes, ySize + vSize, uSize)

        //바이트 배열 -> YUV Image -> ByteArray
        val yuvImage = YuvImage(yuvBytes, ImageFormat.NV21, image.width, image.height, null)
        val imageBytes = ByteArrayOutputStream().use {
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 90, it)
            it.toByteArray()
        }

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        //회전 할 수 있다. 지금은 가로모드이므로 회전할 필요가 없지만, 세로모드로 할 경우에는 90f를 추가해줘야 한다.
        val matrix = Matrix().apply { this.postRotate(0f) }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    //모델에 넣기 위해 사이즈 조절
    fun rescaledBitmap(bitmap: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
    }

    //yolo v8 의 output
    fun outputsToNPMSPredictions(outputs: Array<*>): ArrayList<Result> {
        val objectThresh = 0.45f
        val results = ArrayList<Result>()
        val rows: Int
        val cols: Int

        (outputs[0] as Array<*>).also {
            rows = it.size
            cols = (it[0] as FloatArray).size
        }
        val output = Array(cols) { FloatArray(rows) } // 8400*6 2차원 배열 형태

        for (i in 0 until rows) {
            for (j in 0 until cols) {
                output[j][i] = ((((outputs[0]) as Array<*>)[i]) as FloatArray)[j]
            }
        }

        for (i in 0 until cols) {
            var detectionClass: Int = -1
            var maxClass = 0f
            val classArray = FloatArray(classes.size)
            //label 만 따로 빼서 새로운 1차원 클래스를 만든다. (0~3은 좌표값임)
            System.arraycopy(output[i], 4, classArray, 0, classes.size)
            //그 label 중에서 가장 값이 큰 값을 선정한다.
            for (j in classes.indices) {
                if (classArray[j] > maxClass) {
                    detectionClass = j
                    maxClass = classArray[j]
                }
            }

            //만약 그 확률 값이 특정 확률을 넘어서면 List 형태로 저장한다.
            if (maxClass > objectThresh) {
                val xPos = output[i][0]
                val yPos = output[i][1]
                val width = output[i][2]
                val height = output[i][3]
                //사각형은 화면 밖으로 나갈 수 없으니 화면을 넘기면 최대 화면 값을 가지게 한다.
                val rectF = RectF(
                    max(0f, xPos - width / 2), max(0f, yPos - height / 2),
                    min(INPUT_SIZE - 1f, xPos + width / 2), min(INPUT_SIZE - 1f, yPos + height / 2)
                )
                val result = Result(detectionClass, maxClass, rectF)
                results.add(result)
            }
        }
        return nms(results)
    }

    // 비 최대 억제비 : 겹치는 rect 객체를 하나로 축소
    private fun nms(results: ArrayList<Result>): ArrayList<Result> {
        val nmsList = ArrayList<Result>()

        for (i in classes.indices) {
            //1.클래스 (라벨들) 중에서 가장 높은 확률값을 가졌던 클래스 찾기
            val pq = PriorityQueue<Result>(50) { o1, o2 ->
                o1.score.compareTo(o2.score)
            }

            for (j in results.indices) {
                if (results[j].classIndex == i) {
                    pq.add(results[j])
                }
            }

            //NMS 처리
            while (pq.size > 0) {
                // 큐 안에 속한 최대 확률값을 가진 class 저장
                val detections = pq.toArray()
                val max = detections[0] as Result
                nmsList.add(max)
                pq.clear()

                //0번은 위에서 처음 넣었으니까 1번부터 시작
                for (k in 1 until detections.size) {
                    val detection = detections[k] as Result
                    val rectF = detection.rectF
                    val iouThresh = 0.5f // 50% 이상 교집합인 경우 추가하지않음. 즉 동일하다 판단하고 제거
                    if (boxIOU(max.rectF, rectF) < iouThresh) {
                        pq.add(detection)
                    }
                }
            }
        }
        return nmsList
    }

    // 겹치는 비율 (교집합/합집합)
    private fun boxIOU(a: RectF, b: RectF): Float {
        return boxIntersection(a, b) / boxUnion(a, b)
    }

    //교집합
    private fun boxIntersection(a: RectF, b: RectF): Float {
        val w = overlap(
            (a.left + a.right) / 2, a.right - a.left,
            (b.left + b.right) / 2, b.right - b.left
        )
        val h = overlap(
            (a.top + a.bottom) / 2, a.bottom - a.top,
            (b.top + b.bottom) / 2, b.bottom - b.top
        )
        return if (w < 0 || h < 0) 0f else w * h
    }

    // 합집합
    private fun boxUnion(a: RectF, b: RectF): Float {
        val i: Float = boxIntersection(a, b)
        return (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i
    }

    // 서로 겹치는 부분의 길이
    private fun overlap(x1: Float, w1: Float, x2: Float, w2: Float): Float {
        val l1 = x1 - w1 / 2
        val l2 = x2 - w2 / 2
        val left = max(l1, l2)
        val r1 = x1 + w1 / 2
        val r2 = x2 + w2 / 2
        val right = min(r1, r2)
        return right - left
    }
}