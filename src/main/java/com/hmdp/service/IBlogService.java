package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollDTO;
import com.hmdp.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    public Result getBlogById(String blogId);

    Result likeBlog(Long id);

    Result queryHotBlogs(Integer current);

    Result getBlogLikes(String id);

    Result queryUserBlogs(Integer current, Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max,Integer offset);
}
