package cn.hydrogen.launcher;

import android.app.Activity;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public class ExportProvider extends ContentProvider {
    public static void share(Activity a,String name,String content,String title)throws IOException{
        File dir=new File(a.getFilesDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建导出目录");
        try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(dir,name)),"UTF-8")){w.write(content);}
        Uri uri=Uri.parse("content://cn.windchaser.unified.exports/"+name);
        Intent i=new Intent(Intent.ACTION_SEND).setType(name.endsWith(".json")?"application/json":"text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri(title,uri));
        Ui.open(a,Intent.createChooser(i,title));
    }
    private File file(Uri u)throws FileNotFoundException{
        String n=u.getLastPathSegment();
        if(!"config.json".equals(n)&&!"diagnostics.txt".equals(n)&&!ContainerRuntime.APK_NAME.equals(n))throw new FileNotFoundException("不允许访问此文件");
        return new File(new File(getContext().getFilesDir(),"exports"),n);
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return ContainerRuntime.APK_NAME.equals(uri.getLastPathSegment())?"application/vnd.android.package-archive":"config.json".equals(uri.getLastPathSegment())?"application/json":"text/plain";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File f=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int n=0;n<columns.length;n++)row[n]=OpenableColumns.DISPLAY_NAME.equals(columns[n])?f.getName():OpenableColumns.SIZE.equals(columns[n])?f.length():null;c.addRow(row);return c;}catch(Exception e){return null;}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("只允许读取导出文件");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
