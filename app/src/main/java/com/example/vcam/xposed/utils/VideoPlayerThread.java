package com.example.vcam.xposed.utils;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.net.Uri;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

// Thread này chịu trách nhiệm đọc file video và vẽ lên Surface
public class VideoPlayerThread extends Thread {
    private final Context context;
    private final Uri videoUri;
    private final Surface surface;
    private final CameraCaptureSession.StateCallback sessionCallback;

    public VideoPlayerThread(Context context, Uri videoUri, Surface surface, CameraCaptureSession.StateCallback sessionCallback) {
        this.context = context;
        this.videoUri = videoUri;
        this.surface = surface;
        this.sessionCallback = sessionCallback;
    }

    @Override
    public void run() {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;

        try {
            extractor.setDataSource(context, videoUri, null);
            int trackIndex = selectVideoTrack(extractor);
            if (trackIndex < 0) {
                throw new RuntimeException("Không tìm thấy video track trong file.");
            }
            extractor.selectTrack(trackIndex);

            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, surface, null, 0);
            decoder.start();
            
            // Báo cho DANA biết session đã sẵn sàng
            if (sessionCallback != null) {
                // Chúng ta cần một đối tượng CameraCaptureSession giả, nhưng có thể chỉ cần null ở đây
                 XposedHelpers.callMethod(sessionCallback, "onConfigured", (Object) null);
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isEOS = false;

            while (!Thread.interrupted()) {
                if (!isEOS) {
                    int inIndex = decoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(inIndex);
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outIndex >= 0) {
                    decoder.releaseOutputBuffer(outIndex, true);
                }

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    XposedBridge.log("VCAM (Player): Hết video, bắt đầu lại.");
                    // Lặp lại video
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    isEOS = false;
                }
            }
        } catch (IOException e) {
            XposedBridge.log("VCAM (Player): Lỗi IO - " + e.getMessage());
        } finally {
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            extractor.release();
            XposedBridge.log("VCAM (Player): Đã dừng và giải phóng tài nguyên.");
        }
    }

    private int selectVideoTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                return i;
            }
        }
        return -1;
    }
}