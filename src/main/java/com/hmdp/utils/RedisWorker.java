package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorker {
    // 起始时间戳
    private static final long BEGIN_TIMESTAMP = 1704067200L;
    private static final int SHIFT_BIT = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成唯一id
     * @return
     */
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2. 生成序列号
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // redis内部value从0开始每次自增1
        long incrementId = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3. 拼接并返回
        long id = timestamp << SHIFT_BIT | incrementId;
        return id;
    }

}
