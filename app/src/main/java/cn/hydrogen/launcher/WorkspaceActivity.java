package cn.hydrogen.launcher;
import android.app.Activity;import android.content.Intent;import android.os.Bundle;
/** Explicit internal navigation parks the map; external launches withdraw it. */
public class WorkspaceActivity extends Activity {
 @Override public void startActivityForResult(Intent intent,int request,Bundle options){MapWorkspace workspace=MapWorkspace.get(this);workspace.beforeLaunch(this,intent);try{super.startActivityForResult(intent,request,options);}catch(RuntimeException e){workspace.launchFailed();throw e;}}
 @Override public void finish(){MapWorkspace.get(this).beforeFinish(this);super.finish();}
 @Override public void finishAffinity(){MapWorkspace.get(this).close();super.finishAffinity();}
 @Override public void recreate(){MapWorkspace.get(this).recreate(this);super.recreate();}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);MapWorkspace.get(this).focus(this,focus);}
 @Override protected void onUserLeaveHint(){MapWorkspace.get(this).userLeave();super.onUserLeaveHint();}
}
