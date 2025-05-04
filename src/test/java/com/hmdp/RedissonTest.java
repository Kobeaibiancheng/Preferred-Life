package com.hmdp;


import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
public class RedissonTest {
    @Resource
    private RedissonClient redissonClient;

/*    @Resource
    private RedissonClient redissonClient2;
    @Resource
    private RedissonClient redissonClient3;*/

    private RLock lock;
    private RLock lock2;
    private RLock lock3;
    @BeforeEach
    void setUp(){
        lock = redissonClient.getLock("order");
/*        lock2 = redissonClient2.getLock("order");
        lock3 = redissonClient3.getLock("order");*/
    }

    @Test
    void method1() throws InterruptedException {
        boolean islock = lock.tryLock(1l, TimeUnit.MINUTES);
        if (!islock){
            log.info("获取锁失败！。。。1");
            return;
        }
        try{
            log.info("获取锁成功！。。。1");
            method2();
        }finally {
            log.info("释放锁！。。。1");
            lock.unlock();
        }
    }


    void method2() throws InterruptedException {
        boolean islock = lock.tryLock(1l, TimeUnit.MINUTES);
        if (!islock){
            log.info("获取锁失败！。。。2");
            return;
        }
        try{
            log.info("获取锁成功！。。。2");
        }finally {
            log.info("释放锁！。。。2");
            lock.unlock();
        }
    }


}
