package cmc.rodi.domain.place.service;

import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.Parking;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.domain.place.repository.SavedBookmarkRow;
import cmc.rodi.global.common.pagination.CursorCodec;
import cmc.rodi.global.common.pagination.CursorPage;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 북마크 조회. 다른 도메인(마이페이지)에서 저장 수·저장 목록을 얻는 진입점. */
@Service
@RequiredArgsConstructor
public class BookmarkQueryService {

    private final BookmarkRepository bookmarkRepository;
    private final PlaceRepository placeRepository;

    /** 회원이 저장한 장소 수. 마이페이지 조회·저장 목록 totalCount에 쓰인다. */
    @Transactional(readOnly = true)
    public long countByMember(Long memberId) {
        return bookmarkRepository.countByMemberId(memberId);
    }

    /**
     * 저장한 장소 목록(#3). 최신 저장순 커서 페이징. 아이템은 현위치 목록(#2)과 동일한 {@link PlaceListItem}(코스+주차장), 단 현위치가 없어
     * distanceFromMe는 null. totalCount는 첫 페이지에서만.
     */
    @Transactional(readOnly = true)
    public CursorPage<PlaceListItem> getSavedPlaces(Long memberId, int size, String cursor) {
        Long cursorId = cursor == null ? null : CursorCodec.decode(cursor).id();

        // size+1 조회로 다음 페이지 존재 판별
        List<SavedBookmarkRow> rows =
                bookmarkRepository.findSavedRefs(memberId, cursorId, size + 1);
        boolean hasNext = rows.size() > size;
        List<SavedBookmarkRow> page = hasNext ? rows.subList(0, size) : rows;

        // place 로드 후 저장순(page 순서)대로 아이템 구성. distanceFromMe=null(현위치 없음)
        List<Long> placeIds = page.stream().map(SavedBookmarkRow::getPlaceId).toList();
        Map<Long, Place> placesById =
                placeRepository.findAllById(placeIds).stream()
                        .collect(Collectors.toMap(Place::getId, Function.identity()));
        List<PlaceListItem> items =
                placeIds.stream().map(placesById::get).map(BookmarkQueryService::toItem).toList();

        String nextCursor = null;
        if (hasNext) {
            long lastBookmarkId = page.get(page.size() - 1).getBookmarkId();
            nextCursor = CursorCodec.encode(String.valueOf(lastBookmarkId), lastBookmarkId);
        }

        if (cursorId == null) { // 첫 페이지에서만 totalCount
            return CursorPage.first(
                    items, hasNext, nextCursor, bookmarkRepository.countByMemberId(memberId));
        }
        return CursorPage.next(items, hasNext, nextCursor);
    }

    private static PlaceListItem toItem(Place place) {
        if (place instanceof Course course) {
            return PlaceListItem.ofCourse(course, null); // 저장 목록은 현위치 거리 없음
        }
        return PlaceListItem.ofParking((Parking) place, null);
    }
}
