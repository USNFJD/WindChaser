package cn.hydrogen.launcher;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

final class WidgetCoordinator {
    static final int BIND=201, CONFIGURE=202;
    final AppWidgetHost host; private final MainActivity activity; private final Config cfg;
    private final Map<Boolean,String> errors=new HashMap<>();
    String error(boolean music){return errors.get(music);}
    WidgetCoordinator(MainActivity a,Config c) { activity=a; cfg=c; host=new AppWidgetHost(a,1024); }
    void start() { try { host.startListening(); } catch(Exception e) { LauncherApp.record("小组件监听失败",e); } }
    void stop() { try { host.stopListening(); } catch(Exception e) { LauncherApp.record("停止小组件监听失败",e); } }
    AppWidgetHostView view(boolean music) {
        try{return createView(music);}catch(Exception e){errors.put(music,"小组件加载失败："+e.getClass().getSimpleName()+"\n点击连接重试，详情见诊断日志。");LauncherApp.record("小组件宿主不可用",e);return null;}
    }
    private AppWidgetHostView createView(boolean music) {
        String key=music?"musicWidget":"mapWidget"; int id=cfg.n(key,-1); if(id==-1) return null;
        AppWidgetProviderInfo info=AppWidgetManager.getInstance(activity).getAppWidgetInfo(id);
        // At boot the provider may not be ready yet. Keep its persistent grant/id.
        if(info==null){errors.put(music,"正在等待应用的小组件服务恢复");return null;}
        if(!info.provider.getPackageName().equals(cfg.pkg(music))) { host.deleteAppWidgetId(id); cfg.put(key,-1); errors.put(music,"已更换应用，请连接新应用的小组件。"); return null; }
        try {
            AppWidgetHostView v=host.createView(activity,id,info); v.setAppWidget(id,info);
            v.post(()->{
                if(v.getWidth()>0 && v.getHeight()>0) {
                    float d=activity.getResources().getDisplayMetrics().density;
                    int w=(int)(v.getWidth()/d),h=(int)(v.getHeight()/d);
                    try { v.updateAppWidgetSize(null,w,h,w,h); } catch(Exception e) { LauncherApp.record("更新小组件尺寸失败",e); }
                }
            }); errors.remove(music);return v;
        } catch(Exception e) { errors.put(music,"小组件创建失败："+e.getClass().getSimpleName()+"\n点击连接重试，详情见诊断日志。");LauncherApp.record("小组件创建失败",e); return null; }
    }
    void choose(boolean music) {
        try{chooseProvider(music);}catch(Exception e){LauncherApp.record("无法读取系统小组件能力",e);Ui.toast(activity,"系统小组件服务不可用："+e.getClass().getSimpleName());}
    }
    boolean waiting(){return errors.containsValue("正在等待应用的小组件服务恢复");}
    private void chooseProvider(boolean music) {
        List<AppWidgetProviderInfo> found=new ArrayList<>();
        for(AppWidgetProviderInfo i:AppWidgetManager.getInstance(activity).getInstalledProviders()) if(i.provider.getPackageName().equals(cfg.pkg(music))) found.add(i);
        if(found.isEmpty()) { Ui.toast(activity,"该应用未安装、被停用，或没有提供桌面小组件。请先打开原应用完成初始化。"); return; }
        if(found.size()==1) bind(music,found.get(0));
        else {
            String[] names=new String[found.size()]; for(int n=0;n<names.length;n++) names[n]=found.get(n).label+" · "+(n+1);
            new ThemeDialog.Builder(activity).setTitle("选择应用原生小组件").setItems(names,(d,n)->bind(music,found.get(n))).setNegativeButton("取消",null).show();
        }
    }
    private void bind(boolean music,AppWidgetProviderInfo info) {
        clearPending(); int id=host.allocateAppWidgetId();
        cfg.put("pendingWidget",id); cfg.put("pendingMusic",music);
        try {
            if(AppWidgetManager.getInstance(activity).bindAppWidgetIdIfAllowed(id,info.provider)) configure(id);
            else {
                Intent intent=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider);
                activity.startActivityForResult(intent,BIND);
            }
        } catch(Exception e) { clearPending(); LauncherApp.record("绑定小组件失败",e); Ui.toast(activity,"系统不允许绑定此小组件，请查看诊断"); }
    }
    private void configure(int id) {
        AppWidgetProviderInfo info=AppWidgetManager.getInstance(activity).getAppWidgetInfo(id);
        if(info==null) { clearPending(); Ui.toast(activity,"系统未授予小组件访问权"); return; }
        if(info.configure!=null) {
            try { activity.startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id),CONFIGURE); }
            catch(Exception e) { clearPending(); LauncherApp.record("打开小组件配置失败",e); Ui.toast(activity,"应用小组件配置页无法打开"); }
        } else commit(id);
    }
    private void commit(int id) {
        String key=cfg.b("pendingMusic",false)?"musicWidget":"mapWidget"; int old=cfg.n(key,-1);
        if(old!=-1 && old!=id) host.deleteAppWidgetId(old);
        cfg.put(key,id); cfg.put("pendingWidget",-1); LauncherApp.record("小组件绑定成功 "+key+" id="+id,null); activity.render();
    }
    void clearPending() { int id=cfg.n("pendingWidget",-1); if(id!=-1) host.deleteAppWidgetId(id); cfg.put("pendingWidget",-1); }
    boolean result(int request,int result) {
        if(request!=BIND && request!=CONFIGURE) return false;
        int id=cfg.n("pendingWidget",-1);
        if(result!=Activity.RESULT_OK || id==-1) { clearPending(); Ui.toast(activity,"小组件授权或配置未完成，可以重试"); }
        else if(request==BIND) configure(id); else commit(id);
        return true;
    }
    void remove(boolean music) { String key=music?"musicWidget":"mapWidget"; int id=cfg.n(key,-1); if(id!=-1) host.deleteAppWidgetId(id); cfg.put(key,-1); activity.render(); }
}
