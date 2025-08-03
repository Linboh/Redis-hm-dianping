package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.RedisData;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public  class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Autowired
    private  StringRedisTemplate stringredisTemplate;

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR
            = Executors.newFixedThreadPool(10);

    @Override
    public Result queryShopById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringredisTemplate.opsForValue().get(key);

        // Step 1：判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            return Result.fail("店铺不存在");
        }

        // Step 2：命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // Step 3：判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return Result.ok(shop);
        }

        // Step 4：已过期，需要缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // Step 5：判断是否获取锁成功
        if (isLock) {
            // 获取锁成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        // Step 6：返回过期的商铺信息
        return Result.ok(shop);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1.查询店铺数据
        Shop shop = getById(id);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入Redis
        stringredisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 更新店铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            throw new RuntimeException("店铺id不能为空");
        }

        // 1. 更新数据库
        updateById(shop);

        // 2. 删除缓存
        String key = CACHE_SHOP_KEY + id;
        stringredisTemplate.delete(key);

        return Result.ok();
    }

    /**
     * 获取锁
     * @param key 锁的key
     * @return true代表获取锁成功; false代表获取锁失败
     */
    private boolean tryLock(String key) {
        Boolean flag = stringredisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key 锁的key
     */
    private void unlock(String key) {
        stringredisTemplate.delete(key);
    }

}
