package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지(회원 프로필) — 부분 수정. 조회 요약은 추후 추가. */
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberRepository memberRepository;

    /** 회원 부분 수정(현재: 운전 목표). 없는 회원이면 404. */
    @Transactional
    public void update(Long memberId, MemberUpdateRequest request) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        member.updateDrivingGoal(request.drivingGoal());
    }
}
