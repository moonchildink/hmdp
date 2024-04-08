package com.hmdp;

import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

@SpringBootTest
public class HmDianPingApplicationTest {
    @Resource
    private IShopTypeService typeService;


    @Test
    public void test(){
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        System.out.println(typeList);
//        System.out.println("wy");
    }


}
