package cmc.rodi.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** 닉네임 유일성(V28): 영문 대소문자 무시 유니크 인덱스와 본인 제외 중복 조회를 실제 DB로 검증한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MemberNicknameUniquenessIntegrationTest {

    @Autowired MemberRepository memberRepository;

    @Test
    @DisplayName("유니크 인덱스: 대소문자만 다른 닉네임은 저장할 수 없다")
    void 대소문자만_다른_닉네임_저장_불가() {
        memberRepository.saveAndFlush(memberWithNickname("upper@kakao.com", "Drive_Kim"));

        assertThatThrownBy(
                        () ->
                                memberRepository.saveAndFlush(
                                        memberWithNickname("lower@kakao.com", "drive_kim")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("유니크 인덱스: 닉네임 없는 회원(익명화)은 여러 명이어도 된다")
    void 닉네임_NULL_다수_허용() {
        Member first = memberRepository.saveAndFlush(Member.createBySocial("null-1@kakao.com"));
        Member second = memberRepository.saveAndFlush(Member.createBySocial("null-2@kakao.com"));

        assertThat(first.getNickname()).isNull();
        assertThat(second.getNickname()).isNull();
    }

    @Test
    @DisplayName("중복 조회: 다른 회원의 닉네임은 대소문자가 달라도 중복이다")
    void 다른_회원_닉네임_대소문자_무시_중복() {
        memberRepository.saveAndFlush(memberWithNickname("owner@kakao.com", "Drive_Kim"));
        Member me = memberRepository.saveAndFlush(Member.createBySocial("me@kakao.com"));

        assertThat(memberRepository.existsByNicknameIgnoreCaseExcluding("drive_kim", me.getId()))
                .isTrue();
        assertThat(memberRepository.existsByNicknameIgnoreCaseExcluding("Drive_Kim", me.getId()))
                .isTrue();
        assertThat(memberRepository.existsByNicknameIgnoreCaseExcluding("Drive_Lee", me.getId()))
                .isFalse();
    }

    @Test
    @DisplayName("중복 조회: 본인 닉네임은 대소문자를 바꿔도 중복이 아니다")
    void 본인_닉네임_제외() {
        Member me = memberRepository.saveAndFlush(memberWithNickname("me@kakao.com", "kim_초보"));

        assertThat(memberRepository.existsByNicknameIgnoreCaseExcluding("kim_초보", me.getId()))
                .isFalse();
        assertThat(memberRepository.existsByNicknameIgnoreCaseExcluding("KIM_초보", me.getId()))
                .isFalse();
    }

    private Member memberWithNickname(String email, String nickname) {
        Member member = Member.createBySocial(email);
        member.assignNickname(nickname);
        return member;
    }
}
