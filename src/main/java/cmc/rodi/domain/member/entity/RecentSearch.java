package cmc.rodi.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

/**
 * 최근 검색어(스펙 008). 연관검색어(스펙 009)에서 선택한 지역(REGION)·장소(PLACE)를 최신순으로 보관한다. 회원 id로만 다루는 경량 테이블이라 {@code
 * Member} 연관 대신 raw memberId 컬럼을 쓰고, 등록/갱신은 종류별 부분 유니크 인덱스 + {@code ON CONFLICT} upsert(네이티브)로
 * 처리한다. {@code searched_at}이 최신순 정렬·상한 유지의 기준이다. 중복 판정은 REGION=이름, PLACE=place_id. type·place_id
 * 불변식은 애플리케이션 검증({@code RecentSearchRegisterRequest.isPlaceIdConsistent})과 DB CHECK 제약(V11) 이중으로
 * 지킨다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member_recent_search")
@Check(
        constraints =
                "(type = 'REGION' AND place_id IS NULL)"
                        + " OR (type = 'PLACE' AND place_id IS NOT NULL)")
public class RecentSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecentSearchType type;

    /** 표시명(REGION: 시군구명, PLACE: 장소명). */
    @Column(nullable = false, length = 100)
    private String keyword;

    /** PLACE만 채움(상세 직행용). 스냅샷이라 place FK는 없다. REGION이면 null. */
    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;
}
