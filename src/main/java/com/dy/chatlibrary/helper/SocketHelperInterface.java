package com.dy.chatlibrary.helper;

/**
 * Created by Administrator on 2016/10/10.
 */
public interface SocketHelperInterface {
    public void connect();
    public void onSocketClose();
    public void onSocketError(Exception e);
}
