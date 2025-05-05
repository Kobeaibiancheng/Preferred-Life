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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

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


    /*private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while(true){

                try {
                    //获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //创建订单
                    handerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
            }
        }

        
    }

    private void handerVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户id
        Long userId = voucherOrder.getUserId();
        *//**
         * redisson的分布式锁
         *//*
        //创建redisson对象
        RLock lock = redissonClient.getLock("lock:order" + userId);
        boolean tryLock = lock.tryLock();



        //判断锁是否获取成功
        if(!tryLock){
            //获取锁失败，不成功怎么办
            log.error( ("一个人只允许下一单!"));
            return;
        }else {
            //获取锁成功，下单
            try{
                //下单
                //return createVoucherOrder(voucherId);
            }finally {
                lock.unlock();//业务完成释放锁
            }
        }

    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString());
        //2.判断结果是否为0
        int r = result.intValue();
        if (r != 0){
            //3.不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //4.为0，有购买资格，把下单信息保存到阻塞队列
        Long orderId = redisIdWorker.nextId("order");
        //TODO 保存阻塞队列
        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        //代金卷id
        voucherOrder.setVoucherId(voucherId);
        //放入阻塞队列中
        orderTasks.add(voucherOrder);

        return Result.ok(orderId);
    }*/




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


        /**
         * 基于redis自己实现的分布式锁
         */
        //创建redis分布式锁对象
        /*SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order" + userId);
        boolean tryLock = lock.tryLock(5l);*/


        /**
         * redisson的分布式锁
         */
        //创建redisson对象
        RLock lock = redissonClient.getLock("lock:order" + userId);
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
