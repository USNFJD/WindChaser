package cn.hydrogen.launcher;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import org.json.*;

/** Page-entry regression with the production retained overlay and Java test surfaces. */
final class MapTimelineFlow {
    private final Instrumentation test;private final MapRetentionFlow h;
    MapTimelineFlow(Instrumentation test){this.test=test;h=new MapRetentionFlow(test);}
    private void transition(int source,boolean refresh)throws Exception{
        h.cfg.put("pageAnimation",false);h.page(source);h.parked();
        JSONObject before=h.state();int plans=before.optInt("timeline_plans"),starts=(Integer)h.field(h.activity,"pageAnimationStarts");
        h.cfg.put("pageAnimation",true);
        h.main(()->{try{
            h.activity.showPage(MainActivity.CARDS);View home=(View)h.field(h.activity,"home");
            Object animation=h.field(h.activity,"pageAnimator");
            h.require(animation!=null&&h.field(h.activity,"animatedPage")==home,"entry did not start immediately");
            h.require(home.getTranslationX()==0,"second page entered with a lateral offset");
            h.require(((android.animation.ValueAnimator)animation).getDuration()==120,"wrong fade duration");
            if(refresh){float alpha=home.getAlpha();for(int i=0;i<4;i++)h.activity.showPage(MainActivity.CARDS);
                h.require(h.field(h.activity,"pageAnimator")==animation,"same-page refresh replaced animation");
                h.require(home.getAlpha()==alpha,"same-page refresh snapped the fade");}
        }catch(Exception error){throw new RuntimeException(error);}});
        h.await("entry completed",()->h.field(h.activity,"animatedPage")==null);h.card();
        View home=(View)h.field(h.activity,"home");int[] point=new int[2];h.main(()->home.getLocationOnScreen(point));
        Thread.sleep(650);h.main(()->{int[] after=new int[2];home.getLocationOnScreen(after);h.require(home.getTranslationX()==0&&home.getAlpha()==1,"late page transform");h.require(point[0]==after[0]&&point[1]==after[1],"page moved after entry completed");});
        h.require((Integer)h.field(h.activity,"pageAnimationStarts")==starts+1,"entry ran more than once");
        JSONObject after=h.state();h.require(after.optInt("timeline_plans")==plans,"a delayed map plan moved the page");h.require(!after.optBoolean("moving"),"map left untouchable");h.same(before,after);
    }
    void run(){try{
        h.cfg=new Config(test.getTargetContext());h.backup=h.cfg.exportJson();h.cfg.put("autoRecoverApps",false);h.cfg.put("homePage",MainActivity.CARDS);h.cfg.put("pageAnimation",false);
        h.external(com.autonavi.amapauto.MainMapActivity.class);h.await("fixture resumed",()->!h.state().optBoolean("paused")&&h.state().optInt("ui_width")>0);Thread.sleep(600);
        h.activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(600);h.installTransport();
        h.check("first-to-second enters in place once",()->transition(0,false));
        h.check("third-to-second enters in place once",()->transition(2,false));
        h.check("same-page refresh cannot cancel or replay entry",()->transition(0,true));
        h.check("retained surfaces survive repeated page entries",()->{transition(0,false);transition(2,false);transition(0,false);});
        h.check("late map readiness does not delay or replay page entry",()->{
            h.cfg.put("pageAnimation",false);h.page(0);h.parked();int starts=(Integer)h.field(h.activity,"pageAnimationStarts");h.cfg.put("pageAnimation",true);
            h.main(()->{try{h.set(h.controller,"windowLayoutReady",false);h.set(h.controller,"timelineCapable",false);h.activity.showPage(1);h.require(h.field(h.activity,"pageAnimator")!=null,"entry waited for map readiness");}catch(Exception error){throw new RuntimeException(error);}});
            h.await("fade completed",()->h.field(h.activity,"animatedPage")==null);h.main(()->h.controller.refreshWorkspace());h.card();Thread.sleep(650);h.require((Integer)h.field(h.activity,"pageAnimationStarts")==starts+1,"readiness replayed the entry");
        });
        h.check("leaving mid-entry cancels the fade and parks map untouchably",()->{
            h.cfg.put("pageAnimation",false);h.page(0);h.parked();h.cfg.put("pageAnimation",true);h.main(()->{h.activity.showPage(1);h.activity.showPage(2);});h.parked();Thread.sleep(400);h.parked();h.cfg.put("pageAnimation",false);h.page(1);h.card();
        });
        h.check("full-screen camera still removes the retained overlay during entry",()->{
            h.cfg.put("pageAnimation",false);h.page(0);h.parked();h.cfg.put("pageAnimation",true);h.page(1);h.external(ReverseCameraActivity.class);h.await("camera cleanup",()->!h.state().optBoolean("bridge_added"));h.cfg.put("pageAnimation",false);h.returnHome();
        });
    }catch(Throwable error){h.failed++;h.report.append("FAIL setup ").append(error).append('\n');}
    finally{if(h.activity!=null&&!h.activity.isFinishing())h.main(h.activity::finish);if(h.backup!=null)try{h.cfg.restore(h.backup);}catch(Exception ignored){}}
    h.report.append("PAGE_ENTRY_RESULT passed=").append(h.passed).append(" failed=").append(h.failed).append('\n');Bundle result=new Bundle();result.putString("stream",h.report.toString());h.responses.quit();test.finish(h.failed==0?-1:0,result);
    }
}
