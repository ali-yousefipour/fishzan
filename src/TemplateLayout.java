package ir.fishzan.app;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TemplateLayout {
    interface LogoReader {
        Bitmap read(Uri uri) throws java.io.IOException;
    }

    static final class Logo {
        final String id;
        final Uri uri;
        final Bitmap bitmap;
        final float x, y, width, height;

        Logo(String id, Uri uri, Bitmap bitmap, float x, float y, float width, float height) {
            this.id = id;
            this.uri = uri;
            this.bitmap = bitmap;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    final List<Logo> logos = new ArrayList<>();
    private final Map<String, PointF> offsets = new LinkedHashMap<>();

    private final Map<String, Float> scales = new LinkedHashMap<>();
    private final Map<String, Integer> colors = new LinkedHashMap<>();

    TemplateLayout() {}

    TemplateLayout(String json, LogoReader reader) {
        try {
            JSONObject data = new JSONObject(json);
            JSONObject positions = data.getJSONObject("offsets");
            java.util.Iterator<String> keys = positions.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray xy = positions.getJSONArray(key);
                offsets.put(key, new PointF((float) xy.getDouble(0), (float) xy.getDouble(1)));
            }
            JSONArray images = data.getJSONArray("logos");
            for (int i = 0; i < images.length(); i++) {
                JSONObject image = images.getJSONObject(i);
                Uri uri = Uri.parse(image.getString("uri"));
                Bitmap bitmap;
                try { bitmap = reader.read(uri); }
                catch (java.io.IOException | SecurityException e) { bitmap = null; }
                logos.add(new Logo(image.getString("id"), uri, bitmap,
                    (float) image.getDouble("x"), (float) image.getDouble("y"),
                    (float) image.getDouble("width"), (float) image.getDouble("height")));
            }
            JSONObject appearance = data.optJSONObject("appearance");
            if (appearance != null) {
                java.util.Iterator<String> elements = appearance.keys();
                while (elements.hasNext()) {
                    String key = elements.next();
                    JSONObject style = appearance.optJSONObject(key);
                    if (style == null) continue;
                    double scale = style.optDouble("scale", 1);
                    if (scale >= .25 && scale <= 3) scales.put(key, (float) scale);
                    if (style.has("color")) colors.put(key, style.getInt("color"));
                }
            }
        } catch (JSONException ignored) {
            scales.clear();
            colors.clear();
            offsets.clear();
            logos.clear();
        }
    }

    PointF offset(String key) {
        PointF point = offsets.get(key);
        return point == null ? new PointF() : point;
    }

    void move(String key, float x, float y) {
        offsets.put(key, new PointF(x, y));
    }

    float scale(String key) { return scales.containsKey(key) ? scales.get(key) : 1f; }

    Integer color(String key) { return colors.get(key); }

    void format(String key, float scale, Integer color) {
        if (scale == 1f) scales.remove(key);
        else scales.put(key, scale);
        if (color == null) colors.remove(key);
        else colors.put(key, color);
    }

    void addLogo(Uri uri, Bitmap bitmap, android.graphics.RectF box) {
        logos.add(new Logo("logo:" + UUID.randomUUID(), uri, bitmap,
            box.left, box.top, box.width(), box.height()));
    }

    void removeLogo(String key) {
        logos.removeIf(logo -> logo.id.equals(key));
        offsets.remove(key);
        scales.remove(key);
        colors.remove(key);
    }

    String json() throws JSONException {
        JSONObject positions = new JSONObject();
        for (Map.Entry<String, PointF> entry : offsets.entrySet()) {
            positions.put(entry.getKey(), new JSONArray().put(entry.getValue().x).put(entry.getValue().y));
        }
        JSONArray images = new JSONArray();
        for (Logo logo : logos) {
            images.put(new JSONObject().put("id", logo.id).put("uri", logo.uri.toString())
                .put("x", logo.x).put("y", logo.y).put("width", logo.width).put("height", logo.height));
        }
        JSONObject appearance = new JSONObject();
        java.util.Set<String> styled = new java.util.LinkedHashSet<>(scales.keySet());
        styled.addAll(colors.keySet());
        for (String key : styled) {
            JSONObject style = new JSONObject().put("scale", scale(key));
            if (colors.containsKey(key)) style.put("color", colors.get(key));
            appearance.put(key, style);
        }
        return new JSONObject().put("offsets", positions).put("logos", images)
            .put("appearance", appearance).toString();
    }
}
