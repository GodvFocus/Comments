package com.hmdp.service.impl;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.redisson.api.GeoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanCursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
//        避免缓存穿透
//        Shop shop = cacheClient.getCacheWithNull(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);

        // 逻辑过期方式查询
        Shop shop = cacheClient.getCacheWithLogic(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);

        if(shop == null)
            return Result.fail("商铺不存在");
        return Result.ok(shop);
    }



    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 获取店铺id
        Long id = shop.getId();
        // id判空
        if(id == null){
            return Result.fail("店铺ID为空");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    /**
     * 根据类型查询商铺信息
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. 判断是否有坐标
        if(x == null || y == null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 2. 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3. 查询redis
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().radius(
                key, new Circle(new Point(x, y), new Distance(5000, RedisGeoCommands
                        .DistanceUnit.METERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
        );
        // 4. 解析出id
        if(geoResults == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> resultsContent = geoResults.getContent();
        // 判断是否已经到末尾了
        if(resultsContent.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(resultsContent.size());
        Map<String, Distance> distanceMap = new HashMap<>(resultsContent.size());
        resultsContent.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5. 根据id查询shop
        String idsStr = StrUtil.join(",", ids);
        List<Shop> shopList = query().in("id", ids).last("order by field(id," + idsStr + ")").list();
        for (Shop shop : shopList) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6. 返回
        return Result.ok(shopList);
    }
}
