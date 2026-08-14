package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.dto.CourseTutorialCompletionResponse;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 코스 등록 튜토리얼 완료 저장(스펙 017). */
@Service
@RequiredArgsConstructor
public class MemberCourseTutorialService {

    private final MemberRepository memberRepository;

    /**
     * 완료 시각을 저장한다. 이미 완료한 회원이면 기존 시각을 그대로 반환한다.
     *
     * <p>앱은 로그인·토큰 재발급 응답의 {@code isCourseTutorialCompleted}로 사전에 표시 여부를 결정하고, 튜토리얼을 끝낸 순간 이 API를
     * 호출한다.
     */
    @Transactional
    public CourseTutorialCompletionResponse complete(Long memberId) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        member.completeCourseTutorial(LocalDateTime.now());
        return new CourseTutorialCompletionResponse(member.getCourseTutorialCompletedAt());
    }
}
