package com.xuecheng.media;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class MinioTest {

    MinioClient minioClient =
            MinioClient.builder()
                    .endpoint(System.getenv().getOrDefault("MINIO_ENDPOINT", "http://127.0.0.1:9000"))
                    .credentials(System.getenv().getOrDefault("MINIO_ACCESS_KEY", "minioadmin"),
                                    System.getenv().getOrDefault("MINIO_SECRET_KEY", "minioadmin"))
                    .build();

    @Test
    public void test_upload() throws Exception {
        UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
                .bucket("testbucket")
                .filename("D:\\App\\minio\\Chi09.mkv")
                .object("Chi09.mkv")
                .build();

        minioClient.uploadObject(uploadObjectArgs);

    }

    @Test
    public void test_upload2() throws Exception {
        UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
                .bucket("testbucket")
                .filename("D:\\App\\minio\\1.jpg")
                .object("1.jpg")
                .build();

        minioClient.uploadObject(uploadObjectArgs);

    }

    @Test
    public void test_delete() throws Exception {
        RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                .bucket("testbucket")
                .object("1.jpg")
                .build();

        minioClient.removeObject(removeObjectArgs);

    }

    //查询文件
    @Test
    public void getFile() throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        GetObjectArgs getObjectArgs = GetObjectArgs.builder().bucket("testbucket").object("1.jpg").build();

        FilterInputStream inputStream = minioClient.getObject(getObjectArgs);
        FileOutputStream outputStream = new FileOutputStream(new File("D:\\App\\minio\\1a.jpg"));

        IOUtils.copy(inputStream, outputStream);

        //校验文件完整性 hash
        String mded5Hex = DigestUtils.md5Hex(inputStream);
        String mded5Hex1 = DigestUtils.md5Hex(new FileInputStream(new File("D:\\App\\minio\\1a.jpg")));

        if(mded5Hex.equals(mded5Hex1)) {
            System.out.println("download success");
        }
    }

}
