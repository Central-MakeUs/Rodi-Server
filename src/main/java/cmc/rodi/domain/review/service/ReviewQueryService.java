package cmc.rodi.domain.review.service;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.domain.review.dto.ReviewItem;
import cmc.rodi.domain.review.dto.ReviewListRequest;
import cmc.rodi.domain.review.dto.ReviewSummaryResponse;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.domain.review.repository.LevelCountRow;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.domain.review.repository.ReviewSummaryRow;
import cmc.rodi.global.common.pagination.CursorCodec;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후기 조회 — 레벨별 목록(최신순 커서)과 난이도 분포 요약. 레벨 필터는 생략 시 조회자 본인 레벨, {@code ALL}이면 전체다. 목록은 내가 차단한 회원의 후기를
 * 빼지만, 요약 수치는 차단과 무관하게 전체 기준이라 모두에게 동일하다.
 */
@Service
@RequiredArgsConstructor
public class ReviewQueryService {

    /** 첫 페이지용 sentinel 커서(모든 후기보다 미래). null 커서 대신 써서 바인드 타입 문제를 피한다. */
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final MemberRepository memberRepository;

    /** 장소 후기 목록. 최신순(created_at DESC, id DESC) keyset 커서로 이어진다. */
    @Transactional(readOnly = true)
    public CursorPage<ReviewItem> getReviews(
            Long placeId, Long memberId, ReviewListRequest request) {
        requirePlaceExists(placeId);
        Member me = findMember(memberId);
        Level filter = resolveLevel(request, me);
        boolean firstPage = request.cursor() == null;

        CursorCodec.Cursor cursor = firstPage ? null : CursorCodec.decode(request.cursor());
        List<Review> rows =
                reviewRepository.findPage(
                        placeId,
                        levelsOf(filter),
                        memberId,
                        cursor == null ? FAR_FUTURE : parseCursorTime(cursor.sortValue()),
                        cursor == null ? Long.MAX_VALUE : cursor.id(),
                        PageRequest.of(0, request.size() + 1));

        boolean hasNext = rows.size() > request.size();
        List<Review> page = hasNext ? rows.subList(0, request.size()) : rows;
        List<ReviewItem> items = toItems(page, memberId, me.getLevel());
        String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;

        if (!firstPage) {
            return CursorPage.next(items, hasNext, nextCursor);
        }
        return CursorPage.first(
                items,
                hasNext,
                nextCursor,
                reviewRepository.countVisible(placeId, levelsOf(filter), memberId));
    }

    /** 후기 요약(난이도·혼잡도 분포, 추천 수, 레벨별 후기 수). 집계 단위는 후기 건수다. */
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(
            Long placeId, Long memberId, ReviewListRequest request) {
        requirePlaceExists(placeId);
        Level filter = resolveLevel(request, findMember(memberId));

        ReviewSummaryRow row =
                reviewRepository.summarize(placeId, filter == null ? null : filter.name());
        return new ReviewSummaryResponse(
                filter == null ? ReviewListRequest.ALL_LEVELS : filter.name(),
                row.getTotal(),
                row.getRecommendCount(),
                row.getNotRecommendCount(),
                difficultyCounts(row),
                congestionCounts(row),
                levelCounts(placeId));
    }

    /** 레벨 필터 없음(null)은 전체 레벨 목록으로 표현한다 — null 바인딩은 Postgres가 타입을 못 정한다. */
    private static List<Level> levelsOf(Level filter) {
        return filter == null ? List.of(Level.values()) : List.of(filter);
    }

    /** 생략(null) → 조회자 본인 레벨(없으면 전체), ALL → 전체, 그 외 → 지정 레벨. */
    private Level resolveLevel(ReviewListRequest request, Member me) {
        if (request.allLevels()) {
            return null;
        }
        Level explicit = request.explicitLevel();
        return explicit != null ? explicit : me.getLevel();
    }

    /** 작성자는 fetch join으로 함께 읽어 항목별 추가 조회(N+1)가 없다. */
    private List<ReviewItem> toItems(List<Review> page, Long memberId, Level currentLevel) {
        return page.stream()
                .map(
                        review -> {
                            boolean mine = review.isOwnedBy(memberId);
                            return ReviewItem.of(
                                    review, mine, mine && review.isEditableAt(currentLevel));
                        })
                .toList();
    }

    private Map<Difficulty, Long> difficultyCounts(ReviewSummaryRow row) {
        Map<Difficulty, Long> counts = new EnumMap<>(Difficulty.class);
        counts.put(Difficulty.VERY_EASY, row.getVeryEasy());
        counts.put(Difficulty.EASY, row.getEasy());
        counts.put(Difficulty.NORMAL, row.getNormalDifficulty());
        counts.put(Difficulty.HARD, row.getHard());
        counts.put(Difficulty.VERY_HARD, row.getVeryHard());
        return counts;
    }

    private Map<Congestion, Long> congestionCounts(ReviewSummaryRow row) {
        Map<Congestion, Long> counts = new EnumMap<>(Congestion.class);
        counts.put(Congestion.QUIET, row.getQuiet());
        counts.put(Congestion.NORMAL, row.getNormalCongestion());
        counts.put(Congestion.CROWDED, row.getCrowded());
        return counts;
    }

    /** 후기 없는 레벨도 0으로 채워 드롭다운이 항상 5개를 그릴 수 있게 한다. */
    private Map<Level, Long> levelCounts(Long placeId) {
        Map<Level, Long> counts = new LinkedHashMap<>();
        for (Level level : Level.values()) {
            counts.put(level, 0L);
        }
        for (LevelCountRow row : reviewRepository.countByLevel(placeId)) {
            counts.put(row.getLevel(), row.getCnt());
        }
        return counts;
    }

    private String encodeCursor(Review last) {
        return CursorCodec.encode(last.getCreatedAt().toString(), last.getId());
    }

    private LocalDateTime parseCursorTime(String sortValue) {
        try {
            return LocalDateTime.parse(sortValue);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, e);
        }
    }

    private void requirePlaceExists(Long placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
    }

    private Member findMember(Long memberId) {
        return memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }
}
