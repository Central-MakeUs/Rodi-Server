package cmc.rodi.domain.member.entity;

import cmc.rodi.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원. 소셜 로그인으로 가입하며, 신원은 {@code social_account}가 별도 관리한다. 탈퇴는 soft delete(ADR 0004). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Column(length = 255)
    private String email;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Member(String nickname, String email) {
        this.nickname = nickname;
        this.email = email;
    }
}
