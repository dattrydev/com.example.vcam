package com.example.vcam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class VideoFrameProvider {
    private static File[] frameFiles;
    private static int frameIndex = 0;

    public static void init() {
        File dir = new File("/sdcard/frames");
        if (dir.exists() && dir.isDirectory()) {
            frameFiles = dir.listFiles((file) -> file.getName().toLowerCase().endsWith(".jpg") || file.getName().toLowerCase().endsWith(".jpeg"));
            if (frameFiles != null) {
                Arrays.sort(frameFiles, Comparator.comparing(File::getName));
                frameIndex = 0;
            }
        }
    }

    public static byte[] getNextFrameNV21(int width, int height) {
        if (frameFiles == null || frameFiles.length == 0) {
            init();
            if (frameFiles == null || frameFiles.length == 0) return null;
        }
        File frame = frameFiles[frameIndex];
        frameIndex = (frameIndex + 1) % frameFiles.length;
        try {
            Bitmap bmp = BitmapFactory.decodeFile(frame.getAbsolutePath());
            if (bmp == null) return null;
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, width, height, false);
            byte[] nv21 = bitmapToNV21(scaled);
            bmp.recycle();
            scaled.recycle();
            return nv21;
        } catch (Throwable t) {
            Log.e("VCAM", "Frame error: " + t);
            return null;
        }
    }

    // Convert Bitmap -> NV21
    public static byte[] bitmapToNV21(Bitmap bitmap) {
        int inputWidth = bitmap.getWidth();
        int inputHeight = bitmap.getHeight();
        int[] argb = new int[inputWidth * inputHeight];
        bitmap.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
        return yuv;
    }

    // ARGB -> NV21 (YUV420SP)
    private static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;
        int yIndex = 0;
        int uvIndex = frameSize;
        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                a = (argb[index] & 0xff000000) >> 24;
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff);

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
                yuv420sp[yIndex++] = (byte) (Y < 0 ? 0 : (Y > 255 ? 255 : Y));
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) (V < 0 ? 0 : (V > 255 ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) (U < 0 ? 0 : (U > 255 ? 255 : U));
                }
                index++;
            }
        }
    }
}