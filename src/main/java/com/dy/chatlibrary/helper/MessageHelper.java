package com.dy.chatlibrary.helper;

import android.os.Handler;
import android.os.Message;

import com.dy.chatlibrary.DySocketClient;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Administrator on 2016/10/10.
 */
public class MessageHelper {

    private byte[] lenght = new byte[4];
    private int nextLenght = 4;//下次需要读取的内容长度的byte个数

    private byte[] contentBuff;

    private int nextReadLenght = 0;//内容数组contentbuff填充数据的长度
    private int nextStartPosition = 0;//内容数组contentBuff填充数据的开始位置
    private int mContentLenght = 0;//包内容的长度

    public BlockingQueue<String> inQueue;

    public BlockingQueue<String> outQueue;

    private DySocketClient dySocketClient;

    public MessageHelper(DySocketClient client) {
        inQueue = new LinkedBlockingQueue<>();
        outQueue = new LinkedBlockingQueue<>();
        dySocketClient = client;
    }

    public MessageHelper() {

    }

    /**
     * 开始解包
     */
    private int count_of_msg_header_byte = 0;
    private int count_of_msg_content = 0;
    private ByteBuffer completefram = null;
    private byte headerBuff []= new byte[4];
    public void startUnpack(ByteBuffer byteBuffer){
        while (byteBuffer.hasRemaining()){

            byteBuffer.mark();

            for (int i = count_of_msg_header_byte; i < 4; i++) {
                headerBuff[i] = byteBuffer.get();
                count_of_msg_header_byte++;
            }

            if (count_of_msg_header_byte != 4){
                return;
            }

            if (count_of_msg_content <= 0) {
                count_of_msg_content = byte2int(headerBuff) - 4;

                if (count_of_msg_content > 2097152) {
                    //解析出错
                    return;
                }

                completefram = ByteBuffer.allocate(count_of_msg_content);
            }

            if (completefram != null) {
                int available_receiver_byte = byteBuffer.remaining();
                int next_count_to_complete_fram = completefram.remaining();

                if (available_receiver_byte < next_count_to_complete_fram){
                    completefram.put(byteBuffer.array(), byteBuffer.position(), available_receiver_byte);
                    byteBuffer.position(byteBuffer.position() + available_receiver_byte);
                    return;
                }

                completefram.put(byteBuffer.array(), byteBuffer.position(), next_count_to_complete_fram);
                byteBuffer.position(byteBuffer.position() + next_count_to_complete_fram);

                post(completefram.array());
                completefram = null;
                count_of_msg_header_byte = 0;
                count_of_msg_content = 0;
                headerBuff = new byte[4];
            }

        }
    }


    /**
     * 发送消息
     *
     * @param byteBuff
     */
    private void post(byte [] byteBuff) {

        if (dySocketClient != null && byteBuff != null && byteBuff.length > 0 ){
            dySocketClient.message(new String(byteBuff));
        }

    }

    /**
     * integer 转换成 byte
     */
    public static byte[] intToByteArray(final int integer) {
        int byteNum = (40 - Integer.numberOfLeadingZeros(integer < 0 ? ~integer : integer)) / 8;
        byte[] byteArray = new byte[4];

        for (int n = 0; n < byteNum; n++)
            byteArray[3 - n] = (byte) (integer >>> (n * 8));

        return (byteArray);
    }


    /**
     * 从高位开始，byte转换成integer
     */
    public static int byte2int(byte[] res) {
        int value;
        value = (int) (((res[0] & 0xFF) << 24)
                | ((res[1] & 0xFF) << 16)
                | ((res[2] & 0xFF) << 8)
                | (res[3] & 0xFF));
        return value;
    }
}
