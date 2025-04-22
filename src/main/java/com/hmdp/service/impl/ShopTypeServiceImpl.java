package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;//将这个注入操作redis这个对象注入一下（Resource）

    @Resource
    private IShopTypeService typeService;

    /**
     * 为店铺信息添加redis缓存来实现店铺类型的查询
     *
     * 这是我自己写的，有问题
     * @return
     */
    /*@Override
    public List<ShopType> queryTypeList() {
        //1.从redis中查询店铺类型缓存
        Long size = stringRedisTemplate.opsForList().size("shop:type:");
        //2.判断是否存在
        if (size.intValue() == 1) {
            //3.存在，直接返回结果
            List<String> typeJson = stringRedisTemplate.opsForList().range("shop:type:", 0, -1);

            List<ShopType> ret = new ArrayList<>();
            int k = typeJson.size();
            while (k > 0) {
                ShopType shopType = JSONUtil.toBean(typeJson.get(k-1), ShopType.class);
                ret.add(shopType);
                k--;
            }
            return ret;
        }

        //4.不存在，将查询结果添加到redis中
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();


        stringRedisTemplate.opsForList().leftPushAll("shop:type:",JSONUtil.toJsonStr(typeList));
        //5.返回查询的结果
        return typeList;
    }*/

    @Override
    public Result queryShopTypeList() {
        //String key = CACHE_SHOP_TYPE_KEY; // CACHE_SHOP_TYPE_KEY = "cache:shopType";

        // 1.从 Redis 中查询商铺缓存
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);

        // 2.判断 Redis 中是否有该缓存
        if (shopTypeJsonList != null && !shopTypeJsonList.isEmpty()) {
            // 2.1.若 Redis 中存在该缓存，则直接返回
            ArrayList<ShopType> shopTypes = new ArrayList<>();
            for (String str : shopTypeJsonList) {
                shopTypes.add(JSONUtil.toBean(str, ShopType.class));
            }
            return Result.ok(shopTypes);
        }
        // 2.2.Redis 中若不存在该数据，则从数据库中查询
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 3.判断数据库中是否存在
        if (shopTypes == null || shopTypes.isEmpty()) {
            // 3.1.数据库中也不存在，则返回 false
            return Result.fail("分类不存在！");
        }

        // 3.3.2.1.数据库中存在，则将查询到的信息存入 Redis
        for (ShopType shopType : shopTypes) {
            stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopType));
        }

        // 3.3.2.2.返回
        return Result.ok(shopTypes);







        /*// 1.从 Redis 中查询商铺缓存,                                          //获取redis中key为CACHE_SHOP_TYPE_KEY 的list中的所有元素
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        // 2.判断 Redis 中是否有该缓存
        if (shopTypeJsonList != null && !shopTypeJsonList.isEmpty()) {
            // 3.若 Redis 中存在该缓存，则直接返回
            List<ShopType> shopTypes = new ArrayList<>();
            for (String str : shopTypeJsonList) {
                shopTypes.add(JSONUtil.toBean(str, ShopType.class));
            }
            return Result.ok(shopTypes);
        }
        //4.redis中不存在，从mysql中查找
        List<ShopType> shopTypes = typeService
                .query().orderByAsc("sort").list();


        // 5.判断数据库中是否存在
        if (shopTypes == null || shopTypes.isEmpty()) {
            // 6.数据库中也不存在，则返回 false
            return Result.fail("分类不存在！");
        }

        // 7.数据库中存在，则将查询到的信息存入 Redis
        for (ShopType shopType : shopTypes) {
            *//**
             * 下面这两种方式是有区别的，上面是直接将整个list转化为json字符串，下面是将list中的每一个对象转化为json字符串
             *//*
            //stringRedisTemplate.opsForList().rightPushAll("shop:type:",JSONUtil.toJsonStr(shopTypes));
            stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopType));
        }
        return Result.ok(shopTypes);*/
    }

    @Override
    public Result queryShopTypeString() {
        //1.从redis中查询店铺类型缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        // 2.判断 Redis 中是否存在数据
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 2.1.存在，则返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        /*if (shopTypeJson != null && !shopTypeJson.isEmpty()) {
            //2.存在，直接返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }*/



        //3.不存在，从mysql中查询
        List<ShopType> shopTypes = typeService
                .query().orderByAsc("sort").list();

        //4.判断mysql中是否有数据
        //5.不存在，直接返回
        if (shopTypes == null || shopTypes.isEmpty()) {
            return Result.fail("分类不存在！");
        }
        //6.存在，保存到redis中，并且放回查询到的结果
        String shopTypesJson = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY,shopTypesJson);
        return Result.ok(shopTypes);
    }
}
