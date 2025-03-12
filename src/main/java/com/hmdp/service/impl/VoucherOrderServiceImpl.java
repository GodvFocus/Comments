package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.injector.methods.SelectById;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private RedisWorker redisWorker;
    /**
     * 秒杀券下单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucherOrder(Long voucherId) {
        // 1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断时间
        LocalDateTime beginTime = voucher.getBeginTime();
        if(beginTime.isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        LocalDateTime endTime = voucher.getEndTime();
        if(endTime.isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }
        // 3.判断库存是否充足
        if(voucher.getStock() < 1){
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        // 保证只要字符串内容一样则返回的结果相同
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 判断是否已经购买过
        int userOrderCount = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(userOrderCount > 0){
            return Result.fail("不能重复下单");
        }
        // 4. 扣减库存
        boolean isSuccess = seckillVoucherService.update()
                // 直接在 SQL 层面操作字段值
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if(!isSuccess){
            return Result.fail("库存不足");
        }
        // 5. 创建订单
        long orderId = redisWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setId(orderId);
        save(voucherOrder);
        // 6. 返回订单id
        return Result.ok(orderId);
    }

}
