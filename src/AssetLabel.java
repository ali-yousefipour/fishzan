package ir.fishzan.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

final class AssetLabel {
    private static final int WIDTH = 384;
    private static final int HEIGHT = 120;

    static PrintPage page(double widthMm, String company, String phone, long number, Bitmap logo, Typeface font) {
        return new PrintPage(widthMm, 15, WIDTH, HEIGHT,
            canvas -> draw(canvas, company, phone, number, logo, font));
    }

    private static void draw(Canvas canvas, String company, String phone, long number, Bitmap logo, Typeface font) {
        canvas.drawColor(Color.WHITE);
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        ink.setColor(Color.BLACK);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(2);
        canvas.drawRoundRect(6, 5, 378, 115, 11, 11, ink);
        canvas.drawLine(119, 6, 119, 114, ink);
        canvas.drawLine(119, 61, 377, 61, ink);
        ink.setStyle(Paint.Style.FILL);
        if (logo != null) {
            float scale = Math.min(84f / logo.getWidth(), 83f / logo.getHeight());
            float w = logo.getWidth() * scale;
            float h = logo.getHeight() * scale;
            canvas.drawBitmap(logo, null, new RectF(63 - w / 2, 60 - h / 2, 63 + w / 2, 60 + h / 2), ink);
        } else {
            // The Canva study uses a bold monogram in this replaceable logo slot.
            android.graphics.Path mark = new android.graphics.Path();
            mark.moveTo(29, 87); mark.lineTo(29, 36); mark.lineTo(63, 62);
            mark.lineTo(97, 36); mark.lineTo(97, 87); mark.lineTo(84, 87);
            mark.lineTo(84, 62); mark.lineTo(63, 80); mark.lineTo(42, 62);
            mark.lineTo(42, 87); mark.close();
            canvas.drawPath(mark, ink);
            canvas.drawCircle(63, 39, 10, ink);
        }
        ink.setTypeface(font);
        text(canvas, ink, company, 248, 31, 30, 229);
        text(canvas, ink, phone, 248, 53, 20, 227);
        text(canvas, ink, "شماره اموال", 173, 94, 24, 90);
        ink.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        text(canvas, ink, Long.toString(number), 293, 103, 47, 144);
    }

    private static void text(Canvas canvas, Paint paint, String text, float center, float baseline, int size, float width) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size);
        paint.setTextScaleX(1);
        float measured = paint.measureText(text);
        if (measured > width) paint.setTextScaleX(width / measured);
        canvas.drawText(text, center, baseline, paint);
    }
}
