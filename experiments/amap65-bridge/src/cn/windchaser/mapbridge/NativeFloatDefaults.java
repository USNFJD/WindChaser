package cn.windchaser.mapbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

/** One-time native information-float default. Runs before original Application.onCreate.
 * Only the actual persisted floating-window property is changed; other native options
 * and later manual choices remain under the original settings implementation.
 */
public final class NativeFloatDefaults {
    private static final String PREFS="windchaser-map-defaults";
    private static final String KEY="native-info-float-default-off-revision";
    private static final String PROPERTY="isOpenFloatingWindow";
    private static final int REVISION=2, MAX_CONFIG_BYTES=1024*1024;
    private static final Charset UTF16=Charset.forName("UTF-16LE");
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static Context app;
    private static boolean done, ready;
    private static int checks;
    private static final Runnable VERIFY=new Runnable(){@Override public void run(){verify();}};
    private NativeFloatDefaults(){}

    public static void install(Context context){
        if(app!=null||context==null||!"com.autonavi.amapauto".equals(context.getPackageName()))return;
        app=context.getApplicationContext();done=preferences().getInt(KEY,0)>=REVISION;
        if(done)return;
        try{
            // MapCardBridge installs before the original native Application.onCreate.
            // Prepare the stored default before GFrame reads its initial settings.
            AtomicFile file=config();byte[] before=read(file),after=closedConfig(before);
            if(!Arrays.equals(before,after)){
                FileOutputStream stream=null;
                try{stream=file.startWrite();stream.write(after);file.finishWrite(stream);stream=null;}
                finally{if(stream!=null)file.failWrite(stream);}
            }
            if(!isClosed(read(file)))throw new IOException("Native float setting did not persist");
            // Do not mark complete merely because a void JNI request returned.
            Log.i("WindChaserMap","Native float default prepared before native initialization; awaiting verified ready state");
        }catch(Exception unavailable){Log.w("WindChaserMap","Native float default preparation failed; original application continues",unavailable);}
    }
    private static SharedPreferences preferences(){return app.getSharedPreferences(PREFS,0);}
    private static AtomicFile config(){return new AtomicFile(new File(app.getFilesDir(),"uiconfig.data"));}
    private static byte[] read(AtomicFile file)throws IOException{
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile().getPath()+".bak").exists())return new byte[0];
        FileInputStream input=file.openRead();
        try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=input.read(buffer))!=-1){if(out.size()+n>MAX_CONFIG_BYTES)throw new IOException("Native config too large");out.write(buffer,0,n);}return out.toByteArray();
        }finally{input.close();}
    }
    static byte[] closedConfig(byte[] original)throws IOException{
        if(original.length>MAX_CONFIG_BYTES||(original.length&1)!=0)throw new IOException("Invalid native config encoding/length");
        String text=new String(original,UTF16),prefix=PROPERTY+"[";int start=text.indexOf(prefix);
        if(start>=0){
            int value=start+prefix.length(),end=text.indexOf(']',value);
            if(end!=value+1||!(text.charAt(value)=='0'||text.charAt(value)=='1')||text.indexOf(prefix,value)>=0)throw new IOException("Ambiguous native float setting");
            if(text.charAt(value)=='0')return original;
            return (text.substring(0,value)+"0"+text.substring(value+1)).getBytes(UTF16);
        }
        int end=text.length();while(end>0&&text.charAt(end-1)==0)end--;
        return (text.substring(0,end)+prefix+"0]"+(end==text.length()?String.valueOf((char)0):text.substring(end))).getBytes(UTF16);
    }
    private static boolean isClosed(byte[] bytes)throws IOException{
        if(bytes.length==0)return false;
        return Arrays.equals(bytes,closedConfig(bytes))&&new String(bytes,UTF16).contains(PROPERTY+"[0]");
    }
    /** Called only by the existing native HMI-ready callback, never readiness queries. */
    public static void onReady(boolean value){
        ready=value;MAIN.removeCallbacks(VERIFY);
        if(ready&&app!=null&&!done){checks=0;MAIN.post(VERIFY);}
    }
    private static void verify(){
        if(!ready||done||app==null)return;
        checks++;
        try{
            if(isClosed(read(config()))){
                done=preferences().edit().putInt(KEY,REVISION).commit();
                if(done){Log.i("WindChaserMap","Native float saved off verified after HMI ready; revision 2 complete");return;}
            }else{
                // Failure recovery uses the original setting handler, never changes
                // unrelated native settings or the launcher card preference.
                Class<?> original=Class.forName("com.autonavi.autofloat.FloatWindowManagerNative");
                original.getMethod("nativeCloseFloatWidnowSetting").invoke(null);
            }
        }catch(Exception|LinkageError unavailable){Log.w("WindChaserMap","Native float state unconfirmed; migration remains pending",unavailable);}
        if(checks<6&&ready)MAIN.postDelayed(VERIFY,250);
    }
}
