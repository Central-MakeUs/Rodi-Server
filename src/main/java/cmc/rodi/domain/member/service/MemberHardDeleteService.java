package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 즉시 탈퇴(내부 테스트용). 유예기간·익명화를 거치지 않고 회원을 <b>물리 삭제</b>해, 같은 소셜 계정으로 곧바로 재가입·온보딩을 다시 시험할 수 있게 한다. 운영의
 * 단계적 탈퇴는 {@link MemberWithdrawalService}가 담당한다.
 *
 * <p>배포 환경이 하나뿐이라(운영 서버가 곧 테스트 서버다) 프로파일·설정으로 가리면 정작 앱이 붙는 서버에서 쓸 수 없어, 게이트 없이 배포한다.
 */
@Service
@RequiredArgsConstructor
public class MemberHardDeleteService {

    /**
     * FK에 {@code ON DELETE CASCADE}가 없어 직접 지워야 하는 테이블. 순서대로 지운 뒤 member를 지우면
     * 나머지(review·recent_search ·review_report·member_block·member_practice)는 CASCADE로 함께 사라진다.
     *
     * <p>빠진 FK에 CASCADE를 붙이지는 않는다 — 운영에서 회원을 물리 삭제할 일이 없는데 실수의 파급만 커진다.
     */
    private static final List<String> DEPENDENT_TABLES =
            List.of("bookmark", "refresh_token", "social_account", "member_onboarding");

    private final MemberRepository memberRepository;
    private final EntityManager em;

    /** 본인 계정만 지운다. 회원 id를 파라미터로 받지 않아 남의 계정을 지울 수 없다. */
    @Transactional
    public void hardDelete(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        // JPA delete만 부르면 아래 테이블들에서 FK 위반이 난다. 네이티브로 순서를 지켜 지운다.
        for (String table : DEPENDENT_TABLES) {
            deleteByMemberId(table, memberId);
        }
        em.createNativeQuery("DELETE FROM member WHERE id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
        em.clear(); // 방금 지운 행이 1차 캐시에 남아 이후 조회가 되살아나 보이지 않게 한다
    }

    private void deleteByMemberId(String table, Long memberId) {
        em.createNativeQuery("DELETE FROM " + table + " WHERE member_id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
    }
}
