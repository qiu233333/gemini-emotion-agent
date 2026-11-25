package com.emotionagent.module.chat.controller;

import com.emotionagent.common.ApiResponse;
import com.emotionagent.config.SecurityUtils;
import com.emotionagent.module.chat.entity.ChatMessage;
import com.emotionagent.module.chat.entity.ChatSession;
import com.emotionagent.module.chat.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> sessions() {
        return ApiResponse.ok(chatService.listSessions(SecurityUtils.currentUserId()));
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSession> createSession(@RequestBody(required = false) Map<String, Object> body) {
        String title = body != null && body.get("title") != null ? String.valueOf(body.get("title")) : null;
        Boolean useMemory = body == null || body.get("useDiaryMemory") == null
                || Boolean.parseBoolean(String.valueOf(body.get("useDiaryMemory")));
        return ApiResponse.ok(chatService.createSession(SecurityUtils.currentUserId(), title, useMemory));
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessage>> messages(@PathVariable Long id) {
        return ApiResponse.ok(chatService.listMessages(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<Map<String, Object>> send(@PathVariable Long id,
                                                 @RequestBody Map<String, String> body,
                                                 HttpServletRequest request) {
        String content = body == null ? null : body.get("content");
        return ApiResponse.ok(chatService.sendMessage(
                SecurityUtils.currentUserId(), id, content, request.getRemoteAddr()));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id, HttpServletRequest request) {
        chatService.deleteSession(SecurityUtils.currentUserId(), id, request.getRemoteAddr());
        return ApiResponse.ok();
    }
}
