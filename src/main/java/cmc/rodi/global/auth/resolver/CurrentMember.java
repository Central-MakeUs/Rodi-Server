package cmc.rodi.global.auth.resolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 인증된 회원의 id를 컨트롤러 파라미터로 주입한다. JWT 필터가 넣은 SecurityContext에서 꺼낸다. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {}
