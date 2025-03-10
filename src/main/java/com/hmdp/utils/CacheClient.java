package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 将java对象存入redis
     * @param key
     * @param data
     * @param time
     * @param unit
     */
    public void setExpire(String key, Object data, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data), time, unit);
    }

    /**
     * 使用逻辑过期方式将java对象存入redis
     * @param key
     * @param data
     * @param time
     * @param unit
     */
    public void setExpireWithLogic(String key, Object data, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(data);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 利用缓存空值返回的方法查询缓存，解决缓存穿透
     * @param cachePrefix
     * @param id
     * @param type
     * @param dbfunction
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R getCacheWithNull(
            String cachePrefix, ID id, Class<R> type, Function<ID, R> dbfunction, Long time, TimeUnit unit){
        String key = cachePrefix + id;
        // 1. 从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 如果存在，直接返回
        if(StrUtil.isNotBlank(json)){
            // 空串即不符合条件
            R r = JSONUtil.toBean(json, type);
            return r;
        }

        if(json != null){
            // 缓存内没有通过isNotBlank，但是非null
            return null;
        }

        // 3. 不存在，根据id查询对应数据库
        R r = dbfunction.apply(id);
        // 4. 数据库中未找到直接返回
        if(r == null){
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, unit);
            return null;
        }
        // 5. 写入redis
        this.setExpire(key, r, time, unit);
        // 6. 返回
        return r;
    }


    /**
     * 利用逻辑过期方法查询缓存，解决缓存击穿
     * @param cachePrefix
     * @param id
     * @param type
     * @param dbfunction
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R getCacheWithLogic(
            String cachePrefix, ID id, Class<R> type, Function<ID, R> dbfunction, Long time, TimeUnit unit){
        String key = cachePrefix + id;
        // 1. 从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 如果不存在，直接返回空
        if(StrUtil.isBlank(json)){
            return null;
        }
        // 3. 若存在则检查过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime redisDataExpireTime = redisData.getExpireTime();
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if(redisDataExpireTime.isAfter(LocalDateTime.now())){
            // 4. 未过期则直接返回
            return r;
        }

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        // 5. 过期则申请锁
        if(tryLock(lockKey)){
            // 5.1 能上锁就申请独立线程
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    // 缓存重建
                    // 1. 查数据库
                    R r1 = dbfunction.apply(id);
                    // 2. 写缓存
                    this.setExpireWithLogic(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        // 6. 直接返回原始信息
        return r;
    }


    private boolean tryLock(String key){
        return stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
