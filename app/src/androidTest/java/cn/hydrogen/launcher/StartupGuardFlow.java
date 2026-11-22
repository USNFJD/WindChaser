package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Real lifecycle, external test Activity and independent Binder-death checks. */
final class StartupGuardFlow {
    private final Instrumentation test;private final Context context;private MainActivity activity;
    private int passed,failed;private final StringBuilder report=new StringBuilder();
    interface Task{void run()throws Exception;}
    StartupGuardFlow(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private void check(String title,Task task){try{task.run();passed++;report.append("PASS ");}catch(Throwable e){failed++;report.append("FAIL ").append(e).append(" ");}report.append(title).append('\n');}
    private void main(Runnable task){Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});test.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private Object serviceField(String name)throws Exception{Field active=StartupSequenceService.class.getDeclaredField("running");active.setAccessible(true);Object service=active.get(null);if(service==null)return null;Field f=StartupSequenceService.class.getDeclaredField(name);f.setAccessible(true);return f.get(service);}
    private void enableStartup(Config cfg,String map)throws Exception{
        cfg.put("musicPackage",test.getContext().getPackageName());cfg.put("autoRecoverApps",true);Field attempted=StartupApps.class.getDeclaredField("attempted");attempted.setAccessible(true);attempted.setBoolean(null,false);
        main(()->{try{((StartupApps)field("startupApps")).resume();}catch(Exception e){throw new RuntimeException(e);}});
        for(int n=0;n<100&&serviceField("targets")==null;n++)Thread.sleep(20);
        // Test-only second Activity: production map selection is now fixed to 6.5.
        // Insert an external Settings target into the existing schedule, without
        // adding any runtime override or package bypass to the shipped launcher.
        main(()->{try{java.util.List targets=(java.util.List)serviceField("targets");require(targets!=null&&targets.size()==1,"expected one music fixture");Class<?> type=Class.forName("cn.hydrogen.launcher.StartupSequenceService$Target");Constructor<?> ctor=type.getDeclaredConstructor(Intent.class,long.class,boolean.class,int.class);ctor.setAccessible(true);targets.add(ctor.newInstance(context.getPackageManager().getLaunchIntentForPackage(map).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION),StartupSequenceService.MAP_DELAY,false,context.getPackageManager().getApplicationInfo(map,0).uid));}catch(Exception e){throw new RuntimeException(e);}});
    }
    private void waitStopped()throws Exception{for(int n=0;n<100&&StartupSequenceService.active();n++)Thread.sleep(100);require(!StartupSequenceService.active(),"startup service still active");}
    private JSONObject guardState()throws Exception{try(InputStream in=new FileInputStream(new File(context.getFilesDir(),"map-window-guard.json"))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}}
    private JSONObject await(String reason,int requests)throws Exception{for(int i=0;i<70;i++){try{JSONObject state=guardState();if(state.getString("reason").equals(reason)&&state.getInt("close_requests")>=requests)return state;}catch(Exception ignored){}Thread.sleep(100);}throw new AssertionError("guard timeout "+reason+" "+guardState());}
    private final class Link implements ServiceConnection {
        final CountDownLatch ready=new CountDownLatch(1);Messenger messenger;boolean bound;
        void bind(Intent intent){main(()->{bound=context.bindService(intent,this,Context.BIND_AUTO_CREATE);require(bound,"bind failed");});try{require(ready.await(4,TimeUnit.SECONDS),"bind timeout");}catch(InterruptedException e){throw new AssertionError(e);}}
        public void onServiceConnected(ComponentName name,IBinder binder){messenger=new Messenger(binder);ready.countDown();}
        public void onServiceDisconnected(ComponentName name){}
        void close(){if(bound){main(()->context.unbindService(this));bound=false;}}
    }
    private void send(Messenger service,int command,Messenger owner)throws Exception{Message message=Message.obtain(null,command);message.replyTo=owner;service.send(message);}
    private void restore(Config cfg,Map<String,?> values){SharedPreferences.Editor e=cfg.p.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Float)e.putFloat(item.getKey(),(Float)v);}e.commit();}
    void run(){
        Config cfg=new Config(context);Map<String,?> saved=new HashMap<>(cfg.p.getAll());Link guard=new Link(),token=new Link();Activity[] external={null};
        try{
            cfg.put("autoRecoverApps",false);cfg.put("quickLayout","");cfg.put("quickSlot0","");cfg.put("quickSlot1","");cfg.put("homePage",0);
            activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(900);
            check("startup scans installed apps once",()->require((Integer)field("appScanCount")==1,"duplicate scans "+field("appScanCount")));
            check("returning from settings reuses layout, wallpaper and application list",()->{
                Object wallpaper=field("wallpaper");int renders=(Integer)field("renderCount"),scans=(Integer)field("appScanCount");
                Activity settings=test.startActivitySync(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));main(settings::finish);Thread.sleep(350);
                require(field("wallpaper")==wallpaper&&(Integer)field("renderCount")==renders&&(Integer)field("appScanCount")==scans,"resume reloaded resources");
            });
            check("song cache changes do not rebuild UI; visual settings still refresh",()->{
                int renders=(Integer)field("renderCount");Object wallpaper=field("wallpaper");cfg.put("qqLastTitle","cache-only");
                Activity settings=test.startActivitySync(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));main(settings::finish);Thread.sleep(250);require((Integer)field("renderCount")==renders,"cache rebuilt UI");
                settings=test.startActivitySync(new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));cfg.put("radius",cfg.n("radius",20)==25?24:25);Activity selected=settings;main(selected::finish);Thread.sleep(300);
                require((Integer)field("renderCount")==renders+1&&field("wallpaper")==wallpaper,"visual setting failed/shared wallpaper lost");
            });
            check("default shortcut grid is exactly five rows and two columns",()->{List<QuickGrid.Cell> cells=QuickGrid.cells(cfg);require(cells.size()==6,"tile count");for(QuickGrid.Cell cell:cells){if(cell.id.equals("clock"))require(cell.row==0&&cell.rows==2&&cell.columns==2,"clock");if(cell.id.equals("phone"))require(cell.row==2&&cell.columns==2,"phone");if(cell.id.startsWith("shortcut"))require(cell.row==3&&cell.columns==1,"empty slot");if(cell.id.equals("apps")||cell.id.equals("settings"))require(cell.row==4&&cell.columns==1,"bottom row");}});
            check("resized tiles occupy multiple rows without overlap and survive backup",()->{
                String layout=QuickGrid.resize(cfg,"clock",3,2);cfg.put("quickLayout",layout);boolean[][] occupied=new boolean[QuickGrid.ROWS][QuickGrid.COLUMNS];for(QuickGrid.Cell cell:QuickGrid.cells(cfg))for(int y=cell.row;y<cell.row+cell.rows;y++)for(int x=cell.column;x<cell.column+cell.columns;x++){require(!occupied[y][x],"overlap");occupied[y][x]=true;}
                String backup=cfg.exportJson();cfg.put("quickLayout","");cfg.restore(backup);require(cfg.s("quickLayout","").equals(layout),"layout lost");cfg.put("quickLayout","");
            });
            check("bound shortcuts cannot be silently displaced by oversized tiles",()->{cfg.put("quickSlot0","fixture/one");cfg.put("quickSlot1","fixture/two");boolean rejected=false;try{QuickGrid.resize(cfg,"clock",3,2);}catch(JSONException expected){rejected=true;}require(rejected,"bound app disappeared");cfg.put("quickSlot0","");cfg.put("quickSlot1","");});
            check("actual associated test application launches and quickly returns once",()->{
                cfg.put("musicPackage",test.getContext().getPackageName());cfg.put("mapPackage",test.getContext().getPackageName());cfg.put("autoRecoverApps",true);Field attempted=StartupApps.class.getDeclaredField("attempted");attempted.setAccessible(true);attempted.setBoolean(null,false);
                CountDownLatch started=new CountDownLatch(1);int[] launches={0};BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){launches[0]++;started.countDown();}};context.registerReceiver(receiver,new IntentFilter("cn.hydrogen.launcher.TEST_WARM_STARTED"));
                try{main(()->{activity.showPage(1);try{((StartupApps)field("startupApps")).resume();}catch(Exception e){throw new RuntimeException(e);}});require(started.await(4,TimeUnit.SECONDS),"external app not launched");Thread.sleep(2400);test.waitForIdleSync();require(activity.hasWindowFocus()&&(Integer)field("currentPage")==1,"return focus="+activity.hasWindowFocus()+" page="+field("currentPage")+" resumed="+field("resumed")+" finishing="+activity.isFinishing());require(launches[0]==1,"startup loop");}finally{context.unregisterReceiver(receiver);cfg.put("autoRecoverApps",false);}
            });
            check("map then music launch with 2350ms and 650ms dwell; visible HOME stops foreground work",()->{
                long[] started={0};BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){started[0]=SystemClock.uptimeMillis();}};context.registerReceiver(receiver,new IntentFilter("cn.hydrogen.launcher.TEST_WARM_STARTED"));
                try{enableStartup(cfg,"com.android.settings");for(int n=0;n<60&&!Integer.valueOf(1).equals(serviceField("index"));n++)Thread.sleep(50);
                    long mapObserved=SystemClock.uptimeMillis();require(Integer.valueOf(1).equals(serviceField("index")),"map fixture did not launch first");require(started[0]==0,"music launched before map dwell");
                    Thread.sleep(1700);require(StartupSequenceService.active()&&Boolean.FALSE.equals(serviceField("returning"))&&started[0]==0,"map stage ended too early");
                    for(int n=0;n<60&&started[0]==0;n++)Thread.sleep(50);require(started[0]>0,"music fixture did not launch second");require(started[0]-mapObserved>=2200,"2350ms map stage skipped");
                    waitStopped();test.waitForIdleSync();require(activity.hasWindowFocus(),"HOME not visible");require(SystemClock.uptimeMillis()-started[0]>=500,"650ms music stage skipped");
                    Thread.sleep(700);require(!StartupSequenceService.active()&&activity.hasWindowFocus(),"completed startup repeated");
                }finally{context.unregisterReceiver(receiver);cfg.put("autoRecoverApps",false);}
            });
            check("destroying and recreating background HOME does not cancel pending return",()->{
                Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
                try{enableStartup(cfg,"com.android.settings");for(int n=0;n<60&&!Integer.valueOf(1).equals(serviceField("index"));n++)Thread.sleep(50);require(StartupSequenceService.active(),"service not started");
                    MainActivity old=activity;main(old::recreate);
                    Activity replacement=test.waitForMonitorWithTimeout(monitor,7000);require(replacement instanceof MainActivity&&replacement!=old,"HOME not recreated");activity=(MainActivity)replacement;
                    waitStopped();Thread.sleep(250);test.waitForIdleSync();require(old.isDestroyed()&&activity.hasWindowFocus(),"destroyed HOME interrupted startup");
                }finally{test.removeMonitor(monitor);cfg.put("autoRecoverApps",false);}
            });
            check("manual HOME cancels startup and stale return tokens cannot acknowledge a running sequence",()->{
                enableStartup(cfg,"com.android.settings");for(int n=0;n<60&&!StartupSequenceService.active();n++)Thread.sleep(50);require(StartupSequenceService.active(),"no startup");
                main(()->StartupSequenceService.acknowledge(new Intent().putExtra(StartupApps.RETURN,true).putExtra(StartupApps.TOKEN,"stale")));require(StartupSequenceService.active(),"stale ACK stopped startup");
                main(()->activity.onNewIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));waitStopped();cfg.put("autoRecoverApps",false);
                Thread.sleep(2600);require(!StartupSequenceService.active(),"cancelled startup restarted");
                main(()->context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));Thread.sleep(150);
            });
            check("unchanged music state does not rewrite title artist or lyric TextViews",()->{
                int[] rewrites={0};main(()->{try{ArrayList<?> cards=(ArrayList<?>)field("musicCards");MusicCard card=(MusicCard)cards.get(0);
                    android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){rewrites[0]++;}public void afterTextChanged(android.text.Editable e){}};
                    Field title=MusicCard.class.getDeclaredField("title"),artist=MusicCard.class.getDeclaredField("artist"),lyrics=MusicCard.class.getDeclaredField("lyricRows");title.setAccessible(true);artist.setAccessible(true);lyrics.setAccessible(true);
                    ArrayList<android.widget.TextView> views=new ArrayList<>();views.add((android.widget.TextView)title.get(card));views.add((android.widget.TextView)artist.get(card));Collections.addAll(views,(android.widget.TextView[])lyrics.get(card));
                    card.update();for(android.widget.TextView v:views)v.addTextChangedListener(watcher);for(int n=0;n<10;n++)card.update();for(android.widget.TextView v:views)v.removeTextChangedListener(watcher);
                }catch(Exception e){throw new RuntimeException(e);}});require(rewrites[0]==0,"unchanged text rewritten "+rewrites[0]);
            });
            check("return retries stop after the finite limit when HOME launch is blocked",()->{
                Object[] service={null};Instrumentation.ActivityMonitor blocker=test.addMonitor(MainActivity.class.getName(),new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
                try{cfg.put("autoRecoverApps",true);cfg.put("musicPackage",test.getContext().getPackageName());cfg.put("mapPackage",test.getContext().getPackageName());
                    main(()->context.startService(new Intent(context,StartupSequenceService.class)));
                    for(int n=0;n<40&&service[0]==null;n++){Field f=StartupSequenceService.class.getDeclaredField("running");f.setAccessible(true);service[0]=f.get(null);if(service[0]==null)Thread.sleep(50);}
                    require(service[0]!=null,"service missing");waitStopped();Field tries=StartupSequenceService.class.getDeclaredField("attempts");tries.setAccessible(true);require(tries.getInt(service[0])==7&&blocker.getHits()==7,"return not bounded: "+tries.getInt(service[0])+" hits="+blocker.getHits());
                }finally{test.removeMonitor(blocker);cfg.put("autoRecoverApps",false);main(()->context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));Thread.sleep(150);}
            });
            guard.bind(new Intent(context,MapWindowGuardService.class));token.bind(new Intent().setComponent(new ComponentName(test.getContext().getPackageName(),GuardTokenService.class.getName())));
            check("map guard runs in an independent process and arms a remote Binder",()->{send(guard.messenger,MapWindowGuardService.ARM,token.messenger);JSONObject state=await("armed",0);require(state.getInt("pid")!=android.os.Process.myPid(),"same process");});
            check("stale owner cannot close a new guarded map",()->{Messenger stale=new Messenger(new Handler(Looper.getMainLooper()));send(guard.messenger,MapWindowGuardService.CLOSE,stale);Thread.sleep(180);require(guardState().getString("reason").equals("armed"),"stale close accepted");});
            check("actual remote process death triggers independent map cleanup",()->{token.messenger.send(Message.obtain(null,77));await("owner-died",1);});
            check("cleanup retries are bounded and stop after six requests",()->{await("owner-died",6);Thread.sleep(350);require(guardState().getInt("close_requests")==6,"unbounded cleanup");});
            check("new owner cancels old cleanup; unbinding closes independently",()->{Messenger owner=new Messenger(new Handler(Looper.getMainLooper()));send(guard.messenger,MapWindowGuardService.ARM,owner);await("armed",0);Thread.sleep(220);require(guardState().getString("reason").equals("armed"),"old sweep continued");guard.close();await("host-unbound",1);});
        }catch(Throwable e){failed++;report.append("FAIL fixture ").append(e).append('\n');}
        finally{guard.close();token.close();context.stopService(new Intent(context,MapWindowGuardService.class));if(external[0]!=null)main(external[0]::finish);if(activity!=null)main(activity::finish);restore(cfg,saved);}
        report.append("STARTUP_GUARD_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
