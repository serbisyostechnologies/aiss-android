package com.serbisyos.ui;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.serbisyos.ui.databinding.ActivityMainBinding;

public class AISerbisyosStudioActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://www.aiserbisyosstudio.com/";
    private static final String ALLOWED_HOST = "aiserbisyosstudio.com";
    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private View errorLayout;
    private TextView errorMessage;
    private ValueCallback<Uri[]> fileUploadCallback;
    private String lastUrl = BASE_URL;
    private boolean pageLoadFailed = false;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.swipeRefresh, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        webView = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        errorLayout = findViewById(R.id.errorLayout);
        errorMessage = findViewById(R.id.errorMessage);

        webView.setWebViewClient(new SerbisyosWebViewClient(AISerbisyosStudioActivity.this));

        swipeRefresh.setOnRefreshListener(() -> {
            if (webView.getUrl() != null) {
                webView.reload();
            } else {
                webView.loadUrl(lastUrl);
            }
        });

        webView.setOnScrollChangeListener(
                (View v, int scrollX, int scrollY,
                 int oldScrollX, int oldScrollY) -> {
                    swipeRefresh.setEnabled(scrollY == 0);
                }
        );

        Button retryButton = findViewById(R.id.retryButton);

        configureWebView();

        retryButton.setOnClickListener(v -> {
            errorLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            pageLoadFailed = false;
            webView.loadUrl(lastUrl);
        });

        if (savedInstanceState != null) {
            lastUrl = savedInstanceState.getString(
                    "lastUrl", BASE_URL
            );

            webView.restoreState(savedInstanceState);

            if (webView.getUrl() == null) {
                webView.loadUrl(lastUrl);
            }
        } else {
            webView.loadUrl(BASE_URL);
        }

        configureBackButton();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {

        WebSettings settings = webView.getSettings();

        // Required for React and other modern web applications.
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Support websites with responsive layouts.
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // Allow normal website zoom behavior.
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Keep web content inside the WebView by default.
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        // Keep browser cookies enabled for website authentication.
        android.webkit.CookieManager.getInstance()
                .setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                String host = uri.getHost();

                if (scheme == null) {
                    return true;
                }

                // Only load HTTP(S) web URLs.
                if (scheme.equals("https") || scheme.equals("http")) {

                    // Never allow insecure HTTP for our own site.
                    if (scheme.equals("http")
                            && isOurDomain(host)) {
                        return true;
                    }

                    // Keep our website and its subdomains in-app.
                    if (isOurDomain(host)) {
                        return false;
                    }

                    // Open other websites in the external browser.
                    openExternalUrl(uri);
                    return true;
                }

                // Handle tel:, mailto:, sms: and similar links
                // with an appropriate external application.
                openExternalUrl(uri);
                return true;
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    Bitmap favicon) {

                super.onPageStarted(view, url, favicon);

                lastUrl = url;
                pageLoadFailed = false;

                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {

                super.onPageFinished(view, url);

                lastUrl = url;

                if (!pageLoadFailed) {
                    progressBar.setVisibility(View.GONE);
                    errorLayout.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error) {

                super.onReceivedError(view, request, error);

                // Ignore image, CSS, JS and other subresource errors.
                if (request.isForMainFrame()) {
                    showError(
                            "The website could not be reached. "
                                    + "Check your connection and try again."
                    );
                }
            }

            @Override
            public void onReceivedSslError(
                    WebView view,
                    SslErrorHandler handler,
                    SslError error) {

                // Never bypass certificate validation errors.
                handler.cancel();

                showError(
                        "A secure connection could not be established. "
                                + "Please try again later."
                );
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(
                    WebView view,
                    int newProgress) {

                super.onProgressChanged(view, newProgress);

                progressBar.setProgress(newProgress);

                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams) {

                // Cancel any previous unfinished selection.
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }

                fileUploadCallback = callback;

                try {
                    Intent intent =
                            fileChooserParams.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                } catch (ActivityNotFoundException exception) {
                    fileUploadCallback = null;
                    callback.onReceiveValue(null);

                    Toast.makeText(
                            AISerbisyosStudioActivity.this,
                            "No file picker is available.",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                return true;
            }
        });
        webView.addJavascriptInterface(
                new WebAppInterface(this),
                "Android"
        );
    }

    private boolean isOurDomain(String host) {
        if (host == null) {
            return false;
        }

        String normalizedHost = host.toLowerCase();

        return normalizedHost.equals(ALLOWED_HOST)
                || normalizedHost.endsWith("." + ALLOWED_HOST);
    }

    private void openExternalUrl(Uri uri) {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    uri
            );

            startActivity(intent);

        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    this,
                    "No application can open this link.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void showError(String message) {
        pageLoadFailed = true;

        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            webView.setVisibility(View.GONE);
            errorLayout.setVisibility(View.VISIBLE);
            errorMessage.setText(message);
        });
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == FILE_CHOOSER_REQUEST) {

            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {

                Uri selectedUri = data.getData();

                if (selectedUri != null) {
                    results = new Uri[]{selectedUri};
                } else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData()
                                .getItemAt(i).getUri();
                    }
                }
            }

            if (fileUploadCallback != null) {
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            }
        }
    }

    private void configureBackButton() {

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            finish();
                        }
                    }
                }
        );
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString("lastUrl", lastUrl);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {

        if (fileUploadCallback != null) {
            fileUploadCallback.onReceiveValue(null);
            fileUploadCallback = null;
        }

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }
}