package cmc.rodi.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.dto.NicknameAvailabilityResponse;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 닉네임 사용 가능 여부(스펙 019) — 정책 판정과 대소문자 무시·본인 제외 중복을 실제 DB로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class NicknameServiceIntegrationTest {

    @Autowired NicknameService nicknameService;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;

    private Long meId;

    @BeforeEach
    void setUp() {
        memberRepository.save(memberWithNickname("other@kakao.com", "Drive_Kim"));
        meId = memberRepository.save(memberWithNickname("me@kakao.com", "kim_초보")).getId();
    }

    @Test
    @DisplayName("사용 가능: available=true, 사유 없음")
    void 사용_가능() {
        NicknameAvailabilityResponse response = nicknameService.checkAvailability(meId, "초보_탈출");

        assertThat(response.available()).isTrue();
        assertThat(response.reason()).isNull();
        assertThat(response.message()).isNull();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "초         | MEMBER_400_2",
                "가나다라마바사아자차카 | MEMBER_400_2",
                "초보 탈출  | MEMBER_400_3",
                "초보__탈출 | MEMBER_400_3",
                "씨_발      | MEMBER_400_4",
                "운영자     | MEMBER_400_4",
                "drive_kim   | MEMBER_409_3",
                "DRIVE_KIM   | MEMBER_409_3"
            })
    @DisplayName("사용 불가: 길이·문자·금칙어·중복(대소문자 무시) 사유를 같은 코드로 준다")
    void 사용_불가_사유(String nickname, String reason) {
        NicknameAvailabilityResponse response = nicknameService.checkAvailability(meId, nickname);

        assertThat(response.available()).isFalse();
        assertThat(response.reason()).isEqualTo(reason);
        assertThat(response.message()).isNotBlank();
    }

    @Test
    @DisplayName("본인 현재 닉네임은 그대로든 대소문자만 바꾸든 사용 가능")
    void 본인_닉네임_사용_가능() {
        assertThat(nicknameService.checkAvailability(meId, "kim_초보").available()).isTrue();
        assertThat(nicknameService.checkAvailability(meId, "KIM_초보").available()).isTrue();
    }

    @Test
    @DisplayName("정규화: 앞뒤 공백을 지운 닉네임으로 판정하고 그대로 돌려준다")
    void 정규화된_닉네임_반환() {
        NicknameAvailabilityResponse response = nicknameService.checkAvailability(meId, "  초보_탈출 ");

        assertThat(response.nickname()).isEqualTo("초보_탈출");
        assertThat(response.available()).isTrue();
    }

    @Test
    @DisplayName("빈 문자열·null은 길이 위반")
    void 빈_값_길이_위반() {
        assertThat(nicknameService.checkAvailability(meId, "").reason()).isEqualTo("MEMBER_400_2");
        assertThat(nicknameService.checkAvailability(meId, null).reason())
                .isEqualTo("MEMBER_400_2");
    }

    @Test
    @DisplayName("미인증: 토큰 없이 호출하면 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(get("/api/v1/members/nickname/availability").param("nickname", "초보_탈출"))
                .andExpect(status().isUnauthorized());
    }

    private Member memberWithNickname(String email, String nickname) {
        Member member = Member.createBySocial(email);
        member.assignNickname(nickname);
        return member;
    }
}
