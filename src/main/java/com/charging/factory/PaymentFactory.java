package com.charging.factory;

import com.charging.channel.PaymentChannel;
import com.charging.channel.WeChatPayChannel;
import com.charging.channel.AliPayChannel;
import com.charging.channel.SystemAutoDeductChannel;
import org.springframework.stereotype.Component;

@Component
public class PaymentFactory {

    private final WeChatPayChannel weChatPayChannel;
    private final AliPayChannel aliPayChannel;
    private final SystemAutoDeductChannel systemAutoDeductChannel;

    public PaymentFactory(WeChatPayChannel weChatPayChannel,
                          AliPayChannel aliPayChannel,
                          SystemAutoDeductChannel systemAutoDeductChannel) {
        this.weChatPayChannel = weChatPayChannel;
        this.aliPayChannel = aliPayChannel;
        this.systemAutoDeductChannel = systemAutoDeductChannel;
    }

    /**
     * 根据 method 参数返回对应支付通道
     * "wechat" -> WeChatPayChannel
     * "alipay" -> AliPayChannel
     * "system" -> SystemAutoDeductChannel
     * 默认 -> WeChatPayChannel
     */
    public PaymentChannel createChannel(String method) {
        if (method == null) {
            return weChatPayChannel;
        }
        return switch (method.toLowerCase()) {
            case "wechat" -> weChatPayChannel;
            case "alipay" -> aliPayChannel;
            case "system", "auto_deduct" -> systemAutoDeductChannel;
            default -> weChatPayChannel;
        };
    }
}