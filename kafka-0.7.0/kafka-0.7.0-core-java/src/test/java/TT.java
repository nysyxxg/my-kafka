import java.util.function.Function;

public class TT {
    public  static  void main(String[] args) {
        
        Function  function1 =  new Function() {
            @Override
            public Object apply(Object o) {
                System.out.println("1");
                mytest();
                return null;
            }
        };
    
    
        Function  function2 =  new Function() {
            @Override
            public Object apply(Object o) {
                System.out.println("2");
                try {
                    mytest3();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        
        test(function1,function2);
    
    }
    
    static void  test( Function s1, Function s2){
        try {
            s1.apply(null);
            s2.apply(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void mytest(){
        System.out.println("----mytest--------------");
    }
    
    public static void mytest3() throws Exception {
        System.out.println("--------------mytest3--------");
        throw  new Exception("出错了！！！");
    }
    
    public static void mytest4(){
        System.out.println("----mytest4--------------");
    }
}