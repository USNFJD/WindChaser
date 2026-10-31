package cn.hydrogen.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.*;
import java.io.*;
import java.util.*;

/** Read-only hardware information; missing kernel files are never treated as zero. */
final class DeviceInfo {
    static final String UNKNOWN="系统未公开";
    static String read(File file){
        try(Reader in=new InputStreamReader(new FileInputStream(file),"UTF-8")){
            StringBuilder out=new StringBuilder();char[] buffer=new char[2048];int n;
            while(out.length()<65536&&(n=in.read(buffer,0,Math.min(buffer.length,65536-out.length())))>0)out.append(buffer,0,n);
            return out.toString().trim();
        }catch(IOException|SecurityException unavailable){return "";}
    }
    static String known(String value){return value==null||value.trim().isEmpty()||value.equalsIgnoreCase("unknown")?UNKNOWN:value.trim();}
    static String field(String cpu,String... keys){
        for(String key:keys)for(String line:cpu.split("\n")){int colon=line.indexOf(':');if(colon>0&&line.substring(0,colon).trim().equalsIgnoreCase(key)){String value=line.substring(colon+1).trim();if(!value.isEmpty())return value;}}
        return "";
    }
    static Set<Integer> cpuIds(String list){
        Set<Integer> ids=new TreeSet<>();
        try{for(String part:list.trim().split(",")){String[] range=part.trim().split("-",-1);if(range.length>2)return Collections.emptySet();int first=Integer.parseInt(range[0]);int last=range.length==1?first:Integer.parseInt(range[1]);if(first<0||last<first||last>4095)return Collections.emptySet();for(int i=first;i<=last;i++)ids.add(i);}}
        catch(NumberFormatException invalid){return Collections.emptySet();}
        return ids;
    }
    static long positive(String text){try{long value=Long.parseLong(text.trim());return value>0?value:0;}catch(NumberFormatException invalid){return 0;}}
    static final class Cpu {int cores;boolean availableOnly,frequencyCap;long maxKhz;String name,hardware;}
    static Cpu cpu(File directory,String proc){
        Cpu result=new Cpu();Set<Integer> ids=cpuIds(read(new File(directory,"present")));
        if(ids.isEmpty())ids=cpuIds(read(new File(directory,"possible")));
        ids=new TreeSet<>(ids);
        File[] files;try{files=directory.listFiles();}catch(SecurityException denied){files=null;}
        if(files!=null)for(File file:files)if(file.getName().matches("cpu[0-9]+"))try{int id=Integer.parseInt(file.getName().substring(3));if(id<4096)ids.add(id);}catch(NumberFormatException ignored){}
        if(ids.isEmpty())for(String line:proc.split("\n"))if(line.matches("(?i)^processor\\s*:\\s*[0-9]+\\s*$"))try{int id=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());if(id<4096)ids.add(id);}catch(NumberFormatException ignored){}
        result.cores=ids.size();if(result.cores==0){result.cores=Runtime.getRuntime().availableProcessors();result.availableOnly=true;}
        long cap=0;
        for(int id:ids){File freq=new File(directory,"cpu"+id+"/cpufreq");result.maxKhz=Math.max(result.maxKhz,positive(read(new File(freq,"cpuinfo_max_freq"))));cap=Math.max(cap,positive(read(new File(freq,"scaling_max_freq"))));}
        File[] policies;try{policies=new File(directory,"cpufreq").listFiles();}catch(SecurityException denied){policies=null;}
        if(policies!=null)for(File policy:policies)if(policy.getName().matches("policy[0-9]+")){result.maxKhz=Math.max(result.maxKhz,positive(read(new File(policy,"cpuinfo_max_freq"))));cap=Math.max(cap,positive(read(new File(policy,"scaling_max_freq"))));}
        if(result.maxKhz==0&&cap>0){result.maxKhz=cap;result.frequencyCap=true;}
        result.hardware=known(field(proc,"Hardware","system type"));
        String soc=Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:"";
        String name=known(soc).equals(UNKNOWN)?field(proc,"Hardware","model name","system type","Processor"):soc;
        result.name=known(name);return result;
    }
    static Cpu cpu(){return cpu(new File("/sys/devices/system/cpu"),read(new File("/proc/cpuinfo")));}
    static String frequency(long khz){return khz<=0?UNKNOWN:khz>=1000000?String.format(Locale.CHINA,"%.2f GHz",khz/1000000d):String.format(Locale.CHINA,"%.0f MHz",khz/1000d);}
    static ActivityManager.MemoryInfo memory(Context context){ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);return memory;}
    static String capacity(long bytes){if(bytes<0)return UNKNOWN;return bytes>=1073741824L?String.format(Locale.CHINA,"%.2f GB",bytes/1073741824d):String.format(Locale.CHINA,"%.0f MB",bytes/1048576d);}
    static String storage(File path){
        try{StatFs s=new StatFs(path.getAbsolutePath());long block=Build.VERSION.SDK_INT>=18?s.getBlockSizeLong():s.getBlockSize();long total=Build.VERSION.SDK_INT>=18?s.getBlockCountLong():s.getBlockCount();long free=Build.VERSION.SDK_INT>=18?s.getAvailableBlocksLong():s.getAvailableBlocks();return capacity(block*free)+" 可用 / "+capacity(block*total)+" 总计";}
        catch(IllegalArgumentException|SecurityException denied){return UNKNOWN;}
    }
    static String abi(){return Build.VERSION.SDK_INT>=21?android.text.TextUtils.join(" / ",Build.SUPPORTED_ABIS):Build.CPU_ABI+(Build.CPU_ABI2.isEmpty()||Build.CPU_ABI2.equals("unknown")?"":" / "+Build.CPU_ABI2);}
    static final class Snapshot {
        final Cpu cpu=DeviceInfo.cpu();
        final String kernel=known(read(new File("/proc/sys/kernel/osrelease")));
        final String internal=storage(Environment.getDataDirectory());
        final String shared=Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())||Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())?storage(Environment.getExternalStorageDirectory()):"未挂载";
    }
}
