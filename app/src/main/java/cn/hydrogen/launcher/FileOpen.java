package cn.hydrogen.launcher;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import java.io.*;

final class FileOpen {
    static boolean apk(File f){return "application/vnd.android.package-archive".equals(StorageFiles.mime(f));}
    static Intent intent(Activity a,File f)throws IOException{
        if(!f.isFile()||!f.canRead())throw new IOException("文件不存在或无读取权限");
        // KitKat installers from several car ROMs accept only a world-readable file URI.
        boolean legacyApk=apk(f)&&Build.VERSION.SDK_INT<24;
        if(legacyApk)f.setReadable(true,false);
        Uri uri=legacyApk?Uri.fromFile(f):LocalFileProvider.uri(a,f);
        Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,StorageFiles.mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(!legacyApk){i.setClipData(ClipData.newRawUri(f.getName(),uri));
            for(ResolveInfo r:a.getPackageManager().queryIntentActivities(i,PackageManager.MATCH_DEFAULT_ONLY))a.grantUriPermission(r.activityInfo.packageName,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return i;
    }
    static void open(Activity a,File f,boolean chooser){try{
        Intent i=intent(a,f);
        if(apk(f)&&!chooser)a.startActivity(i);
        else{Intent select=Intent.createChooser(i,"选择应用打开");select.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);select.setClipData(i.getClipData());a.startActivity(select);}
    }catch(ActivityNotFoundException e){Ui.toast(a,apk(f)?"车机未提供 APK 安装器":"没有可打开此格式的应用，请长按选择其他应用");}
     catch(Exception e){LauncherApp.record("打开文件失败 "+f,e);Ui.toast(a,"打开失败："+e.getMessage());}}
}
