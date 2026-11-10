package com.xuecheng.learning;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.learning.feignclient.ContentServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 * @date 2023/2/22 20:14
 */
@SpringBootTest
@Slf4j
public class FeignClientTest {

    @Autowired
    ContentServiceClient contentServiceClient;


    @Test
    public void testContentServiceClient() {
        CoursePublish coursepublish = contentServiceClient.getCoursepublish(24L);
        Assertions.assertNotNull(coursepublish);
    }

    @Test
    public void testContentServiceClient2() {
        log.info("开始测试 Feign 客户端调用...");

        // 测试多个可能的课程ID
        Long[] testCourseIds = {24L, 120L, 121L, 1L};

        for (Long courseId : testCourseIds) {
            log.info("尝试获取课程ID: {}", courseId);

            try {
                CoursePublish coursepublish = contentServiceClient.getCoursepublish(courseId);

                if (coursepublish != null) {
                    log.info("✅ 成功获取课程: ID={}, 名称={}", courseId, coursepublish.getName());
                    Assertions.assertNotNull(coursepublish);
                    return; // 成功则结束测试
                } else {
                    log.warn("课程ID {} 返回 null", courseId);
                }
            } catch (Exception e) {
                log.error("课程ID {} 调用异常: {}", courseId, e.getMessage());
            }
        }

        // 如果所有尝试都失败
        Assertions.fail("无法获取任何课程信息，请检查：\n" +
                "1. content-api 服务是否正常运行\n" +
                "2. Feign 客户端路径配置是否正确\n" +
                "3. 测试的课程ID是否存在");
    }
}