package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private StringRedisTemplate stringRedisTemplate;//将这个注入操作redis这个对象注入一下（Resource）

    @Resource
    private CacheClient cacheClient;//将缓存工具类注入进来


    @Override
    public Result queryById(Long id) {
        /*//1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;//店铺id，但是有个前缀RedisConstants.CACHE_SHOP_KEY
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在//StrUtil.isNotBlank(shopJson)这个函数，只有shopJson是有效的字符串，才返回true，其他都返回false
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);//将json字符串转化为java对象（Shop）
            return Result.ok(shop);
        }

        //判断命中的是否是空值
        if (shopJson != null) {
            //返回一个错误信息
            return Result.fail("店铺信息不存在");
        }


        //4.redis不存在,根据id查数据库
        Shop shop = getById(id);//根据id去数据库查
        //5.mysql中不存在
        if (shop == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return Result.fail("店铺不存在！");
        }
        //6.mysql中存在
        //将店铺信息写入redis，key-value,key=店铺id，value=shop对象转化为Json字符串
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return Result.ok(shop);*/





        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        Shop shop =
                cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //互斥锁解决缓存击穿
        /*Shop shop = queryWithPassMutex(id);
        if (shop == null){
            return Result.fail("店铺不存在！");
        }*/

        /*//利用逻辑过期来解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
*/

        if (shop == null){
            return Result.fail("店铺不存在！");
        }
        //返回
        return Result.ok(shop);
    }



    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 通过逻辑时间解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;//店铺id，但是有个前缀RedisConstants.CACHE_SHOP_KEY
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //3.不存在直接返回null
            return null;
        }
        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();//获取过期时间
        //5.判断缓存是否过期，逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //6、未过期，直接返回缓存中得店铺信息
            return shop;
        }

        //缓存重建
        //7.过期：尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock){
            //8.获取锁成功，开启独立线程进行缓存更新
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //9.获取锁失败，说明有线程拥有锁，直接返回缓存中得店铺信息
        return shop;
    }


    /**
     * 通过互斥锁解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassMutex(Long id){
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;//店铺id，但是有个前缀RedisConstants.CACHE_SHOP_KEY
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在//StrUtil.isNotBlank(shopJson)这个函数，只有shopJson是有效的字符串，才返回true，其他都返回false
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);//将json字符串转化为java对象（Shop）
            return shop;
        }

        //判断命中的是否是空值
        if (shopJson != null) {
            //返回一个错误信息
            return null;
        }


        //4.实现缓存重建
        //4.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean islock = tryLock(lockKey);
            //4.2判断是否获取成功
            if (!islock){
                //4.3失败，则休眠重试
                Thread.sleep(50);
                return queryWithPassMutex(id);
            }

            //模拟重建的延时
            Thread.sleep(200);

            //4.4成功，根据id查询数据库
            shop = getById(id);//根据id去数据库查
            //5.mysql中不存在
            if (shop == null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //6.mysql中存在
            //将店铺信息写入redis，key-value,key=店铺id，value=shop对象转化为Json字符串
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            //7.释放锁
            unlock(lockKey);

        }

        //8.返回
        return shop;


    }


    /**
     * 可能会导致缓存穿透的代码，缓存穿透的代码
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;//店铺id，但是有个前缀RedisConstants.CACHE_SHOP_KEY
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在//StrUtil.isNotBlank(shopJson)这个函数，只有shopJson是有效的字符串，才返回true，其他都返回false
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);//将json字符串转化为java对象（Shop）
            return shop;
        }

        //判断命中的是否是空值
        if (shopJson != null) {
            //返回一个错误信息
            return null;
        }


        //4.redis不存在,根据id查数据库
        Shop shop = getById(id);//根据id去数据库查
        //5.mysql中不存在
        if (shop == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.mysql中存在
        //将店铺信息写入redis，key-value,key=店铺id，value=shop对象转化为Json字符串
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return shop;
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





    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不存在！");
        }

        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
