package com.photosolver.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private int urlIndex = 0;
    private final java.util.ArrayList<String> failures = new java.util.ArrayList<String>();

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
        updateTitle();
    }

    private boolean tryNextUrl() {
        if (urlIndex < urls().length - 1) {
            urlIndex++;
            loadCurrentUrl();
            return true;
        }
        return false;
    }

    /* 标题直接写清现在连的是哪儿：本机 / 线上版（线上的拍照传不到电脑） */
    private void updateTitle() {
        String host = urls()[urlIndex];
        if (host.indexOf("localhost") >= 0 || host.indexOf("192.168.") >= 0) {
            setTitle("错题本 · 本机（能传题）");
        } else {
            setTitle("错题本 · 线上版（拍照传不到电脑）");
        }
    }

    /* 两个地址都打不开时，给一个看得懂的提示页，而不是白屏 */
    private void showFailScreen(String detail) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append("两个地址都打不开\n\n");
                for (int i = 0; i < urls().length; i++) {
                    sb.append((i == urlIndex ? "× 失败：" : "· 待试：")).append(urls()[i]).append("\n");
                }
                sb.append("\n");
                sb.append(detail == null ? "" : ("错误：" + detail + "\n\n"));
                sb.append("检查：\n")
                  .append("1. 电脑上「启动错题本服务.bat」的黑窗口开着吗\n")
                  .append("2. 手机和电脑连的是同一个 WiFi 吗（这台电脑是 CMCC-khTN-5G）\n")
                  .append("3. 手机浏览器打开 http://192.168.1.171:8765 能开吗\n");
                TextView tv = new TextView(MainActivity.this);
                tv.setText(sb.toString());
                tv.setTextSize(16f);
                tv.setPadding(24, 24, 24, 24);
                tv.setTextColor(Color.WHITE);
                tv.setBackgroundColor(Color.rgb(20, 20, 24));
                setContentView(tv);
            }
        });
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
                failures.add(errCode + " " + description);
                if (!tryNextUrl()) {
                    showFailScreen(failures.isEmpty() ? null : failures.get(0));
                }
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                updateTitle();
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
