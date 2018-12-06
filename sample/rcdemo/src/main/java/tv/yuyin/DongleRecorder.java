package tv.yuyin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.iflytek.rc.dongle.DongleBaseObj.DONGLE_VOICE_CODEC;
import com.iflytek.rc.dongle.RcDeviceManager;
import com.iflytek.rc.dongle.VoiceDataEvent;
import com.iflytek.xiri.tool.ICOCodec;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;


@SuppressLint("HandlerLeak")
public class DongleRecorder implements IPCMRecorder {
    private static final String TAG = "DongleRecorder";
    public final static short FRAME_SIZE = 40;
    private static DongleRecorder m_DongleRecorder = null;
    public static final int MSG_RECORDTHREAD_START = 1;
    public static final int MSG_RECORDTHREAD_STOP = 2;
    public static final int MSG_RECORDTHREAD_PREPARESTOP = 0;
    public static final int MSG_RECORDTHREAD_RELEASE = 3;
    public static final int MSG_MAIN_ONDATAEND = 6;
    public static final int MSG_MAIN_ONDATA = 5;
    public static final int MSG_MAIN_ONERROR = 10;
    public static final int TIME_READ_RECORDDATA = 50;
    private Context mContext;
    private static byte[] mDataBuffer = null;
    private Queue<FrameItem> mRdDataQueue;
    private long lastWriteTime = 0;
    private final int WRITE_AUDIO_TIME_INTERVAL = 40;
    private OutputStream m_streamAudioLog = null;
    private int decodeFrameSize = ICOCodec.getDecodeFrameSize();
    private static boolean isStarted = false;
    private static boolean isEnded = false;
    private boolean needdecode = false;
    private static int dongleId;

    private Handler m_Handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_MAIN_ONDATA:
                    if (!mRecognize) {
                        // 暂时在java层解码
                        byte[] buffer = (byte[]) msg.obj;
                        byte[] decodeBuffer = new byte[decodeFrameSize]; // 解码缓冲
                        if (ICOCodec.decode(buffer, decodeBuffer)) {
                            Log.d(TAG, "decode ok ");
                            mlsn.onRecordData(decodeBuffer, decodeBuffer.length, -1);
                        }
                        break;
                    }
                    long curTime = System.currentTimeMillis();
                    if (needdecode) {
                        byte[] decodeBuffer = new byte[decodeFrameSize]; // 解码缓冲
                        byte[] barray = (byte[]) msg.obj;
                        if (barray != null) {
                            // 解码并写文件
                            if (ICOCodec.decode(barray, decodeBuffer)) {
                                FrameItem item = new FrameItem();
                                item.buffer = decodeBuffer;
                                item.volume = msg.arg1;
                                mRdDataQueue.add(item);
                            } else {
                                Log.d(TAG, "decode data error");
                            }
                        }
                    } else {
                        FrameItem item = new FrameItem();
                        item.buffer = (byte[]) msg.obj;
                        item.volume = msg.arg1;
                        mRdDataQueue.add(item);
                    }
                    if (curTime - lastWriteTime > WRITE_AUDIO_TIME_INTERVAL) {
                        // 读缓冲区数据--start--
                        if (mRdDataQueue.size() > 0) {
                            int len = mRdDataQueue.size();
                            if (needdecode) {
                                byte[] buffer = new byte[len * decodeFrameSize];
                                int volumeTotal = 0;
                                for (int i = 0; i < len; i++) {
                                    FrameItem item = mRdDataQueue.poll();
                                    volumeTotal += item.volume;
                                    byte[] barray = item.buffer;
                                    if (barray != null) {
                                        System.arraycopy(barray, 0, buffer,
                                                decodeFrameSize * i,
                                                decodeFrameSize);
                                    }
                                }
                                int volume = volumeTotal / len;
                                mlsn.onRecordData(buffer, buffer.length, volume);
                            } else {
                                byte[] buffer = new byte[len
                                        * WRITE_AUDIO_TIME_INTERVAL];
                                int volumeTotal = 0;
                                for (int i = 0; i < len; i++) {
                                    FrameItem item = mRdDataQueue.poll();
                                    volumeTotal += item.volume;
                                    byte[] barray = item.buffer;
                                    if (barray != null) {
                                        System.arraycopy(barray, 0, buffer, 40 * i,
                                                40);
                                    }
                                }
                                int volume = volumeTotal / len;
                                mlsn.onRecordData(buffer, buffer.length, volume);
                            }
                            mRdDataQueue.clear();
                            lastWriteTime = System.currentTimeMillis();
                        }
                    }
                    break;
                case MSG_MAIN_ONDATAEND:
                    // 防止有最后数据没有写入msc
                    if (mRdDataQueue.size() > 0) {
                        int len = mRdDataQueue.size();
                        if (needdecode) {
                            byte[] buffer = new byte[len * decodeFrameSize];
                            int volumeTotal = 0;
                            for (int i = 0; i < len; i++) {
                                FrameItem item = mRdDataQueue.poll();
                                volumeTotal += item.volume;
                                byte[] barray = item.buffer;
                                if (barray != null) {
                                    System.arraycopy(barray, 0, buffer,
                                            decodeFrameSize * i, decodeFrameSize);
                                }
                            }
                            int volume = volumeTotal / len;
                            mlsn.onRecordData(buffer, buffer.length, volume);
                        } else {
                            byte[] buffer = new byte[len * WRITE_AUDIO_TIME_INTERVAL];
                            int volumeTotal = 0;
                            for (int i = 0; i < len; i++) {
                                FrameItem item = mRdDataQueue.poll();
                                volumeTotal += item.volume;
                                byte[] barray = item.buffer;
                                if (barray != null)
                                    System.arraycopy(barray, 0, buffer, 40 * i, 40);
                            }
                            int volume = volumeTotal / len;
                            mlsn.onRecordData(buffer, buffer.length, volume);
                        }
                        mRdDataQueue.clear();
                        lastWriteTime = System.currentTimeMillis();
                    }
                    mlsn.onDataEnd();
                    break;
                case MSG_MAIN_ONERROR:
                    mlsn.onError("录音设备出现问题，烦请稍候再试一下哦");
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }

    };
    private Handler m_RecordHandler;
    private IRecordListener mlsn;
    private Thread mRecordThread = new Thread(new Runnable() {

        @Override
        public void run() {
            Looper.prepare();
            m_RecordHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MSG_RECORDTHREAD_START:
                            __startRecording();
                            break;
                        case MSG_RECORDTHREAD_STOP:
                        case MSG_RECORDTHREAD_PREPARESTOP:
                            __stopRecording();
                            break;
                        default:
                            super.handleMessage(msg);
                            break;
                    }
                }
            };
            Looper.loop();
        }

        private void __startRecording() {
            Log.d(TAG,
                    "startRecording begin_" + System.currentTimeMillis());
            Log.d(TAG,
                    "startRecording time = " + System.currentTimeMillis()
                            + " dongleId=" + dongleId);
            // ICOCodec.reset(ICOCodec.ICO_DECODER);// 重置编解码器
            boolean bStart = RcDeviceManager.getInstance().startIspVoiceRecord(
                    dongleId, mVoiceDataEvent);

            Log.d(TAG, "start isp voice record: " + bStart);
            if (bStart) {
                // 正在录音
                Log.d(TAG, "正在录音。。。。。。");
                isStarted = true;
                isEnded = false;
            } else {
                Log.d(TAG, "无法开始录音");
                m_Handler.sendEmptyMessage(MSG_MAIN_ONERROR);
            }
            Log.d(TAG, "startRecording end" + System.currentTimeMillis());
        }

        private void __stopRecording() {
            Log.d(TAG, "stopRecording " + System.currentTimeMillis());
            if (isStarted && !isEnded) {
                Log.d(TAG,
                        "stopRecording time = " + System.currentTimeMillis());
                RcDeviceManager.getInstance().stopIspVoiceRecord(dongleId,
                        mVoiceDataEvent);
                isStarted = false;
                isEnded = true;
            }
        }

    });

    public static DongleRecorder getDongleRecorder(Context context, int id) {
        Log.d(TAG, "getDongleRecorder");
        dongleId = id;
        if (m_DongleRecorder == null) {
            /*mDataBuffer = new byte[16 * TIME_READ_RECORDDATA * 2 * 2];
            try {
                InputStream io = null;
                io = context.getResources().getAssets().open("100ms.pcm");
                int len = 0;
                int start = 0;
                while ((len = io.read(mDataBuffer, (start = start + len),
                        mDataBuffer.length - start)) > 0)
                    ;
            } catch (Exception e) {
                e.printStackTrace();
            }*/
            m_DongleRecorder = new DongleRecorder(context);
        }
        return m_DongleRecorder;
    }

    private final VoiceDataEvent mVoiceDataEvent = new VoiceDataEvent() {

        @Override
        public void onRecvData(DONGLE_VOICE_CODEC codecType, byte[] recvData,
                               int dataLen, int volume) {
            Log.d(TAG, "onRecvData 1");
            if (codecType != DONGLE_VOICE_CODEC.ico_codec) {
                return;
            }

            if (dataLen != FRAME_SIZE) {
                Log.d(TAG, "dataLen != FRAME_SIZE, RECORD_ERROR");
                mlsn.onError("录音设备出错，请稍后重试！");
                return;
            }

            Message msg = m_Handler.obtainMessage(MSG_MAIN_ONDATA);
            msg.arg1 = volume;
            msg.obj = recvData.clone();
            m_Handler.sendMessage(msg);
        }

        @Override
        public void onRecordFinish() {
            Log.d(TAG,
                    "onRecordFinish time = " + System.currentTimeMillis());
            m_Handler.sendEmptyMessage(MSG_MAIN_ONDATAEND);
        }

    };

    private DongleRecorder(Context context) {
        mContext = context;
        // 解码库初始化
        ICOCodec.initCodec(ICOCodec.ICO_DECODER);
        // Siren7Codec.initCodec(16000, (short)7000, (short)40);
        mRdDataQueue = new LinkedList<FrameItem>();
        mRecordThread.start();
        stop();
    }

//    private TTSPreferData preferData;
    private boolean started = false;
    private boolean mRecognize;

    public void start(IRecordListener listener, boolean recognize) {
        mRecognize = recognize;
        if (started) {
            stop();
        }
        started = true;
        needdecode = true;
//        preferData = VoiceManager.readPreferences(mContext);
//        if (preferData != null
//                && (preferData.language != 1 || (preferData.language == 1 && XiriCore
//                .getInstance(mContext).isInGame())))
//            needdecode = true;
//        if (XiriCore.getInstance(mContext).isTranslateScene()
//                && XiriCore.getInstance(mContext).getTranslateMode() == Translate.EN2CH)
//            needdecode = true;
        while (m_RecordHandler == null) {
            SystemClock.sleep(5);
        }
        Log.d(TAG, "DongleRecorder.Start");
        mlsn = listener;
        m_RecordHandler.sendEmptyMessage(MSG_RECORDTHREAD_START);
        mRdDataQueue.clear();
    }

    public void stop() {
        Log.d(TAG, "stop");
        started = false;
        if (m_streamAudioLog != null) {
            try {
                m_streamAudioLog.close();
            } catch (IOException e) {
                //ignore
            }
            m_streamAudioLog = null;
        }

        while (m_RecordHandler == null) {
            SystemClock.sleep(5);
        }
        m_RecordHandler.sendEmptyMessage(MSG_RECORDTHREAD_STOP);
    }

    public Boolean preparestop() {
        Log.d(TAG, "preparestop");
        while (m_RecordHandler == null) {
            SystemClock.sleep(5);
        }
        m_RecordHandler.sendEmptyMessage(MSG_RECORDTHREAD_PREPARESTOP);
        return true;
    }

    public void release() {

    }

    static class FrameItem {
        public byte[] buffer;
        public int volume;
    }
}
