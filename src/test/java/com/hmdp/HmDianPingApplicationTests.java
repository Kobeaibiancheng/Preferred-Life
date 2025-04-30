package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;


@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;


    @Test
    public void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L,10L);

    }
}
