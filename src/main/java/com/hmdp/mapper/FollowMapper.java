package com.hmdp.mapper;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    @Select("select a.follow_user_id from hm_comments.tb_follow a inner join hm_comments.tb_follow b " +
            "on  a.follow_user_id = b.follow_user_id where a.user_id = #{userId} and b.user_id = #{id}")
    List<Long> getCommonFollows(Long userId, Long id);
}
