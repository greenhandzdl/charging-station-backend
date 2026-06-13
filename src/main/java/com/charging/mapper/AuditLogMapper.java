package com.charging.mapper;

import com.charging.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper {

    @Insert("INSERT INTO audit_logs (id, actor_id, actor_type, action, resource, resource_id, payload, client_ip, user_agent, created_at) " +
            "VALUES (#{id}, #{actorId}, #{actorType}, #{action}, #{resource}, #{resourceId, jdbcType=OTHER}, #{payload, jdbcType=OTHER}::jsonb, #{clientIp, jdbcType=VARCHAR}, #{userAgent, jdbcType=VARCHAR}, now())")
    int insert(AuditLog auditLog);
}