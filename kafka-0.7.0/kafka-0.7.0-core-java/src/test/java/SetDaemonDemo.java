import java.util.concurrent.TimeUnit;

public class SetDaemonDemo {
    public static void main(String[] args) {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("----睡眠一秒-----");
            }
        });
        //默认为false,设置为false代表非守护线程,主方法执行完成并不会结束.
        // true为守护线程,守护线程在主方法结束时候结束
        thread.setDaemon(true);
//        thread.setDaemon(true);当为守护线程的时候,主方法结束,守护线程就会结束.
        thread.start();
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("主线程over");
    }
}
