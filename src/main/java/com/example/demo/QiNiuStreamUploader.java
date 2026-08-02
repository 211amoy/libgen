package com.example.demo;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;

@Component
public class QiNiuStreamUploader {

    @Value("${qiniu.access-key}")
    private String accessKey;

    @Value("${qiniu.secret-key}")
    private String secretKey;

    @Value("${qiniu.bucket-name}")
    private String bucketName;

    @Value("${qiniu.cdn-domain}")
    private String cdnDomain;

    private final UploadManager uploadManager;

    public QiNiuStreamUploader() {
        Configuration cfg = new Configuration(Region.huadong());
        uploadManager = new UploadManager(cfg);
    }

    // 标准上传方法
    public String upload(InputStream inputStream, String cloudKey) throws Exception {
        Auth auth = Auth.create(accessKey, secretKey);
        String upToken = auth.uploadToken(bucketName);

        Response response = uploadManager.put(inputStream, cloudKey, upToken, null, null);

        if (!response.isOK()) {
            throw new RuntimeException("七牛上传失败，状态码：" + response.statusCode + " 响应内容：" + response.bodyString());
        }

        DefaultPutRet ret = response.jsonToObject(DefaultPutRet.class);
        return cdnDomain + "/" + ret.key;
    }

    // 【关键】兼容你Service里调用的 uploadStream 方法，彻底解决找不到符号报错
    public String uploadStream(InputStream inputStream, String cloudKey) throws Exception {
        return upload(inputStream, cloudKey);
    }
}
