package com.deepseek.chatbot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int FILE_CHOOSER = 1001;
    private static final int MICROPHONE_PERMISSION = 1002;
    private static final String START_URL = "file:///android_asset/chat/index.html";

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ApiKeyStore apiKeyStore;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;
    private long lastBackAt;
    private Toast exitToast;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiKeyStore = new ApiKeyStore(this);
        SystemUi.applyTheme(this, SystemUi.isDark(this));
        if (apiKeyStore.get() == null) {
            showKeySetup();
        } else {
            showChat();
        }
    }

    private void showKeySetup() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(28), dp(32), dp(28), dp(32));

        TextView title = text("使用 DeepSeek Chatbot", 30, SystemUi.primaryTextColor(this));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = text("第一步：输入你的 API Key。验证通过后即可开始对话。", 16, SystemUi.secondaryTextColor(this));
        subtitle.setPadding(0, dp(14), 0, dp(30));
        content.addView(subtitle);

        EditText input = new EditText(this);
        input.setHint("sk-...");
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        content.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView error = text("", 14, Color.rgb(197, 48, 48));
        error.setPadding(0, dp(8), 0, dp(4));
        error.setVisibility(View.GONE);
        content.addView(error);

        Button submit = new Button(this);
        submit.setText("验证并开始使用");
        submit.setTextSize(16);
        content.addView(submit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        ProgressBar progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(12);
        content.addView(progress, progressParams);

        Button obtainKey = new Button(this);
        obtainKey.setText(R.string.open_platform);
        obtainKey.setTextSize(14);
        obtainKey.setOnClickListener(view -> openExternal(Uri.parse("https://platform.deepseek.com/api_keys")));
        content.addView(obtainKey, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView privacy = text("密钥仅在本机加密保存。对话、附件及语音内容会直接发送给 DeepSeek；本应用不经过第三方服务器。", 13, Color.rgb(111, 118, 133));
        privacy.setPadding(0, dp(20), 0, 0);
        content.addView(privacy);

        submit.setOnClickListener(view -> {
            String key = input.getText().toString().trim();
            if (key.isEmpty()) {
                error.setText(R.string.api_key_required);
                error.setVisibility(View.VISIBLE);
                return;
            }
            submit.setEnabled(false);
            input.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            error.setVisibility(View.GONE);
            verifyKey(key, failure -> {
                if (failure == null && apiKeyStore.set(key)) {
                    showChat();
                } else {
                    submit.setEnabled(true);
                    input.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    error.setText(failure == null ? "无法安全保存密钥，请重试" : failure);
                    error.setVisibility(View.VISIBLE);
                }
            });
        });

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        SystemUi.installInsets(scrollView);
        setContentView(scrollView);
    }

    private void verifyKey(String key, KeyVerificationCallback callback) {
        executor.execute(() -> {
            String failure = DeepSeekApi.validateApiKey(key);
            runOnUiThread(() -> {
                if (!destroyed) {
                    callback.onComplete(failure);
                }
            });
        });
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void showChat() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        webView = new WebView(this);
        webView.setBackgroundColor(SystemUi.backgroundColor(SystemUi.isDark(this)));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        webView.addJavascriptInterface(new AndroidBridge(), "DeepSeekAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme()) || "about".equals(uri.getScheme())) {
                    return false;
                }
                openExternal(uri);
                return true;
            }
        });
        webView.setWebChromeClient(new ChatChromeClient());
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        SystemUi.installInsets(root);
        setContentView(root);
        webView.loadUrl(START_URL);
    }

    private void showChangeKeyDialog() {
        EditText input = new EditText(this);
        input.setHint("输入新的 API Key");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        int padding = dp(20);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(padding, dp(8), padding, 0);
        container.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("更换 API Key")
                .setMessage("新密钥验证通过后才会替换当前密钥。")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("验证并更换", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setOnClickListener(view -> {
                String key = input.getText().toString().trim();
                if (key.isEmpty()) {
                    input.setError("请输入 API Key");
                    return;
                }
                confirm.setEnabled(false);
                input.setEnabled(false);
                verifyKey(key, failure -> {
                    if (failure == null && apiKeyStore.set(key)) {
                        dialog.dismiss();
                        Toast.makeText(this, "API Key 已更换", Toast.LENGTH_SHORT).show();
                        if (webView != null) {
                            webView.reload();
                        }
                    } else {
                        confirm.setEnabled(true);
                        input.setEnabled(true);
                        input.setError(failure == null ? "无法安全保存密钥" : failure);
                    }
                });
            });
        });
        dialog.show();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.2f);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openExternal(Uri uri) {
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "没有可打开链接的应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER || fileCallback == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int index = 0; index < count; index++) {
                    results[index] = data.getClipData().getItemAt(index).getUri();
                }
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(results);
        fileCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MICROPHONE_PERMISSION || pendingWebPermission == null) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingWebPermission.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            pendingWebPermission.deny();
            Toast.makeText(this, "语音输入需要麦克风权限", Toast.LENGTH_SHORT).show();
        }
        pendingWebPermission = null;
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            confirmExit();
            return;
        }
        webView.evaluateJavascript("window.DeepSeekHandleBack?.() === true", result -> {
            if (destroyed || "true".equals(result)) {
                return;
            }
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                confirmExit();
            }
        });
    }

    private void confirmExit() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastBackAt <= 2000) {
            if (exitToast != null) {
                exitToast.cancel();
            }
            finish();
            return;
        }
        lastBackAt = now;
        if (exitToast != null) {
            exitToast.cancel();
        }
        exitToast = Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT);
        exitToast.show();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        SystemUi.applyTheme(this, SystemUi.isDark(this));
        if (webView != null) {
            webView.setBackgroundColor(SystemUi.backgroundColor(SystemUi.isDark(this)));
            webView.evaluateJavascript("window.dispatchEvent(new Event('deepseek-system-theme'))", null);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (pendingWebPermission != null) {
            pendingWebPermission.deny();
            pendingWebPermission = null;
        }
        if (webView != null) {
            webView.destroy();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void exportArchive(String filename, String encoded) {
        executor.execute(() -> {
            String safeName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String location;
            try {
                byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DeepSeek Chat");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    ContentResolver resolver = getContentResolver();
                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) {
                        throw new IllegalStateException("Cannot create download");
                    }
                    try (OutputStream output = resolver.openOutputStream(uri)) {
                        if (output == null) {
                            throw new IllegalStateException("Cannot open download");
                        }
                        output.write(bytes);
                    } catch (Exception error) {
                        resolver.delete(uri, null, null);
                        throw error;
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                    location = "系统下载目录 / DeepSeek Chat";
                } else {
                    File directory = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "exports");
                    if (!directory.exists() && !directory.mkdirs()) {
                        throw new IllegalStateException("Cannot create export directory");
                    }
                    File outputFile = new File(directory, safeName);
                    try (OutputStream output = new FileOutputStream(outputFile)) {
                        output.write(bytes);
                    }
                    location = outputFile.getAbsolutePath();
                }
                String completedLocation = location;
                runOnUiThread(() -> Toast.makeText(this, "历史已导出到：" + completedLocation, Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "导出失败，请重试", Toast.LENGTH_LONG).show());
            }
        });
    }

    private final class ChatChromeClient extends WebChromeClient {
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
            WebView popup = new WebView(MainActivity.this);
            popup.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView popupView, android.webkit.WebResourceRequest request) {
                    openExternal(request.getUrl());
                    popupView.destroy();
                    return true;
                }
            });
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(null);
            }
            fileCallback = callback;
            Intent intent = params.createIntent();
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            try {
                startActivityForResult(intent, FILE_CHOOSER);
                return true;
            } catch (ActivityNotFoundException error) {
                fileCallback = null;
                callback.onReceiveValue(null);
                Toast.makeText(MainActivity.this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            boolean audioRequested = false;
            for (String resource : request.getResources()) {
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                    audioRequested = true;
                }
            }
            if (!audioRequested) {
                request.deny();
                return;
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                if (pendingWebPermission != null) {
                    pendingWebPermission.deny();
                }
                pendingWebPermission = request;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION);
            }
        }
    }

    private interface KeyVerificationCallback {
        void onComplete(String failure);
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public String getApiKey() {
            String key = apiKeyStore.get();
            return key == null ? "" : key;
        }

        @JavascriptInterface
        public boolean setApiKey(String key) {
            return key != null && !key.trim().isEmpty() && apiKeyStore.set(key.trim());
        }

        @JavascriptInterface
        public void clearApiKey() {
            apiKeyStore.clear();
        }

        @JavascriptInterface
        public String getSystemTheme() {
            return SystemUi.isDark(MainActivity.this) ? "dark" : "light";
        }

        @JavascriptInterface
        public void setTheme(String theme) {
            boolean dark = "dark".equals(theme);
            runOnUiThread(() -> {
                if (!destroyed) {
                    SystemUi.applyTheme(MainActivity.this, dark);
                    if (webView != null) {
                        webView.setBackgroundColor(SystemUi.backgroundColor(dark));
                    }
                }
            });
        }

        @JavascriptInterface
        public void exportFile(String filename, String base64) {
            if (filename != null && base64 != null) {
                exportArchive(filename, base64);
            }
        }
    }
}
