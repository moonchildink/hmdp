package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.impl.FollowServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    FollowServiceImpl followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") String userId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.followUser(userId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable("id") String userId) {
        return followService.isFollowed(userId);
    }

    @GetMapping("/common/{userId}")
    public Result getCommonFollows(@PathVariable("userId") Long user_id) {
        return followService.queryCommonFollows(user_id);

    }
}
