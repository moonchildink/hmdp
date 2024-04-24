package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

//    @Resource
//    private Blog

    @Resource
    UserServiceImpl userService;

    @Resource
    StringRedisTemplate template;

    @Override
    public Result getBlogById(String blogId) {
        Blog blog = getById(blogId);
        if (blog == null) {
            return Result.fail("指定资源不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null)
            return;
        Long userId = userDTO.getId();
        String key = SystemConstants.LIKED_USER_ZSET + blog.getId();
        Double score = template.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    /**
     * 当前用户对指定id的blog点赞，此处可以使用zset，也就是ordered set，
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = SystemConstants.LIKED_USER_ZSET + id;
        Double score = template.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 未点赞
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", id).update();
            if (isSuccess)
                template.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            return Result.ok();
        } else {
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess)
                template.opsForZSet().remove(key, userId.toString());
            return Result.ok();
        }

    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

    }

    @Override
    public Result queryHotBlogs(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            isBlogLiked(blog);
            queryBlogUser(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result getBlogLikes(String id) {
        String key = SystemConstants.LIKED_USER_ZSET + id;
        Set<String> range = template.opsForZSet().range(key, 0, 4);
        if (range == null || range.isEmpty())
            return Result.ok(Collections.emptyList());
        List<Long> collect = range.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(collect)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);

    }

    @Override
    public Result queryUserBlogs(Integer current, Long id) {
        /// 分页查询
        Page<Blog> page = query()
                .eq("user_id", id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        return Result.ok(records);

    }
}
