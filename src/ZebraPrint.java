package ir.fishzan.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import java.io.IOException;

final class ZebraPrint {
    static final int DOTS_WIDE = 384;
    private static final int DPI = 203;
    private static final String RAWBT = "ru.a402d.rawbtprinter";

    static int dots(double mm) {
        return Math.max(1, (int) Math.round(mm * DPI / 25.4));
    }

    static Intent intent(PrintPage page) throws IOException {
        int width = Math.min(DOTS_WIDE, dots(page.widthMm));
        Bitmap image = page.bitmap(width, dots(page.heightMm));
        byte[] raster;
        try {
            raster = EscPosRaster.encode(width, image.getHeight(),
                (row, colors) -> image.getPixels(colors, 0, width, 0, row, width, 1));
        } finally {
            image.recycle();
        }
        String data = Base64.encodeToString(raster, Base64.NO_WRAP);
        return new Intent(Intent.ACTION_VIEW, Uri.parse("rawbt:base64," + data)).setPackage(RAWBT);
    }

    static void send(Activity activity, Intent intent) {
        activity.startActivity(intent);
    }
}
