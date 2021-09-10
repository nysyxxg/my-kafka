
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * 在使用通道的时候，我们通常都将通道的数据取出存入ByteBuffer对象或者从ByteBuffer对象中获取数据放入通道进行传输；
 * 在使用通道的过程中，我们要注意通道是单向通道还是双向通道，单向通道只能读或写，而双向通道是可读可写的；
 * 如果一个Channel类实现了ReadableByteChannel接口，则表示其是可读的，可以调用read()方法读取；
 * 如果一个Channel类实现了WritableByteChannel接口，则表示其是可写的，可以调用write()方法写入；
 * 如果一个Channel类同时实现了ReadableByteChannel接口和WritableByteChannel接口则为双向通道，如果只实现其中一个，则为单向通道；
 * 如ByteChannel就是一个双向通道，实际上ByteChannel接口本身并不定义新的API方法，它是一个聚集了所继承的多个接口，并重新命名的便捷接口；
 * 如下是一个使用通道的例子，展示了两个通道之间拷贝数据的过程，已添加了完整的注释：
 */
public class TestByteChannel {
    
    
    public static void main(String[] args) throws IOException {
        ReadableByteChannel source = Channels.newChannel(System.in);
        WritableByteChannel dest = Channels.newChannel(System.out);
//        channelCopy1(source, dest);
        channelCopy2(source, dest);
        source.close();
        dest.close();
        
    }
    
    private static void channelCopy1(ReadableByteChannel src, WritableByteChannel dest)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16);
        while (src.read(buffer) != -1) { //  从通道channel中读取数据,, 每次只读取16个字节
            // 切换为读写状态
            buffer.flip();
            // 不能保证全部写入
            dest.write(buffer);// 将读到的数据，写出去
            // 释放已读数据的空间，等待数据写入
            buffer.compact();
            System.out.println("buffer : " +  buffer);
        }
        // 退出循环的时候，由于调用的是compact方法，缓冲区中可能还有数据
        // 需要进一步读取
        buffer.flip();
        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
    }
    
    private static void channelCopy2(ReadableByteChannel src, WritableByteChannel dest)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16);
        while (src.read(buffer) != -1) {
            System.out.println("buffer : " +  buffer);
            // 切换为读状态
            buffer.flip();
            System.out.println("flip .... buffer : " +  buffer);
            // 保证缓冲区的数据全部写入
            while (buffer.hasRemaining()) {
                dest.write(buffer);
            }
            // 清除缓冲区
            buffer.clear();
        }
        // 退出循环的时候，由于调用的是clear方法，缓冲区中已经没有数据，不需要进一步处理
    }
}
