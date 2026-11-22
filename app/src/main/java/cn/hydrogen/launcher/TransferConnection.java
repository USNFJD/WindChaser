package cn.hydrogen.launcher;

import java.io.*;
import java.nio.ByteBuffer;
import javax.net.ssl.*;

/** One accepted connection, classified before parsing HTTP. No proxy port or pump threads. */
final class TransferConnection implements Closeable {
    final boolean secure;
    final InputStream input;
    final OutputStream output;
    private final Tls tls;
    TransferConnection(InputStream rawIn,OutputStream rawOut,SSLContext context)throws IOException {
        BufferedInputStream peek=new BufferedInputStream(rawIn,32768);peek.mark(1);int first=peek.read();peek.reset();
        if(first<0)throw new EOFException("empty connection");
        secure=first==22;
        if(secure){SSLEngine engine=context.createSSLEngine();engine.setUseClientMode(false);engine.setEnabledProtocols(new String[]{"TLSv1.2"});tls=new Tls(engine,peek,rawOut);try{tls.handshake();}catch(IOException|RuntimeException error){tls.finish();throw error;}input=tls.in;output=tls.out;}
        else {tls=null;input=peek;output=rawOut;}
    }
    @Override public void close(){if(tls!=null)tls.finish();}
    private static final class Tls {
        private static final ByteBuffer EMPTY=ByteBuffer.allocate(0);
        private static final int MAX_BUFFER=256*1024;
        private final SSLEngine engine;private final InputStream rawIn;private final OutputStream rawOut;
        private ByteBuffer netIn,netOut,plain;private boolean finished;
        Tls(SSLEngine value,InputStream source,OutputStream sink)throws SSLException {
            engine=value;rawIn=source;rawOut=sink;SSLSession session=engine.getSession();
            netIn=ByteBuffer.allocate(session.getPacketBufferSize());netOut=ByteBuffer.allocate(session.getPacketBufferSize());plain=ByteBuffer.allocate(session.getApplicationBufferSize());plain.flip();engine.beginHandshake();
        }
        private ByteBuffer larger(ByteBuffer old,boolean reading)throws SSLException {
            if(old.capacity()>=MAX_BUFFER)throw new SSLException("TLS record exceeds bound");
            ByteBuffer next=ByteBuffer.allocate(Math.min(MAX_BUFFER,old.capacity()*2));if(!reading)old.flip();next.put(old);if(reading)next.flip();return next;
        }
        private void receive()throws IOException {
            if(!netIn.hasRemaining())netIn=larger(netIn,false);
            int n=rawIn.read(netIn.array(),netIn.position(),netIn.remaining());if(n<0)throw new EOFException("TLS peer disconnected");netIn.position(netIn.position()+n);
        }
        private SSLEngineResult unwrap()throws IOException {
            for(;;){if(netIn.position()==0)receive();netIn.flip();plain.compact();SSLEngineResult result;
                try{result=engine.unwrap(netIn,plain);}finally{netIn.compact();plain.flip();}
                if(result.getStatus()==SSLEngineResult.Status.BUFFER_OVERFLOW){plain=larger(plain,true);continue;}
                if(result.getStatus()==SSLEngineResult.Status.BUFFER_UNDERFLOW){receive();continue;}
                return result;
            }
        }
        private SSLEngineResult wrap(ByteBuffer source)throws IOException {
            for(;;){netOut.clear();SSLEngineResult result=engine.wrap(source,netOut);
                if(result.getStatus()==SSLEngineResult.Status.BUFFER_OVERFLOW){netOut=larger(netOut,false);continue;}
                if(result.getStatus()==SSLEngineResult.Status.BUFFER_UNDERFLOW)throw new SSLException("unexpected TLS wrap underflow");
                netOut.flip();if(netOut.hasRemaining())rawOut.write(netOut.array(),netOut.position(),netOut.remaining());rawOut.flush();return result;
            }
        }
        void handshake()throws IOException {
            int idle=0;
            for(;;){SSLEngineResult.HandshakeStatus status=engine.getHandshakeStatus();SSLEngineResult result;
                switch(status){
                    case FINISHED:case NOT_HANDSHAKING:return;
                    case NEED_TASK:Runnable task;while((task=engine.getDelegatedTask())!=null)task.run();if(++idle>64)throw new SSLException("TLS task stalled");continue;
                    case NEED_WRAP:result=wrap(EMPTY);break;
                    case NEED_UNWRAP:result=unwrap();break;
                    default:throw new SSLException("unsupported TLS handshake state");
                }
                if(result.getStatus()==SSLEngineResult.Status.CLOSED)throw new EOFException("TLS handshake closed");
                if(result.bytesConsumed()+result.bytesProduced()>0)idle=0;else if(++idle>64)throw new SSLException("TLS handshake stalled");
            }
        }
        final InputStream in=new InputStream(){
            @Override public int read()throws IOException {byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:one[0]&255;}
            @Override public int read(byte[] bytes,int offset,int length)throws IOException {
                if(bytes==null)throw new NullPointerException();if(offset<0||length<0||length>bytes.length-offset)throw new IndexOutOfBoundsException();if(length==0)return 0;
                int idle=0;
                while(!plain.hasRemaining()){
                    if(engine.isInboundDone())return -1;handshake();if(plain.hasRemaining())break;
                    SSLEngineResult result=unwrap();if(result.getStatus()==SSLEngineResult.Status.CLOSED&&!plain.hasRemaining())return -1;
                    if(result.bytesConsumed()+result.bytesProduced()>0)idle=0;else if(++idle>64)throw new SSLException("TLS read stalled");
                }
                int n=Math.min(length,plain.remaining());plain.get(bytes,offset,n);return n;
            }
        };
        final OutputStream out=new OutputStream(){
            @Override public void write(int value)throws IOException {write(new byte[]{(byte)value},0,1);}
            @Override public void write(byte[] bytes,int offset,int length)throws IOException {
                if(bytes==null)throw new NullPointerException();if(offset<0||length<0||length>bytes.length-offset)throw new IndexOutOfBoundsException();
                ByteBuffer source=ByteBuffer.wrap(bytes,offset,length);int idle=0;
                while(source.hasRemaining()){handshake();SSLEngineResult result=wrap(source);if(result.getStatus()==SSLEngineResult.Status.CLOSED)throw new EOFException("TLS output closed");if(result.bytesConsumed()+result.bytesProduced()>0)idle=0;else if(++idle>64)throw new SSLException("TLS write stalled");}
            }
            @Override public void flush()throws IOException {rawOut.flush();}
        };
        void finish(){if(finished)return;finished=true;try{engine.closeOutbound();for(int i=0;i<3&&!engine.isOutboundDone();i++)wrap(EMPTY);}catch(IOException ignored){}finally{try{engine.closeInbound();}catch(SSLException ignored){}} }
    }
}
