package com.wooriport.core_api.base.dto.user;

import com.wooriport.core_api.domain.Users;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class PortiTypeUpdateRequestDto {

    @NotNull(message = "porTI 유형을 선택해주세요.")
    private Users.PortiType portiType;
    // SWIMMING / ARCHERY / JUDO / RHYTHMIC / FENCING / CYCLING
}
