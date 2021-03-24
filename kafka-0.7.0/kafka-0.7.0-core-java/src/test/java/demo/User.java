package demo;


public class User {

        public static void main(String[] args) {
            Control control = new Control();
//            control.invoke(1);
//            control.invoke(MyTool.class,1);

            control.invoke(User.class,"test", null);
//
//            control.invoke(MyTool.class,"c",String.class,"123");
        }
        
        public void test(){
            System.out.println("----------my---user -----test------------");
        }
        
    }