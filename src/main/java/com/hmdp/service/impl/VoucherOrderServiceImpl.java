package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private RedisWorker redisWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    // 创建阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;

    // 在类初始化完毕后执行
    @PostConstruct
    private void init(){
       SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                // 1. 获取队列中的订单
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2. 创建订单
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("订单生成失败:", e);
                }
            }
        }
    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder) throws InterruptedException {
        Long userId = voucherOrder.getUserId();
        // 对用户id上锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 1.尝试获取锁
        boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
        if(!isLock){
            // 未获得锁说明该用户已经有进程在下单
            log.error("一人仅限购买一单");
            return;
        }
        try {
            // 2.获得锁则执行事务
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 秒杀券下单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucherOrder(Long voucherId) {
        // 1. 执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString()
        );
        // 2. 判断结果是否为0
        int resultVaule = result.intValue();
        // 2.1 不为0，分两种情况发送异常报告
        if(resultVaule != 0){
            return Result.fail(resultVaule == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2 为0生成订单id
        long orderId = redisWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setId(orderId);
        // 2.3 将订单id、用户id、优惠券id加入阻塞队列
        orderTasks.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 判断是否已经购买过
        int userOrderCount = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(userOrderCount > 0){
            log.error("不能重复下单");
            return;
        }
        // 4. 扣减库存
        boolean isSuccess = seckillVoucherService.update()
                // 直接在 SQL 层面操作字段值
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if(!isSuccess){
            log.error("库存不足");
            return;
        }
        // 5. 创建订单
        save(voucherOrder);
    }

}
