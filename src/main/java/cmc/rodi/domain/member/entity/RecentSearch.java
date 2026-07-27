package cmc.rodi.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 최근 검색어(스펙 008). 회원이 검색한 키워드를 최신순으로 보관한다. 회원 id로만 다루는 경량 로그성 테이블이라 {@code Member} 연관 대신 raw
 * memberId 컬럼을 쓰고, 저장/갱신은 {@code ON CONFLICT} upsert(네이티브)로 처리한다. {@code searched_at}이 최신순 정렬·상한
 * 유지의 기준이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "member_recent_search",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "keyword"}))
public class RecentSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 50)
    private String keyword;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;
}
