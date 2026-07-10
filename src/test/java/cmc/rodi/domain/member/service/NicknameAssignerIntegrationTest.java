package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 닉네임 부여의 실제 동작(리소스 풀 로드·미사용 후보 선택·유일성)을 실제 DB로 검증한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NicknameAssignerIntegrationTest {

    @Autowired NicknameAssigner nicknameAssigner;
    @Autowired MemberRepository memberRepository;

    @Test
    @DisplayName("배정: 풀에서 닉네임을 반환하고, 사용 중 닉네임은 다시 배정하지 않는다")
    void 미사용_배정_및_유일성() {
        String assigned = nicknameAssigner.assign();
        assertThat(assigned).isNotBlank();

        // 배정받은 닉네임으로 회원을 만들면(사용 중이 되면)
        Member member = Member.createBySocial("nickname-test@kakao.com");
        member.assignNickname(assigned);
        memberRepository.save(member);

        // 이후에는 그 닉네임을 다시 배정하지 않는다(미사용 후보에서만 선택).
        for (int i = 0; i < 30; i++) {
            assertThat(nicknameAssigner.assign()).isNotEqualTo(assigned);
        }
    }
}
