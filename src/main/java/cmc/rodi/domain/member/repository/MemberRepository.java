package cmc.rodi.domain.member.repository;

import cmc.rodi.domain.member.entity.Member;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByNickname(String nickname);

    /** 현재 사용 중인(NULL 아님) 닉네임 목록. 닉네임 배정 시 미사용 후보 계산에 쓴다. */
    @Query("select m.nickname from Member m where m.nickname is not null")
    List<String> findUsedNicknames();

    /** 익명화 대상: 탈퇴 요청이 threshold(=now-유예) 이전이고 아직 익명화되지 않은 회원. */
    List<Member> findByDeletedAtBeforeAndAnonymizedAtIsNull(LocalDateTime threshold);
}
