package com.hmdp.redisIdWorkerTest;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class RedisIdWorkerTest {

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    /**
     * 测试RedisIdWorker
     * 并发测试 - 300个线程，每个线程生成100个ID，总共30000个ID
     * 性能测试 - 统计生成30000个ID的总耗时
     * 唯一性验证 - 通过打印ID可以观察是否有重复
     * 使用CountDownLatch - 确保所有线程执行完毕后统计时间
     */
    @Test
    public void testIdWorker() throws InterruptedException {
        //我准备了 300 个线程任务，每执行完一个，就把这个计数减 1
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            //每个线程执行完后调用：意思是：“我执行完了！”
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //主线程等待 latch 为 0 时再执行：意思是：“等我执行完！”
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
}