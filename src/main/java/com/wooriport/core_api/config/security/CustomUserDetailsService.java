package com.wooriport.core_api.config.security;

import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userIdStr) throws UsernameNotFoundException {
        // 우리가 아까 JwtTokenProvider에서 subject에 UUID를 넣었기 때문에,
        // 여기로 넘어오는 userIdStr 값은 이메일이 아니라 "UUID 문자열"입니다!
        UUID userId = UUID.fromString(userIdStr);

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("해당 유저를 찾을 수 없습니다: " + userId));

        // 찾은 엔티티를 어댑터(CustomUserDetails)로 예쁘게 포장해서 시큐리티에게 넘겨줍니다.
        return new CustomUserDetails(user);
    }
}