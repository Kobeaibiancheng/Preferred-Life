package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import jdk.nashorn.internal.ir.CallNode;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //判断是关注还是取关
        if (isFollow){
            //如果是关注，添加数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            save(follow);
        }else{
            //如果是取关，就删除数据  delete from tb_follow where user_id = ? and follow_user_id = ?
            remove(new QueryWrapper<Follow>()
                    .eq("follow_user_id", followUserId).eq("user_id", userId));
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();

        //select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("follow_user_id", followUserId).eq("user_id", userId).count();

        //大于0  代表关注了
        return Result.ok(count > 0);
    }
}
