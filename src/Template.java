package ir.fishzan.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class Template {
    final String name;
    final int paperWidth, paperHeight;
    private final Context context;
    private final String asset;
    private final File directory;
    private java.lang.ref.WeakReference<Bitmap> cachedPaper = new java.lang.ref.WeakReference<>(null);
    private final JSONArray rules;
    private final java.util.Map<JSONArray, Typeface> fonts = new java.util.HashMap<>();
    private final java.util.Map<JSONArray, JSONObject> styles = new java.util.HashMap<>();
    private final String terminalSuffix, referencePrefix;
    final double widthMm;
    final double heightMm;
    final double amountLeft;
    private final JSONArray logoBox, noteBox;
    private final java.util.List<PrintedLabel> labels = new java.util.ArrayList<>();

    private static final class PrintedLabel {
        final JSONObject spec;
        final Typeface font;

        PrintedLabel(JSONObject spec, Typeface font) {
            this.spec = spec;
            this.font = font;
        }
    }
    final JSONArray amount, date, time, tracking, reference;

    Template(Context context, String asset, File dir) throws Exception {
        this.context = context.getApplicationContext();
        this.asset = asset;
        this.directory = dir;
        JSONObject data;
        try (InputStream in = open(context, asset, dir, "template.json")) {
            data = new JSONObject(new String(read(in), StandardCharsets.UTF_8).trim());
        }
        name = data.getString("name").trim();
        widthMm = data.getDouble("widthMm");
        heightMm = data.getDouble("heightMm");
        amountLeft = data.getDouble("amountLeft");
        logoBox = data.optJSONArray("logoBox");
        noteBox = data.optJSONArray("noteBox");
        rules = data.optJSONArray("rules");
        try (InputStream in = open(context, asset, dir, "paper.png")) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, options);
            if (options.outWidth < 1 || options.outWidth > 2000 ||
                options.outHeight < 1 || options.outHeight > 5000) {
                throw new IllegalArgumentException("ابعاد تصویر قالب نامعتبر است.");
            }
            paperWidth = options.outWidth;
            paperHeight = options.outHeight;
        }
        if (name.isEmpty() || name.length() > 60 ||
            !(widthMm >= 20 && widthMm <= 120 && heightMm >= 20 && heightMm <= 500)) {
            throw new IllegalArgumentException("مشخصات قالب نامعتبر است.");
        }
        JSONObject fields = data.getJSONObject("fields");
        amount = field(fields, "amount");
        date = field(fields, "date");
        time = fields.has("time") ? field(fields, "time") : null;
        tracking = fields.has("tracking") ? field(fields, "tracking") : null;
        reference = fields.has("reference") ? field(fields, "reference") : null;
        terminalSuffix = data.optString("terminalSuffix");
        referencePrefix = data.optString("referencePrefix");
        JSONObject styleMap = data.optJSONObject("styles");
        for (String key : new String[]{"amount", "date", "time", "tracking", "reference"}) {
            if (!fields.has(key)) continue;
            JSONArray slot = fields.getJSONArray(key);
            JSONObject style = styleMap == null ? null : styleMap.optJSONObject(key);
            if (style == null) style = new JSONObject();
            String file = style.optString("font", "font.ttf");
            if (!file.matches("[A-Za-z0-9_-]+[.]ttf")) throw new IllegalArgumentException("font");
            Typeface typeface = dir == null
                ? Typeface.createFromAsset(context.getAssets(), asset + "/" + file)
                : Typeface.createFromFile(new File(dir, file));
            fonts.put(slot, typeface);
            styles.put(slot, style);
        }
        JSONArray labelsData = data.optJSONArray("labels");
        if (labelsData != null) {
            if (labelsData.length() > 80) throw new IllegalArgumentException("labels");
            for (int i = 0; i < labelsData.length(); i++) {
                JSONObject spec = labelsData.getJSONObject(i);
                String file = spec.getString("font");
                if (!file.matches("[A-Za-z0-9_-]+[.]ttf")) throw new IllegalArgumentException("font");
                Typeface typeface = dir == null
                    ? Typeface.createFromAsset(context.getAssets(), asset + "/" + file)
                    : Typeface.createFromFile(new File(dir, file));
                labels.add(new PrintedLabel(spec, typeface));
            }
        }
        if (!(amountLeft >= 0 && amountLeft < amount.optDouble(0))) {
            throw new IllegalArgumentException("amountLeft");
        }
    }

    private Bitmap paper() {
        Bitmap bitmap = cachedPaper.get();
        if (bitmap == null) {
            try (InputStream in = open(context, asset, directory, "paper.png")) {
                bitmap = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                throw new IllegalStateException("تصویر قالب باز نشد.", e);
            }
            if (bitmap == null) throw new IllegalStateException("تصویر قالب نامعتبر است.");
            cachedPaper = new java.lang.ref.WeakReference<>(bitmap);
        }
        return bitmap;
    }

    private byte[] read(InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private InputStream open(Context context, String asset, File dir, String name) throws Exception {
        return dir == null ? context.getAssets().open(asset + "/" + name)
                           : new java.io.FileInputStream(new File(dir, name));
    }

    private JSONArray field(JSONObject fields, String key) throws Exception {
        JSONArray f = fields.getJSONArray(key);
        if (f.length() != (key.equals("tracking") || key.equals("reference") ? 7 : 6)) {
            throw new IllegalArgumentException(key);
        }
        for (int i = 0; i < f.length(); i++) {
            if (!Double.isFinite(f.getDouble(i))) throw new IllegalArgumentException(key);
        }
        if (f.getDouble(0) < 0 || f.getDouble(0) > paperWidth ||
            f.getDouble(1) < 0 || f.getDouble(1) > paperHeight ||
            f.getDouble(2) <= 0 || f.getDouble(2) > 500 ||
            f.getDouble(3) <= 0 || f.getDouble(3) > 5 ||
            f.getDouble(4) <= 0 || f.getDouble(4) > 5 ||
            Math.abs(f.getDouble(5)) > 1000 ||
            (f.length() == 7 && (f.getInt(6) < 1 || f.getInt(6) > 40))) {
            throw new IllegalArgumentException(key);
        }
        return f;
    }

    java.util.List<String> labelTexts() {
        java.util.List<String> texts = new java.util.ArrayList<>(labels.size());
        for (PrintedLabel label : labels) texts.add(label.spec.optString("text"));
        return texts;
    }

    java.util.Map<String, String> elements(JSONArray editedLabels, TemplateLayout layout) {
        java.util.Map<String, String> elements = new java.util.LinkedHashMap<>();
        elements.put("amount", "مبلغ");
        elements.put("date", "تاریخ");
        if (time != null) elements.put("time", "ساعت");
        if (tracking != null) elements.put("tracking", "شمارهٔ پیگیری");
        if (reference != null) elements.put("reference", "شمارهٔ مرجع");
        elements.put("note", "متن پایین فیش");
        for (int i = 0; i < labels.size(); i++) {
            String title = editedLabels.optString(i, labels.get(i).spec.optString("text"));
            elements.put("label:" + i, title.isEmpty() ? "نوشتهٔ " + (i + 1) : title);
        }
        if (rules != null) for (int i = 0; i < rules.length(); i++) {
            elements.put("rule:" + i, "خط یا کادر " + (i + 1));
        }
        for (int i = 0; i < layout.logos.size(); i++) {
            TemplateLayout.Logo logo = layout.logos.get(i);
            elements.put(logo.id, "تصویر یا لوگو " + (i + 1) + (logo.bitmap == null ? " (تصویر در دسترس نیست)" : ""));
        }
        return elements;
    }

    RectF logoPosition(Bitmap bitmap, int index, boolean legacy) {
        RectF area = logoBox != null && logoBox.length() == 4
            ? new RectF((float) logoBox.optDouble(0), (float) logoBox.optDouble(1),
                (float) logoBox.optDouble(2), (float) logoBox.optDouble(3))
            : new RectF(12, 12, paperWidth - 12, paperHeight / 8f);
        float scale = Math.min((legacy ? area.width() : area.width() / 3) / bitmap.getWidth(),
            area.height() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float center = legacy ? area.centerX() : area.left + area.width() * ((index % 3) + .5f) / 3;
        float x = center - width / 2;
        float y = area.centerY() - height / 2;
        return new RectF(x, y, x + width, y + height);
    }

    Bitmap render(String value, String timestamp, String trace, String ref, TemplateLayout layout,
        String note, JSONArray editedLabels, int resolution) {
        return render(value, timestamp, trace, ref, layout, note, editedLabels, resolution, null, null);
    }

    Bitmap render(String value, String timestamp, String trace, String ref, TemplateLayout layout,
        String note, JSONArray editedLabels, int resolution, java.util.Map<String, RectF> bounds, Bitmap reusable) {
        Bitmap image = reusable == null
            ? Bitmap.createBitmap(paperWidth * resolution, paperHeight * resolution, Bitmap.Config.ARGB_8888)
            : reusable;
        Canvas canvas = new Canvas(image);
        canvas.scale(resolution, resolution);
        drawPage(canvas, value, timestamp, trace, ref, layout, note, editedLabels, bounds);
        return image;
    }

    void drawPage(Canvas canvas, String value, String timestamp, String trace, String ref, TemplateLayout layout,
        String note, JSONArray editedLabels, java.util.Map<String, RectF> bounds) {
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(paper(), 0, 0, null);
        if (rules != null) {
            Paint rulePaint = new Paint();
            rulePaint.setColor(Color.BLACK);
            for (int i = 0; i < rules.length(); i++) {
                JSONArray line = rules.optJSONArray(i);
                if (line == null || line.length() != 4) continue;
                String key = "rule:" + i;
                PointF move = layout.offset(key);
                RectF box = new RectF((float) line.optDouble(0) + move.x, (float) line.optDouble(1) + move.y,
                    (float) line.optDouble(2) + move.x, (float) line.optDouble(3) + move.y);
                canvas.drawRect(box, rulePaint);
                if (bounds != null) bounds.put(key, box);
            }
        }
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < labels.size(); i++) {
            PrintedLabel label = labels.get(i);
            JSONObject spec = label.spec;
            String text = editedLabels.optString(i, spec.optString("text"));
            if (!spec.optBoolean("latin")) text = localDigits(text);
            if (text.isEmpty() && bounds == null) continue;
            if (text.isEmpty()) text = spec.optString("text");
            labelPaint.setTypeface(label.font);
            labelPaint.setColor(spec.optBoolean("white") ? Color.WHITE : Color.BLACK);
            String key = "label:" + i;
            float sizeScale = layout.scale(key);
            labelPaint.setTextSize((float) spec.optDouble("size") * sizeScale);
            labelPaint.setTextScaleX(1);
            float left = (float) spec.optDouble("left");
            float right = (float) spec.optDouble("right");
            float textWidth = labelPaint.measureText(text);
            if (textWidth > 0) {
                labelPaint.setTextScaleX(Math.min((float) spec.optDouble("scale"), (right - left) * sizeScale / textWidth));
                PointF move = layout.offset(key);
                float baseline = (float) spec.optDouble("baseline") + move.y;
                right += move.x;
                applyTextColor(labelPaint, layout, key, move, right - labelPaint.measureText(text) / 2, baseline);
                canvas.drawText(text, right, baseline, labelPaint);
                textBounds(bounds, key, right - labelPaint.measureText(text), right, baseline, labelPaint);
            }
        }
        Paint logoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        for (TemplateLayout.Logo logo : layout.logos) {
            PointF move = layout.offset(logo.id);
            float x = logo.x + move.x;
            float y = logo.y + move.y;
            float scale = Math.min(layout.scale(logo.id), Math.min(paperWidth / logo.width, paperHeight / logo.height));
            float width = logo.width * scale;
            float height = logo.height * scale;
            RectF area = new RectF(x, y, x + width, y + height);
            if (logo.bitmap != null) canvas.drawBitmap(logo.bitmap, null, area, logoPaint);
            if (bounds != null) bounds.put(logo.id, area);
        }
        if (!note.isEmpty() || bounds != null) {
            if (note.isEmpty()) note = "متن پایین فیش";
            Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
            label.setColor(Color.BLACK);
            label.setTextSize(28 * layout.scale("note"));
            Integer customColor = layout.color("note");
            if (customColor != null) label.setColor(customColor);
            label.setTextAlign(Paint.Align.CENTER);
            float x = noteBox == null ? paperWidth / 2f : (float) noteBox.optDouble(0);
            float y = noteBox == null ? paperHeight - 70 : (float) noteBox.optDouble(1);
            float available = 2 * Math.min(x - 24, paperWidth - x - 24);
            float width = label.measureText(note);
            if (available > 0 && width > available) label.setTextScaleX(available / width);
            PointF move = layout.offset("note");
            x += move.x;
            y += move.y;
            canvas.drawText(note, x, y, label);
            float halfWidth = label.measureText(note) / 2;
            textBounds(bounds, "note", x - halfWidth, x + halfWidth, y, label);
        }
        if (!value.isEmpty()) {
            String grouped = String.format(Locale.US, "%,d", Long.parseLong(value));
            String mode = styles.get(amount).optString("mode", "suffix");
            if (!mode.equals("comma")) grouped = grouped.replace(',', '،');
            draw(canvas, mode.equals("suffix") ? grouped + "/--" : grouped, amount, "amount", layout, bounds);
        }
        if (!timestamp.isEmpty()) {
            String[] parts = timestamp.split(" ");
            String fullDate = parts[0].length() == 8 ? "14" + parts[0] : parts[0];
            String shortDate = fullDate.substring(2);
            String mode = styles.get(date).optString("mode");
            String dateText;
            if (mode.equals("mellat")) dateText = fullDate + "-" + parts[1].substring(0, 5) + "/" + terminalSuffix;
            else if (mode.equals("full-dash")) dateText = fullDate + " -- " + parts[1];
            else if (time != null) dateText = mode.equals("full-date") ? fullDate : shortDate;
            else dateText = shortDate + " " + parts[1];
            draw(canvas, dateText, date, "date", layout, bounds);
            draw(canvas, parts[1], time, "time", layout, bounds);
        }
        draw(canvas, trace, tracking, "tracking", layout, bounds);
        if (!ref.isEmpty() && reference != null) {
            String suffix = styles.get(reference).optString("mode").equals("slash") ? "/" : "";
            draw(canvas, referencePrefix + ref + suffix, reference, "reference", layout, bounds);
        }
    }

    private static String localDigits(String text) {
        StringBuilder value = new StringBuilder();
        for (char c : text.toCharArray()) {
            value.append(c >= '0' && c <= '9' ? (char) ('۰' + c - '0') : c);
        }
        return value.toString();
    }

    private void applyTextColor(Paint paint, TemplateLayout layout, String key, PointF move, float x, float baseline) {
        Integer customColor = layout.color(key);
        if (customColor != null) {
            paint.setColor(customColor);
            return;
        }
        if (paint.getColor() != Color.WHITE || (move.x == 0 && move.y == 0)) return;
        int column = Math.max(0, Math.min(paperWidth - 1, Math.round(x)));
        int row = Math.max(0, Math.min(paperHeight - 1, Math.round(baseline + paint.ascent() / 3)));
        int background = paper().getPixel(column, row);
        if (Color.red(background) + Color.green(background) + Color.blue(background) > 420) {
            paint.setColor(Color.BLACK);
        }
    }

    private void textBounds(java.util.Map<String, RectF> bounds, String key, float left, float right,
        float baseline, Paint paint) {
        if (bounds != null) bounds.put(key, new RectF(left, baseline + paint.ascent(),
            right, baseline + paint.descent()));
    }

    private void draw(Canvas canvas, String text, JSONArray field, String key, TemplateLayout layout,
        java.util.Map<String, RectF> bounds) {
        if (field == null || text.isEmpty()) return;
        JSONObject style = styles.get(field);
        boolean latin = style.optBoolean("latin");
        StringBuilder persian = new StringBuilder();
        for (char c : text.toCharArray()) {
            persian.append(!latin && c >= '0' && c <= '9' ? (char) ('۰' + c - '0') : c);
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(fonts.get(field));
        paint.setColor(style.optBoolean("white") ? Color.WHITE : Color.BLACK);
        paint.setTextSize((float) field.optDouble(2) * layout.scale(key));
        float spacing = (float) field.optDouble(5) * paint.getTextSize() / 1000;
        float width = spacing * (persian.length() - 1);
        for (int i = 0; i < persian.length(); i++) width += paint.measureText(persian.substring(i, i + 1));
        PointF move = layout.offset(key);
        float right = (float) field.optDouble(0) + move.x;
        float baseline = (float) field.optDouble(1) + move.y;
        float left = right - width * (float) field.optDouble(3);
        boolean customized = layout.scale(key) != 1 || move.x != 0 || move.y != 0;
        if (bounds == null && field == amount && (left < (customized ? 0 : amountLeft) || right > paperWidth)) {
            throw new IllegalArgumentException("مبلغ در فضای تعیین‌شدهٔ این قالب جا نمی‌شود.");
        }
        if (bounds != null) {
            float sx = (float) field.optDouble(3);
            float sy = (float) field.optDouble(4);
            bounds.put(key, new RectF(right - width * sx, baseline + paint.ascent() * sy,
                right, baseline + paint.descent() * sy));
        }
        applyTextColor(paint, layout, key, move, (left + right) / 2, baseline);
        canvas.save();
        canvas.translate(right, baseline);
        canvas.scale((float) field.optDouble(3), (float) field.optDouble(4));
        float x = -width;
        for (int i = 0; i < persian.length(); i++) {
            String glyph = persian.substring(i, i + 1);
            canvas.drawText(glyph, x, 0, paint);
            x += paint.measureText(glyph) + spacing;
        }
        canvas.restore();
    }
}


