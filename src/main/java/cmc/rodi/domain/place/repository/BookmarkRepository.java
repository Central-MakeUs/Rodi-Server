package cmc.rodi.domain.place.repository;

import cmc.rodi.domain.place.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    /** 장소의 북마크 수(상세 응답용). */
    long countByPlaceId(Long placeId);

    /** 현재 회원이 이 장소를 북마크했는지. */
    boolean existsByMemberIdAndPlaceId(Long memberId, Long placeId);
}
