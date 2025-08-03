package com.hmdp.ShopTest;


import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ShopTestDemo {

    @Autowired
    private ShopServiceImpl shopService;

    @Test
    public void testShop() {
        shopService.saveShop2Redis(1L, 30L);
    }

}
