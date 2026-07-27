package cmc.rodi.global.auth.resolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 인증된 회원의 id를 컨트롤러 파라미터로 주입한다. JWT 필터가 넣은 SecurityContext에서 꺼낸다. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {

    /**
     * 인증이 필수인지 여부. true(기본)면 미인증 시 401. false면 미인증 시 null을 주입해 옵셔널 JWT 엔드포인트(공개 목록·검색 등)에서 로그인 여부에
     * 따라 분기할 수 있게 한다.
     */
    boolean required() default true;
}
