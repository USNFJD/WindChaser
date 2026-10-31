package cn.hydrogen.launcher;

/** All desktop geometry uses the measured content area, including reserved system bars. */
final class ScreenLayout {
    final int width,height;final float unit;
    ScreenLayout(int width,int height){this.width=Math.max(1,width);this.height=Math.max(1,height);unit=Math.min(this.width/1280f,this.height/720f);}
    int gridColumns(){return Math.max(4,Math.min(10,(int)(width/unit/200)));}
}
