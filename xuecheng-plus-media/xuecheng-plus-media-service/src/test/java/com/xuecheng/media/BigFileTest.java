package com.xuecheng.media;

import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BigFileTest {

    MinioClient minioClient =
            MinioClient.builder()
                    .endpoint(System.getenv().getOrDefault("MINIO_ENDPOINT", "http://127.0.0.1:9000"))
                    .credentials(System.getenv().getOrDefault("MINIO_ACCESS_KEY", "minioadmin"),
                                    System.getenv().getOrDefault("MINIO_SECRET_KEY", "minioadmin"))
                    .build();

    @Test
    public void testChunk() throws IOException {
        File sourceFile = new File("D:\\App\\minio\\Chi09.mkv");
        String chunkFilePath = "D:\\App\\minio\\chunk\\";
        int chunkSize = 1024 * 1024 * 10;
        int chunkNum = (int) Math.ceil((double) sourceFile.length() / chunkSize);

        // 只读模式打开源文件
        RandomAccessFile raf_r = new RandomAccessFile(sourceFile, "r");

        byte[] bytes = new byte[1024];

        for (int i = 0; i < chunkNum; i++) {
            File chunkFile = new File(chunkFilePath + i);

            // 打开一个读写模式流，指向当前分块文件
            RandomAccessFile raf_rw = new RandomAccessFile(chunkFile, "rw");
            int len = -1;
            while ((len = raf_r.read(bytes)) != -1) {       // 读取数据到缓冲区
                raf_rw.write(bytes, 0, len);            // 从缓冲区写入到raf_rw指定的分块文件

                // 检查当前分块是否达到指定大小
                if (chunkFile.length() >= chunkSize) {
                    break;
                }
            }
            raf_rw.close();
        }
        raf_r.close();
    }

    @Test
    public void testMerge() throws IOException {
        File chunkFolder = new File("D:\\App\\minio\\chunk\\");
        File sourceFile = new File("D:\\App\\minio\\Chi09.mkv");
        File mergeFile = new File("D:\\App\\minio\\Chi09.mkv_1");

        File[] files = chunkFolder.listFiles();
        List<File> fileList = Arrays.asList(files);

        Collections.sort(fileList, new Comparator<File>() {

            @Override
            public int compare(File o1, File o2) {
                return Integer.parseInt(o1.getName()) - Integer.parseInt(o2.getName());
            }
        });
        byte[] bytes = new byte[1024];
        RandomAccessFile raf_rw = new RandomAccessFile(mergeFile, "rw");
        for (File file : fileList) {
            RandomAccessFile raf_r = new RandomAccessFile(file, "r");
            int len = -1;
            while ((len = raf_r.read(bytes)) != -1) {
                raf_rw.write(bytes, 0, len);
            }
            raf_r.close();

        }
        raf_rw.close();

        //校验文件
        try (FileInputStream fileInputStream = new FileInputStream(sourceFile);
             FileInputStream mergeFileStream = new FileInputStream(mergeFile)
        ) {
            //取出原始文件的md5
            String originalMd5 = DigestUtils.md5Hex(fileInputStream);
            //取出合并文件的md5进行比较
            String mergeFileMd5 = DigestUtils.md5Hex(mergeFileStream);
            if (originalMd5.equals(mergeFileMd5)) {
                System.out.println("合并文件成功");
            }
            else {
                System.out.println("合并文件失败");
            }

        }
    }

    //将分块文件上传至minio
    @Test
    public void uploadChunk() {
        String chunkFolderPath = "D:\\App\\minio\\chunk\\";
        File chunkFolder = new File(chunkFolderPath);
        //分块文件
        File[] files = chunkFolder.listFiles();
        //将分块文件上传至minio
        for (int i = 0; i < files.length; i++) {
            try {
                UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
                        .bucket("testbucket")
                        .filename(files[i].getAbsolutePath())
                        .object("chunk/" + i)
                        .build();
                minioClient.uploadObject(uploadObjectArgs);
                System.out.println("上传分块成功" + i);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    //合并文件，要求分块文件最小5M
    @Test
    public void test_merge() throws Exception {

        List<ComposeSource> sources = Stream.iterate(0, i -> ++i)
                .limit(17)
                .map(i -> ComposeSource.builder()
                        .bucket("testbucket")
                        .object("chunk/".concat(Integer.toString(i)))
                        .build())
                .collect(Collectors.toList());

        ComposeObjectArgs composeObjectArgs = ComposeObjectArgs.builder()
                .bucket("testbucket")
                .object("merge01.mkv")
                .sources(sources)
                .build();
        minioClient.composeObject(composeObjectArgs);
    }
}
