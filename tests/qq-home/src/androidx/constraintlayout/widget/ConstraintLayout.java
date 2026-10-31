package androidx.constraintlayout.widget;
public class ConstraintLayout {
 /** Exact verified public int aliases from paired QQ 2.1.1.1. */
 public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
  public int t=-1,s=-1,i=-1,l=-1,u=-1,v=-1;
  public LayoutParams(int w,int h){super(w,h);}
  public LayoutParams(LayoutParams old){super(old.width,old.height);t=old.t;s=old.s;i=old.i;l=old.l;u=old.u;v=old.v;leftMargin=old.leftMargin;rightMargin=old.rightMargin;topMargin=old.topMargin;bottomMargin=old.bottomMargin;}
 }
}
