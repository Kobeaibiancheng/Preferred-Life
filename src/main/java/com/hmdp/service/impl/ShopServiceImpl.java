package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
    @Override
    public Result queryById(Long id) {
        //1.从redis中查询商铺缓存
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
        return Result.ok(shop);
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
