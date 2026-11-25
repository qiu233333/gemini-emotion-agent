package com.emotionagent.config;

import com.emotionagent.module.auth.entity.SysUser;
import com.emotionagent.module.auth.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SysUserMapper sysUserMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                SecurityUser parsed = jwtService.parseToken(token);
                SysUser dbUser = sysUserMapper.selectById(parsed.getUserId());
                if (dbUser != null && "ACTIVE".equalsIgnoreCase(dbUser.getStatus())) {
                    SecurityUser effectiveUser = new SecurityUser(dbUser.getId(), dbUser.getUsername(), dbUser.getRole());
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            effectiveUser,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + dbUser.getRole()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ex) {
                log.debug("Invalid JWT token: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
