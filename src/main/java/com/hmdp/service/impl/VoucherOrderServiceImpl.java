package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
            //还未开始
            return Result.fail("秒杀还未开始!");
        }
        //3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
            //已经结束
            return Result.fail("秒杀已经结束!");
        }
        //4.判断库存是否充足
        if(voucher.getStock() < 1){
            //库存不足
            return Result.fail("库存不足！");
        }



        Long userId = UserHolder.getUser().getId();
        //创建redis分布式锁对象
        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order" + userId);

        //创建redisson对象
        RLock lock = redissonClient.getLock("order" + userId);
        boolean tryLock = lock.tryLock();
        //判断锁是否获取成功
        if(!tryLock){
            //获取锁失败，不成功怎么办
            return Result.fail("一个人只允许下一单!");
        }else {
            //获取锁成功，下单
            try{
                return createVoucherOrder(voucherId);
            }finally {
                lock.unlock();//业务完成释放锁
            }
        }

    }


    @Transactional
    public  Result createVoucherOrder(Long voucherId){
        /**
         * 实现一人一单
         */
        //用户id
        Long userId = UserHolder.getUser().getId();


        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0){
            return Result.fail("用户已经下过一单了！");
        }


        //5.扣减库存
        //CAS实现的线程安全
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")// set stock = stock - 1
                //.eq("voucher_id", voucherId).eq("stock",voucher.getStock())//where id = ? and stock = ?
                .eq("voucher_id", voucherId).gt("stock",0)//where id = ? and stock > 0
                .update();
        if (!success){
            //扣减失败
            return Result.fail("库存不足");
        }


        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        voucherOrder.setUserId(userId);
        //代金卷id
        voucherOrder.setVoucherId(voucherId);

        //将订单写入数据库
        save(voucherOrder);


        return Result.ok(orderId);
    }
}
