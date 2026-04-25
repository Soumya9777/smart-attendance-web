package smartattendance.qr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class QrCodeGenerator {
    private static final int VERSION = 5;
    private static final int SIZE = VERSION * 4 + 17;
    private static final int DATA_CODEWORDS = 108;
    private static final int ERROR_CORRECTION_CODEWORDS = 26;
    private static final int MASK_PATTERN = 0;

    private QrCodeGenerator() {
    }

    public static BufferedImage createQrImage(String text, int scale, int quietZone) {
        boolean[][] modules = generateQr(text);
        int qrPixels = (SIZE + quietZone * 2) * scale;
        BufferedImage image = new BufferedImage(qrPixels, qrPixels, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, qrPixels, qrPixels);
        graphics.setColor(Color.BLACK);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (modules[y][x]) {
                    graphics.fillRect((x + quietZone) * scale, (y + quietZone) * scale, scale, scale);
                }
            }
        }
        graphics.dispose();
        return image;
    }

    public static boolean[][] generateQr(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > 104) {
            throw new IllegalArgumentException("QR text is too long for this project QR version");
        }

        boolean[][] modules = new boolean[SIZE][SIZE];
        boolean[][] function = new boolean[SIZE][SIZE];
        drawFunctionPatterns(modules, function);

        byte[] codewords = addErrorCorrection(encodeData(data));
        drawCodewords(modules, function, codewords);
        drawFormatBits(modules, function);
        return modules;
    }

    private static byte[] encodeData(byte[] data) {
        BitBuffer bits = new BitBuffer();
        bits.appendBits(0x4, 4);
        bits.appendBits(data.length, 8);
        for (byte value : data) {
            bits.appendBits(value & 0xFF, 8);
        }
        bits.appendBits(0, Math.min(4, DATA_CODEWORDS * 8 - bits.size()));
        while (bits.size() % 8 != 0) {
            bits.appendBits(0, 1);
        }

        List<Integer> codewords = new ArrayList<>();
        for (int i = 0; i < bits.size(); i += 8) {
            int value = 0;
            for (int j = 0; j < 8; j++) {
                value = (value << 1) | bits.get(i + j);
            }
            codewords.add(value);
        }
        for (int pad = 0xEC; codewords.size() < DATA_CODEWORDS; pad ^= 0xEC ^ 0x11) {
            codewords.add(pad);
        }

        byte[] result = new byte[DATA_CODEWORDS];
        for (int i = 0; i < DATA_CODEWORDS; i++) {
            result[i] = (byte) (int) codewords.get(i);
        }
        return result;
    }

    private static byte[] addErrorCorrection(byte[] data) {
        int[] generator = reedSolomonGenerator(ERROR_CORRECTION_CODEWORDS);
        int[] remainder = new int[ERROR_CORRECTION_CODEWORDS];
        for (byte value : data) {
            int factor = (value & 0xFF) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, remainder.length - 1);
            remainder[remainder.length - 1] = 0;
            for (int i = 0; i < remainder.length; i++) {
                remainder[i] ^= multiply(generator[i], factor);
            }
        }

        byte[] result = new byte[DATA_CODEWORDS + ERROR_CORRECTION_CODEWORDS];
        System.arraycopy(data, 0, result, 0, data.length);
        for (int i = 0; i < remainder.length; i++) {
            result[data.length + i] = (byte) remainder[i];
        }
        return result;
    }

    private static int[] reedSolomonGenerator(int degree) {
        int[] result = new int[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = multiply(result[j], root);
                if (j + 1 < result.length) {
                    result[j] ^= result[j + 1];
                }
            }
            root = multiply(root, 2);
        }
        return result;
    }

    private static int multiply(int x, int y) {
        int result = 0;
        while (y != 0) {
            if ((y & 1) != 0) {
                result ^= x;
            }
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= 0x11D;
            }
            y >>>= 1;
        }
        return result;
    }

    private static void drawFunctionPatterns(boolean[][] modules, boolean[][] function) {
        drawFinder(modules, function, 3, 3);
        drawFinder(modules, function, SIZE - 4, 3);
        drawFinder(modules, function, 3, SIZE - 4);

        for (int i = 8; i < SIZE - 8; i++) {
            setFunction(modules, function, 6, i, i % 2 == 0);
            setFunction(modules, function, i, 6, i % 2 == 0);
        }

        drawAlignment(modules, function, 30, 30);
        setFunction(modules, function, 8, SIZE - 8, true);
        reserveFormat(function);
    }

    private static void drawFinder(boolean[][] modules, boolean[][] function, int centerX, int centerY) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = centerX + dx;
                int y = centerY + dy;
                if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
                    continue;
                }
                int distance = Math.max(Math.abs(dx), Math.abs(dy));
                setFunction(modules, function, x, y, distance == 3 || distance <= 1);
            }
        }
    }

    private static void drawAlignment(boolean[][] modules, boolean[][] function, int centerX, int centerY) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dy));
                setFunction(modules, function, centerX + dx, centerY + dy, distance != 1);
            }
        }
    }

    private static void reserveFormat(boolean[][] function) {
        for (int i = 0; i <= 8; i++) {
            if (i != 6) {
                function[8][i] = true;
                function[i][8] = true;
            }
        }
        for (int i = 0; i < 8; i++) {
            function[8][SIZE - 1 - i] = true;
            function[SIZE - 1 - i][8] = true;
        }
    }

    private static void drawCodewords(boolean[][] modules, boolean[][] function, byte[] codewords) {
        int bitIndex = 0;
        int totalBits = codewords.length * 8;
        for (int right = SIZE - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vertical = 0; vertical < SIZE; vertical++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? SIZE - 1 - vertical : vertical;
                    if (!function[y][x] && bitIndex < totalBits) {
                        boolean bit = ((codewords[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1) != 0;
                        modules[y][x] = bit ^ mask(x, y);
                        bitIndex++;
                    }
                }
            }
        }
    }

    private static boolean mask(int x, int y) {
        return ((x + y) & 1) == 0;
    }

    private static void drawFormatBits(boolean[][] modules, boolean[][] function) {
        int formatData = (1 << 3) | MASK_PATTERN;
        int remainder = formatData;
        for (int i = 0; i < 10; i++) {
            remainder = (remainder << 1) ^ (((remainder >>> 9) & 1) * 0x537);
        }
        int bits = ((formatData << 10) | remainder) ^ 0x5412;

        for (int i = 0; i <= 5; i++) {
            setFunction(modules, function, 8, i, getBit(bits, i));
        }
        setFunction(modules, function, 8, 7, getBit(bits, 6));
        setFunction(modules, function, 8, 8, getBit(bits, 7));
        setFunction(modules, function, 7, 8, getBit(bits, 8));
        for (int i = 9; i < 15; i++) {
            setFunction(modules, function, 14 - i, 8, getBit(bits, i));
        }

        for (int i = 0; i < 8; i++) {
            setFunction(modules, function, SIZE - 1 - i, 8, getBit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            setFunction(modules, function, 8, SIZE - 15 + i, getBit(bits, i));
        }
        setFunction(modules, function, 8, SIZE - 8, true);
    }

    private static boolean getBit(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    private static void setFunction(boolean[][] modules, boolean[][] function, int x, int y, boolean black) {
        modules[y][x] = black;
        function[y][x] = true;
    }

    private static final class BitBuffer {
        private final List<Integer> bits = new ArrayList<>();

        void appendBits(int value, int length) {
            if (length < 0 || length > 31 || value >>> length != 0) {
                throw new IllegalArgumentException("Invalid bit value");
            }
            for (int i = length - 1; i >= 0; i--) {
                bits.add((value >>> i) & 1);
            }
        }

        int size() {
            return bits.size();
        }

        int get(int index) {
            return bits.get(index);
        }
    }
}
