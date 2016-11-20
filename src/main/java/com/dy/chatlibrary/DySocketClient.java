package com.dy.chatlibrary;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.dy.chatlibrary.common.ChatConfigs;
import com.dy.chatlibrary.helper.MessageHelper;
import com.dy.chatlibrary.helper.SocketHelperInterface;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * Created by wudayong on 2016/11/19.
 */

public abstract class DySocketClient implements Runnable, SocketHelperInterface {


    private static final int SIZE_BUFF_READ = 16384;

    //    public static final int
    private int state = ChatConfigs.STATE_SOCKET_CONNCTE_CLOSE;

    //服务端地址
    private String host;
    //端口号
    private int port;

    //发送消息线程
    private Thread writeThread;

    //socket 客户端
    private Socket socket;
    //socket 输出流
    private OutputStream outputStream;
    //socket 输入流
    private InputStream inputStream;

    //解包助手类
    private MessageHelper messageHelper;


    public DySocketClient(String host, int port) {

        if (TextUtils.isEmpty(host) || port == 0) {
            throw new IllegalArgumentException();
        }

        this.host = host;
        this.port = port;

        messageHelper = new MessageHelper(this);
    }

    @Override
    public void connect() {
        if (state == ChatConfigs.STATE_SOCKET_CONNCTED || state == ChatConfigs.STATE_SOCKET_CONNCTING )
            return;

        state = ChatConfigs.STATE_SOCKET_CONNCTING;

//        mThreadPool.execute(this);
        writeThread = new Thread(this);
        writeThread.start();

    }

    public abstract void open();

    public abstract void close();

    public abstract void error(String error);

    public abstract void message(String msg);

    @Override
    public void run() {

        try {
            if (socket == null) {
                socket = new Socket(Proxy.NO_PROXY);
            }

            if (!socket.isBound()) {
                socket.connect(new InetSocketAddress(host, port), ChatConfigs.SOCKET_TIMEOUT);
            }
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            state = ChatConfigs.STATE_SOCKET_CONNCTED;

            open();
        } catch (Exception e) {
            onSocketClose();
            error("connect exception: " + e.getMessage());
            return;
        }

        writeThread = new Thread(new SocketWriteThread());
        writeThread.start();

        byte[] buff = new byte[SIZE_BUFF_READ];
        int readBytes;

        try {
            while (state != ChatConfigs.STATE_SOCKET_CONNCTE_CLOSE && (readBytes = inputStream.read(buff)) != -1) {
                messageHelper.startUnpack(ByteBuffer.wrap(buff, 0, readBytes));
            }

            //来到这里，线程准备关闭
            onSocketClose();

        } catch (IOException e) {
            e.printStackTrace();
            onSocketClose();
            error("read exception: " + e.getMessage());
        }
    }

    private class SocketWriteThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.interrupted()) {

                if (inputStream == null || outputStream == null){
                    return;
                }

                try {
                    String msg = messageHelper.outQueue.take();

                    outputStream.write(MessageHelper.intToByteArray(msg.toString().getBytes().length + 4));
                    outputStream.write(msg.toString().getBytes());
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    onSocketClose();
                    error("send exception: " + e.getMessage());


                }catch (InterruptedException e){

                }
            }
        }
    }

    public void send(String msg) {
        messageHelper.outQueue.add(msg);
    }

    @Override
    public void onSocketClose() {
        if (state == ChatConfigs.STATE_SOCKET_CONNCTE_CLOSE)
            return;


        try {

            if (socket != null) {
                socket.close();
                socket = null;
            }

            if (writeThread != null){
                writeThread.interrupt();
                writeThread = null;
            }


        } catch (IOException e) {
            e.printStackTrace();
            onSocketError(e);
        }finally {

            close();

        }

        state = ChatConfigs.STATE_SOCKET_CONNCTE_CLOSE;

    }

    @Override
    public void onSocketError(Exception e) {
        error(e.getMessage());
        state = ChatConfigs.STATE_SOCKET_CONNCTE_ERROR;
    }

    public int getState() {
        return state;
    }

}
