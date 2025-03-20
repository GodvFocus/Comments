package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.getUserById(blog);
            this.isLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("博客不存在");
        }
        getUserById(blog);
        isLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 判断blog中的isLike属性
     * @param blog
     */
    private void isLiked(Blog blog) {
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO == null){
            return;
        }
        Long userId = userDTO.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1. 根据blog id查询blog信息
        Blog blog = getById(id);
        // 2. 判断当前用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null){
            // 3. 如果未点赞，可以点赞
            boolean isUpdate = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isUpdate){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        else {
            // 4. 如果已点赞，则取消点赞
            boolean isUpdate = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isUpdate){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询博客的点赞列表
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 查询top5的点赞用户 zrange key 0 4
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 2. 查询用户信息
        List<Long> userIds = range.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        if(userIds == null || userIds.isEmpty()){
            // 如果没人点赞就返回一个空列表
            return Result.ok(Collections.emptyList());
        }

        String ids = StrUtil.join(",", userIds);

        List<UserDTO> userDTOList = userService.query()
                .in("id", userIds)
                .last("order by field(id," + ids + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 3. 返回
        return Result.ok(userDTOList);
    }

    /**
     * 根据博客中的用户id查询用户信息
     * @param blog
     */
    private void getUserById(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
