import java.nio.ByteBuffer;

public class TestByteBuffer {
    
    public static void main(String[] args) {
//        System.out.println("------1---储存Long类型--8个字节-------------");
//        ByteBuffer  byteBuffer = ByteBuffer.allocate(8);
//        byteBuffer.putLong(10000);
//        byteBuffer.flip();
//        System.out.println(byteBuffer.getLong());
//
//
//        System.out.println("------2---储存double类型--8个字节-------------");
//        ByteBuffer  byteBuffer2 = ByteBuffer.allocate(8);
//        byteBuffer2.putDouble(10000);
//        byteBuffer2.flip();
//        System.out.println(byteBuffer2.getDouble());
//
    
//        System.out.println("------3---储存 Int 类型--4个字节-------------");
//        ByteBuffer  byteBuffer3 = ByteBuffer.allocate(4);
//        byteBuffer3.putInt(10000);
//        byteBuffer3.flip();
//        System.out.println(byteBuffer3.getInt());
//
//        System.out.println("----4-----储存 float 类型--4个字节-------------");
//        ByteBuffer  byteBuffer4 = ByteBuffer.allocate(4);
//        byteBuffer4.putFloat(10000);
//        byteBuffer4.flip();
//        System.out.println(byteBuffer4.getFloat());
    
    
    
//        System.out.println("-----5----储存 short 类型--4个字节-------------");
//        ByteBuffer  byteBuffer5 = ByteBuffer.allocate(2);
//        byteBuffer5.putShort((short) 10001);
//        byteBuffer5.flip();
//        System.out.println(byteBuffer5.getShort());
//
//        System.out.println("-----6----储存 char 类型--4个字节-------------");
//        ByteBuffer  byteBuffer6 = ByteBuffer.allocate(2);
//        byteBuffer6.putChar('许');
//        byteBuffer6.flip();
//        System.out.println(byteBuffer6.getChar());
    
    
        System.out.println("-----7----储存 char 类型--4个字节-------------");
        ByteBuffer  byteBuffer7 = ByteBuffer.allocate(1);
        byteBuffer7.put((byte) 123);
        byteBuffer7.flip();
        System.out.println(byteBuffer7.get());
    }
    
    
}
