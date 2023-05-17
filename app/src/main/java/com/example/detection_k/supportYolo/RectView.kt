package com.example.detection_k.supportYolo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.round

class RectView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {
    lateinit var classes: Array<String>

    // 불과 연기에 대한 Map
    private val fireMap = HashMap<RectF, String>()
    private val smokeMap = HashMap<RectF, String>()

    // 색상 지정
    private val firePaint = Paint().apply {
        this.style = Paint.Style.STROKE    //빈 사각형 그림
        this.strokeWidth = 10.0f           //굵기 10
        this.color = Color.RED             //빨간색
        this.strokeCap = Paint.Cap.ROUND   //끝을 뭉특하게
        this.strokeJoin = Paint.Join.ROUND //끝 주위를 뭉특하게
        this.strokeMiter = 100f            //뭉특한 정도 100도
    }
    private val smokePaint = Paint().apply {
        this.style = Paint.Style.STROKE
        this.strokeWidth = 10.0f
        this.color = Color.GRAY
        this.strokeCap = Paint.Cap.ROUND
        this.strokeJoin = Paint.Join.ROUND
        this.strokeMiter = 100f
    }
    private val textPaint = Paint().apply {
        this.textSize = 60f
        this.color = Color.WHITE
    }

    //RectF의 크기를 화면의 크기에 맞게 수정한다.
    fun transFormRect(results: ArrayList<Result>): ArrayList<Result> {
        //핸드폰 기종에 따라 PreviewView 의 크기는 변함.
        val scaleX = width / ProcessOnnx.INPUT_SIZE.toFloat()
        val scaleY = scaleX * 9f / 16f
        val realY = width * 9f / 16f
        val diffY = realY - height

        results.forEach {
            it.rectF.left *= scaleX
            it.rectF.right *= scaleX
            it.rectF.top = it.rectF.top * scaleY - (diffY / 2f)
            it.rectF.bottom = it.rectF.bottom * scaleY - (diffY / 2f)
        }

        //전면 카메라의 경우 좌우 반전이 되어있다. 따라서 좌표값도 좌우 반전해야한다.
        results.forEach {
            // 좌우 반전
            val temp = it.rectF.left
            it.rectF.left = width - it.rectF.right
            it.rectF.right = width - temp
        }
        return results
    }

    //초기화
    fun clear() {
        fireMap.clear()
        smokeMap.clear()
    }

    // Result 가 담긴 리스트들을 받아와서 각각의 해시맵 (fireMap, smokeMap)에 담는다.
    fun resultToList(results: ArrayList<Result>) {
        results.forEach {
            if (it.classIndex == 0) {
                fireMap[it.rectF] = classes[0] + ", " + round(it.score * 100) + "%"
            }else{
                smokeMap[it.rectF] = classes[1] + ", " + round(it.score * 100) + "%"
            }
        }
    }

    override fun onDraw(canvas: Canvas?) {
        //각 값에 맞게 그림을 그린다.
        fireMap.forEach {
            canvas!!.drawRect(it.key, firePaint)
            canvas.drawText(it.value, it.key.left + 10, it.key.top + 60, textPaint)
        }

        smokeMap.forEach {
            canvas!!.drawRect(it.key, smokePaint)
            canvas.drawText(it.value, it.key.left + 10, it.key.top + 60, textPaint)
        }
        super.onDraw(canvas)
    }

}