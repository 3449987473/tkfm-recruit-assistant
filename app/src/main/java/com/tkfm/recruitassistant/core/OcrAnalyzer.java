package com.tkfm.recruitassistant.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Screen OCR pipeline.  The reference app crops the middle band of the capture, runs
 * ML Kit Chinese recognition, cleans each line (replacement table, trim, collapse repeated
 * characters) and finally maps lines to the requirement table.  This class keeps the same
 * shape with an extra full-frame pass and a retry on the middle band when fewer than five
 * tags are found.
 */
public final class OcrAnalyzer {

    public static final class DetectedTag {
        public final String tag;
        public final Rect box;
        public final String raw;
        public final int order;

        DetectedTag(String tag, Rect box, String raw, int order) {
            this.tag = tag;
            this.box = box;
            this.raw = raw;
            this.order = order;
        }
    }

    /** A numeric element that can be the hour of the recruit-time control. */
    public static final class TimeMark {
        public final String text;
        public final int value;
        public final Rect box;

        TimeMark(String text, int value, Rect box) {
            this.text = text;
            this.value = value;
            this.box = box;
        }
    }

    public static final class OcrResult {
        public final List<DetectedTag> tags;
        public final List<TimeMark> times;
        public final Rect startButtonBox;
        public final String rawText;

        OcrResult(List<DetectedTag> tags, List<TimeMark> times, Rect startButtonBox,
                  String rawText) {
            this.tags = tags;
            this.times = times;
            this.startButtonBox = startButtonBox;
            this.rawText = rawText;
        }

        public List<String> tagNames() {
            List<String> names = new ArrayList<>();
            for (DetectedTag t : tags) names.add(t.tag);
            return names;
        }
    }

    public interface Callback {
        void onResult(OcrResult result);
        void onError(Exception e);
    }

    private static TextRecognizer recognizer;
    private static final java.util.regex.Pattern TIME_PATTERN =
            java.util.regex.Pattern.compile("0?[1-9]");

    private static synchronized TextRecognizer recognizer() {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build());
        }
    return recognizer;
    }

    private OcrAnalyzer() {
    }

    public static void recognize(Context context, Bitmap bitmap, Callback callback) {
        recognize(context, bitmap, false, callback);
    }

    public static void recognize(Context context, Bitmap bitmap, boolean detectStartButton,
                                 Callback callback) {
        if (bitmap == null) {
            callback.onError(new IllegalArgumentException("empty bitmap"));
            return;
        }
        TagCatalog catalog = TagCatalog.get(context);
        Bitmap working = scaleDown(bitmap, 1800);
        // Recognition runs on a scaled bitmap, but callers (the in-game assistant)
        // need coordinates in the original screen space to dispatch taps.
        float scale = working.getWidth() / (float) bitmap.getWidth();
        InputImage image = InputImage.fromBitmap(working, 0);

        recognizer().process(image)
                .addOnSuccessListener(text -> {
                    Pass full = extract(text, catalog, bitmap.getHeight(), 0, scale);
                    Log.i("OcrAnalyzer", "full tags=" + names(full.tags)
                            + " anchors=" + full.headerBox + "/" + full.footerBox
                            + " start=" + full.startBox);

                    int height = bitmap.getHeight();
                    int top = full.headerBox != null
                            ? full.headerBox.bottom + Math.max(4, height / 300)
                            : Math.round(height * 0.20f);
                    int bottom = full.footerBox != null
                            ? full.footerBox.top - Math.max(4, height / 300)
                            : Math.round(height * 0.55f);
                    if (top < 0) top = 0;
                    if (bottom > height) bottom = height;
                    if (bottom - top < height * 0.06f) {
                        top = Math.round(height * 0.20f);
                        bottom = Math.round(height * 0.55f);
                    }
                    if (bottom - top < 40) {
                        finishWithStartButton(bitmap, catalog, detectStartButton,
                                full, callback);
                        return;
                    }

                    // A second, higher-resolution pass over just the tag panel.  The
                    // two-character chips (亚人 / 美乳) are the ones ML Kit loses when the
                    // whole 1264x2780 screenshot is scaled down to 1800 px.
                    Bitmap crop;
                    Bitmap scaledCrop;
                    final float up = Math.max(1f, Math.min(2f,
                            1600f / Math.max(1, bitmap.getWidth())));
                    try {
                        crop = Bitmap.createBitmap(bitmap, 0, top, bitmap.getWidth(),
                                bottom - top);
                        int scaledW = Math.round(crop.getWidth() * up);
                        int scaledH = Math.round(crop.getHeight() * up);
                        scaledCrop = Bitmap.createScaledBitmap(crop, scaledW, scaledH, true);
                        if (scaledCrop != crop) crop.recycle();
                    } catch (Exception e) {
                        Log.e("OcrAnalyzer", "band crop failed", e);
                        finishWithStartButton(bitmap, catalog, detectStartButton,
                                full, callback);
                        return;
                    }
                    final int bandTop = top;
                    Log.i("OcrAnalyzer", "band crop=" + scaledCrop.getWidth() + "x"
                            + scaledCrop.getHeight() + " up=" + up + " top=" + bandTop);
                    InputImage bandImage = InputImage.fromBitmap(scaledCrop, 0);
                    recognizer().process(bandImage)
                            .addOnSuccessListener(text2 -> {
                                Pass band = extract(text2, catalog, height,
                                        Math.round(bandTop * up), up);
                                Log.i("OcrAnalyzer", "band tags=" + names(band.tags)
                                        + " start=" + band.startBox
                                        + " raw=" + band.rawText.toString().replace('\n', '|'));
                                Pass chosen = choosePass(full, band);
                                scaledCrop.recycle();
                                finishWithStartButton(bitmap, catalog, detectStartButton,
                                        chosen, callback);
                            })
                            .addOnFailureListener(e -> {
                                Log.e("OcrAnalyzer", "band OCR failed", e);
                                scaledCrop.recycle();
                                finishWithStartButton(bitmap, catalog, detectStartButton,
                                        full, callback);
                            });
                })
                .addOnFailureListener(callback::onError);
    }

    private static List<String> names(List<DetectedTag> tags) {
        List<String> out = new ArrayList<>();
        for (DetectedTag tag : tags) out.add(tag.tag);
        return out;
    }

    /**
     * Only the assistant needs the start button.  The full-frame pass often misses the
     * small "开始招募" button at the bottom, so run a high-resolution bottom strip when
     * auto-recruit is enabled and the full pass did not find it.
     */
    private static void finishWithStartButton(Bitmap bitmap, TagCatalog catalog,
                                              boolean detectStartButton, Pass chosen,
                                              Callback callback) {
        if (!detectStartButton || chosen.startBox != null) {
            callback.onResult(chosen.toResult());
            return;
        }
        final int height = bitmap.getHeight();
        final int top = Math.round(height * 0.70f);
        if (height - top < 40) {
            callback.onResult(chosen.toResult());
            return;
        }
        Bitmap crop = null;
        Bitmap scaled = null;
        try {
            crop = Bitmap.createBitmap(bitmap, 0, top, bitmap.getWidth(), height - top);
            final float up = Math.max(1f, Math.min(2f,
                    1600f / Math.max(1, bitmap.getWidth())));
            scaled = Bitmap.createScaledBitmap(crop,
                    Math.round(crop.getWidth() * up),
                    Math.round(crop.getHeight() * up), true);
            if (scaled != crop) crop.recycle();
            final Bitmap scaledRef = scaled;
            final int bottomTop = top;
            recognizer().process(InputImage.fromBitmap(scaled, 0))
                    .addOnSuccessListener(text -> {
                        Pass bottom = extract(text, catalog, height,
                                Math.round(bottomTop * up), up);
                        if (chosen.startBox == null) chosen.startBox = bottom.startBox;
                        Log.i("OcrAnalyzer", "bottom start=" + bottom.startBox
                                + " raw=" + bottom.rawText.toString().replace('\n', '|'));
                        scaledRef.recycle();
                        callback.onResult(chosen.toResult());
                    })
                    .addOnFailureListener(e -> {
                        Log.e("OcrAnalyzer", "bottom OCR failed", e);
                        scaledRef.recycle();
                        callback.onResult(chosen.toResult());
                    });
        } catch (Exception e) {
            Log.e("OcrAnalyzer", "bottom crop failed", e);
            if (scaled != null) scaled.recycle();
            callback.onResult(chosen.toResult());
        }
    }

    /**
     * Prefer the high-resolution band result.  If it has fewer than five tags, fill the
     * gap from the full-frame pass (usually because the band cut off a chip).
     */
    private static Pass choosePass(Pass full, Pass band) {
        if (band.tags.isEmpty()) return full;
        if (band.tags.size() > 5) {
            // Longest-match should keep this at five; if an OEM font still produces a
            // duplicate, the full pass is the safer five-tag result.
            if (full.tags.size() == 5) return full;
        }
        Pass out = new Pass();
        out.sortHeight = full.sortHeight;
        out.startBox = full.startBox;
        out.tags.addAll(band.tags);
        Set<String> names = new LinkedHashSet<>();
        for (DetectedTag tag : out.tags) names.add(tag.tag);
        for (DetectedTag tag : full.tags) {
            if (out.tags.size() >= 5) break;
            if (names.add(tag.tag)) out.tags.add(tag);
        }
        out.times.addAll(full.times);
        out.rawText.append(full.rawText).append("\n---- band ----\n").append(band.rawText);
        sortReadingOrder(out.tags, out.sortHeight);
        sortTimes(out.times);
        return out;
    }


    private static Bitmap scaleDown(Bitmap source, int maxSide) {
        int w = source.getWidth();
        int h = source.getHeight();
        int side = Math.max(w, h);
        if (side <= maxSide) return source;
        float ratio = maxSide / (float) side;
        Bitmap scaled = Bitmap.createScaledBitmap(source, Math.round(w * ratio),
                Math.round(h * ratio), true);
        return scaled;
    }

    private static Pass extract(Text text, TagCatalog catalog, int sortHeight, int yOffset,
                                float scale) {
        Pass pass = new Pass();
        pass.sortHeight = sortHeight;
        String full = text.getText() == null ? "" : text.getText();
        pass.rawText.append(full).append('\n');
        Map<String, DetectedTag> found = new LinkedHashMap<>();
        int order = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String rawLine = line.getText();
                String normLine = catalog.normalize(rawLine);
                Rect lineBox = line.getBoundingBox();
                if (lineBox != null) {
                    if (pass.headerBox == null && isRequirementHeader(normLine)) {
                        pass.headerBox = toOriginal(lineBox, yOffset, scale);
                    }
                    if ((pass.footerBox == null || lineBox.top < pass.footerBox.top)
                            && isRequirementFooter(normLine)) {
                        pass.footerBox = toOriginal(lineBox, yOffset, scale);
                    }
                    if (pass.startBox == null && isStartButton(normLine)) {
                        pass.startBox = toOriginal(lineBox, yOffset, scale);
                    }
                }

                List<String> lineTags = catalog.matchAllTags(normLine);
                if (lineTags.isEmpty() && usable(rawLine)) {
                    String fuzzy = fuzzyTag(catalog, normLine);
                    if (fuzzy != null) lineTags = Collections.singletonList(fuzzy);
                }

                for (Text.Element element : line.getElements()) {
                    String raw = element.getText();
                    Rect elementBox = element.getBoundingBox();
                    if (elementBox == null) elementBox = line.getBoundingBox();
                    if (elementBox != null) {
                        String trimmed = raw == null ? "" : raw.trim();
                        java.util.regex.Matcher timeMatcher = TIME_PATTERN.matcher(trimmed);
                        if (timeMatcher.matches()) {
                            pass.times.add(new TimeMark(trimmed,
                                    Integer.parseInt(trimmed),
                                    toOriginal(elementBox, yOffset, scale)));
                        }
                    }
                    if (!usable(raw)) continue;
                    String tag = catalog.matchTag(catalog.normalize(raw));
                    if (tag == null) tag = fuzzyTag(catalog, catalog.normalize(raw));
                    if (tag == null) continue;
                    // "群体攻击" also contains the shorter "攻击" alias of 攻击者.
                    // Only accept the element-level tag if the line-level matcher
                    // (longest, non-overlapping) agreed with it.
                    if (!lineTags.isEmpty() && !lineTags.contains(tag)) {
                        Log.d("OcrAnalyzer", "skip substring tag=" + tag + " line=" + rawLine);
                        continue;
                    }
                    Rect box = element.getBoundingBox();
                    if (box == null) box = line.getBoundingBox();
                    if (box == null) box = new Rect(0, 0, 0, 0);
                    Rect shifted = toOriginal(box, yOffset, scale);
                    if (!found.containsKey(tag)) {
                        found.put(tag, new DetectedTag(tag, shifted, raw, order++));
                    }
                }
                // Always inspect the whole line too: ML Kit may split a chip across
                // elements, and the line text still contains the complete tag.
                if (!usable(rawLine)) continue;
                Rect fallbackBox = line.getBoundingBox();
                if (fallbackBox == null) fallbackBox = new Rect(0, 0, 0, 0);
                for (String tag : lineTags) {
                    if (found.containsKey(tag)) continue;
                    Rect tagBox = bestBoxForTag(line, catalog, tag, fallbackBox);
                    Rect shifted = toOriginal(tagBox, yOffset, scale);
                    found.put(tag, new DetectedTag(tag, shifted, rawLine, order++));
                }
            }
        }
        pass.tags.addAll(found.values());
        sortReadingOrder(pass.tags, sortHeight);
        sortTimes(pass.times);
        return pass;
    }

    private static boolean isRequirementHeader(String normalized) {
        return normalized != null && (normalized.contains("招募条件")
                || normalized.contains("徵才條件")
                || normalized.contains("招募條件")
                || normalized.contains("招暮条件")
                || normalized.contains("招幕条件")
                || normalized.contains("征才条件"));
    }

    private static boolean isRequirementFooter(String normalized) {
        return normalized != null && (normalized.contains("本日剩余")
                || normalized.contains("本日剩餘")
                || normalized.contains("剩余更换")
                || normalized.contains("剩餘更換")
                || normalized.contains("招募可能"));
    }

    private static boolean isStartButton(String normalized) {
        if (normalized == null) return false;
        String text = normalized.trim();
        // Must be the button label itself.  A "contains" check would match the
        // settings link "助手设置（…/开始招募）" and tap the wrong place.
        return text.equals("开始招募") || text.equals("開始招募")
                || text.equals("开始招暮") || text.equals("開始招暮")
                || text.equals("开始招幕") || text.equals("開始招幕");
    }

    /**
     * Element boxes are more precise than a merged line box for dispatching a tap.
     * Find the element (or contiguous element union) that forms the matched tag.
     */
    private static Rect bestBoxForTag(Text.Line line, TagCatalog catalog, String tag,
                                      Rect fallback) {
        List<Text.Element> elements = line.getElements();
        for (String alias : catalog.aliasesOf(tag)) {
            for (Text.Element element : elements) {
                String norm = catalog.normalize(element.getText());
                if (norm.equals(alias) && element.getBoundingBox() != null) {
                    return element.getBoundingBox();
                }
            }
        }
        Rect union = null;
        for (Text.Element element : elements) {
            String norm = catalog.normalize(element.getText());
            if (norm.length() < 1) continue;
            boolean part = false;
            for (String alias : catalog.aliasesOf(tag)) {
                if (alias.contains(norm) || norm.contains(alias)) {
                    part = true;
                    break;
                }
            }
            if (!part) continue;
            Rect box = element.getBoundingBox();
            if (box == null) continue;
            if (union == null) union = new Rect(box);
            else union.union(box);
        }
        return union != null ? union : fallback;
    }

    private static Rect toOriginal(Rect box, int yOffset, float scale) {
        if (scale <= 0f) scale = 1f;
        return new Rect(
                Math.round(box.left / scale),
                Math.round((box.top + yOffset) / scale),
                Math.round(box.right / scale),
                Math.round((box.bottom + yOffset) / scale));
    }

    private static void sortTimes(List<TimeMark> times) {
        Collections.sort(times, (a, b) -> {
            if (a.box.top != b.box.top) return Integer.compare(a.box.top, b.box.top);
            return Integer.compare(a.box.left, b.box.left);
        });
    }

    /** Tolerate a single OCR substitution on tags of three or more characters. */
    private static String fuzzyTag(TagCatalog catalog, String line) {
        if (line == null || line.length() > 8) return null;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String tag : catalog.allTags()) {
            String alias = catalog.normalize(tag);
            if (alias.length() < 3) continue;
            if (Math.abs(line.length() - alias.length()) > 1) continue;
            int distance = editDistance(line, alias);
            if (distance <= 1 && distance < bestDistance) {
                bestDistance = distance;
                best = tag;
            }
        }
        return best;
    }

    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    /** Rejects headers, counters, and lines that cannot be tag chips. */
    private static boolean usable(String raw) {
        if (raw == null) return false;
        String text = raw.trim();
        if (text.length() < 2 || text.length() > 24) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (java.lang.Character.isDigit(c)) return false;
            if (c == '☆' || c == '★') return false;
        }
        String[] banned = {"招募", "徵才", "征才", "时间", "時間", "条件", "條件", "剩余",
                "剩餘", "更换", "更換", "开始", "開始", "取消", "最多", "选择", "選擇",
                "全境", "所需", "资源", "資源", "可能", "范围", "範圍", "本日"};
        for (String b : banned) if (text.contains(b)) return false;
        return true;
    }

    private static void sortReadingOrder(List<DetectedTag> tags, int height) {
        int tolerance = Math.max(18, height / 45);
        Collections.sort(tags, (a, b) -> {
            int ay = a.box.top / tolerance;
            int by = b.box.top / tolerance;
            if (ay != by) return Integer.compare(ay, by);
            return Integer.compare(a.box.left, b.box.left);
        });
    }

    static final class Pass {
        final List<DetectedTag> tags = new ArrayList<>();
        final List<TimeMark> times = new ArrayList<>();
        final StringBuilder rawText = new StringBuilder();
        Rect headerBox;
        Rect footerBox;
        Rect startBox;
        int sortHeight = Integer.MAX_VALUE;

        Pass merge(Pass other) {
            Set<String> names = new LinkedHashSet<>();
            for (DetectedTag t : tags) names.add(t.tag);
            for (DetectedTag t : other.tags) {
                if (!names.contains(t.tag)) {
                    tags.add(t);
                    names.add(t.tag);
                }
            }
            for (TimeMark t : other.times) {
                boolean duplicate = false;
                for (TimeMark existing : times) {
                    if (existing.value == t.value
                            && Math.abs(existing.box.top - t.box.top) < 12
                            && Math.abs(existing.box.left - t.box.left) < 12) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) times.add(t);
            }
            rawText.append('\n').append(other.rawText);
            sortReadingOrder(tags, sortHeight);
            sortTimes(times);
            return this;
        }

        OcrResult toResult() {
            return new OcrResult(Collections.unmodifiableList(new ArrayList<>(tags)),
                    Collections.unmodifiableList(new ArrayList<>(times)),
                    startBox,
                    rawText.toString().trim());
        }
    }
}
