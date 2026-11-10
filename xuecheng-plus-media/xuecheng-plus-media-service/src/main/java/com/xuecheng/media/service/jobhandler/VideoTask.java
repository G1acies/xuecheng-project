package com.xuecheng.media.service.jobhandler;

import com.xuecheng.base.model.utils.Mp4VideoUtil;
import com.xuecheng.media.mapper.MediaProcessMapper;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileProcessService;
import com.xuecheng.media.service.MediaFileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.minio.messages.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;

/**
 * XxlJob开发示例（Bean模式）
 * <p>
 * 开发步骤：
 * 1、任务开发：在Spring Bean实例中，开发Job方法；
 * 2、注解配置：为Job方法添加注解 "@XxlJob(value="自定义jobhandler名称", init = "JobHandler初始化方法", destroy = "JobHandler销毁方法")"，注解value值对应的是调度中心新建任务的JobHandler属性的值。
 * 3、执行日志：需要通过 "XxlJobHelper.log" 打印执行日志；
 * 4、任务结果：默认任务结果为 "成功" 状态，不需要主动设置；如有诉求，比如设置任务结果为失败，可以通过 "XxlJobHelper.handleFail/handleSuccess" 自主设置任务结果；
 *
 * @author xuxueli 2019-12-11 21:52:51
 */
@Slf4j
@Component
public class VideoTask {
    private static Logger logger = LoggerFactory.getLogger(VideoTask.class);

    @Value("${videoprocess.ffmpegpath}")
    String ffmpegpath;

    @Autowired
    MediaFileProcessService mediaFileProcessService;

    @Autowired
    MediaFileService mediaFileService;

    /**
     * 视频处理任务
     *
     * @throws Exception
     */
    @XxlJob("videoJobHandler")
    public void videoJobHandler() throws Exception {

        int shareIndex = XxlJobHelper.getShardIndex();
        int shareTotal = XxlJobHelper.getShardTotal();



        //确定cpu核心数
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        // 查询待处理任务
        List<MediaProcess> mediaProcessList = mediaFileProcessService.getMediaProcessList(shareIndex, shareTotal, availableProcessors);
        // 任务数量
        int size = mediaProcessList.size();

        CountDownLatch countDownLatch = new CountDownLatch(size);

        // 创建一个线程池
        ExecutorService executorService = Executors.newFixedThreadPool(size);
        mediaProcessList.forEach(mediaProcess -> {
            // 将任务加入线程池
            executorService.execute(() -> {
                try {
                    Long taskId = mediaProcess.getId();
                    String fileId = mediaProcess.getFileId();
                    // 开启任务
                    boolean b = mediaFileProcessService.startTask(taskId);
                    // 抢占失败
                    if (!b) {
                        log.debug("抢占任务失败，任务id{}", taskId);
                        return;
                    }
                    // 下载minio视频到本地
                    String processBucket = mediaProcess.getBucket();
                    String filePath = mediaProcess.getFilePath();
                    File file = mediaFileService.downloadFileFromMinIO(processBucket, filePath);
                    if (file == null) {
                        log.debug("下载失败，任务id{}，bucket{}，objectName{}", taskId, processBucket, filePath);
                        // 保存任务处理失败的结果
                        mediaFileProcessService.saveProcessFinishStatus(taskId, "3", fileId, null, "下载视频到本地失败");
                        return;
                    }
                    // 抢占成功,执行视频转码
                    //源avi视频的路径
                    String video_path = file.getAbsolutePath();
                    //转换后mp4文件的名称

                    String mp4_name = fileId + ".mp4";
                    //转换后mp4文件的路径
                    // 先创建一个临时文件
                    File tempDir = new File("D:/App/minio/temp");
                    File tempFile = null;
                    try {
                        tempFile = File.createTempFile("minio", ".mp4", tempDir);
                        log.debug("临时文件创建成功: {}", tempFile.getAbsolutePath());
                    } catch (IOException e) {
                        log.debug("创建临时文件异常，{}", e.getMessage());
                        mediaFileProcessService.saveProcessFinishStatus(taskId, "3", fileId, null, "创建临时文件失败");
                        return;
                    }
                    String mp4_path = tempFile.getAbsolutePath();
                    //创建工具类对象
                    Mp4VideoUtil videoUtil = new Mp4VideoUtil(ffmpegpath, video_path, mp4_name, mp4_path);
                    //开始视频转换，成功将返回success
                    String result = videoUtil.generateMp4();
                    if (!result.equals("success")) {
                        log.debug("视频转码失败id{}，bucket{}，objectName{}", taskId, processBucket, filePath);
                        mediaFileProcessService.saveProcessFinishStatus(taskId, "2", fileId, null, result);
                        return;
                    }
                    // 上传到minio
                    boolean b1 = mediaFileService.addMediaFilesToMinIO(mp4_path, "video/mp4", processBucket, mediaProcess.getFilePath());
                    if (!b1) {
                        log.debug("上传到minio失败，taskId:{}，bucket:{}，objectName:{}", taskId, processBucket, filePath);
                        mediaFileProcessService.saveProcessFinishStatus(taskId, "2", fileId, null, "创建临时文件失败");
                        return;
                    }

                    //mp4文件的url
                    String url = getFilePathByMd5(fileId, "mp4");


                    // 保存任务处理结果
                    mediaFileProcessService.saveProcessFinishStatus(taskId, "2", fileId, url, "任务状态更新失败");
                } finally {
                    countDownLatch.countDown();
                }
            });
        });

        countDownLatch.await(30, TimeUnit.MINUTES);
    }

    private String getFilePathByMd5(String fileMd5, String fileExt) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/" + fileMd5 + fileExt;
    }

}
