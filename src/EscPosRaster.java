package ir.fishzan.app;

import java.io.ByteArrayOutputStream;

final class EscPosRaster {
    interface Rows { void read(int row, int[] colors); }

    static byte[] encode(int width, int height, Rows rows) {
        int stride = (width + 7) / 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream(height * stride + 160);
        out.write(0x1b);
        out.write(0x40);
        int[] colors = new int[width];
        for (int top = 0; top < height; top += 128) {
            int lines = Math.min(128, height - top);
            out.write(0x1d);
            out.write(0x76);
            out.write(0x30);
            out.write(0);
            out.write(stride & 255);
            out.write(stride >> 8);
            out.write(lines & 255);
            out.write(lines >> 8);
            for (int row = top; row < top + lines; row++) {
                rows.read(row, colors);
                for (int start = 0; start < width; start += 8) {
                    int packed = 0;
                    for (int i = 0; i < 8 && start + i < width; i++) {
                        int rgb = colors[start + i];
                        int gray = 299 * ((rgb >> 16) & 255) + 587 * ((rgb >> 8) & 255) + 114 * (rgb & 255);
                        if (gray < 170000) packed |= 0x80 >> i;
                    }
                    out.write(packed);
                }
            }
        }
        return out.toByteArray();
    }
}
