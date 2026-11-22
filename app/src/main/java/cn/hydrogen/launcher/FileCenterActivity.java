package cn.hydrogen.launcher;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Small shared-storage browser plus a dedicated LAN transfer folder. */
public class FileCenterActivity extends WorkspaceActivity {
    private WallpaperLayer defaultBackground;
    static final String PICK="windchaser.pick", MIME="windchaser.mime";private boolean picker;private String pickType;private File pendingInstall;
    private int mode;private File root,folder,clipboard,clipboardRoot;private boolean cut,busy,registered;private float unit;
    private LinearLayout body;private TextView location,status;private EditText port;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private int generation;
    private final BroadcastReceiver changes=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(mode==2)build();}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);picker=getIntent().getBooleanExtra(PICK,false);pickType=getIntent().getStringExtra(MIME);mode=saved==null?(getIntent().getBooleanExtra("transfer",false)?2:0):saved.getInt("mode",0);root=StorageFiles.root(this);folder=saved==null?root:new File(saved.getString("folder",root.toString()));if(saved!=null&&mode==1)try{root=StorageFiles.rootFor(this,folder);}catch(IOException ignored){folder=root;}if(picker&&saved==null){mode=1;try{folder=StorageFiles.transfer(this);root=StorageFiles.rootFor(this,folder);}catch(IOException ignored){folder=root;}}build();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("mode",mode);out.putString("folder",folder.toString());}
    private int d(float n){return Math.round(n*unit);}private TextView text(String value,int size,int color){return Ui.text(this,value,size*unit/getResources().getDisplayMetrics().scaledDensity,color);}
    private View button(String value,Runnable action){TextView view=text(value,16,0xffd7ecee);view.setGravity(Gravity.CENTER);view.setPadding(d(12),d(8),d(12),d(8));view.setBackground(ThemeStyle.panel(this,"files",0x80506b7c,d(12),d(1),0x305e8997));view.setOnClickListener(v->{if(!busy)action.run();});view.setContentDescription(value);return view;}
    private boolean permitted(){return Build.VERSION.SDK_INT<23||Build.VERSION.SDK_INT>=33||checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    private void enter(int selected){mode=selected;if(!permitted()){requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},601);build();return;}try{root=StorageFiles.root(this);folder=mode==2||picker?StorageFiles.transfer(this):root;}catch(Exception e){Ui.toast(this,e.getMessage());}build();}
    private void chooseStorage(){List<File> roots=StorageFiles.roots(this);String[] names=new String[roots.size()];for(int n=0;n<roots.size();n++){File at=roots.get(n);names[n]=(same(at,StorageFiles.root(this))?"内部共享存储":at.getName())+"\n"+at;}new ThemeDialog.Builder(this).setTitle("存储设备 · 内部存储 / SD卡 / U盘").setItems(names,(dialog,n)->{root=roots.get(n);folder=root;build();}).setNegativeButton("返回",null).show();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==601){if(permitted())enter(mode);else{Ui.toast(this,"允许存储权限后才能管理文件和接收上传");build();}}}
    private void build(){
        unit=Math.max(.6f,Math.min(getResources().getDisplayMetrics().widthPixels/1280f,getResources().getDisplayMetrics().heightPixels/720f));
        FrameLayout backdrop=new FrameLayout(this);defaultBackground=WallpaperLayer.defaultBackground(this,defaultBackground);WallpaperLayer picture=defaultBackground;picture.setContentDescription("文件中心默认背景");backdrop.addView(picture,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackgroundColor(0x88030e1c);backdrop.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=Ui.column(this);content.setPadding(d(30),d(22),d(30),d(22));backdrop.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(button("‹ 返回",this::back),new LinearLayout.LayoutParams(d(110),d(48)));TextView title=text(picker?("video/mp4".equals(pickType)?"选择背景视频":"image/*".equals(pickType)?"选择背景图片":"选择文件"):mode==0?"文件中心":mode==1?"文件管理":"文件传输",28,Ui.WHITE);title.setPadding(d(24),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,d(60),1));header.addView(text("WindChaser",16,0xffa5c9d6),new LinearLayout.LayoutParams(d(140),d(60)));content.addView(header,new LinearLayout.LayoutParams(-1,d(70)));
        if(mode==0){
            LinearLayout tiles=new LinearLayout(this);tiles.setGravity(Gravity.CENTER);String[] names={"文件管理","文件传输"},detail={"复制、剪切、重命名、删除与打开文件","手机通过 HTTP 或 HTTPS 上传到车机"};
            for(int n=0;n<2;n++){final int selected=n+1;LinearLayout tile=Ui.column(this);tile.setGravity(Gravity.CENTER);tile.setPadding(d(24),d(24),d(24),d(24));tile.setBackground(ThemeStyle.panel(this,"files",0x80516e83,d(30),d(1),0x506dabb8));ImageView icon=new ImageView(this);icon.setImageResource(n==0?R.drawable.ic_file_manager:R.drawable.ic_file_transfer);tile.addView(icon,new LinearLayout.LayoutParams(d(88),d(88)));TextView label=text(names[n],32,Ui.WHITE);label.setGravity(Gravity.CENTER);tile.addView(label,new LinearLayout.LayoutParams(-1,d(72)));TextView sub=text(detail[n],17,0xffb4ccd8);sub.setGravity(Gravity.CENTER);tile.addView(sub,new LinearLayout.LayoutParams(-1,d(60)));tile.setContentDescription(names[n]);tile.setOnClickListener(v->enter(selected));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,d(320),1);p.leftMargin=d(18);p.rightMargin=d(18);tiles.addView(tile,p);}content.addView(tiles,new LinearLayout.LayoutParams(-1,0,1));
        }else{
            if(!permitted()){content.addView(text("需要存储权限才能浏览共享文件。",20,Ui.WHITE));content.addView(button("允许存储权限",()->enter(mode)));ThemeStyle.apply(backdrop,"filesText");setContentView(backdrop);Ui.fullscreen(this);return;}
            if(mode==2){try{File received=StorageFiles.transfer(this);if(!StorageFiles.within(received,folder)||!folder.isDirectory())folder=received;}catch(Exception e){content.addView(text(e.getMessage(),18,Ui.WHITE));ThemeStyle.apply(backdrop,"filesText");setContentView(backdrop);return;}transferControls(content);}
            LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.addView(button(mode==2?"刷新":"上一级",()->{if(mode==1&&folder.getParentFile()!=null&& !same(folder,root))folder=folder.getParentFile();build();}),new LinearLayout.LayoutParams(d(100),d(44)));
            if(mode==1){LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(d(110),d(44));sp.leftMargin=d(8);actions.addView(button("存储设备",this::chooseStorage),sp);}
            location=text(folder.toString(),14,0xffb2ceda);location.setSingleLine();location.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);location.setPadding(d(14),0,d(12),0);actions.addView(location,new LinearLayout.LayoutParams(0,d(50),1));
            if(mode==1&&!picker){actions.addView(button("新建文件夹",this::newFolder),new LinearLayout.LayoutParams(d(125),d(44)));View paste=button(clipboard==null?"粘贴":"粘贴 · "+(cut?"剪切":"复制"),this::paste);paste.setEnabled(clipboard!=null&&!busy);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(d(138),d(44));pp.leftMargin=d(10);actions.addView(paste,pp);}if(picker)actions.addView(button("传输目录",()->{try{folder=StorageFiles.transfer(this);root=StorageFiles.rootFor(this,folder);build();}catch(IOException e){Ui.toast(this,e.getMessage());}}),new LinearLayout.LayoutParams(d(118),d(44)));content.addView(actions,new LinearLayout.LayoutParams(-1,d(64)));
            ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackground(ThemeStyle.panel(this,"files",0x78102032,d(20),d(1),0x3051778a));body=Ui.column(this);body.setPadding(d(18),d(8),d(18),d(16));scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));load();
            TextView hint=text(busy?"正在处理文件，请稍候…":mode==1?"浏览全部共享文件；存储设备可切换 SD卡 / U盘。长按可复制、剪切、重命名、删除或查看信息。":"本页仅显示文件传输接收目录；点击打开，长按可操作。",13,0xffa3c0cc);content.addView(hint,new LinearLayout.LayoutParams(-1,d(32)));
        }
        ThemeStyle.apply(backdrop,"filesText");setContentView(backdrop);Ui.fullscreen(this);
    }
    private void transferControls(LinearLayout content){
        LinearLayout panel=Ui.column(this);panel.setPadding(d(18),d(12),d(18),d(12));panel.setBackground(ThemeStyle.panel(this,"files",0x90506b7c,d(18),0,0));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);status=text(FileTransferService.state,17,Ui.WHITE);row.addView(status,new LinearLayout.LayoutParams(0,d(42),1));
        port=new EditText(this);port.setText(String.valueOf(FileTransferService.running?FileTransferService.port:FileTransferService.preferredPort(this)));port.setTextSize(16*unit/getResources().getDisplayMetrics().scaledDensity);port.setTextColor(Ui.WHITE);port.setInputType(InputType.TYPE_CLASS_NUMBER);port.setEnabled(!FileTransferService.running&&!FileTransferService.starting);port.setContentDescription("传输端口");row.addView(port,new LinearLayout.LayoutParams(d(90),d(48)));
        View toggle=button(FileTransferService.running||FileTransferService.starting?"关闭传输":"开启传输",this::toggleServer);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(d(160),d(44));tp.leftMargin=d(14);row.addView(toggle,tp);panel.addView(row);
        List<String> urls=FileTransferService.running?FileTransferService.addresses():Collections.emptyList();String address=urls.isEmpty()?"连接手机热点并开启传输后，这里显示 HTTP 和 HTTPS 地址。":join(urls);TextView url=text(address,20,0xffd1f1e9);url.setSingleLine(true);url.setTextIsSelectable(true);url.setContentDescription("文件传输地址");url.setOnLongClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("文件传输地址",address));Ui.toast(this,"地址已复制");return true;});HorizontalScrollView addressLine=new HorizontalScrollView(this);addressLine.setHorizontalScrollBarEnabled(false);addressLine.setFillViewport(true);addressLine.addView(url,new FrameLayout.LayoutParams(-2,-2));panel.addView(addressLine,new LinearLayout.LayoutParams(-1,-2));
        TextView code=text("访问码："+TransferServer.accessCode(this),19,Ui.WHITE);code.setTextIsSelectable(true);code.setContentDescription("传输访问码");panel.addView(code,new LinearLayout.LayoutParams(-1,d(36)));
        TextView note=text("HTTP 和 HTTPS 共用一个端口，默认 8888、访问码默认 123456，每批最多 200 个文件，单文件最大 300 MB。HTTPS 首次访问需确认本机证书；HTTP 不加密内容。IP 随热点分配。",13,0xffb1ced8);note.setPadding(0,d(5),0,0);panel.addView(note);content.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    }
    private String join(List<String> values){StringBuilder s=new StringBuilder();for(String value:values){if(s.length()>0)s.append("  " );s.append(value);}return s.toString();}
    private void toggleServer(){if(FileTransferService.running||FileTransferService.starting){stopService(new Intent(this,FileTransferService.class));build();return;}try{int number=Integer.parseInt(port.getText().toString());if(number<1024||number>65535)throw new NumberFormatException();Intent intent=new Intent(this,FileTransferService.class).putExtra("port",number);if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);}catch(Exception e){Ui.toast(this,"请输入 1024 到 65535 的可用端口");}}
    private void load(){final int request=++generation;File at=folder;LinearLayout target=body;worker.execute(()->{try{File[] entries=StorageFiles.list(root,at);runOnUiThread(()->{if(isFinishing()||request!=generation)return;if(entries.length==0)target.addView(text("这个文件夹还没有文件",18,Ui.MUTED),new LinearLayout.LayoutParams(-1,d(80)));for(File file:entries){if(file.getName().startsWith(".upload-")||(picker&&!selectable(file)))continue;LinearLayout row=Ui.column(this);row.setPadding(d(14),d(10),d(14),d(10));TextView name=text((file.isDirectory()?"▣  ":"◇  ")+file.getName(),20,Ui.WHITE);name.setSingleLine(false);name.setEllipsize(null);name.setMinHeight(d(32));name.setContentDescription("完整文件名："+file.getName());row.addView(name,new LinearLayout.LayoutParams(-1,-2));row.addView(text((file.isDirectory()?"文件夹":StorageFiles.size(file.length()))+"  ·  "+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(file.lastModified())),13,0xff9fbfcf),new LinearLayout.LayoutParams(-1,d(24)));row.setContentDescription(file.getName());row.setOnClickListener(v->{if(busy)return;if(file.isDirectory()){folder=file;build();}else open(file);});row.setOnLongClickListener(v->{if(!busy&&!picker)menu(file);return true;});ThemeStyle.apply(row,"filesText");target.addView(row,new LinearLayout.LayoutParams(-1,-2));}});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&request==generation)target.addView(text("读取失败："+e.getMessage(),17,Ui.MUTED));});}});}
    private boolean selectable(File file){if(file.isDirectory())return true;String mime=StorageFiles.mime(file);return pickType==null||"*/*".equals(pickType)||mime.equals(pickType)||pickType.endsWith("/*")&&mime.startsWith(pickType.substring(0,pickType.length()-1));}
    private void open(File file){
        if(picker){try{android.net.Uri uri=LocalFileProvider.uri(this,file);setResult(RESULT_OK,new Intent().setDataAndType(uri,StorageFiles.mime(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));finish();}catch(IOException e){Ui.toast(this,e.getMessage());}return;}
        if(FileOpen.apk(file)&&Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            pendingInstall=file;try{startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,android.net.Uri.parse("package:"+getPackageName())),602);}catch(ActivityNotFoundException e){Ui.toast(this,"请在系统设置中允许追风桌面安装应用");}return;
        }
        FileOpen.open(this,file,false);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==602&&pendingInstall!=null){File f=pendingInstall;pendingInstall=null;if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())open(f);else Ui.toast(this,"尚未允许安装应用");}}
    private void menu(File file){
        String[] names=file.isDirectory()?new String[]{"复制","剪切","删除","重命名","复制路径","文件信息"}:new String[]{"复制","剪切","删除","重命名","复制路径","文件信息","选择应用打开"};
        new ThemeDialog.Builder(this).setTitle(file.getName()).setItems(names,(dialog,which)->{
            if(which<2){clipboard=file;clipboardRoot=root;cut=which==1;Ui.toast(this,(cut?"已剪切：":"已复制：")+file.getName()+"，进入目标文件夹后点粘贴");if(mode==2)mode=1;build();}
            else if(which==2)new ThemeDialog.Builder(this).setTitle("删除文件").setMessage("确定删除“"+file.getName()+"”？此操作不可撤销。").setPositiveButton("删除",(d,n)->operate(()->StorageFiles.delete(root,file,0))).setNegativeButton("取消",null).show();
            else if(which==3)rename(file);
            else if(which==4){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("文件路径",file.getAbsolutePath()));Ui.toast(this,"完整路径已复制");}
            else if(which==5)new ThemeDialog.Builder(this).setTitle("文件信息").setMessage("名称："+file.getName()+"\n路径："+file.getAbsolutePath()+"\n类型："+(file.isDirectory()?"文件夹":StorageFiles.mime(file))+"\n"+(file.isDirectory()?"":"大小："+StorageFiles.size(file.length())+"（"+file.length()+" 字节）\n")+"修改时间："+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date(file.lastModified()))+"\n可写："+(file.canWrite()?"是":"否")).setPositiveButton("关闭",null).show();
            else if(which==6)FileOpen.open(this,file,true);
        }).setNegativeButton("取消",null).show();
    }
    private void rename(File file){
        EditText input=new EditText(this);input.setSingleLine();input.setText(file.getName());input.setSelectAllOnFocus(true);input.setContentDescription("完整文件名，含后缀");
        LinearLayout form=Ui.column(this);form.setPadding(d(20),d(8),d(20),d(8));form.addView(input);form.addView(text("可同时修改后缀；改后缀不会转换文件格式。",14,Ui.MUTED));
        AlertDialog dialog=new ThemeDialog.Builder(this).setTitle("重命名").setView(form).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=input.getText().toString();
            if(name.trim().isEmpty()||name.equals(".")||name.equals("..")||name.matches(".*[/\\\\\\r\\n\\x00].*")||name.startsWith(".upload-")){input.setError("请输入有效的文件名，不含路径分隔符");return;}
            if(!name.equals(file.getName())&&new File(file.getParentFile(),name).exists()){input.setError("同名文件已存在");return;}
            Runnable save=()->{dialog.dismiss();File storage=root;operate(()->{File result=StorageFiles.rename(storage,file,name);if(clipboard!=null&&same(clipboard,file))clipboard=result;});};
            if(!file.isDirectory()&&!extension(file.getName()).equalsIgnoreCase(extension(name)))new ThemeDialog.Builder(this).setTitle("修改文件后缀？").setMessage("新名称："+name+"\n\n更改后缀可能导致应用无法识别文件，文件内容与格式不会改变。").setPositiveButton("仍然修改",(d,n)->save.run()).setNegativeButton("返回修改",null).show();else save.run();
        }));dialog.show();
    }
    private static String extension(String name){int dot=name.lastIndexOf('.');return dot>0?name.substring(dot):"";}
    private interface Operation{void run()throws Exception;}
    private void operate(Operation operation){if(busy)return;busy=true;build();worker.execute(()->{String result="操作完成";try{operation.run();}catch(Exception e){result="操作失败："+e.getMessage();}final String message=result;runOnUiThread(()->{busy=false;if(!isFinishing()){Ui.toast(this,message);build();}});});}
    private void paste(){if(clipboard==null)return;File source=clipboard,sourceRoot=clipboardRoot,dest=folder,targetRoot=root;boolean moving=cut;operate(()->{StorageFiles.paste(sourceRoot,source,targetRoot,dest,moving);if(moving){clipboard=null;clipboardRoot=null;}});}
    private void newFolder(){EditText name=new EditText(this);name.setSingleLine();new ThemeDialog.Builder(this).setTitle("新建文件夹").setView(name).setPositiveButton("创建",(dialog,n)->{String value=name.getText().toString().trim();File at=folder;operate(()->{File target=StorageFiles.unique(at,value);StorageFiles.checked(root,target);if(!target.mkdir())throw new IOException("无法创建文件夹");});}).setNegativeButton("取消",null).show();}
    private boolean same(File a,File b){try{return a.getCanonicalFile().equals(b.getCanonicalFile());}catch(Exception e){return a.equals(b);}}
    private void back(){if(busy)return;File boundary=root;if(mode==2)try{boundary=StorageFiles.transfer(this);}catch(IOException ignored){}if(mode!=0&&!same(folder,boundary)&&folder.getParentFile()!=null){folder=folder.getParentFile();build();}else if(picker){setResult(RESULT_CANCELED);finish();}else if(mode!=0){mode=0;build();}else finish();}
    @Override public void onBackPressed(){back();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("transfer",false))enter(2);}
    @Override protected void onResume(){super.onResume();if(!registered){IntentFilter filter=new IntentFilter(FileTransferService.CHANGED);if(Build.VERSION.SDK_INT>=33)registerReceiver(changes,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(changes,filter);registered=true;}if(mode==2)build();}
    @Override protected void onPause(){if(registered){unregisterReceiver(changes);registered=false;}super.onPause();}
    @Override protected void onDestroy(){if(defaultBackground!=null){defaultBackground.dispose();defaultBackground=null;}generation++;worker.shutdownNow();super.onDestroy();}
}
