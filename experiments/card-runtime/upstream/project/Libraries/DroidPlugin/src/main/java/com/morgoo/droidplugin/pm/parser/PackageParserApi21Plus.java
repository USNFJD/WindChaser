package com.morgoo.droidplugin.pm.parser;

import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.*;
import android.os.Bundle;
import android.os.Parcel;
import android.util.LruCache;
import com.morgoo.droidplugin.reflect.FieldUtils;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/** API21+: retain platform manifest parsing and public archive certificate verification.
 * Does not emulate the removed PackageUserState or replace certificate validation. */
final class PackageParserApi21Plus extends PackageParser {
    private Object parsed;
    private File apk;
    private Signature[] signatures;
    // Cache immutable parcel templates, never PackageInfo objects later modified
    // by PluginPackageParser. Bound by flag combinations and replaced on import.
    private final LruCache<Integer, byte[]> archiveInfo = new LruCache<Integer, byte[]>(16);
    private static byte[] snapshot(PackageInfo info) {
        Parcel parcel=Parcel.obtain();
        try { info.writeToParcel(parcel,0);return parcel.marshall(); }
        finally { parcel.recycle(); }
    }
    private static PackageInfo restore(byte[] data) {
        Parcel parcel=Parcel.obtain();
        try { parcel.unmarshall(data,0,data.length);parcel.setDataPosition(0);return PackageInfo.CREATOR.createFromParcel(parcel); }
        finally { parcel.recycle(); }
    }
    PackageParserApi21Plus(Context context) { super(context); }
    public void parsePackage(File file, int flags) throws Exception {
        apk=file;signatures=null;archiveInfo.evictAll();
        Class<?> parser=Class.forName("android.content.pm.PackageParser");
        mPackageParser=parser.getDeclaredConstructor().newInstance();
        Method parse=parser.getDeclaredMethod("parsePackage",File.class,int.class);
        parse.setAccessible(true);parsed=parse.invoke(mPackageParser,file,0);
        collectCertificates(0);
    }
    public void collectCertificates(int flags) throws Exception {
        PackageInfo info=mContext.getPackageManager().getPackageArchiveInfo(apk.getPath(),PackageManager.GET_SIGNATURES);
        if(info==null || info.signatures==null || info.signatures.length==0) throw new SecurityException("Archive has no verified signing certificate");
        signatures=info.signatures;archiveInfo.put(PackageManager.GET_SIGNATURES,snapshot(info));
    }
    private Object info(Object component) throws Exception {
        Object value=FieldUtils.readField(component,"info",true);
        if(value instanceof PackageItemInfo) {
            try { ((PackageItemInfo)value).metaData=(Bundle)FieldUtils.readField(component,"metaData",true); } catch(Exception ignored){}
        }
        return value;
    }
    public ActivityInfo generateActivityInfo(Object component,int flags)throws Exception{return new ActivityInfo((ActivityInfo)info(component));}
    public ServiceInfo generateServiceInfo(Object component,int flags)throws Exception{return new ServiceInfo((ServiceInfo)info(component));}
    public ProviderInfo generateProviderInfo(Object component,int flags)throws Exception{return new ProviderInfo((ProviderInfo)info(component));}
    public InstrumentationInfo generateInstrumentationInfo(Object component,int flags)throws Exception{return new InstrumentationInfo((InstrumentationInfo)info(component));}
    public PermissionInfo generatePermissionInfo(Object component,int flags)throws Exception{return new PermissionInfo((PermissionInfo)info(component));}
    public PermissionGroupInfo generatePermissionGroupInfo(Object component,int flags)throws Exception{return new PermissionGroupInfo((PermissionGroupInfo)info(component));}
    public ApplicationInfo generateApplicationInfo(int flags)throws Exception{
        ApplicationInfo app=new ApplicationInfo((ApplicationInfo)FieldUtils.readField(parsed,"applicationInfo",true));
        try{app.metaData=(Bundle)FieldUtils.readField(parsed,"mAppMetaData",true);}catch(Exception ignored){}
        return app;
    }
    public synchronized PackageInfo generatePackageInfo(int[] gids,int flags,long first,long last,HashSet<String> granted)throws Exception{
        byte[] template=archiveInfo.get(flags);
        if(template==null) {
            PackageInfo parsedInfo=mContext.getPackageManager().getPackageArchiveInfo(apk.getPath(),flags);
            if(parsedInfo==null)throw new IllegalStateException("Archive parse failed: "+apk);
            template=snapshot(parsedInfo);archiveInfo.put(flags,template);
        }
        PackageInfo value=restore(template);
        value.gids=gids;value.firstInstallTime=first;value.lastUpdateTime=last;
        if((flags&PackageManager.GET_SIGNATURES)!=0)value.signatures=signatures==null?null:signatures.clone();
        return value;
    }
    private List list(String name)throws Exception{return (List)FieldUtils.readField(parsed,name,true);}
    public List getActivities()throws Exception{return list("activities");}
    public List getServices()throws Exception{return list("services");}
    public List getProviders()throws Exception{return list("providers");}
    public List getPermissions()throws Exception{return list("permissions");}
    public List getPermissionGroups()throws Exception{return list("permissionGroups");}
    public List getRequestedPermissions()throws Exception{return list("requestedPermissions");}
    public List getReceivers()throws Exception{return list("receivers");}
    public List getInstrumentations()throws Exception{return list("instrumentation");}
    public String getPackageName()throws Exception{return (String)FieldUtils.readField(parsed,"packageName",true);}
    public String readNameFromComponent(Object value)throws Exception{return (String)FieldUtils.readField(value,"className",true);}
    public List<IntentFilter> readIntentFilterFromComponent(Object value)throws Exception{return (List<IntentFilter>)FieldUtils.readField(value,"intents",true);}
    public void writeSignature(Signature[] value)throws Exception{signatures=value;}
}
