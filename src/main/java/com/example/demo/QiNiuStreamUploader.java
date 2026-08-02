package com.example.demo;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
        // 华东存储区域，华北/西南替换 Region.huabei() / Region.xinan()
        Configuration cfg = new Configuration(Region.huadong());
        uploadManager = new UploadManager(cfg);
    }

    /**
     * 文件输入流直接上传七牛对象存储
     * @param inputStream 远程下载文件流
     * @param cloudKey 云端存储路径+文件名
     * @return CDN可直接访问链接
     */
    public String uploadStream(InputStream inputStream, String cloudKey) throws Exception {
        Auth auth = Auth.create(accessKey, secretKey);
        String uploadToken = auth.uploadToken(bucketName);
        DefaultPutRet result = uploadManager.put(inputStream, cloudKey, uploadToken, null, null);
        return cdnDomain + "/" + result.key;
    }
}