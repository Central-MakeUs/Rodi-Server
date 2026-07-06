package cmc.rodi.domain.member.repository;

import cmc.rodi.domain.member.entity.Member;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByNickname(String nickname);

    /** 익명화 대상: 탈퇴 요청이 threshold(=now-유예) 이전이고 아직 익명화되지 않은 회원. */
    List<Member> findByDeletedAtBeforeAndAnonymizedAtIsNull(LocalDateTime threshold);
}
