package com.autoai.chapter06.example01;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class AsynchronousFileChannelTest {

    /**
     * 排他锁
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Test
    public void testLock() throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Callable<String>> callables = new ArrayList<>();

        Callable<String> callable = () -> {
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                    , StandardOpenOption.WRITE);
            // 递归获取锁
            Future<FileLock> lock = this.getLock(fileChannel, 0, 1, false);
            FileLock fileLock = lock.get();
            log.info("thread:{} get lock", Thread.currentThread().getName());
            Thread.sleep(3000);
            fileLock.release();
            log.info("thread:{} release lock", Thread.currentThread().getName());
            return "ok";
        };

        callables.add(callable);
        callables.add(callable);
        List<Future<String>> futures = executorService.invokeAll(callables);
        // 同步
        for (Future<String> future : futures) {
            future.get();
        }
    }

    private Future<FileLock> getLock(AsynchronousFileChannel fileChannel, int position, long size, boolean shared) {
        Future<FileLock> lock;
        try {
            lock = fileChannel.lock(position, size, shared);
        } catch (OverlappingFileLockException e) {
            // 2s后递归
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            lock = this.getLock(fileChannel, position, size, shared);
        }
        return lock;

    }

    /**
     * 锁定不同区域
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Test
    public void testLock2() throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Callable<String>> callables = new ArrayList<>();

        Callable<String> callable1 = () -> {
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                    , StandardOpenOption.WRITE);
            // 递归获取锁
            Future<FileLock> lock = this.getLock(fileChannel, 0, 2, false);
            FileLock fileLock = lock.get();
            log.info("thread:{} get lock", Thread.currentThread().getName());
            Thread.sleep(3000);
            fileLock.release();
            log.info("thread:{} release lock", Thread.currentThread().getName());
            return "ok";
        };

        Callable<String> callable2 = () -> {
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                    , StandardOpenOption.WRITE);
            // 递归获取锁
            Future<FileLock> lock = this.getLock(fileChannel, 2, 2, false);
            FileLock fileLock = lock.get();
            log.info("thread:{} get lock", Thread.currentThread().getName());
            Thread.sleep(3000);
            fileLock.release();
            log.info("thread:{} release lock", Thread.currentThread().getName());
            return "ok";
        };

        callables.add(callable1);
        callables.add(callable2);
        List<Future<String>> futures = executorService.invokeAll(callables);
        // 同步
        for (Future<String> future : futures) {
            future.get();
        }
    }

    /**
     * 锁定重叠区域
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Test
    public void testLock3() throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Callable<String>> callables = new ArrayList<>();

        Callable<String> callable1 = () -> {
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                    , StandardOpenOption.WRITE);
            // 递归获取锁
            Future<FileLock> lock = this.getLock(fileChannel, 0, 3, false);
            FileLock fileLock = lock.get();
            log.info("thread:{} get lock", Thread.currentThread().getName());
            Thread.sleep(3000);
            fileLock.release();
            log.info("thread:{} release lock", Thread.currentThread().getName());
            return "ok";
        };

        Callable<String> callable2 = () -> {
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                    , StandardOpenOption.WRITE);
            // 递归获取锁
            Future<FileLock> lock = this.getLock(fileChannel, 0, 5, false);
            FileLock fileLock = lock.get();
            log.info("thread:{} get lock", Thread.currentThread().getName());
            Thread.sleep(3000);
            fileLock.release();
            log.info("thread:{} release lock", Thread.currentThread().getName());
            return "ok";
        };

        callables.add(callable1);
        callables.add(callable2);
        List<Future<String>> futures = executorService.invokeAll(callables);
        // 同步
        for (Future<String> future : futures) {
            future.get();
        }
    }

    @Test
    public void test1() throws IOException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                , StandardOpenOption.WRITE);
        long size = fileChannel.size();
        boolean isOpen = fileChannel.isOpen();
        log.info("size:{},isOpen:{}", size, isOpen);
    }

    @Test
    public void testLock4() throws InterruptedException, ExecutionException {

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Callable<String>> callables = new ArrayList<>();

        Callable<String> callable = () -> {
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                    , StandardOpenOption.WRITE, StandardOpenOption.READ);
            try {
                fileChannel.lock("my attachment", new CompletionHandler<FileLock, String>() {
                    @Override
                    public void completed(FileLock result, String attachment) {
                        log.info("thread:{} get lock", Thread.currentThread().getName());
                        try {
                            Thread.sleep(3000);
                            result.release();
                        } catch (InterruptedException | IOException e) {
                            e.printStackTrace();
                        }
                        log.info("thread:{} release lock", Thread.currentThread().getName());
                    }

                    @Override
                    public void failed(Throwable exc, String attachment) {
                        log.info("thread:{} fail to get lock", Thread.currentThread().getName());
                    }
                });
            } catch (OverlappingFileLockException e) {
                log.info("catch e thread:{} fail to get lock", Thread.currentThread().getName());
            }
            return "ok";
        };

        callables.add(callable);
        callables.add(callable);
        List<Future<String>> futures = executorService.invokeAll(callables);
        // 同步
        for (Future<String> future : futures) {
            future.get();
        }
    }

    @Test
    public void testLock5() throws IOException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                , StandardOpenOption.WRITE, StandardOpenOption.READ);
        fileChannel.close();
        fileChannel.lock("my attachment", new CompletionHandler<FileLock, String>() {
            @Override
            public void completed(FileLock result, String attachment) {
                log.info("thread:{} get lock", Thread.currentThread().getName());
                try {
                    result.release();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                log.info("thread:{} release lock", Thread.currentThread().getName());
            }

            @Override
            public void failed(Throwable exc, String attachment) {
                log.info("thread:{} fail to get lock", Thread.currentThread().getName());
            }
        });
    }

    @Test
    public void processA() throws IOException, InterruptedException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                , StandardOpenOption.WRITE, StandardOpenOption.READ);
        fileChannel.lock("my attachment", new CompletionHandler<FileLock, String>() {
            @Override
            public void completed(FileLock result, String attachment) {
                log.info("thread:{} get lock", Thread.currentThread().getName());
                try {
                    Thread.sleep(5000);
                    result.release();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                log.info("thread:{} release lock", Thread.currentThread().getName());
            }

            @Override
            public void failed(Throwable exc, String attachment) {
                log.info("thread:{} fail to get lock", Thread.currentThread().getName());
            }
        });
        Thread.sleep(10000);
    }

    @Test
    public void processB() throws IOException, InterruptedException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                , StandardOpenOption.WRITE, StandardOpenOption.READ);
        fileChannel.lock("my attachment", new CompletionHandler<FileLock, String>() {
            @Override
            public void completed(FileLock result, String attachment) {
                log.info("thread:{} get lock", Thread.currentThread().getName());
                try {
                    Thread.sleep(5000);
                    result.release();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                log.info("thread:{} release lock", Thread.currentThread().getName());
            }

            @Override
            public void failed(Throwable exc, String attachment) {
                log.info("thread:{} fail to get lock", Thread.currentThread().getName());
            }
        });
        Thread.sleep(10000);
    }

    @Test
    public void testRead1() throws IOException, ExecutionException, InterruptedException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                , StandardOpenOption.WRITE, StandardOpenOption.READ);
        ByteBuffer byteBuffer = ByteBuffer.allocate(22);
        Future<Integer> future = fileChannel.read(byteBuffer, 0);
        Integer size = future.get();
        if (size > 0) {
            log.info("size:{}", size);
        }
        fileChannel.close();
        log.info("result:{}", new String(byteBuffer.array()));
    }

    @Test
    public void testRead2() throws IOException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/第6章AIO的使用.md")
                , StandardOpenOption.WRITE, StandardOpenOption.READ);
        ByteBuffer byteBuffer = ByteBuffer.allocate(22);

        Phaser phaser = new Phaser(2);

        fileChannel.read(byteBuffer, 0, "my attach", new CompletionHandler<Integer, String>() {
            @Override
            public void completed(Integer result, String attachment) {
                log.info("size:{}, attach:{}", result, attachment);
                log.info("result:{}", new String(byteBuffer.array()));
                phaser.arrive();
            }

            @Override
            public void failed(Throwable exc, String attachment) {
                log.info("exc:{}, attach:{}", exc, attachment);
                phaser.arrive();
            }
        });
        phaser.arriveAndAwaitAdvance();
        fileChannel.close();
    }

    @Test
    public void testWrite1() throws IOException, ExecutionException, InterruptedException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(
                Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/file/a.txt")
                , StandardOpenOption.WRITE, StandardOpenOption.READ);
        ByteBuffer byteBuffer = ByteBuffer.wrap("朱开生".getBytes());
        Future<Integer> future = fileChannel.write(byteBuffer, 0);
        Integer size = future.get();
        if (size > 0) {
            log.info("size:{}", size);
        }
        fileChannel.close();
        log.info("result:{}", new String(byteBuffer.array()));
    }

    @Test
    public void testWrite2() throws IOException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(
                Paths.get("/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter06/file/a.txt")
                , StandardOpenOption.WRITE, StandardOpenOption.READ);
        ByteBuffer byteBuffer = ByteBuffer.wrap("朱开生".getBytes());

        Phaser phaser = new Phaser(2);

        fileChannel.write(byteBuffer, 0, "my attach", new CompletionHandler<Integer, String>() {
            @Override
            public void completed(Integer result, String attachment) {
                log.info("size:{}, attach:{}", result, attachment);
                log.info("result:{}", new String(byteBuffer.array()));
                phaser.arrive();
            }

            @Override
            public void failed(Throwable exc, String attachment) {
                log.info("exc:{}, attach:{}", exc, attachment);
                phaser.arrive();
            }
        });
        phaser.arriveAndAwaitAdvance();
        fileChannel.close();
    }
}
