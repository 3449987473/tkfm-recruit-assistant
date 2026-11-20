package com.tkfm.recruitassistant;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tkfm.recruitassistant.core.OcrAnalyzer;
import com.tkfm.recruitassistant.core.TagCatalog;
import com.tkfm.recruitassistant.ui.AutoFragment;
import com.tkfm.recruitassistant.ui.ManualFragment;
import com.tkfm.recruitassistant.ui.RecordsFragment;
import com.tkfm.recruitassistant.ui.RulesDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements MaterialToolbar.OnMenuItemClickListener {

    public static final String EXTRA_CAPTURE_PATH = "extra_capture_path";
    private static final String EXTRA_BATCH_DIR = "extra_batch_dir";

    private static final String TAG_AUTO = "tab_auto";
    private static final String TAG_MANUAL = "tab_manual";
    private static final String TAG_RECORDS = "tab_records";

    private BottomNavigationView bottomNav;
    private MaterialToolbar toolbar;
    private int current = -1;
    private String pendingCapturePath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.toolbar_main);
        toolbar.setOnMenuItemClickListener(this);
        bottomNav = findViewById(R.id.bottomNav);
        int navWidth = getResources().getDisplayMetrics().widthPixels * 2 / 3;
        android.view.ViewGroup.LayoutParams navParams = bottomNav.getLayoutParams();
        navParams.width = navWidth;
        bottomNav.setLayoutParams(navParams);
        bottomNav.setOnItemSelectedListener(this::onNavItemSelected);
        showTab(0);
        handleIntent(getIntent());
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_auto) {
            showTab(0);
            return true;
        } else if (id == R.id.nav_manual) {
            showTab(1);
            return true;
        } else if (id == R.id.nav_records) {
            showTab(2);
            return true;
        }
        return false;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_rules) {
            RulesDialog.show(this);
            return true;
        } else if (item.getItemId() == R.id.action_about) {
            RulesDialog.showAbout(this);
            return true;
        }
        return false;
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String batchDir = intent.getStringExtra(EXTRA_BATCH_DIR);
        if (batchDir != null) {
            intent.removeExtra(EXTRA_BATCH_DIR);
            runBatchOcr(batchDir);
        }
        String path = intent.getStringExtra(EXTRA_CAPTURE_PATH);
        if (path == null) return;
        intent.removeExtra(EXTRA_CAPTURE_PATH);
        pendingCapturePath = path;
        showTab(0);
        if (getAutoFragment() != null) {
            getAutoFragment().openCapturedImage(path);
            pendingCapturePath = null;
        }
    }

    /** Debug-only batch OCR runner used to regression-test folder images. */
    private void runBatchOcr(String dirPath) {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            Log.w("BatchOcr", "ignored on non-debuggable build");
            return;
        }
        File dir = new File(dirPath);
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".png");
        });
        if (files == null || files.length == 0) {
            Log.w("BatchOcr", "no images in " + dirPath);
            return;
        }
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        Log.i("BatchOcr", "START dir=" + dirPath + " files=" + files.length);
        runBatchFile(files, 0, TagCatalog.get(this), new int[]{0, 0});
    }

    private void runBatchFile(File[] files, int index, TagCatalog catalog, int[] counters) {
        if (index >= files.length) {
            Log.i("BatchOcr", "DONE " + counters[0] + "/" + counters[1]);
            Toast.makeText(this, "批量 OCR 完成：" + counters[0] + "/" + counters[1],
                    Toast.LENGTH_LONG).show();
            return;
        }
        File file = files[index];
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            Log.e("BatchOcr", "decode failed " + file.getName());
            runBatchFile(files, index + 1, catalog, counters);
            return;
        }
        OcrAnalyzer.recognize(this, bitmap, true, new OcrAnalyzer.Callback() {
            @Override
            public void onResult(OcrAnalyzer.OcrResult result) {
                List<String> got = result.tagNames();
                List<String> expected = expectedTags(file.getName(), catalog);
                Set<String> missing = new HashSet<>(expected);
                missing.removeAll(got);
                Set<String> extra = new HashSet<>(got);
                extra.removeAll(expected);
                boolean ok = got.size() == 5
                        && new HashSet<>(got).equals(new HashSet<>(expected));
                counters[1]++;
                if (ok) counters[0]++;
                Log.i("BatchOcr", "FILE=" + file.getName() + " OK=" + ok
                        + " EXPECT=" + expected + " GOT=" + got
                        + " MISSING=" + missing + " EXTRA=" + extra
                        + " START=" + result.startButtonBox);
                bitmap.recycle();
                runBatchFile(files, index + 1, catalog, counters);
            }

            @Override
            public void onError(Exception e) {
                Log.e("BatchOcr", "ocr failed " + file.getName(), e);
                bitmap.recycle();
                runBatchFile(files, index + 1, catalog, counters);
            }
        });
    }

    private List<String> expectedTags(String fileName, TagCatalog catalog) {
        List<String> out = new ArrayList<>();
        String base = fileName.replaceAll("\\.[^.]+$", "");
        for (String part : base.split("[\\s_]+")) {
            if (part.isEmpty()) continue;
            String tag = catalog.matchTag(catalog.normalize(part));
            if (tag != null) out.add(tag);
        }
        return out;
    }

    public void showTab(int index) {
        if (index == current) {
            if (index == 2 && getRecordsFragment() != null) getRecordsFragment().refresh();
            return;
        }
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        Fragment target;
        String tag;
        if (index == 0) {
            tag = TAG_AUTO;
            target = fm.findFragmentByTag(TAG_AUTO);
            if (target == null) target = new AutoFragment();
        } else if (index == 1) {
            tag = TAG_MANUAL;
            target = fm.findFragmentByTag(TAG_MANUAL);
            if (target == null) target = new ManualFragment();
        } else {
            tag = TAG_RECORDS;
            target = fm.findFragmentByTag(TAG_RECORDS);
            if (target == null) target = new RecordsFragment();
        }
        for (Fragment f : fm.getFragments()) {
            if (f != target && f.isAdded()) ft.hide(f);
        }
        if (target.isAdded()) {
            ft.show(target);
        } else {
            ft.add(R.id.container, target, tag);
        }
        ft.commit();
        fm.executePendingTransactions();
        current = index;
        if (bottomNav != null) {
            int item = index == 0 ? R.id.nav_auto : index == 1 ? R.id.nav_manual : R.id.nav_records;
            if (bottomNav.getSelectedItemId() != item) bottomNav.setSelectedItemId(item);
        }
        if (toolbar != null) {
            toolbar.setSubtitle(index == 0 ? "自动识别 · 屏幕 OCR" : index == 1
                    ? "手动计算 · 1~3 项组合" : "招募记录 · 本机保存");
        }
        if (index == 2 && getRecordsFragment() != null) getRecordsFragment().refresh();
        if (index == 0 && pendingCapturePath != null && getAutoFragment() != null) {
            getAutoFragment().openCapturedImage(pendingCapturePath);
            pendingCapturePath = null;
        }
    }

    public void loadManualTags(List<String> tags) {
        showTab(1);
        ManualFragment fragment = getManualFragment();
        if (fragment != null) fragment.setTags(tags);
    }

    @Nullable
    public AutoFragment getAutoFragment() {
        return (AutoFragment) getSupportFragmentManager().findFragmentByTag(TAG_AUTO);
    }

    @Nullable
    public ManualFragment getManualFragment() {
        return (ManualFragment) getSupportFragmentManager().findFragmentByTag(TAG_MANUAL);
    }

    @Nullable
    public RecordsFragment getRecordsFragment() {
        return (RecordsFragment) getSupportFragmentManager().findFragmentByTag(TAG_RECORDS);
    }
}
