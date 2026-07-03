package cmc.rodi.global.auth.entity;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.common.entity.BaseEntity;
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

/** 회원의 소셜 계정. 한 회원이 여러 공급자를 연결할 수 있다(1:N). 식별은 {@code (provider, providerId)}. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "social_account",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_social_provider",
                        columnNames = {"provider", "provider_id"}))
public class SocialAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Column(length = 255)
    private String email;

    @Builder
    private SocialAccount(Member member, SocialProvider provider, String providerId, String email) {
        this.member = member;
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
    }
}
