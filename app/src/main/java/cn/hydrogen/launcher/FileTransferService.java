package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import java.net.*;
import java.util.*;

/** A user-started foreground service. Closing the file screen does not interrupt an upload. */
public class FileTransferService extends Service {
    static final String CHANGED="cn.windchaser.unified.TRANSFER_CHANGED",STOP="cn.windchaser.unified.STOP_TRANSFER";
    static volatile String state="HTTP / HTTPS 服务未开启";static volatile int port;static volatile boolean running,starting;
    private volatile TransferServer server;private volatile boolean destroyed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private static FileTransferService owner;
    @Override public void onCreate(){super.onCreate();owner=this;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(server!=null||starting)return START_NOT_STICKY;
        int chosen=intent==null?preferredPort(this):intent.getIntExtra("port",preferredPort(this));if(chosen<1024||chosen>65535){state="端口需在 1024 到 65535 之间";notifyChange();stopSelf();return START_NOT_STICKY;}
        starting=true;state="正在准备本机 HTTPS 证书…";foreground();notifyChange();
        new Thread(()->{try{
            TransferServer next=new TransferServer(this,StorageFiles.transfer(this),this::notifyChange);server=next;if(destroyed){next.close();return;}next.start(chosen);
            if(destroyed){next.close();return;}
            main.post(()->publishStarted(next));
        }catch(Exception error){TransferServer failed=server;if(failed!=null)failed.close();main.post(()->{if(destroyed||owner!=this)return;LauncherApp.record("HTTPS 文件传输启动失败",error);state="启动失败："+error.getClass().getSimpleName()+"，请检查存储权限或更换端口";starting=false;notifyChange();stopSelf();});}},"transfer-start").start();
        return START_NOT_STICKY;
    }
    private void publishStarted(TransferServer next){
        // Lifecycle callbacks and published state share the main queue. A late
        // worker cannot revive a closed service or overwrite its replacement.
        if(destroyed||owner!=this){next.close();return;}
        port=next.port();getSharedPreferences("transfer-settings",MODE_PRIVATE).edit().putInt("port",port).apply();running=true;starting=false;state="HTTP / HTTPS 服务已开启";notifyChange();
    }
    void notifyChange(){sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));}
    static synchronized android.content.SharedPreferences settings(Context context){
        android.content.SharedPreferences prefs=context.getSharedPreferences("transfer-settings",MODE_PRIVATE);
        // Earlier releases generated the PIN and saved 8443 even when the user never edited it.
        if(!prefs.getBoolean("defaults057",false)){
            android.content.SharedPreferences.Editor edit=prefs.edit().putString("accessCode","123456").putBoolean("defaults057",true);
            if(!prefs.contains("port")||prefs.getInt("port",8888)==8443)edit.putInt("port",8888);
            if(!edit.commit())throw new IllegalStateException("文件传输设置保存失败");
        }return prefs;
    }
    static int preferredPort(Context context){return settings(context).getInt("port",8888);}
    static List<String> addresses(){ArrayList<String> out=new ArrayList<>();try{Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){NetworkInterface nic=interfaces.nextElement();if(!nic.isUp()||nic.isLoopback())continue;Enumeration<InetAddress> addresses=nic.getInetAddresses();while(addresses.hasMoreElements()){InetAddress address=addresses.nextElement();if(address instanceof Inet4Address&&!address.isLoopbackAddress()&&!address.isLinkLocalAddress()){String host=address.getHostAddress()+":"+port;out.add("http://"+host);out.add("https://"+host);}}}}catch(Exception ignored){}return out;}
    private void foreground(){String channel="file-transfer";if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(channel,"局域网文件传输",NotificationManager.IMPORTANCE_LOW));int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        PendingIntent open=PendingIntent.getActivity(this,81,new Intent(this,FileCenterActivity.class).putExtra("transfer",true),flags),stop=PendingIntent.getService(this,82,new Intent(this,FileTransferService.class).setAction(STOP),flags);
        Notification.Builder notification=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        startForeground(81,notification.setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("追风桌面 · 文件传输").setContentText("局域网 HTTP / HTTPS 服务正在运行，点击返回；可随时关闭").setOngoing(true).setContentIntent(open).addAction(android.R.drawable.ic_menu_close_clear_cancel,"关闭服务",stop).build());
    }
    @Override public void onDestroy(){destroyed=true;if(server!=null)server.close();if(owner==this){owner=null;running=starting=false;port=0;if(!state.startsWith("启动失败"))state="HTTP / HTTPS 服务已关闭";notifyChange();}stopForeground(true);super.onDestroy();}
}
