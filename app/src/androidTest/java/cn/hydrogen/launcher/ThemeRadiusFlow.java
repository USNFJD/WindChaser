package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Exercises painted Android backgrounds and real launcher views; no commercial map or music engine is claimed. */
final class ThemeRadiusFlow extends RevisionFlow {
    ThemeRadiusFlow(Instrumentation t){super(t);report.setLength(0);report.append("API19 theme radius: real drawable pixels and launcher views; no commercial engine acceptance.\n");}
    int clearCorner(Drawable background){
        require(background!=null,"missing card background");Rect old=new Rect(background.getBounds());Bitmap image=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);
        try{background.setBounds(0,0,160,160);background.draw(new Canvas(image));int clear=0;for(int y=0;y<80;y++)for(int x=0;x<80;x++)if(Color.alpha(image.getPixel(x,y))==0)clear++;require(Color.alpha(image.getPixel(80,80))>0,"card surface vanished");return clear;}
        finally{background.setBounds(old);image.recycle();}
    }
    View[] cards(){
        ViewGroup home=(ViewGroup)field(activity,"home"),pair=(ViewGroup)home.getChildAt(0),content=(ViewGroup)field(activity,"standardContent");
        View[] out={pair.getChildAt(0),pair.getChildAt(1),find(root(),"快捷区时间日期，横跨两列"),find(root(),"蓝牙电话"),find(root(),"应用列表"),find(root(),"系统设置"),find(root(),"快捷区自定义应用1"),content.getChildAt(content.getChildCount()-1)};
        for(int n=0;n<out.length;n++)require(out[n]!=null&&out[n].getBackground() instanceof GradientDrawable,"missing real card "+n);return out;
    }
    int[] corners(View[] views){int[] result=new int[views.length];for(int i=0;i<views.length;i++)result[i]=clearCorner(views[i].getBackground());return result;}
    void snapshot(String name){View view=root();if(view.getWidth()<1||view.getHeight()<1)return;Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);try{view.draw(new Canvas(image));try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getFilesDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}}catch(IOException e){throw new RuntimeException(e);}finally{image.recycle();}}
    ScrollView scroll(View view){if(view instanceof ScrollView&&view.getBackground() instanceof GradientDrawable)return (ScrollView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ScrollView child=scroll(((ViewGroup)view).getChildAt(i));if(child!=null)return child;}return null;}
    @Override void run(){Config cfg=new Config(test.getTargetContext());Map<String,?> saved=new HashMap<>(cfg.p.getAll());
        try{
            ThemeOptions.reset(cfg,null);cfg.put("autoRecoverApps",false);cfg.put("keepCompanions",false);cfg.put("weatherAuto",false);cfg.put("pageAnimation",false);cfg.put("homePage",1);cfg.put("swap",false);
            check("all panel areas preserve default shape and respond from square to large corners",()->main(()->{
                for(String area:new String[]{"map","music","quick","dock","settings","files"})for(float baseline:new float[]{12,18,20,22,30}){
                    cfg.put("radius",20);GradientDrawable themed=ThemeStyle.panel(test.getTargetContext(),area,0xff101e2a,baseline,0,0);require(clearCorner(themed)==clearCorner(Ui.panel(0xff101e2a,baseline,0,0)),area+" changed default shape");
                    cfg.put("radius",0);int square=clearCorner(ThemeStyle.panel(test.getTargetContext(),area,0xff101e2a,baseline,0,0));cfg.put("radius",10);int small=clearCorner(ThemeStyle.panel(test.getTargetContext(),area,0xff101e2a,baseline,0,0));cfg.put("radius",40);int large=clearCorner(ThemeStyle.panel(test.getTargetContext(),area,0xff101e2a,baseline,0,0));require(square<small&&small<large,area+" ignores corner setting");
                }
            }));
            cfg.put("radius",0);activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(350);
            check("eight real dashboard surfaces update in place and stay stable on repeated theme application",()->main(()->{
                View[] views=cards();int[] square=corners(views);snapshot("radius-square.png");cfg.put("radius",20);ThemeStyle.apply(root(),"cardText");int[] normal=corners(views);snapshot("radius-default.png");cfg.put("radius",40);ThemeStyle.apply(root(),"cardText");int[] large=corners(views);snapshot("radius-large.png");
                for(int n=0;n<views.length;n++){require(square[n]<normal[n]&&normal[n]<large[n],"dashboard surface "+n+" unchanged or double-scaled");require(views[n]==cards()[n],"view recreated during style refresh");}
                for(int n=0;n<5;n++)ThemeStyle.apply(root(),"cardText");require(Arrays.equals(large,corners(views)),"repeated application compounded corners");activity.render();activity.showPage(1);require(Arrays.equals(large,corners(cards())),"fresh cards disagree with refreshed cards");
            }));
            check("radius reset and backup restore update already-created card backgrounds",()->main(()->{
                cfg.put("radius",40);String backup;try{backup=cfg.exportJson();}catch(Exception e){throw new RuntimeException(e);}View[] views=cards();ThemeStyle.apply(root(),"cardText");int[] large=corners(views);cfg.put("radius",0);ThemeStyle.apply(root(),"cardText");int[] square=corners(views);try{cfg.restore(backup);}catch(Exception e){throw new RuntimeException(e);}ThemeStyle.apply(root(),"cardText");require(Arrays.equals(large,corners(views)),"restore radius missing");ThemeOptions.reset(cfg,"布局");ThemeStyle.apply(root(),"cardText");int[] defaults=corners(views);for(int i=0;i<views.length;i++)require(square[i]<defaults[i]&&defaults[i]<large[i],"reset radius missing on card "+i);
            }));
            check("settings content background refreshes without recreating its scroll view",()->{
                SettingsActivity settings=(SettingsActivity)test.startActivitySync(new Intent(activity,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));main(()->{try{View nav=find(settings.getWindow().getDecorView(),"设置分类：卡片与布局");require(nav!=null,"layout category missing");nav.performClick();View surface=scroll(settings.getWindow().getDecorView());require(surface!=null,"settings card missing");cfg.put("radius",0);ThemeStyle.apply(settings.getWindow().getDecorView(),"settingsText");int square=clearCorner(surface.getBackground());cfg.put("radius",40);ThemeStyle.apply(settings.getWindow().getDecorView(),"settingsText");require(clearCorner(surface.getBackground())>square,"settings background stayed fixed");}finally{settings.finish();}});
            });
            check("launcher menu window follows corners while keeping its surface opacity",()->main(()->{
                cfg.put("radius",0);AlertDialog menu=new ThemeDialog.Builder(activity).setTitle("圆角测试").setItems(new String[]{"测试选项"},null).create();try{menu.show();ThemeStyle.watchDialog(menu);View decor=menu.getWindow().getDecorView();int square=clearCorner(decor.getBackground());cfg.put("radius",40);ThemeStyle.apply(decor,"dialogText");require(clearCorner(decor.getBackground())>square,"menu window stayed fixed");}finally{menu.dismiss();}
            }));
            check("remote widget backgrounds and contents remain excluded",()->main(()->{
                android.appwidget.AppWidgetHostView remote=new android.appwidget.AppWidgetHostView(activity);View child=new View(activity);cfg.put("radius",0);child.setBackground(ThemeStyle.panel(activity,"map",0xff101e2a,20,0,0));remote.addView(child);int original=clearCorner(child.getBackground());cfg.put("radius",40);ThemeStyle.apply(remote,"cardText");require(clearCorner(child.getBackground())==original,"remote content was restyled");
            }));
            check("existing floating music card follows preference changes and closes cleanly",()->{
                main(()->{cfg.put("radius",0);activity.startService(new Intent(activity,FloatingMusicService.class));});Thread.sleep(250);final View[] panel={null};main(()->{try{panel[0]=find(dialogRoot("QQ音乐悬浮控制"),"QQ音乐悬浮控制");}catch(Exception e){throw new RuntimeException(e);}require(panel[0]!=null,"floating card missing");int before=clearCorner(panel[0].getBackground());cfg.put("radius",40);require(clearCorner(panel[0].getBackground())>before,"floating radius stayed fixed");});main(()->activity.stopService(new Intent(activity,FloatingMusicService.class)));Thread.sleep(160);main(()->require(panel[0].getParent()==null,"floating window retained after stop"));
            });
        }catch(Throwable e){failed++;report.append("FAIL fixture ").append(e).append('\n');}
        finally{main(()->{test.getTargetContext().stopService(new Intent(test.getTargetContext(),FloatingMusicService.class));if(activity!=null)activity.finish();});restore(cfg,saved);}
        report.append("RADIUS_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
