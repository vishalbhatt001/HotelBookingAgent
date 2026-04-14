package com.enterprise.booking.observability;

import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MethodLog {

    private static final int MAX_VALUE_LEN = 500;

    private MethodLog() {
    }

    public static Scope start(Logger log, String methodName, String purpose, Object... keyValues) {
        String callId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        Map<String, Object> args = kv(keyValues);
        log.info(
                "method_call_start callId={} method={} purpose={} args={}",
                callId,
                methodName,
                purpose,
                summarize(args)
        );
        return new Scope(log, callId, methodName, purpose, startedAt, args);
    }

    public static final class Scope {
        private final Logger log;
        private final String callId;
        private final String methodName;
        private final String purpose;
        private final Instant startedAt;
        private final Map<String, Object> args;

        private Scope(Logger log, String callId, String methodName, String purpose, Instant startedAt, Map<String, Object> args) {
            this.log = log;
            this.callId = callId;
            this.methodName = methodName;
            this.purpose = purpose;
            this.startedAt = startedAt;
            this.args = args;
        }

        public void success(Object response) {
            long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.info(
                    "method_call_success callId={} method={} purpose={} elapsedMs={} args={} response={}",
                    callId,
                    methodName,
                    purpose,
                    elapsedMs,
                    summarize(args),
                    summarize(response)
            );
        }

        public void failure(Throwable ex) {
            long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.error(
                    "method_call_failure callId={} method={} purpose={} elapsedMs={} args={} error={}",
                    callId,
                    methodName,
                    purpose,
                    elapsedMs,
                    summarize(args),
                    ex.getMessage(),
                    ex
            );
        }
    }

    private static Map<String, Object> kv(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (keyValues == null) {
            return map;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private static String summarize(Object value) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value).replace('\n', ' ').replace('\r', ' ');
        if (text.length() > MAX_VALUE_LEN) {
            return text.substring(0, MAX_VALUE_LEN) + "...";
        }
        return text;
    }
}
