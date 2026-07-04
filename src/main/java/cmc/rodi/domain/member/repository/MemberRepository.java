package cmc.rodi.domain.member.repository;

import cmc.rodi.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByNickname(String nickname);
}
