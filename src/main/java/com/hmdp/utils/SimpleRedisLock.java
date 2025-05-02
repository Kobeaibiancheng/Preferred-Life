package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;//锁的名称，不同的业务，有不同的分布式锁，由用户传递
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString()+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(Long timeOutSec) {
        //获取当前线程的id,拼接成UUID+id
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        
        //获取锁
        String lockName = KEY_PREFIX + name;//锁的名称
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockName, threadId, timeOutSec, TimeUnit.MINUTES);

        return Boolean.TRUE.equals(success);
    }

    /*@Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String lockName = KEY_PREFIX + name;//锁的名称
        //锁里面存的线程id
        String string = stringRedisTemplate.opsForValue().get(lockName);
        //判断是否是自己创建的锁
        if (threadId.equals(string)){
            //如果是
            //释放锁
            stringRedisTemplate.delete(lockName);
        }
    }*/

    /**
     * 通过lua脚本实现判断锁和释放锁的原子性
     */
    @Override
    public void unlock() {
        String lockName = KEY_PREFIX + name;//锁的名称
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockName),threadId);
    }
}
