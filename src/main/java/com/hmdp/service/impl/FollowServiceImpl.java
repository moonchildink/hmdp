package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    StringRedisTemplate template;

    @Override
    public Result isFollowed(String userId) {
        String currentUserId = UserHolder.getUser().getIcon();
        String key = SystemConstants.FOLLOWED_USERS + currentUserId;
        boolean isFollowed = Boolean.TRUE.equals(template.opsForSet().isMember(key, userId));
        return Result.ok(isFollowed);
    }

    @Override
    public Result followUser(String userId, Boolean isFollow) {
        UserDTO userDTO = UserHolder.getUser();
        if (isFollow) {
            // 关注用户
            Follow follow = new Follow();
            follow.setFollowUserId(Long.parseLong(userId));
            follow.setUserId(userDTO.getId());
            boolean success = save(follow);
            if (success) {
                String key = SystemConstants.FOLLOWED_USERS + userDTO.getId();
                template.opsForSet().add(key, userId);
            }
        } else {
            boolean isRemove = remove(new QueryWrapper<Follow>().eq("user_id", userDTO.getId()).eq("follow_user_id", userId));
            if (isRemove) {
                String key = SystemConstants.FOLLOWED_USERS + userDTO.getId();
                template.opsForSet().remove(key, userId);
            }

        }
        return Result.ok();
    }

    @Resource
    UserServiceImpl userService;

    @Override
    public Result queryCommonFollows(Long userId) {
        Long currentUserId = UserHolder.getUser().getId();
        // 获取当前用户关注列表
        String currentUserKey = SystemConstants.FOLLOWED_USERS + currentUserId;
        String otherUserKey = SystemConstants.FOLLOWED_USERS + userId;

        Set<String> intersect = template.opsForSet().intersect(currentUserKey, otherUserKey);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(collect)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);

    }
}
