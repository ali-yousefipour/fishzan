package ir.fishzan.app;

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
    private final String filename;
    private final PrintPage page;

    ReceiptPrint(String filename, PrintPage page) {
        this.filename = filename;
        this.page = page;
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
                    page.writePdf(output);
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

}
