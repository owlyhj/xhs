package com.iflytek.xiri.tool;

public class ICOCodec {
    static {
        System.loadLibrary("icocodec");
    }

    public final static int ICO_ENCODER = 1; // 编码功能
    public final static int ICO_DECODER = 2; // 解码功能

    /**
     * 初始化ico codec
     *
     * @param initCoder 需要初始化的coder: ICO_ENCODER, ICO_DECODER 或 ICO_ENCODER |
     *                  ICO_DECODER
     */
    public native static void initCodec(int initCoder);

    /**
     * 解码
     *
     * @param inData  编码帧，大小等于getEncodeFrameSize()
     * @param outData 声音数据，加压出来的声音数据
     * @return
     */
    public native static boolean decode(byte[] inData, byte[] outData);

    /**
     * 编码
     *
     * @param inData  声音数据，大小等于getDecodeFrameSize()
     * @param outData 编码帧
     * @return
     */
    public native static boolean encode(byte[] inData, byte[] outData);

    /**
     * 编解码引擎复位
     *
     * @param coder 需要初始化的coder: ICO_ENCODER, ICO_DECODER 或 ICO_ENCODER |
     *              ICO_DECODER
     */
    public native static void reset(int coder);

    /**
     * 获取编码帧大小，单位字节。16k,16bit 的 encode frame size 是 40bytes
     *
     * @return
     */
    public native static int getEncodeFrameSize();

    /**
     * 获取解码帧大小，单位字节。16k,16bit 的 decode frame size 是 640bytes
     *
     * @return
     */
    public native static int getDecodeFrameSize();
}
