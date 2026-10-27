package app.onepve.geelyconsole.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * QQ音乐车机版官方 AIDL 直连控制器 (移植自吉利官方/老版本《QQ音乐助手》com.ecarx.qq.tool 核心机制)
 *
 * 核心原理：
 * 1. QQ音乐车机版 (com.tencent.qqmusiccar) 内部声明了官方远程通信服务:
 *    com.tencent.qqmusiccar.third.api.QQMusicApiService (AIDL 接口: com.tencent.qqmusic.third.api.contract.IQQMusicApi)
 * 2. 通过 IPC Binder 直接发送执行事务 (TRANSACTION_execute = 1)：
 *    - "skipToNext": 切下一首 (零系统按键拦截、零焦点竞争、无论前后台100%必切)
 *    - "skipToPrevious": 切上一首
 *    - "resumeMusic": 恢复播放 / 出声 (秒起播)
 *    - "pauseMusic": 暂停播放
 * 3. 当后台进程休眠时，通过专属 Schema (qqmusicactive://qq.com/aidl?p=...) 静默拉起。
 */
public class QQMusicCarController {

    private static final String TAG = "QQMusicCarController";
    public static final String PACKAGE_QQMUSICCAR = "com.tencent.qqmusiccar";
    private static final String SERVICE_ACTION = "com.tencent.qqmusiccar.third.api.QQMusicApiService";
    private static final String DESCRIPTOR = "com.tencent.qqmusic.third.api.contract.IQQMusicApi";
    private static final int TRANSACTION_EXECUTE = 1;

    private static volatile QQMusicCarController sInstance;
    private final Context mContext;
    private IBinder mRemote;
    private volatile boolean mBinding = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "QQMusicApiService connected successfully!");
            mRemote = service;
            mBinding = false;
            // 握手初始化 (参考 QQ音乐助手 e.java: sayHi)
            try {
                Bundle params = new Bundle();
                params.putInt("sdkVersionCode", 1);
                params.putString("platformType", "car");
                Bundle ret = executeRaw("hi", params);
                int code = ret != null ? ret.getInt("code", -1) : -1;
                Log.i(TAG, "QQMusicApiService handshake 'hi' ret=" + code);
            } catch (Throwable t) {
                Log.w(TAG, "Handshake failed: " + t.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "QQMusicApiService disconnected");
            mRemote = null;
            mBinding = false;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.w(TAG, "QQMusicApiService binding died");
            mRemote = null;
            mBinding = false;
        }
    };

    private QQMusicCarController(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public static QQMusicCarController getInstance(Context context) {
        if (sInstance == null) {
            synchronized (QQMusicCarController.class) {
                if (sInstance == null) {
                    sInstance = new QQMusicCarController(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * 检查车机是否安装了 QQ音乐车机版
     */
    public boolean isInstalled() {
        try {
            mContext.getPackageManager().getPackageInfo(PACKAGE_QQMUSICCAR, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 预热连接 QQ音乐车机版后台服务
     */
    public synchronized void ensureConnected() {
        if (!isInstalled()) {
            return;
        }
        if (mRemote != null && mRemote.isBinderAlive()) {
            return;
        }
        if (mBinding) {
            return;
        }
        try {
            mBinding = true;
            Intent intent = new Intent(SERVICE_ACTION);
            intent.setPackage(PACKAGE_QQMUSICCAR);
            boolean bound = mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                mBinding = false;
                Log.d(TAG, "bindService returned false, trying to wake process via schema...");
                startQQMusicProcess();
            }
        } catch (Throwable t) {
            mBinding = false;
            Log.w(TAG, "ensureConnected error: " + t.getMessage());
        }
    }

    /**
     * 发送 Schema 协议静默激活 QQ 音乐进程 (移植自 QQ音乐助手 CommonCmd.startQQMusicProcess)
     */
    public void startQQMusicProcess() {
        try {
            JSONObject json = new JSONObject();
            json.put("cmd", "start");
            json.put("action", 0);
            json.put("packageName", mContext.getPackageName());
            String uriStr = "qqmusicactive://qq.com/aidl?p=" + URLEncoder.encode(json.toString(), "utf-8");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriStr));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            Log.i(TAG, "Triggered QQMusicCar start process schema");
        } catch (Throwable t) {
            Log.w(TAG, "startQQMusicProcess failed: " + t.getMessage());
        }
    }

    /**
     * 底层 IPC 事务调用 (TRANSACTION_execute = 1)
     */
    private Bundle executeRaw(String method, Bundle params) {
        if (mRemote == null || !mRemote.isBinderAlive()) {
            ensureConnected();
            if (mRemote == null) {
                return null;
            }
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeString(method);
            if (params != null) {
                data.writeInt(1);
                params.writeToParcel(data, 0);
            } else {
                data.writeInt(0);
            }
            mRemote.transact(TRANSACTION_EXECUTE, data, reply, 0);
            reply.readException();
            if (reply.readInt() != 0) {
                return Bundle.CREATOR.createFromParcel(reply);
            }
            return null;
        } catch (RemoteException e) {
            Log.w(TAG, "RemoteException calling " + method + ": " + e.getMessage());
            mRemote = null;
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "Throwable calling " + method + ": " + t.getMessage());
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /**
     * 执行指定 API 并检查返回值 code == 0
     */
    public boolean execute(String method) {
        try {
            Bundle ret = executeRaw(method, null);
            if (ret != null) {
                int code = ret.getInt("code", -1);
                Log.i(TAG, "execute " + method + " code=" + code);
                return code == 0;
            }
        } catch (Throwable t) {
            Log.w(TAG, "execute " + method + " error: " + t.getMessage());
        }
        return false;
    }

    /**
     * 方控下一曲直发
     */
    public boolean skipToNext() {
        return execute("skipToNext");
    }

    /**
     * 方控上一曲直发
     */
    public boolean skipToPrevious() {
        return execute("skipToPrevious");
    }

    /**
     * 播放 / 恢复出声
     */
    public boolean resumeMusic() {
        return execute("resumeMusic");
    }

    /**
     * 暂停
     */
    public boolean pauseMusic() {
        return execute("pauseMusic");
    }
}
