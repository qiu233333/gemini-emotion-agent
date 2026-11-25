package com.emotionagent.module.audit.service;

import com.emotionagent.module.audit.entity.AuditLog;
import com.emotionagent.module.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogMapper auditLogMapper;

    public void record(Long actorUserId, String actorRole, String action,
                       String resourceType, Long resourceId, String summaryMasked, String ipAddress) {
        try {
            AuditLog logRow = new AuditLog();
            logRow.setActorUserId(actorUserId);
            logRow.setActorRole(actorRole);
            logRow.setAction(action);
            logRow.setResourceType(resourceType);
            logRow.setResourceId(resourceId);
            logRow.setSummaryMasked(summaryMasked != null && summaryMasked.length() > 500
                    ? summaryMasked.substring(0, 500) : summaryMasked);
            logRow.setIpAddress(ipAddress);
            logRow.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(logRow);
        } catch (Exception ex) {
            log.warn("Audit log write failed: {}", ex.getMessage());
        }
    }
}
