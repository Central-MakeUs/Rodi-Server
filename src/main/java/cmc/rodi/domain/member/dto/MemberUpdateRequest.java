package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.common.validation.GraphemeSize;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 부분 수정 요청(마이페이지). <b>보낸 필드만 수정한다</b>(스펙 020).
 *
 * <table>
 *   <tr><th>요청</th><th>{@code drivingGoal}</th><th>{@code nickname}</th></tr>
 *   <tr><td>생략</td><td>변경 없음</td><td>변경 없음</td></tr>
 *   <tr><td>{@code null}·빈값</td><td>목표 삭제</td><td>길이 위반(지울 수 없다)</td></tr>
 *   <tr><td>문자열</td><td>변경(30자)</td><td>판정 후 변경</td></tr>
 * </table>
 *
 * <p>생략과 {@code null}을 구분하려고 record가 아닌 setter로 받는다. Jackson은 본문에 <b>있는 필드만</b> setter를 부르므로(명시한
 * {@code null} 포함) setter 호출 여부가 곧 "보냈는가"다. record·{@code Optional}로는 생략한 필드도 {@code
 * Optional.empty()}로 채워져 둘을 구분할 수 없다.
 */
public class MemberUpdateRequest {

    @Schema(
            description = "운전 목표. 생략하면 그대로, null·빈값이면 삭제. 최대 30자 — 이모지·자모 포함 사용자가 보는 글자 수 기준",
            example = "골목길에 익숙해지기")
    @GraphemeSize(max = Member.DRIVING_GOAL_MAX_LENGTH)
    private String drivingGoal;

    @Schema(
            description =
                    "닉네임. 생략하면 그대로. 2~10자, 한글·영문·숫자·_(처음·끝·연속 불가), 금칙어·중복(영문 대소문자 무시) 불가."
                            + " 위반 시 MEMBER_400_2~4·MEMBER_409_3",
            example = "초보_탈출")
    private String nickname;

    private boolean drivingGoalSent;
    private boolean nicknameSent;

    /** 운전 목표만 바꾸는 요청. */
    public static MemberUpdateRequest ofDrivingGoal(String drivingGoal) {
        MemberUpdateRequest request = new MemberUpdateRequest();
        request.setDrivingGoal(drivingGoal);
        return request;
    }

    /** 닉네임만 바꾸는 요청. */
    public static MemberUpdateRequest ofNickname(String nickname) {
        MemberUpdateRequest request = new MemberUpdateRequest();
        request.setNickname(nickname);
        return request;
    }

    @JsonSetter("drivingGoal")
    void setDrivingGoal(String drivingGoal) {
        this.drivingGoal = drivingGoal;
        this.drivingGoalSent = true;
    }

    @JsonSetter("nickname")
    void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameSent = true;
    }

    public String getDrivingGoal() {
        return drivingGoal;
    }

    public String getNickname() {
        return nickname;
    }

    /** 본문에 {@code drivingGoal}이 있었는지({@code null} 포함). */
    public boolean hasDrivingGoal() {
        return drivingGoalSent;
    }

    /** 본문에 {@code nickname}이 있었는지({@code null} 포함). */
    public boolean hasNickname() {
        return nicknameSent;
    }
}
