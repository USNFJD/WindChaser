package android.content;
public class Context {public android.content.res.Resources resources=new android.content.res.Resources();public Intent launched,broadcast;public android.content.res.Resources getResources(){return resources;}public String getPackageName(){return "com.tencent.qqmusiccar";}public void startActivity(Intent i){launched=i;}public void sendBroadcast(Intent i){broadcast=i;}}
