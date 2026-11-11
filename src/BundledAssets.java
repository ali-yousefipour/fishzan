package ir.fishzan.app;

import android.content.Context;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class BundledAssets {
    static InputStream open(Context context, String path) throws IOException {
        try {
            return context.getAssets().open(path);
        } catch (IOException assetError) {
            ZipFile apk = new ZipFile(context.getApplicationInfo().sourceDir);
            try {
                ZipEntry entry = apk.getEntry("assets/" + path);
                if (entry == null) throw assetError;
                return new FilterInputStream(apk.getInputStream(entry)) {
                    @Override public void close() throws IOException {
                        try { super.close(); }
                        finally { apk.close(); }
                    }
                };
            } catch (IOException | RuntimeException error) {
                apk.close();
                throw error;
            }
        }
    }

    static Set<String> templateFolders(Context context) throws IOException {
        Set<String> folders = new TreeSet<>();
        String[] listed = context.getAssets().list("templates");
        if (listed != null) java.util.Collections.addAll(folders, listed);
        if (!folders.isEmpty()) return folders;
        try (ZipFile apk = new ZipFile(context.getApplicationInfo().sourceDir)) {
            java.util.Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                String path = entries.nextElement().getName();
                String[] parts = path.split("/");
                if (parts.length == 4 && parts[0].equals("assets") && parts[1].equals("templates")
                    && parts[3].equals("template.json")) folders.add(parts[2]);
            }
        }
        return folders;
    }
}
