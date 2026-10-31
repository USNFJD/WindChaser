package cn.hydrogen.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Uninstalled, independent APK to exercise real Activity, Surface, lifecycle and input. */
public class ProbeActivity extends Activity {
    int count,ticks;TextView label;Handler handler=new Handler();SurfaceView surface;
    Runnable clock=new Runnable(){public void run(){ticks++;draw();handler.postDelayed(this,500);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(12,8,12,8);root.setBackgroundColor(getPackageName().endsWith("left")?0xffd7edf2:0xffebdff5);
        label=new TextView(this);label.setTextSize(20);root.addView(label);show();
        Button plus=new Button(this);plus.setText("Increment counter");plus.setOnClickListener(v->{count++;show();Log.i("CardProbe",getPackageName()+" counter="+count);});root.addView(plus);
        Button detail=new Button(this);detail.setText("Open second Activity");detail.setOnClickListener(v->startActivity(new Intent(this,DetailActivity.class)));root.addView(detail);
        Button dialog=new Button(this);dialog.setText("Open dialog");dialog.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Guest dialog").setMessage(getPackageName()).setPositiveButton("OK",null).show());root.addView(dialog);
        EditText edit=new EditText(this);edit.setSingleLine();edit.setHint("Keyboard test");root.addView(edit);
        surface=new SurfaceView(this);surface.getHolder().addCallback(new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder h){draw();}public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){draw();}public void surfaceDestroyed(SurfaceHolder h){}});
        surface.setOnTouchListener((v,e)->{Log.i("CardProbe",getPackageName()+" TOUCH action="+e.getActionMasked()+" count="+e.getPointerCount()+" x="+e.getX()+" y="+e.getY());return true;});root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);handler.post(clock);
        Log.i("CardProbe",getPackageName()+" CREATE uid="+android.os.Process.myUid());
    }
    void show(){label.setText(getPackageName()+"\nCounter: "+count);}
    void draw(){if(surface==null||!surface.getHolder().getSurface().isValid())return;Canvas c=null;try{c=surface.getHolder().lockCanvas();if(c!=null){c.drawColor(0xff213345);Paint p=new Paint(3);p.setColor(Color.CYAN);p.setTextSize(24);c.drawText("Live Surface tick "+ticks,20,36,p);c.drawCircle(30+(ticks*19)%Math.max(1,c.getWidth()-60),90,18,p);}}finally{if(c!=null)surface.getHolder().unlockCanvasAndPost(c);}}
    @Override public void onPause(){super.onPause();Log.i("CardProbe",getPackageName()+" PAUSE");}
    @Override public void onResume(){super.onResume();Log.i("CardProbe",getPackageName()+" RESUME");}
    @Override public void onStop(){super.onStop();Log.i("CardProbe",getPackageName()+" STOP");}
    @Override public void onDestroy(){handler.removeCallbacks(clock);super.onDestroy();}
}
