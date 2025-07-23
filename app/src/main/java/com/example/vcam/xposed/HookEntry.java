package com.example.vcam.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XModuleResources;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Handler;
import android.view.Surface;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

// Thêm các lớp helper và lớp thread để xử lý video
import com.example.vcam.xposed.utils.CameraDeviceProxy;
import com.example.vcam.xposed.utils.VideoPlayerThread;

public class HookEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String TARGET_PACKAGE = "id.dana";
    private static final String VCAM_ID = "99"; // ID cho camera ảo, nên chọn số lớn để tránh trùng lặp
    private static final String REAL_FRONT_CAMERA_ID = "1"; // ID camera trước thật, thường là "1"
    private static String MODULE_PATH = null;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
    }
    
    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("VCAM (Camera2): Đã nạp vào DANA. Bắt đầu hook...");

        final Class<?> cameraManagerClass = XposedHelpers.findClass("android.hardware.camera2.CameraManager", lpparam.classLoader);

        // --- Hook 1: getCameraIdList ---
        // Thêm camera ảo của chúng ta vào danh sách camera hệ thống
        XposedHelpers.findAndHookMethod(cameraManagerClass, "getCameraIdList", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                String[] originalIds = (String[]) param.getResult();
                String[] newIds = Arrays.copyOf(originalIds, originalIds.length + 1);
                newIds[originalIds.length] = VCAM_ID;
                param.setResult(newIds);
                XposedBridge.log("VCAM: Đã thêm camera ảo. Danh sách ID mới: " + Arrays.toString(newIds));
            }
        });

        // --- Hook 2: getCameraCharacteristics ---
        // Khi DANA hỏi thông tin về camera ảo, chúng ta giả mạo bằng cách trả về thông tin của camera trước thật.
        XposedHelpers.findAndHookMethod(cameraManagerClass, "getCameraCharacteristics", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String cameraId = (String) param.args[0];
                if (VCAM_ID.equals(cameraId)) {
                    XposedBridge.log("VCAM: DANA hỏi thông tin của VCAM_ID. Tráo đổi bằng thông tin của camera ID " + REAL_FRONT_CAMERA_ID);
                    param.args[0] = REAL_FRONT_CAMERA_ID; // Lừa nó hỏi thông tin của camera thật
                }
            }
        });

        // --- Hook 3: openCamera ---
        // Can thiệp vào quá trình mở camera. Đây là phần phức tạp nhất.
        XposedHelpers.findAndHookMethod(cameraManagerClass, "openCamera", String.class, android.hardware.camera2.CameraDevice.StateCallback.class, Handler.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String cameraId = (String) param.args[0];
                XposedBridge.log("VCAM: DANA đang cố mở camera ID: " + cameraId);

                if (VCAM_ID.equals(cameraId)) {
                    XposedBridge.log("VCAM: Đã chặn lời gọi mở camera ảo. Bắt đầu quy trình giả mạo...");

                    // Lấy context của ứng dụng DANA
                    Context context = (Context) XposedHelpers.callMethod(
                            XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread"),
                            "getSystemContext"
                    );

                    // Lấy URI video đã lưu từ MainActivity
                    SharedPreferences prefs = context.getSharedPreferences("VCAM_PREFS", Context.MODE_WORLD_READABLE);
                    String uriString = prefs.getString("video_uri", null);

                    if (uriString == null) {
                        XposedBridge.log("VCAM: LỖI - Không tìm thấy URI video đã lưu. Hãy mở ứng dụng VCam và chọn một video.");
                        return;
                    }
                    Uri videoUri = Uri.parse(uriString);

                    // Lấy StateCallback mà DANA cung cấp để chúng ta có thể giao tiếp lại với nó
                    final Object stateCallback = param.args[1];

                    // Tạo một đối tượng CameraDevice giả mạo
                    Object fakeCameraDevice = CameraDeviceProxy.create(context, videoUri);

                    // Lấy Handler mà DANA cung cấp để chạy callback trên đúng luồng
                    Handler handler = (Handler) param.args[2];
                    if (handler != null) {
                        // Giả lập việc mở camera thành công bằng cách gọi lại callback của DANA
                        handler.post(() -> {
                            XposedHelpers.callMethod(stateCallback, "onOpened", fakeCameraDevice);
                        });
                    } else {
                        XposedHelpers.callMethod(stateCallback, "onOpened", fakeCameraDevice);
                    }

                    // Quan trọng: Ngăn chặn lời gọi đến hàm openCamera gốc
                    param.setResult(null);
                }
            }
        });
    }
}