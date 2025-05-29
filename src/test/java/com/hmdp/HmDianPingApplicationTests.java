package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;


@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Test
    public void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L,10L);

    }



    @Test
    public void testHyperloglogTest(){
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++){
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("hl1",values);
            }
        }

        Long size = stringRedisTemplate.opsForHyperLogLog().size("hl1");
        System.out.println(size.intValue());
    }
}
