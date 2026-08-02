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
        Configuration cfg = new Configuration(Region.huabei());
        uploadManager = new UploadManager(cfg);
    }

    public String upload(InputStream inputStream, String cloudKey) throws Exception {
        Auth auth = Auth.create(accessKey, secretKey);
        String upToken = auth.uploadToken(bucketName);

        // 1. 先接收 Response 对象（这一行对应报错45行）
        Response response = uploadManager.put(inputStream, cloudKey, upToken, null, null);

        // 校验上传是否成功
        if (!response.isOK()) {
            throw new RuntimeException("七牛上传失败，状态码：" + response.statusCode + " 详情：" + response.bodyString());
        }

        // 2. JSON反序列化为返回实体
        DefaultPutRet ret = response.jsonToObject(DefaultPutRet.class);

        return cdnDomain + "/" + ret.key;
    }
}
