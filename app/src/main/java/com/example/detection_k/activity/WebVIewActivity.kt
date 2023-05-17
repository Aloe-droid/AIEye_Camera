package com.example.detection_k.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import com.example.detection_k.R

class WebVIewActivity : AppCompatActivity() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var webViewActivity: Activity
    }

    init {
        webViewActivity = this@WebVIewActivity
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        webView = findViewById(R.id.webView)

        //자동꺼짐 해제
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        webView.webViewClient = object : WebViewClient() {
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler?, error: SslError?
            ) {
                // https 가 아닌 http 에서도 사용 가능하게 ssl 인증서 에러 무시
                handler!!.proceed()
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {

                if(view?.settings?.mediaPlaybackRequiresUserGesture == true){
                    view.settings.mediaPlaybackRequiresUserGesture = false
                }

                super.onPageStarted(view, url, favicon)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // 웹뷰에서 사용하는 각종 권한 요청수락
            override fun onPermissionRequest(request: PermissionRequest?) {
                request!!.grant(request.resources)
            }
        }

        val webViewSettings = webView.settings
        //웹뷰에서 재생가능한 콘텐츠를 자동으로 재생할 수 있도록 설정
        webViewSettings.mediaPlaybackRequiresUserGesture = false
        //자바 스크립트를 쓸수 있게
        webViewSettings.javaScriptEnabled = true
        // 웹뷰 내부 DB 사용 가능
        webViewSettings.databaseEnabled = true
        // 캐시 사용 안함
        webViewSettings.cacheMode = WebSettings.LOAD_NO_CACHE

        val url = intent.getStringExtra("url")!!
        Log.d("url", url)

        webView.loadUrl(url)

    }


    //뒤로가기 버튼이 웹에서도 적용되게 변경
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        //해당 액티비티가 실행중이 아니라면 스트리밍도 일시정지
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.webViewClient = object : WebViewClient(){}
        webView.clearCache(true)
        webView.clearHistory()
        webView.destroy()
        super.onDestroy()
    }
}