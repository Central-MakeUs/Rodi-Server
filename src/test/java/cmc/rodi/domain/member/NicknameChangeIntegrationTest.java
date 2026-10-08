package cmc.rodi.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 닉네임 변경(스펙 020) — PATCH /members/me의 판정·저장과 "보낸 필드만 수정" 의미를 실제 DB로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class NicknameChangeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;

    private Member me;

    @BeforeEach
    void 로그인() {
        Member other = Member.createBySocial("other@kakao.com");
        other.assignNickname("Drive_Kim");
        memberRepository.save(other);

        me = Member.createBySocial("me@kakao.com");
        me.assignNickname("차근차근_토끼");
        me.applyOnboarding(Level.SEED, "기존 목표");
        me = memberRepository.save(me);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(me.getId(), null, List.of()));
    }

    @AfterEach
    void 로그아웃() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("변경: 통과한 닉네임이 저장된다")
    void 변경_성공() throws Exception {
        patchMe("{\"nickname\":\"초보_탈출\"}").andExpect(status().isOk());

        assertThat(reload().getNickname()).isEqualTo("초보_탈출");
    }

    @Test
    @DisplayName("정규화: 앞뒤 공백을 지우고, NFD 한글은 NFC로 저장한다")
    void 정규화_저장() throws Exception {
        patchMe("{\"nickname\":\"  초보_탈출 \"}").andExpect(status().isOk());
        assertThat(reload().getNickname()).isEqualTo("초보_탈출");

        String nfd = Normalizer.normalize("주차왕", Normalizer.Form.NFD);
        patchMe("{\"nickname\":\"" + nfd + "\"}").andExpect(status().isOk());
        assertThat(reload().getNickname()).isEqualTo("주차왕").hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"초보", "가나다라마바사아자차"})
    @DisplayName("길이 경계: 2자·10자는 통과")
    void 길이_경계_통과(String nickname) throws Exception {
        patchMe("{\"nickname\":\"" + nickname + "\"}").andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "\"초\"                   | MEMBER_400_2",
                "\"가나다라마바사아자차카\" | MEMBER_400_2",
                "null                     | MEMBER_400_2",
                "\"\"                     | MEMBER_400_2",
                "\"초보 탈출\"            | MEMBER_400_3",
                "\"_초보\"                | MEMBER_400_3",
                "\"초보_\"                | MEMBER_400_3",
                "\"초보__탈출\"           | MEMBER_400_3",
                "\"ㅋㅋ초보\"             | MEMBER_400_3",
                "\"초보😀\"               | MEMBER_400_3",
                "\"초보!\"                | MEMBER_400_3",
                "\"씨발\"                 | MEMBER_400_4",
                "\"씨_발\"                | MEMBER_400_4",
                "\"시1발\"                | MEMBER_400_4",
                "\"개새끼운전\"           | MEMBER_400_4",
                "\"운영자\"               | MEMBER_400_4",
                "\"ROdi\"                 | MEMBER_400_4",
                "\"미친\"                 | MEMBER_400_4",
                "\"ass\"                  | MEMBER_400_4"
            })
    @DisplayName("위반: 400과 사유 코드, 닉네임은 그대로")
    void 위반_400(String nicknameJson, String code) throws Exception {
        patchMe("{\"nickname\":" + nicknameJson + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));

        assertThat(reload().getNickname()).isEqualTo("차근차근_토끼");
    }

    @ParameterizedTest
    @ValueSource(strings = {"미친듯이", "class", "시발점", "멜로디", "새끼손가락", "초보지만"})
    @DisplayName("오탐 방지: 금칙어를 품은 정상 단어는 통과")
    void 오탐_방지(String nickname) throws Exception {
        patchMe("{\"nickname\":\"" + nickname + "\"}").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Drive_Kim", "drive_kim", "DRIVE_KIM"})
    @DisplayName("중복: 다른 회원의 닉네임은 대소문자가 달라도 409")
    void 중복_409(String nickname) throws Exception {
        patchMe("{\"nickname\":\"" + nickname + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_409_3"));
    }

    @Test
    @DisplayName("본인 닉네임: 그대로 내도 200, 대소문자만 바꿔도 200")
    void 본인_닉네임() throws Exception {
        patchMe("{\"nickname\":\"차근차근_토끼\"}").andExpect(status().isOk());

        me.changeNickname("kim_초보");
        memberRepository.flush();
        patchMe("{\"nickname\":\"KIM_초보\"}").andExpect(status().isOk());
        assertThat(reload().getNickname()).isEqualTo("KIM_초보");
    }

    @Test
    @DisplayName("부분 수정: 닉네임만 보내면 운전 목표는 그대로")
    void 닉네임만_보내면_목표_유지() throws Exception {
        patchMe("{\"nickname\":\"초보_탈출\"}").andExpect(status().isOk());

        assertThat(reload().getDrivingGoal()).isEqualTo("기존 목표");
    }

    @Test
    @DisplayName("부분 수정: 운전 목표만 보내면 닉네임은 그대로")
    void 목표만_보내면_닉네임_유지() throws Exception {
        patchMe("{\"drivingGoal\":\"새 목표\"}").andExpect(status().isOk());

        Member found = reload();
        assertThat(found.getDrivingGoal()).isEqualTo("새 목표");
        assertThat(found.getNickname()).isEqualTo("차근차근_토끼");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\""})
    @DisplayName("하위 호환: 운전 목표를 null·빈값으로 보내면 삭제된다")
    void 목표_삭제(String goalJson) throws Exception {
        patchMe("{\"drivingGoal\":" + goalJson + "}").andExpect(status().isOk());

        assertThat(reload().getDrivingGoal()).isNull();
    }

    @Test
    @DisplayName("빈 본문 {}: 200이고 아무것도 바뀌지 않는다")
    void 빈_본문() throws Exception {
        patchMe("{}").andExpect(status().isOk());

        Member found = reload();
        assertThat(found.getNickname()).isEqualTo("차근차근_토끼");
        assertThat(found.getDrivingGoal()).isEqualTo("기존 목표");
    }

    @Test
    @DisplayName("원자성: 닉네임이 실패하면 함께 보낸 운전 목표도 저장되지 않는다")
    void 닉네임_실패시_목표_미저장() throws Exception {
        patchMe("{\"drivingGoal\":\"새 목표\",\"nickname\":\"Drive_Kim\"}")
                .andExpect(status().isConflict());

        assertThat(reload().getDrivingGoal()).isEqualTo("기존 목표");
    }

    @Test
    @DisplayName("원자성: 운전 목표가 30자를 넘으면 함께 보낸 닉네임도 저장되지 않는다")
    void 목표_실패시_닉네임_미저장() throws Exception {
        String tooLong = "가".repeat(31);

        patchMe("{\"drivingGoal\":\"" + tooLong + "\",\"nickname\":\"초보_탈출\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.drivingGoal").value("30자 이하로 입력해주세요."));

        assertThat(reload().getNickname()).isEqualTo("차근차근_토끼");
    }

    private ResultActions patchMe(String body) throws Exception {
        return mockMvc.perform(
                patch("/api/v1/members/me").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Member reload() {
        return memberRepository.findById(me.getId()).orElseThrow();
    }
}
