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

    /**
     * 회원의 폐기되지 않은 모든 refresh를 폐기(재사용 탐지·탈퇴 시). flushAutomatically=true — 벌크 UPDATE 전에 영속성 컨텍스트의
     * 변경(예: 같은 트랜잭션에서 세팅한 member.deletedAt)을 먼저 flush해야, clearAutomatically로 컨텍스트가 비워질 때 그 변경이 유실되지
     * 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update RefreshToken r set r.revokedAt = :now "
                    + "where r.member = :member and r.revokedAt is null")
    int revokeAllByMember(@Param("member") Member member, @Param("now") LocalDateTime now);
}
