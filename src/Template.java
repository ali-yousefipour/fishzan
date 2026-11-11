package ir.fishzan.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class Template {
    final String name;
    final Bitmap paper;
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
        try (InputStream in = open(context, asset, dir, "paper.png")) {
            byte[] png = read(in);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(png, 0, png.length, options);
            if (options.outWidth < 1 || options.outWidth > 2000 ||
                options.outHeight < 1 || options.outHeight > 5000) {
                throw new IllegalArgumentException("ابعاد تصویر قالب نامعتبر است.");
            }
            paper = BitmapFactory.decodeByteArray(png, 0, png.length);
        }
        if (paper == null || name.isEmpty() || name.length() > 60 ||
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
        if (f.getDouble(0) < 0 || f.getDouble(0) > paper.getWidth() ||
            f.getDouble(1) < 0 || f.getDouble(1) > paper.getHeight() ||
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

    Bitmap render(String value, String timestamp, String trace, String ref, Bitmap logo, String note, JSONArray editedLabels, int resolution) {
        Bitmap image = Bitmap.createBitmap(paper.getWidth() * resolution, paper.getHeight() * resolution, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(Color.WHITE);
        canvas.scale(resolution, resolution);
        canvas.drawBitmap(paper, 0, 0, null);
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < labels.size(); i++) {
            PrintedLabel label = labels.get(i);
            JSONObject spec = label.spec;
            String text = editedLabels.optString(i, spec.optString("text"));
            if (!spec.optBoolean("latin")) text = localDigits(text);
            if (text.isEmpty()) continue;
            labelPaint.setTypeface(label.font);
            labelPaint.setColor(spec.optBoolean("white") ? Color.WHITE : Color.BLACK);
            labelPaint.setTextSize((float) spec.optDouble("size"));
            labelPaint.setTextScaleX(1);
            float left = (float) spec.optDouble("left");
            float right = (float) spec.optDouble("right");
            float textWidth = labelPaint.measureText(text);
            if (textWidth > 0) {
                labelPaint.setTextScaleX(Math.min((float) spec.optDouble("scale"), (right - left) / textWidth));
                canvas.drawText(text, right, (float) spec.optDouble("baseline"), labelPaint);
            }
        }
        if (logo != null && logoBox != null && logoBox.length() == 4) {
            float left = (float) logoBox.optDouble(0);
            float top = (float) logoBox.optDouble(1);
            float right = (float) logoBox.optDouble(2);
            float bottom = (float) logoBox.optDouble(3);
            float scale = Math.min((right - left) / logo.getWidth(), (bottom - top) / logo.getHeight());
            float width = logo.getWidth() * scale;
            float height = logo.getHeight() * scale;
            float x = (left + right - width) / 2;
            float y = (top + bottom - height) / 2;
            canvas.drawBitmap(logo, null, new android.graphics.RectF(x, y, x + width, y + height),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        if (!note.isEmpty()) {
            Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
            label.setColor(Color.BLACK);
            label.setTextSize(28);
            label.setTextAlign(Paint.Align.CENTER);
            float x = noteBox == null ? paper.getWidth() / 2f : (float) noteBox.optDouble(0);
            float y = noteBox == null ? paper.getHeight() - 70 : (float) noteBox.optDouble(1);
            float available = 2 * Math.min(x - 24, paper.getWidth() - x - 24);
            float width = label.measureText(note);
            if (available > 0 && width > available) label.setTextScaleX(available / width);
            canvas.drawText(note, x, y, label);
        }
        if (!value.isEmpty()) {
            String grouped = String.format(Locale.US, "%,d", Long.parseLong(value));
            String mode = styles.get(amount).optString("mode", "suffix");
            if (!mode.equals("comma")) grouped = grouped.replace(',', '،');
            draw(canvas, mode.equals("suffix") ? grouped + "/--" : grouped, amount);
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
            draw(canvas, dateText, date);
            draw(canvas, parts[1], time);
        }
        draw(canvas, trace, tracking);
        if (!ref.isEmpty() && reference != null) {
            String suffix = styles.get(reference).optString("mode").equals("slash") ? "/" : "";
            draw(canvas, referencePrefix + ref + suffix, reference);
        }
        return image;
    }

    private static String localDigits(String text) {
        StringBuilder value = new StringBuilder();
        for (char c : text.toCharArray()) {
            value.append(c >= '0' && c <= '9' ? (char) ('۰' + c - '0') : c);
        }
        return value.toString();
    }

    private void draw(Canvas canvas, String text, JSONArray field) {
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
        paint.setTextSize((float) field.optDouble(2));
        float spacing = (float) field.optDouble(5) * paint.getTextSize() / 1000;
        float width = spacing * (persian.length() - 1);
        for (int i = 0; i < persian.length(); i++) width += paint.measureText(persian.substring(i, i + 1));
        if (field == amount && field.optDouble(0) - width * field.optDouble(3) < amountLeft) {
            throw new IllegalArgumentException("مبلغ در فضای تعیین‌شدهٔ این قالب جا نمی‌شود.");
        }
        canvas.save();
        canvas.translate((float) field.optDouble(0), (float) field.optDouble(1));
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


