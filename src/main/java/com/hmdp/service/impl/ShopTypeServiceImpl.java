package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 查询店铺类型
     * @return
     */
    @Override
    public Result queryList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        // 1. 从redis查询缓存
        String typeJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 如果存在，直接返回
        if(StrUtil.isNotBlank(typeJson)){
            List<ShopType> typeList = JSONUtil.toList(typeJson, ShopType.class);
        }
        // 3. redis内不存在，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 4. 数据库中未找到直接返回
        if(typeList == null || typeList.size() == 0){
            return Result.fail("未查询到结果");
        }
        // 5. 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        // 6. 返回
        return Result.ok(typeList);
    }
}
