package com.enterprise.booking.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Aspect
@Component
@ConditionalOnProperty(
        prefix = "observability.class-logging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ClassLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ClassLoggingAspect.class);

    @Value("${observability.class-logging.include-args:false}")
    private boolean includeArgs;

    @Value("${observability.class-logging.include-result:true}")
    private boolean includeResult;

    @Value("${observability.class-logging.max-payload-length:300}")
    private int maxPayloadLength;

    @Value("${observability.class-logging.slow-method-threshold-ms:1500}")
    private long slowMethodThresholdMs;

    @Pointcut("within(com.enterprise.booking..*) && !within(com.enterprise.booking.observability..*)")
    public void appClasses() {
        // pointcut marker
    }

    @Around("appClasses()")
    public Object logMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = className + "." + signature.getName();
        long started = System.nanoTime();

        if (includeArgs) {
            log.info("enter method={} args=[{}]", methodName, summarizeArgs(joinPoint.getArgs()));
        } else {
            log.info("enter method={}", methodName);
        }

        try {
            Object result = joinPoint.proceed();
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            if (elapsedMs >= slowMethodThresholdMs) {
                log.warn("slow method={} elapsedMs={}", methodName, elapsedMs);
            }
            if (!includeResult) {
                log.info("exit method={} elapsedMs={}", methodName, elapsedMs);
            } else if (result == null) {
                log.info("exit method={} elapsedMs={} result=null", methodName, elapsedMs);
            } else {
                log.info(
                        "exit method={} elapsedMs={} resultType={} result={}",
                        methodName,
                        elapsedMs,
                        result.getClass().getSimpleName(),
                        summarizeArg(result)
                );
            }
            return result;
        } catch (Throwable ex) {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            if (elapsedMs >= slowMethodThresholdMs) {
                log.warn("slow-failed method={} elapsedMs={}", methodName, elapsedMs);
            }
            log.error("error method={} elapsedMs={} message={}", methodName, elapsedMs, ex.getMessage(), ex);
            throw ex;
        }
    }

    private String summarizeArgs(Object[] args) {
        return Arrays.stream(args)
                .map(this::summarizeArg)
                .collect(Collectors.joining(", "));
    }

    private String summarizeArg(Object value) {
        if (value == null) {
            return "null";
        }
        String type = value.getClass().getSimpleName();
        String text = Objects.toString(value);
        if (text.length() > maxPayloadLength) {
            text = text.substring(0, maxPayloadLength) + "...";
        }
        return type + "(" + text + ")";
    }
}
