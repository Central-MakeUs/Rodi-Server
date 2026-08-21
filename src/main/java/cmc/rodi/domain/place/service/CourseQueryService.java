package cmc.rodi.domain.place.service;

import cmc.rodi.domain.place.dto.MyCourseItem;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.global.common.pagination.CursorCodec;
import cmc.rodi.global.common.pagination.CursorPage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내가 등록한 코스 조회(스펙 015). */
@Service
@RequiredArgsConstructor
public class CourseQueryService {

    private final CourseRepository courseRepository;

    /**
     * 내 코스 목록. 최신 등록순 커서 페이징이며 {@code status}로 승인 상태를 걸러 볼 수 있다(null이면 전체).
     *
     * <p>승인 전 코스는 전체 목록·검색에 나오지 않으므로(스펙 014), 등록자가 자기 코스의 심사 상태를 확인하는 유일한 창구다.
     */
    @Transactional(readOnly = true)
    public CursorPage<MyCourseItem> getMyCourses(
            Long memberId, ApprovalStatus status, int size, String cursor) {
        Long cursorId = cursor == null ? null : CursorCodec.decode(cursor).id();

        // size+1 조회로 다음 페이지 존재 판별
        List<MyCourseItem> rows =
                courseRepository.findMyCourses(memberId, status, cursorId, Limit.of(size + 1));
        boolean hasNext = rows.size() > size;
        List<MyCourseItem> items = hasNext ? rows.subList(0, size) : rows;

        String nextCursor = null;
        if (hasNext) {
            long lastId = items.get(items.size() - 1).courseId();
            nextCursor = CursorCodec.encode(String.valueOf(lastId), lastId);
        }

        if (cursorId == null) { // 첫 페이지에서만 totalCount(상태 필터가 적용된 개수)
            return CursorPage.first(
                    items, hasNext, nextCursor, courseRepository.countMyCourses(memberId, status));
        }
        return CursorPage.next(items, hasNext, nextCursor);
    }
}
