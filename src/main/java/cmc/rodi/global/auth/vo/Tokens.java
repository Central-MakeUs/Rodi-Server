package cmc.rodi.global.auth.vo;

/** 발급된 토큰 세트(access + refresh 원문). */
public record Tokens(String accessToken, String refreshToken) {}
