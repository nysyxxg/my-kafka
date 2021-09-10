package kafka.message;

import kafka.utils.IteratorTemplate;
import kafka.utils.SystemTime;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class FileMessageSet extends MessageSet {
    
    private Logger logger = Logger.getLogger(FileMessageSet.class);
    
    private AtomicLong setSize = new AtomicLong();
    private AtomicLong setHighWaterMark = new AtomicLong();
    
    FileChannel channel;
    long offset;
    long limit;
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
        initFileMsgSet();
        
    }
    
    private void initFileMsgSet() throws IOException {
        System.out.println("----------------FileMessageSet------0----------init---------------setHighWaterMark-" + setHighWaterMark.get());
        if (mutable) {
            if (limit < Long.MAX_VALUE || offset > 0)
                throw new IllegalArgumentException("Attempt to open a mutable message set with a view or offset, which is not allowed.");
            
            if (needRecover.get()) { // 如果需要恢复
                // set the file position to the end of the file for appending messages
                long startMs = System.currentTimeMillis();
                Long truncated = recover();
                logger.info("Recovery succeeded in " + (System.currentTimeMillis() - startMs) / 1000 + " seconds. " + truncated + " bytes truncated.");
            } else {
                setSize.set(channel.size());
                setHighWaterMark.set(sizeInBytes());
                channel.position(channel.size());
            }
        } else {
            System.out.println("----------------FileMessageSet------1----------init---------------setHighWaterMark-" + setHighWaterMark.get());
            long minSize = Math.min(channel.size(), limit);
            setSize.set(minSize - offset);
            setHighWaterMark.set(sizeInBytes());
            System.out.println("----------------FileMessageSet-------2---------init---------------setHighWaterMark-" + setHighWaterMark.get());
            if (logger.isDebugEnabled()) {
                logger.debug("initializing high water mark in immutable mode: " + highWaterMark());
            }
        }
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
    
    public long sizeInBytes() {
        return setSize.get();
    }
    
    /**
     * public abstract long transferTo(long position,long count,WritableByteChannel target)throws IOException
     * 将字节从此通道的文件传输到给定的可写入字节通道。
     * 试图读取从此通道的文件中给定 position 处开始的 count 个字节，并将其写入目标通道。
     * 此方法的调用不一定传输所有请求的字节；是否传输取决于通道的性质和状态。
     * 如果此通道的文件从给定的 position 处开始所包含的字节数小于 count 个字节，
     * 或者如果目标通道是非阻塞的并且其输出缓冲区中的自由空间少于 count 个字节，则所传输的字节数要小于请求的字节数。
     * 此方法不修改此通道的位置。如果给定的位置大于该文件的当前大小，则不传输任何字节。
     * 如果目标通道中有该位置，则从该位置开始写入各字节，然后将该位置增加写入的字节数。
     * 与从此通道读取并将内容写入目标通道的简单循环语句相比，此方法可能高效得多。
     * 很多操作系统可将字节直接从文件系统缓存传输到目标通道，而无需实际复制各字节。
     * 参数：
     * position - 文件中的位置，从此位置开始传输；必须为非负数
     * count - 要传输的最大字节数；必须为非负数
     * target - 目标通道
     * 返回：实际已传输的字节数，可能为零
     *
     * @param destChannel
     * @param writeOffset
     * @param maxSize
     * @return
     * @throws IOException
     */
    public Long writeTo(WritableByteChannel destChannel, Long writeOffset, Long maxSize) throws IOException {
        return channel.transferTo(offset + writeOffset, Math.min(maxSize, sizeInBytes()), destChannel);
    }
    
    @Override
    public Iterator<MessageAndOffset> iterator() {
        System.out.println("--------------------------FileMessageSet-------------------iterator()---------");
        return new IteratorTemplate<MessageAndOffset>() {
            long location = offset;
            public MessageAndOffset makeNext() throws Throwable {
                // read the size of the item
                ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
                channel.read(sizeBuffer, location);
                if (sizeBuffer.hasRemaining()) {
                    return allDone();
                }
                
                sizeBuffer.rewind();
                int size = sizeBuffer.getInt();
                if (size < Message.MinHeaderSize) {
                    return allDone();
                }
                
                // read the item itself
                ByteBuffer buffer = ByteBuffer.allocate(size);
                channel.read(buffer, location + 4);
                if (buffer.hasRemaining()) {
                    return allDone();
                }
                buffer.rewind();
                
                // increment the location and return the item
                location += size + 4;
                return new MessageAndOffset(new Message(buffer), location);
            }
        };
    }
    
    public void flush() throws IOException {
        checkMutable();
        SystemTime systemTime = new SystemTime();
        Long startTime = systemTime.milliseconds();
        channel.force(true);
        Long elapsedTime = systemTime.milliseconds() - startTime;
        LogFlushStats.recordFlushRequest(elapsedTime);
        if (logger.isDebugEnabled()) {
            logger.debug("flush time " + elapsedTime);
        }
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
                Math.min(this.offset + readOffset + size, highWaterMark()), false, new AtomicBoolean(false));
    }
    
    public Long recover() throws IOException {
        checkMutable();
        long len = channel.size();
        ByteBuffer buffer = ByteBuffer.allocate(4);
        Long validUpTo = 0L;
        Long next = 0L;
        do {
            next = validateMessage(channel, validUpTo, len, buffer);
            if (next >= 0)
                validUpTo = next;
        } while (next >= 0);
        channel.truncate(validUpTo);
        setSize.set(validUpTo);
        setHighWaterMark.set(validUpTo);
        if (logger.isDebugEnabled()) {
            logger.info("recover high water mark:" + highWaterMark());
        }
        /* This should not be necessary, but fixes bug 6191269 on some OSs. */
        channel.position(validUpTo);
        needRecover.set(false);
        return len - validUpTo;
    }
    
    private Long validateMessage(FileChannel channel, Long start, Long len, ByteBuffer buffer)
            throws IOException {
        buffer.rewind();
        int read = channel.read(buffer, start);
        if (read < 4) {
            return -1L;
        }
        
        // check that we have sufficient bytes left in the file
        int size = buffer.getInt(0);
        ;
        int MinHeaderSize = Message.getMinHeaderSize();
        System.out.println("---------------------------MinHeaderSize--------------------=" + MinHeaderSize);
        if (size < MinHeaderSize)
            return -1L;
        
        Long next = start + 4 + size;
        if (next > len)
            return -1L;
        
        // read the message
        ByteBuffer messageBuffer = ByteBuffer.allocate(size);
        Long curr = start + 4;
        while (messageBuffer.hasRemaining()) {
            read = channel.read(messageBuffer, curr);
            if (read < 0) {
                throw new IllegalStateException("File size changed during recovery!");
            } else {
                curr += read;
            }
        }
        messageBuffer.rewind();
        Message message = new Message(messageBuffer);
        if (!message.isValid()) {
            return -1L;
        } else {
            return next;
        }
    }
    
}
