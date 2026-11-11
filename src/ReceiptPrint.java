package ir.fishzan.app;

import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import java.io.IOException;
import java.io.OutputStream;

final class ReceiptPrint extends PrintDocumentAdapter {
    interface PageContent {
        void draw(Canvas canvas);
    }

    private final String filename;
    private final Template template;
    private final PageContent content;

    ReceiptPrint(String filename, Template template, PageContent content) {
        this.filename = filename;
        this.template = template;
        this.content = content;
    }

    @Override public void onLayout(PrintAttributes old, PrintAttributes current,
        CancellationSignal cancel, LayoutResultCallback callback, Bundle extras) {
        if (cancel.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        callback.onLayoutFinished(new PrintDocumentInfo.Builder(filename)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),
            !current.equals(old));
    }

    @Override public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
        CancellationSignal cancel, WriteResultCallback callback) {
        new Thread(() -> {
            try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(destination)) {
                if (!cancel.isCanceled()) {
                    writePdf(output, cancel);
                }
            } catch (IOException | RuntimeException e) {
                if (cancel.isCanceled()) callback.onWriteCancelled();
                else callback.onWriteFailed("ساخت فایل چاپ ناموفق بود.");
                return;
            }
            if (cancel.isCanceled()) callback.onWriteCancelled();
            else callback.onWriteFinished(new PageRange[]{new PageRange(0, 0)});
        }, "receipt-print").start();
    }

    private void writePdf(OutputStream output, CancellationSignal cancel) throws IOException {
        int width = Math.max(1, (int) Math.round(template.widthMm * 72 / 25.4));
        int height = Math.max(1, (int) Math.round(template.heightMm * 72 / 25.4));
        PdfDocument pdf = new PdfDocument();
        try {
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(width, height, 1).create());
            try {
                Canvas canvas = page.getCanvas();
                canvas.scale((float) width / template.paperWidth, (float) height / template.paperHeight);
                content.draw(canvas);
            } finally {
                pdf.finishPage(page);
            }
            if (!cancel.isCanceled()) pdf.writeTo(output);
        } finally {
            pdf.close();
        }
    }
}
