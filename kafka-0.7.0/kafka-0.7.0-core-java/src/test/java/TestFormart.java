public class TestFormart {
    public static void main(String[] args) {
        String str = String.format("reset fetch offset of ( %s ) to %d", "xxg", 120);
        System.out.println(str);
    
        System.out.println("-----------------------------------------------------------");
    
    
        String str2 = "reset fetch offset of ( %s ) to %d".format("xxg", 120);
        System.out.println(str2);
    }
}
