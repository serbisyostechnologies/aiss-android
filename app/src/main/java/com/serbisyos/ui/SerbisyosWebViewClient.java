package com.serbisyos.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class SerbisyosWebViewClient extends WebViewClient {

    private Activity activity;

    public SerbisyosWebViewClient(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        if (!url.startsWith("https") || !url.startsWith("http")) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(request.getUrl().toString()));
                activity.startActivityForResult(i, 2001);
            } catch (ActivityNotFoundException ignored) {

            }
        }
        return true;
    }
}
