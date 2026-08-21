package cmc.rodi.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.dto.CourseTutorialCompletionResponse;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberCourseTutorialService;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 코스 등록 튜토리얼 완료 저장(스펙 017) — 최초 완료 시각 보존과 인증 필수. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class MemberCourseTutorialIntegrationTest {

    @Autowired MemberCourseTutorialService memberCourseTutorialService;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;
    @PersistenceContext EntityManager em;

    @Test
    @DisplayName("처음 호출하면 완료 시각을 저장하고, 다시 호출해도 최초 시각을 유지한다")
    void 완료_멱등() {
        Member member = memberRepository.save(Member.createBySocial("tutorial@kakao.com"));
        assertThat(member.isCourseTutorialCompleted()).isFalse();

        CourseTutorialCompletionResponse first =
                memberCourseTutorialService.complete(member.getId());
        CourseTutorialCompletionResponse second =
                memberCourseTutorialService.complete(member.getId());

        assertThat(first.courseTutorialCompletedAt()).isNotNull();
        assertThat(second.courseTutorialCompletedAt()).isEqualTo(first.courseTutorialCompletedAt());
        memberRepository.flush();
        em.clear();

        Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getCourseTutorialCompletedAt())
                .isEqualTo(first.courseTutorialCompletedAt());
    }

    @Test
    @DisplayName("미인증: 토큰 없이 완료 저장 호출 시 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/course-tutorial"))
                .andExpect(status().isUnauthorized());
    }
}
