package cmc.rodi.domain.place.service;

import cmc.rodi.domain.place.repository.BookmarkRepository;
import cmc.rodi.domain.place.repository.PlaceRepository;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 북마크 저장·해제(#5). 코스·주차장 공통(place 단위)이며 저장·해제 모두 멱등하다. */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PlaceRepository placeRepository;

    /** 저장(멱등). 이미 저장돼 있으면 그대로 성공. 없는 장소면 404. */
    @Transactional
    public void bookmark(Long placeId, Long memberId) {
        if (!placeRepository.existsById(placeId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        // ON CONFLICT DO NOTHING — 동시 요청(더블탭)에도 유니크 충돌 없이 멱등
        bookmarkRepository.saveIfAbsent(memberId, placeId);
    }

    /** 해제(멱등). 북마크가 없어도 성공으로 본다. */
    @Transactional
    public void unbookmark(Long placeId, Long memberId) {
        bookmarkRepository.deleteByMemberIdAndPlaceId(memberId, placeId);
    }
}
