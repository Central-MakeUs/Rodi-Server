package cmc.rodi.domain.place.entity;

import cmc.rodi.domain.member.entity.PracticeType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/** 코스(place 상속). 주행거리·주의사항 + 경로점(1:N) + 연습태그(N:M, {@link PracticeType} 재사용). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "course")
@DiscriminatorValue("COURSE")
@PrimaryKeyJoinColumn(name = "place_id")
public class Course extends Place {

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(columnDefinition = "text")
    private String cautions;

    /** 연습 태그. course_practice_type(course_id, practice_type)에 enum 이름으로 저장. */
    @ElementCollection
    @CollectionTable(name = "course_practice_type", joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "practice_type", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<PracticeType> tags = new LinkedHashSet<>();

    /** 경로점(시작·경유·도착). sequence 오름차순. */
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence asc")
    private List<Waypoint> waypoints = new ArrayList<>();

    @Builder
    private Course(
            String name,
            String description,
            String address,
            Point location,
            Integer distanceMeters,
            String cautions) {
        super(name, address, location);
        this.description = description;
        this.distanceMeters = distanceMeters;
        this.cautions = cautions;
    }

    public void addTag(PracticeType tag) {
        this.tags.add(tag);
    }

    /** 경로점 추가(cascade로 함께 저장). 양방향 연관을 여기서 세팅한다. */
    public void addWaypoint(WaypointType type, short sequence, Point location, String name) {
        this.waypoints.add(
                Waypoint.builder()
                        .course(this)
                        .waypointType(type)
                        .sequence(sequence)
                        .location(location)
                        .name(name)
                        .build());
    }

    @Override
    public PlaceType getPlaceType() {
        return PlaceType.COURSE;
    }
}
