package android.view;import java.util.*;import android.content.*;
public class ViewGroup extends View {public List<View> children=new ArrayList<>();public ViewGroup(Context c){super(c);}
 public static class LayoutParams {public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}
 public static class MarginLayoutParams extends LayoutParams {public int leftMargin,rightMargin,topMargin,bottomMargin,startMargin,endMargin;public MarginLayoutParams(int w,int h){super(w,h);}public void setMarginStart(int v){startMargin=v;}public void setMarginEnd(int v){endMargin=v;}}
 public void addView(View v,LayoutParams p){children.add(v);v.parent=this;v.lp=p;}public void removeView(View v){children.remove(v);v.parent=null;}
 public View findViewWithTag(Object t){View own=super.findViewWithTag(t);if(own!=null)return own;for(View v:children){View f=v.findViewWithTag(t);if(f!=null)return f;}return null;}
 public View findViewById(int i){View own=super.findViewById(i);if(own!=null)return own;for(View v:children){View f=v.findViewById(i);if(f!=null)return f;}return null;}
}
