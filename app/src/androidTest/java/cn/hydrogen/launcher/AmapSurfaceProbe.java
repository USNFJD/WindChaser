package cn.hydrogen.launcher;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Test APK only. Ordinary exported Binder interface, no system permissions. */
public class AmapSurfaceProbe extends Activity implements SurfaceHolder.Callback {
    static final String TOKEN="com.autosimilarwidget.view.IAutoSimilarWidgetViewService",CALLBACK="com.autosimilarwidget.view.IAutoWidgetStateProvider";
    private final StringBuilder report=new StringBuilder();private final Handler main=new Handler(Looper.getMainLooper());
    private IBinder service;private Surface surface;private boolean bound,added;private TextView text;
    private final Binder callback=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
        if(code==INTERFACE_TRANSACTION){reply.writeString(CALLBACK);return true;}if(code>=1&&code<=3){data.enforceInterface(CALLBACK);int value=code==3?data.readInt():0;main.post(()->log("CALLBACK code="+code+" value="+value));reply.writeNoException();return true;}return super.onTransact(code,data,reply,flags);
    }};
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){service=binder;try{log("descriptor="+binder.getInterfaceDescriptor());Parcel in=Parcel.obtain(),out=Parcel.obtain();try{in.writeInterfaceToken(TOKEN);service.transact(3,in,out,0);out.readException();log("isMapRunning="+out.readInt());}finally{in.recycle();out.recycle();}in=Parcel.obtain();out=Parcel.obtain();try{in.writeInterfaceToken(TOKEN);in.writeStrongBinder(callback);service.transact(4,in,out,0);out.readException();}finally{in.recycle();out.recycle();}attach();}catch(Exception e){log("FAIL "+e);}}
        public void onServiceDisconnected(ComponentName name){service=null;log("disconnected");}
    };
    public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);text=new TextView(this);root.addView(text);SurfaceView view=new SurfaceView(this);view.getHolder().addCallback(this);root.addView(view,new LinearLayout.LayoutParams(640,360));setContentView(root);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);try{bound=bindService(new Intent().setComponent(new ComponentName("com.autonavi.amapauto","com.autonavi.amapauto.adapter.internal.widget.AutoSimilarWidgetService")),connection,BIND_AUTO_CREATE);log("bind="+bound);}catch(Exception e){log("FAIL "+e);}main.postDelayed(()->log("PROBE_COMPLETE"),18000);}
    private void log(String s){report.append(s).append('\n');text.setText(report);try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(getFilesDir(),"amap-surface-probe.txt")),"UTF-8")){w.write(report.toString());}catch(Exception ignored){}}
    private void attach(){if(added||service==null||surface==null||!surface.isValid())return;try{transactSurface(1);added=true;log("surface attached mode=0");}catch(Exception e){log("FAIL attach "+e);}}
    private void transactSurface(int code)throws Exception{Parcel in=Parcel.obtain(),out=Parcel.obtain();try{in.writeInterfaceToken(TOKEN);in.writeInt(1);surface.writeToParcel(in,0);in.writeInt(0);service.transact(code,in,out,0);out.readException();}finally{in.recycle();out.recycle();}}
    public void surfaceCreated(SurfaceHolder h){surface=h.getSurface();attach();}public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}public void surfaceDestroyed(SurfaceHolder h){detach();surface=null;}
    private void detach(){if(added&&service!=null)try{transactSurface(2);}catch(Exception e){log("detach "+e);}added=false;}
    protected void onDestroy(){main.removeCallbacksAndMessages(null);detach();if(bound)unbindService(connection);super.onDestroy();}
}
