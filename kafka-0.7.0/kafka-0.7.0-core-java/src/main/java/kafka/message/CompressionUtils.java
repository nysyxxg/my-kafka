package kafka.message;

import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionUtils {
    
    private static Logger logger = Logger.getLogger(CompressionUtils.class);
    
    
    public static Message compress(Iterable<Message> messages) throws IOException {
        return compress(messages, new DefaultCompressionCodec());
    }
    
    public static Message compress(Iterable<Message> messages, CompressionCodec compressionCodec) throws IOException {
        
        if (compressionCodec instanceof DefaultCompressionCodec) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutput = new GZIPOutputStream(outputStream);
            if (logger.isDebugEnabled()) {
                logger.debug("Allocating message byte buffer of size = " + MessageSet.messageSetSize(messages));
            }
            ByteBuffer messageByteBuffer = ByteBuffer.allocate(MessageSet.messageSetSize(messages));
            
            messages.forEach(message -> {
                message.serializeTo(messageByteBuffer);
            });
            messageByteBuffer.rewind();
            
            try {
                gzipOutput.write(messageByteBuffer.array());
            } catch (IOException e) {
                logger.error("Error while writing to the GZIP output stream", e);
                if (gzipOutput != null) {
                    gzipOutput.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                throw e;
            } finally {
                if (gzipOutput != null) gzipOutput.close();
                if (outputStream != null) outputStream.close();
            }
            
            Message oneCompressedMessage = new Message(outputStream.toByteArray(), compressionCodec);
            return oneCompressedMessage;
        } else if (compressionCodec instanceof GZIPCompressionCodec) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutput = new GZIPOutputStream(outputStream);
            if (logger.isDebugEnabled())
                logger.debug("Allocating message byte buffer of size = " + MessageSet.messageSetSize(messages));
            
            ByteBuffer messageByteBuffer = ByteBuffer.allocate(MessageSet.messageSetSize(messages));
            messages.forEach(m -> m.serializeTo(messageByteBuffer));
            messageByteBuffer.rewind();
            
            try {
                gzipOutput.write(messageByteBuffer.array());
            } catch (IOException e) {
                logger.error("Error while writing to the GZIP output stream", e);
                if (gzipOutput != null)
                    gzipOutput.close();
                if (outputStream != null)
                    outputStream.close();
                throw e;
            } finally {
                if (gzipOutput != null)
                    gzipOutput.close();
                if (outputStream != null)
                    outputStream.close();
            }
            
            Message oneCompressedMessage = new Message(outputStream.toByteArray(), compressionCodec);
            return oneCompressedMessage;
        } else {
            throw new kafka.common.UnknownCodecException("Unknown Codec: " + compressionCodec);
        }
    }
    
    public static ByteBufferMessageSet decompress(Message message) throws IOException {
        CompressionCodec compressionCodec = message.compressionCodec();
        
        if (compressionCodec instanceof DefaultCompressionCodec) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            InputStream inputStream = new ByteBufferBackedInputStream(message.payload());
            GZIPInputStream gzipIn = new GZIPInputStream(inputStream);
            byte intermediateBuffer[] = new byte[1024];
            try {
            
//                    Stream.continually(gzipIn.read(intermediateBuffer)).takeWhile(_ > 0).foreach {
//                        dataRead =>  outputStream.write(intermediateBuffer, 0, dataRead)
//                    }
                
                while(true){
                    int size = gzipIn.read(intermediateBuffer);
                    if(size > 0){
                        outputStream.write(intermediateBuffer, 0, size);
                    }else {
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Error while reading from the GZIP input stream", e);
                if (gzipIn != null) gzipIn.close();
                if (outputStream != null) outputStream.close();
                throw e;
            } finally {
                if (gzipIn != null) gzipIn.close();
                if (outputStream != null) outputStream.close();
            }
            
            ByteBuffer outputBuffer = ByteBuffer.allocate(outputStream.size());
            outputBuffer.put(outputStream.toByteArray());
            outputBuffer.rewind();
            byte outputByteArray[] = outputStream.toByteArray();
            return new ByteBufferMessageSet(outputBuffer);
            
        } else if (compressionCodec instanceof GZIPCompressionCodec) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            InputStream inputStream = new ByteBufferBackedInputStream(message.payload());
            GZIPInputStream gzipIn = new GZIPInputStream(inputStream);
            byte intermediateBuffer[] = new byte[1024];
            
            try {
//                    Stream.continually(gzipIn.read(intermediateBuffer)).takeWhile(_ > 0).foreach {
//                        dataRead =>
//                        outputStream.write(intermediateBuffer, 0, dataRead)
//                    }
    
                while(true){
                    int size = gzipIn.read(intermediateBuffer);
                    if(size > 0){
                        outputStream.write(intermediateBuffer, 0, size);
                    }else {
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Error while reading from the GZIP input stream", e);
                if (gzipIn != null) gzipIn.close();
                if (outputStream != null) outputStream.close();
                ;
                throw e;
            } finally {
                if (gzipIn != null) gzipIn.close();
                if (outputStream != null) outputStream.close();
            }
            
            ByteBuffer outputBuffer = ByteBuffer.allocate(outputStream.size());
            outputBuffer.put(outputStream.toByteArray());
            outputBuffer.rewind();
            byte outputByteArray[] = outputStream.toByteArray();
            return new ByteBufferMessageSet(outputBuffer);
            
        } else {
            throw new kafka.common.UnknownCodecException("Unknown Codec: " + message.compressionCodec());
        }
    }
}
