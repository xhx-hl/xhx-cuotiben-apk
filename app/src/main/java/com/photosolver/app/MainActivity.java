package com.photosolver.app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private int urlIndex = 0;

    /* 第一个：这台电脑上「启动错题本服务.bat」开的服务（手机连同一个 WiFi 才能用，拍照能直传电脑）。
       第二个：线上发布版，只在连不上本机服务时兜底（它是纯静态，拍照只能存本机、传不到电脑）。 */
    private String[] urls() {
        return new String[]{
                BuildConfig.APP_LAN_URL,
                "https://photo-solver-79959.app.workbuddy.host/"
        };
    }

    private void loadCurrentUrl() {
        webView.loadUrl(urls()[urlIndex]);
    }

    private boolean tryNextUrl() {
        if (urlIndex < urls().length - 1) {
            urlIndex++;
            loadCurrentUrl();
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);

        // 本机服务打不开就自动依次试下一个地址（IP 变了也不至于白屏）
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, int errCode, String description, String failingUrl) {
                if (!tryNextUrl()) {
                    super.onReceivedError(v, errCode, description, failingUrl);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                v.loadUrl(url);
                return true;
            }
        });

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

        loadCurrentUrl();
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
