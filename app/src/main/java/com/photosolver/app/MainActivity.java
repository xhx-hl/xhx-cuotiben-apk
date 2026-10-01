package com.photosolver.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final int REQUEST_SELECT_FILE = 1;

    private WebView webView;
    private int urlIndex = 0;
    private final java.util.ArrayList<String> failures = new java.util.ArrayList<String>();
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;

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

            /* 页面里的「拍照 / 从相册选」按钮就是 <input type="file">，
               必须由这里把系统相机/相册调起来 —— 不实现它，按钮点了完全没反应。 */
            @Override
            public boolean onShowFileChooser(final WebView webView,
                    final ValueCallback<Uri[]> cb, FileChooserParams fileChooserParams) {
                try {
                    if (filePathCallback != null) {
                        filePathCallback.onReceiveValue(null);
                    }
                    filePathCallback = cb;
                    cameraImageUri = makeCameraUri();
                    Intent intent = fileChooserParams.createIntent();
                    // 自己准备一个输出位置：相机拍完有些机型返回的 data 是 null，靠这个 Uri 取回照片
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "打不开相机：" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        loadCurrentUrl();
    }

    /* 在系统相册里占一个位置放刚拍的照片，页面才能读得到 */
    private Uri makeCameraUri() {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.TITLE, "cuotiban_capture.jpg");
        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        cv.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Cuotiben");
        } else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        }
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
    }

    /* 相机/相册关掉之后，把选到的图片交回给页面 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SELECT_FILE || filePathCallback == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (data != null) {
                String path = data.getDataString();
                if (path != null) {
                    results = new Uri[]{ Uri.parse(path) };
                }
            }
            // 用系统相机拍的时候 data 常常是 null，就用我们自己准备的 Uri
            if (results == null && cameraImageUri != null) {
                results = new Uri[]{ cameraImageUri };
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        cameraImageUri = null;
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
