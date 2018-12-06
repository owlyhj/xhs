package tv.yuyin;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.rc.dongle.RcDeviceInfo;
import com.iflytek.rc.dongle.RcDeviceManager;
import com.iflytek.rc.dongle.RcDeviceManagerEvent;
import com.iflytek.rc.utility.DongleKeyDefines;
import com.iflytek.rc.utility.DongleTypes;

import android.hardware.usb.UsbDevice;

import java.io.FileOutputStream;

import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.util.Log;
import android.widget.Toast;

import java.io.FileInputStream;



public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    private TextView mStatusView;
    private TextView mInfoView;
    private Button mPlayBtn;
    private Button mExitBtn;
    private Button mGetStatus;
    private Button mSendIR;
    private Button mClear;
    private FileOutputStream mRecordFos = null;
    private final String RECORD_PATH = Environment.getExternalStorageDirectory().getPath() + "/remotedemo.pcm";
    private Boolean isPlay = false;
    private AudioPlayer mAudioPlayer;
    private Context mContext;
    private RcDeviceManager mRcDeviceManager = RcDeviceManager.getInstance();
    private final int GET_DEVICE_INFO = 0x001;
    private final int PLAY_RECORD = 0x002;
    private final int CLEAR = 0x003;
    private final int SEND_IR_CODE = 0x004;
    private int tempID;

    private byte[] ircode = new byte[2];

    // 语音识别对象
    private SpeechRecognizer mAsr;
    // 返回结果格式，支持：plain,xml,json
    private String mResultType = "plain";
    private  final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    private  final String GRAMMAR_TYPE_ABNF = "abnf";
    private  final String GRAMMAR_TYPE_BNF = "bnf";

    String mContent;// 语法、词典临时变量

    // 本地语法id
    private String mLocalGrammarID;
    // 本地语法文件
    private String mLocalGrammar = null;
    // 本地语法构建路径
    private String grmPath = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/msc/test";
    private Toast mToast;


    @Override
    protected void onDestroy() {
        finish();
        System.exit(0);
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        finish();
        System.exit(0);
        super.onStop();
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GET_DEVICE_INFO:
                    updateInfo(tempID);
                    break;
                case SEND_IR_CODE:
                    ircode[0] = 0x01;
                    ircode[1] = 0x02;
                    if(RcDeviceManager.getInstance().sendIrCode(tempID, ircode)){
                        mInfoView.append("红外发送成功！\n\r");
                    }else{
                        mInfoView.append("红外发送失败！\n\r");
                    }
                    int offset = mInfoView.getLineCount() * mInfoView.getLineHeight();
                    if (offset > (mInfoView.getHeight() - mInfoView.getLineHeight() - 10)) {
                        mInfoView.scrollTo(0, offset - mInfoView.getHeight() + mInfoView.getLineHeight() + 10);
                    }
                    break;
                case CLEAR:
                    mInfoView.scrollTo(0, 0);
                    mInfoView.setText("");
                    break;
                case PLAY_RECORD:
                    isPlay = true;
                    PlayerThread playerThread = new PlayerThread();
                    playerThread.start();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);
        mContext = this;

        mRcDeviceManager.init(mContext);
        mRcDeviceManager.addDongleCallBack(mDongleEvent);

        mStatusView = (TextView) findViewById(R.id.status);
        /*mInfoView = (TextView) findViewById(R.id.rcinfo);
        mInfoView.setMovementMethod(ScrollingMovementMethod.getInstance());
        mPlayBtn = (Button) findViewById(R.id.play);
        mGetStatus = (Button) findViewById(R.id.getStatus);
        mSendIR = (Button) findViewById(R.id.sendir);
        mSendIR.setOnClickListener(mClickLsn);
        mClear = (Button) findViewById(R.id.clear);
        mClear.setOnClickListener(mClickLsn);
        mGetStatus.setOnClickListener(mClickLsn);
        mPlayBtn.setOnClickListener(mClickLsn);
        mExitBtn = (Button) findViewById(R.id.exit);*/
        mExitBtn.setOnClickListener(mClickLsn);
        mAudioPlayer = new AudioPlayer();
        initASR();

        mToast = Toast.makeText(this,"",Toast.LENGTH_SHORT);

    }

    private View.OnClickListener mClickLsn = new View.OnClickListener() {

        public void onClick(View v) {
            switch (v.getId()) {
                /*case R.id.play:
                    mHandler.sendEmptyMessage(PLAY_RECORD);
                    break;
                case R.id.exit:
                    finish();
                    System.exit(0);
                    break;
                case R.id.getStatus:
                    mHandler.sendEmptyMessage(GET_DEVICE_INFO);
                    break;
                case R.id.sendir:
                    mHandler.sendEmptyMessage(SEND_IR_CODE);
                    break;
                case R.id.clear:
                    mHandler.sendEmptyMessage(CLEAR);
                    break;*/
            }

        }
    };


    private void updateInfo(int deviceID) {
        RcDeviceInfo dongleInfo = RcDeviceManager.getInstance().getRcDeviceInfo(deviceID);
        if (dongleInfo != null) {
            mInfoView.append("\n\r");
            mInfoView.append("deviceID=" + deviceID + "\n\r");
            mInfoView.append("rcConnectStatus=" + RcDeviceManager.getInstance().getRCStatus(deviceID) + "\n");
            mInfoView.append("deviceName=" + dongleInfo.deviceName + "\n");
            mInfoView.append("dongleType=" + dongleInfo.dongleType + "\n");
            mInfoView.append("dongleMarketInfo=" + RcDeviceManager.getInstance().getDongleMarketInfo(deviceID) + "\n");
            mInfoView.append("dongleUUID=" + RcDeviceManager.getInstance().getDongleUUID(deviceID) + "\n");
            mInfoView.append("rcMarketInfo=" + RcDeviceManager.getInstance().getRcMarketInfo(deviceID) + "\n");
            mInfoView.append("rcUUID=" + RcDeviceManager.getInstance().getRCUUID(deviceID) + "\n");
            mInfoView.post(new Runnable() {
                @Override
                public void run() {
                    int offset = mInfoView.getLineCount() * mInfoView.getLineHeight();
                    if (offset > (mInfoView.getHeight() - mInfoView.getLineHeight() - 10)) {
                        mInfoView.scrollTo(0, offset - mInfoView.getHeight() + mInfoView.getLineHeight() + 10);
                    }

                }
            });
        }
    }

    private RcDeviceManagerEvent mDongleEvent = new RcDeviceManagerEvent() {


        @Override
        public void onRemoteStatusChange(int deviceID, RcDeviceInfo rcDeviceInfo, RcDeviceManager.RcStatus rcStatus) {
//            if (rcDeviceInfo != null)
//                updateInfo(rcDeviceInfo.rcDeviceID);
            tempID = deviceID;
        }

        /**
         * dongle有keydown事件上报
         *
         * @param deviceID
         * @param dongleKeyCode
         */
        public void onDongleKeyDown(int deviceID, DongleKeyDefines dongleKeyCode) {
            if (dongleKeyCode == DongleKeyDefines.key_xiri) {
                tempID = deviceID;
                mStatusView.setText("语音键按下");
                buildGram();
                File file = new File(RECORD_PATH);
                Log.d(TAG, "recordPath:" + RECORD_PATH);
                if (file.exists())
                    file.delete();
                try {
                    mRecordFos = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
               /* if (mRecordFos == null) {
                    return;
                }*/

                RcDeviceInfo deviceInfo = RcDeviceManager.getInstance().getRcDeviceInfo(deviceID);
                if (deviceInfo.dongleType == DongleTypes.dongle_iflytek_young
                        || deviceInfo.dongleType == DongleTypes.dongle_third_party) {
                    PcmRecorder.getPcmRecorder(mContext).start(mIRecordLis, true);
                } else {
                    DongleRecorder.getDongleRecorder(mContext, deviceID).start(mIRecordLis, true);

                }

            }

        }

        /**
         * dongle有keyup事件上报
         *
         * @param deviceID
         * @param dongleKeyCode
         */
        public void onDongleKeyUp(int deviceID, DongleKeyDefines dongleKeyCode) {
            if (dongleKeyCode == DongleKeyDefines.key_xiri) {
                mStatusView.setText("语音键抬起");
                RcDeviceInfo deviceInfo = RcDeviceManager.getInstance().getRcDeviceInfo(deviceID);
                if (deviceInfo.dongleType == DongleTypes.dongle_iflytek_young
                        || deviceInfo.dongleType == DongleTypes.dongle_third_party) {
                    PcmRecorder.getPcmRecorder(mContext).preparestop();
                } else {
                    DongleRecorder.getDongleRecorder(mContext, deviceID).preparestop();

                }

                if (mAsr != null && mAsr.isListening()) {
                    Log.d("MscSpeechLog","stopListening");
                    mAsr.stopListening();
                }

                try {
                    mRecordFos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * 回调要求用户允许打开dongle设备的权限
         *
         * @param usbDevice
         * @return
         */
        public void requestForUsbPermission(UsbDevice usbDevice) {
            askForUsbPermission(usbDevice);
        }


        @Override
        public void requestForRcUUID(int deviceID) {

        }

        /**
         * 低电量
         */
        public void onLowPower() {
        }
    };

    /**
     * usb设备权限检查
     *
     * @param usbDevice
     * @return
     */
    private void askForUsbPermission(UsbDevice usbDevice) {
        if (usbDevice == null) {
            return;
        }
        UsbManager manager = (UsbManager) mContext
                .getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            return;
        }

        Toast.makeText(mContext, "勾选默认情况下用于该设备，并点击“确定”，就可以使用讯飞电视助手啦",
                Toast.LENGTH_LONG).show();
        // 询问是否可以操作dongle
        manager.requestPermission(usbDevice, PendingIntent.getBroadcast(
                mContext, 0, new Intent("com.iflytek.request.USB_PERMISSION"), 0));
    }


    IPCMRecorder.IRecordListener mIRecordLis = new IPCMRecorder.IRecordListener() {
        @Override
        public int onRecordData(byte[] dataBuffer, int length, int volume) {
            mStatusView.setText("音量" + volume);
            try {
                //mRecordFos.write(dataBuffer);

                mAsr.writeAudio(dataBuffer,0,length);

            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        public void onError(String desc) {
            mStatusView.setText("错误：" + desc);

        }

        @Override
        public void onDataEnd() {
            mStatusView.setText("录音结束");
            mHandler.sendEmptyMessage(PLAY_RECORD);
        }
    };

    // 播音线程
    class PlayerThread extends Thread {
        @Override
        public void run() {
            java.io.FileInputStream in;
            try {
                File f = new File(RECORD_PATH);
                if (!f.exists()) {
                    Log.e(TAG, "音频文件不存在：path:" + RECORD_PATH);
                    isPlay = false;
                    return;
                }
                in = new FileInputStream(f);
                byte[] buf = new byte[1024];
                mAudioPlayer.play();
                int len = 0;
                while ((len = in.read(buf)) != -1) {
                    mAudioPlayer.writeAudio(buf, 0, len);
                }

            } catch (Exception e) {
            }
            mAudioPlayer.stop();
            isPlay = false;
        }
    }

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                //showTip("初始化失败,错误码："+code);
            }else {
                //buildGram();
            }
        }
    };

    GrammarListener grammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if (error == null) {
                mLocalGrammarID = grammarId;
                showTip("语法构建成功：" + grammarId);
                setParams();
                int ret = mAsr.startListening(mRecognizerListener);
                if (ret != ErrorCode.SUCCESS) {
                    showTip("识别失败,错误码：" + ret);
                }
            } else {
                showTip("语法构建失败,错误码：" + error.getErrorCode());
            }
        }
    };

    private void buildGram() {
        int ret = 0;
        mLocalGrammar = FucUtil.readFile(this,"call.bnf", "utf-8");
        mContent = new String(mLocalGrammar);
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        // 设置文本编码格式
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING,"utf-8");
        // 设置引擎类型
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, "local");
        // 设置语法构建路径
        mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
        //使用8k音频的时候请解开注释
//					mAsr.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
        // 设置资源路径
        mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
        ret = mAsr.buildGrammar(GRAMMAR_TYPE_BNF, mContent, grammarListener);
        if(ret != ErrorCode.SUCCESS){
            showTip("语法构建失败,错误码：" + ret);
        }
    }

    private void initASR() {
        // 初始化识别对象
        mAsr = SpeechRecognizer.createRecognizer(this, mInitListener);

    }
    private void setParams() {
        if (TextUtils.isEmpty(mLocalGrammarID)) {
            showTip("请先构建语法");
            return;
        }
        // 清空参数
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        // 设置识别引擎
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, "local");

        mResultType = "plain";
        // 设置本地识别资源
        mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
        // 设置语法构建路径
        mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
        // 设置返回结果格式
        mAsr.setParameter(SpeechConstant.RESULT_TYPE, mResultType);
        // 设置本地识别使用语法id
        mAsr.setParameter(SpeechConstant.LOCAL_GRAMMAR, "call");
        // 设置识别的门限值
        mAsr.setParameter(SpeechConstant.MIXED_THRESHOLD, "30");

        mAsr.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");

    }

    //获取识别资源路径
    private String getResourcePath(){
        StringBuffer tempBuffer = new StringBuffer();
        //识别通用资源
        tempBuffer.append(ResourceUtil.generateResourcePath(this, ResourceUtil.RESOURCE_TYPE.assets, "asr/common.jet"));
        return tempBuffer.toString();
    }


    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            showTip(error.getPlainDescription(true));
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.d(TAG, results.getResultString());

            showTip("结果" + results.getResultString());

        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据："+data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };

}
