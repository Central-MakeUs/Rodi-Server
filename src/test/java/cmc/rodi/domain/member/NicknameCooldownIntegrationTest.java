package cmc.rodi.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

/** 닉네임 변경 30일 제한(스펙 020, #80) — PATCH /members/me와 사용 가능 여부 확인 API를 실제 DB로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class NicknameCooldownIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;

    private Member me;

    @AfterEach
    void 로그아웃() {
        SecurityContextHolder.clearContext();
    }

    /** 가입 때 받은 닉네임만 있는 회원(아직 직접 바꾼 적 없음). */
    private void 처음_회원() {
        me = Member.createBySocial("cooldown@kakao.com");
        me.assignNickname("차근차근_토끼");
        me.applyOnboarding(Level.SEED, "기존 목표");
        login();
    }

    /** {@code daysAgo}일 전에 닉네임을 {@code kim_초보}로 직접 바꾼 회원. */
    private void 바꾼_지(int daysAgo) {
        me = Member.createBySocial("cooldown@kakao.com");
        me.assignNickname("차근차근_토끼");
        me.applyOnboarding(Level.SEED, "기존 목표");
        me.changeNickname("kim_초보", now().minusDays(daysAgo));
        login();
    }

    private void login() {
        me = memberRepository.save(me);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(me.getId(), null, List.of()));
    }

    @Test
    @DisplayName("첫 변경: 바로 바꿀 수 있고 변경 시각이 기록된다")
    void 첫_변경() throws Exception {
        처음_회원();

        patchMe("{\"nickname\":\"초보_탈출\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("초보_탈출"))
                .andExpect(jsonPath("$.data.nicknameChangeableAt").exists());

        Member found = reload();
        assertThat(found.getNickname()).isEqualTo("초보_탈출");
        assertThat(found.getNicknameChangedAt()).isCloseTo(now(), within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("제한 중: 다른 닉네임으로 바꾸면 400 MEMBER_400_5, 닉네임 그대로")
    void 제한_중_거부() throws Exception {
        바꾼_지(10);
        String changeableAt = iso(reload().getNicknameChangedAt().plusDays(30));

        patchMe("{\"nickname\":\"초보_탈출\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_400_5"))
                .andExpect(jsonPath("$.data.nicknameChangeableAt").value(changeableAt));

        assertThat(reload().getNickname()).isEqualTo("kim_초보");
    }

    @Test
    @DisplayName("30일이 지나면 다시 바꿀 수 있고, 변경 시각이 새로 기록된다")
    void 기간_지나면_허용() throws Exception {
        바꾼_지(31);
        LocalDateTime before = reload().getNicknameChangedAt();

        patchMe("{\"nickname\":\"초보_탈출\"}").andExpect(status().isOk());

        Member found = reload();
        assertThat(found.getNickname()).isEqualTo("초보_탈출");
        assertThat(found.getNicknameChangedAt()).isAfter(before);
    }

    @Test
    @DisplayName("제한 중 같은 값: 200, 아무것도 바뀌지 않고 변경 시각도 그대로")
    void 제한_중_같은_값() throws Exception {
        바꾼_지(10);
        LocalDateTime changedAt = reload().getNicknameChangedAt();

        patchMe("{\"nickname\":\"kim_초보\"}").andExpect(status().isOk());

        Member found = reload();
        assertThat(found.getNickname()).isEqualTo("kim_초보");
        assertThat(found.getNicknameChangedAt()).isEqualTo(changedAt);
    }

    @Test
    @DisplayName("같은 값은 검사 전에 통과: 지금 닉네임이 현재 규칙에 어긋나도 그대로 보내면 200, 운전 목표도 바뀐다")
    void 같은_값_검사_생략() throws Exception {
        처음_회원();
        me.assignNickname("미친"); // 금칙어(정확 일치) — 규칙이 나중에 바뀐 상황
        memberRepository.flush();

        patchMe("{\"nickname\":\"미친\",\"drivingGoal\":\"새 목표\"}").andExpect(status().isOk());
        check("미친").andExpect(jsonPath("$.data.available").value(true));

        Member found = reload();
        assertThat(found.getNickname()).isEqualTo("미친");
        assertThat(found.getNicknameChangedAt()).isNull();
        assertThat(found.getDrivingGoal()).isEqualTo("새 목표");
    }

    @Test
    @DisplayName("제한 중 대소문자만 변경: 200, 닉네임은 바뀌고 변경 시각은 그대로")
    void 제한_중_대소문자() throws Exception {
        바꾼_지(10);
        LocalDateTime changedAt = reload().getNicknameChangedAt();

        patchMe("{\"nickname\":\"KIM_초보\"}").andExpect(status().isOk());

        Member found = reload();
        assertThat(found.getNickname()).isEqualTo("KIM_초보");
        assertThat(found.getNicknameChangedAt()).isEqualTo(changedAt);
    }

    @Test
    @DisplayName("제한 중 운전 목표와 함께 보내면 400 MEMBER_400_5, 둘 다 저장되지 않는다")
    void 제한_중_원자성() throws Exception {
        바꾼_지(10);

        patchMe("{\"drivingGoal\":\"새 목표\",\"nickname\":\"초보_탈출\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_400_5"));

        Member found = reload();
        assertThat(found.getNickname()).isEqualTo("kim_초보");
        assertThat(found.getDrivingGoal()).isEqualTo("기존 목표");
    }

    @Test
    @DisplayName("제한 중 형식이 틀린 닉네임도 MEMBER_400_5가 먼저 나온다")
    void 제한이_형식보다_먼저() throws Exception {
        바꾼_지(10);

        patchMe("{\"nickname\":\"초\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_400_5"));
    }

    @Test
    @DisplayName("제한 중 운전 목표만 보내면 그대로 바뀐다(닉네임 제한과 무관)")
    void 제한_중_목표만() throws Exception {
        바꾼_지(10);

        patchMe("{\"drivingGoal\":\"새 목표\"}").andExpect(status().isOk());

        assertThat(reload().getDrivingGoal()).isEqualTo("새 목표");
    }

    @Test
    @DisplayName("다른 에러(중복 409)는 data가 그대로 null이다")
    void 다른_에러_data_없음() throws Exception {
        Member other = Member.createBySocial("other@kakao.com");
        other.assignNickname("Drive_Kim");
        memberRepository.save(other);
        처음_회원();

        patchMe("{\"nickname\":\"drive_kim\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_409_3"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("성공 응답: 운전 목표만 바꿔도 현재 닉네임과 다음 변경 가능 시각을 준다")
    void 성공_응답_목표만() throws Exception {
        바꾼_지(10);
        String changeableAt = iso(reload().getNicknameChangedAt().plusDays(30));

        patchMe("{\"drivingGoal\":\"새 목표\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("kim_초보"))
                .andExpect(jsonPath("$.data.nicknameChangeableAt").value(changeableAt));
    }

    @Test
    @DisplayName("성공 응답: 제한이 없으면 nicknameChangeableAt은 null")
    void 성공_응답_제한_없음() throws Exception {
        바꾼_지(31);

        patchMe("{\"drivingGoal\":\"새 목표\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nicknameChangeableAt").doesNotExist());
    }

    @Test
    @DisplayName(
            "확인 API: 제한 중이면 available=false·MEMBER_400_5·다음 변경 가능 시각, 같은 값·대소문자만 다르면 available=true")
    void 확인_API_제한_중() throws Exception {
        바꾼_지(10);
        String changeableAt = iso(reload().getNicknameChangedAt().plusDays(30));

        check("초보_탈출")
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("MEMBER_400_5"))
                .andExpect(jsonPath("$.data.nicknameChangeableAt").value(changeableAt));
        check("kim_초보")
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.nicknameChangeableAt").doesNotExist());
        check("KIM_초보").andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("확인 API: 다른 사유(길이)면 nicknameChangeableAt은 null")
    void 확인_API_다른_사유() throws Exception {
        처음_회원();

        check("초")
                .andExpect(jsonPath("$.data.reason").value("MEMBER_400_2"))
                .andExpect(jsonPath("$.data.nicknameChangeableAt").doesNotExist());
    }

    @Test
    @DisplayName("마이페이지: 제한 중이면 nicknameChangeableAt = 변경 시각 + 30일, 아니면 null")
    void 마이페이지_변경_가능_시각() throws Exception {
        바꾼_지(10);
        String changeableAt = iso(reload().getNicknameChangedAt().plusDays(30));

        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nicknameChangeableAt").value(changeableAt));
    }

    @Test
    @DisplayName("마이페이지: 아직 직접 바꾼 적 없으면 nicknameChangeableAt은 null")
    void 마이페이지_첫_변경_전() throws Exception {
        처음_회원();

        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nicknameChangeableAt").doesNotExist());
    }

    @Test
    @DisplayName("확인 API: 아직 직접 바꾼 적 없으면 제한 없음")
    void 확인_API_첫_변경() throws Exception {
        처음_회원();

        check("초보_탈출").andExpect(jsonPath("$.data.available").value(true));
    }

    private ResultActions patchMe(String body) throws Exception {
        return mockMvc.perform(
                patch("/api/v1/members/me").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions check(String nickname) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/members/nickname/availability").param("nickname", nickname))
                .andExpect(status().isOk());
    }

    private Member reload() {
        return memberRepository.findById(me.getId()).orElseThrow();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 응답 JSON과 같은 형식 — Jackson은 LocalDateTime을 ISO_LOCAL_DATE_TIME으로 쓴다(toString()과 소수 자릿수가 다를 수
     * 있다).
     */
    private static String iso(LocalDateTime value) {
        return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
