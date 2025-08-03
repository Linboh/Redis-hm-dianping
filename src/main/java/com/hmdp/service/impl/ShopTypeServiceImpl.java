package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private ShopTypeMapper shopTypeMapper;

    @Autowired
    StringRedisTemplate stringRedisTemplate;



    @Override
    public List<ShopType> queryShopTypeById() {

        String key = "shop:hot:list";

        // Step 1：查缓存
        String jsonList = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(jsonList)) {
            return JSONUtil.toList(jsonList, ShopType.class);
        }

        // Step 2：查数据库
        List<ShopType> shops = query().orderByAsc("sort").list(); // 自定义热门 SQL 查询
        if (CollectionUtils.isEmpty(shops)) return Collections.emptyList();

        // Step 3：封装数据 避免把敏感字段（如 cost、库存）暴露给前端
//        List<ShopType> dtoList = shops.stream()
//                .map(shop -> BeanUtil.copyProperties(shop, ShopType.class))
//                .collect(Collectors.toList());

        // Step 4：写缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shops));

        return shops;
    }
}
