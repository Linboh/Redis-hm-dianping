package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static org.apache.logging.log4j.message.MapMessage.MapFormat.JSON;

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
    private  StringRedisTemplate redisTemplate;

    @Autowired
    private ShopMapper shopMapper;

    /**
     * 根据id查询店铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryShopById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = redisTemplate.opsForValue().get(key);

        // Step 1：缓存命中
        if (shopJson != null) {
            if ("null".equals(shopJson)) return Result.fail("店铺不存在"); // 缓存穿透
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // Step 2：查询数据库
        Shop Shop = shopMapper.selectById(id);
        if (Shop == null) {
            // 缓存空对象防止穿透，过期时间短
            redisTemplate.opsForValue().set(key, "null", Duration.ofMinutes(5));
            return Result.fail("店铺不存在");
        }

        // Step 3：写入缓存
        Shop shop = BeanUtil.copyProperties(Shop, Shop.class);
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), Duration.ofMinutes(30));
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
        redisTemplate.delete(key);

        return Result.ok();
    }

}
