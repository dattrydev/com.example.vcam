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
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("id.dana")) return;

        // Hook Camera1 (API)
        try {
            XposedHelpers.findAndHookMethod(
                "android.hardware.Camera$PreviewCallback",
                lpparam.classLoader,
                "onPreviewFrame",
                byte[].class, Camera.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
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

        // Hook Camera2 (ImageReader)
        try {
            XposedHelpers.findAndHookMethod(
                "android.media.ImageReader$OnImageAvailableListener",
                lpparam.classLoader,
                "onImageAvailable",
                ImageReader.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        ImageReader reader = (ImageReader) param.args[0];
                        Image image = reader.acquireLatestImage();
                        if (image != null) {
                            int w = image.getWidth();
                            int h = image.getHeight();
                            byte[] fakeFrame = VideoFrameProvider.getNextFrameNV21(w, h);
                            if (fakeFrame != null && image.getFormat() == ImageFormat.YUV_420_888) {
                                // Ghi buffer NV21 vào image planes (YUV_420_888)
                                ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
                                ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
                                ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
                                yBuffer.put(fakeFrame, 0, w*h);
                                uBuffer.put(fakeFrame, w*h, (w*h)/4);
                                vBuffer.put(fakeFrame, w*h+(w*h)/4, (w*h)/4);
                            }
                            image.close();
                        }
                    }
                }
            );
        } catch (Throwable t) {
            Log.e("VCAM", "Camera2 hook failed: " + t);
        }

        // Hook CameraX (ImageAnalysis)
        try {
            Class<?> imageAnalysisClass = lpparam.classLoader.loadClass("androidx.camera.core.ImageAnalysis$Analyzer");
            for (Class<?> clazz : lpparam.classLoader.getDefinedClasses()) {
                if (clazz.getName().startsWith("androidx.camera.core") && clazz.getName().contains("ImageAnalysis")) {
                    for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                        if (method.getName().equals("analyze")) {
                            XposedHelpers.findAndHookMethod(
                                clazz,
                                "analyze",
                                lpparam.classLoader.loadClass("androidx.camera.core.ImageProxy"),
                                new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        Object imageProxy = param.args[0];
                                        int w = (int) imageProxy.getClass().getMethod("getWidth").invoke(imageProxy);
                                        int h = (int) imageProxy.getClass().getMethod("getHeight").invoke(imageProxy);
                                        byte[] fakeFrame = VideoFrameProvider.getNextFrameNV21(w, h);
                                        // Có thể cần thêm logic để set buffer vào imageProxy (tuỳ vào CameraX version)
                                    }
                                }
                            );
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e("VCAM", "CameraX hook failed: " + t);
        }
    }
}