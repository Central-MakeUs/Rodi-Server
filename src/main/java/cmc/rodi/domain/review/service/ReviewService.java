package cmc.rodi.domain.review.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.domain.review.dto.ReviewCreateResponse;
import cmc.rodi.domain.review.dto.ReviewReportRequest;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.entity.ReportReason;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.domain.review.exception.ReviewErrorCode;
import cmc.rodi.domain.review.repository.ReviewReportRepository;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.form.FormType;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.util.Arrays;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 후기 쓰기(작성·수정·삭제)와 신고. 작성 시 회원의 현재 레벨을 스냅샷으로 남기고, 수정은 그 레벨이 현재 레벨과 같을 때만 허용한다(삭제는 레벨 무관). */
@Service
@RequiredArgsConstructor
public class ReviewService {

    /** 신고 사유 폼의 문항 식별자(클라이언트가 응답을 구분하는 키). */
    private static final String REPORT_QUESTION_ID = "REVIEW_REPORT_REASON";

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final PlaceRepository placeRepository;
    private final MemberRepository memberRepository;

    /** 후기 작성. 같은 장소에 여러 번 쓸 수 있다. 레벨 없는 회원(온보딩 미완료)은 작성 불가. */
    @Transactional
    public ReviewCreateResponse create(Long placeId, Long memberId, ReviewRequest request) {
        Place place =
                placeRepository
                        .findById(placeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        Member member = findMember(memberId);
        if (member.getLevel() == null) {
            throw new BusinessException(ReviewErrorCode.LEVEL_REQUIRED);
        }

        Review review =
                Review.builder()
                        .place(place)
                        .member(member)
                        .recommended(request.recommended())
                        .difficulty(request.difficulty())
                        .congestion(request.congestion())
                        .practiceMethod(request.practiceMethod())
                        .content(request.content())
                        .caution(request.caution())
                        .memberLevel(member.getLevel()) // 작성 시점 레벨 스냅샷
                        .build();
        return new ReviewCreateResponse(reviewRepository.save(review).getId());
    }

    /** 후기 수정(전체 교체). 본인 후기이고 작성 당시 레벨 = 현재 레벨일 때만 가능. */
    @Transactional
    public void update(Long reviewId, Long memberId, ReviewRequest request) {
        Review review = findReview(reviewId);
        requireOwner(review, memberId);
        if (!review.isEditableAt(findMember(memberId).getLevel())) {
            throw new BusinessException(ReviewErrorCode.LEVEL_CHANGED);
        }
        review.update(
                request.recommended(),
                request.difficulty(),
                request.congestion(),
                request.practiceMethod(),
                request.content(),
                request.caution());
    }

    /** 후기 삭제. 본인 후기면 레벨과 무관하게 삭제한다(좋아요·신고는 FK CASCADE로 함께 제거). */
    @Transactional
    public void delete(Long reviewId, Long memberId) {
        Review review = findReview(reviewId);
        requireOwner(review, memberId);
        reviewRepository.delete(review);
    }

    /** 좋아요 등록(멱등). 없는 후기면 404. 본인 후기도 누를 수 있다. */
    /** 신고 사유 폼. 선택지 문구·순서·직접입력 여부를 서버가 정의해 내려준다(앱 배포 없이 문구 변경 가능). 저장이 없어 조회 트랜잭션도 열지 않는다. */
    public FormResponse getReportForm() {
        return new FormResponse(
                REPORT_QUESTION_ID,
                FormType.SINGLE_SELECT,
                "신고 사유",
                null, // 화면에 부연 설명 없음
                true,
                Arrays.stream(ReportReason.values())
                        .sorted(Comparator.comparingInt(ReportReason::getOrder))
                        .map(ReportReason::toOption)
                        .toList());
    }

    /** 후기 신고(멱등). 본인 후기는 신고할 수 없고, 재신고는 첫 신고를 유지한다. */
    @Transactional
    public void report(Long reviewId, Long memberId, ReviewReportRequest request) {
        Review review = findReview(reviewId);
        if (review.isOwnedBy(memberId)) {
            throw new BusinessException(ReviewErrorCode.SELF_REPORT_NOT_ALLOWED);
        }
        reviewReportRepository.saveIfAbsent(
                reviewId, memberId, request.reason().name(), request.detail());
    }

    private Review findReview(Long reviewId) {
        return reviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    /** 후기는 목록에 공개돼 있어 존재를 숨길 이유가 없다 → 남의 후기는 404가 아니라 403. */
    private void requireOwner(Review review, Long memberId) {
        if (!review.isOwnedBy(memberId)) {
            throw new BusinessException(ReviewErrorCode.NOT_REVIEW_OWNER);
        }
    }
}
