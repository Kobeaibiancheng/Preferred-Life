package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogByid(Long id) {
        //1.根据id查询blog
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("该笔记不存在！");
        }
        //2.查到了，根据笔记查询用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);//根据用户id去数据库中查询用户信息

        isBlogLiked(blog);


        // 将用户信息写入该bolg对象
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        blog.setLevel(user.getLevel());


        return Result.ok(blog);
    }


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());

            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 判断是否已经点赞
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        
        //有可能没有登陆
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null){
            //用户没有登录就不用查询点赞
            return;
        }
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        //1.判断当前用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null) {
            //已点赞
            blog.setIsLike(true);
        }else {
            blog.setIsLike(false);
        }
        //
        //blog.setIsLike(BooleanUtil.isTrue(isMember));
    }



    /**
     * 点赞笔记功能
     * @param id
     */
    @Override
    public Result likeBlog(Long id) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        //1.判断当前用户是否已经点赞
        //Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());

        //更改一下，在SortedSet中查找
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null){
            //2.如果没有点赞，则进行点赞，并将用户加入redis中的set集合
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();//update tb_blog set like = like + 1 where id = ?
            if (isSuccess) {
                //如果更新成功，添加用户id到redis中的set
                //stringRedisTemplate.opsForSet().add(key, userId.toString());
                //更改一下，添加到SortedSet  zadd key value score
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
            }
        }else {
            //3.如果已经点赞，则取消点赞，并将用户从redis中的set集合中取消
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();//update tb_blog set like = like + 1 where id = ?
            if (isSuccess){
                //如果更新成功，将用户id从redis中的set移除
                //stringRedisTemplate.opsForSet().remove(key, userId.toString());
                //更改一下从SortedSet中删除
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //获取redis中当前店铺的key
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        //1.查询top5的点赞用户  zrange key 0 4   这查出来的是用户id和score 因为当时存的就是这个
        Set<String> top5Id = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if (top5Id == null || top5Id.isEmpty()){
            return Result.ok(Collections.emptyList());
        }

        //2.解析查询出来的额用户id
        List<Long> ids = top5Id.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.再根据用户id去查询用户的部分信息（用来显示在前端的）

        String idStr = StrUtil.join(",", ids);

        List<User> users = userService.query()
                .in("id",ids).last("ORDER BY FIELD(id,"+ idStr + ")")
                .list();

        //List<User> users = userService.listByIds(ids);

        List<UserDTO> userDTOS = new ArrayList<>();
        for (User user: users){
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            userDTOS.add(userDTO);
        }


        //4.然后再返回
        return Result.ok(userDTOS);
    }
}
