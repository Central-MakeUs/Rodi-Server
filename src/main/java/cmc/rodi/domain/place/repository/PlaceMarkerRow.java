package cmc.rodi.domain.place.repository;

/** 마커 클러스터 검색(스펙 021)의 행 프로젝션 — 마커에 필요한 공통 필드만. */
public interface PlaceMarkerRow {
    Long getId();

    String getPlaceType();

    String getName();

    String getAddress();

    Double getLat();

    Double getLng();
}
