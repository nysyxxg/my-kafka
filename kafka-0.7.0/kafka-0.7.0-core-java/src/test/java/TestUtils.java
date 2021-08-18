import kafka.utils.Utils;
import org.junit.Test;

import java.util.Properties;

public class TestUtils {
    
    @Test
    public void testGetIntInRange() {
        Properties props = new Properties();
        props.put("port", "9092");
        int port = Utils.getInt(props, "port", -1);
        System.out.println(port);
        
    }
}
