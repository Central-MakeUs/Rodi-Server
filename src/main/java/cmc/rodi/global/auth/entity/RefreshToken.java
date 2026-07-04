package cmc.rodi.global.auth.entity;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서버 발급 refresh token. 원문 대신 해시를 저장하며, 재발급 시 회전한다(ADR 0009). 회원당 여러 기기 세션 허용. 폐기는 삭제가 아닌 {@code
 * revokedAt} 마킹으로 처리해, 폐기된 토큰의 재제출을 탐지한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "refresh_token",
        uniqueConstraints =
                @UniqueConstraint(name = "uq_refresh_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_refresh_token_member", columnList = "member_id"))
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Builder
    private RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    /** 회전: 새 토큰으로 교체하며 폐기 마킹(재사용 탐지 근거로 행은 남긴다). */
    public void rotate(Long newTokenId, LocalDateTime now) {
        this.revokedAt = now;
        this.replacedByTokenId = newTokenId;
        this.lastUsedAt = now;
    }

    /** 단순 폐기(로그아웃 등). */
    public void revoke(LocalDateTime now) {
        this.revokedAt = now;
    }
}
