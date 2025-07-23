package com.example.vcam.xposed.utils;

import android.content.Context;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.net.Uri;
import android.os.Handler;
import android.view.Surface;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

// Class này tạo ra một đối tượng CameraDevice "giả"
public class CameraDeviceProxy implements InvocationHandler {
    private final Context context;
    private final Uri videoUri;
    private VideoPlayerThread videoPlayerThread;

    private CameraDeviceProxy(Context context, Uri videoUri) {
        this.context = context;
        this.videoUri = videoUri;
    }

    public static Object create(Context context, Uri videoUri) {
        return Proxy.newProxyInstance(
                CameraDevice.class.getClassLoader(),
                new Class<?>[]{CameraDevice.class},
                new CameraDeviceProxy(context, videoUri)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        XposedBridge.log("VCAM (Proxy): DANA đã gọi hàm: " + methodName);

        if ("createCaptureSession".equals(methodName)) {
            // Đây là lúc DANA cung cấp Surface để chúng ta vẽ video lên
            List<Surface> surfaces = (List<Surface>) args[0];
            CameraCaptureSession.StateCallback sessionCallback = (CameraCaptureSession.StateCallback) args[1];

            if (surfaces != null && !surfaces.isEmpty()) {
                Surface targetSurface = surfaces.get(0); // Lấy surface đầu tiên
                XposedBridge.log("VCAM (Proxy): Đã nhận được Surface từ DANA. Bắt đầu phát video...");

                // Dừng thread cũ nếu có
                if (videoPlayerThread != null) {
                    videoPlayerThread.interrupt();
                }

                // Khởi động một thread mới để giải mã và phát video lên Surface
                videoPlayerThread = new VideoPlayerThread(context, videoUri, targetSurface, sessionCallback);
                videoPlayerThread.start();
            }
            return null;
        } else if ("close".equals(methodName)) {
            // Khi DANA đóng camera, chúng ta cũng dừng thread phát video
            if (videoPlayerThread != null) {
                videoPlayerThread.interrupt();
                videoPlayerThread = null;
                XposedBridge.log("VCAM (Proxy): DANA đã đóng camera. Đã dừng phát video.");
            }
            return null;
        } else if ("getId".equals(methodName)) {
            // Trả về ID ảo của chúng ta
            return "99";
        }

        // Đối với các hàm khác, trả về giá trị mặc định để tránh lỗi
        return null;
    }
}