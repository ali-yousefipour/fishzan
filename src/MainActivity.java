package ir.fishzan.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.icu.util.Calendar;
import android.net.Uri;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.text.method.DigitsKeyListener;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MainActivity extends Activity {
    private final List<Template> templates = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Spinner picker;
    private EditText amount, date, tracking, reference;
    private ImageView preview;
    private TextView status, trackingLabel, referenceLabel;
    private Button print, saveReceipt, savePdf;
    private ReceiptArchive archive;
    private AssetLabelDialog assetDialog;
    private File pendingPdf;
    private Typeface homa;
    private Bitmap receipt;
    private TemplateLayout activeLayout;
    private LayoutEditor layoutEditor;
    private final java.util.Map<Uri, Bitmap> loadedLogos = new java.util.HashMap<>();
    private Calendar selectedDate = PersianDateDialog.calendar();
    private Button dateButton, timeButton;
    private final List<String> templateIds = new ArrayList<>();
    private int selection = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        homa = Typeface.createFromAsset(getAssets(), "templates/fanous/BHoma.ttf");
        archive = new ReceiptArchive(this);
        if (state != null) {
            String pending = state.getString("pendingPdf", "");
            if (pending.matches("fishzan-[0-9]+[.]pdf")) pendingPdf = new File(getCacheDir(), pending);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xfff2f5fb);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(20));
        scroll.addView(layout);
        setContentView(scroll);

        LinearLayout header = row(layout);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        int iconId = getResources().getIdentifier("fishzan_canva", "drawable", getPackageName());
        icon.setImageResource(iconId);
        header.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView title = label(header, "فیش زن");
        title.setPadding(dp(12), 0, dp(12), 0);
        title.setTypeface(homa, Typeface.BOLD);
        title.setTextSize(30);
        LinearLayout tools = row(layout);
        tools.setPadding(0, dp(12), 0, 0);
        button(tools, "بایگانی فیش‌ها", this::openArchive);
        button(tools, "چاپ شماره اموال", this::openAssetLabel);
        LinearLayout templatesPanel = panel(layout, "قالب فیش");
        picker = new Spinner(this);
        picker.setMinimumHeight(dp(48));
        templatesPanel.addView(picker, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout templateActions = row(templatesPanel);
        button(templateActions, "ورود قالب از ZIP", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 1);
        });
        button(templateActions, "ویرایش قالب", this::editTemplate);
        button(templatesPanel, "چیدمان و لوگوها", this::editLayout);
        LinearLayout form = panel(layout, "اطلاعات فیش");
        label(form, "مبلغ به ریال");
        amount = input(form, "مثلاً ۲۰۷۵۰۰۰۰", InputType.TYPE_CLASS_NUMBER, 12);
        label(form, "تاریخ شمسی و ساعت");
        date = new EditText(this);
        if (state != null) selectedDate.setTimeInMillis(state.getLong("selectedDate", selectedDate.getTimeInMillis()));
        LinearLayout dateActions = row(form);
        dateButton = button(dateActions, "", () -> PersianDateDialog.show(this, selectedDate, chosen -> {
            selectedDate.set(Calendar.YEAR, chosen.get(Calendar.YEAR));
            selectedDate.set(Calendar.MONTH, chosen.get(Calendar.MONTH));
            selectedDate.set(Calendar.DAY_OF_MONTH, chosen.get(Calendar.DAY_OF_MONTH));
            updateDate();
        }));
        timeButton = button(dateActions, "", this::chooseTime);
        updateDate();
        trackingLabel = label(form, "شمارهٔ پیگیری");
        tracking = input(form, "", InputType.TYPE_CLASS_NUMBER, 40);
        referenceLabel = label(form, "شمارهٔ مرجع");
        reference = input(form, "", InputType.TYPE_CLASS_NUMBER, 40);
        LinearLayout files = row(form);
        saveReceipt = button(files, "ثبت در بایگانی", this::archiveCurrent);
        savePdf = button(files, "ذخیرهٔ PDF", () -> exportPdf(currentPage(), currentTitle()));
        print = button(layout, "چاپ فیش", this::print);
        print.setTextColor(Color.WHITE);
        print.setBackgroundTintList(new ColorStateList(
            new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
            new int[]{0xff5e6a7e, 0xff183b75}));
        status = label(layout, "");
        LinearLayout previewPanel = panel(layout, "پیش‌نمایش");
        status.setTextColor(0xffa52714);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setBackgroundColor(Color.WHITE);
        preview.setContentDescription("پیش‌نمایش فیش");
        preview.setOnClickListener(view -> editLayout());
        preview.setContentDescription("پیش‌نمایش فیش، برای ویرایش چیدمان ضربه بزنید");
        previewPanel.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        TextView credit = label(layout, "سازنده: علی یوسفی پور");
        credit.setGravity(android.view.Gravity.CENTER);
        credit.setPadding(0, dp(16), 0, dp(8));

        try {
            String[] assets = getAssets().list("templates");
            if (assets != null) for (String name : assets) {
                try {
                    add(new Template(this, "templates/" + name, null), "asset:" + name);
                } catch (Exception e) {
                    android.util.Log.w("Fishzan", "Could not load template " + name, e);
                }
            }
        } catch (java.io.IOException e) {
            android.util.Log.w("Fishzan", "Could not list bundled templates", e);
        }
        File[] imported = getDir("templates", MODE_PRIVATE).listFiles();
        if (imported != null) {
            java.util.Arrays.sort(imported);
            for (File dir : imported) {
                try { add(new Template(this, null, dir), "file:" + dir.getName()); }
                catch (Exception e) { android.util.Log.w("Fishzan", "Could not load imported template", e); }
            }
        }
        restoreCopies();
        if (templates.isEmpty()) status.setText("قالبی پیدا نشد. یک فایل ZIP قالب وارد کنید.");
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        picker.setAdapter(adapter);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View row, int position, long id) {
                select(position);
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable text) {}
        };
        amount.addTextChangedListener(watcher);
        date.addTextChangedListener(watcher);
        tracking.addTextChangedListener(watcher);
        reference.addTextChangedListener(watcher);
        updateDocumentActions(false);
        if (!templates.isEmpty()) {
            int index = state == null ? 0 : Math.min(state.getInt("selection"), templates.size() - 1);
            if (state != null) {
                int restored = templateIds.indexOf(state.getString("selectedTemplate"));
                if (restored >= 0) index = restored;
                amount.setText(state.getString("amount"));
                tracking.setText(state.getString("tracking", ""));
                reference.setText(state.getString("reference", ""));
            }
            picker.setSelection(index);
            select(index);
            String draft = state == null ? null : state.getString("layoutDraft");
            if (draft != null) {
                JSONArray edited = templateLabels();
                try { edited = new JSONArray(state.getString("layoutLabels", edited.toString())); }
                catch (JSONException ignored) {}
                showLayoutEditor(index, new TemplateLayout(draft, this::loadLogo), edited,
                    state.getString("layoutNote", templateNote()));
            }
        }
        if (state != null && state.getBundle("assetDraft") != null) {
            openAssetLabel(state.getBundle("assetDraft"));
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (pendingPdf != null) state.putString("pendingPdf", pendingPdf.getName());
        if (assetDialog != null) state.putBundle("assetDraft", assetDialog.draft());
        state.putInt("selection", Math.max(0, selection));
        if (selection >= 0) state.putString("selectedTemplate", templateIds.get(selection));
        if (layoutEditor != null) {
            try { state.putString("layoutDraft", layoutEditor.layout().json()); }
            catch (JSONException ignored) {}
            state.putString("layoutLabels", layoutEditor.labels().toString());
            state.putString("layoutNote", layoutEditor.note());
        }
        state.putString("amount", amount.getText().toString());
        state.putLong("selectedDate", selectedDate.getTimeInMillis());
        state.putString("tracking", tracking.getText().toString());
        state.putString("reference", reference.getText().toString());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout panel(LinearLayout parent, String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(14));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(Color.WHITE);
        surface.setCornerRadius(dp(14));
        surface.setStroke(dp(1), 0xffd6e0ee);
        panel.setBackground(surface);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        parent.addView(panel, params);
        TextView heading = label(panel, title);
        heading.setTextSize(21);
        heading.setPadding(0, 0, 0, dp(6));
        return panel;
    }

    private TextView label(LinearLayout layout, String caption) {
        TextView text = new TextView(this);
        text.setText(caption);
        text.setTypeface(homa);
        text.setTextSize(16);
        text.setTextColor(0xff10284c);
        layout.addView(text);
        return text;
    }

    private EditText input(LinearLayout layout, String hint, int type, int length) {
        EditText field = new EditText(this);
        field.setTypeface(homa);
        field.setSingleLine(true);
        field.setInputType(type);
        if (type == InputType.TYPE_CLASS_NUMBER) {
            field.setKeyListener(DigitsKeyListener.getInstance("0123456789۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩"));
            field.setRawInputType(type);
        }
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
        field.setTextDirection(View.TEXT_DIRECTION_LTR);
        field.setHint(hint);
        field.setMinHeight(dp(50));
        field.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), 0xff9aabc4);
        field.setBackground(background);
        layout.addView(field, new LinearLayout.LayoutParams(-1, -2));
        return field;
    }

    private Button button(LinearLayout layout, String caption, Runnable action) {
        Button button = new Button(this);
        button.setTypeface(homa);
        button.setText(caption);
        button.setOnClickListener(view -> action.run());
        button.setMinHeight(dp(48));
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = layout.getOrientation() == LinearLayout.HORIZONTAL
            ? new LinearLayout.LayoutParams(0, -2, 1)
            : new LinearLayout.LayoutParams(-1, -2);
        layout.addView(button, params);
        return button;
    }

    private LinearLayout row(LinearLayout layout) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void chooseTime() {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        int[] fields = {Calendar.HOUR_OF_DAY, Calendar.MINUTE, Calendar.SECOND};
        String[] captions = {"ساعت", "دقیقه", "ثانیه"};
        NumberPicker[] numbers = new NumberPicker[fields.length];
        for (int i = 0; i < fields.length; i++) {
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setGravity(android.view.Gravity.CENTER);
            row.addView(group, new LinearLayout.LayoutParams(0, -2, 1));
            label(group, captions[i]);
            NumberPicker number = new NumberPicker(this);
            number.setMinValue(0);
            number.setMaxValue(i == 0 ? 23 : 59);
            number.setFormatter(value -> String.format(new Locale("fa"), "%02d", value));
            number.setValue(selectedDate.get(fields[i]));
            number.setContentDescription(captions[i]);
            group.addView(number);
            numbers[i] = number;
        }
        new AlertDialog.Builder(this).setTitle("انتخاب زمان")
            .setView(row).setNegativeButton("انصراف", null).setPositiveButton("تأیید", (dialog, which) -> {
                for (int i = 0; i < fields.length; i++) {
                    numbers[i].clearFocus();
                    selectedDate.set(fields[i], numbers[i].getValue());
                }
                updateDate();
            }).show();
    }

    private void updateDate() {
        String day = String.format(Locale.US, "%04d/%02d/%02d", selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH) + 1, selectedDate.get(Calendar.DAY_OF_MONTH));
        String clock = String.format(Locale.US, "%02d:%02d:%02d", selectedDate.get(Calendar.HOUR_OF_DAY),
            selectedDate.get(Calendar.MINUTE), selectedDate.get(Calendar.SECOND));
        dateButton.setText("تاریخ  " + persianDigits(day));
        timeButton.setText("ساعت  " + persianDigits(clock));
        date.setText(day + " " + clock);
    }

    private String persianDigits(String text) {
        StringBuilder value = new StringBuilder();
        for (char c : text.toCharArray()) value.append(c >= '0' && c <= '9' ? (char) ('۰' + c - '0') : c);
        return value.toString();
    }

    private void add(Template template, String id) {
        templates.add(template);
        templateIds.add(id);
        names.add(getPreferences(MODE_PRIVATE).getString(id + ":name", template.name));
    }

    private void restoreCopies() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        List<String> ids = new ArrayList<>(prefs.getStringSet("copies", java.util.Collections.emptySet()));
        java.util.Collections.sort(ids);
        for (String id : ids) {
            int original = templateIds.indexOf(prefs.getString(id + ":source", ""));
            if (original >= 0) add(templates.get(original), id);
        }
    }

    private String templateNote() {
        return getPreferences(MODE_PRIVATE).getString(templateIds.get(selection) + ":note", "");
    }

    private JSONArray templateLabels() {
        String json = getPreferences(MODE_PRIVATE).getString(templateIds.get(selection) + ":labels", "[]");
        try { return new JSONArray(json); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private void editTemplate() {
        if (selection < 0) return;
        int index = selection;
        String id = templateIds.get(index);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        fields.setPadding(dp(20), dp(8), dp(20), dp(8));
        label(fields, "نام قالب");
        EditText name = input(fields, "", InputType.TYPE_CLASS_TEXT, 60);
        name.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        name.setText(names.get(index));
        label(fields, "متن پایین فیش");
        EditText note = input(fields, "", InputType.TYPE_CLASS_TEXT, 100);
        note.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        note.setText(templateNote());
        List<EditText> editable = new ArrayList<>();
        List<String> defaults = templates.get(index).labelTexts();
        JSONArray edits = templateLabels();
        if (!defaults.isEmpty()) label(fields, "نوشته‌های ثابت فیش");
        for (int i = 0; i < defaults.size(); i++) {
            EditText item = input(fields, defaults.get(i), InputType.TYPE_CLASS_TEXT, 140);
            item.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            item.setText(edits.optString(i, defaults.get(i)));
            item.setContentDescription(defaults.get(i));
            editable.add(item);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ویرایش قالب")
            .setView(scroll).setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty()) {
                name.setError("نام قالب را وارد کنید.");
                return;
            }
            JSONArray saved = new JSONArray();
            for (EditText item : editable) saved.put(item.getText().toString().trim());
            getPreferences(MODE_PRIVATE).edit().putString(id + ":name", value)
                .putString(id + ":note", note.getText().toString().trim())
                .putString(id + ":labels", saved.toString()).apply();
            names.set(index, value);
            adapter.notifyDataSetChanged();
            refresh();
            dialog.dismiss();
        });
    }

    private TemplateLayout loadLayout(int index) {
        String key = templateIds.get(index) + ":layout";
        String json = getPreferences(MODE_PRIVATE).getString(key, "");
        if (!json.isEmpty()) return new TemplateLayout(json, this::loadLogo);
        TemplateLayout layout = new TemplateLayout();
        String legacy = getPreferences(MODE_PRIVATE).getString("storeLogo", "");
        if (!legacy.isEmpty()) {
            Uri uri = Uri.parse(legacy);
            try {
                Bitmap bitmap = loadLogo(uri);
                layout.addLogo(uri, bitmap, templates.get(index).logoPosition(bitmap, 0, true));
            } catch (java.io.IOException | SecurityException ignored) {}
        }
        return layout;
    }

    private void editLayout() {
        if (selection < 0) return;
        showLayoutEditor(selection, loadLayout(selection), templateLabels(), templateNote());
    }

    private void showLayoutEditor(int index, TemplateLayout draft, JSONArray labels, String note) {
        layoutEditor = new LayoutEditor(this, templates.get(index), draft,
            labels, note, this::chooseTemplateImage, () -> saveLayout(index, null), () -> {
                EditText name = new EditText(this);
                name.setSingleLine();
                name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
                name.setHint("نام قالب جدید");
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ذخیره با نام جدید")
                    .setView(name).setNegativeButton("انصراف", null).setPositiveButton("ذخیره", null).create();
                dialog.show();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String title = name.getText().toString().trim();
                    if (title.isEmpty() || names.contains(title)) {
                        name.setError("نام تازه‌ای برای قالب وارد کنید.");
                        return;
                    }
                    if (saveLayout(index, title)) dialog.dismiss();
                });
            }, () -> layoutEditor = null);
    }

    private boolean saveLayout(int index, String copyName) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String id = templateIds.get(index);
        String target = id;
        TemplateLayout edited = layoutEditor.layout();
        android.content.SharedPreferences.Editor editor = prefs.edit();
        try {
            if (copyName != null) {
                target = "copy:" + UUID.randomUUID();
                java.util.Set<String> copies = new java.util.HashSet<>(
                    prefs.getStringSet("copies", java.util.Collections.emptySet()));
                copies.add(target);
                editor.putStringSet("copies", copies).putString(target + ":name", copyName)
                    .putString(target + ":source", prefs.getString(id + ":source", id));
            }
            boolean saved = editor.putString(target + ":layout", edited.json())
                .putString(target + ":labels", layoutEditor.labels().toString())
                .putString(target + ":note", layoutEditor.note()).commit();
            if (!saved) throw new java.io.IOException("save");
            layoutEditor.dismiss();
            if (copyName == null) {
                activeLayout = edited;
                refresh();
            } else {
                add(templates.get(index), target);
                adapter.notifyDataSetChanged();
                picker.setSelection(templates.size() - 1);
                select(templates.size() - 1);
            }
            return true;
        } catch (JSONException | java.io.IOException e) {
            Toast.makeText(this, "ذخیرهٔ چیدمان ممکن نبود.", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static final String BUILTIN_LOGO = "fishzan-logo";
    private static final String[] LOGO_FILES = {"pasargad.png", "behpardakht.png", "samankish.png",
        "sadad.png", "parsian.png", "shaparak.jpg"};
    private static final String[] LOGO_NAMES = {"پاسارگاد", "به‌پرداخت", "سامان کیش", "سداد", "پارسیان", "شاپرک"};

    private void chooseTemplateImage() {
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        items.setPadding(dp(16), 0, dp(16), 0);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(items);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("افزودن تصویر یا لوگو")
            .setView(scroll).setNegativeButton("انصراف", null)
            .setNeutralButton("انتخاب از گوشی", (window, which) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, 2);
            }).create();
        for (int i = 0; i < LOGO_FILES.length; i++) {
            Uri uri = new Uri.Builder().scheme(BUILTIN_LOGO).path("/" + LOGO_FILES[i]).build();
            try {
                Bitmap bitmap = loadLogo(uri);
                LinearLayout item = row(items);
                item.setGravity(android.view.Gravity.CENTER_VERTICAL);
                ImageView thumbnail = new ImageView(this);
                thumbnail.setImageBitmap(bitmap);
                thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
                thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                item.addView(thumbnail, new LinearLayout.LayoutParams(dp(64), dp(42)));
                button(item, LOGO_NAMES[i], () -> {
                    if (layoutEditor != null) layoutEditor.addLogo(uri, bitmap);
                    dialog.dismiss();
                });
            } catch (java.io.IOException e) {
                android.util.Log.w("Fishzan", "Built-in logo unavailable", e);
            }
        }
        dialog.show();
    }

    private Bitmap loadLogo(Uri uri) throws java.io.IOException {
        Bitmap bitmap = loadedLogos.get(uri);
        if (bitmap == null) {
            bitmap = readLogo(uri);
            if (loadedLogos.size() >= 12) loadedLogos.clear();
            loadedLogos.put(uri, bitmap);
        }
        return bitmap;
    }

    private void select(int index) {
        Template template = templates.get(index);
        if (selection != index || activeLayout == null) activeLayout = loadLayout(index);
        selection = index;
        showNumber(trackingLabel, tracking, template.tracking);
        showNumber(referenceLabel, reference, template.reference);
        refresh();
    }

    private void showNumber(TextView label, EditText input, org.json.JSONArray field) {
        int visibility = field == null ? View.GONE : View.VISIBLE;
        label.setVisibility(visibility);
        input.setVisibility(visibility);
        if (field != null) input.setHint(String.format("%d رقم", field.optInt(6)));
    }

    private boolean matchesLength(String value, int length) {
        return value.length() == length && value.matches("[0-9]+");
    }

    private String ascii(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.trim().toCharArray()) {
            int digit = Character.digit(c, 10);
            result.append(digit >= 0 ? (char) ('0' + digit) : c);
        }
        return result.toString();
    }

    private void refresh() {
        if (selection < 0) return;
        String money = ascii(amount.getText().toString());
        String timestamp = ascii(date.getText().toString());
        boolean validAmount = money.matches("[0-9]{1,12}") && Long.parseLong(money) > 0;
        boolean validDate = timestamp.matches("(?:[0-9]{2}|[0-9]{4})/(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01]) ([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]");
        Template template = templates.get(selection);
        String trace = ascii(tracking.getText().toString());
        String ref = ascii(reference.getText().toString());
        int traceLength = template.tracking == null ? 0 : template.tracking.optInt(6);
        int refLength = template.reference == null ? 0 : template.reference.optInt(6);
        boolean validTrace = traceLength == 0 || matchesLength(trace, traceLength);
        boolean validRef = refLength == 0 || matchesLength(ref, refLength);
        String warning = !money.isEmpty() && !validAmount ? "مبلغ باید عدد مثبت باشد." :
            !timestamp.isEmpty() && !validDate ? "تاریخ و ساعت را به شکل نمونه وارد کنید." :
            !trace.isEmpty() && !validTrace ? String.format("شمارهٔ پیگیری باید %d رقم باشد.", traceLength) :
            !ref.isEmpty() && !validRef ? String.format("شمارهٔ مرجع باید %d رقم باشد.", refLength) : "";
        try {
            receipt = template.render(validAmount ? money : "", validDate ? timestamp : "",
                validTrace ? trace : "", validRef ? ref : "", activeLayout, templateNote(), templateLabels(), 1);
        } catch (IllegalArgumentException e) {
            warning = e.getMessage();
            validAmount = false;
            receipt = template.render("", validDate ? timestamp : "",
                validTrace ? trace : "", validRef ? ref : "", activeLayout, templateNote(), templateLabels(), 1);
        }
        preview.setImageBitmap(receipt);
        status.setText(warning);
        updateDocumentActions(validAmount && validDate && validTrace && validRef);
    }

    private void updateDocumentActions(boolean enabled) {
        print.setEnabled(enabled);
        saveReceipt.setEnabled(enabled);
        savePdf.setEnabled(enabled);
    }

    private String currentTitle() { return "فیش " + names.get(selection); }

    private PrintPage currentPage() {
        Template template = templates.get(selection);
        String money = ascii(amount.getText().toString());
        String timestamp = date.getText().toString();
        String trace = ascii(tracking.getText().toString());
        String ref = ascii(reference.getText().toString());
        TemplateLayout layout = activeLayout;
        String note = templateNote();
        JSONArray labels = templateLabels();
        return new PrintPage(template.widthMm, template.heightMm, template.paperWidth, template.paperHeight,
            canvas -> template.drawPage(canvas, money, timestamp, trace, ref, layout, note, labels, null));
    }

    private void print() {
        PrintPage page = currentPage();
        String title = currentTitle();
        new AlertDialog.Builder(this).setTitle("روش چاپ")
            .setItems(new String[]{"چاپ با RawBT برای Zebra ZQ110", "چاپ PDF با سرویس اندروید"},
                (dialog, which) -> {
                    if (which == 0) sendRawBt(page);
                    else page.androidPrint(this, filename(title));
                }).show();
    }

    private String filename(String title) {
        String safe = title.replaceAll("[^\\p{L}\\p{N} _-]", " ").trim();
        return (safe.isEmpty() ? "فیش" : safe) + ".pdf";
    }

    private void sendRawBt(PrintPage page) {
        try {
            ZebraPrint.send(this, ZebraPrint.intent(page));
        } catch (java.io.IOException | RuntimeException e) {
            android.util.Log.w("Fishzan", "RawBT handoff failed", e);
            Toast.makeText(this, "ارسال انجام نشد. RawBT و تنظیمات چاپگر را بررسی کنید.", Toast.LENGTH_LONG).show();
        }
    }

    private void archiveCurrent() {
        PrintPage page = currentPage();
        JSONObject details = new JSONObject();
        try {
            details.put("template", templateIds.get(selection)).put("amount", amount.getText().toString())
                .put("date", selectedDate.getTimeInMillis()).put("tracking", tracking.getText().toString())
                .put("reference", reference.getText().toString());
        } catch (JSONException e) { throw new IllegalStateException(e); }
        EditText title = new EditText(this);
        title.setTypeface(homa);
        title.setSingleLine();
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        title.setText(currentTitle());
        title.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ثبت فیش در بایگانی")
            .setView(title).setNegativeButton("انصراف", null).setPositiveButton("ذخیره", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = title.getText().toString().trim();
            if (name.isEmpty()) { title.setError("عنوان فیش را وارد کنید."); return; }
            try {
                archive.save(page, name, details);
                dialog.dismiss();
                Toast.makeText(this, "فیش در بایگانی ثبت شد.", Toast.LENGTH_SHORT).show();
            } catch (JSONException | java.io.IOException | RuntimeException e) {
                android.util.Log.w("Fishzan", "Could not save receipt", e);
                Toast.makeText(this, "ذخیرهٔ فیش انجام نشد.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openArchive() {
        List<ReceiptArchive.Entry> entries = archive.list();
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("بایگانی فیش‌ها")
                .setMessage("هنوز فیشی ذخیره نشده است. از «ثبت در بایگانی» استفاده کنید.")
                .setPositiveButton("بستن", null).show();
            return;
        }
        String[] titles = new String[entries.size()];
        android.icu.text.DateFormat formatter = new android.icu.text.SimpleDateFormat("yyyy/MM/dd  HH:mm",
            new android.icu.util.ULocale("fa_IR@calendar=persian"));
        for (int i = 0; i < titles.length; i++) {
            ReceiptArchive.Entry entry = entries.get(i);
            titles[i] = entry.title + "  •  " + formatter.format(new java.util.Date(entry.timestamp));
        }
        new AlertDialog.Builder(this).setTitle("بایگانی فیش‌ها").setItems(titles,
            (dialog, which) -> openReceipt(entries.get(which))).setNegativeButton("بستن", null).show();
    }

    private void openReceipt(ReceiptArchive.Entry entry) {
        List<String> actions = new ArrayList<>(java.util.Arrays.asList(
            "مشاهدهٔ فیش", "چاپ مجدد با RawBT", "چاپ PDF با اندروید", "ذخیرهٔ PDF"));
        int source = templateIds.indexOf(entry.details.optString("template"));
        if (source >= 0) actions.add("بازگردانی اطلاعات به فرم");
        new AlertDialog.Builder(this).setTitle(entry.title)
            .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                switch (which) {
                    case 0: previewSaved(entry); break;
                    case 1: sendRawBt(entry.page); break;
                    case 2: entry.page.androidPrint(this, filename(entry.title)); break;
                    case 3: exportPdf(entry.page, entry.title); break;
                    case 4:
                        picker.setSelection(source);
                        select(source);
                        amount.setText(entry.details.optString("amount"));
                        tracking.setText(entry.details.optString("tracking"));
                        reference.setText(entry.details.optString("reference"));
                        selectedDate.setTimeInMillis(entry.details.optLong("date", System.currentTimeMillis()));
                        updateDate();
                        break;
                    default: break;
                }
            }).setNegativeButton("بستن", null).show();
    }

    private void previewSaved(ReceiptArchive.Entry entry) {
        try {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setContentDescription(entry.title);
            double scale = Math.min(550 / entry.page.widthMm, 2200 / entry.page.heightMm);
            image.setImageBitmap(entry.page.bitmap((int) (scale * entry.page.widthMm),
                (int) (scale * entry.page.heightMm)));
            ScrollView scroll = new ScrollView(this);
            scroll.addView(image, new ScrollView.LayoutParams(-1, -2));
            new AlertDialog.Builder(this).setTitle(entry.title).setView(scroll)
                .setPositiveButton("بستن", null).show();
        } catch (java.io.IOException | RuntimeException e) {
            Toast.makeText(this, "پیش‌نمایش PDF باز نشد.", Toast.LENGTH_LONG).show();
        }
    }

    private void exportPdf(PrintPage page, String title) {
        File temporary = null;
        try {
            temporary = File.createTempFile("fishzan-", ".pdf", getCacheDir());
            try (FileOutputStream output = new FileOutputStream(temporary)) { page.writePdf(output); }
            pendingPdf = temporary;
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/pdf")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, filename(title));
            startActivityForResult(create, 3);
        } catch (java.io.IOException | RuntimeException e) {
            if (temporary != null) temporary.delete();
            pendingPdf = null;
            Toast.makeText(this, "فایل PDF ساخته نشد.", Toast.LENGTH_LONG).show();
        }
    }

    private void finishExport(int result, Intent data) {
        File temporary = pendingPdf;
        pendingPdf = null;
        if (temporary == null) return;
        try {
            if (result != RESULT_OK || data == null || data.getData() == null) return;
            try (java.io.OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                if (output == null) throw new java.io.IOException("destination");
                new PrintPage(0, 0, temporary).writePdf(output);
            }
            Toast.makeText(this, "فایل PDF ذخیره شد.", Toast.LENGTH_SHORT).show();
        } catch (java.io.IOException | RuntimeException e) {
            Toast.makeText(this, "نوشتن فایل PDF انجام نشد.", Toast.LENGTH_LONG).show();
        } finally {
            temporary.delete();
        }
    }

    private void openAssetLabel() { openAssetLabel(null); }

    private void openAssetLabel(Bundle draft) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String logo = draft == null ? prefs.getString("assetLogo", "") : draft.getString("logo", "");
        Bitmap bitmap = null;
        if (!logo.isEmpty()) {
            try { bitmap = loadLogo(Uri.parse(logo)); }
            catch (java.io.IOException | SecurityException ignored) {}
        }
        double width = selection >= 0 ? templates.get(selection).widthMm : 55.0333333;
        if (draft != null) width = draft.getDouble("widthMm", width);
        assetDialog = new AssetLabelDialog(this, prefs, width, homa, bitmap, draft, () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 4);
        }, (page, value) -> {
            ZebraPrint.send(this, ZebraPrint.intent(page));
            try {
                archive.save(page, "شماره اموال " + value, new JSONObject().put("assetNumber", value));
            } catch (java.io.IOException | JSONException | RuntimeException e) {
                android.util.Log.w("Fishzan", "Could not archive sent asset label", e);
            }
        }, this::exportPdf, () -> assetDialog = null);
    }

    private Bitmap readLogo(Uri uri) throws java.io.IOException {
        if (BUILTIN_LOGO.equals(uri.getScheme())) {
            String filename = uri.getLastPathSegment();
            if (!java.util.Arrays.asList(LOGO_FILES).contains(filename)) {
                throw new java.io.IOException("unknown built-in logo");
            }
            try (InputStream input = getAssets().open("logos/" + filename)) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap == null) throw new java.io.IOException("built-in logo");
                return bitmap;
            }
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, options);
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) {
            options.inSampleSize *= 2;
        }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            if (bitmap == null) throw new java.io.IOException("logo");
            return bitmap;
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 3) { finishExport(result, data); return; }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == 4) {
            if (assetDialog == null) return;
            Uri uri = data.getData();
            try {
                Bitmap bitmap = loadLogo(uri);
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                assetDialog.setLogo(uri, bitmap);
            } catch (java.io.IOException | SecurityException e) {
                Toast.makeText(this, "تصویر نشان باز نشد.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (request == 2) {
            if (layoutEditor == null) return;
            Uri uri = data.getData();
            try {
                Bitmap selected = loadLogo(uri);
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                layoutEditor.addLogo(uri, selected);
            } catch (java.io.IOException | SecurityException e) {
                Toast.makeText(this, "باز کردن تصویر لوگو ممکن نبود.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (request != 1) return;
        importTemplates(data.getData());
    }

    private void importTemplates(Uri uri) {
        File storage = getDir("templates", MODE_PRIVATE);
        java.util.Map<String, File> folders = new java.util.LinkedHashMap<>();
        List<Template> incoming = new ArrayList<>();
        try {
            long total = 0;
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory()) continue;
                    String[] path = name.split("/", -1);
                    if (path.length > 2 || (path.length == 2 && !path[0].matches("[A-Za-z0-9_-]+"))) {
                        throw new java.io.IOException("folder");
                    }
                    String file = path[path.length - 1];
                    if (!(file.equals("template.json") || file.matches("[A-Za-z0-9_-]+[.]ttf") || file.equals("paper.png"))) {
                        throw new java.io.IOException("entry");
                    }
                    String key = path.length == 1 ? "" : path[0];
                    File folder = folders.get(key);
                    if (folder == null) {
                        if (folders.size() >= 50) throw new java.io.IOException("count");
                        folder = new File(storage, UUID.randomUUID().toString());
                        if (!folder.mkdir()) throw new java.io.IOException("mkdir");
                        folders.put(key, folder);
                    }
                    File target = new File(folder, file);
                    if (!target.createNewFile()) throw new java.io.IOException("duplicate");
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            total += read;
                            if (total > 96 * 1024 * 1024) throw new java.io.IOException("size");
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }
            if (folders.isEmpty()) throw new java.io.IOException("empty");
            for (File folder : folders.values()) incoming.add(new Template(this, null, folder));
            int first = templates.size();
            int index = 0;
            for (File folder : folders.values()) add(incoming.get(index++), "file:" + folder.getName());
            adapter.notifyDataSetChanged();
            picker.setSelection(first);
            select(first);
            Toast.makeText(this, String.format("%d قالب وارد شد.", incoming.size()), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.w("Fishzan", "Could not import templates", e);
            for (File folder : folders.values()) {
                File[] files = folder.listFiles();
                if (files != null) for (File file : files) file.delete();
                folder.delete();
            }
            Toast.makeText(this, "بستهٔ ZIP قالب باز نشد. ساختار و فایل‌های آن را بررسی کنید.", Toast.LENGTH_LONG).show();
        }
    }
}

