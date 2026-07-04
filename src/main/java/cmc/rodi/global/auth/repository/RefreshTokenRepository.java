package cmc.rodi.global.auth.repository;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 회원의 폐기되지 않은 모든 refresh를 폐기(재사용 탐지·탈퇴 시). */
    @Modifying(clearAutomatically = true)
    @Query(
            "update RefreshToken r set r.revokedAt = :now "
                    + "where r.member = :member and r.revokedAt is null")
    int revokeAllByMember(@Param("member") Member member, @Param("now") LocalDateTime now);
}
