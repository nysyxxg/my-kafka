package kafka.utils;


import java.text.SimpleDateFormat;
import java.util.Date;

public class SystemTime implements Time {
    
    public   Long milliseconds() {
        return System.currentTimeMillis();
    }
    
    public static Long getMilliseconds() {
        return System.currentTimeMillis();
    }
    
    public Long nanoseconds() {
        return System.nanoTime();
    }
    
    public static void sleepByTime(Long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void sleep(Long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public static  String  getDateFormat(){
        Date date = new Date();
        String strDateFormat = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(strDateFormat);
        return sdf.format(date);
    }
    
    public static void main(String[] args){
        System.out.println(SystemTime.getDateFormat());
    }
    
}
