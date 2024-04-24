package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

//    @Resource
//    private Blog

    @Resource
    UserServiceImpl userService;

    @Override
    public Result getBlogById(String blogId) {
        // 根据用户访问的id返回博文
        Blog blog = query().eq("id", blogId).one();
        if (blog == null) {
            return Result.fail("指定资源不存在");
        }
        User user = userService.query().eq("id", blog.getUserId()).one();
        if (user == null) {
            //todo:其实可以考虑将用户设置为匿名/默认用户
            return Result.fail("用户不存在");
        }
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {

    }
}
