package ir.fishzan.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

final class PrintPage {
    interface Drawing { void draw(Canvas canvas); }

    final double widthMm, heightMm;
    private final Drawing drawing;
    private final int width, height;
    private final File pdf;

    PrintPage(double widthMm, double heightMm, int width, int height, Drawing drawing) {
        this(widthMm, heightMm, width, height, drawing, null);
    }

    PrintPage(double widthMm, double heightMm, File pdf) {
        this(widthMm, heightMm, 0, 0, null, pdf);
    }

    private PrintPage(double widthMm, double heightMm, int width, int height, Drawing drawing, File pdf) {
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.width = width;
        this.height = height;
        this.drawing = drawing;
        this.pdf = pdf;
    }

    void androidPrint(Activity activity, String name) {
        PrintManager manager = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
        PrintAttributes attributes = new PrintAttributes.Builder()
            .setMediaSize(new PrintAttributes.MediaSize("fishzan", "Fishzan",
                (int) Math.round(widthMm / 25.4 * 1000), (int) Math.round(heightMm / 25.4 * 1000)))
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();
        manager.print(name, new ReceiptPrint(name, this), attributes);
    }

    void writePdf(OutputStream output) throws IOException {
        if (pdf != null) {
            try (FileInputStream input = new FileInputStream(pdf)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            return;
        }
        int pointsWide = (int) Math.round(widthMm * 72 / 25.4);
        int pointsHigh = (int) Math.round(heightMm * 72 / 25.4);
        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pointsWide, pointsHigh, 1).create());
            try { draw(page.getCanvas(), pointsWide, pointsHigh); }
            finally { document.finishPage(page); }
            document.writeTo(output);
        } finally {
            document.close();
        }
    }

    Bitmap bitmap(int pixelsWide, int pixelsHigh) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(pixelsWide, pixelsHigh, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(android.graphics.Color.WHITE);
        try {
            if (pdf == null) draw(new Canvas(bitmap), pixelsWide, pixelsHigh);
            else {
                ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer;
                try { renderer = new PdfRenderer(fd); }
                catch (IOException | RuntimeException e) {
                    fd.close();
                    throw e;
                }
                try (PdfRenderer resource = renderer; PdfRenderer.Page page = resource.openPage(0)) {
                    Matrix fit = new Matrix();
                    fit.setScale((float) pixelsWide / page.getWidth(), (float) pixelsHigh / page.getHeight());
                    page.render(bitmap, null, fit, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                }
            }
            return bitmap;
        } catch (IOException | RuntimeException e) {
            bitmap.recycle();
            throw e;
        }
    }

    private void draw(Canvas canvas, int targetWidth, int targetHeight) {
        canvas.scale((float) targetWidth / width, (float) targetHeight / height);
        drawing.draw(canvas);
    }
}
