package cmc.rodi.domain.member.entity;

/** 최근 검색어 항목 종류(스펙 008). 연관검색어(스펙 009)에서 선택한 대상의 유형. */
public enum RecentSearchType {
    REGION, // 지역명(시군구). place_id 없음
    PLACE // 장소명. place_id 함께 저장
}
