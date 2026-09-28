package com.photosolver.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    // 已发布的网页地址（WebView 加载它，从而复用里面的拍照解题 + 错题本功能）
    private static final String APP_URL = "https://photo-solver-79959.app.workbuddy.host/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);

        webView.setWebViewClient(new WebViewClient());

        // 处理摄像头权限请求（getUserMedia 视频流 = RESOURCE_VIDEO_CAPTURE）
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        java.util.ArrayList<String> granted = new java.util.ArrayList<String>();
                        for (String r : request.getResources()) {
                            if (r.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                                granted.add(r);
                            }
                        }
                        if (granted.isEmpty()) {
                            request.deny();
                        } else {
                            request.grant(granted.toArray(new String[0]));
                        }
                    }
                });
            }
        });

        webView.loadUrl(APP_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
