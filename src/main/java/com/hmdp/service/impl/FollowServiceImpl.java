package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result isFollowed(String userId) {
        UserDTO userDTO = UserHolder.getUser();
        Long count = query().eq("user_id", userDTO.getId()).eq("follow_user_id", userId).count();
        return Result.ok(count > 0);

    }

    @Override
    public Result followUser(String userId, Boolean isFollow) {
        UserDTO userDTO = UserHolder.getUser();
        if (isFollow) {
            // 关注用户
            Follow follow = new Follow();
            follow.setFollowUserId(Long.parseLong(userId));
            follow.setUserId(userDTO.getId());
            save(follow);
        } else {
            remove(new QueryWrapper<Follow>().eq("user_id", userDTO.getId()).eq("follow_user_id", userId));
        }
        return Result.ok();
    }
}
