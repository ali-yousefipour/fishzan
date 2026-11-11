package ir.fishzan.app;

import android.content.Context;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class AppFonts {
    static Typeface interfaceFont(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            int resource = context.getResources().getIdentifier("b_homa", "font", context.getPackageName());
            if (resource != 0) {
                try { return context.getResources().getFont(resource); }
                catch (RuntimeException e) {
                    android.util.Log.w("Fishzan", "Could not load the interface font resource", e);
                }
            }
        }
        return bundled(context, "templates/fanous/BHoma.ttf", Typeface.DEFAULT);
    }

    static Typeface bundled(Context context, String asset, Typeface fallback) {
        File copy = null;
        try (InputStream input = BundledAssets.open(context, asset)) {
            copy = File.createTempFile("fishzan-font-", ".ttf", context.getCacheDir());
            try (FileOutputStream output = new FileOutputStream(copy)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            return Typeface.createFromFile(copy);
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("Fishzan", "Could not load bundled font " + asset, e);
            return fallback;
        } finally {
            if (copy != null) copy.delete();
        }
    }
}
