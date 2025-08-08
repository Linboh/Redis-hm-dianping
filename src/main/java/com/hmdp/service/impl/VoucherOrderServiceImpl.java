package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    // 阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 代理对象
    private IVoucherOrderService proxy;

    // lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }



    /**
     * 优惠券秒杀
     * Lua脚本判断 - 原子性判断库存和一人一单
     * 有购买资格，把下单信息保存到阻塞队列,返回成功
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        //生成订单id
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        //判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }


        // 2.有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);//把下单信息保存到阻塞队列

        //获取代理对象,createVoucherOrder方法加了事务注解
        proxy = (IVoucherOrderService) AopContext.currentProxy();


        // 3.返回订单id
        return Result.ok(orderId);
    }

    // 项目启动时初始化
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 线程任务类
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    /**
     * 处理订单
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1.获取用户
        Long userId = voucherOrder.getUserId();
        // 2.创建锁对象 - 作为最后一道防线
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 3.尝试获取锁，设置较短超时时间
        boolean isLock = redisLock.tryLock();
        if (!isLock) {
            // 理论上不应该发生，记录异常情况
            log.error("异步处理订单时获取锁失败，用户ID: {}", userId);
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            redisLock.unlock();
        }
    }

    /**
     * 创建订单
     * @param voucherOrder
     * @return
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 创建订单
        save(voucherOrder);

        // 5.扣减库存
        /*Lua脚本已处理 - 库存扣减在Lua脚本中完成，具有原子性
            异步处理特点 - 进入队列的订单都是"预扣库存"成功的
            避免重复扣减 - 数据库扣减会导致库存被扣两次
            更好的做法是完全移除数据库库存扣减：可以通过定时任务定期同步Redis库存到数据库
        */
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).update();
    }


}