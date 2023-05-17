package com.example.detection_k.supportYolo

import android.graphics.RectF

//결과값을 모아 놓은 클래스 (Index = 번호, Score = 점수, 확률, RectF = 검출된 객체의 사각형
class Result(val classIndex: Int, val score: Float, val rectF: RectF)