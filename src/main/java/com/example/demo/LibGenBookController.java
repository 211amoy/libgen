package com.example.demo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LibGenBookController {

    @Autowired
    private LibGenComputerBookService libGenBookService;

    /**
     * 仅查询：计算机最新前50本书籍列表（不上传）
     * 地址：http://localhost:8080/libgen/computer/top50/list
     */
    @GetMapping("/libgen/computer/top50/list")
    public List<LibGenBookItem> getTop50BookList() throws Exception {
        return libGenBookService.searchLatestTop50ComputerBooks();
    }

    /**
     * 一键批量下载并流式上传七牛云
     * 地址：http://localhost:8080/libgen/computer/top50/batch-qiniu
     */
    @GetMapping("/libgen/computer/top50/batch-qiniu")
    public String batchUploadToQiNiu() throws Exception {
        return libGenBookService.batchUploadAllTop50();
    }
}