package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual View touch dispatch; synthetic songs/apps and captured outbound QQ protocol. */
final class WindChaserFlow {
    private final Instrumentation test;private MainActivity activity;private QqMusicBridge bridge;
    private final ArrayList<Intent> sent=new ArrayList<>();private final StringBuilder report=new StringBuilder("Synthetic songs/apps; real View gestures; captured QQ broadcasts. No commercial app executed.\n");
    private int passed,failed;
    private interface Check{void run()throws Exception;}
    WindChaserFlow(Instrumentation t){test=t;}
    private void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private void check(String name,Check body){try{body.run();passed++;report.append("PASS ");}catch(Throwable e){failed++;report.append("FAIL ").append(e).append(" ");}report.append(name).append('\n');}
    private void main(Runnable body){Throwable[] error={null};test.runOnMainSync(()->{try{body.run();}catch(Throwable e){error[0]=e;}});test.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private int current()throws Exception{return (Integer)field("currentPage");}
    private View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=find(((ViewGroup)root).getChildAt(n),label);if(found!=null)return found;}return null;}
    private View card(){return find(activity.getWindow().getDecorView(),currentUnchecked()==MainActivity.MINIMAL?"简洁页无边框音乐区":"QQ音乐卡片");}
    private int currentUnchecked(){try{return current();}catch(Exception e){throw new RuntimeException(e);}}
    private View control(String label){View view=find(card(),label);require(view!=null&&view.isShown(),"missing "+label);return view;}
    private GridView grid(){try{return(GridView)field("grid");}catch(Exception e){throw new RuntimeException(e);}}
    private void dispatch(View root,int action,float x,float y,long down,long when){MotionEvent e=MotionEvent.obtain(down,when,action,x,y,0);try{root.dispatchTouchEvent(e);}finally{e.recycle();}}
    private void touch(View target,float sx,float sy,float ex,float ey,int finish){
        main(()->{View root=activity.getWindow().getDecorView();int[] xy=new int[2],origin=new int[2];target.getLocationOnScreen(xy);root.getLocationOnScreen(origin);float x=xy[0]-origin[0],y=xy[1]-origin[1];long now=SystemClock.uptimeMillis();dispatch(root,0,x+sx,y+sy,now,now);for(int n=1;n<=8;n++)dispatch(root,2,x+sx+(ex-sx)*n/8,y+sy+(ey-sy)*n/8,now,now+n*20);dispatch(root,finish,x+ex,y+ey,now,now+180);});
    }
    private void swipe(boolean left){View root=activity.getWindow().getDecorView();touch(root,root.getWidth()*(left?.8f:.2f),activity.u(45),root.getWidth()*(left?.2f:.8f),activity.u(45),MotionEvent.ACTION_UP);}
    private Intent last(){require(!sent.isEmpty(),"no outbound seek");return sent.get(sent.size()-1);}
    private int seconds(){return Integer.parseInt(last().getData().getQueryParameter("m1"));}
    private void modeReply(int mode){try{bridge.accept("{\"module\":\"play\",\"command\":{\"method\":\"update_play_mode\",\"data\":{\"play_mode\":"+mode+"}}}");}catch(Exception e){throw new RuntimeException(e);}}
    private void seedSong(String title){
        main(()->{try{bridge.accept("{\"module\":\"play\",\"command\":{\"method\":\"update_song\",\"data\":{\"key_title\":\""+title+"\",\"key_artist\":[{\"singer\":\"交互测试样例（非真实播放）\"}],\"curr_time\":10000,\"total_time\":180000}}}");bridge.state=3;bridge.lyrics.clear();for(int n=0;n<35;n++)bridge.lyrics.add(new QqMusicBridge.Line(n*5000,"第 "+(n+1)+" 行 · 点击或上下拖动歌词"));activity.render();sent.clear();}catch(Exception e){throw new RuntimeException(e);}});
    }
    private void savePrefs(Config config,Map<String,?> values){SharedPreferences.Editor e=config.p.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Float)e.putFloat(k,(Float)v);}e.commit();}
    private void capture(String name)throws Exception{final Bitmap[] bitmap={null};main(()->{View root=activity.getWindow().getDecorView();bitmap[0]=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap[0]));});try(OutputStream out=new FileOutputStream(new File(test.getTargetContext().getFilesDir(),name))){bitmap[0].compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap[0].recycle();}}
    @SuppressWarnings("unchecked") void run(){
        Config config=new Config(test.getTargetContext());Map<String,?> saved=new HashMap<>(config.p.getAll());
        try{
            require(!Ui.installed(test.getTargetContext(),Config.MUSIC),"isolated emulator required");config.put("autoRecoverApps",false);config.put("pageAnimation",false);config.put("homePage",MainActivity.MINIMAL);config.put("musicMode",0);config.put("musicPackage",Config.MUSIC);
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(900);test.waitForIdleSync();
            main(()->{try{Field f=MainActivity.class.getDeclaredField("musicBridge");f.setAccessible(true);((QqMusicBridge)f.get(activity)).stop();bridge=new QqMusicBridge(new ContextWrapper(activity){@Override public void sendBroadcast(Intent intent){sent.add(new Intent(intent));}@Override public PackageManager getPackageManager(){return new android.test.mock.MockPackageManager(){@Override public ApplicationInfo getApplicationInfo(String pkg,int flags)throws PackageManager.NameNotFoundException{if(Config.MUSIC.equals(pkg))return new ApplicationInfo();return activity.getPackageManager().getApplicationInfo(pkg,flags);}};}},()->{});f.set(activity,bridge);}catch(Exception e){throw new RuntimeException(e);}});
            check("verified relative-second seek protocol, direction and range",()->{
                Intent forward=QqMusicBridge.seekIntent(55000,10000,180000),back=QqMusicBridge.seekIntent(20000,80000,180000);require("7".equals(forward.getData().getQueryParameter("m0"))&&"45".equals(forward.getData().getQueryParameter("m1")),"forward mismatch");require("8".equals(back.getData().getQueryParameter("m0"))&&"60".equals(back.getData().getQueryParameter("m1")),"back mismatch");require(QqMusicBridge.seekIntent(5000,0,0)==null,"unknown duration allowed");require(QqMusicBridge.seekIntent(10000,10200,180000)==null,"subsecond emitted");require("169".equals(QqMusicBridge.seekIntent(Long.MAX_VALUE,10000,180000).getData().getQueryParameter("m1")),"end not bounded");
            });
            check("cached metadata cannot seek until real state, old song revision rejected",()->main(()->{bridge.connected=false;bridge.duration=180000;require(!bridge.seekTo(60000,bridge.trackRevision),"cached song allowed");bridge.connected=true;require(!bridge.seekTo(60000,bridge.trackRevision+1),"old song allowed");require(sent.isEmpty(),"unexpected broadcast");}));
            for(int page:new int[]{MainActivity.MINIMAL,MainActivity.CARDS}){
                main(()->activity.showPage(page));seedSong("追风桌面 · 手势验证 "+page);
                check("page "+page+" progress tap emits one seek and awaits QQ state",()->{
                    View seek=control("歌曲播放进度，可点击或拖动");touch(seek,seek.getWidth()*.6f,seek.getHeight()/2f,seek.getWidth()*.6f,seek.getHeight()/2f,MotionEvent.ACTION_UP);
                    require(sent.size()==1&&seconds()>85&&seconds()<115,"bad seek count/time "+sent.size());require(bridge.position==10000&&bridge.state==3,"invented playback state");require(current()==page,"seek flipped page");
                });
                seedSong("独立进度拖动 "+page);
                check("page "+page+" progress drag commits on release, cancel sends nothing",()->{
                    main(sent::clear);View seek=control("歌曲播放进度，可点击或拖动");touch(seek,seek.getWidth()*.3f,seek.getHeight()/2f,seek.getWidth()*.8f,seek.getHeight()/2f,MotionEvent.ACTION_UP);require(sent.size()==1&&seconds()>120,"drag seek missing");main(sent::clear);touch(seek,seek.getWidth()*.3f,seek.getHeight()/2f,seek.getWidth()*.8f,seek.getHeight()/2f,MotionEvent.ACTION_CANCEL);require(sent.isEmpty(),"cancel committed");require(current()==page,"drag flipped page");
                });
                seedSong("追风桌面 · 歌词验证 "+page);
                check("page "+page+" lyric click seeks its own timestamp",()->{
                    ViewGroup lyrics=(ViewGroup)control("歌词，可上下拖动或点击调整播放进度");View row=null;for(int n=0;n<lyrics.getChildCount();n++)if(lyrics.getChildAt(n).isShown())row=lyrics.getChildAt(n);require(row!=null,"no lyrics");long target=((QqMusicBridge.Line)row.getTag()).time;touch(row,row.getWidth()*.4f,row.getHeight()/2f,row.getWidth()*.4f,row.getHeight()/2f,MotionEvent.ACTION_UP);require(sent.size()==1&&Math.abs(seconds()*1000-(target-10000))<1000,"lyric timestamp mismatch");require(current()==page,"lyric click flipped page");
                });
                seedSong("追风桌面 · 滚动验证 "+page);
                check("page "+page+" vertical lyric drag browses then seeks, cancel sends nothing",()->{
                    View words=control("歌词，可上下拖动或点击调整播放进度");float y=words.getHeight()*.65f;touch(words,words.getWidth()*.4f,y,words.getWidth()*.4f,y-activity.u(95),MotionEvent.ACTION_UP);require(sent.size()==1&&seconds()>=10,"lyric drag missing");main(sent::clear);touch(words,words.getWidth()*.4f,y,words.getWidth()*.4f,y-activity.u(95),MotionEvent.ACTION_CANCEL);require(sent.isEmpty(),"lyric cancel committed");require(current()==page,"lyric drag flipped page");
                });
                seedSong("歌词翻页 "+page);
                check("page "+page+" horizontal lyric swipe advances page without seeking",()->{
                    View words=control("歌词，可上下拖动或点击调整播放进度");touch(words,words.getWidth()*.85f,words.getHeight()*.4f,words.getWidth()*.15f,words.getHeight()*.4f,MotionEvent.ACTION_UP);require(current()==page+1,"lyrics did not advance");require(sent.isEmpty(),"swipe sought song");main(()->activity.showPage(page));
                });
                check("page "+page+" horizontal cover swipe, cancel and small drag preserve intent",()->{
                    View area=card();float y=control("当前歌曲封面").getTop()+activity.u(90);touch(area,area.getWidth()*.8f,y,area.getWidth()*.2f,y,MotionEvent.ACTION_CANCEL);require(current()==page&&sent.isEmpty(),"cancel navigated");View words=control("歌词，可上下拖动或点击调整播放进度");touch(words,words.getWidth()*.6f,words.getHeight()*.5f,words.getWidth()*.6f-activity.u(30),words.getHeight()*.5f,MotionEvent.ACTION_UP);require(current()==page&&sent.isEmpty(),"small drag navigated/sought");touch(area,area.getWidth()*.8f,y,area.getWidth()*.2f,y,MotionEvent.ACTION_UP);require(current()==page+1,"cover did not advance");main(()->activity.showPage(page));
                });
                check("page "+page+" adaptive lyrics stay centered and transport stays close to bottom",()->main(()->{
                    MusicCard music=(MusicCard)card();music.update();ViewGroup words=(ViewGroup)control("歌词，可上下拖动或点击调整播放进度");int count=0,selected=-1;TextView active=null;
                    for(int n=0;n<words.getChildCount();n++)if(words.getChildAt(n).isShown()){count++;if(words.getChildAt(n).isSelected()){selected=n;active=(TextView)words.getChildAt(n);}}
                    require(count>=4&&selected==(count-1)/2,"active row not centered");require(((QqMusicBridge.Line)active.getTag()).time==bridge.currentPosition(),"wrong active timestamp");require(active.getTextSize()>((TextView)words.getChildAt(0)).getTextSize(),"active style missing");
                    LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)words.getLayoutParams();require(p.topMargin<=activity.u(14)&&p.bottomMargin<=activity.u(10),"large lyric gaps");ImageView mode=(ImageView)control("切换播放模式");require(mode.getDrawable() instanceof PlaybackModeIcon,"mode symbol missing");
                    ViewGroup controls=(ViewGroup)control("居中播放控制栏");require(controls.getChildCount()==5,"not five controls");float center=controls.getWidth()/2f,previousCenter=-1;for(int n=0;n<5;n++){View child=controls.getChildAt(n);float x=(child.getLeft()+child.getRight())/2f;require(child.isClickable()&&child.getWidth()>0,"inactive button");if(n==2)require(Math.abs(x-center)<=1,"play not centered");if(n>0)require(Math.abs((x-previousCenter)-controls.getWidth()/5f)<=2,"uneven button spacing");previousCenter=x;}require(((ImageView)controls.getChildAt(2)).getDrawable() instanceof TransportIcon,"play icon missing");
                    View bottom=page==MainActivity.MINIMAL?control("媒体音量滑块"):control("播放 / 暂停");int[] outer=new int[2],inner=new int[2];music.getLocationOnScreen(outer);bottom.getLocationOnScreen(inner);int gap=outer[1]+music.getHeight()-inner[1]-bottom.getHeight();require(gap>=0&&gap<=activity.u(24),"large bottom gap "+gap);
                }));
            }
            check("lyrics swipe right goes from second to first; empty metadata still swipes",()->{main(()->activity.showPage(1));seedSong("返回简洁页");View words=control("歌词，可上下拖动或点击调整播放进度");touch(words,words.getWidth()*.15f,words.getHeight()*.5f,words.getWidth()*.85f,words.getHeight()*.5f,MotionEvent.ACTION_UP);require(current()==0&&sent.isEmpty(),"back swipe failed");main(()->{bridge.connected=false;bridge.lyrics.clear();((MusicCard)card()).update();});words=control("歌词，可上下拖动或点击调整播放进度");touch(words,words.getWidth()*.85f,words.getHeight()*.5f,words.getWidth()*.15f,words.getHeight()*.5f,MotionEvent.ACTION_UP);require(current()==1&&sent.isEmpty(),"empty lyric swipe failed");});
            check("play modes use verified WeDrive indices without URI and authoritative callback",()->main(()->{try{
                require(QqMusicBridge.nextMode(0)==101&&QqMusicBridge.nextMode(101)==105&&QqMusicBridge.nextMode(105)==103&&QqMusicBridge.nextMode(103)==101,"mode order");
                int[] modes={101,105,103},indices={18,17,16};for(int n=0;n<3;n++){Intent intent=QqMusicBridge.modeIntent(modes[n]);require(intent.getData()==null&&QqMusicBridge.WEDRIVE_ACTION.equals(intent.getAction())&&intent.getComponent().getClassName().endsWith("BroadcastReceiverCenterForWeDrive"),"bad route");require(new org.json.JSONObject(intent.getStringExtra(QqMusicBridge.WEDRIVE_EXTRA)).getJSONObject("command").getJSONObject("extData").getInt("index")==indices[n],"bad index");modeReply(modes[n]);require(bridge.playMode==modes[n]&&!bridge.modeText().equals("播放模式"),"callback missing");}modeReply(999);require(bridge.playMode==103,"invalid mode replaced state");
            }catch(Exception e){throw new RuntimeException(e);}}));
            check("mode button emits actual background command without inventing state",()->{
                main(()->{sent.clear();control("切换播放模式").performClick();((MusicCard)card()).update();});require(bridge.pendingMode==101&&bridge.playMode==103&&bridge.modeText().equals("切换中…"),"missing pending state");require(sent.size()==2&&QqMusicBridge.WEDRIVE_ACTION.equals(last().getAction())&&last().getData()==null,"button did not dispatch WeDrive");require(Math.abs(control("切换播放模式").getAlpha()-.45f)<.01f,"pending visual missing");
            });
            check("matching QQ reply clears pending and cancels fallback",()->{
                main(()->{modeReply(101);((MusicCard)card()).update();});int before=sent.size();Thread.sleep(1050);require(bridge.pendingMode==0&&bridge.playMode==101&&bridge.modeText().equals("单曲循环")&&sent.size()==before,"confirmed command retried");require(control("切换播放模式").getAlpha()==1f,"pending visual not cleared");
            });
            check("rapid mode clicks preserve newest choice against a stale reply",()->{
                main(()->{sent.clear();control("切换播放模式").performClick();control("切换播放模式").performClick();modeReply(105);});require(bridge.pendingMode==103&&bridge.playMode==105,"old response cleared new request");Thread.sleep(1050);require(sent.size()==5&&"103".equals(last().getData().getQueryParameter("m0")),"fallback retried obsolete mode");main(()->modeReply(103));require(bridge.pendingMode==0&&bridge.playMode==103,"latest response ignored");
            });
            check("no reply retries once then reports failure without changing actual mode",()->{
                main(()->{sent.clear();bridge.setMode(105);});Thread.sleep(3900);test.waitForIdleSync();require(bridge.pendingMode==0&&bridge.playMode==103&&bridge.modeText().equals("顺序播放"),"timeout invented success");require(sent.size()==3&&bridge.hint.contains("未确认"),"missing bounded retry or failure feedback");
            });
            check("unsupported mode sends nothing and invalid reply cannot confirm pending",()->{
                main(()->{sent.clear();bridge.setMode(999);});require(sent.isEmpty(),"invalid mode emitted");main(()->{bridge.setMode(101);modeReply(999);});require(bridge.pendingMode==101&&bridge.playMode==103,"invalid reply accepted");main(()->modeReply(101));
            });
            check("long-press mode opens an explicit chooser without opening QQ",()->{
                final AlertDialog[] dialog={null};main(()->{try{sent.clear();control("切换播放模式").performLongClick();Field f=MusicCard.class.getDeclaredField("playbackMenu");f.setAccessible(true);dialog[0]=(AlertDialog)f.get(card());require(dialog[0]!=null&&dialog[0].isShowing(),"menu not open");ListView list=dialog[0].getListView();require(list.getAdapter().getCount()==4&&list.getAdapter().getItem(0).toString().contains("单曲循环")&&list.getAdapter().getItem(3).toString().contains("打开 QQ音乐"),"bad menu content");list.performItemClick(list.getChildAt(1),1,1);}catch(Exception e){throw new RuntimeException(e);}});require(bridge.pendingMode==105&&sent.size()==2&&QqMusicBridge.WEDRIVE_ACTION.equals(last().getAction()),"explicit mode did not dispatch");main(()->modeReply(105));
            });
            check("right button opens truthful current-track panel, refreshes and labels cache",()->{
                main(()->{try{sent.clear();control("歌曲信息").performClick();Field f=MusicCard.class.getDeclaredField("songInfo");f.setAccessible(true);SongInfoDialog dialog=(SongInfoDialog)f.get(card());require(dialog!=null&&dialog.isShowing(),"song info missing");View root=dialog.getWindow().getDecorView();bridge.title="实时歌曲信息";bridge.artist="测试歌手";bridge.connected=true;bridge.state=3;((MusicCard)card()).update();require(((TextView)find(root,"歌曲信息歌名")).getText().toString().equals("实时歌曲信息"),"title did not refresh");require(((TextView)find(root,"歌曲信息歌手")).getText().toString().equals("测试歌手"),"artist did not refresh");require(((TextView)find(root,"歌曲信息播放状态")).getText().toString().equals("已暂停"),"pause reported as playing");require(find(root,"歌曲信息封面")!=null&&find(root,"歌曲信息播放进度")!=null,"metadata missing");bridge.connected=false;((MusicCard)card()).update();require(((TextView)find(root,"歌曲信息来源")).getText().toString().contains("上次播放"),"cache presented as live");bridge.title="";bridge.artist="";((MusicCard)card()).update();require(((TextView)find(root,"歌曲信息歌名")).getText().toString().equals("暂无歌曲"),"empty data fabricated");require(find(root,"播放列表")==null&&sent.isEmpty()&&currentUnchecked()==MainActivity.CARDS,"info button sent controls or navigated");find(root,"关闭歌曲信息").performClick();require(!dialog.isShowing(),"info did not close");}catch(Exception e){throw new RuntimeException(e);}});
            });
            check("previous play pause and next icons dispatch the original commands",()->{
                main(()->{sent.clear();bridge.state=3;control("上一首").performClick();control("播放 / 暂停").performClick();bridge.state=2;control("播放 / 暂停").performClick();control("下一首").performClick();});require(sent.size()==8,"transport command count");int[] codes={2,0,1,3};for(int n=0;n<4;n++)require((""+codes[n]).equals(sent.get(n*2+1).getData().getQueryParameter("m0")),"bad transport command");
            });
            check("song change during progress drag never seeks the new song",()->{
                seedSong("切歌前");View seek=control("歌曲播放进度，可点击或拖动");main(()->{int[] xy=new int[2];seek.getLocationOnScreen(xy);View root=activity.getWindow().getDecorView();long now=SystemClock.uptimeMillis();dispatch(root,0,xy[0]+seek.getWidth()*.3f,xy[1]+seek.getHeight()/2f,now,now);try{bridge.accept("{\"module\":\"play\",\"command\":{\"method\":\"update_song\",\"data\":{\"key_title\":\"切歌后\",\"key_artist\":[],\"curr_time\":0,\"total_time\":180000}}}");}catch(Exception e){throw new RuntimeException(e);}dispatch(root,1,xy[0]+seek.getWidth()*.7f,xy[1]+seek.getHeight()/2f,now,now+100);});require(sent.isEmpty(),"drag sought new song");
            });
            check("first -> second -> third swipes are sequential; first does not wrap",()->{
                main(()->activity.showPage(MainActivity.MINIMAL));swipe(false);require(current()==0,"first wrapped");swipe(true);require(current()==1,"missing cards");swipe(true);require(current()==2,"missing apps");swipe(false);require(current()==1,"third did not return second");
            });
            final ArrayList<ResolveInfo> apps=(ArrayList<ResolveInfo>)field("applications");
            check("100 applications paginate exactly once with no missing/duplicate items",()->{
                ResolveInfo original=Ui.apps(activity).get(0);main(()->{apps.clear();for(int n=0;n<100;n++){ResolveInfo info=new ResolveInfo(original);info.activityInfo=new ActivityInfo(original.activityInfo);info.activityInfo.name=original.activityInfo.name+".Fixture"+n;info.nonLocalizedLabel="测试应用 "+(n+1);apps.add(info);}activity.showPage(MainActivity.APPS);});Thread.sleep(180);test.waitForIdleSync();
                HashSet<String> items=new HashSet<>();int pages=activity.pageCount();require(pages>3,"no fourth page");for(int n=2;n<pages;n++){final int p=n;main(()->activity.showPage(p));for(int j=0;j<grid().getAdapter().getCount();j++)require(items.add(Ui.component((ResolveInfo)grid().getAdapter().getItem(j))),"duplicate app");require(grid().getLastVisiblePosition()==grid().getAdapter().getCount()-1,"app overflows page");}require(items.size()==100,"missing apps "+items.size());
            });
            check("last app page stops; third/fourth grid swipes go forward and back",()->{
                main(()->activity.showPage(999));int last=current();swipe(true);require(current()==last,"last wrapped");main(()->activity.showPage(2));View g=grid();touch(g,g.getWidth()*.85f,g.getHeight()*.5f,g.getWidth()*.15f,g.getHeight()*.5f,MotionEvent.ACTION_UP);require(current()==3,"grid did not advance");touch(g,g.getWidth()*.15f,g.getHeight()*.5f,g.getWidth()*.85f,g.getHeight()*.5f,MotionEvent.ACTION_UP);require(current()==2,"grid did not return");
            });capture("v0510-app-page.png");
            check("removing apps clamps page and always retains the third page",()->{main(()->{apps.clear();activity.showPage(999);});require(current()==2&&activity.pageCount()==3&&grid().getAdapter().getCount()==0,"removed apps left stale page");});
            check("old homepage preference and backup migrate; new backups preserve order",()->{
                config.p.edit().remove("pageOrder055").putInt("homePage",2).commit();require(new Config(activity).n("homePage",-1)==0,"old minimal not migrated");String old="{\"format\":\"hydrogen-config\",\"version\":1,\"settings\":{\"homePage\":0}}";config.restore(old);require(config.n("homePage",-1)==1,"old card backup not migrated");config.put("homePage",2);String text=config.exportJson();config.restore(text);require(config.n("homePage",-1)==2,"new order changed");config.restore(new Config(activity).exportJson());
            });
            check("Chinese and English app labels are 追风桌面 / WindChaser",()->{Configuration cn=new Configuration(activity.getResources().getConfiguration());cn.locale=Locale.SIMPLIFIED_CHINESE;Configuration en=new Configuration(cn);en.locale=Locale.ENGLISH;require("追风桌面".equals(activity.createConfigurationContext(cn).getString(R.string.app_name)),"Chinese name wrong");require("WindChaser".equals(activity.createConfigurationContext(en).getString(R.string.app_name)),"English name wrong");});
            main(()->{config.put("musicMode",0);config.put("musicPackage",Config.MUSIC);activity.showPage(MainActivity.MINIMAL);});seedSong("追风桌面 · 歌词交互样例");capture("v0510-seek-minimal.png");main(()->activity.showPage(MainActivity.CARDS));capture("v0510-seek-card.png");
        }catch(Throwable e){failed++;report.append("FAIL fixture ").append(e).append('\n');}
        finally{if(activity!=null)main(activity::finish);savePrefs(config,saved);}
        report.append("WINDCHASER_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
