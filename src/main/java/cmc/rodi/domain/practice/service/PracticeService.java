package cmc.rodi.domain.practice.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.domain.practice.dto.PracticeRegisterResponse;
import cmc.rodi.domain.practice.dto.PracticeStatusUpdateRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitResponse;
import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.entity.PracticeStatus;
import cmc.rodi.domain.practice.exception.PracticeErrorCode;
import cmc.rodi.domain.practice.repository.MemberPracticeRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 연습 목록 담기·제거. 방문 여부 변경은 후속 커밋에서 붙인다. */
@Service
@RequiredArgsConstructor
public class PracticeService {

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
        MemberPractice saved =
                memberPracticeRepository.save(
                        MemberPractice.builder().member(member).place(place).build());
        return PracticeRegisterResponse.from(saved);
    }

    /**
     * 방문 여부 변경. {@code VISITED}는 그 자리에서 방문 처리(횟수 +1, 시각 기록)하고, {@code NOT_VISITED}는 사유를 남긴다. 미방문
     * 사유는 한 번 저장하면 덮어쓸 수 없다(409) — 다녀온 것으로 바꾸는 건 언제든 가능하다.
     */
    @Transactional
    public PracticeVisitResponse updateStatus(
            Long practiceId, Long memberId, PracticeStatusUpdateRequest request) {
        MemberPractice practice = findOwnedPractice(practiceId, memberId);

        if (request.status() == PracticeStatus.VISITED) {
            // 앱은 측정한 인정 주행거리만 보내고, 인증 여부는 서버가 필요 거리와 비교해 판정한다.
            int certifiedMeters =
                    request.certifiedDistanceMeters() == null
                            ? 0
                            : request.certifiedDistanceMeters();
            boolean certifiedNow = practice.markVisited(LocalDateTime.now(), certifiedMeters);
            return PracticeVisitResponse.of(practice, certifiedMeters, certifiedNow);
        }

        if (request.skipReason() == null) {
            throw new BusinessException(PracticeErrorCode.SKIP_REASON_REQUIRED);
        }
        if (practice.hasSkipReason()) {
            throw new BusinessException(PracticeErrorCode.SKIP_REASON_ALREADY_SET);
        }
        practice.markNotVisited(request.skipReason(), request.skipDetail());
        return PracticeVisitResponse.notVisited(practice);
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
