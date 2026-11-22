/** Test-only JNI event substitute. NEVER added to the patched commercial APK. */
public final class vq {
    private static final vq INSTANCE=new vq();
    public static int starts,resumes,pauses,stops,focuses;
    public static vq i(){return INSTANCE;}
    public void e(){starts++;}
    public void d(){resumes++;com.autonavi.amapauto.MainMapActivity.nativeResumed=true;}
    public void b(boolean finishing){pauses++;com.autonavi.amapauto.MainMapActivity.nativeResumed=false;}
    public void c(boolean finishing){stops++;com.autonavi.amapauto.MainMapActivity.nativeResumed=false;}
    public void e(boolean focus){focuses++;}
    public static int uiWidth,uiHeight,uiSyncs;public void a(android.view.Surface surface,int format,int width,int height){uiWidth=width;uiHeight=height;uiSyncs++;}
}
