package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        /**
         * 直接查询mysql
         * 通过mybatis-plus实现的mysql查询
         */
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);

        /**
         * 添加redis缓存  list
         * 现在需要对这个店铺类型添加redis缓存,添加的时候选择key——list的数据结构
         */
        //Result result = typeService.queryShopTypeList();
        //return Result.ok(result);


        /**
         * 添加redis缓存
         * 将查询到的店铺类型List保存在为String类型的redis中
         */
        /*Result result = typeService.queryShopTypeString();
        return Result.ok(result);*/

    }
}
