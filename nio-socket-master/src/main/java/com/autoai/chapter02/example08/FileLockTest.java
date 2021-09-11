package com.autoai.chapter02.example08;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/1 15:59
 * @Description:
 */
@Slf4j
public class FileLockTest {

    /**
     * ============= 验证FileLock lock(long position, long size, boolean shared)方法是同步的 =============
     * 针对不同进程
     */
    @Test
    public void test1() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            log.info("test1 begin...");
            channel.lock(0, 1, false);
            ByteBuffer byteBuffer = ByteBuffer.allocate(20);
            channel.read(byteBuffer);
            log.info("read:{}",new String(byteBuffer.array(), Charset.forName("utf-8")));
            Thread.sleep(100000);
            log.info("test1 end...");
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Test
    public void test2() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            log.info("test2 begin...");
            channel.lock(0, 1, false);
            Thread.sleep(10000);
            log.info("test2 end...");
        } catch (Exception e) {
            log.error(e.toString());
        }
    }
    /**
     * ============= 验证AsynchronousCloseException异常的发生 =============
     * 偶尔出现，lock的时候，正好执行close
     */
    @Test
    public void test3() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            // 创建线程池
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 5, 5, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(100), new ThreadFactory() {
                private AtomicInteger count = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread th = new Thread(r);
                    th.setName("zks-" + count.getAndIncrement());
                    return th;
                }
            }, (r, executor) -> log.error("queue is full"));

            threadPoolExecutor.submit(() -> {
                try {
                    channel.lock(0, 1, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            threadPoolExecutor.submit(() -> {
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            Thread.sleep(5000);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * ============= 验证FileLockInterruptionException异常的发生 =============
     * lock的时候，正好执行shutdownNow，线程中断
     */
    @Test
    public void test4() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/i.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            // 创建线程池
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 5, 5, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(100), new ThreadFactory() {
                private AtomicInteger count = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread th = new Thread(r);
                    th.setName("zks-" + count.getAndIncrement());
                    return th;
                }
            }, (r, executor) -> log.error("queue is full"));

            CountDownLatch countDownLatch = new CountDownLatch(1);

            threadPoolExecutor.submit(() -> {
                try {
                    countDownLatch.countDown();
                    channel.lock(0, 1, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            countDownLatch.await();
            Thread.sleep(1);
            threadPoolExecutor.shutdownNow();

            Thread.sleep(5000);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * ============= 验证共享锁自己不能写（出现异常） =============
     * 无法验证，在Mac OS X和FreeBSD中，不区分“POSIX”和“FLOCK”锁，一个进程使用 lockf/fcntl/flock 中任何一个方法对文件加独占锁，
     * 另一个进程使用 lockf/fcntl/flock 中任何一个方法对同一文件加独占锁时会等待另一个进程释放锁。
     */
    @Test
    public void test5() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            FileLock lock = channel.lock(0, 3, true);
            log.info("shared:{}", lock.isShared());
            ByteBuffer byteBuffer = ByteBuffer.wrap("123".getBytes());
            channel.write(byteBuffer);
            Thread.sleep(5000);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证独占锁自己能写
     */
    @Test
    public void test6() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            FileLock lock = channel.lock(1, 2, false);
            log.info("shared:{}", lock.isShared());
            ByteBuffer byteBuffer = ByteBuffer.wrap("123".getBytes());
            channel.write(byteBuffer);
            Thread.sleep(5000);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * ============= 验证独占锁别人不能写（出现异常）=============
     * macos验证不出来
     */
    @Test
    public void test7() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            FileLock lock = channel.lock(1, 2, false);
            log.info("valid :{}", lock.isValid());
            Thread.sleep(Integer.MAX_VALUE);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Test
    public void test8() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.wrap("123".getBytes());
            channel.write(byteBuffer);
            Thread.sleep(5000);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证独占锁自己能读
     */
    @Test
    public void test9() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            channel.lock(1, 2, false);
            ByteBuffer byteBuffer = ByteBuffer.allocate(3);
            channel.read(byteBuffer);
            log.info("result:{}", new String(byteBuffer.array()));
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * ============= 验证独占锁别人不能读（出现异常）=============
     * macos验证不出来
     */
    @Test
    public void test10() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            FileLock lock = channel.lock(1, 2, false);
            log.info("valid :{}", lock.isValid());
            Thread.sleep(Integer.MAX_VALUE);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    @Test
    public void test11() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            ByteBuffer byteBuffer = ByteBuffer.allocate(3);
            channel.read(byteBuffer);
            log.info("result:{}", new String(byteBuffer.array()));
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * 验证lock()方法的参数position和size的含义
     */
    @Test
    public void test12() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel()
        ){
            channel.lock(1, 2, true);
            ByteBuffer byteBuffer1 = ByteBuffer.wrap("1".getBytes());
            channel.write(byteBuffer1);
            log.info("result:{}", new String(byteBuffer1.array()));
            ByteBuffer byteBuffer2 = ByteBuffer.wrap("2".getBytes());
            channel.write(byteBuffer2);
            log.info("result:{}", new String(byteBuffer2.array()));
            ByteBuffer byteBuffer3 = ByteBuffer.wrap("3".getBytes());
            channel.write(byteBuffer3);
            log.info("result:{}", new String(byteBuffer3.array()));
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * FileLock 常见API的使用
     */
    @Test
    public void test13() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel();
            FileLock fileLock = channel.lock(1, 2, true)
        ){
            long position = fileLock.position();
            long size = fileLock.size();
            boolean valid = fileLock.isValid();
            boolean shared = fileLock.isShared();
            boolean isLockAcquirdeByChannel = Objects.equals(fileLock.acquiredBy().hashCode(), channel.hashCode());
            log.info("position:{},size:{},valid:{},shared:{},isLockAcquiredByChannel:{}",position, size, valid, shared, isLockAcquirdeByChannel);
            fileLock.release();
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    /**
     * boolean overlaps(long position, long size)方法的使用
     */
    @Test
    public void test14() {
        try (
            RandomAccessFile file = new RandomAccessFile("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/file/j.txt", "rw");
            FileChannel channel = file.getChannel();
            FileLock fileLock = channel.lock(1, 2, true)
        ){
            boolean overlaps1 = fileLock.overlaps(2, 10);
            boolean overlaps2 = fileLock.overlaps(3, 10);
            log.info("overlaps1:{},overlaps2:{}", overlaps1, overlaps2);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

}
