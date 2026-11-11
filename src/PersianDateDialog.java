package ir.fishzan.app;

import android.app.AlertDialog;
import android.content.Context;
import android.icu.util.Calendar;
import android.icu.util.ULocale;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;

final class PersianDateDialog {
    private static final String[] MONTHS = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
        "مهر","آبان","آذر","دی","بهمن","اسفند"};
    private static final String[] WEEK = {"ش","ی","د","س","چ","پ","ج"};

    static Calendar calendar() {
        return Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
    }

    static void show(Context context, Calendar selected, Consumer<Calendar> chosen) {
        Calendar month = (Calendar) selected.clone();
        month.set(Calendar.DAY_OF_MONTH, 1);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(12 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("انتخاب تاریخ شمسی")
            .setView(content).setNegativeButton("انصراف", null)
            .setNeutralButton("امروز", (ignored, which) -> chosen.accept(calendar())).create();
        render(context, content, month, selected, dialog, chosen);
        dialog.show();
    }

    private static void render(Context context, LinearLayout content, Calendar month,
        Calendar selected, AlertDialog dialog, Consumer<Calendar> chosen) {
        content.removeAllViews();
        LinearLayout header = new LinearLayout(context);
        content.addView(header);
        Button previous = new Button(context);
        previous.setText("قبلی");
        header.addView(previous);
        previous.setOnClickListener(view -> {
            month.add(Calendar.MONTH, -1);
            render(context, content, month, selected, dialog, chosen);
        });
        Button title = new Button(context, null, android.R.attr.borderlessButtonStyle);
        title.setGravity(android.view.Gravity.CENTER);
        title.setText(MONTHS[month.get(Calendar.MONTH)] + " " + digits(month.get(Calendar.YEAR)));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        title.setOnClickListener(view -> chooseMonth(context, month,
            () -> render(context, content, month, selected, dialog, chosen)));
        Button next = new Button(context);
        next.setText("بعدی");
        header.addView(next);
        next.setOnClickListener(view -> {
            month.add(Calendar.MONTH, 1);
            render(context, content, month, selected, dialog, chosen);
        });

        LinearLayout days = new LinearLayout(context);
        content.addView(days);
        for (String name : WEEK) {
            TextView day = new TextView(context);
            day.setGravity(android.view.Gravity.CENTER);
            day.setText(name);
            days.addView(day, new LinearLayout.LayoutParams(0, -2, 1));
        }
        int first = month.get(Calendar.DAY_OF_WEEK) % 7;
        int last = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int offset = 0; offset < first + last; offset += 7) {
            LinearLayout week = new LinearLayout(context);
            content.addView(week);
            for (int col = 0; col < 7; col++) {
                int day = offset + col - first + 1;
                if (day < 1 || day > last) {
                    week.addView(new android.view.View(context), new LinearLayout.LayoutParams(0, 0, 1));
                    continue;
                }
                Button button = new Button(context, null, android.R.attr.borderlessButtonStyle);
                button.setText(digits(day));
                button.setPadding(0, 0, 0, 0);
                if (selected.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
                    selected.get(Calendar.MONTH) == month.get(Calendar.MONTH) &&
                    selected.get(Calendar.DAY_OF_MONTH) == day) {
                    android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
                    fill.setColor(0xff246456);
                    fill.setCornerRadius(18);
                    button.setBackground(fill);
                    button.setTextColor(android.graphics.Color.WHITE);
                }
                week.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
                button.setOnClickListener(view -> {
                    month.set(Calendar.DAY_OF_MONTH, day);
                    chosen.accept(month);
                    dialog.dismiss();
                });
            }
        }
    }

    private static void chooseMonth(Context context, Calendar month, Runnable redraw) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        android.widget.Spinner months = new android.widget.Spinner(context);
        android.widget.ArrayAdapter<String> options = new android.widget.ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, MONTHS);
        months.setAdapter(options);
        months.setSelection(month.get(Calendar.MONTH));
        form.addView(months);
        android.widget.EditText year = new android.widget.EditText(context);
        year.setSingleLine();
        year.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        year.setHint("سال شمسی");
        year.setText(Integer.toString(month.get(Calendar.YEAR)));
        year.setSelectAllOnFocus(true);
        form.addView(year);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("ماه و سال")
            .setView(form).setNegativeButton("انصراف", null).setPositiveButton("نمایش", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int value;
            try { value = Integer.parseInt(year.getText().toString().trim()); }
            catch (NumberFormatException e) { value = 0; }
            if (value < 1000 || value > 1999) {
                year.setError("سال چهار رقمی وارد کنید.");
                return;
            }
            month.set(Calendar.YEAR, value);
            month.set(Calendar.MONTH, months.getSelectedItemPosition());
            redraw.run();
            dialog.dismiss();
        });
    }

    private static String digits(int value) {
        return String.format(new java.util.Locale("fa"), "%d", value);
    }
}
