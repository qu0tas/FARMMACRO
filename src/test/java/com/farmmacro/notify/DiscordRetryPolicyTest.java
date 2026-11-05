package com.farmmacro.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

/** Повтор только когда соединение не установилось: тогда Discord точно ничего не получил и дубля не будет. */
class DiscordRetryPolicyTest {
    @Test
    void onlyConnectFailuresAreRetried() {
        assertTrue(DiscordNotify.connectFailure(new ConnectException("refused")));
        assertTrue(DiscordNotify.connectFailure(new HttpConnectTimeoutException("connect timed out")));
        assertFalse(
                DiscordNotify.connectFailure(new HttpTimeoutException("request timed out")),
                "ответа не дождались — мог дойти");
        assertFalse(DiscordNotify.connectFailure(new IOException("reset")));
        assertEquals(2, DiscordNotify.CONNECT_RETRY_MS.length);
    }
}
