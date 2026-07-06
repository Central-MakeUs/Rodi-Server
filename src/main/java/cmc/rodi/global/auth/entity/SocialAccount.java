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

    /** 공급자 refresh token(애플 탈퇴 revoke용). 애플만 채운다. */
    @Column(name = "provider_refresh_token", length = 512)
    private String providerRefreshToken;

    /** 공급자 프로필 닉네임(카카오톡 닉네임 등). 서비스 닉네임(member.nickname)과 분리. */
    @Column(name = "provider_nickname", length = 50)
    private String providerNickname;

    @Column(name = "provider_profile_image_url", length = 512)
    private String providerProfileImageUrl;

    @Builder
    private SocialAccount(
            Member member,
            SocialProvider provider,
            String providerId,
            String email,
            String providerRefreshToken,
            String providerNickname,
            String providerProfileImageUrl) {
        this.member = member;
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.providerRefreshToken = providerRefreshToken;
        this.providerNickname = providerNickname;
        this.providerProfileImageUrl = providerProfileImageUrl;
    }

    /** 재로그인 시 공급자 프로필·refresh token을 최신값으로 갱신(값이 있을 때만). */
    public void updateProviderInfo(
            String providerRefreshToken, String providerNickname, String providerProfileImageUrl) {
        if (providerRefreshToken != null) {
            this.providerRefreshToken = providerRefreshToken;
        }
        if (providerNickname != null) {
            this.providerNickname = providerNickname;
        }
        if (providerProfileImageUrl != null) {
            this.providerProfileImageUrl = providerProfileImageUrl;
        }
    }

    /** 익명화 — 공급자 프로필·이메일·refresh token 등 개인정보 제거. 식별자(provider, providerId)는 재가입 차단 위해 유지. */
    public void anonymize() {
        this.email = null;
        this.providerRefreshToken = null;
        this.providerNickname = null;
        this.providerProfileImageUrl = null;
    }
}
