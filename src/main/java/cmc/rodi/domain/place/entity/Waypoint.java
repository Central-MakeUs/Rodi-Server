package cmc.rodi.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/** 코스 경로점. 코스에 1:N으로 붙고 sequence로 순서를 갖는다(START·VIA·DESTINATION). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "waypoint",
        uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "sequence"}))
public class Waypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "waypoint_type", nullable = false, length = 20)
    private WaypointType waypointType;

    @Column(nullable = false)
    private Short sequence;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(length = 255)
    private String name;

    @Builder
    private Waypoint(
            Course course, WaypointType waypointType, Short sequence, Point location, String name) {
        this.course = course;
        this.waypointType = waypointType;
        this.sequence = sequence;
        this.location = location;
        this.name = name;
    }
}
