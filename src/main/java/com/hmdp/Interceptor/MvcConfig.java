package com.hmdp.Interceptor;



import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. token 刷新拦截器，拦截所有请求
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0); // 优先级高

        // 2. 登录校验拦截器，只拦截需要登录的接口
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns(
                        "/user/me",
                        "/user/logout",
                        "/blog/like",
                        "/follow/**",
                        "/upload",
                        "/voucher/seckill"
                ).order(1); // 优先级低
    }
}

