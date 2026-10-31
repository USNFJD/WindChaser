package cn.hydrogen.launcher;

import android.content.Context;
import android.os.Environment;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.text.Collator;
import java.util.*;

/** Shared-storage operations. Every recursive step remains inside the selected storage root. */
final class StorageFiles {
    static File root(Context c){File root=Environment.getExternalStorageDirectory();return android.os.Build.VERSION.SDK_INT>=33?c.getExternalFilesDir(null):root;}
    static List<File> roots(Context c){
        ArrayList<File> out=new ArrayList<>();addRoot(out,root(c));if(android.os.Build.VERSION.SDK_INT>=33)return out;
        String secondary=System.getenv("SECONDARY_STORAGE");if(secondary!=null)for(String path:secondary.split(File.pathSeparator))addRoot(out,new File(path));
        if(android.os.Build.VERSION.SDK_INT>=19)for(File app:c.getExternalFilesDirs(null))if(app!=null){String path=app.getAbsolutePath();int android=path.indexOf("/Android/");if(android>0)addRoot(out,new File(path.substring(0,android)));}
        File[] storage=new File("/storage").listFiles();if(storage!=null)for(File file:storage)if(!file.getName().equals("emulated")&&!file.getName().equals("self"))addRoot(out,file);
        File[] mounts=new File("/mnt").listFiles();if(mounts!=null)for(File file:mounts){String name=file.getName().toLowerCase(Locale.US);if(name.contains("sd")||name.contains("usb")||name.contains("udisk")||name.contains("ext"))addRoot(out,file);}
        return out;
    }
    private static void addRoot(List<File> roots,File file){
        try{if(file==null||!file.isDirectory()||!file.canRead())return;File canonical=file.getCanonicalFile();if(canonical.getPath().equals("/"))return;for(File root:roots)if(within(root,canonical)||within(canonical,root))return;roots.add(canonical);}catch(IOException|SecurityException ignored){}
    }
    static File rootFor(Context c,File file)throws IOException{for(File root:roots(c))if(within(root,file))return root;throw new IOException("文件不在可访问的共享存储中");}
    static File transfer(Context c)throws IOException{
        File parent=android.os.Build.VERSION.SDK_INT>=33?c.getExternalFilesDir(null):Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if(parent==null)throw new IOException("存储尚未就绪");File folder=new File(parent,"WindChaserTransfer");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建接收目录，请允许存储权限");return folder.getCanonicalFile();
    }
    static boolean within(File root,File file)throws IOException{String r=root.getCanonicalPath(),f=file.getCanonicalPath();return f.equals(r)||f.startsWith(r+File.separator);}
    static File checked(File root,File file)throws IOException{if(!within(root,file))throw new IOException("路径不在可访问的存储目录内");return file.getCanonicalFile();}
    static File[] list(File root,File folder)throws IOException{
        checked(root,folder);File[] children=folder.listFiles();if(children==null)throw new IOException("无法读取这个目录");
        final Collator names=Collator.getInstance(Locale.CHINA);Arrays.sort(children,(a,b)->a.isDirectory()!=b.isDirectory()?(a.isDirectory()?-1:1):names.compare(a.getName(),b.getName()));return children;
    }
    static String mime(File file){String name=file.getName();int p=name.lastIndexOf('.');String type=p<0?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(p+1).toLowerCase(Locale.US));return type==null?"application/octet-stream":type;}
    static String size(long bytes){if(bytes<1024)return bytes+" B";if(bytes<1024*1024)return String.format(Locale.US,"%.1f KB",bytes/1024f);if(bytes<1024L*1024*1024)return String.format(Locale.US,"%.1f MB",bytes/1048576f);return String.format(Locale.US,"%.2f GB",bytes/1073741824f);}
    static File unique(File folder,String name)throws IOException{
        if(name.isEmpty()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0)throw new IOException("文件名不正确");
        File target=new File(folder,name);String base=name,ext="";int dot=name.lastIndexOf('.');if(dot>0){base=name.substring(0,dot);ext=name.substring(dot);}
        for(int n=1;target.exists()&&n<10000;n++)target=new File(folder,base+" ("+n+")"+ext);
        if(target.exists())throw new IOException("重名文件过多");return target;
    }
    static File rename(File root,File source,String name)throws IOException{
        source=checked(root,source);if(source.equals(root.getCanonicalFile()))throw new IOException("不能重命名存储根目录");
        if(!source.exists())throw new IOException("文件已不存在");
        if(name.trim().isEmpty()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf('\0')>=0||name.indexOf('\n')>=0||name.indexOf('\r')>=0||name.startsWith(".upload-"))throw new IOException("文件名不正确");
        File target=checked(root,new File(source.getParentFile(),name));
        if(target.equals(source))return source;
        if(target.exists())throw new IOException("同名文件已存在，请换一个名称");
        if(!source.renameTo(target))throw new IOException("无法重命名，请检查存储权限和文件是否被占用");return target;
    }
    static File paste(File root,File source,File folder,boolean cut)throws IOException{
        return paste(root,source,root,folder,cut);
    }
    static File paste(File sourceRoot,File source,File targetRoot,File folder,boolean cut)throws IOException{
        source=checked(sourceRoot,source);folder=checked(targetRoot,folder);if(!source.exists()||!folder.isDirectory())throw new IOException("文件或目标目录已不存在");
        if(source.equals(sourceRoot.getCanonicalFile()))throw new IOException("不能操作存储根目录");
        if(source.isDirectory()&&within(source,folder))throw new IOException("不能把文件夹放入它自己或其子目录");
        if(cut&&source.getParentFile().equals(folder))return source;
        File target=unique(folder,source.getName());if(cut&&source.renameTo(target))return target;
        try{copy(sourceRoot,targetRoot,source,target,0);}catch(IOException error){try{delete(targetRoot,target,0);}catch(IOException ignored){}throw error;}
        if(cut)delete(sourceRoot,source,0);return target;
    }
    private static void copy(File sourceRoot,File targetRoot,File source,File target,int depth)throws IOException{
        checked(sourceRoot,source);checked(targetRoot,target);if(depth>48)throw new IOException("文件夹层级太深");
        if(source.isDirectory()){
            if(!target.mkdir())throw new IOException("无法创建 "+target.getName());
            for(File child:list(sourceRoot,source))copy(sourceRoot,targetRoot,child,new File(target,child.getName()),depth+1);
        }else{
            try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(target)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("操作已取消");out.write(bytes,0,n);}out.getFD().sync();}
        }
    }
    static void delete(File root,File file,int depth)throws IOException{
        File resolved=checked(root,file);if(resolved.equals(root.getCanonicalFile()))throw new IOException("不能删除存储根目录");if(depth>48)throw new IOException("文件夹层级太深");
        // Do not traverse a symbolic directory through a differently named entry.
        if(file.isDirectory()){File entry=new File(file.getParentFile().getCanonicalFile(),file.getName());if(!entry.equals(resolved))throw new IOException("不递归删除链接目录");for(File child:list(root,file))delete(root,child,depth+1);}
        if(file.exists()&&!file.delete())throw new IOException("无法删除 "+file.getName());
    }
}
