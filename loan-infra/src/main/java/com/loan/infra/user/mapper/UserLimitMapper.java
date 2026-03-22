package com.loan.infra.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.infra.user.po.UserLimitPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserLimitMapper extends BaseMapper<UserLimitPO> {

    @Update("UPDATE t_user_limit SET " +
            "used_limit = used_limit + #{amount}, " +
            "available_limit = available_limit - #{amount}, " +
            "update_time = NOW() " +
            "WHERE user_id = #{userId} AND available_limit >= #{amount}")
    int secureDecreaseLimit(@Param("userId") Long userId, @Param("amount") Double amount);
}
