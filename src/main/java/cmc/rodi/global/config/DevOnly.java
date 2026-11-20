package cmc.rodi.global.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Profile;

/**
 * 로컬·개발 서버 전용 빈(스펙 023). 테스트용·내부용 API 컨트롤러에 붙이면 {@code prod}에서는 빈이 만들어지지 않아 엔드포인트가 없고(404)
 * Swagger에서도 빠진다. 인증 규칙은 바꾸지 않는다 — 붙여도 JWT가 필요한 API는 그대로 필요하다.
 *
 * <p>관리자 API처럼 운영에서도 써야 하는 기능에는 붙이지 않는다(권한으로 막을 대상).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Profile({"local", "dev"})
public @interface DevOnly {}
