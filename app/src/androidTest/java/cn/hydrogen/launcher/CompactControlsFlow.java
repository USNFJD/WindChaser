package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Only the two compact windows and dock mute label; not a full regression. */
final class CompactControlsFlow {
    private final Instrumentation test;private MainActivity activity;private Config cfg;private AudioManager audio;private int passed,failed;private final ArrayList<Dialog> dialogs=new ArrayList<>();private final StringBuilder report=new StringBuilder("Targeted Android API19 check: compact picker/song windows and dock mute only\n");
    CompactControlsFlow(Instrumentation test){this.test=test;}
    private interface Step{void run()throws Exception;}
    private interface Condition{boolean ready()throws Exception;}
    private void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void check(String name,Step step){android.util.Log.i("CompactControlsTest","START "+name);try{step.run();passed++;report.append("PASS ");}catch(Throwable e){failed++;report.append("FAIL ").append(e).append(" ");}report.append(name).append('\n');main(()->{for(Dialog d:dialogs)if(d.isShowing())d.dismiss();});}
    private void main(Runnable task){final Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void await(String why,Condition task)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<end){if(task.ready())return;Thread.sleep(60);}throw new AssertionError(why);}
    private Dialog owned()throws Exception{Object host=field(activity,"mapDialogs");Dialog found=null;for(Object item:(Collection<?>)field(host,"windows")){Dialog d=(Dialog)field(item,"dialog");if(d.isShowing())found=d;}return found;}
    private void ready(Dialog d)throws Exception{await("dialog did not reach its requested layout",()->d.isShowing()&&bounds(d).height()>0&&bounds(d).width()>0&&bounds(d).width()==d.getWindow().getAttributes().width);}
    private Rect bounds(Dialog d){View v=d.getWindow().getDecorView();int[] xy=new int[2];v.getLocationOnScreen(xy);return new Rect(xy[0],xy[1],xy[0]+v.getWidth(),xy[1]+v.getHeight());}
    private void visible(View v){Rect area=new Rect();require(v!=null&&v.isShown()&&v.getWidth()>0&&v.getHeight()>0&&v.getGlobalVisibleRect(area)&&area.width()>=v.getWidth()-1&&area.height()>=v.getHeight()-1,"button clipped");}
    private View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=find(((ViewGroup)root).getChildAt(n),label);if(found!=null)return found;}return null;}
    private void capture(Dialog d,String name){main(()->{View root=d.getWindow().getDecorView();Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException e){throw new RuntimeException(e);}finally{b.recycle();}});}
    private String label()throws Exception{final String[] text={null};main(()->{try{text[0]=((TextView)field(activity,"muteLabel")).getText().toString();}catch(Exception e){throw new RuntimeException(e);}});return text[0];}
    private void assertMute(boolean muted)throws Exception{String expected=muted?"已静音":"静音";await("mute label "+expected,()->expected.equals(label()));main(()->{try{View button=(View)field(activity,"muteButton");require(expected.contentEquals(button.getContentDescription()),"accessibility state differs");visible(button);}catch(Exception e){throw new RuntimeException(e);}});}
    void run(){
        cfg=new Config(test.getTargetContext());String backup;try{backup=cfg.exportJson();}catch(org.json.JSONException e){throw new AssertionError(e);}audio=(AudioManager)test.getTargetContext().getSystemService(Context.AUDIO_SERVICE);int before=audio.getStreamVolume(AudioManager.STREAM_MUSIC),max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),audible=Math.max(1,max/2);
        try{
            cfg.put("autoRecoverApps",false);cfg.put("pageAnimation",false);cfg.put("homePage",MainActivity.CARDS);cfg.put("mapShare",60);cfg.put("swap",false);cfg.put("quickWidth",100);audio.setStreamVolume(AudioManager.STREAM_MUSIC,audible,0);
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(500);
            final int[] baseline={0};
            check("other menu keeps previous width and uses ordinary application window",()->{
                AlertDialog[] box={null};main(()->{box[0]=new ThemeDialog.Builder(activity).setTitle("尺寸对照").setItems(new String[]{"保持原宽"},null).setNegativeButton("取消",null).show();dialogs.add(box[0]);});ready(box[0]);Thread.sleep(120);
                int w=activity.getResources().getDisplayMetrics().widthPixels;float density=activity.getResources().getDisplayMetrics().density;int expected=Math.max(1,Math.min(w-Math.round(24*density),Math.max(Math.round(280*density),Math.round(w*.5f))));Rect area=activity.mapDialogArea();if(area!=null&&area.width()>=48&&area.height()>=48)expected=Math.min(expected,area.width());baseline[0]=expected;
                require(Math.abs(bounds(box[0]).width()-expected)<=1,"unrelated menu width changed");require(box[0].getWindow().getAttributes().type==WindowManager.LayoutParams.TYPE_APPLICATION,"picker elevated to overlay");main(box[0]::dismiss);
            });
            check("actual Add Application picker is 60 percent, bounded and stable",()->{
                main(()->Ui.pick(activity,app->{}));await("picker missing",()->owned() instanceof AlertDialog);AlertDialog picker=(AlertDialog)owned();dialogs.add(picker);ready(picker);Thread.sleep(120);Rect initial=bounds(picker);require(Math.abs(initial.width()-Math.round(baseline[0]*.6f))<=1,"picker width "+initial.width()+" baseline "+baseline[0]);require(initial.height()<=Math.ceil(activity.getResources().getDisplayMetrics().heightPixels*.62f),"picker too tall");main(()->visible(picker.getButton(AlertDialog.BUTTON_NEGATIVE)));main(()->picker.getListView().setSelection(picker.getListView().getCount()-1));Thread.sleep(200);require(initial.equals(bounds(picker)),"picker moved while scrolling");require(picker.getWindow().getAttributes().windowAnimations==0,"picker animates");capture(picker,"compact-app-picker.png");main(picker::dismiss);
            });
            check("song window beside map is 60 percent and repeated show does not shrink again",()->{
                QqMusicBridge bridge=(QqMusicBridge)field(activity,"musicBridge");SongInfoDialog[] box={null};main(()->{box[0]=new SongInfoDialog(activity,bridge);box[0].show();dialogs.add(box[0]);});ready(box[0]);Thread.sleep(120);int baselineWidth=Math.min(activity.u(680),(int)(activity.getResources().getDisplayMetrics().widthPixels*.88f));Rect area=activity.mapDialogArea();if(area!=null&&area.width()>=48&&area.height()>=48)baselineWidth=Math.min(baselineWidth,area.width());int expected=Math.round(baselineWidth*.6f);require(Math.abs(bounds(box[0]).width()-expected)<=1,"song width "+bounds(box[0]).width()+" expected "+expected);main(()->{visible(find(box[0].getWindow().getDecorView(),"关闭歌曲信息"));visible(find(box[0].getWindow().getDecorView(),"歌曲信息打开QQ音乐"));});Rect first=bounds(box[0]);main(box[0]::show);Thread.sleep(160);require(first.equals(bounds(box[0])),"song repeatedly shrinks or moves");capture(box[0],"compact-song-info.png");main(box[0]::dismiss);
            });
            check("first-page song window is also 60 percent with reachable close",()->{
                main(()->activity.showPage(MainActivity.MINIMAL));Thread.sleep(180);QqMusicBridge bridge=(QqMusicBridge)field(activity,"musicBridge");SongInfoDialog[] box={null};main(()->{box[0]=new SongInfoDialog(activity,bridge);box[0].show();dialogs.add(box[0]);});ready(box[0]);Thread.sleep(120);int previous=Math.min(activity.u(680),(int)(activity.getResources().getDisplayMetrics().widthPixels*.88f));require(Math.abs(bounds(box[0]).width()-Math.round(previous*.6f))<=1,"first-page song width");main(()->visible(find(box[0].getWindow().getDecorView(),"关闭歌曲信息")));main(box[0]::dismiss);main(()->activity.showPage(MainActivity.CARDS));Thread.sleep(180);
            });
            check("mute click immediately changes text and second click restores previous volume",()->{
                assertMute(false);main(()->{try{((View)field(activity,"muteButton")).performClick();}catch(Exception e){throw new RuntimeException(e);}});require(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0,"mute failed");assertMute(true);main(()->{try{((View)field(activity,"muteButton")).performClick();}catch(Exception e){throw new RuntimeException(e);}});require(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==audible,"volume restore failed");assertMute(false);
            });
            check("system media volume changes keep mute text synchronized",()->{
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);assertMute(true);audio.setStreamVolume(AudioManager.STREAM_MUSIC,audible,0);assertMute(false);
            });
        }catch(Throwable e){failed++;report.append("FAIL fixture ").append(e).append('\n');}
        finally{main(()->{for(Dialog d:dialogs)if(d.isShowing())d.dismiss();if(activity!=null)activity.finish();});audio.setStreamVolume(AudioManager.STREAM_MUSIC,before,0);try{cfg.restore(backup);}catch(Exception e){failed++;report.append("FAIL restore ").append(e).append('\n');}}
        report.append("COMPACT_CONTROLS_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
