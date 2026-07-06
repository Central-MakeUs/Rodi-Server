package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.policy.WithdrawalPolicy;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.repository.SocialAccountRepository;
import cmc.rodi.global.auth.social.SocialClientResolver;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단계적 탈퇴 배치. Day3: 유예 경과 회원을 익명화하고 공급자 연결을 해제한다. Day10: 재가입 허용을 위해 소셜 식별자를 삭제한다. 회원별 실패는 격리해 나머지
 * 진행을 막지 않고, anonymizedAt이 남지 않아 다음 회차에 재시도된다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalBatchService {

    private static final Logger log = LoggerFactory.getLogger(MemberWithdrawalBatchService.class);

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialClientResolver socialClientResolver;

    /** Day 3 — 유예 경과분 익명화 + 공급자 revoke/unlink. */
    @Transactional
    public void anonymizeExpired() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minus(WithdrawalPolicy.RECOVERABLE_WINDOW);
        List<Member> targets =
                memberRepository.findByDeletedAtBeforeAndAnonymizedAtIsNull(threshold);
        for (Member member : targets) {
            try {
                anonymize(member, now);
            } catch (Exception e) {
                log.warn("회원 익명화 실패(다음 회차 재시도) memberId={}", member.getId(), e);
            }
        }
        if (!targets.isEmpty()) {
            log.info("탈퇴 유예 경과 익명화 대상 {}건 처리", targets.size());
        }
    }

    private void anonymize(Member member, LocalDateTime now) {
        for (SocialAccount account : socialAccountRepository.findByMember(member)) {
            socialClientResolver.resolve(account.getProvider()).revoke(account);
            account.anonymize();
        }
        member.anonymize(now);
    }

    /** Day 10 — 재가입 허용: 소셜 식별자(social_account) 삭제. */
    @Transactional
    public void releaseExpired() {
        LocalDateTime threshold =
                LocalDateTime.now().minus(WithdrawalPolicy.RE_REGISTERABLE_WINDOW);
        int released = socialAccountRepository.deleteByMemberDeletedAtBefore(threshold);
        if (released > 0) {
            log.info(
                    "탈퇴 {}일 경과 소셜 연결 {}건 해제(재가입 허용)",
                    WithdrawalPolicy.RE_REGISTERABLE_WINDOW.toDays(),
                    released);
        }
    }
}
