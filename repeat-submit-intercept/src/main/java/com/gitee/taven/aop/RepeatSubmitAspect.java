package com.gitee.taven.aop;

import com.gitee.taven.ApiResult;
import com.gitee.taven.utils.RedisLock;
import com.gitee.taven.utils.RequestUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

@Aspect
@Component
public class RepeatSubmitAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepeatSubmitAspect.class);

    @Autowired
    private RedisLock redisLock;

    @Pointcut("@annotation(com.gitee.taven.aop.NoRepeatSubmit)")
    public void pointCut() {}

    @Around("pointCut()")
    public Object before(ProceedingJoinPoint pjp) {
        try {
            HttpServletRequest request = RequestUtils.getRequest();
            Assert.notNull(request, "request can not null");

            // 此处可以用token或者JSessionId
            String token = request.getHeader("Authorization");
            String path = request.getServletPath();
            String key = getKey(token, path);
            String clientId = getClientId();

            boolean isSuccess = redisLock.tryLock(key, clientId, 10);
            LOGGER.info("tryLock key = [{}], clientId = [{}]", key, clientId);

            if (isSuccess) {
                LOGGER.info("tryLock success, key = [{}], clientId = [{}]", key, clientId);
                // 获取锁成功, 执行进程
                Object result = pjp.proceed();
                // 解锁
                redisLock.releaseLock(key, clientId);
                LOGGER.info("releaseLock success, key = [{}], clientId = [{}]", key, clientId);
                return result;

            } else {
                // 获取锁失败，认为是重复提交的请求
                LOGGER.info("tryLock fail, key = [{}]", key);
                return new ApiResult(200, "重复请求，请稍后再试", null);
            }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return new ApiResult(500, "系统异常", null);
    }

    private String getKey(String token, String path) {
        return token + path;
    }

    private String getClientId() {
        return UUID.randomUUID().toString();
    }

}