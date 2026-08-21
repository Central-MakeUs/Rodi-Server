package cmc.rodi.domain.place.repository;

/** 장소명 관련도 검색(스펙 009) native 쿼리의 행 프로젝션. */
public interface PlaceNameMatchRow {
    Long getId();

    String getName();

    String getAddress();

    /** 키워드가 이름에서 처음 나오는 위치(1-based, PostgreSQL POSITION). 앞쪽일수록 관련도 높음. */
    Integer getMatchPos();
}
