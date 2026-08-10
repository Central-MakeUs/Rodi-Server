package cmc.rodi.domain.practice.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.domain.practice.dto.PracticeRegisterResponse;
import cmc.rodi.domain.practice.dto.PracticeSkipReasonRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitResponse;
import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.entity.SkipReason;
import cmc.rodi.domain.practice.exception.PracticeErrorCode;
import cmc.rodi.domain.practice.repository.MemberPracticeRepository;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.form.FormType;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 연습 목록 담기·제거와 방문 기록(다녀왔어요·안 했어요). 조회는 {@link PracticeQueryService}. */
@Service
@RequiredArgsConstructor
public class PracticeService {

    /** 미방문 이유 폼의 문항 식별자(클라이언트가 응답을 구분하는 키). */
    private static final String SKIP_REASON_QUESTION_ID = "WHY_NOT_PRACTICED";

    private final MemberPracticeRepository memberPracticeRepository;
    private final PlaceRepository placeRepository;
    private final MemberRepository memberRepository;

    /**
     * 연습 목록에 담기. 이미 담긴 장소면 <b>행을 새로 만들지 않고</b> 상태를 예정으로 되돌린다(재도전). 지난 연습 횟수는 그대로 유지된다. 코스·주차장 모두 담을
     * 수 있다.
     */
    @Transactional
    public PracticeRegisterResponse register(Long placeId, Long memberId) {
        MemberPractice practice =
                memberPracticeRepository.findByMemberIdAndPlaceId(memberId, placeId).orElse(null);
        if (practice != null) {
            practice.replan();
            return PracticeRegisterResponse.from(practice);
        }

        Place place =
                placeRepository
                        .findById(placeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        // 같은 장소를 연달아 두 번 누르면 두 요청이 나란히 "없음"으로 보고 둘 다 INSERT를 시도한다.
        // 그냥 save()면 늦게 온 쪽이 uq_member_practice에 걸려 500이 되므로, DB에 판정을 맡긴다.
        memberPracticeRepository.insertIfAbsent(member.getId(), place.getId());
        MemberPractice saved =
                memberPracticeRepository
                        .findByMemberIdAndPlaceId(memberId, placeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        return PracticeRegisterResponse.from(saved);
    }

    /**
     * 방문 기록(RV-01 "다녀왔어요"). 상태를 요청으로 받지 않는다 — 이 호출 자체가 방문이므로 서버가 {@code VISITED}로 정하고, 인증 여부만 인정
     * 주행거리로 판정한다. 호출할 때마다 횟수가 오르므로 한 방문에서 중복 호출하지 않는 건 클라이언트 몫이다.
     */
    @Transactional
    public PracticeVisitResponse recordVisit(
            Long practiceId, Long memberId, PracticeVisitRequest request) {
        // 행을 잠그고 읽어 [쿨다운 판정 → 방문 기록]을 직렬화한다. 잠금이 없으면 동시에 들어온 두 요청이
        // 서로의 미커밋 visited_at을 못 봐 둘 다 통과하고 누적 거리가 두 번 더해진다. 순서는 항목 → 회원.
        MemberPractice practice = findOwnedPracticeForUpdate(practiceId, memberId);
        LocalDateTime now = LocalDateTime.now();

        // 재시도·연타는 같은 방문으로 본다 — 거리가 레벨로 환산되고 레벨은 되돌릴 수 없다(ADR 0012).
        // 오류로 돌려주면 앱이 다시 시도하므로, 현재 상태를 그대로 200으로 준다.
        if (practice.isWithinVisitCooldown(now)) {
            return PracticeVisitResponse.unchanged(practice, findMember(memberId));
        }

        int certifiedMeters = request.metersOrZero();
        boolean certifiedNow = practice.markVisited(now, certifiedMeters);

        // 누적은 읽고-더하고-쓰기라 같은 회원의 방문이 겹치면 한쪽이 사라진다. 행을 잠가 직렬화한다.
        Member member = findMemberForUpdate(memberId);
        boolean levelUp = member.addDistance(practice.accruableMeters(certifiedMeters));
        return PracticeVisitResponse.of(practice, certifiedMeters, certifiedNow, member, levelUp);
    }

    /** 미방문 이유 폼. 문구·순서·직접입력 여부를 서버가 정의해 내려준다(앱 배포 없이 문구 변경 가능). 저장이 없어 조회 트랜잭션도 열지 않는다. */
    public FormResponse getSkipReasonForm() {
        return new FormResponse(
                SKIP_REASON_QUESTION_ID,
                FormType.SINGLE_SELECT,
                "왜 연습을 다녀오지 않았나요?",
                "이유를 알려주시면 더 나은 코스를 추천해드릴게요!",
                true,
                Arrays.stream(SkipReason.values())
                        .sorted(Comparator.comparingInt(SkipReason::getOrder))
                        .map(SkipReason::toOption)
                        .toList());
    }

    /**
     * 미방문 사유 제출(RV-01 "안 했어요"). 상태를 {@code NOT_VISITED}로 바꾸면서 사유를 함께 저장해, 사유 없는 미방문이 남지 않는다. 사유는 한
     * 번 저장하면 덮어쓸 수 없고(409), 다시 다녀오면(방문 기록) 비워져 새로 남길 수 있다.
     */
    @Transactional
    public void submitSkipReason(
            Long practiceId, Long memberId, PracticeSkipReasonRequest request) {
        MemberPractice practice = findOwnedPractice(practiceId, memberId);
        if (practice.hasSkipReason()) {
            throw new BusinessException(PracticeErrorCode.SKIP_REASON_ALREADY_SET);
        }
        practice.markNotVisited();
        practice.applySkipReason(request.reason(), request.detail());
    }

    /** 목록에서 제거(멱등). 본인 항목만 지울 수 있고, 없으면 그대로 성공으로 본다. */
    @Transactional
    public void delete(Long practiceId, Long memberId) {
        memberPracticeRepository
                .findById(practiceId)
                .ifPresent(
                        practice -> {
                            requireOwner(practice, memberId);
                            memberPracticeRepository.delete(practice);
                        });
    }

    private Member findMember(Long memberId) {
        return memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private Member findMemberForUpdate(Long memberId) {
        return memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private MemberPractice findOwnedPracticeForUpdate(Long practiceId, Long memberId) {
        MemberPractice practice =
                memberPracticeRepository
                        .findByIdForUpdate(practiceId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        requireOwner(practice, memberId);
        return practice;
    }

    private MemberPractice findOwnedPractice(Long practiceId, Long memberId) {
        MemberPractice practice =
                memberPracticeRepository
                        .findById(practiceId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        requireOwner(practice, memberId);
        return practice;
    }

    private void requireOwner(MemberPractice practice, Long memberId) {
        if (!practice.isOwnedBy(memberId)) {
            throw new BusinessException(PracticeErrorCode.NOT_PRACTICE_OWNER);
        }
    }
}
