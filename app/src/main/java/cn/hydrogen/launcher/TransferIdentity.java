package cn.hydrogen.launcher;

import android.content.Context;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.net.ssl.*;

/** A separate, locally generated RSA identity for each installation. No embedded private key. */
final class TransferIdentity {
    static SSLServerSocketFactory factory(Context context)throws Exception{return context(context).getServerSocketFactory();}
    static synchronized SSLContext context(Context context)throws Exception{
        android.content.SharedPreferences prefs=context.getSharedPreferences("transfer-identity",0);
        String saved=prefs.getString("password","");if(saved.isEmpty()){byte[] random=new byte[24];new SecureRandom().nextBytes(random);saved=android.util.Base64.encodeToString(random,android.util.Base64.NO_WRAP);prefs.edit().putString("password",saved).commit();}
        char[] password=saved.toCharArray();File file=new File(context.getFilesDir(),"transfer-identity.bks");KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());
        if(file.isFile()){try(InputStream in=new FileInputStream(file)){store.load(in,password);}}
        else{
            store.load(null,password);KeyPairGenerator keys=KeyPairGenerator.getInstance("RSA");keys.initialize(2048);KeyPair pair=keys.generateKeyPair();
            byte[] algorithm=seq(oid(new byte[]{0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,1,1,11}),new byte[]{5,0});
            byte[] name=seq(set(seq(oid(new byte[]{0x55,4,3}),tag(12,"WindChaser Local Transfer".getBytes("UTF-8")))));
            SimpleDateFormat time=new SimpleDateFormat("yyMMddHHmmss'Z'",Locale.US);time.setTimeZone(TimeZone.getTimeZone("UTC"));long now=System.currentTimeMillis();
            byte[] validity=seq(tag(23,time.format(new Date(now-86400000L)).getBytes("US-ASCII")),tag(23,time.format(new Date(now+10L*365*86400000L)).getBytes("US-ASCII")));
            byte[] body=seq(tag(0xa0,integer(BigInteger.valueOf(2))),integer(new BigInteger(120,new SecureRandom()).add(BigInteger.ONE)),algorithm,name,validity,name,pair.getPublic().getEncoded());
            Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(pair.getPrivate());signature.update(body);byte[] signed=signature.sign();byte[] bits=new byte[signed.length+1];System.arraycopy(signed,0,bits,1,signed.length);
            byte[] encoded=seq(body,algorithm,tag(3,bits));X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));certificate.verify(pair.getPublic());
            store.setKeyEntry("windchaser",pair.getPrivate(),password,new java.security.cert.Certificate[]{certificate});
            // Android 4.4's BKS writer closes its stream. Serialize in memory first
            // so the app retains ownership of the file descriptor being synced.
            ByteArrayOutputStream encodedStore=new ByteArrayOutputStream();store.store(encodedStore,password);
            File tmp=new File(file.getParentFile(),file.getName()+".new");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(encodedStore.toByteArray());out.getFD().sync();}if(!tmp.renameTo(file))throw new IOException("无法保存本机 HTTPS 证书");
        }
        KeyManagerFactory manager=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());manager.init(store,password);
        // Use the already bundled Conscrypt engine: KitKat's platform server
        // cipher/signature defaults fail with current phone/browser TLS clients.
        SSLContext tls=SSLContext.getInstance("TLS",org.conscrypt.Conscrypt.newProvider());tls.init(manager.getKeyManagers(),null,new SecureRandom());return tls;
    }
    private static byte[] integer(BigInteger value)throws IOException{return tag(2,value.toByteArray());}
    private static byte[] oid(byte[] bytes)throws IOException{return tag(6,bytes);}
    private static byte[] seq(byte[]... bytes)throws IOException{return tag(48,join(bytes));}
    private static byte[] set(byte[]... bytes)throws IOException{return tag(49,join(bytes));}
    private static byte[] join(byte[]... bytes)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] b:bytes)out.write(b);return out.toByteArray();}
    private static byte[] tag(int type,byte[] bytes)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(type);if(bytes.length<128)out.write(bytes.length);else{int count=0,n=bytes.length;while(n>0){count++;n>>>=8;}out.write(128|count);for(int k=count-1;k>=0;k--)out.write(bytes.length>>>(8*k));}out.write(bytes);return out.toByteArray();}
}
