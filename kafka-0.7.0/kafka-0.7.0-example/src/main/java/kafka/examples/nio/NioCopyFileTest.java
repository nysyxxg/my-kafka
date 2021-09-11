package kafka.examples.nio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

public class NioCopyFileTest {
    
    public static void fileChannelCopy(String sfPath, String tfPath) {
        File sf = new File(sfPath);
        File tf = new File(tfPath);
        FileInputStream fi = null;
        FileOutputStream fo = null;
        FileChannel in = null;
        FileChannel out = null;
        try {
            fi = new FileInputStream(sf);
            fo = new FileOutputStream(tf);
            in = fi.getChannel();//得到对应的文件通道
            out = fo.getChannel();//得到对应的文件通道
            in.transferTo(0, in.size(), out);//连接两个通道，并且从in通道读取，然后写入out通道
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fi.close();
                in.close();
                fo.close();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        String sPath = "D:\\常用软件\\JDK1.8\\jdk-8u181-linux-x64.tar.gz";
        String tPath = "D:\\常用软件\\JDK1.8\\NIO.tar.gz";
        fileChannelCopy(sPath, tPath);
        long end = System.currentTimeMillis();
        System.out.println("用时为：" + (end - start));
    }
}
