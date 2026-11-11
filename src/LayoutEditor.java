package ir.fishzan.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;

final class LayoutEditor {
    private final Context context;
    private final Template template;
    private final TemplateLayout layout;
    private final JSONArray labels;
    private final String note;
    private final Map<String, RectF> bounds = new HashMap<>();
    private final List<String> keys = new ArrayList<>();
    private final Spinner picker;
    private final Button remove;
    private final Preview preview;
    private final AlertDialog dialog;
    private String selected;

    LayoutEditor(Context context, Template template, TemplateLayout layout, JSONArray labels,
        String note, Runnable chooseLogo, Runnable save, Runnable close) {
        this.context = context;
        this.template = template;
        this.layout = layout;
        this.labels = labels;
        this.note = note;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(16), dp(8), dp(16), 0);
        TextView instructions = new TextView(context);
        instructions.setText("در پیش‌نمایش نمونه، عنصر را انتخاب کنید و روی فیش بکشید.");
        content.addView(instructions);
        picker = new Spinner(context);
        picker.setMinimumHeight(dp(48));
        content.addView(picker, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(context);
        content.addView(actions);
        Button add = new Button(context);
        add.setText("افزودن لوگو");
        add.setOnClickListener(view -> chooseLogo.run());
        actions.addView(add, new LinearLayout.LayoutParams(0, -2, 1));
        remove = new Button(context);
        remove.setText("حذف لوگو");
        remove.setOnClickListener(view -> {
            if (selected != null && selected.startsWith("logo:")) {
                layout.removeLogo(selected);
                updateOptions(null);
            }
        });
        actions.addView(remove, new LinearLayout.LayoutParams(0, -2, 1));

        preview = new Preview(context);
        content.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View row, int position, long id) {
                if (position < keys.size()) select(keys.get(position));
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        updateOptions(null);
        dialog = new AlertDialog.Builder(context).setTitle("چیدمان و لوگوهای قالب")
            .setView(content).setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره", null).create();
        dialog.setOnDismissListener(ignored -> close.run());
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> save.run());
        dialog.getWindow().setLayout(-1, -1);
    }

    TemplateLayout layout() { return layout; }

    void dismiss() { dialog.dismiss(); }

    void addLogo(Uri uri, Bitmap bitmap) {
        if (layout.logos.size() >= 12) {
            android.widget.Toast.makeText(context, "هر قالب تا ۱۲ لوگو می‌پذیرد.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        layout.addLogo(uri, bitmap, template.logoPosition(bitmap, layout.logos.size(), false));
        updateOptions(layout.logos.get(layout.logos.size() - 1).id);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void updateOptions(String preferred) {
        Map<String, String> elements = template.elements(labels, layout);
        keys.clear();
        keys.addAll(elements.keySet());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(elements.values()));
        picker.setAdapter(adapter);
        int index = Math.max(0, keys.indexOf(preferred));
        picker.setSelection(index);
        select(keys.get(index));
        preview.render();
    }

    private void select(String key) {
        selected = key;
        remove.setEnabled(key.startsWith("logo:"));
        preview.invalidate();
    }

    private final class Preview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF page = new RectF();
        private Bitmap image;
        private float scale, previousX, previousY;
        private boolean dragging;

        Preview(Context context) {
            super(context);
            setContentDescription("پیش‌نمایش قابل جابه‌جایی");
        }

        void render() {
            bounds.clear();
            image = template.render("1250000", "1405/08/20 12:30:00",
                sample(template.tracking), sample(template.reference), layout, note, labels, 1, bounds, image);
            invalidate();
        }

        private String sample(JSONArray field) {
            if (field == null) return "";
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < field.optInt(6); i++) digits.append((i + 1) % 10);
            return digits.toString();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (image == null || getWidth() == 0 || getHeight() == 0) return;
            scale = Math.min((float) getWidth() / image.getWidth(), (float) getHeight() / image.getHeight());
            float width = image.getWidth() * scale;
            float height = image.getHeight() * scale;
            float x = (getWidth() - width) / 2;
            float y = (getHeight() - height) / 2;
            page.set(x, y, x + width, y + height);
            canvas.drawColor(0xffe4ebe6);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawBitmap(image, null, page, paint);
            RectF element = bounds.get(selected);
            if (element != null) {
                paint.setColor(0xff217965);
                paint.setStrokeWidth(dp(2));
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(x + element.left * scale, y + element.top * scale,
                    x + element.right * scale, y + element.bottom * scale, paint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (scale <= 0 || selected == null) return false;
            float x = (event.getX() - page.left) / scale;
            float y = (event.getY() - page.top) / scale;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = page.contains(event.getX(), event.getY());
                    previousX = x;
                    previousY = y;
                    return dragging;
                case MotionEvent.ACTION_MOVE:
                    RectF box = bounds.get(selected);
                    if (!dragging || box == null) return false;
                    PointF offset = layout.offset(selected);
                    float dx = clamp(x - previousX, -box.left, template.paper.getWidth() - box.right);
                    float dy = clamp(y - previousY, -box.top, template.paper.getHeight() - box.bottom);
                    layout.move(selected, offset.x + dx, offset.y + dy);
                    previousX = x;
                    previousY = y;
                    render();
                    return true;
                case MotionEvent.ACTION_UP:
                    performClick();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
                default:
                    return dragging;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
