package cmc.rodi.domain.place.entity;

import cmc.rodi.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/**
 * 장소 공통 슈퍼클래스. 주차장(course)·코스(parking)를 JOINED 상속으로 두고 {@code place_id}를 공유 PK로 쓴다(ADR 0002). 구분자는
 * {@code place_type}. 위치는 PostGIS geometry(Point, SRID 4326)이며 대표 좌표(코스=시작점, 주차장=위치)로 목록 거리정렬·좌표응답에
 * 쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "place")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "place_type", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class Place extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** 시군구 단위 주소(예: "서울특별시 강남구"). 목록·좌표 응답 표시용. */
    @Column(length = 100)
    private String address;

    /** 대표 좌표(SRID 4326). 코스는 시작점, 주차장은 위치. */
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    protected Place(String name, String description, String address, Point location) {
        this.name = name;
        this.description = description;
        this.address = address;
        this.location = location;
    }

    /** 이 장소의 구분자. 서브클래스가 반환한다. */
    public abstract PlaceType getPlaceType();
}
