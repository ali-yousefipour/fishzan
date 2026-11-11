package ir.fishzan.app;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;

final class ReceiptPrint extends PrintDocumentAdapter {
    private final Bitmap receipt;
    private final Template template;

    ReceiptPrint(Bitmap receipt, Template template) {
        this.receipt = receipt;
        this.template = template;
    }

    @Override public void onLayout(PrintAttributes old, PrintAttributes current,
        CancellationSignal cancel, LayoutResultCallback callback, Bundle extras) {
        if (cancel.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        callback.onLayoutFinished(new PrintDocumentInfo.Builder("receipt.pdf")
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
            } catch (IOException e) {
                if (cancel.isCanceled()) callback.onWriteCancelled();
                else callback.onWriteFailed("ساخت فایل چاپ ناموفق بود.");
                return;
            }
            if (cancel.isCanceled()) callback.onWriteCancelled();
            else callback.onWriteFinished(new PageRange[]{new PageRange(0, 0)});
        }, "receipt-print").start();
    }

    private void writePdf(OutputStream output, CancellationSignal cancel) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        int width = receipt.getWidth();
        int height = receipt.getHeight();
        try (DeflaterOutputStream pixels = new DeflaterOutputStream(compressed)) {
            int[] row = new int[width];
            byte[] gray = new byte[width];
            for (int y = 0; y < height; y++) {
                if (cancel.isCanceled()) return;
                receipt.getPixels(row, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x++) {
                    int rgb = row[x];
                    gray[x] = (byte) ((299 * ((rgb >> 16) & 255) + 587 * ((rgb >> 8) & 255)
                        + 114 * (rgb & 255) + 500) / 1000);
                }
                pixels.write(gray);
            }
        }

        // Fractional PDF points preserve the PSD's physical page size.
        String pageWidth = String.format(Locale.US, "%.6f", template.widthMm * 72 / 25.4);
        String pageHeight = String.format(Locale.US, "%.6f", template.heightMm * 72 / 25.4);
        String commands = "q\n" + pageWidth + " 0 0 " + pageHeight + " 0 0 cm\n/Receipt Do\nQ\n";
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        int[] offsets = new int[6];
        append(pdf, "%PDF-1.4\n");
        object(pdf, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        object(pdf, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        object(pdf, offsets, 3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + pageWidth + " " + pageHeight
            + "] /Resources << /XObject << /Receipt 4 0 R >> >> /Contents 5 0 R >>");
        offsets[4] = pdf.size();
        append(pdf, "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + width + " /Height " + height
            + " /BitsPerComponent 8 /ColorSpace /DeviceGray /Filter /FlateDecode /Length "
            + compressed.size() + " >>\nstream\n");
        compressed.writeTo(pdf);
        append(pdf, "\nendstream\nendobj\n");
        object(pdf, offsets, 5, "<< /Length " + commands.length() + " >>\nstream\n" + commands + "endstream");
        int xref = pdf.size();
        append(pdf, "xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.length; i++) {
            append(pdf, String.format(Locale.US, "%010d 00000 n \n", offsets[i]));
        }
        append(pdf, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        pdf.writeTo(output);
    }

    private static void object(ByteArrayOutputStream pdf, int[] offsets, int number, String content)
        throws IOException {
        offsets[number] = pdf.size();
        append(pdf, number + " 0 obj\n" + content + "\nendobj\n");
    }

    private static void append(ByteArrayOutputStream pdf, String text) throws IOException {
        pdf.write(text.getBytes(StandardCharsets.US_ASCII));
    }
}
