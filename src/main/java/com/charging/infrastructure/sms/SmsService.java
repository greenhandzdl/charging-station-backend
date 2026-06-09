package com.charging.infrastructure.sms;

/**
 * 短信服务接口。
 * <p>
 * 当前为课程设计级别实现，验证码通过日志输出。
 * 生产环境可替换为真实短信通道（阿里云/腾讯云/七牛等）。
 */
public interface SmsService {

    /**
     * 发送短信验证码。
     *
     * @param phone 手机号
     * @param code  6位验证码
     */
    void sendVerificationCode(String phone, String code);

    /**
     * 验证短信验证码。
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @return true 如果验证码正确
     */
    boolean verifyCode(String phone, String code);

    /**
     * 获取验证码剩余有效时间（秒）。
     *
     * @param phone 手机号
     * @return 剩余秒数，0 表示不存在或已过期
     */
    long getCodeTtl(String phone);
}