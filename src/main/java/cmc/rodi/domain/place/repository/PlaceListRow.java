package cmc.rodi.domain.place.repository;

/** 현위치 목록 native 쿼리의 행 프로젝션(공통 필드 + 현위치까지 거리). 코스 전용 필드(태그·주행거리)는 서비스가 별도로 채운다. */
public interface PlaceListRow {
    Long getId();

    String getPlaceType();

    String getName();

    String getAddress();

    Double getLat();

    Double getLng();

    /** 현위치까지 거리(m). */
    Double getDistance();

    /** 필터 매칭 여부(1=매칭, 0=비매칭). 필터 적용 쿼리에서만 채워지고, 비필터 쿼리에선 null. */
    Integer getMatched();
}
