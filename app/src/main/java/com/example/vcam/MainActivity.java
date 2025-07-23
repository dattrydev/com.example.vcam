package com.example.vcam;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "VCAM_PREFS";
    private static final String KEY_VIDEO_URI = "video_uri";
    private TextView tvVideoPath;

    // Trình xử lý kết quả cho việc yêu cầu quyền
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Đã cấp quyền truy cập bộ nhớ", Toast.LENGTH_SHORT).show();
                    openVideoPicker();
                } else {
                    Toast.makeText(this, "Cần quyền truy cập bộ nhớ để chọn video", Toast.LENGTH_LONG).show();
                }
            });

    // Trình xử lý kết quả cho việc chọn file
    private final ActivityResultLauncher<Intent> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri videoUri = result.getData().getData();
                    if (videoUri != null) {
                        // Lưu URI và cấp quyền truy cập lâu dài
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                        getContentResolver().takePersistableUriPermission(videoUri, takeFlags);
                        saveVideoUri(videoUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvVideoPath = findViewById(R.id.tvVideoPath);
        Button btnSelectVideo = findViewById(R.id.btnSelectVideo);

        btnSelectVideo.setOnClickListener(v -> checkPermissionAndPickVideo());
        updateVideoPathDisplay();
    }

    private void checkPermissionAndPickVideo() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            openVideoPicker();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        videoPickerLauncher.launch(intent);
    }

    private void saveVideoUri(Uri uri) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_VIDEO_URI, uri.toString());
        editor.apply();

        Toast.makeText(this, "Đã lưu video!", Toast.LENGTH_SHORT).show();
        updateVideoPathDisplay();
    }
    
    private void updateVideoPathDisplay() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        String uriString = prefs.getString(KEY_VIDEO_URI, null);
        if (uriString != null) {
            tvVideoPath.setText("Video đang phát: " + Uri.parse(uriString).getPath());
        } else {
            tvVideoPath.setText("Chưa có video nào được chọn");
        }
    }
}