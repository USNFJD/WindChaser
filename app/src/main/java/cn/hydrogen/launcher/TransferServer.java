package cn.hydrogen.launcher;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.json.*;

/** A single-port HTTP / HTTPS entry point, protected by a device PIN and per-run session cookies. */
final class TransferServer implements Closeable {
    static final long MAX_UPLOAD=300L*1024*1024;
    private final Context context;private final File folder;private final Runnable changed;
    private final Set<Socket> sockets=Collections.synchronizedSet(new HashSet<>());
    private final ThreadPoolExecutor clients=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private final Object writes=new Object();private volatile boolean closed;private ServerSocket listener;private SSLContext tls;
    final String token;
    private final String code;private int failedLogins;private long blockedUntil;
    TransferServer(Context context,File folder,Runnable changed){this.context=context;this.folder=folder;this.changed=changed;code=accessCode(context);byte[] bytes=new byte[18];new SecureRandom().nextBytes(bytes);token=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING);}
    static synchronized String accessCode(Context context){
        android.content.SharedPreferences prefs=FileTransferService.settings(context);String code=prefs.getString("accessCode","123456");
        if(!code.matches("[0-9]{6}")){code="123456";if(!prefs.edit().putString("accessCode",code).commit())throw new IllegalStateException("访问码保存失败");}return code;
    }
    void start(int port)throws Exception{tls=TransferIdentity.context(context);if(closed)throw new IOException("server closed");listener=new ServerSocket(port);if(closed){listener.close();throw new IOException("server closed");}new Thread(this::accept,"windchaser-transfer").start();}
    int port(){return listener.getLocalPort();}
    private void accept(){while(!closed)try{Socket socket=listener.accept();sockets.add(socket);try{clients.execute(()->handle(socket));}catch(RejectedExecutionException busy){sockets.remove(socket);socket.close();}}catch(IOException e){if(!closed)LauncherApp.record("文件传输连接失败",e);}}
    private void handle(Socket socket){
        try(Socket client=socket){client.setSoTimeout(30000);try(TransferConnection connection=new TransferConnection(client.getInputStream(),client.getOutputStream(),tls)){InputStream in=new BufferedInputStream(connection.input);OutputStream out=new BufferedOutputStream(connection.output);
            String first=line(in,4096);if(first==null)return;String[] request=first.split(" ");if(request.length!=3){send(out,400,"text/plain","无效请求");return;}
            Map<String,String> headers=new HashMap<>();int total=first.length();String header;while((header=line(in,4096))!=null&&!header.isEmpty()){total+=header.length();if(total>16384)throw new IOException("headers too large");int p=header.indexOf(':');if(p>0)headers.put(header.substring(0,p).toLowerCase(Locale.US),header.substring(p+1).trim());}
            URI uri=new URI(request[1]);String path=uri.getPath(),method=request[0];
            if(path==null||!path.startsWith("/")){send(out,400,"text/plain","无效地址");return;}
            String resource=path.substring(1);boolean authorized=authorized(headers.get("cookie"),connection.secure);
            if("POST".equals(method)&&resource.equals("login")){login(in,out,headers,connection.secure);return;}
            if("GET".equals(method)&&resource.isEmpty()){send(out,200,"text/html; charset=utf-8",authorized?page():loginPage(""));return;}
            if("GET".equals(method)&&resource.equals("background")){send(out,200,"image/jpeg",background());return;}
            if(!authorized){send(out,403,"text/plain","请打开首页并输入车机上的6位访问码");return;}
            if("GET".equals(method)&&resource.equals("list")){JSONArray list=new JSONArray();File[] files=folder.listFiles();if(files!=null)for(File f:files)if(f.isFile()&&!f.getName().startsWith(".upload-"))list.put(new JSONObject().put("name",f.getName()).put("size",StorageFiles.size(f.length())));send(out,200,"application/json; charset=utf-8",list.toString());return;}
            String name=parameter(uri.getRawQuery(),"name");
            if("PUT".equals(method)&&resource.equals("upload")){
                long length;try{length=Long.parseLong(headers.get("content-length"));}catch(Exception e){send(out,411,"text/plain","请使用浏览器选择文件上传");return;}
                if(length<0||length>MAX_UPLOAD){send(out,413,"text/plain","单个文件不能超过 300 MB");return;}
                if(folder.getUsableSpace()<length+1024*1024){send(out,507,"text/plain","车机剩余空间不足，请清理文件后重试");return;}
                // Inactivity timeout only: a continuous upload may run for as long as it needs.
                client.setSoTimeout(120000);
                try{name=uploadName(name);}catch(IOException invalid){send(out,400,"text/plain",invalid.getMessage());return;}File temp=File.createTempFile(".upload-",".part",folder);boolean done=false;
                try{try(FileOutputStream file=new FileOutputStream(temp)){byte[] buffer=new byte[65536];long left=length;while(left>0){if(closed)throw new IOException("server closed");int n=in.read(buffer,0,(int)Math.min(left,buffer.length));if(n<0)throw new EOFException("upload interrupted");file.write(buffer,0,n);left-=n;}file.getFD().sync();}
                    File target;synchronized(writes){target=StorageFiles.unique(folder,name);if(!temp.renameTo(target))throw new IOException("保存文件失败");}target.setReadable(true,false);done=true;changed.run();send(out,200,"application/json; charset=utf-8",new JSONObject().put("name",target.getName()).toString());
                }finally{if(!done)temp.delete();}return;
            }
            if("GET".equals(method)&&resource.equals("file")){
                try{name=uploadName(name);}catch(IOException invalid){send(out,400,"text/plain","无效文件名");return;}File file;try{file=StorageFiles.checked(folder,new File(folder,name));}catch(IOException invalid){send(out,403,"text/plain","无法访问此文件");return;}if(!file.isFile()||file.getName().startsWith(".upload-")){send(out,404,"text/plain","文件不存在");return;}
                head(out,200,StorageFiles.mime(file),file.length(),"Content-Disposition: attachment; filename*=UTF-8''"+URLEncoder.encode(name,"UTF-8").replace("+","%20")+"\r\n");try(InputStream data=new FileInputStream(file)){byte[] bytes=new byte[65536];int n;while((n=data.read(bytes))!=-1)out.write(bytes,0,n);}out.flush();return;
            }
            send(out,404,"text/plain","未找到");
        }}catch(Exception error){if(!closed)LauncherApp.record("文件传输请求结束 "+error.getClass().getSimpleName(),null);}finally{sockets.remove(socket);}
    }
    private static String cookieName(boolean secure){return secure?"windchaser_session":"windchaser_session_http";}
    private boolean authorized(String cookie,boolean secure){if(cookie==null)return false;for(String item:cookie.split(";"))if(item.trim().equals(cookieName(secure)+"="+token))return true;return false;}
    private synchronized void login(InputStream in,OutputStream out,Map<String,String> headers,boolean secure)throws Exception{
        if(android.os.SystemClock.elapsedRealtime()<blockedUntil){send(out,429,"text/html; charset=utf-8",loginPage("尝试过多，请30秒后重试。"));return;}
        int length;try{length=Integer.parseInt(headers.get("content-length"));}catch(Exception e){send(out,411,"text/plain","缺少访问码");return;}
        if(length<0||length>128){send(out,413,"text/plain","访问码格式不正确");return;}
        byte[] body=new byte[length];int at=0;while(at<length){int n=in.read(body,at,length-at);if(n<0)throw new EOFException();at+=n;}
        String candidate=parameter(new String(body,"UTF-8"),"code");
        if(!MessageDigest.isEqual(code.getBytes("US-ASCII"),candidate.getBytes("US-ASCII"))){
            failedLogins++;if(failedLogins>=5){blockedUntil=android.os.SystemClock.elapsedRealtime()+30000;failedLogins=0;}
            send(out,403,"text/html; charset=utf-8",loginPage("访问码不正确，请查看车机文件传输页面。"));return;
        }
        failedLogins=0;head(out,303,"text/plain",0,"Location: /\r\nSet-Cookie: "+cookieName(secure)+"="+token+"; Path=/; HttpOnly; "+(secure?"Secure; ":"")+"SameSite=Strict\r\n");out.flush();
    }
    static String uploadName(String name)throws IOException{
        if(name==null||name.isEmpty()||name.length()>180||name.equals(".")||name.equals("..")||name.startsWith(".upload-"))throw new IOException("无效文件名");
        if(name.getBytes("UTF-8").length>240)throw new IOException("文件名过长，请缩短后重试");
        if(name.matches(".*[\\\\/:\u0000-\u001f].*"))throw new IOException("文件名包含路径字符");return name;
    }
    @android.annotation.SuppressLint("ResourceType")
    private byte[] background()throws IOException{
        // The drawable is a nodpi JPEG; read its original compressed bytes for the browser.
        try(InputStream in=context.getResources().openRawResource(R.drawable.jade_flow)){return read(in);}
    }
    private static String parameter(String query,String key)throws Exception{if(query==null)return "";for(String p:query.split("&")){String[] pair=p.split("=",2);if(pair.length==2&&key.equals(URLDecoder.decode(pair[0],"UTF-8")))return URLDecoder.decode(pair[1],"UTF-8");}return "";}
    private static String line(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b==10)break;if(b!=13)out.write(b);if(out.size()>limit)throw new IOException("line too long");}return b<0&&out.size()==0?null:out.toString("US-ASCII");}
    private static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
    private static void head(OutputStream out,int status,String type,long size,String extra)throws IOException{String reason=status==200?"OK":status==303?"See Other":status==403?"Forbidden":status==404?"Not Found":status==429?"Too Many Requests":"Error";out.write(("HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: "+type+"\r\nContent-Length: "+size+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n"+extra+"\r\n").getBytes("US-ASCII"));}
    private static void send(OutputStream out,int status,String type,String body)throws IOException{send(out,status,type,body.getBytes("UTF-8"));}
    private static void send(OutputStream out,int status,String type,byte[] bytes)throws IOException{head(out,status,type,bytes.length,"");out.write(bytes);out.flush();}
    @Override public void close(){closed=true;if(listener!=null)try{listener.close();}catch(IOException ignored){}synchronized(sockets){for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}}clients.shutdownNow();}
    private String loginPage(String message){return "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>追风桌面 · 连接车机</title>"
        +"<style>body{font:17px system-ui;color:#e7f4ff;background:#071525 url(/background) center/cover fixed;margin:0;padding:24px}main{max-width:420px;margin:12vh auto;padding:28px;border:1px solid #477184;border-radius:24px;background:#091927ed}input,button{box-sizing:border-box;width:100%;font:inherit;padding:15px;margin:12px 0;border-radius:12px;border:0}input{font-size:28px;letter-spacing:8px;text-align:center}button{background:#a7e2d2;color:#12323c}p{line-height:1.7;color:#bad3df}</style>"
        +"<main><h1>连接追风桌面</h1><p>输入车机「文件传输」页面显示的6位访问码，即可上传和下载文件。</p><form method='post' action='/login'><input name='code' type='password' inputmode='numeric' pattern='[0-9]{6}' maxlength='6' minlength='6' placeholder='6位访问码' aria-label='6位访问码' autocomplete='off' required><button>连接车机</button></form><p>"+message+"</p><p>可收藏当前地址，下次直接打开。</p></main>";}
    private String page()throws IOException{try(InputStream in=context.getAssets().open("transfer.html")){return new String(read(in),"UTF-8");}}
}
