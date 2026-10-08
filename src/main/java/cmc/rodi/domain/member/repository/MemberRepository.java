package cmc.rodi.domain.member.repository;

import cmc.rodi.domain.member.entity.Member;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 다른 회원이 이 닉네임을 쓰고 있는지(영문 대소문자 무시). 본인({@code excludeMemberId})은 제외해, 자기 닉네임을 다시 내거나 대소문자만 바꾸는
     * 경우를 중복으로 보지 않는다. {@code lower()} 비교라 V27의 {@code lower(nickname)} 유니크 인덱스를 탄다.
     */
    @Query(
            "select count(m) > 0 from Member m"
                    + " where lower(m.nickname) = lower(:nickname) and m.id <> :excludeMemberId")
    boolean existsByNicknameIgnoreCaseExcluding(
            @Param("nickname") String nickname, @Param("excludeMemberId") Long excludeMemberId);

    /**
     * 누적 거리 갱신용 회원 조회(행 잠금). 누적은 읽고-더하고-쓰기라, 같은 회원의 방문 기록이 겹치면 한쪽 증가분이 사라진다(lost update). 레벨은 내려가지
     * 않아 되돌릴 수도 없으므로 갱신을 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    /** 현재 사용 중인(NULL 아님) 닉네임 목록. 닉네임 배정 시 미사용 후보 계산에 쓴다. */
    @Query("select m.nickname from Member m where m.nickname is not null")
    List<String> findUsedNicknames();

    /** 익명화 대상: 탈퇴 요청이 threshold(=now-유예) 이전이고 아직 익명화되지 않은 회원. */
    List<Member> findByDeletedAtBeforeAndAnonymizedAtIsNull(LocalDateTime threshold);
}
