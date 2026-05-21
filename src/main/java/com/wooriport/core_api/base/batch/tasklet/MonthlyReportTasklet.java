package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.UserRepository;
import com.wooriport.core_api.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportTasklet implements Tasklet {

    private final UserRepository userRepository;
    private final ReportService reportService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        // 전달 (이전 달) 기준으로 리포트 생성
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        int year  = lastMonth.getYear();
        int month = lastMonth.getMonthValue();

        log.info("[MonthlyReportJob] 시작 — {}년 {}월 리포트 생성", year, month);

        // 활성 사용자 전체 조회
        List<Users> users = userRepository.findAllActiveUsers();

        if (users.isEmpty()) {
            log.info("[MonthlyReportJob] 활성 사용자 없음");
            return RepeatStatus.FINISHED;
        }

        log.info("[MonthlyReportJob] 대상 사용자 {}명", users.size());

        for (Users user : users) {
            try {
                // ReportService.generateMonthlyReport()
                // → transactions 집계 → Flask AI 코멘트 요청 → reports 저장
                reportService.generateMonthlyReport(user.getId(), year, month);

            } catch (Exception e) {
                log.error("[MonthlyReportJob] 실패 — userId: {}, 사유: {}",
                        user.getId(), e.getMessage(), e);
            }
        }

        log.info("[MonthlyReportJob] 완료");
        return RepeatStatus.FINISHED;
    }
}
