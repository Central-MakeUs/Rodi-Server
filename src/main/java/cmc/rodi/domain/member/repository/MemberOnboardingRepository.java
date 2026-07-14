package cmc.rodi.domain.member.repository;

import cmc.rodi.domain.member.entity.MemberOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;

/** 온보딩 원자료 저장소. PK는 member_id(1:1)라 {@code existsById(memberId)}로 온보딩 완료 여부를 판정한다. */
public interface MemberOnboardingRepository extends JpaRepository<MemberOnboarding, Long> {}
