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
    private String note;
    private final Map<String, RectF> bounds = new HashMap<>();
    private final List<String> keys = new ArrayList<>();
    private final Spinner picker;
    private final Button remove, edit, appearance;
    private final Preview preview;
    private final AlertDialog dialog;
    private String selected;

    LayoutEditor(Context context, Template template, TemplateLayout layout, JSONArray labels,
        String note, Runnable showLogos, Runnable chooseImage, Runnable save, Runnable saveCopy, Runnable close) {
        this.context = context;
        this.template = template;
        this.layout = layout;
        this.labels = labels;
        for (int i = labels.length(); i < template.labelTexts().size(); i++) {
            labels.put(template.labelTexts().get(i));
        }
        this.note = note;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(16), dp(8), dp(16), 0);
        TextView instructions = new TextView(context);
        instructions.setText("برای انتخاب روی عنصر بزنید. برای جابه‌جایی، آن را بکشید.");
        content.addView(instructions);
        picker = new Spinner(context);
        picker.setMinimumHeight(dp(48));
        content.addView(picker, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout images = new LinearLayout(context);
        content.addView(images);
        Button logos = new Button(context);
        logos.setText("لوگوهای آماده");
        logos.setOnClickListener(view -> showLogos.run());
        images.addView(logos, new LinearLayout.LayoutParams(0, -2, 1));
        Button add = new Button(context);
        add.setText("تصویر از گوشی");
        add.setOnClickListener(view -> chooseImage.run());
        images.addView(add, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout actions = new LinearLayout(context);
        content.addView(actions);
        remove = new Button(context);
        remove.setText("حذف تصویر");
        remove.setOnClickListener(view -> {
            if (selected != null && selected.startsWith("logo:")) {
                layout.removeLogo(selected);
                updateOptions(null);
            }
        });
        actions.addView(remove, new LinearLayout.LayoutParams(0, -2, 1));
        edit = new Button(context);
        edit.setText("ویرایش متن");
        edit.setOnClickListener(view -> editText());
        actions.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));

        appearance = new Button(context);
        appearance.setText("اندازه و رنگ");
        appearance.setMinimumHeight(dp(48));
        appearance.setOnClickListener(view -> editAppearance());
        actions.addView(appearance, new LinearLayout.LayoutParams(0, -2, 1));

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
            .setPositiveButton("ذخیره", null).setNeutralButton("ذخیره با نام جدید", null).create();
        dialog.setOnDismissListener(ignored -> close.run());
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> save.run());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> saveCopy.run());
        dialog.getWindow().setLayout(-1, -1);
    }

    TemplateLayout layout() { return layout; }
    JSONArray labels() { return labels; }
    String note() { return note; }

    private void editText() {
        String key = selected;
        boolean footer = key.equals("note");
        int index = footer ? -1 : Integer.parseInt(key.substring("label:".length()));
        android.widget.EditText input = new android.widget.EditText(context);
        input.setSingleLine();
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(140)});
        input.setText(footer ? note : labels.optString(index));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(context).setTitle("ویرایش متن فیش").setView(input)
            .setNegativeButton("انصراف", null).setPositiveButton("ثبت", (dialog, which) -> {
                String value = input.getText().toString().trim();
                if (footer) note = value;
                else {
                    try { labels.put(index, value); }
                    catch (org.json.JSONException e) { throw new IllegalStateException(e); }
                }
                updateOptions(key);
            }).show();
    }

    private void editAppearance() {
        String key = selected;
        boolean image = key.startsWith("logo:");
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        form.setPadding(dp(22), dp(12), dp(22), dp(8));

        TextView size = new TextView(context);
        form.addView(size);
        android.widget.SeekBar scale = new android.widget.SeekBar(context);
        scale.setMax(275);
        scale.setProgress(Math.round(layout.scale(key) * 100) - 25);
        scale.setContentDescription(image ? "اندازهٔ تصویر" : "اندازهٔ نوشته");
        form.addView(scale);
        java.util.function.IntConsumer showSize = progress -> size.setText(
            String.format(new java.util.Locale("fa"), "%s  ٪%d", image ? "اندازهٔ تصویر" : "اندازهٔ نوشته", progress + 25));
        showSize.accept(scale.getProgress());
        scale.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                showSize.accept(progress);
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            public void onStopTrackingTouch(android.widget.SeekBar bar) {}
        });

        android.widget.EditText hex = new android.widget.EditText(context);
        if (!image) {
            TextView caption = new TextView(context);
            caption.setText("رنگ نوشته");
            form.addView(caption);
            String[] names = {"رنگ قالب", "مشکی", "سفید", "قرمز", "آبی", "سبز", "خاکستری", "رنگ دلخواه"};
            String[] values = {"", "#000000", "#FFFFFF", "#C62828", "#174EA6", "#146B3A", "#555555"};
            Integer previous = layout.color(key);
            String initial = previous == null ? "" : String.format(java.util.Locale.ROOT, "#%06X", previous & 0xffffff);
            Spinner palette = new Spinner(context);
            palette.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, names));
            form.addView(palette, new LinearLayout.LayoutParams(-1, dp(48)));
            hex.setSingleLine();
            hex.setTextDirection(View.TEXT_DIRECTION_LTR);
            hex.setHint("#RRGGBB");
            hex.setText(initial);
            hex.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});
            form.addView(hex, new LinearLayout.LayoutParams(-1, -2));
            int initialChoice = java.util.Arrays.asList(values).indexOf(initial);
            palette.setSelection(initialChoice < 0 ? names.length - 1 : initialChoice);
            palette.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View row, int position, long id) {
                    boolean custom = position == names.length - 1;
                    if (!custom) hex.setText(values[position]);
                    hex.setVisibility(custom ? View.VISIBLE : View.GONE);
                }
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        AlertDialog editor = new AlertDialog.Builder(context)
            .setTitle(image ? "اندازهٔ تصویر یا لوگو" : "اندازه و رنگ نوشته")
            .setView(form).setNegativeButton("انصراف", null).setPositiveButton("اعمال", null).create();
        editor.show();
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            Integer color = null;
            String value = hex.getText().toString().trim();
            if (!image && !value.isEmpty()) {
                if (!value.matches("#[0-9A-Fa-f]{6}")) {
                    hex.setError("رنگ را به شکل #RRGGBB وارد کنید.");
                    return;
                }
                color = Color.parseColor(value);
            }
            layout.format(key, (scale.getProgress() + 25) / 100f, color);
            preview.render();
            keepInsidePage(key);
            editor.dismiss();
        });
    }

    private void keepInsidePage(String key) {
        RectF box = bounds.get(key);
        if (box == null) return;
        float dx = box.width() > template.paperWidth ? -box.left
            : box.left < 0 ? -box.left : Math.min(0, template.paperWidth - box.right);
        float dy = box.height() > template.paperHeight ? -box.top
            : box.top < 0 ? -box.top : Math.min(0, template.paperHeight - box.bottom);
        if (dx != 0 || dy != 0) {
            PointF original = layout.offset(key);
            layout.move(key, original.x + dx, original.y + dy);
            preview.render();
        }
    }

    void dismiss() { dialog.dismiss(); }

    void addLogo(Uri uri, Bitmap bitmap) {
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
        edit.setEnabled(key.startsWith("label:") || key.equals("note"));
        appearance.setEnabled(!key.startsWith("rule:"));
        appearance.setText(key.startsWith("logo:") ? "اندازهٔ تصویر" : "اندازه و رنگ");
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
                    if (dragging) {
                        String hit = hitTest(x, y);
                        if (hit != null) {
                            picker.setSelection(keys.indexOf(hit));
                            select(hit);
                        }
                    }
                    previousX = x;
                    previousY = y;
                    return dragging;
                case MotionEvent.ACTION_MOVE:
                    RectF box = bounds.get(selected);
                    if (!dragging || box == null) return false;
                    PointF offset = layout.offset(selected);
                    float dx = clamp(x - previousX, -box.left, template.paperWidth - box.right);
                    float dy = clamp(y - previousY, -box.top, template.paperHeight - box.bottom);
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

        private String hitTest(float x, float y) {
            for (int i = keys.size() - 1; i >= 0; i--) {
                String key = keys.get(i);
                RectF box = bounds.get(key);
                if (!key.startsWith("rule:") && box != null && box.contains(x, y)) return key;
            }
            float slop = dp(12) / scale;
            String nearest = null;
            float best = slop * slop;
            for (String key : keys) {
                RectF box = bounds.get(key);
                if (box == null) continue;
                float dx = Math.max(0, Math.max(box.left - x, x - box.right));
                float dy = Math.max(0, Math.max(box.top - y, y - box.bottom));
                float distance = dx * dx + dy * dy;
                if (distance < best) {
                    best = distance;
                    nearest = key;
                }
            }
            return nearest;
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
