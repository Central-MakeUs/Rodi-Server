package cmc.rodi.global.auth.repository;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.auth.entity.SocialAccount;
import cmc.rodi.global.auth.entity.SocialProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderId(SocialProvider provider, String providerId);

    List<SocialAccount> findByMember(Member member);

    /** 재가입 허용(Day 10): 탈퇴가 threshold(=now-재가입기간) 이전인 회원의 소셜 식별자 삭제. */
    @Modifying(clearAutomatically = true)
    @Query("delete from SocialAccount sa where sa.member.deletedAt <= :threshold")
    int deleteByMemberDeletedAtBefore(@Param("threshold") LocalDateTime threshold);
}
