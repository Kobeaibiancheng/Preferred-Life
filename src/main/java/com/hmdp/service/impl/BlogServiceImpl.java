package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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


        return Result.ok(blog);
    }

    /**
     * 判断是否已经点赞
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked" + blog.getId();
        //1.判断当前用户是否已经点赞
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        //
        blog.setIsLike(BooleanUtil.isTrue(isMember));
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
     * 点赞笔记功能
     * @param id
     */
    @Override
    public Result likeBlog(Long id) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked" + id;
        //1.判断当前用户是否已经点赞
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());

        if (BooleanUtil.isFalse(isMember)){
            //2.如果没有点赞，则进行点赞，并将用户加入redis中的set集合
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();//update tb_blog set like = like + 1 where id = ?
            if (isSuccess) {
                //如果更新成功，添加用户id到redis中的set
                stringRedisTemplate.opsForSet().add(key, userId.toString());
            }
        }else {
            //3.如果已经点赞，则取消点赞，并将用户从redis中的set集合中取消
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();//update tb_blog set like = like + 1 where id = ?
            if (isSuccess){
                //如果更新成功，将用户id从redis中的set移除
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }
}
