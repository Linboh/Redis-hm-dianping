package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.aop.framework.AopContext;

import javax.annotation.Resource;
import java.time.LocalDateTime;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 秒杀下单逻辑：一人一单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }

        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }

        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }

        // 5.一人一单逻辑
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            // 获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    /**
     * 创建订单
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.1.判断当前用户是否已经购买过（一人一单）
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过了！");
        }

        // 5.2.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // 乐观锁，只有库存大于0才能扣减
                .update();

        if (!success) {
            return Result.fail("库存不足！");
        }

        // 5.3.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 5.3.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 5.3.2.用户id
        voucherOrder.setUserId(userId);
        // 5.3.3.代金券id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);

        // 5.4.返回订单id
        return Result.ok(orderId);
    }
}
