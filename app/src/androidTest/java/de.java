import android.view.View;
/** Test-only substitute for the original JNIContext layout listener. */
public final class de {
    private static final de INSTANCE=new de();public static int lastWidth,lastHeight;
    public final View.OnLayoutChangeListener B=(v,l,t,r,b,ol,ot,or,ob)->{lastWidth=r-l;lastHeight=b-t;};
    public static de A(){return INSTANCE;}
    public void a(View view){view.addOnLayoutChangeListener(B);}
    public void b(View view){view.removeOnLayoutChangeListener(B);}
}
