package com.tkfm.recruitassistant;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * A small in-app mock of the 全境征才 panel.  It exists so OCR and screen capture can be
 * exercised end-to-end without installing the game.
 */
public class DemoPanelActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> projectionLauncher;
    private BroadcastReceiver captureReceiver;
    private TextView status;
    private TextView hourText;
    private int hour = 1;
    private int selectedTags;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo_panel);
        status = findViewById(R.id.demoStatus);
        hourText = findViewById(R.id.hourText);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnCaptureDemo).setOnClickListener(v -> requestCapture());
        findViewById(R.id.btnStartRecruit).setOnClickListener(v -> {
            Log.i("DemoPanel", "start recruit clicked");
            Toast.makeText(this, "模拟：已点击开始招募", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.hourUp).setOnClickListener(v -> setHour(hour + 1));
        findViewById(R.id.hourDown).setOnClickListener(v -> setHour(hour - 1));

        int[] ids = {R.id.tag1, R.id.tag2, R.id.tag3, R.id.tag4, R.id.tag5};
        for (int id : ids) {
            TextView tag = findViewById(id);
            tag.setOnClickListener(v -> toggle(tag));
        }

        projectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        status.setText("已取消屏幕录制权限");
                        return;
                    }
                    status.setText("正在截取屏幕…");
                    CaptureService.start(this, result.getResultCode(), result.getData(), "demo");
                });

        captureReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String path = intent.getStringExtra(CaptureService.EXTRA_PATH);
                String error = intent.getStringExtra(CaptureService.EXTRA_ERROR);
                if (path != null) {
                    Intent open = new Intent(DemoPanelActivity.this, MainActivity.class);
                    open.putExtra(MainActivity.EXTRA_CAPTURE_PATH, path);
                    open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(open);
                    finish();
                } else {
                    status.setText("截屏失败：" + (error == null ? "未知错误" : error));
                }
            }
        };
    }

    private void setHour(int value) {
        hour = ((value - 1) % 9 + 9) % 9 + 1;
        hourText.setText(String.format(java.util.Locale.CHINA, "%02d", hour));
    }

    private void toggle(TextView tag) {
        if (tag.isSelected()) {
            tag.setSelected(false);
            selectedTags--;
        } else {
            if (selectedTags >= 3) {
                Toast.makeText(this, "最多选择三项", Toast.LENGTH_SHORT).show();
                return;
            }
            tag.setSelected(true);
            selectedTags++;
        }
    }

    private void requestCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projectionLauncher.launch(manager.createScreenCaptureIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (captureReceiver != null) {
            ContextCompat.registerReceiver(this, captureReceiver,
                    new IntentFilter(CaptureService.ACTION_CAPTURE_DONE),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        }
    }

    @Override
    protected void onStop() {
        if (captureReceiver != null) {
            try {
                unregisterReceiver(captureReceiver);
            } catch (Exception ignored) {
            }
        }
        super.onStop();
    }
}
