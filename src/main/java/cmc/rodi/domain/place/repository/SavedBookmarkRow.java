package cmc.rodi.domain.place.repository;

/**
 * 저장 목록 커서 조회의 행 프로젝션. bookmark.id는 IDENTITY라 저장 시각과 단조 증가 → id 내림차순이 곧 최신 저장순이고 커서 keyset도 id 하나로
 * 충분하다.
 */
public interface SavedBookmarkRow {
    Long getPlaceId();

    /** 커서 keyset·정렬 기준(저장 시각순 = id순). */
    Long getBookmarkId();
}
