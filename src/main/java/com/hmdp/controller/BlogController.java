package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 根据用户查询
        return blogService.queryHotBlogs(current);
    }


    @GetMapping("/{blogging}")
    public Result getBlogById(@PathVariable("blogging") String blogging) {
        return blogService.getBlogById(blogging);

    }


    @GetMapping("/likes/{id}")
    public Result getBlogLikes(@PathVariable("id") String id) {
        return blogService.getBlogLikes(id);
    }


    @GetMapping("/of/user")
    public Result getUserBlogs(@RequestParam(value = "current", defaultValue = "1") Integer current,
                               @RequestParam("id") Long id) {
        return blogService.queryUserBlogs(current, id);
    }

    @GetMapping("/of/follow")
    public Result followUserBlogs(@RequestParam(value = "lastId") Long max, @RequestParam(value = "offset",required = false) Integer offset) {
        return blogService.queryBlogOfFollow(max,offset);
    }

}
