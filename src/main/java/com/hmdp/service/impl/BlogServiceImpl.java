package com.hmdp.service.impl;

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
import java.util.List;

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
        Long userId = blog.getUserId();
        String key = SystemConstants.USER_LIKE_PREFIX + blog.getId();
        boolean isLiked = Boolean.TRUE.equals(template.opsForSet().isMember(key, userId.toString()));
        blog.setIsLike(isLiked);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = SystemConstants.USER_LIKE_PREFIX + id;
        boolean isLiked = Boolean.TRUE.equals(template.opsForSet().isMember(key, userId.toString()));
        if (!isLiked) {
            // 未点赞
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", id).update();
            if (isSuccess)
                template.opsForSet().add(key, userId.toString());
            return Result.ok();
        } else {
            boolean isSuccess = update().setSql("liked = liked-1").eq("id", id).update();
            if (isSuccess)
                template.opsForSet().remove(key, userId.toString());
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

    }
}
