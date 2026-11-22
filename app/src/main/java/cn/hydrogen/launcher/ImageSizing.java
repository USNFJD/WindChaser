package cn.hydrogen.launcher;

/** Pixel geometry only; no density-based resource scaling or View references. */
final class ImageSizing {
    static final class Plan {
        final int left,top,width,height,outWidth,outHeight,sample;
        Plan(int l,int t,int w,int h,int ow,int oh,int s){left=l;top=t;width=w;height=h;outWidth=ow;outHeight=oh;sample=s;}
    }
    static Plan plan(int sw,int sh,int tw,int th,int fit){
        if(sw<1||sh<1||tw<1||th<1)throw new IllegalArgumentException("Invalid image dimensions");
        int w=sw,h=sh;
        if(fit==0){if((long)sw*th>(long)sh*tw)w=Math.max(1,(int)Math.round((double)sh*tw/th));else h=Math.max(1,(int)Math.round((double)sw*th/tw));}
        else if(fit==2){w=Math.min(sw,tw);h=Math.min(sh,th);}
        int ow,oh;
        if(fit==0){ow=Math.min(w,tw);oh=Math.min(h,th);}
        else {double scale=Math.min(1,Math.min((double)tw/w,(double)th/h));ow=Math.max(1,(int)Math.round(w*scale));oh=Math.max(1,(int)Math.round(h*scale));}
        int sample=1;while(w/(sample*2)>=ow&&h/(sample*2)>=oh&&sample<=1<<28)sample*=2;
        return new Plan((sw-w)/2,(sh-h)/2,w,h,ow,oh,sample);
    }
    private ImageSizing(){}
}
