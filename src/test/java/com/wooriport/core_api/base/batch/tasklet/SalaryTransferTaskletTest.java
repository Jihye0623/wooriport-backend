package com.wooriport.core_api.base.batch.tasklet;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.UserRepository;
import com.wooriport.core_api.service.TransferPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SalaryTransferTaskletTest {

    @Mock UserRepository userRepository;
    @Mock AssetRepository assetRepository;
    @Mock TransferPlanService transferPlanService;
    @InjectMocks SalaryTransferTasklet salaryTransferTasklet;

    @Test
    @DisplayName("오늘 급여일 대상자가 없으면 FINISHED만 반환하고 아무 것도 하지 않는다")
    void noTargetsToday_returnsFinished_noOp() {
        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of());

        RepeatStatus result = salaryTransferTasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(assetRepository, never()).findByUserIdAndIsSalaryTrue(any());
    }

    @Test
    @DisplayName("급여 통장이 설정되지 않은 유저는 스킵한다")
    void noSalaryAsset_skipped() {
        Users user = user(UUID.randomUUID());
        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findByUserIdAndIsSalaryTrue(user.getId())).willReturn(Optional.empty());

        RepeatStatus result = salaryTransferTasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(transferPlanService, never()).generateFromSalary(any());
    }

    @Test
    @DisplayName("자동이체 대상 계좌가 설정되지 않았으면 스킵한다")
    void noAutoTransferAsset_skipped() {
        Users user = user(null);
        Assets salaryAsset = asset(1_000_000L);
        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findByUserIdAndIsSalaryTrue(user.getId())).willReturn(Optional.of(salaryAsset));

        salaryTransferTasklet.execute(null, null);

        assertThat(salaryAsset.getBalance()).isEqualTo(1_000_000L); // 변경 없음
        verify(transferPlanService, never()).generateFromSalary(any());
    }

    @Test
    @DisplayName("급여 통장 잔액이 0 이하면 이체를 실행하지 않는다")
    void salaryAssetBalanceZeroOrLess_skipped() {
        UUID wooriId = UUID.randomUUID();
        Users user = user(wooriId);
        Assets salaryAsset = asset(0L);
        Assets wooriAsset = asset(500_000L);
        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findByUserIdAndIsSalaryTrue(user.getId())).willReturn(Optional.of(salaryAsset));
        given(assetRepository.findById(wooriId)).willReturn(Optional.of(wooriAsset));

        salaryTransferTasklet.execute(null, null);

        assertThat(wooriAsset.getBalance()).isEqualTo(500_000L); // 변경 없음
        verify(transferPlanService, never()).generateFromSalary(any());
    }

    @Test
    @DisplayName("정상 이체: 급여 통장은 0으로, 우리은행 계좌는 이체액만큼 증가하고 이체 계획을 생성한다")
    void transfersFullBalance_symmetrically_andGeneratesPlan() {
        UUID wooriId = UUID.randomUUID();
        Users user = user(wooriId);
        Assets salaryAsset = asset(500_000L);
        Assets wooriAsset = asset(100_000L);

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findByUserIdAndIsSalaryTrue(user.getId())).willReturn(Optional.of(salaryAsset));
        given(assetRepository.findById(wooriId)).willReturn(Optional.of(wooriAsset));

        salaryTransferTasklet.execute(null, null);

        assertThat(salaryAsset.getBalance()).isZero();
        assertThat(wooriAsset.getBalance()).isEqualTo(600_000L); // 100,000 + 500,000
        verify(transferPlanService).generateFromSalary(user.getId());
    }

    @Test
    @DisplayName("이체 후 이체 계획 생성이 실패해도 이미 이동한 잔액은 그대로 유지된다 (독립적인 예외 처리)")
    void planGenerationFails_transferAlreadyMovedBalanceStays() {
        UUID wooriId = UUID.randomUUID();
        Users user = user(wooriId);
        Assets salaryAsset = asset(500_000L);
        Assets wooriAsset = asset(100_000L);

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(user));
        given(assetRepository.findByUserIdAndIsSalaryTrue(user.getId())).willReturn(Optional.of(salaryAsset));
        given(assetRepository.findById(wooriId)).willReturn(Optional.of(wooriAsset));
        given(transferPlanService.generateFromSalary(user.getId()))
                .willThrow(new RuntimeException("포트폴리오 미설정"));

        RepeatStatus result = salaryTransferTasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED); // 계획 생성 실패가 전체 배치를 죽이지 않음
        assertThat(salaryAsset.getBalance()).isZero();
        assertThat(wooriAsset.getBalance()).isEqualTo(600_000L); // 이체 자체는 유지
    }

    @Test
    @DisplayName("한 유저 처리 중 예외가 발생해도 나머지 유저는 계속 처리된다")
    void oneUserFails_othersStillProcessed() {
        Users failingUser = user(UUID.randomUUID());
        UUID okWooriId = UUID.randomUUID();
        Users okUser = user(okWooriId);
        Assets okSalaryAsset = asset(300_000L);
        Assets okWooriAsset = asset(0L);

        given(userRepository.findBySalaryDate(anyInt())).willReturn(List.of(failingUser, okUser));
        given(assetRepository.findByUserIdAndIsSalaryTrue(failingUser.getId())).willReturn(Optional.empty());
        given(assetRepository.findByUserIdAndIsSalaryTrue(okUser.getId())).willReturn(Optional.of(okSalaryAsset));
        given(assetRepository.findById(okWooriId)).willReturn(Optional.of(okWooriAsset));

        RepeatStatus result = salaryTransferTasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        assertThat(okSalaryAsset.getBalance()).isZero();
        assertThat(okWooriAsset.getBalance()).isEqualTo(300_000L);
        verify(transferPlanService).generateFromSalary(okUser.getId());
    }

    private Users user(UUID autoTransferToAssetId) {
        return Users.builder()
                .id(UUID.randomUUID())
                .name("홍길동")
                .autoTransferToAssetId(autoTransferToAssetId)
                .build();
    }

    private Assets asset(Long balance) {
        return Assets.builder()
                .id(UUID.randomUUID())
                .institution("우리은행")
                .assetType(Assets.AccountType.CHECKING)
                .balance(balance)
                .bankType(Assets.BankType.WOORI)
                .syncedAt(LocalDateTime.now())
                .build();
    }
}
