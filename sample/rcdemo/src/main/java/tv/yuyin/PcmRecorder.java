package tv.yuyin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import java.io.InputStream;

@SuppressLint("HandlerLeak")
public class PcmRecorder implements IPCMRecorder {
    private static final String TAG = "PcmRecorder";
    private static PcmRecorder m_PcmRecorder = null;
    public static final int MSG_RECORDTHREAD_START = 1;
    public static final int MSG_RECORDTHREAD_STOP = 2;
    public static final int MSG_RECORDTHREAD_PREPARESTOP = 0;
    public static final int MSG_RECORDTHREAD_RELEASE = 3;
    public static final int MSG_RECORDTHREAD_READDATA = 4;
    public static final int MSG_MAIN_ONDATAEND = 6;
    public static final int MSG_MAIN_ONDATA = 5;
    public static final int MSG_MAIN_ONERROR = 10;
    public static final int TIME_READ_RECORDDATA = 50;
    private static Boolean mIsFirstReadData = true;
    private Context mContext;
    private static byte[] mDataBuffer = null;

    // 解决判定录音何时结束
    private long mStartTime = 0;
    private long mStopTime = 0;
    private long mDataCount = 0;

    private Handler m_Handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_MAIN_ONDATA:
                    byte[] datas = (byte[]) msg.obj;
                    if (datas != null && datas.length > 0) {
                        int vol = 0;
                        int l = datas.length / 2;
                        for (int i = 0; i < l; i++) {
                            int v0 = (((short) datas[i * 2 + 1] << 8) | datas[2 * i]);
                            vol = v0 > vol ? v0 : -v0 > vol ? -v0 : vol;
                        }
                        vol = (int) ((vol * 30) >> 15);
                        mlsn.onRecordData(datas, datas.length, vol);
                    }
                    break;
                case MSG_MAIN_ONDATAEND:
                    // Log.d("CALeTIME",
                    // "==> sendEmptyMessage(MSG_MAIN_ONDATAEND) starttime="+SystemClock.elapsedRealtime());
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
    IRecordListener mlsn;
    private Thread mRecordThread = new Thread(new Runnable() {
        private byte[] _dataBuffer = null;
        private AudioRecord _mRecorder = null;

        @Override
        public void run() {
            Looper.prepare();

            int framePeriod = 16 * TIME_READ_RECORDDATA;
            _dataBuffer = new byte[framePeriod * 2];
            m_RecordHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MSG_RECORDTHREAD_START:

                            __startRecording();
                            break;
                        case MSG_RECORDTHREAD_STOP:
                            __stopRecording();
                            break;
                        case MSG_RECORDTHREAD_PREPARESTOP:
                            Log.d(TAG,
                                    " MSG_RECORDTHREAD_PREPARESTOP starttime="
                                            + SystemClock.elapsedRealtime());
                            __preparestopRecording();
                            break;
                        case MSG_RECORDTHREAD_READDATA:
                            Log.d(TAG, "MSG_RECORDTHREAD_READDATA");
                            __readRecordData(false, 0);
                            m_RecordHandler
                                    .sendEmptyMessageDelayed(
                                            MSG_RECORDTHREAD_READDATA,
                                            TIME_READ_RECORDDATA);
                            break;
                        default:
                            super.handleMessage(msg);
                            break;
                    }
                }
            };
            Looper.loop();

            _mRecorder.release();
            _mRecorder = null;
        }

        private boolean __readRecordData(boolean last, long needCount) {
            // Log.d("CALTIME",
            // "==>__readRecordData starttime="+SystemClock.elapsedRealtime());
            int count = 0;
            try {
                if (_mRecorder != null) {
                    // if (mIsFirstReadData)
                    // {
                    // _setMutePcm();
                    // mIsFirstReadData = false;
                    // }
                    long time = SystemClock.elapsedRealtime();
                    Log.d("TIME", "==>_mRecorder.read starttime="
                            + SystemClock.elapsedRealtime());
                    count = _mRecorder.read(_dataBuffer, 0, _dataBuffer.length);
                    mDataCount += count;
                    Log.d("TIME", "count is " + count);
                    // Log.d("TIME",
                    // "<==_mRecorder.read starttime="
                    // + SystemClock.elapsedRealtime());
                    // if (SystemClock.elapsedRealtime() - time < 40 && count >
                    // 0)
                    // 改成按录音数据是否达到按下按键到放开按键的时间差来算
                    if (last) {
                        if (mDataCount < needCount && count > 0)
                            last = false;

                    }
                    if (count > 0) {
                        byte[] data = new byte[count];
                        System.arraycopy(_dataBuffer, 0, data, 0, count);
                        Message msg = m_Handler.obtainMessage(MSG_MAIN_ONDATA);
                        msg.obj = data;
                        m_Handler.sendMessage(msg);
                    } else {
                        // Log.d(TAG, "count = " + count);
                    }
                } else {
                    Log.d(TAG, "readRecordData null");
                }
            } catch (Exception e) {
            }
            // Log.d("CALTIME",
            // "<==__readRecordData starttime="+SystemClock.elapsedRealtime());
            return last;
        }

        private void __startRecording() {
            mStartTime = SystemClock.elapsedRealtime();
            mDataCount = 0;
            Log.d("ifly_timestamp", "XiriOpenAudio " + mStartTime);
            m_RecordHandler.removeMessages(MSG_RECORDTHREAD_READDATA);
            // Log.d(TAG, "startRecording begin_" +
            // SystemClock.elapsedRealtime());
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
            }
            if (_mRecorder != null) {
                if (_mRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    _mRecorder.stop();
                }
                _mRecorder.release();
                _mRecorder = null;
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    // //close_print_exception
                }
            }
            int recordBufferSize = 16 * 40 * 2 * 25;
            int min = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (recordBufferSize < min)
                recordBufferSize = min;
            _mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, recordBufferSize);
            if (_mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                _mRecorder.release();
                _mRecorder = null;
                m_Handler.sendEmptyMessage(MSG_MAIN_ONERROR);
                return;
            }
            _mRecorder.startRecording();
            if (_mRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                _mRecorder.stop();
                _mRecorder.release();
                _mRecorder = null;
                m_Handler.sendEmptyMessage(MSG_MAIN_ONERROR);
                return;
            }
            m_RecordHandler.sendEmptyMessageDelayed(MSG_RECORDTHREAD_READDATA,
                    1);
            Log.d(TAG,
                    "startRecording end" + SystemClock.elapsedRealtime());
        }

        private void _setMutePcm() {
            Message msg = m_Handler.obtainMessage(MSG_MAIN_ONDATA);
            msg.obj = mDataBuffer;
            m_Handler.sendMessage(msg);
        }

        private void __stopRecording() {
            long count = (SystemClock.elapsedRealtime() - mStartTime) * 16 * 2;
            m_RecordHandler.removeMessages(MSG_RECORDTHREAD_READDATA);
            Log.d(TAG, "stopRecording" + SystemClock.elapsedRealtime());
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
            }
            if (_mRecorder != null) {
                if (_mRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    Log.d("ifly_timestamp", "XiriStopAudio");
                    while (!__readRecordData(true, count))
                        ;
                    _setMutePcm();
                    _mRecorder.stop();
                    m_Handler.sendEmptyMessage(MSG_MAIN_ONDATAEND);

                }
                _mRecorder.release();
                _mRecorder = null;
            }
        }

        private void __preparestopRecording() {

            Log.d(
                    TAG,
                    " __preparestopRecording starttime="
                            + SystemClock.elapsedRealtime());
            long count = (SystemClock.elapsedRealtime() - mStartTime) * 16 * 2;
            m_RecordHandler.removeMessages(MSG_RECORDTHREAD_READDATA);
            Log.d(TAG,
                    "preparestopRecording" + SystemClock.elapsedRealtime());
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
            }
            if (_mRecorder != null) {
                if (_mRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    Log.d(TAG,
                            "==> while (!__readRecordData(true)) starttime="
                                    + SystemClock.elapsedRealtime());
                    while (!__readRecordData(true, count)) {

                    }
                    ;
                    Log.d(TAG,
                            "<== while (!__readRecordData(true)) starttime="
                                    + SystemClock.elapsedRealtime());
                    // Log.d("CALTIME",
                    // "<== sendEmptyMessage(MSG_MAIN_ONDATAEND) starttime="+SystemClock.elapsedRealtime());
                    _setMutePcm();
                    m_Handler.sendEmptyMessage(MSG_MAIN_ONDATAEND);
                    _mRecorder.stop();
                    Log.d("ifly_timestamp", "XiriStopAudio");

                }
                _mRecorder.release();
                _mRecorder = null;
            }
        }
    });

    public static PcmRecorder getPcmRecorder(Context cntext) {
        if (m_PcmRecorder == null) {
            mDataBuffer = new byte[16 * TIME_READ_RECORDDATA * 2 * 2];
            try {
                InputStream io = null;
                io = cntext.getResources().getAssets().open("100ms.pcm");
                int len = 0;
                int start = 0;
                while ((len = io.read(mDataBuffer, (start = start + len),
                        mDataBuffer.length - start)) > 0)
                    ;
            } catch (Exception e) {

            }
            m_PcmRecorder = new PcmRecorder(cntext);
        }
        return m_PcmRecorder;
    }

    private PcmRecorder(Context cntext) {
        mContext = cntext;
        mRecordThread.start();
    }

    public void start(IRecordListener listener, boolean recognize) {
        mIsFirstReadData = true;
        while (m_RecordHandler == null) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                // close_print_exception
            }
        }
        mlsn = listener;
        Log.d(TAG, "PcmRecorder.Start");
        m_RecordHandler.sendEmptyMessage(MSG_RECORDTHREAD_START);
    }

    public void stop() {
        while (m_RecordHandler == null) {

            Log.e(TAG, " stop recorderhandler is null");
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                // close_print_exception
            }
        }
        m_RecordHandler.sendEmptyMessage(MSG_RECORDTHREAD_STOP);
    }

    public Boolean preparestop() {
        Log.d(TAG,
                " preparedtop starttime=" + SystemClock.elapsedRealtime());

        while (m_RecordHandler == null) {
            Log.e(TAG, " preparestop recorderhandler is null");
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                // close_print_exception
            }
        }
        Log.d(TAG,
                " preparedtop starttime 2=" + SystemClock.elapsedRealtime());

        m_RecordHandler.sendEmptyMessage(MSG_RECORDTHREAD_PREPARESTOP);
        Log.d(TAG,
                " preparedtop starttime 3=" + SystemClock.elapsedRealtime());
        return true;
    }

    public void release() {

    }
}
