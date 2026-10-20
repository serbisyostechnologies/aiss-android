package com.serbisyos.ui;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.net.URL;
import java.util.Locale;
import android.content.Intent;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebAppInterface {

    private final Context context;
    private final ExecutorService shareExecutor =
            Executors.newSingleThreadExecutor();

    public WebAppInterface(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void downloadImage(String fileUrl, String fileName) {
        try {
            URL parsedUrl = new URL(fileUrl);

            // Allow HTTPS downloads only.
            if (!"https".equalsIgnoreCase(parsedUrl.getProtocol())) {
                showToast("Invalid download URL");
                return;
            }

            String host = parsedUrl.getHost().toLowerCase(Locale.ROOT);

            // Allow your website and Cloudinary only.
            if (!host.equals("www.aiserbisyosstudio.com")
                    && !host.equals("aiserbisyosstudio.com")
                    && !host.equals("res.cloudinary.com")) {
                showToast("Download host not allowed");
                return;
            }

            String safeFileName = sanitizeFileName(fileName);

            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(fileUrl));

            request.setTitle(safeFileName);
            request.setDescription("Downloading from AISerbisyosStudio");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    safeFileName
            );

            DownloadManager manager =
                    (DownloadManager) context.getSystemService(
                            Context.DOWNLOAD_SERVICE
                    );

            if (manager == null) {
                showToast("Download service unavailable");
                return;
            }

            manager.enqueue(request);
            showToast("Download started");

        } catch (Exception e) {
            showToast("Unable to start download");
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "aistudio_download.png";
        }

        // Prevent directory traversal and invalid filename characters.
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (safe.equals(".") || safe.equals("..")) {
            return "aistudio_download.png";
        }

        if (safe.length() > 150) {
            safe = safe.substring(0, 150);
        }

        return safe;
    }

    private void showToast(String message) {
        android.os.Handler handler = new android.os.Handler(
                android.os.Looper.getMainLooper()
        );

        handler.post(() ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        );
    }

    @JavascriptInterface
    public void shareImage(String imageUrl, String fileName) {

        shareExecutor.execute(() -> {
            File imageFile = null;
            HttpURLConnection connection = null;

            try {
                URL url = new URL(imageUrl);

                if (!"https".equalsIgnoreCase(url.getProtocol())) {
                    throw new Exception("Only HTTPS URLs are allowed");
                }

                String host = url.getHost().toLowerCase(Locale.ROOT);

                if (!host.equals("www.aiserbisyosstudio.com")
                        && !host.equals("aiserbisyosstudio.com")
                        && !host.equals("res.cloudinary.com")) {
                    throw new Exception("Untrusted image host");
                }

                String safeName = sanitizeFileName(fileName);

                if (!safeName.matches(
                        "(?i).+\\.(png|jpe?g|webp|gif)"
                )) {
                    safeName = "aistudio_shared_image.png";
                }

                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");

                int status = connection.getResponseCode();

                if (status < 200 || status >= 300) {
                    throw new Exception("Unable to retrieve image");
                }

                String contentType = connection.getContentType();

                if (contentType == null
                        || !contentType.toLowerCase(Locale.ROOT)
                        .startsWith("image/")) {
                    throw new Exception("URL does not return an image");
                }

                File shareDir = new File(
                        context.getCacheDir(),
                        "shared_images"
                );

                if (!shareDir.exists() && !shareDir.mkdirs()) {
                    throw new Exception("Unable to create cache directory");
                }

                imageFile = new File(shareDir, safeName);

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output =
                             new FileOutputStream(imageFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    long maxBytes = 30L * 1024 * 1024;

                    while ((bytesRead = input.read(buffer)) != -1) {
                        totalBytes += bytesRead;

                        if (totalBytes > maxBytes) {
                            throw new Exception("Image is too large to share");
                        }

                        output.write(buffer, 0, bytesRead);
                    }
                }

                Uri imageUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        imageFile
                );

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType(contentType);
                shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                shareIntent.addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                Intent chooser = Intent.createChooser(
                        shareIntent,
                        "Share image"
                );

                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                context.startActivity(chooser);

            } catch (Exception e) {
                showToast("Unable to share image. Please try again.");

                if (imageFile != null && imageFile.exists()) {
                    // Remove a partial file after a failed download.
                    imageFile.delete();
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}