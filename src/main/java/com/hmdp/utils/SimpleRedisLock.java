package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;//锁的名称，不同的业务，有不同的分布式锁，由用户传递
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock:";


    @Override
    public boolean tryLock(Long timeOutSec) {
        //获取当前线程的id
        long threadId = Thread.currentThread().getId();

        
        //获取锁
        String lockName = KEY_PREFIX + name;//锁的名称
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockName, threadId + "", timeOutSec, TimeUnit.MINUTES);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //释放锁
        String lockName = KEY_PREFIX + name;//锁的名称
        stringRedisTemplate.delete(lockName);
    }
}
