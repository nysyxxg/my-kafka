import java.util.ArrayList;
import java.util.List;

public class TestListadd {
    
    public static void main(String[] args) {
        List<String> list = new ArrayList<String>();
        list.add("100");
        list.add("200");
        list.add("300");
    
        list.add(0,"001");
        for (String str : list) {
            System.out.println(str);
        }
        System.out.println("-------------------------------");
        list.remove(0);
        for (String str : list) {
            System.out.println(str);
        }
        
    }
}
