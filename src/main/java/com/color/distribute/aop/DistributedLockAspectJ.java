package com.color.distribute.aop;

import com.color.service.base.ColorException;
import com.color.distribute.annotation.DistributedLock;
import com.color.distribute.lock.IDistributedLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁
 *
 * @author yue.zhang
 * @create 2018-09-28 14:31
 **/
@Aspect
public class DistributedLockAspectJ {

    @Autowired
    private IDistributedLock distributedLock;

    LocalVariableTableParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

    @Pointcut("@annotation(com.color.distribute.annotation.DistributedLock) && execution(* *(..))")
    private void pointCut() {
    }

    @Around("@annotation(distributedLock)")
    public Object distributedLock(ProceedingJoinPoint pjp, DistributedLock distributedLock) throws Throwable {

        String[] keys = distributedLock.key().split(",");
        String[] values = distributedLock.value().split(",");
        if (keys.length != values.length) {
            throw new ColorException(500, "distributor lock error, key and value mismatch.");
        }

        String[] parameterNames = parameterNameDiscoverer.getParameterNames(((MethodSignature)pjp.getSignature()).getMethod());
        Object[] args = pjp.getArgs();

        EvaluationContext context = getEvaluationContext(parameterNames, args);
        ExpressionParser parser = new SpelExpressionParser();
        String lockKey = getLockKey(context, parser, keys, values);

        String requestId = null;
        Object result = null;
        try {
            requestId = lock(lockKey, distributedLock);
            result = pjp.proceed(args);
        } finally {
            unLock(lockKey, requestId);
        }
        return result;
    }

    private String getLockKey(EvaluationContext context, ExpressionParser parser, String[] keys, String[] values) {
        StringBuilder keySb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            String arg = parser.parseExpression(values[i]).getValue(context, String.class);
            keySb.append(keys[i] + "_" + arg);
            if (i < keys.length - 1) {
                keySb.append("_");
            }
        }
        return keySb.toString();
    }

    private EvaluationContext getEvaluationContext(String[] parameterNames, Object[] args) {
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        return context;
    }

    private boolean unLock(String lockKey, String requestId) {
        return distributedLock.unLock(format(lockKey), requestId);
    }

    private String lock(String lockKey, String requestId) {
        return distributedLock.tryLock(format(lockKey), 30000);
    }

    private String format(String lockKey) {
        return MessageFormat.format("distributedLock:{0}", lockKey);
    }

    private String lock(String lockKey, DistributedLock anno) throws Exception {
        String requestId = null;
        boolean isLockSuccess = false;
        //加锁value
        if (anno.isRetry()) {
            Long expectTime = System.currentTimeMillis() + anno.waitTime();
            while (System.currentTimeMillis() < expectTime) {
                try {
                    TimeUnit.MILLISECONDS.sleep(5L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                requestId = lock(lockKey, requestId);
                if (null != requestId) {
                    isLockSuccess = true;
                    break;
                }
            }
        } else {
            requestId = lock(lockKey, requestId);
            if (null != requestId) {
                isLockSuccess = true;
            }
        }

        if (!isLockSuccess) {
            throw new ColorException(500, "get distribute lock failed ", format(lockKey));
        }

        return requestId;
    }
}
