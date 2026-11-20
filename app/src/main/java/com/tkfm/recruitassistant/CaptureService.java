package com.tkfm.recruitassistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot screen capture.  A foreground service is required before a MediaProjection
 * token may be used on modern Android; the service captures exactly one frame and stops.
 */
public class CaptureService extends Service {

    public static final String ACTION_CAPTURE_DONE = "com.tkfm.recruitassistant.CAPTURE_DONE";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_DELAY = "delay";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "data";

    private static final String CHANNEL = "capture";
    private static final int NOTIFICATION_ID = 7101;
    private static final int DONE_NOTIFICATION_ID = 7102;

    private String source = "auto";
    private long captureDelayMs;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicBoolean cleaning = new AtomicBoolean(false);
    private int framesSeen;
    private long startedAt;

    public static void start(Context context, int resultCode, Intent data) {
        start(context, resultCode, data, "auto");
    }

    public static void start(Context context, int resultCode, Intent data, String source) {
        start(context, resultCode, data, source, 0);
    }

    public static void start(Context context, int resultCode, Intent data, String source,
                             long delayMs) {
        Intent intent = new Intent(context, CaptureService.class);
        intent.putExtra(EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(EXTRA_DATA, data);
        intent.putExtra(EXTRA_SOURCE, source);
        intent.putExtra(EXTRA_DELAY, delayMs);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("capture");
        thread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground(null);
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        source = intent.getStringExtra(EXTRA_SOURCE) == null ? "auto"
                : intent.getStringExtra(EXTRA_SOURCE);
        captureDelayMs = Math.max(0, intent.getLongExtra(EXTRA_DELAY, 0));
        if (data == null) {
            fail("no projection token");
            return START_NOT_STICKY;
        }
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        try {
            projection = manager.getMediaProjection(resultCode, data);
        } catch (Exception e) {
            fail(e.getMessage());
            return START_NOT_STICKY;
        }
        if (projection == null) {
            fail("projection unavailable");
            return START_NOT_STICKY;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Rect bounds = wm.getMaximumWindowMetrics().getBounds();
            metrics.widthPixels = bounds.width();
            metrics.heightPixels = bounds.height();
            metrics.densityDpi = getResources().getDisplayMetrics().densityDpi;
        } else {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        }
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        framesSeen = 0;
        final Handler handler = new Handler(thread.getLooper());

        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                cleanup();
            }
        }, handler);

        final int fw = width;
        final int fh = height;
        final int dpi = metrics.densityDpi;
        // Wait until the user has switched to the game before creating the virtual
        // display. The foreground service was already started while we were visible.
        handler.postDelayed(() -> beginCapture(fw, fh, dpi, handler), captureDelayMs);
        return START_NOT_STICKY;
    }

    private void beginCapture(int width, int height, int dpi, Handler handler) {
        if (finished.get() || cleaning.get() || projection == null) return;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        display = projection.createVirtualDisplay("tkfm-capture", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null,
                handler);
        startedAt = System.currentTimeMillis();
        reader.setOnImageAvailableListener(r -> captureFrame(r, width, height), handler);
        handler.postDelayed(() -> {
            if (!finished.get()) fail("capture timeout");
        }, 8000);
    }

    private void captureFrame(ImageReader imageReader, int width, int height) {
        if (finished.getAndSet(true)) return;
        Bitmap bitmap = null;
        try (Image image = imageReader.acquireLatestImage()) {
            if (image == null) {
                finished.set(false);
                return;
            }
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height,
                    Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            bitmap = Bitmap.createBitmap(padded, 0, 0, width, height);
            if (padded != bitmap) padded.recycle();
        } catch (Exception e) {
            fail(e.getMessage());
            return;
        }
        if (bitmap == null) {
            fail("empty frame");
            return;
        }
        // A fresh virtual display can emit one blank frame before the mirror starts.
        framesSeen++;
        if (framesSeen <= 1 || looksBlack(bitmap)) {
            bitmap.recycle();
            finished.set(false);
            return;
        }
        String path = save(bitmap);
        bitmap.recycle();
        if (path == null) {
            fail("unable to save capture");
            return;
        }
        notifyDone(path);
        Intent done = new Intent(ACTION_CAPTURE_DONE);
        done.setPackage(getPackageName());
        done.putExtra(EXTRA_PATH, path);
        done.putExtra(EXTRA_SOURCE, source);
        sendBroadcast(done);
        cleanup();
        stopSelf();
    }

    private boolean looksBlack(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                int px = bitmap.getPixel(Math.min(w - 1, x * w / 8),
                        Math.min(h - 1, y * h / 8));
                int r = (px >> 16) & 0xFF;
                int g = (px >> 8) & 0xFF;
                int b = px & 0xFF;
                if (r > 10 || g > 10 || b > 10) return false;
            }
        }
        return true;
    }

    private String save(Bitmap bitmap) {
        try {
            File dir = new File(getCacheDir(), "captures");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void fail(String message) {
        if (finished.getAndSet(true)) return;
        notifyDone(null);
        Intent done = new Intent(ACTION_CAPTURE_DONE);
        done.setPackage(getPackageName());
        done.putExtra(EXTRA_ERROR, message == null ? "unknown" : message);
        done.putExtra(EXTRA_SOURCE, source);
        sendBroadcast(done);
        cleanup();
        stopSelf();
    }

    private void startInForeground(String path) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "屏幕识别",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        if (path != null) open.putExtra(MainActivity.EXTRA_CAPTURE_PATH, path);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle("全境征才助手")
                .setContentText(path == null ? "正在截取屏幕…" : "识别已完成，点击查看")
                .setContentIntent(pending)
                .setOngoing(path == null)
                .setOnlyAlertOnce(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void notifyDone(String path) {
        startInForeground(path);
        if (path != null) {
            try {
                NotificationManagerCompat.from(this).notify(DONE_NOTIFICATION_ID,
                        buildDoneNotification(path));
            } catch (SecurityException ignored) {
            }
        }
    }

    private Notification buildDoneNotification(String path) {
        Intent open = new Intent(this, MainActivity.class);
        open.putExtra(MainActivity.EXTRA_CAPTURE_PATH, path);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle("识别完成")
                .setContentText("已截取屏幕，点击查看识别结果")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
    }

    private void cleanup() {
        if (!cleaning.compareAndSet(false, true)) return;
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (display != null) {
            display.release();
            display = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        cleanup();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
