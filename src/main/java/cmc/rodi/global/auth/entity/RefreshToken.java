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

/** 서버 발급 refresh token. 원문 대신 해시를 저장하고, 재발급·로그아웃·탈퇴 시 무효화한다(ADR 0009). 회원당 여러 기기 세션 허용. */
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

    @Builder
    private RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
}
