package com.example.demo;

import java.util.List;

import lombok.Data;

@Data
public class LibGenApiResp {
    private List<LibGenBookItem> results;
}