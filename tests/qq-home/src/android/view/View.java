package android.view;
import java.util.*;import android.content.*;
public class View {
 public static final int VISIBLE=0,GONE=8;public int visibility;static int next=100;public int width,height,id;public Object tag;public ViewGroup parent;public Context context;public ViewGroup.LayoutParams lp;public OnClickListener click;
 public List<OnLayoutChangeListener> layouts=new ArrayList<>();public List<OnAttachStateChangeListener> attachments=new ArrayList<>();
 public int paddingLeft,paddingRight,paddingTop,paddingBottom;public int getPaddingLeft(){return paddingLeft;}public int getPaddingRight(){return paddingRight;}public int getPaddingTop(){return paddingTop;}public int getPaddingBottom(){return paddingBottom;}public void setPadding(int l,int t,int r,int b){paddingLeft=l;paddingTop=t;paddingRight=r;paddingBottom=b;}
 public View(Context c){context=c;}public Context getContext(){return context;}public int getWidth(){return width;}public int getHeight(){return height;}public int getRight(){return width;}public Object getParent(){return parent;}
 public ViewGroup.LayoutParams getLayoutParams(){return lp;}public void setLayoutParams(ViewGroup.LayoutParams p){lp=p;}
 public View findViewWithTag(Object v){return v.equals(tag)?this:null;}public View findViewById(int v){return v==id?this:null;}
 public void setTag(Object v){tag=v;}public void setVisibility(int v){visibility=v;}public void setId(int v){id=v;}public int getId(){return id;}public static int generateViewId(){return next++;}
 public void setContentDescription(CharSequence s){}public void setFocusable(boolean b){}public void setClickable(boolean b){}public void setOnClickListener(OnClickListener l){click=l;}
 public void addOnLayoutChangeListener(OnLayoutChangeListener l){if(!layouts.contains(l))layouts.add(l);}public void removeOnLayoutChangeListener(OnLayoutChangeListener l){layouts.remove(l);}
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attachments.add(l);}public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){attachments.remove(l);}
 public void layout(int w,int h){width=w;height=h;for(OnLayoutChangeListener l:new ArrayList<>(layouts))l.onLayoutChange(this,0,0,w,h,0,0,0,0);}
 public interface OnLayoutChangeListener {void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
 public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public interface OnClickListener {void onClick(View v);}protected void onDraw(android.graphics.Canvas c){}public boolean isPressed(){return false;}public void setPressed(boolean p){}public void invalidate(){}
}
