package com.enterprise.booking.observability;

import org.slf4j.Logger;
import org.slf4j.MDC;

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
        String existingCallId = MDC.get("callId");
        boolean createdHere = false;
        String callId = existingCallId;
        if (callId == null || callId.isBlank()) {
            callId = UUID.randomUUID().toString();
            MDC.put("callId", callId);
            createdHere = true;
        }
        Instant startedAt = Instant.now();
        Map<String, Object> args = kv(keyValues);
        log.info(
                "method_call_start callId={} method={} purpose={} args={}",
                callId,
                methodName,
                purpose,
                summarize(args)
        );
        return new Scope(log, callId, methodName, purpose, startedAt, args, createdHere);
    }

    public static final class Scope {
        private final Logger log;
        private final String callId;
        private final String methodName;
        private final String purpose;
        private final Instant startedAt;
        private final Map<String, Object> args;
        private final boolean createdHere;

        private Scope(Logger log, String callId, String methodName, String purpose, Instant startedAt, Map<String, Object> args, boolean createdHere) {
            this.log = log;
            this.callId = callId;
            this.methodName = methodName;
            this.purpose = purpose;
            this.startedAt = startedAt;
            this.args = args;
            this.createdHere = createdHere;
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
            cleanupIfNeeded();
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
            cleanupIfNeeded();
        }

        private void cleanupIfNeeded() {
            if (createdHere) {
                MDC.remove("callId");
            }
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
