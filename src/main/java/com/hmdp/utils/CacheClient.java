package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component//由bean来维护
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    //通过构造函数注入
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //将任意java对象添加至redis缓存，设置过期时间，和过期类型
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }


    //将任意java对象添加至redis缓存，设置逻辑过期时间，和过期类型
    public void setWithLogicalExpire(String key, Object value, Long time,TimeUnit timeUnit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }


    //获取
    public <R,ID> R queryWithPassThrough(
            String keyPrefix , ID id, Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit timeUnit){
        //1.从redis中查询商铺缓存
        String key = keyPrefix + id;//店铺id，但是有个前缀RedisConstants.CACHE_SHOP_KEY
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在
            return JSONUtil.toBean(json, type);
        }

        /*//判断命中的是否是空值
        if (json != null) {
            //返回一个错误信息
            return null;
        }*/


        //4.redis不存在,根据id查数据库
        R r = dbFallBack.apply(id);//根据id去数据库查
        //5.不存在
        if (r == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.mysql中存在
        //将店铺信息写入redis，key-value,key=店铺id，value=shop对象转化为Json字符串
        this.set(key, r, time, timeUnit);
        //7.返回
        return r;
    }




    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 通过逻辑时间解决缓存穿透
     * @param id
     * @return
     */
    public <R,ID> R queryWithLogicalExpire
    (String keyPrefix, ID id, Class<R> type,Function<ID,R> dbFallBack,Long time,TimeUnit timeUnit){
        //1.从redis中查询商铺缓存
        String key = keyPrefix + id;//店铺id，但是有个前缀RedisConstants.CACHE_SHOP_KEY
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(json)) {
            //3.不存在直接返回null
            return null;
        }
        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();//获取过期时间
        //5.判断缓存是否过期，逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //6、未过期，直接返回缓存中得店铺信息
            return r;
        }

        //缓存重建
        //7.过期：尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock){
            //8.获取锁成功，开启独立线程进行缓存更新
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    //查询数据库
                    R r1 = dbFallBack.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,timeUnit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //9.获取锁失败，说明有线程拥有锁，直接返回缓存中得店铺信息
        return r;
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}
