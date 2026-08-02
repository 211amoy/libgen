package com.example.demo;
import lombok.Data;

@Data
public class LibGenBookItem {
    private String id;
    private String title;         // 书名（中文“计算机”可匹配）
    private String author;        // 作者
    private String publisher;     // 出版社
    private String year;          // 出版年份（用于最新排序）
    private String language;      // 语言
    private String extension;     // 文件后缀 pdf/epub/mobi
    private String size;          // 文件大小
    private String downloadLink;  // 直接下载地址
}