package cn.hydrogen.launcher;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;

// Test fixture only. It is never shipped inside the launcher APK or passed off as QQ/Amap.
public class ProbeWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){
        int clicks=c.getSharedPreferences("probe",0).getInt("clicks",0);
        for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),cn.hydrogen.launcher.test.R.layout.probe_widget);v.setTextViewText(cn.hydrogen.launcher.test.R.id.probe_text,"Native widget probe · clicks="+clicks);
            Intent action=new Intent(c,ProbeWidget.class).setAction("cn.hydrogen.launcher.test.CLICK");
            v.setOnClickPendingIntent(cn.hydrogen.launcher.test.R.id.probe_button,PendingIntent.getBroadcast(c,id,action,PendingIntent.FLAG_UPDATE_CURRENT|67108864));manager.updateAppWidget(id,v);}
    }
    @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);if("cn.hydrogen.launcher.test.CLICK".equals(i.getAction())){c.getSharedPreferences("probe",0).edit().putInt("clicks",c.getSharedPreferences("probe",0).getInt("clicks",0)+1).commit();onUpdate(c,AppWidgetManager.getInstance(c),AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,ProbeWidget.class)));}}
}
