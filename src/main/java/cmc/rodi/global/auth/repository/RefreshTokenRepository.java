package cmc.rodi.global.auth.repository;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.auth.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByMember(Member member);
}
