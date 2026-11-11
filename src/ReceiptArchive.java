package ir.fishzan.app;

import android.content.Context;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class ReceiptArchive {
    static final class Entry {
        final JSONObject details;
        final PrintPage page;
        final String title;
        final long timestamp;

        Entry(JSONObject details, File pdf) throws JSONException {
            this.details = details;
            title = details.getString("title");
            timestamp = details.getLong("savedAt");
            page = new PrintPage(details.getDouble("widthMm"), details.getDouble("heightMm"), pdf);
        }
    }

    private final File root;

    ReceiptArchive(Context context) {
        root = context.getDir("receipts", Context.MODE_PRIVATE);
    }

    void save(PrintPage page, String title, JSONObject details) throws IOException, JSONException {
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID();
        File draft = new File(root, "." + id);
        if (!draft.mkdir()) throw new IOException("archive directory");
        File pdf = new File(draft, "receipt.pdf");
        File metadata = new File(draft, "receipt.json");
        try {
            details.put("title", title).put("savedAt", System.currentTimeMillis())
                .put("widthMm", page.widthMm).put("heightMm", page.heightMm);
            try (FileOutputStream output = new FileOutputStream(pdf)) {
                page.writePdf(output);
                output.getFD().sync();
            }
            try (FileOutputStream output = new FileOutputStream(metadata)) {
                output.write(details.toString().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (!draft.renameTo(new File(root, id))) throw new IOException("archive commit");
        } finally {
            if (draft.exists()) {
                pdf.delete();
                metadata.delete();
                draft.delete();
            }
        }
    }

    List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        File[] folders = root.listFiles(file -> file.isDirectory() && !file.getName().startsWith("."));
        if (folders == null) return entries;
        Arrays.sort(folders, Collections.reverseOrder());
        for (File folder : folders) {
            File pdf = new File(folder, "receipt.pdf");
            if (!pdf.isFile()) continue;
            try (FileInputStream input = new FileInputStream(new File(folder, "receipt.json"))) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] bytes = new byte[4096];
                int count;
                while ((count = input.read(bytes)) != -1) buffer.write(bytes, 0, count);
                JSONObject details = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                entries.add(new Entry(details, pdf));
            } catch (IOException | JSONException e) {
                android.util.Log.w("Fishzan", "Could not load a saved receipt", e);
            }
        }
        return entries;
    }
}
