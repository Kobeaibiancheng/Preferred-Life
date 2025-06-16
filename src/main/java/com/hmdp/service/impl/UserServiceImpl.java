package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.不符合，返回手机号不符合
            return Result.fail("手机号格式错误！");
        }
        String code = RandomUtil.randomNumbers(6);
        /*
        //3.符合，生成验证码并保存到session
        session.setAttribute("code", code);//将验证码保存到session中
        */

        //3.符合，将验证码保存到redis中,set key value ex 120,并设置有效期为LOGIN_CODE_TTL=2分钟
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+ phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);


        //4.发送验证码
        log.debug("发送短信验证码成功，验证码为：{}",code);
        //5.返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.不符合，返回手机号格式错误
            return Result.fail("手机号格式错误！");
        }
        /**
         * 保存在session中
         */
        /*
        //2.校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.toString().equals(code)){
            //3.不一致
            return Result.fail("验证码错误！");
        }
        //4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();//使用的是mybatis-plus 很方便的实现了单表sql查询

        //5.判断用户是否存在
        if(user == null){
            //6.用户不存在，创建用户
            user = createUserWithPhone(phone);
        }
        //6.保存用户信息到session
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        session.setAttribute("user", userDTO);
        return Result.ok();
        */

        //3.从redis获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();//从前端提交过来的数据中获取提交的验证码
        if(cacheCode == null || !cacheCode.equals(code)){
            //3.不一致
            return Result.fail("验证码错误！");
        }
        //4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();//使用的是mybatis-plus 很方便的实现了单表sql查询

        //5.判断用户是否存在
        if(user == null){
            //6.用户不存在，创建用户
            user = createUserWithPhone(phone);
        }

        //6.1随机生成token，作为登陆令牌
        String token = UUID.randomUUID(true).toString();
        //6.2将User对象转化为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        //6.3存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //6.4设置有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //7.返回token
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        //token会存放在请求头中，从请求头中获取token
        String token = request.getHeader("Authorization");
        //判断token是否存在

        if (token == null || token.isEmpty()) {
            //不存在
            return Result.fail("未登录！");
        }
        //存在
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(key);
        return Result.ok();

    }


    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2.保存用户
        save(user);
        return user;
    }
}
