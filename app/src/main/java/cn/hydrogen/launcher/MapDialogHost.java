package cn.hydrogen.launcher;

import android.app.Dialog;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import java.util.*;

/** Only known launcher-owned dialog windows may supply card focus proof. */
final class MapDialogHost {
    private final MainActivity activity;
    private final Runnable changed;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<Owned> windows=new LinkedHashSet<>();
    private final Set<Dialog> prepared=Collections.newSetFromMap(new WeakHashMap<Dialog,Boolean>());
    private long transferUntil;
    private Dialog pending;
    private boolean closed;
    private final Runnable settled=this::settled;
    private void settled(){transferUntil=0;pending=null;changed.run();}
    MapDialogHost(MainActivity activity,Runnable changed){this.activity=activity;this.changed=changed;}
    boolean inTransfer(){return pending!=null||!windows.isEmpty()||SystemClock.uptimeMillis()<transferUntil;}
    boolean focused(){
        if(closed||!activity.mapDialogEligible())return false;
        for(Owned owned:windows)if(owned.dialog.isShowing()&&owned.view.hasWindowFocus())return true;
        return SystemClock.uptimeMillis()<transferUntil;
    }
    private boolean eligible(Dialog dialog){return !closed&&activity.mapDialogEligible()&&prepared.contains(dialog)&&dialog.getWindow()!=null&&dialog.getWindow().getAttributes().type==WindowManager.LayoutParams.TYPE_APPLICATION;}
    private void transfer(){transferUntil=SystemClock.uptimeMillis()+500;handler.removeCallbacks(settled);handler.postDelayed(settled,500);}
    void showing(Dialog dialog){if(!eligible(dialog))return;pending=dialog;transfer();}
    void failed(Dialog dialog){prepared.remove(dialog);if(pending==dialog){pending=null;transferUntil=0;handler.removeCallbacks(settled);changed.run();}}
    void prepare(Dialog dialog){
        if(closed||!activity.mapDialogEligible()||dialog.getWindow()==null)return;
        prepared.add(dialog);pending=dialog;transfer();
        // The MAP remains TYPE_PHONE/TYPE_APPLICATION_OVERLAY. Our own dialog
        // needs no overlay grant and lives beside, rather than beneath, the map.
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION);
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        position(dialog);
    }
    void shown(Dialog dialog){
        if(!eligible(dialog)||!dialog.isShowing())return;
        position(dialog);
        for(Owned owned:windows)if(owned.dialog==dialog)return;
        View decor=dialog.getWindow().getDecorView();Owned owned=new Owned(dialog,decor);
        if(pending==dialog)pending=null;windows.add(owned);decor.addOnAttachStateChangeListener(owned);transfer();
        if(decor.getWindowToken()!=null)owned.onViewAttachedToWindow(decor);
        LauncherApp.record("桌面自有弹窗 type="+dialog.getWindow().getAttributes().type+" area="+activity.mapDialogArea(),null);
        changed.run();
    }
    private void position(Dialog dialog){
        Rect area=activity.mapDialogArea();if(area==null||area.width()<48||area.height()<48)return;
        Window window=dialog.getWindow();WindowManager.LayoutParams p=window.getAttributes();
        int width=Math.min(area.width(),p.width>0?p.width:Math.max(1,Math.round(activity.getResources().getDisplayMetrics().widthPixels*.5f)));
        int cap=Math.min(area.height(),Math.max(1,Math.round(activity.getResources().getDisplayMetrics().heightPixels*.62f)));
        int height=p.height>0?Math.min(cap,p.height):cap;
        int y=area.top+(area.height()-height)/2;
        int x=area.left+(area.width()-width)/2;
        int gravity=Gravity.TOP|Gravity.LEFT;
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        if(p.width!=width||p.height!=height||p.x!=x||p.y!=y||p.gravity!=gravity||p.dimAmount!=0){
            p.width=width;p.height=height;p.x=x;p.y=y;p.gravity=gravity;p.dimAmount=0;window.setAttributes(p);
        }
    }
    void dismissAll(){
        pending=null;transferUntil=0;handler.removeCallbacks(settled);
        for(Owned owned:new ArrayList<>(windows)){owned.dialog.dismiss();owned.detach();}
        windows.clear();prepared.clear();transferUntil=0;handler.removeCallbacks(settled);
    }
    void close(){closed=true;dismissAll();handler.removeCallbacksAndMessages(null);}
    @android.annotation.TargetApi(18)
    private static final class Focus18 {
        static Object attach(View view,Runnable changed){ViewTreeObserver.OnWindowFocusChangeListener listener=focused->changed.run();view.getViewTreeObserver().addOnWindowFocusChangeListener(listener);return listener;}
        static void detach(View view,Object listener){if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnWindowFocusChangeListener((ViewTreeObserver.OnWindowFocusChangeListener)listener);}
    }
    private final class Owned implements View.OnAttachStateChangeListener {
        final Dialog dialog;final View view;Object focus;
        Owned(Dialog dialog,View view){this.dialog=dialog;this.view=view;}
        public void onViewAttachedToWindow(View v){
            if(closed){dialog.dismiss();return;}if(!eligible(dialog)){detach();return;}
            windows.add(this);if(Build.VERSION.SDK_INT>=18&&focus==null)focus=Focus18.attach(view,changed);
            transfer();changed.run();
        }
        public void onViewDetachedFromWindow(View v){detach();prepared.remove(dialog);if(!closed&&activity.mapDialogEligible())transfer();changed.run();}
        void detach(){windows.remove(this);view.removeOnAttachStateChangeListener(this);
            if(Build.VERSION.SDK_INT>=18&&focus!=null){Focus18.detach(view,focus);focus=null;}}
    }
}
