package com.example.vcam;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.util.Log;

import java.nio.ByteBuffer;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {
    public static byte[] data_buffer = null;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // --- HOOK CAMERA 1 ---
        try {
            XposedHelpers.findAndHookMethod(
                "android.hardware.Camera$PreviewCallback",
                lpparam.classLoader,
                "onPreviewFrame",
                byte[].class, Camera.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Camera cam = (Camera) param.args[1];
                        Camera.Parameters params = cam.getParameters();
                        Camera.Size size = params.getPreviewSize();
                        int w = size.width;
                        int h = size.height;
                        byte[] fakeFrame = VideoFrameProvider.getNextFrameNV21(w, h);
                        if (fakeFrame != null && fakeFrame.length == w*h*3/2)
                            param.args[0] = fakeFrame;
                    }
                }
            );
        } catch (Throwable t) {
            Log.e("VCAM", "Camera1 hook failed: " + t);
        }

        // --- HOOK CAMERA 2: ImageReader callback ---
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                XposedHelpers.findAndHookMethod(
                    "android.media.ImageReader$OnImageAvailableListener",
                    lpparam.classLoader,
                    "onImageAvailable",
                    ImageReader.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ImageReader reader = (ImageReader) param.args[0];
                            Image image = null;
                            try {
                                image = reader.acquireLatestImage();
                                if (image != null && image.getFormat() == ImageFormat.YUV_420_888) {
                                    int w = image.getWidth();
                                    int h = image.getHeight();
                                    byte[] fakeFrame = VideoFrameProvider.getNextFrameNV21(w, h);
                                    if (fakeFrame != null && fakeFrame.length == w*h*3/2) {
                                        ByteBuffer yBuf = image.getPlanes()[0].getBuffer();
                                        ByteBuffer uBuf = image.getPlanes()[1].getBuffer();
                                        ByteBuffer vBuf = image.getPlanes()[2].getBuffer();
                                        // Copy NV21 to YUV_420_888 (simple, may need adjust for chroma offset)
                                        yBuf.put(fakeFrame, 0, w*h);
                                        uBuf.put(fakeFrame, w*h, w*h/4);
                                        vBuf.put(fakeFrame, w*h+w*h/4, w*h/4);
                                    }
                                    image.close();
                                }
                            } catch (Exception e) {
                                Log.e("VCAM", "Camera2 hook error: " + e);
                                if (image != null) image.close();
                            }
                        }
                    }
                );
            } catch (Throwable t) {
                Log.e("VCAM", "Camera2 hook failed: " + t);
            }
        }

        // --- HOOK CameraX: ImageAnalysis analyzer ---
        try {
            Class<?> imageAnalysisClass = lpparam.classLoader.loadClass("androidx.camera.core.ImageAnalysis$Analyzer");
            XposedHelpers.findAndHookMethod(
                imageAnalysisClass,
                "analyze",
                lpparam.classLoader.loadClass("androidx.camera.core.ImageProxy"),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object imageProxy = param.args[0];
                        try {
                            int w = (int) imageProxy.getClass().getMethod("getWidth").invoke(imageProxy);
                            int h = (int) imageProxy.getClass().getMethod("getHeight").invoke(imageProxy);
                            byte[] fakeFrame = VideoFrameProvider.getNextFrameNV21(w, h);
                            // TODO: set buffer for CameraX (may require reflection for planes)
                        } catch (Exception e) {
                            Log.e("VCAM", "CameraX analyze hook error: " + e);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            Log.e("VCAM", "CameraX hook failed: " + t);
        }

        // --- HOOK CameraX: Preview ---
        try {
            XposedHelpers.findAndHookMethod(
                "androidx.camera.core.Preview",
                lpparam.classLoader,
                "setSurfaceProvider",
                lpparam.classLoader.loadClass("androidx.camera.core.Preview$SurfaceProvider"),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Log.e("VCAM", "CameraX Preview.setSurfaceProvider hooked");
                        // You can try to replace the SurfaceProvider here if needed
                    }
                }
            );
        } catch (Throwable t) {
            Log.e("VCAM", "CameraX Preview hook failed: " + t);
        }
    }
}