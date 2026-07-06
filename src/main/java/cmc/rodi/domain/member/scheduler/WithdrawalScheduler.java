package cmc.rodi.domain.member.scheduler;

import cmc.rodi.domain.member.service.MemberWithdrawalBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 단계적 탈퇴 일 배치. 매일 04:00에 익명화(Day3)·소셜 식별자 해제(Day10)를 수행한다. */
@Component
@RequiredArgsConstructor
public class WithdrawalScheduler {

    private final MemberWithdrawalBatchService batchService;

    @Scheduled(cron = "${withdrawal.batch-cron:0 0 4 * * *}")
    public void run() {
        batchService.anonymizeExpired();
        batchService.releaseExpired();
    }
}
