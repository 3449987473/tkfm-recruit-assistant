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
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps one MediaProjection grant alive for the whole assistant session.
 *
 * <p>Android 14+ treats screen capture as a session.  Creating and releasing a
 * VirtualDisplay for every frame can make some OEM builds (notably realme/OPPO on
 * Android 15/16) end the session after the first capture, which forces the user to
 * re-authorize on every analysis.  This service therefore creates the VirtualDisplay
 * once, keeps the latest Image buffered, and hands a bitmap to the caller on demand.
 * Releasing the display only happens when the assistant is stopped.</p>
 */
public class ProjectionService extends Service {

    private static final String TAG = "ProjectionService";
    public static final String ACTION_START = "com.tkfm.recruitassistant.PROJECTION_START";
    public static final String ACTION_STOP = "com.tkfm.recruitassistant.PROJECTION_STOP";
    public static final String ACTION_STATE = "com.tkfm.recruitassistant.PROJECTION_STATE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_RUNNING = "running";

    private static final String CHANNEL = "assistant_projection";
    private static final int NOTIFICATION_ID = 7201;
    private static final long FRESH_FRAME_TIMEOUT_MS = 450;

    public interface CaptureCallback {
        void onCaptured(@Nullable Bitmap bitmap, @Nullable String error);
    }

    private static volatile ProjectionService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean capturing = new AtomicBoolean(false);

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private Image heldImage;
    private CaptureCallback pendingCallback;
    private boolean awaitFreshFrame;
    private int width;
    private int height;
    private int densityDpi;

    private final Runnable captureTimeout = () -> {
        if (pendingCallback == null) return;
        Log.w(TAG, "no fresh frame within " + FRESH_FRAME_TIMEOUT_MS + "ms, using buffered frame");
        awaitFreshFrame = false;
        if (heldImage != null) {
            deliverHeldFrame();
        } else {
            failCapture("抓屏超时：没有可用画面");
        }
    };

    public static boolean isRunning() {
        ProjectionService s = instance;
        return s != null && s.projection != null && s.display != null;
    }

    public static void start(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, ProjectionService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(EXTRA_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, ProjectionService.class);
        intent.setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Exception ignored) {
        }
    }

    /** Captures one frame, or reports why it could not.  Callback is on the main thread. */
    public static boolean capture(CaptureCallback callback) {
        ProjectionService s = instance;
        if (s == null || s.projection == null || s.display == null) {
            callback.onCaptured(null, "录屏未授权");
            return false;
        }
        return s.captureInternal(callback);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("assistant-projection");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            if (projection != null) {
                startForegroundNow("游戏内助手已就绪（录屏会话保持中）");
                return START_NOT_STICKY;
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundNow("正在准备录屏…");
        if (intent == null || intent.getParcelableExtra(EXTRA_DATA) == null) {
            Log.w(TAG, "missing projection token");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection != null) {
            if (display == null) startDisplay();
            broadcastState();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        try {
            projection = manager.getMediaProjection(resultCode, data);
        } catch (Exception e) {
            Log.e(TAG, "getMediaProjection failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.w(TAG, "projection stopped by system/user");
                stopSelf();
            }
        }, handler);

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
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        densityDpi = metrics.densityDpi;
        instance = this;
        startDisplay();
        startForegroundNow("游戏内助手已就绪（录屏会话保持中）");
        broadcastState();
        Log.i(TAG, "projection ready " + width + "x" + height);
        return START_NOT_STICKY;
    }

    private void startDisplay() {
        if (projection == null || reader != null) return;
        try {
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            display = projection.createVirtualDisplay("assistant-frame", width, height,
                    densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, handler);
            reader.setOnImageAvailableListener(this::onFrameAvailable, handler);
            Log.i(TAG, "persistent virtual display created");
        } catch (Exception e) {
            Log.e(TAG, "create persistent display failed", e);
            failCapture("无法创建录屏画面：" + e.getMessage());
            stopSelf();
        }
    }

    /**
     * Keep the newest Image buffered.  Holding one buffer is safe with maxImages=2 and
     * means a capture request never has to create a second VirtualDisplay.
     */
    private void onFrameAvailable(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireLatestImage();
        } catch (Exception e) {
            Log.e(TAG, "acquireLatestImage failed", e);
        }
        if (image == null) return;
        if (heldImage != null) {
            try {
                heldImage.close();
            } catch (Exception ignored) {
            }
        }
        heldImage = image;
        if (pendingCallback != null && awaitFreshFrame) {
            awaitFreshFrame = false;
            handler.removeCallbacks(captureTimeout);
            deliverHeldFrame();
        }
    }

    private void deliverHeldFrame() {
        if (pendingCallback == null) return;
        Bitmap bitmap = null;
        if (heldImage != null) {
            bitmap = imageToBitmap(heldImage);
            try {
                heldImage.close();
            } catch (Exception ignored) {
            }
            heldImage = null;
        }
        CaptureCallback callback = pendingCallback;
        pendingCallback = null;
        awaitFreshFrame = false;
        handler.removeCallbacks(captureTimeout);
        capturing.set(false);
        if (bitmap == null) {
            main.post(() -> callback.onCaptured(null, "抓屏画面为空"));
        } else {
            Bitmap result = bitmap;
            main.post(() -> callback.onCaptured(result, null));
        }
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap padded = Bitmap.createBitmap(width + rowPadding / pixelStride,
                    height, Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            Bitmap bitmap = Bitmap.createBitmap(padded, 0, 0, width, height);
            if (padded != bitmap) padded.recycle();
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "convert frame failed", e);
            return null;
        }
    }

    private boolean captureInternal(CaptureCallback callback) {
        if (!capturing.compareAndSet(false, true)) {
            callback.onCaptured(null, "正在抓屏，请稍候");
            return false;
        }
        pendingCallback = callback;
        awaitFreshFrame = true;
        handler.postDelayed(captureTimeout, FRESH_FRAME_TIMEOUT_MS);
        return true;
    }

    private void failCapture(String error) {
        CaptureCallback callback = pendingCallback;
        pendingCallback = null;
        awaitFreshFrame = false;
        if (handler != null) handler.removeCallbacks(captureTimeout);
        if (heldImage != null) {
            try {
                heldImage.close();
            } catch (Exception ignored) {
            }
            heldImage = null;
        }
        capturing.set(false);
        if (callback != null) {
            String message = error;
            main.post(() -> callback.onCaptured(null, message));
        }
    }

    private void broadcastState() {
        Intent state = new Intent(ACTION_STATE);
        state.setPackage(getPackageName());
        state.putExtra(EXTRA_RUNNING, isRunning());
        sendBroadcast(state);
    }

    private void startForegroundNow(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "游戏内助手",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("保持屏幕抓取，用于全境征才悬浮助手");
            nm.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 10, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ProjectionService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 11, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle("全境征才助手")
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, "停止助手", stopIntent)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        capturing.set(false);
        if (handler != null) handler.removeCallbacks(captureTimeout);
        if (heldImage != null) {
            try {
                heldImage.close();
            } catch (Exception ignored) {
            }
            heldImage = null;
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
            reader = null;
        }
        if (display != null) {
            try {
                display.release();
            } catch (Exception ignored) {
            }
            display = null;
        }
        if (projection != null) {
            try {
                projection.stop();
            } catch (Exception ignored) {
            }
            projection = null;
        }
        if (thread != null) thread.quitSafely();
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
        broadcastState();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
