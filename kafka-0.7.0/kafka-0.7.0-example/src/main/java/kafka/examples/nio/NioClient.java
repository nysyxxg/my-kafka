package kafka.examples.nio;

import kafka.examples.util.NioUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NioClient {

    public static void main(String[] args) {
        try{
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(8899));//服务端就是bind  然后accept  serverSocketChannel

            Selector selector = Selector.open();

            socketChannel.register(selector, SelectionKey.OP_CONNECT);//注册连接事件

            while(true) {
                int number = selector.select();

                if(number > 0) {
                    Set<SelectionKey> selectionKeySet =  selector.selectedKeys();

                    Iterator<SelectionKey> iterable = selectionKeySet.iterator();
                    while(iterable.hasNext()) {//有事件发生
                        SelectionKey selectionKey = iterable.next();

                        SocketChannel client = (SocketChannel) selectionKey.channel();
                        if(selectionKey.isConnectable()) {//判断 selectionkey 状态  可连接的
                            if(client.isConnectionPending()) {//是否在准备连接的进程中
                                client.finishConnect();//这里会阻塞，如果连接未建立，抛异常 ，

                                ByteBuffer byteBuffer = ByteBuffer.allocate(1024); // 申请缓冲区
                                byteBuffer.put((LocalDateTime.now() + "，连接成功").getBytes()); // 将数据放入缓冲区中
                                // 在写模式下调用flip方法，那么limit就设置为了position当前的值(即当前写了多少数据),postion会被置为0，以表示读操作从缓存的头开始读。
                                byteBuffer.flip();//  // 将Buffer从写模式切换到读模式（必须调用这个方法）
                                client.write(byteBuffer);
                                
                                // 创建一个线程池
                                ExecutorService executorService = Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());
                                executorService.submit(() -> {//起一个新的线程，去接收控制台的输入 ，不影响其他线程
                                    while(true) {
                                        try{
                                            byteBuffer.clear();
                                            // 从控制台获取输入流数据
                                            InputStreamReader inputStreamReader = new InputStreamReader(System.in);
                                            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                                            byteBuffer.put(bufferedReader.readLine().getBytes());
                                            byteBuffer.flip();
                                            client.write(byteBuffer);

                                        }catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            }

                            iterable.remove();//这个事件清楚，很关键
                            client.register(selector, SelectionKey.OP_READ);//注册读事件
                        } else if(selectionKey.isReadable()){//可读取
                            SocketChannel socketChannel1 = (SocketChannel) selectionKey.channel();
                            
                            //  方法1： 接收服务器端发送的消息的时候，一次性申请最大内存
//                            ByteBuffer readBuffer = ByteBuffer.allocate(1024);
//                            int readCount = socketChannel1.read(readBuffer);
                            
                            // 方法2：每次读取少量数据，申请最小内存，多次读取
                            ByteBuffer readBuffer = NioUtil.getDataByteBuffer(socketChannel);
                            int readCount = readBuffer.capacity();
                            System.out.println(" ============读到的字节数=======readCount=" + readCount);
                            
                            if(readCount > 0) {
                                String receiveMsg = new String(readBuffer.array());
                                System.out.println("receiveMsg : " + receiveMsg);
                            }
                            iterable.remove();
                        }
                    }
                }
            }
        }catch (Exception e ) {
            e.printStackTrace();
        }
    }
}