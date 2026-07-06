package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.auth.service.RefreshTokenService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 요청 처리(Day 0). soft delete로 유예기간을 시작하고 서버 세션(refresh)을 전체 폐기한다. 개인정보 익명화·공급자 해제·소셜 식별자 해제는
 * 유예/기간 경과 후 배치가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final MemberRepository memberRepository;
    private final RefreshTokenService refreshTokenService;

    /** 탈퇴 요청 — deletedAt 세팅 + 회원의 모든 refresh 세션 폐기. */
    @Transactional
    public void withdraw(Long memberId) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        member.withdraw(LocalDateTime.now());
        refreshTokenService.revokeAll(member);
    }
}
