package com.mysterytech.amarhisab.nativeapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int NATIVE_IMG_REQ = 1002;
    private static final int NATIVE_JSON_REQ = 1003;
    private ServerSocket localServer;

    public class AndroidBridge {
        @JavascriptInterface
        public void startGoogleLogin() {
            String authUrl = "https://amar-hisab-89144.firebaseapp.com/__/auth/handler?apiKey=AIzaSyDummy&appName=%5BDEFAULT%5D&authType=signInViaRedirect&redirectUrl=amarhisab%3A%2F%2Fauth&providerId=google.com";
            // সরাসরি আপনার লাইভ সাইটে নেটিভ রিডাইরেক্ট প্যারামিটারসহ ওপেন করা
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:18944/index.html?native_auth=1"));
            startActivity(i);
        }

        @JavascriptInterface
        public void openImagePicker() {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Select Photo"), NATIVE_IMG_REQ);
        }

        @JavascriptInterface
        public void openJsonPicker() {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(Intent.createChooser(i, "Select Backup JSON"), NATIVE_JSON_REQ);
        }

        @JavascriptInterface
        public void saveBase64File(String base64Data, final String filename) {
            try {
                String pure = base64Data.contains(",") ? base64Data.split(",")[1] : base64Data;
                byte[] bytes = Base64.decode(pure, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put("_display_name", filename);
                    v.put("mime_type", "application/json");
                    v.put("relative_path", Environment.DIRECTORY_DOWNLOADS);
                    Uri u = getContentResolver().insert(MediaStore.Files.getContentUri("external"), v);
                    if (u != null) {
                        OutputStream os = getContentResolver().openOutputStream(u);
                        if (os != null) { os.write(bytes); os.close(); }
                    }
                } else {
                    FileOutputStream fos = new FileOutputStream(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename));
                    fos.write(bytes); fos.close();
                }
                runOnUiThread(new Runnable() { public void run() { Toast.makeText(getApplicationContext(), "Saved: " + filename, Toast.LENGTH_LONG).show(); } });
            } catch (final Exception e) {
                runOnUiThread(new Runnable() { public void run() { Toast.makeText(getApplicationContext(), "Save error: " + e.getMessage(), Toast.LENGTH_LONG).show(); } });
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            Window w = getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(0xFF16A34A);
        }
        startLocalAssetServer();
        webView = new WebView(this);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { return true; } });
        setContentView(webView);

        final WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                final WebView popup = new WebView(MainActivity.this);
                WebSettings ps = popup.getSettings();
                ps.setJavaScriptEnabled(true);
                ps.setDomStorageEnabled(true);
                ps.setJavaScriptCanOpenWindowsAutomatically(true);
                ps.setSupportMultipleWindows(true);
                ps.setUserAgentString(s.getUserAgentString());
                CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true);

                final Dialog dlg = new Dialog(MainActivity.this, android.R.style.Theme_NoTitleBar);
                dlg.setContentView(popup, new ViewGroup.LayoutParams(-1, -1));
                dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface d) { popup.destroy(); }
                });
                popup.setWebViewClient(new WebViewClient());
                popup.setWebChromeClient(new WebChromeClient() {
                    @Override public void onCloseWindow(WebView w) { dlg.dismiss(); }
                });
                dlg.show();
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
        webView.loadUrl("http://localhost:18944/index.html");
        handleDeepLink(getIntent());
    }

    private void startLocalAssetServer() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    localServer = new ServerSocket(18944);
                    while (!localServer.isClosed()) {
                        final Socket client = localServer.accept();
                        new Thread(new Runnable() { public void run() { handleClient(client); } }).start();
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void handleClient(Socket client) {
        try {
            InputStream in = client.getInputStream();
            byte[] buf = new byte[2048];
            int len = in.read(buf);
            if (len <= 0) { client.close(); return; }
            String req = new String(buf, 0, len), path = "index.html";
            String[] lines = req.split("\r\n");
            if (lines.length > 0 && lines[0].startsWith("GET ")) {
                String raw = lines[0].split(" ")[1];
                int q = raw.indexOf('?');
                if (q != -1) raw = raw.substring(0, q);
                if (raw.startsWith("/")) raw = raw.substring(1);
                if (!raw.isEmpty()) path = raw;
            }
            InputStream assetIn = getAssets().open(path);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096]; int n;
            while ((n = assetIn.read(chunk)) != -1) baos.write(chunk, 0, n);
            assetIn.close();
            byte[] body = baos.toByteArray();
            String mime = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : path.endsWith(".json") ? "application/json" : path.endsWith(".png") ? "image/png" : path.endsWith(".svg") ? "image/svg+xml" : "text/html; charset=utf-8";
            OutputStream out = client.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + mime + "\r\nCross-Origin-Opener-Policy: same-origin-allow-popups\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes());
            out.write(body); out.flush(); client.close();
        } catch (Exception e) { try { client.close(); } catch (Exception ignored) {} }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == NATIVE_IMG_REQ || requestCode == NATIVE_JSON_REQ) && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            try {
                InputStream is = getContentResolver().openInputStream(data.getData());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] b = new byte[4096]; int n;
                while ((n = is.read(b)) != -1) baos.write(b, 0, n);
                is.close();
                final String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                final boolean isImg = (requestCode == NATIVE_IMG_REQ);
                webView.post(new Runnable() {
                    public void run() {
                        if (isImg) webView.evaluateJavascript("window.onNativeImagePicked('data:image/jpeg;base64," + b64 + "')", null);
                        else webView.evaluateJavascript("window.onNativeJsonPicked('" + b64 + "')", null);
                    }
                });
            } catch (Exception ignored) {}
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null && "amarhisab".equals(data.getScheme())) {
            final String token = data.getQueryParameter("token");
            if (token != null && !token.isEmpty() && webView != null) {
                webView.postDelayed(new Runnable() {
                    public void run() {
                        webView.evaluateJavascript("window.onNativeGoogleToken('" + token + "')", null);
                    }
                }, 400);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    @Override public void onBackPressed() { if (webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { try { if (localServer != null) localServer.close(); } catch (Exception ignored) {} super.onDestroy(); }
}
