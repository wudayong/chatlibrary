package com.dy.chatlibrary.common;

/**
 * Created by wudayong on 2016/11/20.
 */

public class ChatConfigs {

    public static final int TIME_OUT = 30 * 1000;//30秒
    public static final int CONNECT_TIME_OUT = 30 * 1000;//30秒

    public static final int SOCKET_TIMEOUT = 20 * 1000;
    public static final int STATE_SOCKET_CONNCTING = 1;
    public static final int STATE_SOCKET_CONNCTED = 2;
    public static final int STATE_SOCKET_CONNCTE_CLOSE = 3;
    public static final int STATE_SOCKET_CONNCTE_ERROR = 4;
}
