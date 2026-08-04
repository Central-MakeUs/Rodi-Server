package cmc.rodi.domain.review.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.domain.review.dto.ReviewCreateResponse;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.domain.review.exception.ReviewErrorCode;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 후기 쓰기(작성·수정·삭제). 작성 시 회원의 현재 레벨을 스냅샷으로 남기고, 수정은 그 레벨이 현재 레벨과 같을 때만 허용한다(삭제는 레벨 무관). */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
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
