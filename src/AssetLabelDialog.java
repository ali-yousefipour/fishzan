package ir.fishzan.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;

final class AssetLabelDialog {
    interface PrintAction { void send(PrintPage page, long number) throws IOException; }

    private final Activity activity;
    private final SharedPreferences preferences;
    private final double widthMm;
    private final Typeface font;
    private final EditText company, phone, number;
    private final ImageView preview;
    private Bitmap logo;
    private String logoUri;

    AssetLabelDialog(Activity activity, SharedPreferences prefs, double widthMm, Typeface font, Bitmap logo,
        android.os.Bundle draft, Runnable chooseLogo, PrintAction print,
        java.util.function.BiConsumer<PrintPage, String> export, Runnable close) {
        this.activity = activity;
        this.preferences = prefs;
        this.widthMm = widthMm;
        this.font = font;
        this.logo = logo;
        logoUri = draft == null ? prefs.getString("assetLogo", "") : draft.getString("logo", "");
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        company = field(form, "نام شرکت", draft == null ? prefs.getString("assetCompany", "مبین شات")
            : draft.getString("company", ""), false, 60);
        phone = field(form, "شمارهٔ همراه", draft == null ? prefs.getString("assetPhone", "09120727133")
            : draft.getString("phone", ""), true, 20);
        number = field(form, "شمارهٔ بعدی اموال", draft == null ? Long.toString(prefs.getLong("assetNext", 3000))
            : draft.getString("number", ""), true, 12);

        LinearLayout logos = new LinearLayout(activity);
        form.addView(logos);
        addButton(logos, "انتخاب نشان", chooseLogo, true);
        addButton(logos, "حذف نشان", () -> {
            this.logo = null;
            logoUri = "";
            refresh();
        }, true);
        TextView caption = new TextView(activity);
        caption.setTypeface(font);
        caption.setText("پیش‌نمایش برچسب ۱۵ میلی‌متری");
        caption.setTextSize(17);
        form.addView(caption);
        preview = new ImageView(activity);
        preview.setAdjustViewBounds(true);
        preview.setContentDescription("پیش‌نمایش شماره اموال");
        form.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        addButton(form, "ذخیرهٔ PDF برچسب", () -> {
            long value = validatedNumber();
            if (value > 0) export.accept(page(value), "شماره اموال " + value);
        }, false);
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable text) {}
        };
        company.addTextChangedListener(watcher);
        phone.addTextChangedListener(watcher);
        number.addTextChangedListener(watcher);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("چاپ شماره اموال")
            .setView(scroll).setNegativeButton("بستن", null)
            .setNeutralButton("ثبت تنظیمات", null).setPositiveButton("چاپ RawBT", null).create();
        dialog.setOnDismissListener(ignored -> close.run());
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            long value = validatedNumber();
            if (value > 0 && save(value)) dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            long value = validatedNumber();
            if (value <= 0) return;
            PrintPage page = page(value);
            if (!save(value + 1)) return;
            try {
                print.send(page, value);
                number.setText(Long.toString(value + 1));
                Toast.makeText(activity, "برچسب برای RawBT ارسال شد.", Toast.LENGTH_SHORT).show();
            } catch (IOException | RuntimeException e) {
                save(value);
                Toast.makeText(activity, "ارسال انجام نشد. RawBT و اتصال چاپگر را بررسی کنید.", Toast.LENGTH_LONG).show();
            }
        });
        refresh();
    }

    android.os.Bundle draft() {
        android.os.Bundle data = new android.os.Bundle();
        data.putString("company", company.getText().toString());
        data.putString("phone", phone.getText().toString());
        data.putString("number", number.getText().toString());
        data.putString("logo", logoUri);
        data.putDouble("widthMm", widthMm);
        return data;
    }

    void setLogo(Uri uri, Bitmap bitmap) {
        logo = bitmap;
        logoUri = uri.toString();
        refresh();
    }

    private boolean save(long value) {
        boolean saved = preferences.edit().putString("assetCompany", company.getText().toString().trim())
            .putString("assetPhone", ascii(phone.getText().toString())).putString("assetLogo", logoUri)
            .putLong("assetNext", value).commit();
        if (!saved) Toast.makeText(activity, "تنظیمات ذخیره نشد.", Toast.LENGTH_LONG).show();
        return saved;
    }

    private long validatedNumber() {
        String text = ascii(number.getText().toString());
        if (company.getText().toString().trim().isEmpty()) {
            company.setError("نام شرکت را وارد کنید.");
            return -1;
        }
        if (!ascii(phone.getText().toString()).matches("[0-9]{7,20}")) {
            phone.setError("شمارهٔ تماس را با ارقام وارد کنید.");
            return -1;
        }
        if (!text.matches("[0-9]{1,12}") || Long.parseLong(text) == 0 || Long.parseLong(text) == 999999999999L) {
            number.setError("شمارهٔ مثبت و کمتر از ۹۹۹۹۹۹۹۹۹۹۹۹ وارد کنید.");
            return -1;
        }
        return Long.parseLong(text);
    }

    private PrintPage page(long value) {
        return AssetLabel.page(widthMm, company.getText().toString().trim(), ascii(phone.getText().toString()), value, logo, font);
    }

    private void refresh() {
        String text = ascii(number.getText().toString());
        long value = text.matches("[0-9]{1,12}") ? Long.parseLong(text) : 0;
        try { preview.setImageBitmap(page(value).bitmap(600, (int) Math.round(600 * 15 / widthMm))); }
        catch (IOException ignored) {}
    }

    private EditText field(LinearLayout form, String label, String value, boolean numeric, int length) {
        TextView caption = new TextView(activity);
        caption.setTypeface(font);
        caption.setText(label);
        caption.setTextSize(16);
        form.addView(caption);
        EditText input = new EditText(activity);
        input.setTypeface(font);
        input.setSingleLine();
        if (numeric) {
            input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩"));
            input.setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(length)});
        input.setText(value);
        form.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private void addButton(LinearLayout layout, String text, Runnable action, boolean horizontal) {
        Button button = new Button(activity);
        button.setTypeface(font);
        button.setText(text);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        layout.addView(button, horizontal ? new LinearLayout.LayoutParams(0, -2, 1)
            : new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String ascii(String text) {
        StringBuilder value = new StringBuilder();
        for (char c : text.trim().toCharArray()) {
            int digit = Character.digit(c, 10);
            value.append(digit >= 0 ? (char) ('0' + digit) : c);
        }
        return value.toString();
    }
}
