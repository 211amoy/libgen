package com.example.demo;
import java.io.InputStream;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class LibGenComputerBookService {

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private QiNiuStreamUploader qiNiuUploader;

    // 检索中文关键词
    private static final String SEARCH_KEYWORD = "计算机";
    // Consumet第三方中转LibGen API（国内直连稳定，无代理可用）
    private static final String API_BASE_URL = "https://api.consumet.org/books/libgen/s?bookTitle=";
    private static final String API_FULL_URL = API_BASE_URL + SEARCH_KEYWORD;
    // 截取前50本
    private static final int TOP_COUNT = 50;
    // 七牛云端归档目录
    private static final String QINIU_CLOUD_PREFIX = "libgen/computer/latest_top50/";
    // 随机数用于波动休眠
    private static final Random RANDOM = new Random();

    // 完整浏览器指纹请求头，避免WAF判定为爬虫
    private final Headers FULL_BROWSER_HEADERS = new Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
            .add("Accept", "application/json, text/plain, */*")
            .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .add("Referer", "https://consumet.org/")
            .add("sec-ch-ua", "\"Chromium\";v=\"129\", \"Not=A?Brand\";v=\"24\"")
            .add("sec-ch-ua-mobile", "?0")
            .add("sec-ch-ua-platform", "\"Windows\"")
            .build();

    /**
     * 第一步：调用API查询 → 年份倒序最新排序 → 截取前50 → 过滤pdf/epub有效书籍
     */
    public List<LibGenBookItem> searchLatestTop50ComputerBooks() throws Exception {
        Request request = new Request.Builder()
                .url(API_FULL_URL)
                .headers(FULL_BROWSER_HEADERS)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("第三方API请求失败，状态码：" + response.code());
            }
            String jsonBody = response.body().string();
            LibGenApiResp apiResp = JSON.parseObject(jsonBody, LibGenApiResp.class);

            List<LibGenBookItem> rawList = apiResp.getResults();
            if (rawList == null || rawList.isEmpty()) {
                return List.of();
            }

            // 1. 按出版年份倒序（最新书籍排在最前）
            // 年份非数字放末尾兜底
            List<LibGenBookItem> sortedList = rawList.stream()
                    .sorted((o1, o2) -> {
                        String y1 = o1.getYear() == null ? "0" : o1.getYear();
                        String y2 = o2.getYear() == null ? "0" : o2.getYear();
                        return y2.compareTo(y1);
                    })
                    // 2. 截断前50条
                    .limit(TOP_COUNT)
                    // 3. 过滤有效下载链接 + 仅保留pdf/epub格式
                    .filter(item -> StrUtil.isNotBlank(item.getDownloadLink()))
                    .filter(item -> {
                        String ext = item.getExtension();
                        return "pdf".equalsIgnoreCase(ext) || "epub".equalsIgnoreCase(ext);
                    })
                    .collect(Collectors.toList());

            return sortedList;
        }
    }

    /**
     * 第二步：单本书远程拉取文件流，直接流式上传七牛云（不存本地）
     */
    public String uploadSingleBookToQiNiu(LibGenBookItem book) throws Exception {
        String downUrl = book.getDownloadLink();
        if (StrUtil.isBlank(downUrl)) {
            return "❌ 无有效下载直链";
        }

        // 文件名清洗，剔除OSS非法字符 \ / : * ? " < > |
        String safeFileName = ReUtil.replaceAll(book.getTitle(), "[\\\\/:*?\"<>|]", "_");
        String cloudSaveKey = QINIU_CLOUD_PREFIX + safeFileName + "." + book.getExtension();

        Request downloadRequest = new Request.Builder()
                .url(downUrl)
                .headers(FULL_BROWSER_HEADERS)
                .build();

        try (Response resp = okHttpClient.newCall(downloadRequest).execute()) {
            if (!resp.isSuccessful()) {
                return "❌ 文件下载失败 HTTP状态码：" + resp.code();
            }
            // 流直接上传，零本地落地
            InputStream fileStream = resp.body().byteStream();
            String cdnAccessUrl = qiNiuUploader.upload(fileStream, cloudSaveKey);
            return "✅ 上传成功：" + cdnAccessUrl;
        }
    }

    /**
     * 第三步：一键批量上传前50本全部到七牛（无代理防封禁：8s固定+1~3s随机休眠）
     */
    public String batchUploadAllTop50() throws Exception {
        List<LibGenBookItem> bookList = searchLatestTop50ComputerBooks();
        if (bookList.isEmpty()) {
            return "未获取到计算机类LibGen书籍，API直连超时，请切换手机热点重试";
        }

        int successCount = 0;
        int failCount = 0;
        StringBuilder logContent = new StringBuilder();

        for (LibGenBookItem book : bookList) {
            // 强制风控休眠：固定8秒 + 随机1~3秒波动，彻底避免IP短时间高频触发WAF 403
            TimeUnit.SECONDS.sleep(8);
            TimeUnit.MILLISECONDS.sleep(RANDOM.nextInt(3000) + 1000);

            try {
                String uploadResult = uploadSingleBookToQiNiu(book);
                logContent.append("【").append(book.getTitle()).append("】").append(uploadResult).append("\n");
                if (uploadResult.startsWith("✅")) successCount++;
                else failCount++;
            } catch (Exception e) {
                failCount++;
                logContent.append("【").append(book.getTitle()).append("】异常：").append(e.getMessage()).append("\n");
            }
        }

        return String.format(
                "======== LibGen 计算机类最新Top50批量上传结果 ========\n" +
                "筛选后可处理总数：%d 本\n成功上传：%d 本 | 失败：%d 本\n\n详细执行日志：\n%s",
                bookList.size(), successCount, failCount, logContent
        );
    }
}
