package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.MyPageResponse;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.service.BookmarkQueryService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지(회원 프로필) — 조회·부분 수정. 저장 수는 place 도메인의 북마크 조회로 조합한다. */
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberRepository memberRepository;
    private final BookmarkQueryService bookmarkQueryService;

    /** 마이페이지 조회. 닉네임·레벨·레벨별 추천 태그·운전목표 + 저장한 장소 수. 없는 회원이면 404. */
    @Transactional(readOnly = true)
    public MyPageResponse getMyPage(Long memberId) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        long savedPlaceCount = bookmarkQueryService.countByMember(memberId);
        return new MyPageResponse(
                member.getNickname(),
                member.getLevel(),
                RecommendationTags.of(member.getLevel()),
                member.getDrivingGoal(),
                savedPlaceCount);
    }

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
