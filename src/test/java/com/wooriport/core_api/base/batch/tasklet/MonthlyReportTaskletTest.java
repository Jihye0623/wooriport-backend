package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.UserRepository;
import com.wooriport.core_api.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonthlyReportTaskletTest {

    @Mock UserRepository userRepository;
    @Mock ReportService reportService;
    @InjectMocks MonthlyReportTasklet monthlyReportTasklet;

    @Test
    @DisplayName("활성 사용자가 없으면 FINISHED만 반환하고 아무 것도 하지 않는다")
    void noActiveUsers_returnsFinished_noOp() {
        given(userRepository.findAllActiveUsers()).willReturn(List.of());

        RepeatStatus result = monthlyReportTasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(reportService, never()).generateMonthlyReport(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("대상 연월은 오늘 기준 전달(지난달)로 계산된다")
    void targetYearMonth_isLastMonth() {
        Users user = Users.builder().id(UUID.randomUUID()).name("홍길동").build();
        given(userRepository.findAllActiveUsers()).willReturn(List.of(user));

        monthlyReportTasklet.execute(null, null);

        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        verify(reportService).generateMonthlyReport(user.getId(), lastMonth.getYear(), lastMonth.getMonthValue());
    }

    @Test
    @DisplayName("한 유저의 리포트 생성이 실패해도 나머지 유저는 계속 처리된다")
    void oneUserFails_othersStillProcessed() {
        Users failingUser = Users.builder().id(UUID.randomUUID()).name("실패유저").build();
        Users okUser = Users.builder().id(UUID.randomUUID()).name("정상유저").build();
        given(userRepository.findAllActiveUsers()).willReturn(List.of(failingUser, okUser));

        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        doThrow(new RuntimeException("리포트 생성 실패"))
                .when(reportService).generateMonthlyReport(failingUser.getId(), lastMonth.getYear(), lastMonth.getMonthValue());
        doNothing()
                .when(reportService).generateMonthlyReport(okUser.getId(), lastMonth.getYear(), lastMonth.getMonthValue());

        RepeatStatus result = monthlyReportTasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(reportService).generateMonthlyReport(okUser.getId(), lastMonth.getYear(), lastMonth.getMonthValue());
    }
}
