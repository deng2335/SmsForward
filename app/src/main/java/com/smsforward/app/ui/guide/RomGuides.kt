package com.smsforward.app.ui.guide

import androidx.compose.ui.graphics.Color
import com.smsforward.app.ui.theme.BrandBlue
import com.smsforward.app.ui.theme.BrandRose
import com.smsforward.app.ui.theme.BrandTeal
import com.smsforward.app.ui.theme.BrandViolet

/** 步骤按钮要跳到哪里 */
enum class GuideAction {
    AUTO_START,     // 系统自启动/启动管理
    BATTERY,        // 电池优化
    NOTIFICATION,   // 通知设置
    APP_DETAILS,    // 应用详情（耗电管理、速冻等入口在里面）
    MULTI_TASK,     // 没有系统页面可跳，只提示操作
}

data class RomStep(
    val title: String,
    val path: List<String>,
    val desc: String,
    val action: GuideAction,
)

data class RomGuide(
    val key: String,
    val name: String,
    val systems: String,
    val badge: String,
    val color: Color,
    /** 用于匹配 Build.MANUFACTURER / Build.BRAND 的小写关键字 */
    val keywords: List<String>,
    val note: String? = null,
    val steps: List<RomStep>,
) {
    fun oneLine(step: RomStep): String = step.path.joinToString(" › ")
}

/**
 * 国产 ROM 保活路径库。路径来自各厂商支持页与官方社区，随系统版本可能变化，
 * 所以每一步都带"去设置"按钮 + 复制路径，失败会回退到应用详情页。
 */
object RomGuides {

    val all: List<RomGuide> = listOf(
        RomGuide(
            key = "realme",
            name = "realme / OPPO",
            systems = "realme UI / ColorOS",
            badge = "rm",
            color = Color(0xFFF5B800),
            keywords = listOf("realme", "oppo", "oneplus", "oplus"),
            note = "realme UI 1.0 / 2.0 的自启动入口在「设置 › 应用管理 › 自启动管理」，其余步骤相同。",
            steps = listOf(
                RomStep(
                    title = "允许自启动",
                    path = listOf("设置", "应用", "自启动"),
                    desc = "realme UI 3.0+ 的路径；列表里找到「短信转发」并打开开关。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "允许完全后台行为",
                    path = listOf("设置", "电池", "应用耗电管理", "短信转发"),
                    desc = "打开「允许自启动」「允许关联启动」「允许完全后台行为」三个开关，这一步最关键。",
                    action = GuideAction.APP_DETAILS,
                ),
                RomStep(
                    title = "关闭应用速冻",
                    path = listOf("手机管家", "应用速冻"),
                    desc = "确认「短信转发」没有被勾选冻结，已冻结就取消。",
                    action = GuideAction.APP_DETAILS,
                ),
                RomStep(
                    title = "电池优化设为不优化",
                    path = listOf("设置", "电池", "电池优化", "短信转发"),
                    desc = "选择「不优化」，否则 Doze 会延迟后台网络，推送要等到下次唤醒。",
                    action = GuideAction.BATTERY,
                ),
                RomStep(
                    title = "锁住最近任务",
                    path = listOf("多任务界面", "下拉卡片"),
                    desc = "下拉「短信转发」卡片出现小锁，一键清理时不会被杀掉。",
                    action = GuideAction.MULTI_TASK,
                ),
            ),
        ),
        RomGuide(
            key = "xiaomi",
            name = "小米 / Redmi",
            systems = "HyperOS / MIUI",
            badge = "MI",
            color = Color(0xFFFF6900),
            keywords = listOf("xiaomi", "redmi", "poco"),
            note = "HyperOS 把「省电策略」放在应用详情里，必须选「无限制」，否则锁屏后会被限制后台。",
            steps = listOf(
                RomStep(
                    title = "允许自启动",
                    path = listOf("设置", "应用设置", "授权管理", "自启动管理"),
                    desc = "找到「短信转发」并打开开关。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "关闭关联启动",
                    path = listOf("设置", "应用设置", "授权管理", "自启动管理", "右上角设置"),
                    desc = "建议关闭「允许应用关联启动」，减少被别的 App 链式唤醒后又被系统杀掉。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "省电策略设为无限制",
                    path = listOf("设置", "应用设置", "应用管理", "短信转发", "省电策略"),
                    desc = "选择「无限制」，小米机型这一步不做，锁屏后基本收不到推送。",
                    action = GuideAction.APP_DETAILS,
                ),
                RomStep(
                    title = "锁住最近任务",
                    path = listOf("多任务界面", "长按卡片"),
                    desc = "长按卡片选「锁定」，或下拉卡片加锁。",
                    action = GuideAction.MULTI_TASK,
                ),
            ),
        ),
        RomGuide(
            key = "vivo",
            name = "vivo / iQOO",
            systems = "OriginOS / Funtouch",
            badge = "vi",
            color = Color(0xFF415FFF),
            keywords = listOf("vivo", "iqoo"),
            note = "vivo 的「后台高耗电」开关藏得比较深，在电池耗电排行页右上角。",
            steps = listOf(
                RomStep(
                    title = "允许自启动",
                    path = listOf("设置", "应用与权限", "权限管理", "自启动管理"),
                    desc = "打开「短信转发」的自启动开关。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "允许后台高耗电",
                    path = listOf("设置", "电池", "耗电排行", "右上角「后台高耗电」"),
                    desc = "找到「短信转发」，允许后台高耗电运行。",
                    action = GuideAction.BATTERY,
                ),
                RomStep(
                    title = "i管家放行",
                    path = listOf("i管家", "应用管理", "自启动"),
                    desc = "部分机型这里才是真正的开关，两个入口都确认一遍。",
                    action = GuideAction.APP_DETAILS,
                ),
                RomStep(
                    title = "锁住最近任务",
                    path = listOf("多任务界面", "下拉卡片"),
                    desc = "下拉卡片加锁。",
                    action = GuideAction.MULTI_TASK,
                ),
            ),
        ),
        RomGuide(
            key = "huawei",
            name = "华为",
            systems = "HarmonyOS / EMUI",
            badge = "HW",
            color = Color(0xFFC7000B),
            keywords = listOf("huawei"),
            note = "HarmonyOS 5 及以上取消了独立「手机管家」，入口统一到「设置 › 应用和服务 › 应用启动管理」；找不到就在设置顶部搜索「应用启动管理」。",
            steps = listOf(
                RomStep(
                    title = "改为手动管理",
                    path = listOf("设置", "应用和服务", "应用启动管理", "短信转发"),
                    desc = "关闭「自动管理」，会弹出三个开关。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "三个开关全开",
                    path = listOf("启动管理", "手动管理"),
                    desc = "允许自启动、允许关联启动、允许后台活动，全部打开。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "休眠保持网络",
                    path = listOf("设置", "电池", "更多电池设置", "休眠时始终保持网络连接"),
                    desc = "可选但建议开启，否则锁屏久了推送会延迟。",
                    action = GuideAction.BATTERY,
                ),
                RomStep(
                    title = "锁住最近任务",
                    path = listOf("多任务界面", "下拉卡片"),
                    desc = "下拉卡片加锁。",
                    action = GuideAction.MULTI_TASK,
                ),
            ),
        ),
        RomGuide(
            key = "honor",
            name = "荣耀",
            systems = "MagicOS / Magic UI",
            badge = "HN",
            color = Color(0xFF1E6FFF),
            keywords = listOf("honor", "hihonor"),
            note = "荣耀与华为同源，入口在「设置 › 应用 › 应用启动管理」，同样要关掉自动管理改手动。",
            steps = listOf(
                RomStep(
                    title = "改为手动管理",
                    path = listOf("设置", "应用", "应用启动管理", "短信转发"),
                    desc = "关闭「自动管理」。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "三个开关全开",
                    path = listOf("启动管理", "手动管理"),
                    desc = "允许自启动、允许关联启动、允许后台活动。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "锁住最近任务",
                    path = listOf("多任务界面", "下拉卡片"),
                    desc = "下拉卡片加锁。",
                    action = GuideAction.MULTI_TASK,
                ),
            ),
        ),
        RomGuide(
            key = "samsung",
            name = "三星",
            systems = "One UI",
            badge = "SS",
            color = Color(0xFF1428A0),
            keywords = listOf("samsung"),
            note = "三星会把不常用 App 自动丢进「休眠应用」，必须从名单里移出来。",
            steps = listOf(
                RomStep(
                    title = "移出休眠名单",
                    path = listOf("设置", "电池", "后台使用限制"),
                    desc = "把「短信转发」从「休眠应用」「深度休眠应用」里移除。",
                    action = GuideAction.BATTERY,
                ),
                RomStep(
                    title = "电池设为不受限制",
                    path = listOf("设置", "应用", "短信转发", "电池"),
                    desc = "选择「不受限制」。",
                    action = GuideAction.APP_DETAILS,
                ),
                RomStep(
                    title = "保持打开",
                    path = listOf("最近任务", "长按应用图标"),
                    desc = "选择「保持打开」，避免一键清理。",
                    action = GuideAction.MULTI_TASK,
                ),
            ),
        ),
        RomGuide(
            key = "meizu",
            name = "魅族",
            systems = "Flyme",
            badge = "MZ",
            color = Color(0xFF00A3E0),
            keywords = listOf("meizu"),
            note = "Flyme 的自启动与后台管理都在手机管家里。",
            steps = listOf(
                RomStep(
                    title = "允许自启动",
                    path = listOf("手机管家", "权限管理", "自启动"),
                    desc = "打开「短信转发」。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "允许后台运行",
                    path = listOf("手机管家", "权限管理", "后台管理"),
                    desc = "设为「允许后台运行」。",
                    action = GuideAction.APP_DETAILS,
                ),
                RomStep(
                    title = "省电模式放行",
                    path = listOf("设置", "电池", "省电模式"),
                    desc = "开启省电模式时，确认本应用不被限制。",
                    action = GuideAction.BATTERY,
                ),
            ),
        ),
        RomGuide(
            key = "nubia",
            name = "努比亚 / 红魔",
            systems = "MyOS / RedMagic OS",
            badge = "NB",
            color = Color(0xFFE60012),
            keywords = listOf("nubia", "redmagic"),
            note = "游戏手机省电策略更激进，建议同时在游戏空间里排除本应用。",
            steps = listOf(
                RomStep(
                    title = "允许自启动",
                    path = listOf("设置", "应用", "自启动管理"),
                    desc = "打开「短信转发」。",
                    action = GuideAction.AUTO_START,
                ),
                RomStep(
                    title = "允许后台耗电",
                    path = listOf("设置", "电池", "后台耗电管理"),
                    desc = "允许后台活动。",
                    action = GuideAction.APP_DETAILS,
                ),
                RomStep(
                    title = "锁住最近任务",
                    path = listOf("多任务界面", "下拉卡片"),
                    desc = "下拉卡片加锁。",
                    action = GuideAction.MULTI_TASK,
                ),
            ),
        ),
        RomGuide(
            key = "pixel",
            name = "原生 / Pixel",
            systems = "Pixel、索尼、摩托罗拉等",
            badge = "PX",
            color = Color(0xFF3DDC84),
            keywords = listOf("google", "sony", "motorola", "nothing", "nokia"),
            note = "原生系统最省心，只需要电池优化放行，没有自启动白名单和速冻。",
            steps = listOf(
                RomStep(
                    title = "电池设为不受限制",
                    path = listOf("设置", "应用", "短信转发", "电池"),
                    desc = "选择「不受限制」。",
                    action = GuideAction.APP_DETAILS,
                ),
                RomStep(
                    title = "电池优化放行",
                    path = listOf("设置", "电池", "电池优化", "所有应用", "短信转发"),
                    desc = "选择「不优化」。",
                    action = GuideAction.BATTERY,
                ),
                RomStep(
                    title = "允许通知",
                    path = listOf("设置", "通知", "短信转发"),
                    desc = "前台服务通知必须可见，否则容易被系统回收。",
                    action = GuideAction.NOTIFICATION,
                ),
            ),
        ),
    )

    /** 按厂商名匹配；匹配不到返回 null，UI 会退回到第一个品牌并提示手动选择。 */
    fun detect(manufacturer: String?, brand: String?): RomGuide? {
        val haystack = listOfNotNull(manufacturer, brand)
            .joinToString(" ")
            .lowercase()
        if (haystack.isBlank()) return null
        return all.firstOrNull { guide -> guide.keywords.any { haystack.contains(it) } }
    }

    val checkItems: List<String> = emptyList()

    /** 品牌角标颜色用不到主题色时，给个兜底 */
    val fallbackColor: Color = BrandBlue

    val accentColors: List<Color> = listOf(BrandBlue, BrandTeal, BrandViolet, BrandRose)
}
