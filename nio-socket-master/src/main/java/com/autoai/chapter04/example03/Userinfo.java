package com.autoai.chapter04.example03;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * @Author: zhukaishengy
 * @Date: 2020/6/19 17:28
 * @Description:
 */

@Data
@Builder
public class Userinfo implements Serializable {

    private String id;
    private String name;
    private Integer age;
}

