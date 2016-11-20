package com.dy.chatlibrary;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;


/**
 * Created by wudayong on 2016/11/19.
 */

public class DyPushService extends Service{


    public static final String ACTION_START = "action_start";
    public static final String ACTION_STOP = "action_stop";
    public static final String ACTION_PING = "action_ping";
    public static final String ACTION_RECONNECT = "action_reconnect";


    private MyBinder myBinder = new MyBinder();

    //sharePreference key, 保持socket的启动和停止
    private static final String SP_SOCKET_STATE = "sp_socket_state";
    //保持上一次重连接socket的时间间隔,用于计算下一次重连的时间间隔
    private static final String SP_RECONNECT_INTERVAL_TIME = "sp_interval_time";

    //上次连接socket的时间
    private long mStartTime = 0l;

    //是否已经启动过socket
    private boolean wasStarted = false;

    //保持一些信息到本地
    private SharedPreferences mPrefs;

    //用于监听网络变化, 根据网络的状态让socket做相应的动作
    private ConnectivityManager mConneManager;
    //闹钟
    private AlarmManager alarmManager;

    //Socket
    private DySocketClient mClient;

    //服务器URL
    private static final String CHAT_HOST = "weida.products-test.zhuzhu.com";

    //端口号
    private static final int CHAT_PORT = 8384;

    //网络状态监听器
    private ConnectivityListener mConneListener;

    //每隔一段时间通过设定的闹钟,唤醒service; 30 分钟间隔
    private static final long SERVICE_KEEP_ALIVE_INTERVAL = 30 * 60 * 1000;

    //执行重新连接动作的最短时间间隔,10s
    private static final long RECONNECT_MIN_INTERVAL = 10 * 1000;
    //执行重新连接动作的最大时间间隔, 60s
    private static final long RECONNECT_MAX_INTERVAL = 60 * 1000;


    public static void startService(Context ctx){
        Intent start = new Intent(ctx, DyPushService.class);
        start.setAction(ACTION_START);
        ctx.startService(start);
    }

    public static void stopService(Context ctx){
        Intent start = new Intent(ctx, DyPushService.class);
        start.setAction(ACTION_STOP);
        ctx.startService(start);
    }

    public static void actionPing(Context ctx){
        Intent start = new Intent(ctx, DyPushService.class);
        start.setAction(ACTION_PING);
        ctx.startService(start);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mPrefs = getSharedPreferences(SP_SOCKET_STATE, MODE_PRIVATE);
        mConneManager = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        mConneListener = new ConnectivityListener();
        alarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);

        checkKillBySystem();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null){
            switch (intent.getAction()){
                case ACTION_START:
                    start();
                    break;
                case ACTION_STOP:
                    //停止socket
                    stop();
                    //停止service自身
                    stopSelf();

                    break;
                case ACTION_PING:
                    startPing();
                    break;
                case ACTION_RECONNECT:
                    break;
                default:
                    break;
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (wasStarted){
            stop();
        }
    }

    /**
     * 此方法是在oncreate中调用
     * 如果service被系统kill掉后,重启service将在此方法中重连socket
     * */
    private void checkKillBySystem(){
        if (wasStarted() == true){

            stopPing();

            //service被系统kill掉后,更加START_STICCKY重启
            //重新连接socket
            start();
        }
    }

    /**
     * 启动socket通信
     * 并且注册Connectivity BroadcastReceiver
     * */
    private synchronized void start(){

        if (wasStarted() == true){
            //如果已经启动,将不再往下执行
            return;
        }

        startPing();

        //连接socket
        connect();

        //注册广播, 监听网络状态
        registerReceiver(mConneListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

    }

    /**
     * 停止socket通信
     *
     * */
    private synchronized void stop(){
        if (wasStarted == false){
            return;
        }

        setStarted(false);

        stopPing();

        cancelReconnect();

        if (mClient != null){
            mClient.onSocketClose();
        }

        //解除注册广播
        unregisterReceiver(mConneListener);

    }

    private boolean wasStarted(){
        return mPrefs != null? mPrefs.getBoolean(SP_SOCKET_STATE, false) : false;
    }

    private void setStarted(boolean started){
        mPrefs.edit().putBoolean(SP_SOCKET_STATE, started).commit();
        wasStarted = started;
    }

    private void connect(){
        mClient = new DySocketClient(CHAT_HOST, CHAT_PORT) {
            @Override
            public void open() {
                setStarted(true);

                mStartTime = System.currentTimeMillis();

            }

            @Override
            public void message(String msg) {

            }

            @Override
            public void close() {
//                setStarted(false);
                scheduleReconnect();

            }

            @Override
            public void error(String error) {

            }

        };
        mClient.connect();
    }

    public void sendMessage(String msg){
        if (mClient == null || wasStarted == false){
            connect();
        }

        mClient.send(msg);

    }

    /**
     * service自身通过AlarmManager 每隔一段时间通过startService(Intent)唤醒自身。
     * 这是一种应用级的service保活的手段
     * */
    private void startPing (){
        Intent ping = new Intent(this, DyPushService.class);
        ping.setAction(ACTION_PING);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, ping, 0);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + SERVICE_KEEP_ALIVE_INTERVAL,
                SERVICE_KEEP_ALIVE_INTERVAL,
                pendingIntent);
    }

    private void stopPing (){
        Intent ping = new Intent(this, DyPushService.class);
        ping.setAction(ACTION_PING);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, ping, 0);
        alarmManager.cancel(pendingIntent);
    }

    /**
     * 当socket因一些原因关闭后,将自动在一段时间间隔后尝试重连,时间间隔在10s ~ 60s
     * 设置时间间隔是避免socket在瞬间连接多次,造成资源和性能的损耗
     * */
    private void scheduleReconnect(){
        long interval = mPrefs.getLong(SP_RECONNECT_INTERVAL_TIME, RECONNECT_MIN_INTERVAL);

        long now = System.currentTimeMillis();
        long elapsed = now - mStartTime;

        long nextInterval = 0l;

        if (elapsed < interval){
            //现在距离上次的连接时间 < 上次重连的间隔时间
            //那么2倍上次间隔时间或60s后再尝试重连
            nextInterval = Math.min(interval *2, RECONNECT_MAX_INTERVAL);
        }else {
            //现在距离上次的连接时间 >= 上次重连的间隔时间
            //那么10s后尝试重连
            nextInterval = RECONNECT_MIN_INTERVAL;
        }

        mPrefs.edit().putLong(SP_RECONNECT_INTERVAL_TIME, nextInterval).commit();

        Intent intent = new Intent(this, DyPushService.class);
        intent.setAction(ACTION_RECONNECT);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        alarmManager.set(AlarmManager.RTC_WAKEUP, now + nextInterval, pendingIntent);
    }

    /**
     * 取消socket一段时间后的重连
     * */
    private void cancelReconnect(){
        Intent intent = new Intent(this, DyPushService.class);
        intent.setAction(ACTION_RECONNECT);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        alarmManager.cancel(pendingIntent);
    }

    private boolean checkNetWork(){
        NetworkInfo info = mConneManager.getActiveNetworkInfo();
        if (info != null && info.isConnectedOrConnecting()){
            return true;
        }else {
            return false;
        }

    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    public class MyBinder extends Binder{
        public DyPushService getService(){return DyPushService.this;}
    }

    private class ConnectivityListener extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null){
                return;
            }

            switch (intent.getAction()) {
                case ConnectivityManager.CONNECTIVITY_ACTION:

                    if (mClient == null)
                        return;

                    if (checkNetWork()) {
                        //当网络可用时,尝试连接socket
                        connect();

                    } else {

                        //当网络不可用时,关闭socket
                        mClient.onSocketClose();
                    }
                    break;
            }
        }
    }
}
