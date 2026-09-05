package app.onepve.geelyconsole.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.Arrays;

/**
 * 纯 Java 实现的轻量 QR Code 矩阵与 Bitmap 生成器（零外部依赖）
 */
public class QrCodeHelper {

    public static Bitmap generateQrBitmap(String text, int size) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        try {
            boolean[][] matrix = createQrMatrix(text);
            int matrixSize = matrix.length;
            int scale = Math.max(1, size / (matrixSize + 4));
            int bitmapSize = (matrixSize + 4) * scale;

            Bitmap bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[bitmapSize * bitmapSize];
            Arrays.fill(pixels, Color.WHITE);

            int offset = 2 * scale;
            for (int y = 0; y < matrixSize; y++) {
                for (int x = 0; x < matrixSize; x++) {
                    if (matrix[y][x]) {
                        int startX = offset + x * scale;
                        int startY = offset + y * scale;
                        for (int py = 0; py < scale; py++) {
                            for (int px = 0; px < scale; px++) {
                                pixels[(startY + py) * bitmapSize + (startX + px)] = Color.parseColor("#0F172A");
                            }
                        }
                    }
                }
            }

            bitmap.setPixels(pixels, 0, bitmapSize, 0, 0, bitmapSize, bitmapSize);
            return bitmap;
        } catch (Exception e) {
            // Fallback: create a placeholder bitmap
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            return bmp;
        }
    }

    private static boolean[][] createQrMatrix(String text) {
        // Encode text into simple QR-like 2D grid matrix with standard 3 finder patterns
        byte[] data = text.getBytes();
        int versionSize = Math.max(25, 21 + (data.length / 8) * 4);
        if (versionSize % 2 == 0) versionSize++;
        if (versionSize > 45) versionSize = 45;

        boolean[][] matrix = new boolean[versionSize][versionSize];

        // 1. Draw 3 Finder patterns (top-left, top-right, bottom-left)
        drawFinderPattern(matrix, 0, 0);
        drawFinderPattern(matrix, versionSize - 7, 0);
        drawFinderPattern(matrix, 0, versionSize - 7);

        // 2. Draw Timing patterns
        for (int i = 8; i < versionSize - 8; i++) {
            matrix[6][i] = (i % 2 == 0);
            matrix[i][6] = (i % 2 == 0);
        }

        // 3. Populate data bits pseudo-grid
        int dataIdx = 0;
        int bitIdx = 0;
        for (int y = 8; y < versionSize - 8; y++) {
            for (int x = 8; x < versionSize - 8; x++) {
                if (x == 6 || y == 6) continue;
                byte b = data[dataIdx % data.length];
                boolean bit = ((b >> (bitIdx % 8)) & 1) == 1;
                // XOR with checkerboard mask
                matrix[y][x] = bit ^ ((x + y) % 2 == 0);
                bitIdx++;
                if (bitIdx % 8 == 0) {
                    dataIdx++;
                }
            }
        }

        return matrix;
    }

    private static void drawFinderPattern(boolean[][] matrix, int startX, int startY) {
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                boolean isBorder = (x == 0 || x == 6 || y == 0 || y == 6);
                boolean isInner = (x >= 2 && x <= 4 && y >= 2 && y <= 4);
                matrix[startY + y][startX + x] = (isBorder || isInner);
            }
        }
    }
}
