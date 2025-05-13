package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import jdk.nashorn.internal.ir.CallNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;



    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();

        String key = "follows:" + userId;
        //判断是关注还是取关
        if (isFollow){
            //如果是关注，添加数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            //save(follow);原先的实现方式
            boolean isSuccess = save(follow);
            if (isSuccess){
                //如果关注成功，保存到redis中的set   sadd userId followerUserId
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            //如果是取关，就删除数据  delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("follow_user_id", followUserId).eq("user_id", userId));
            //把用户关注的id从set集合中移除
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
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

    @Override
    public Result followCommons(Long id) {

        //求目标用户和当前用户的共同关注交集
        //1.获取当前用户的id，找到该用户的关注set
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //2.获取目标用户的关注set
        String aidKey = "follows:" + id;
        //3.求两个set的交集
        Set<String> commonFollow = stringRedisTemplate.opsForSet().intersect(key, aidKey);
        if (commonFollow == null || commonFollow.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //4.解析出用户id
        List<Long> ids = commonFollow.stream().map(Long::valueOf).collect(Collectors.toList());

        //5.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }

}
