package cmc.rodi.domain.member.service;

import cmc.rodi.domain.member.dto.LevelProgressResponse;
import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.MyPageResponse;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.service.BookmarkQueryService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지(회원 프로필) — 조회·부분 수정. 저장 수는 place 도메인의 북마크 조회로 조합한다. */
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    /** V28의 닉네임 유니크 인덱스. */
    private static final String NICKNAME_UNIQUE_INDEX = "uq_member_nickname_lower";

    private final MemberRepository memberRepository;
    private final BookmarkQueryService bookmarkQueryService;
    private final NicknameService nicknameService;

    /** 마이페이지 조회. 닉네임·레벨·레벨별 추천 태그·운전목표 + 저장한 장소 수 + 레벨 게이지. 없는 회원이면 404. */
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
                savedPlaceCount,
                LevelProgressResponse.from(member));
    }

    /**
     * 회원 부분 수정 — 보낸 필드만 바꾼다(스펙 020). 없는 회원이면 404.
     *
     * <p>닉네임 판정(변경 제한 30일 포함)을 <b>어떤 필드도 바꾸기 전에</b> 끝내, 하나라도 실패하면 아무것도 저장되지 않게 한다(운전 목표 길이는 요청 검증에서
     * 이미 걸러진다). 판정과 저장 사이에 다른 회원이 같은 닉네임을 먼저 저장하면 유니크 인덱스가 막는데, 커밋 시점이 아니라 여기서 잡아 409로 바꾸려고 바로
     * flush한다.
     */
    @Transactional
    public void update(Long memberId, MemberUpdateRequest request) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        // DB 컬럼 정밀도(마이크로초)에 맞춰 잘라, 저장 전후 값이 같게 한다
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        String nickname =
                request.hasNickname()
                        ? nicknameService.validate(member, request.getNickname(), now)
                        : null;

        if (request.hasDrivingGoal()) {
            member.updateDrivingGoal(request.getDrivingGoal());
        }
        if (nickname != null) {
            member.changeNickname(nickname, now);
            flushNickname();
        }
    }

    private void flushNickname() {
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            if (isNicknameUniqueViolation(e)) {
                throw new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED);
            }
            throw e;
        }
    }

    private static boolean isNicknameUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && NICKNAME_UNIQUE_INDEX.equalsIgnoreCase(violation.getConstraintName());
    }
}
