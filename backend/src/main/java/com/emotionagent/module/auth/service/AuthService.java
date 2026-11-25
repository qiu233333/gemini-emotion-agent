package com.emotionagent.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.emotionagent.common.BusinessException;
import com.emotionagent.config.JwtService;
import com.emotionagent.module.audit.service.AuditService;
import com.emotionagent.module.auth.dto.LoginRequest;
import com.emotionagent.module.auth.dto.RegisterRequest;
import com.emotionagent.module.auth.dto.UpdateProfileRequest;
import com.emotionagent.module.auth.dto.UserProfileDto;
import com.emotionagent.module.auth.entity.SysUser;
import com.emotionagent.module.auth.mapper.SysUserMapper;
import com.emotionagent.module.diary.entity.EmotionDiary;
import com.emotionagent.module.diary.mapper.EmotionDiaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final EmotionDiaryMapper emotionDiaryMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Transactional
    public Map<String, Object> register(RegisterRequest req, String ip) {
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername().trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException("用户名已存在，请换一个用户名");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname().trim());
        user.setEmail(req.getEmail());
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setAiMemoryEnabled(true);
        user.setMemoryWindowDays(30);
        user.setDailyAiLimit(30);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);

        auditService.record(user.getId(), user.getRole(), "REGISTER", "USER", user.getId(),
                "New user registered: " + user.getUsername(), ip);

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("user", toDto(user));
        return resp;
    }

    public Map<String, Object> login(LoginRequest req, String ip) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername().trim()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        auditService.record(user.getId(), user.getRole(), "LOGIN", "USER", user.getId(),
                "User login success: " + user.getUsername(), ip);

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("user", toDto(user));
        return resp;
    }

    public UserProfileDto getProfile(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return toDto(user);
    }

    @Transactional
    public UserProfileDto updateProfile(Long userId, UpdateProfileRequest req, String ip) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            user.setNickname(req.getNickname().trim());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail().trim());
        }
        if (req.getBio() != null) {
            user.setBio(req.getBio().trim());
        }
        if (req.getAiMemoryEnabled() != null) {
            user.setAiMemoryEnabled(req.getAiMemoryEnabled());
        }
        if (req.getMemoryWindowDays() != null) {
            user.setMemoryWindowDays(req.getMemoryWindowDays());
        }
        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getOldPassword() == null || !passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
                throw new BusinessException("原密码不正确");
            }
            user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        auditService.record(user.getId(), user.getRole(), "UPDATE_PROFILE", "USER", user.getId(),
                "Updated profile/memory settings: aiMemoryEnabled=" + user.getAiMemoryEnabled()
                        + ", windowDays=" + user.getMemoryWindowDays(), ip);
        return toDto(user);
    }

    /**
     * 一键清除历史记忆关联：将当前用户所有历史日记从记忆检索池中移除，并关闭默认历史关联。
     */
    @Transactional
    public int clearHistoryMemory(Long userId, String ip) {
        int updated = emotionDiaryMapper.update(null, new LambdaUpdateWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId)
                .set(EmotionDiary::getMemoryIncluded, false));
        auditService.record(userId, "USER", "CLEAR_MEMORY", "MEMORY", userId,
                "Cleared memory inclusion for diaries count=" + updated, ip);
        return updated;
    }

    public UserProfileDto toDto(SysUser user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .bio(user.getBio())
                .role(user.getRole())
                .status(user.getStatus())
                .aiMemoryEnabled(user.getAiMemoryEnabled())
                .memoryWindowDays(user.getMemoryWindowDays())
                .dailyAiLimit(user.getDailyAiLimit())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
