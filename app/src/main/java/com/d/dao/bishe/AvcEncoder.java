package com.d.dao.bishe;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;


public class AvcEncoder {

    private MediaCodec mediaCodec;
    int m_width;
    int m_height;
    byte[] m_info = null;


    private int m_framerate;
    private byte[] yuv420 = null;

    public AvcEncoder(int width, int height, int framerate, int bitrate) {

        m_framerate = framerate;
        m_width = width;
        m_height = height;
        yuv420 = new byte[width * height * 3 / 2];

        int colorFormat = selectColorFormat(selectCodec("video/avc"), "video/avc");
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);//125000
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        //mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);//5

        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

    }

    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / m_framerate;
    }

    public int offerEncoder(byte[] input, byte[] output) throws Exception {
        int pos = 0;
        long generateIndex = 0;
        long pts = 0;

        swapYV12toI420(input, yuv420, m_width, m_height);
        try {
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            Log.e("inputBufferIndex", "" + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                pts = computePresentationTime(generateIndex);
                long timepts = 1000000 * inputBufferIndex / 20;
//                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, timepts, 0);

                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];

                Log.e("offerEncoder2", "offerEncoder InputBufSize: " + inputBuffer.capacity() + " inputSize: " + input.length + " bytes");
                inputBuffer.clear();
                inputBuffer.put(yuv420);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, yuv420.length, pts, 0);
                generateIndex += 1;
//                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

//            long timepts = 1000000*count / 20;
//            mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, timepts, 0);
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

            Log.e("outputBufferIndex", "" + outputBufferIndex);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                byte[] outData = new byte[bufferInfo.size];
                Log.e("offerEncoder", "offerEncoder InputBufSize:" + outputBuffer.capacity() + " outputSize:" + outData.length + " bytes written");

                outputBuffer.get(outData);

                if (m_info != null) {
                    System.arraycopy(outData, 0, output, pos, outData.length);
                    Log.e("offerEncoder2", "offer Encoder save sps head,len:" + outData.length);
                    pos += outData.length;

                } else {
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                    if (spsPpsBuffer.getInt() == 0x00000001) {
                        m_info = new byte[outData.length];
                        System.arraycopy(outData, 0, m_info, 0, outData.length);
                    } else {
                        Log.e("offerEncoder2", "not found media head.");
                        return -1;
                    }
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }

            if (output[4] == 0x65) //key frame
            {
                System.arraycopy(output, 0, yuv420, 0, pos);
                System.arraycopy(m_info, 0, output, 0, m_info.length);
                System.arraycopy(yuv420, 0, output, m_info.length, pos);
                pos += m_info.length;
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        return pos;
    }

    public int offerEncoder2(byte[] input, byte[] output) throws Exception {
        int pos = 0;
        try {
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                Log.d("offerEncoder2", "offerEncoder InputBufSize: " + inputBuffer.capacity() + " inputSize: " + input.length + " bytes");
                inputBuffer.clear();
                inputBuffer.put(input);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);

            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                byte[] data = new byte[bufferInfo.size];
                outputBuffer.get(data);

                Log.d("offerEncoder2", "offerEncoder InputBufSize:" + outputBuffer.capacity() + " outputSize:" + data.length + " bytes written");

                if (m_info != null) {
                    System.arraycopy(data, 0, output, pos, data.length);
                    pos += data.length;
                } else // ????pps sps ??п??? ?????????У? ??????????????
                {
                    Log.d("offerEncoder2", "offer Encoder save sps head,len:" + data.length);
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(data);
                    if (spsPpsBuffer.getInt() == 0x00000001) {
                        m_info = new byte[data.length];
                        System.arraycopy(data, 0, m_info, 0, data.length);
                    } else {
                        Log.e("offerEncoder2", "not found media head.");
                        return -1;
                    }
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }

            if (output[4] == 0x65) //key frame   ????????????????? 00 00 00 01 65 ???pps sps?? ?????
            {
                System.arraycopy(output, 0, input, 0, pos);
                System.arraycopy(m_info, 0, output, 0, m_info.length);
                System.arraycopy(input, 0, output, m_info.length, pos);
                pos += m_info.length;
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
        return pos;
    }


    private void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(yv12bytes, 0, i420bytes, 0, width * height);
        System.arraycopy(yv12bytes, width * height + width * height / 4, i420bytes, width * height, width * height / 4);
        System.arraycopy(yv12bytes, width * height, i420bytes, width * height + width * height / 4, width * height / 4);
    }


    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e("AvcEncoder", "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
}


