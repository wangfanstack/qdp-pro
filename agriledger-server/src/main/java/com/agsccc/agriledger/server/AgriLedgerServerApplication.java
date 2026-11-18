package com.agsccc.agriledger.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目的启动类
 *
 * @author 农E链源码
 */
@SpringBootApplication(scanBasePackages = {"${agriledger.info.base-package}.server", "${agriledger.info.base-package}.module"})
public class AgriLedgerServerApplication {

    public static void main(String[] args) {

        SpringApplication.run(AgriLedgerServerApplication.class, args);
    }

}
