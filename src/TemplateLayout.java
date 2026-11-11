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
        } catch (JSONException ignored) {
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

    void addLogo(Uri uri, Bitmap bitmap, android.graphics.RectF box) {
        logos.add(new Logo("logo:" + UUID.randomUUID(), uri, bitmap,
            box.left, box.top, box.width(), box.height()));
    }

    void removeLogo(String key) {
        logos.removeIf(logo -> logo.id.equals(key));
        offsets.remove(key);
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
        return new JSONObject().put("offsets", positions).put("logos", images).toString();
    }
}
