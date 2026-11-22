package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Settings selection/persistence only; this does not emulate a vendor Bluetooth audio route. */
final class BluetoothAssociationFlow extends RevisionFlow {
    private SettingsActivity settings;
    BluetoothAssociationFlow(Instrumentation test){super(test);report.setLength(0);report.append("API19 Bluetooth app association; no vendor audio routing claimed.\n");}
    private View text(View root,String prefix){
        if(root instanceof TextView&&((TextView)root).getText().toString().startsWith(prefix))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=text(((ViewGroup)root).getChildAt(i),prefix);if(found!=null)return found;}
        return null;
    }
    private View window(String title){try{
        Class<?> type=Class.forName("android.view.WindowManagerGlobal");Object manager=type.getDeclaredMethod("getInstance").invoke(null);
        java.lang.reflect.Field field=type.getDeclaredField("mViews");field.setAccessible(true);
        for(Object view:(List<?>)field.get(manager))if(list((View)view)!=null&&text((View)view,title)!=null)return (View)view;
        throw new AssertionError("missing dialog: "+title);
    }catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private ListView list(View root){if(root instanceof ListView)return (ListView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){ListView found=list(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}return null;}
    private void item(String title,int index){main(()->{ListView list=list(window(title));require(list!=null,"missing dialog list");list.performItemClick(null,index,list.getAdapter().getItemId(index));});}
    private void choose(String className){
        List<ResolveInfo> apps=Ui.apps(test.getTargetContext());int target=-1;
        for(int i=0;i<apps.size();i++)if(className.equals(apps.get(i).activityInfo.name)){target=i;break;}
        require(target>=0,"fixture app missing");item("选择已安装应用",target);
    }
    private void row(){main(()->{View row=text(settings.getWindow().getDecorView(),"蓝牙音乐应用 · ");require(row!=null,"association entry missing");row.performClick();});}
    private void open(){settings=(SettingsActivity)test.startActivitySync(new Intent(test.getTargetContext(),SettingsActivity.class).putExtra("category",7).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    @Override void run(){Config cfg=new Config(test.getTargetContext());Map<String,?> saved=new HashMap<>(cfg.p.getAll());String music=cfg.pkg(true);
        try{
            cfg.put("autoRecoverApps",false);cfg.put("keepCompanions",false);cfg.put(Config.BLUETOOTH_MUSIC_COMPONENT,"");open();
            check("selecting Bluetooth app saves its launch component without extra buttons or music changes",()->{
                main(()->{String text=allText(settings.getWindow().getDecorView());require(text.contains("蓝牙音乐应用 · 未关联")&&!text.contains("导出原车蓝牙")&&!text.contains("直接打开"),"unexpected association UI");});
                row();choose(FileCenterActivity.class.getName());
                require(cfg.s(Config.BLUETOOTH_MUSIC_COMPONENT,"").equals(new ComponentName(test.getTargetContext(),FileCenterActivity.class).flattenToString()),"selection not saved");require(music.equals(cfg.pkg(true)),"QQ association changed");
            });
            check("association survives settings recreation and configuration backup",()->{
                String backup=cfg.exportJson();main(()->settings.finish());open();
                main(()->require(text(settings.getWindow().getDecorView(),"蓝牙音乐应用 · 未关联")==null,"selection lost on reopen"));
                cfg.put(Config.BLUETOOTH_MUSIC_COMPONENT,"");cfg.restore(backup);require(!cfg.s(Config.BLUETOOTH_MUSIC_COMPONENT,"").isEmpty(),"backup lost association");
                row();item("蓝牙音乐应用",0);choose(DeviceInfoActivity.class.getName());
                require(cfg.s(Config.BLUETOOTH_MUSIC_COMPONENT,"").equals(new ComponentName(test.getTargetContext(),DeviceInfoActivity.class).flattenToString()),"replacement not saved");
            });
            check("uninstalled app is recoverable and clearing association persists",()->{
                cfg.put(Config.BLUETOOTH_MUSIC_COMPONENT,"missing.bluetooth.app/.Music");main(()->settings.finish());open();
                main(()->require(allText(settings.getWindow().getDecorView()).contains("应用已卸载，请重新选择"),"missing app status absent"));
                row();item("蓝牙音乐应用",1);require(cfg.s(Config.BLUETOOTH_MUSIC_COMPONENT,"not-cleared").isEmpty(),"clear failed");
                cfg.restore(cfg.exportJson());require(cfg.s(Config.BLUETOOTH_MUSIC_COMPONENT,"not-cleared").isEmpty(),"clear not backed up");
                String before=cfg.exportJson();boolean rejected=false;try{cfg.restore("{\"format\":\"hydrogen-config\",\"version\":1,\"settings\":{\"bluetoothMusicComponent\":7}}");}catch(org.json.JSONException expected){rejected=true;}
                require(rejected&&before.equals(cfg.exportJson()),"invalid backup partially changed preferences");
            });
        }catch(Throwable e){failed++;report.append("FAIL setup ").append(e).append('\n');}
        finally{if(settings!=null)main(()->settings.finish());restore(cfg,saved);}
        report.append("BLUETOOTH_ASSOCIATION_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
