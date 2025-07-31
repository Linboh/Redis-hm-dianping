package com.hmdp.utils;

/**
 * 常量定义
 */
public class RedisConstants {
    //验证码
    public static final String LOGIN_CODE_KEY = "login:code:";

    //验证码过期时间
    public static final Long LOGIN_CODE_TTL = 2L;

    //用户token
    public static final String LOGIN_USER_KEY = "login:token:";

    //用户token过期时间
    public static final Long LOGIN_USER_TTL = 36000L;

    //空值过期时间
    public static final Long CACHE_NULL_TTL = 2L;

    //商铺缓存过期时间
    public static final Long CACHE_SHOP_TTL = 30L;

    //商铺缓存key
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    //商铺互斥锁key
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    //商铺互斥锁过期时间
    public static final Long LOCK_SHOP_TTL = 10L;

    //秒杀库存key
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
}
