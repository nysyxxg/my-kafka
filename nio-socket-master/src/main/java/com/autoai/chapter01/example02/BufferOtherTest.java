package com.autoai.chapter01.example02;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;

/**
 * @Author:
 * @Date:
 * @Description:
 */
@Slf4j
public class BufferOtherTest {
    
    /**
     * 验证1：
     * 缓冲区的capacity不能为负数
     */
    @Test
    public void test1() {
        try {
            ByteBuffer.allocate(-1);
        } catch (Exception e) {
            System.out.println("ByteBuffer 的容量 capacity 大小，不能为负值！");
            log.error(e.toString());
        }
    }
    
    /**
     * 验证2：
     * 缓冲区的limit不能为负数
     */
    @Test
    public void test2() {
        try {
            byte[] bytes = new byte[]{1, 2};
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            byteBuffer.limit(-1);
        } catch (Exception e) {
            System.out.println("ByteBuffer  限制limit 大小 不能为 负数！！");
            log.error(e.toString());
        }
    }
    
    /**
     * 缓冲区的position不能为负数
     */
    @Test
    public void test3() {
        try {
            byte[] bytes = new byte[]{1, 2};
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            byteBuffer.position(-1);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }
    
    /**
     * 验证：position不能大于其limit。
     */
    @Test
    public void test4() {
        try {
            byte[] bytes = new byte[]{1, 2, 3};
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            log.info("limit:{},position:{}", byteBuffer.limit(), byteBuffer.position());
            byteBuffer.limit(1);
            byteBuffer.position(2);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }
    
    /**
     * 验证：limit不能大于其capacity。
     */
    @Test
    public void test5() {
        try {
            byte[] bytes = new byte[]{1, 2, 3};
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            log.info("limit:{},capacity:{}", byteBuffer.limit(), byteBuffer.capacity());
            byteBuffer.limit(400);
        } catch (Exception e) {
            System.out.println("ByteBuffer 的  limit不能大于其 capacity  容量！！！");
            log.error(e.toString());
        }
    }
    
    /**
     * 如果定义了mark，则在将position调整为不小于该mark的值时，该mark不丢弃。
     */
    @Test
    public void test6() {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.position(1);
        byteBuffer.mark();
        log.info("position:{}", byteBuffer.position());
        System.out.println(" byteBuffer  在: " + byteBuffer.position() + " 位置设置mark标记..........");
        byteBuffer.position(3);
        log.info("position:{}", byteBuffer.position());
        
        byteBuffer.reset();
        log.info("position:{}", byteBuffer.position());
        System.out.println(" byteBuffer  回到: " + byteBuffer.position() + " 位置 ..........");
        
    }
    
    /**
     * 如果定义了mark，则在将position调整为小于该mark的值时，该mark被丢弃。
     */
    @Test
    public void test7() {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.position(3);
        byteBuffer.mark();
        log.info("position:{}", byteBuffer.position());
        byteBuffer.position(1);
        log.info("position:{}", byteBuffer.position());
        try {
            byteBuffer.reset();
        } catch (InvalidMarkException e) {
            System.out.println(" byteBuffer 的mark标记无效.............");
            log.error("mark abandoned");
        }
    }
    
    /**
     * 如果定义了mark，则在将limit调整为不小于该mark的值时，该mark不丢弃。
     */
    @Test
    public void test8() {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.mark();
        log.info("limit:{}", byteBuffer.limit());
        
        byteBuffer.limit(3);
        log.info("limit:{}", byteBuffer.limit());
        
        byteBuffer.reset();
        log.info("position:{}", byteBuffer.position());
    }
    
    /**
     * 如果定义了mark，则在将limit调整为不小于该mark的值时，该mark不丢弃。
     */
    @Test
    public void test8_1() {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        
        System.out.println(" A  byteBuffer position =" + byteBuffer.position() + " limit = " + byteBuffer.limit());
        byteBuffer.position(2);
        byteBuffer.mark();
        System.out.println(" B  byteBuffer position =" + byteBuffer.position() + " limit = " + byteBuffer.limit());
        
        log.info("limit:{}", byteBuffer.limit());
        
        byteBuffer.position(3);
        byteBuffer.limit(3);  // 将limit调整为不小于该mark的值时，该mark不丢弃。
        System.out.println(" C  byteBuffer position =" + byteBuffer.position() + " limit = " + byteBuffer.limit());
        
        log.info("limit:{}", byteBuffer.limit());
        
        byteBuffer.reset();
        
        System.out.println(" D  byteBuffer position =" + byteBuffer.position() + " limit = " + byteBuffer.limit());
        
        log.info("position:{}", byteBuffer.position());
    }
    
    /**
     * 如果定义了mark，则在将limit调整为小于该mark的值时，该mark被丢弃。
     */
    @Test
    public void test9() {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.position(3);
        byteBuffer.mark();
        
        System.out.println(" A  byteBuffer position =" + byteBuffer.position() + " limit = " + byteBuffer.limit());
        log.info("limit:{}", byteBuffer.limit());
        
        byteBuffer.limit(2);
        log.info("limit:{}", byteBuffer.limit());
        System.out.println(" B  byteBuffer position =" + byteBuffer.position() + " limit = " + byteBuffer.limit());
        
        try {
            byteBuffer.reset();
        } catch (InvalidMarkException e) {
            log.error("mark abandoned");
        }
    }
    
    /**
     * 验证：如果未定义mark，那么调用reset()方法将导致抛出InvalidMarkException异常。
     */
    @Test
    public void test10() {
        try {
            byte[] bytes = new byte[]{1, 2, 3};
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            byteBuffer.reset();
        } catch (InvalidMarkException e) {
            log.error("未定义mark");
        }
    }
    
    /**
     * 验证：如果position大于新的limit，则position的值就是新limit的值。
     */
    @Test
    public void test11() {
        byte[] bytes = new byte[]{1, 2, 3};
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        log.info("position:{}, limit:{}", byteBuffer.position(), byteBuffer.limit());
        byteBuffer.position(3);
        byteBuffer.limit(1);
        log.info("position:{}, limit:{}", byteBuffer.position(), byteBuffer.limit());
    }
    
    /**
     * 验证：当limit和position值一样时，在指定的position写入数据时会出现异常，因为此位置是被限制的。
     */
    @Test
    public void test12() {
        try {
            byte[] bytes = new byte[]{1, 2, 3};
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            log.info("position:{}, limit:{}", byteBuffer.position(), byteBuffer.limit());
            byteBuffer.position(3);  //
            System.out.println("byteBuffer : " + byteBuffer);
            System.out.println(" position : " + byteBuffer.position() + " limit: " + byteBuffer.limit());
            byteBuffer.put((byte) 4);
        } catch (BufferOverflowException e) {
            log.error("此位置是被限制的");
        }
    }
}
