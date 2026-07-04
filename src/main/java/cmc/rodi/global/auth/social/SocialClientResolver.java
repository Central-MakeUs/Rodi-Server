package cmc.rodi.global.auth.social;

import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.exception.BusinessException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** provider로 알맞은 {@link SocialClient}를 찾는 레지스트리. 새 공급자 빈이 추가되면 자동 등록된다. */
@Component
public class SocialClientResolver {

    private final Map<SocialProvider, SocialClient> clients;

    public SocialClientResolver(List<SocialClient> socialClients) {
        Map<SocialProvider, SocialClient> map = new EnumMap<>(SocialProvider.class);
        socialClients.forEach(client -> map.put(client.provider(), client));
        this.clients = map;
    }

    public SocialClient resolve(SocialProvider provider) {
        SocialClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }
        return client;
    }
}
