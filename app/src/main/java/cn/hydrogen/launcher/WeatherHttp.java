package cn.hydrogen.launcher;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** HTTPS with normal hostname/chain checks and public roots absent on old 4.x ROMs. */
final class WeatherHttp {
    private static SSLSocketFactory legacy;
    static String get(Context context,String address)throws Exception{
        return get(context,address,12000,12000,256*1024);
    }
    static String get(Context context,String address,int connect,int read,int limit)throws Exception{
        HttpsURLConnection c=(HttpsURLConnection)new URL(address).openConnection();
        if(Build.VERSION.SDK_INT<21)c.setSSLSocketFactory(legacyTls(context));
        c.setConnectTimeout(connect);c.setReadTimeout(read);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","WindChaser/1.0.1 (github.com/USNFJD/WindChaser)");
        try{
            if(c.getResponseCode()!=200)throw new IOException("天气服务 HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("响应过大");out.write(b,0,n);}return out.toString("UTF-8");
            }
        }finally{c.disconnect();}
    }
    static synchronized SSLSocketFactory legacyTls(Context context)throws Exception{
        if(legacy!=null)return legacy;
        TrustManagerFactory original=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());original.init((KeyStore)null);
        X509TrustManager system=(X509TrustManager)original.getTrustManagers()[0];
        KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());roots.load(null);
        try(InputStream in=context.getAssets().open("isrgrootx1.pem")){roots.setCertificateEntry("ISRG Root X1",CertificateFactory.getInstance("X.509").generateCertificate(in));}
        // QQ's image CDN uses this chain; many KitKat system stores predate it.
        // Public root verified against DigiCert's published SHA-256 fingerprint.
        try(InputStream in=context.getAssets().open("digicertglobalrootg2.pem")){roots.setCertificateEntry("DigiCert Global Root G2",CertificateFactory.getInstance("X.509").generateCertificate(in));}
        TrustManagerFactory extra=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());extra.init(roots);X509TrustManager modern=(X509TrustManager)extra.getTrustManagers()[0];
        X509TrustManager trusted=new X509TrustManager(){
            public java.security.cert.X509Certificate[] getAcceptedIssuers(){return system.getAcceptedIssuers();}
            public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{system.checkClientTrusted(chain,auth);}
            public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{try{system.checkServerTrusted(chain,auth);}catch(CertificateException oldRoot){modern.checkServerTrusted(chain,auth);}}
        };
        // KitKat's cipher set cannot negotiate with this weather service. Conscrypt
        // supplies modern TLS while preserving certificate and hostname validation.
        SSLContext tls=SSLContext.getInstance("TLS",org.conscrypt.Conscrypt.newProvider());tls.init(null,new TrustManager[]{trusted},null);legacy=tls.getSocketFactory();return legacy;
    }
}
