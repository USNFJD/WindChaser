package cn.hydrogen.launcher;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

/** Read-only per-file URI grants for the Android Open With chooser. */
public class LocalFileProvider extends ContentProvider {
    static Uri uri(Context c,File file)throws IOException{return new Uri.Builder().scheme("content").authority(c.getPackageName()+".files").appendPath("storage").appendPath(file.getCanonicalPath()).build();}
    private File file(Uri uri)throws FileNotFoundException{
        try{List<String> parts=uri.getPathSegments();if(parts.size()!=2||!"storage".equals(parts.get(0)))throw new IOException("无效路径");File candidate=new File(parts.get(1));File file=StorageFiles.checked(StorageFiles.rootFor(getContext(),candidate),candidate);if(!file.isFile())throw new IOException("文件不存在");return file;}catch(Exception error){throw new FileNotFoundException(error.getMessage());}
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){try{return StorageFiles.mime(file(uri));}catch(Exception e){return "application/octet-stream";}}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("仅允许读取");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File f=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor out=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int n=0;n<columns.length;n++)values[n]=OpenableColumns.DISPLAY_NAME.equals(columns[n])?f.getName():OpenableColumns.SIZE.equals(columns[n])?f.length():null;out.addRow(values);return out;}catch(Exception e){return null;}}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String s,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] args){throw new UnsupportedOperationException();}
}
