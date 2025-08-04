package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);

        //当前时间 - 固定起始时间（2022年1月1日零点的时间戳）
        //直接用完整时间戳会导致 ID 占位太大、浪费空间、影响数据库索引性能，还可能导致前端丢精度。
        //通过减去一个固定起始时间，可以压缩 ID 长度，提高系统整体性能和兼容性，这是生成高性能全局 ID 的常规优化做法。
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.将时间戳左移 COUNT_BITS（一般为32），为序列号腾出低位空间，
        // 然后用或运算拼接上序列号，生成全局唯一ID
        return timestamp << COUNT_BITS | count;
    }
}
