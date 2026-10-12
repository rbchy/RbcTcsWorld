package com.rbctcsworld.ecommerce.auth;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;

/**
 * RedisOps on the Lettuce client. The connection is opened on first use (the backend starts even when Redis
 * is not up yet) and Lettuce reconnects by itself afterwards. Short timeouts: a slow Redis must not make
 * every login slow - the store falls back to memory instead.
 */
class LettuceRedisOps implements RedisOps {

    /** One round trip, no race between "count" and "lock": Redis runs a script atomically. */
    static final String RECORD_FAILURE = """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if n >= tonumber(ARGV[2]) then
              redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
              redis.call('DEL', KEYS[1])
            end
            return n
            """;

    private static final Duration TIMEOUT = Duration.ofMillis(500);

    private final RedisClient client;
    private StatefulRedisConnection<String, String> connection;

    LettuceRedisOps(String url) {
        RedisURI uri = RedisURI.create(url);
        uri.setTimeout(TIMEOUT);
        client = RedisClient.create(uri);
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(TIMEOUT).build())
                .build());
    }

    private synchronized RedisCommands<String, String> redis() {
        if (connection == null || !connection.isOpen()) {
            connection = client.connect();
        }
        return connection.sync();
    }

    @Override
    public long recordFailure(String failKey, String lockKey, long windowMillis, int maxFailures, long lockMillis) {
        Long n = redis().eval(RECORD_FAILURE, ScriptOutputType.INTEGER, new String[] {failKey, lockKey},
                String.valueOf(windowMillis), String.valueOf(maxFailures), String.valueOf(lockMillis));
        return n == null ? 0 : n;
    }

    @Override
    public long ttlMillis(String key) {
        Long ttl = redis().pttl(key);
        return ttl == null ? -2 : ttl;
    }

    @Override
    public long count(String key) {
        String v = redis().get(key);
        return v == null ? 0 : Long.parseLong(v);
    }

    @Override
    public void delete(String... keys) {
        redis().del(keys);
    }

    @Override
    public synchronized void close() {
        if (connection != null) connection.close();
        client.shutdown();
    }
}
