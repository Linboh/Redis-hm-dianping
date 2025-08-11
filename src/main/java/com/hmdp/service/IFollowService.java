package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;

/**
 * <p>
 *  关注服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注和取关
     * @param followUserId 要关注的用户id
     * @param isFollow 是否关注
     * @return 结果
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断是否关注
     * @param followUserId 要判断的用户id
     * @return 是否关注
     */
    Result isFollow(Long followUserId);
}
