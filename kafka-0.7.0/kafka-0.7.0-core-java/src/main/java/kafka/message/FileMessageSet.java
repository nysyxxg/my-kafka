package kafka.message;

import kafka.utils.SystemTime;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class FileMessageSet extends MessageSet {
    private AtomicLong setSize = new AtomicLong();
    private AtomicLong setHighWaterMark = new AtomicLong();
    private Logger logger = Logger.getLogger(FileMessageSet.class);
    
    
    FileChannel channel;
    Long offset;
    Long limit;
    Boolean mutable;
    AtomicBoolean needRecover;
    
    
    public FileMessageSet(File file, Boolean mutable, AtomicBoolean needRecover) throws Throwable {
        this(Utils.openChannel(file, mutable), mutable, needRecover);
    }
    
    public FileMessageSet(FileChannel channel, Boolean mutable, AtomicBoolean needRecover) throws Throwable {
        this(channel, 0L, Long.MAX_VALUE, mutable, needRecover);
    }
    
    
    public FileMessageSet(FileChannel channel,
                          Long offset, Long limit, Boolean mutable, AtomicBoolean needRecover) throws Throwable {
        
        this.channel = channel;
        this.offset = offset;
        this.limit = limit;
        this.mutable = mutable;
        this.needRecover = needRecover;
    }
    
    
    public FileMessageSet() throws Throwable {
    }
    
    public FileMessageSet(FileChannel channel, Boolean mutable) throws Throwable {
        this(channel, 0L, Long.MAX_VALUE, mutable, new AtomicBoolean(false));
    }
    
    public FileMessageSet(File file, boolean mutable) throws Throwable {
        this(Utils.openChannel(file, mutable), mutable);
    }
    
    
    public Long highWaterMark() {
        return setHighWaterMark.get();
    }
    
    public void close() throws IOException {
        if (mutable) {
            flush();
        }
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public Long sizeInBytes() {
        return setSize.get();
    }
    
    @Override
    public Long writeTo(WritableByteChannel destChannel, Long writeOffset, Long maxSize) throws IOException {
        return channel.transferTo(offset + writeOffset, Math.min(maxSize, sizeInBytes()), destChannel);
    }
    
    public void flush() throws IOException {
        checkMutable();
        SystemTime systemTime = new SystemTime();
        Long startTime = systemTime.milliseconds();
        channel.force(true);
        Long elapsedTime = systemTime.milliseconds() - startTime;
        LogFlushStats.recordFlushRequest(elapsedTime);
        if (logger.isDebugEnabled())
            logger.debug("flush time " + elapsedTime);
        setHighWaterMark.set(sizeInBytes());
        if (logger.isDebugEnabled())
            logger.debug("flush high water mark:" + highWaterMark());
    }
    
    void checkMutable() {
        if (!mutable)
            throw new IllegalStateException("Attempt to invoke mutation on immutable message set.");
    }
    
    
    public void append(MessageSet messages) {
        checkMutable();
        Long written = 0L;
        while (written < messages.sizeInBytes()) {
            try {
                written += messages.writeTo(channel, 0L, messages.sizeInBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        setSize.getAndAdd(written);
    }
    
    
    public MessageSet read(Long readOffset, Long size) throws Throwable {
        return new FileMessageSet(channel, this.offset + readOffset,
                Math.min(this.offset + readOffset + size, highWaterMark()),
                false, new AtomicBoolean(false));
    }
    
}
