package cn.hydrogen.launcher;

import android.graphics.*;
import java.io.*;

/** Decode only the visible region, then keep a bitmap no larger than its measured consumer. */
final class DisplayBitmap {
    interface Source { InputStream open() throws IOException; }
    static Bitmap decode(Source source,int width,int height,int fit)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;bounds.inScaled=false;
        try(InputStream in=source.open()){BitmapFactory.decodeStream(in,null,bounds);}
        ImageSizing.Plan plan=ImageSizing.plan(bounds.outWidth,bounds.outHeight,width,height,fit);
        BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inPreferredConfig=Bitmap.Config.RGB_565;options.inSampleSize=plan.sample;
        Bitmap decoded=null;
        boolean cropped=plan.width!=bounds.outWidth||plan.height!=bounds.outHeight;
        if(cropped){
            BitmapRegionDecoder region=null;
            try(InputStream in=source.open()){
                region=BitmapRegionDecoder.newInstance(in,false);
                if(region!=null)decoded=region.decodeRegion(new Rect(plan.left,plan.top,plan.left+plan.width,plan.top+plan.height),options);
            }catch(IOException|IllegalArgumentException unsupported){}finally{if(region!=null)region.recycle();}
        }
        if(decoded==null){
            // Bound fallback allocations even for a very wide unsupported image format.
            if(cropped)while(bounds.outWidth/options.inSampleSize>(long)width*2||bounds.outHeight/options.inSampleSize>(long)height*2)options.inSampleSize*=2;
            try(InputStream in=source.open()){decoded=BitmapFactory.decodeStream(in,null,options);}
            if(decoded==null)throw new IOException("Image cannot be decoded");
            if(cropped){
                int l=Math.max(0,Math.min(decoded.getWidth()-1,(int)((long)plan.left*decoded.getWidth()/bounds.outWidth)));
                int t=Math.max(0,Math.min(decoded.getHeight()-1,(int)((long)plan.top*decoded.getHeight()/bounds.outHeight)));
                int w=Math.max(1,Math.min(decoded.getWidth()-l,(int)((long)plan.width*decoded.getWidth()/bounds.outWidth)));
                int h=Math.max(1,Math.min(decoded.getHeight()-t,(int)((long)plan.height*decoded.getHeight()/bounds.outHeight)));
                Bitmap region=Bitmap.createBitmap(decoded,l,t,w,h);if(region!=decoded)decoded.recycle();decoded=region;
            }
        }
        Bitmap result;
        try{result=Bitmap.createScaledBitmap(decoded,plan.outWidth,plan.outHeight,true);}catch(RuntimeException|OutOfMemoryError failure){decoded.recycle();throw failure;}
        if(result!=decoded)decoded.recycle();return result;
    }
    private DisplayBitmap(){}
}
