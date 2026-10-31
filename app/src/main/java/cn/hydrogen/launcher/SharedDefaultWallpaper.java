package cn.hydrogen.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;

/** Reuse live default images with identical pixel geometry; no Activity or strong bitmap cache. */
final class SharedDefaultWallpaper {
    private static final Map<Long,WeakReference<Bitmap>> images=new HashMap<>();
    @android.annotation.SuppressLint("ResourceType")
    static synchronized Bitmap decode(Context context,int width,int height)throws IOException{
        Iterator<WeakReference<Bitmap>> it=images.values().iterator();
        while(it.hasNext()){Bitmap image=it.next().get();if(image==null||image.isRecycled())it.remove();}
        long key=((long)width<<32)|(height&0xffffffffL);WeakReference<Bitmap> reference=images.get(key);Bitmap image=reference==null?null:reference.get();
        if(image==null||image.isRecycled()){
            Context app=context.getApplicationContext();
            image=DisplayBitmap.decode(()->app.getResources().openRawResource(R.drawable.jade_flow),width,height,0);
            images.put(key,new WeakReference<>(image));
        }
        return image;
    }
    private SharedDefaultWallpaper(){}
}
