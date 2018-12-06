package tv.yuyin;

public interface IPCMRecorder {
    public interface IRecordListener {
        int onRecordData(byte[] dataBuffer, int length, int volume);

        void onError(String desc);

        void onDataEnd();
    }

    public interface IAECRecordListener {
        void onRecordData(byte[] dataBuffer, int length);

        void onError(int errorCode);

        void onDataEnd();
    }

    public void start(IRecordListener listener, boolean recognize);

    public void stop();

    public Boolean preparestop();

    public void release();
}
