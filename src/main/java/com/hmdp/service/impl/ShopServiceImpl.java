package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 根据id查询店铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryShopById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringredisTemplate.opsForValue().get(key);

        // Step 1：缓存命中
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // 判断命中的是否是空值 shopJson = ""
        if (shopJson != null) {
            return Result.fail("店铺不存在");
        }

        // Step 2：实现缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            // 获取互斥锁
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                // 获取锁失败，休眠并重试
                Thread.sleep(50);
                return queryShopById(id);
            }

            // 获取锁成功，再次检测redis缓存是否存在
            shopJson = stringredisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                shop = JSONUtil.toBean(shopJson, Shop.class);
                return Result.ok(shop);
            }

            // 查询数据库
            shop = getById(id);
            if (shop == null) {
                // 缓存空对象防止穿透
                stringredisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }

            // 写入缓存
            stringredisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lockKey);
        }

        return Result.ok(shop);
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
