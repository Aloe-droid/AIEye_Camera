package com.example.detection_k.bluetooth

import android.app.Dialog
import android.content.Context
import android.view.Window
import com.example.detection_k.R

class ProgressDialog(context: Context) : Dialog(context) {
    init {
        //다이어로그 제목은 안보이게
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_progress)
    }
}
