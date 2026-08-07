package cmc.rodi.domain.practice.service;

import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.practice.dto.PracticeItem;
import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.repository.MemberPracticeRepository;
import cmc.rodi.global.common.pagination.CursorCodec;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 연습 목록 조회. 최근 방문순(방문 이력이 없으면 담은 시각) keyset 커서로 이어진다. 상태 필터는 두지 않고 한 목록에 전부 내려주며, 상태 구분은 클라이언트가
 * 배지로 한다.
 */
@Service
@RequiredArgsConstructor
public class PracticeQueryService {

    /** 첫 페이지용 sentinel 커서(모든 항목보다 미래). null 커서 대신 써서 바인드 타입 문제를 피한다. */
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final MemberPracticeRepository memberPracticeRepository;

    @Transactional(readOnly = true)
    public CursorPage<PracticeItem> getMyPractices(Long memberId, int size, String cursor) {
        boolean firstPage = cursor == null;
        CursorCodec.Cursor decoded = firstPage ? null : CursorCodec.decode(cursor);

        List<MemberPractice> rows =
                memberPracticeRepository.findPage(
                        memberId,
                        decoded == null ? FAR_FUTURE : parseCursorTime(decoded.sortValue()),
                        decoded == null ? Long.MAX_VALUE : decoded.id(),
                        PageRequest.of(0, size + 1));

        boolean hasNext = rows.size() > size;
        List<MemberPractice> page = hasNext ? rows.subList(0, size) : rows;
        List<PracticeItem> items =
                page.stream().map(p -> PracticeItem.of(p, toPlaceItem(p.getPlace()))).toList();
        String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;

        if (!firstPage) {
            return CursorPage.next(items, hasNext, nextCursor);
        }
        return CursorPage.first(
                items, hasNext, nextCursor, memberPracticeRepository.countByMemberId(memberId));
    }

    /** 장소 요약은 저장 목록과 동일한 아이템. 현위치를 받지 않으므로 거리(distanceFromMe)는 null. */
    private static PlaceListItem toPlaceItem(Place place) {
        if (place instanceof Course course) {
            return PlaceListItem.ofCourse(course, null);
        }
        return PlaceListItem.ofParking((Parking) place, null);
    }

    private String encodeCursor(MemberPractice last) {
        return CursorCodec.encode(last.recentActivityAt().toString(), last.getId());
    }

    private LocalDateTime parseCursorTime(String sortValue) {
        try {
            return LocalDateTime.parse(sortValue);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, e);
        }
    }
}
