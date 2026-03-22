package com.loan.infra.common.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.infra.common.outbox.po.OutboxMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxMapper extends BaseMapper<OutboxMessagePO> {

    @Update("UPDATE t_message_outbox SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE t_message_outbox SET status = 'FAILED', retry_count = retry_count + 1, " +
            "next_retry_time = #{nextRetryTime}, update_time = NOW() WHERE id = #{id}")
    int updateStatusWithRetry(@Param("id") Long id, @Param("nextRetryTime") LocalDateTime nextRetryTime);

    @Select("SELECT * FROM t_message_outbox WHERE status = 'PENDING' " +
            "AND (next_retry_time IS NULL OR next_retry_time <= #{threshold}) " +
            "AND create_time < DATE_SUB(#{threshold}, INTERVAL 5 SECOND) " +
            "ORDER BY create_time ASC LIMIT #{limit}")
List<OutboxMessagePO> selectPendingMessages(@Param("threshold") LocalDateTime threshold, @Param("limit") int limit);
}