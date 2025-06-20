package com.hmdp.service;

import com.hmdp.dto.Result;

import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    //List<ShopType> queryTypeList();//我自己写的有问题

    Result queryShopTypeList();

    Result queryShopTypeString();
}
