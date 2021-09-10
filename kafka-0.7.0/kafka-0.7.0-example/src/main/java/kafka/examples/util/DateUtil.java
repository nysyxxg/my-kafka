package kafka.examples.util;

import kafka.utils.SystemTime;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {
    
    public static  String  getDateFormat(){
        Date date = new Date();
        String strDateFormat = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(strDateFormat);
        return sdf.format(date);
    }
    
    public static void main(String[] args){
        System.out.println(DateUtil.getDateFormat());
    }
    
}
