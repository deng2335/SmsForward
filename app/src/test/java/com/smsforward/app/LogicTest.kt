package com.smsforward.app

import com.smsforward.app.data.entity.ChannelEntity
import com.smsforward.app.data.entity.RuleEntity
import com.smsforward.app.data.entity.SmsEntity
import com.smsforward.app.data.model.Dedupe
import com.smsforward.app.data.model.IncomingSms
import com.smsforward.app.data.model.MatchType
import com.smsforward.app.data.entity.toEntity
import com.smsforward.app.forward.RuleMatcher
import com.smsforward.app.ui.guide.RomGuides
import com.smsforward.app.util.OtpParser
import com.smsforward.app.util.TextFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 逻辑测试：不依赖真机，覆盖验证码识别、规则匹配、去重与展示格式。 */
class LogicTest {

    // ---------------- 验证码识别 ----------------

    @Test
    fun `识别银行短信里的验证码`() {
        val body = "【招商银行】您正在登录，验证码 483920，5分钟内有效。"
        assertEquals("483920", OtpParser.extract(body))
    }

    @Test
    fun `识别冒号与空格分隔的验证码`() {
        assertEquals("837261", OtpParser.extract("【京东】您的验证码为：837261，请勿泄露。"))
    }

    @Test
    fun `识别带英文字样的验证码`() {
        assertEquals("102938", OtpParser.extract("Your verification code is 102938. Do not share it."))
    }

    @Test
    fun `纯数字短信取六位`() {
        assertEquals("654321", OtpParser.extract("654321"))
    }

    @Test
    fun `签名前缀加纯数字也采纳`() {
        assertEquals("654321", OtpParser.extract("【银行】 654321"))
    }

    @Test
    fun `全角数字也能识别`() {
        assertEquals("483920", OtpParser.extract("验证码４８３９２０"))
    }

    @Test
    fun `金额短信不误判为验证码`() {
        assertNull(OtpParser.extract("您尾号1234的储蓄卡消费人民币1,200.00元，余额8,456.21元。"))
    }

    @Test
    fun `银行转账金额不会被当成验证码`() {
        assertNull(OtpParser.extract("【工商银行】您尾号1234的储蓄卡转账支出500000元，余额200000元。"))
    }

    @Test
    fun `消费金额不会被当成验证码`() {
        assertNull(OtpParser.extract("本次消费金额 123456 元，请知悉。"))
    }

    @Test
    fun `六位数余额也不会误判`() {
        assertNull(OtpParser.extract("您账户余额 888888 元。"))
    }

    @Test
    fun `验证码在金额前面时只取验证码`() {
        assertEquals("123456", OtpParser.extract("验证码123456，本次消费1000元"))
    }

    @Test
    fun `验证码在金额后面时只取验证码`() {
        assertEquals("123456", OtpParser.extract("本次消费500元，验证码123456"))
    }

    @Test
    fun `尾号不会被当成验证码`() {
        assertEquals("1234", OtpParser.extract("【银行】验证码：1234，您尾号8888的卡正在支付。"))
    }

    @Test
    fun `订单号不会被当成验证码`() {
        assertNull(OtpParser.extract("订单号88888888，请勿泄露。"))
    }

    @Test
    fun `客服电话不会被当成验证码`() {
        assertNull(OtpParser.extract("如有疑问请拨打客服电话10086。"))
    }

    @Test
    fun `验证码放在数字后面的倒装句`() {
        assertEquals("123456", OtpParser.extract("123456 是您的验证码，请勿泄露"))
    }

    @Test
    fun `动态密码也能识别`() {
        assertEquals("882211", OtpParser.extract("您的动态密码为882211，请勿告诉他人。"))
    }

    @Test
    fun `四位验证码也识别`() {
        assertEquals("4821", OtpParser.extract("验证码 4821，5分钟内有效"))
    }

    @Test
    fun `八位验证码也识别`() {
        assertEquals("12345678", OtpParser.extract("您的验证码是12345678"))
    }

    @Test
    fun `链接里的数字不算验证码`() {
        assertNull(OtpParser.extract("点击 https://x.cn/123456 查看详情"))
    }

    @Test
    fun `打码手机号不算验证码`() {
        assertNull(OtpParser.extract("验证码已发送至138****8000"))
    }

    @Test
    fun `取件码不算验证码`() {
        assertNull(OtpParser.extract("您的快递已到，取件码 6-2-3041"))
    }

    @Test
    fun `普通聊天短信没有验证码`() {
        assertNull(OtpParser.extract("晚上七点老地方吃饭，别迟到。"))
    }

    // ---------------- 规则匹配 ----------------

    private fun sms(sender: String, body: String, code: String? = null) = SmsEntity(
        sender = sender,
        body = body,
        receivedAt = 1_700_000_000_000,
        code = code,
        dedupeKey = "k-$sender-$body",
    )

    private fun rule(matchType: Int, pattern: String = "") = RuleEntity(
        name = "测试规则",
        matchType = matchType,
        pattern = pattern,
        targetIds = "1",
    )

    @Test
    fun `全部短信规则命中任意短信`() {
        assertTrue(RuleMatcher.matches(rule(MatchType.ALL), sms("10086", "随便一条")))
    }

    @Test
    fun `关键词规则命中任意一个词`() {
        val r = rule(MatchType.KEYWORD, "验证码,登录")
        assertTrue(RuleMatcher.matches(r, sms("1069", "您的登录验证码是 123456")))
        assertFalse(RuleMatcher.matches(r, sms("1069", "您的话费余额不足")))
    }

    @Test
    fun `发件人规则忽略加86前缀和空格`() {
        val r = rule(MatchType.SENDER, "13800138000")
        assertTrue(RuleMatcher.matches(r, sms("+86 138 0013 8000", "在吗")))
        assertFalse(RuleMatcher.matches(r, sms("13900139000", "在吗")))
    }

    @Test
    fun `正则规则按正文匹配`() {
        val r = rule(MatchType.REGEX, "^【银行】")
        assertTrue(RuleMatcher.matches(r, sms("95588", "【银行】您有一笔转账")))
        assertFalse(RuleMatcher.matches(r, sms("95588", "【商城】优惠券到账")))
    }

    @Test
    fun `非法正则不会崩溃`() {
        assertFalse(RuleMatcher.matches(rule(MatchType.REGEX, "(["), sms("1", "测试")))
    }

    // ---------------- 去重 ----------------

    @Test
    fun `同一分钟内相同短信只入库一次`() {
        val t = 1_700_000_000_000
        assertEquals(Dedupe.key("95588", "内容", t), Dedupe.key("95588", "内容", t + 30_000))
    }

    @Test
    fun `跨分钟或内容不同则视为不同短信`() {
        val t = 1_700_000_000_000
        assertTrue(Dedupe.key("95588", "内容", t) != Dedupe.key("95588", "内容", t + 61_000))
        assertTrue(Dedupe.key("95588", "内容A", t) != Dedupe.key("95588", "内容B", t))
    }

    // ---------------- 入库映射 ----------------

    @Test
    fun `入库时自动提取验证码与去重键`() {
        val entity = IncomingSms(
            sender = "95588",
            body = "  【工商银行】验证码 778899  ",
            timestamp = 1_700_000_000_000,
        ).toEntity("broadcast")
        assertEquals("778899", entity.code)
        assertEquals("【工商银行】验证码 778899", entity.body)
        assertTrue(entity.dedupeKey.isNotBlank())
    }

    @Test
    fun `没有时间戳时使用当前时间`() {
        val entity = IncomingSms("10086", "测试", timestamp = 0L).toEntity("observer")
        assertTrue(entity.receivedAt > 0)
    }

    // ---------------- 展示格式 ----------------

    @Test
    fun `手机号分段展示`() {
        assertEquals("138 0013 8000", TextFormat.senderLabel("+8613800138000"))
        assertEquals("10086", TextFormat.senderLabel("10086"))
        assertEquals("未知号码", TextFormat.senderLabel(null))
    }

    @Test
    fun `头像首字取样`() {
        assertEquals("中", TextFormat.senderInitial("中国移动"))
        assertEquals("86", TextFormat.senderInitial("10086"))
    }

    // ---------------- 自启动指引 ----------------

    @Test
    fun `识别 realme 与 OPPO 机型`() {
        assertEquals("realme", RomGuides.detect("realme", "realme")?.key)
        assertEquals("realme", RomGuides.detect("OPPO", "OPPO")?.key)
    }

    @Test
    fun `识别小米红米机型`() {
        assertEquals("xiaomi", RomGuides.detect("Xiaomi", "Redmi")?.key)
        assertEquals("xiaomi", RomGuides.detect("Xiaomi", "POCO")?.key)
    }

    @Test
    fun `识别华为荣耀三星`() {
        assertEquals("huawei", RomGuides.detect("HUAWEI", "HUAWEI")?.key)
        assertEquals("honor", RomGuides.detect("HONOR", "HONOR")?.key)
        assertEquals("samsung", RomGuides.detect("samsung", "samsung")?.key)
    }

    @Test
    fun `未知或空厂商不会崩溃`() {
        assertNull(RomGuides.detect("SomeBrand", "X"))
        assertNull(RomGuides.detect(null, null))
    }

    @Test
    fun `每个品牌都有步骤和路径`() {
        RomGuides.all.forEach { guide ->
            assertTrue("${guide.key} 没有步骤", guide.steps.isNotEmpty())
            guide.steps.forEach { step ->
                assertTrue("${guide.key} 步骤没有路径", step.path.isNotEmpty())
                assertTrue("${guide.key} 步骤没有标题", step.title.isNotBlank())
            }
        }
    }
}
