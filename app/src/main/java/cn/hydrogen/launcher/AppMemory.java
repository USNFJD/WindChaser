package cn.hydrogen.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Process;
import java.util.*;

/** On-demand, same-UID PSS sample. No resident worker or periodic sampling. */
final class AppMemory {
    static final class Item {
        final int pid,kb;final String name;
        Item(int pid,String name,int kb){this.pid=pid;this.name=name;this.kb=kb;}
    }
    final List<Item> items;final long kb;final boolean partial;
    private AppMemory(List<Item> values,boolean missing){items=Collections.unmodifiableList(values);long sum=0;for(Item item:values)sum+=item.kb;kb=sum;partial=missing;}
    static AppMemory capture(Context context){
        ArrayList<Item> values=new ArrayList<>();boolean missing=false;
        try{
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> running=manager.getRunningAppProcesses();
            TreeMap<Integer,String> own=new TreeMap<>();
            if(running!=null)for(ActivityManager.RunningAppProcessInfo p:running)if(p.uid==Process.myUid())own.put(p.pid,p.processName);
            if(!own.containsKey(Process.myPid()))own.put(Process.myPid(),context.getPackageName());
            int[] ids=new int[own.size()];int index=0;for(int id:own.keySet())ids[index++]=id;
            Debug.MemoryInfo[] result=manager.getProcessMemoryInfo(ids);
            for(index=0;index<ids.length;index++){
                int amount=result!=null&&index<result.length&&result[index]!=null?result[index].getTotalPss():0;
                if(amount>0)values.add(new Item(ids[index],own.get(ids[index]),amount));else missing=true;
            }
        }catch(RuntimeException unavailable){missing=true;}
        return new AppMemory(values,missing);
    }
    String total(){return items.isEmpty()?"系统未提供":String.format(Locale.CHINA,"%.1f MB%s",kb/1024d,partial?"（部分进程）":"");}
}
