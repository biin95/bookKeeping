package com.biin95.bookkeeping.nlp

/**
 * 商户别名→标准名→分类映射表
 *
 *  用户说的词 → 归一化商户名 → 分类
 *  "拼好饭"    → "美团"       → "餐饮"
 *  "抖音商城"  → "抖音"       → "购物"
 */
object MerchantMapping {

    // 用户输入中可能出现的词 → 归一化后的标准商户名
    private val aliasToStandard = mapOf(
        // 餐饮外卖
        "美团" to "美团", "美团外卖" to "美团", "美团优选" to "美团",
        "拼好饭" to "美团", "大众点评" to "美团", "点评" to "美团",
        "饿了么" to "饿了么", "饿了吗" to "饿了么",
        "麦当劳" to "麦当劳", "肯德基" to "肯德基", "KFC" to "肯德基",
        "瑞幸" to "瑞幸", "luckin" to "瑞幸", "库迪" to "库迪",
        "星巴克" to "星巴克", "蜜雪冰城" to "蜜雪冰城",

        // 电商购物
        "淘宝" to "淘宝", "天猫" to "淘宝", "淘特" to "淘宝", "菜鸟" to "淘宝",
        "京东" to "京东", "京东超市" to "京东", "京东快递" to "京东",
        "拼多多" to "拼多多", "拼团" to "拼多多", "多多买菜" to "拼多多",
        "抖音" to "抖音", "抖音商城" to "抖音", "抖音小店" to "抖音",
        "快手" to "快手", "小红书" to "小红书",
        "苏宁" to "苏宁", "网易严选" to "网易严选",
        "得物" to "得物", "识货" to "得物",

        // 交通出行
        "滴滴" to "滴滴", "滴滴打车" to "滴滴", "花小猪" to "滴滴",
        "高德" to "高德", "高德地图" to "高德",
        "百度地图" to "百度地图", "腾讯地图" to "腾讯地图",
        "T3出行" to "T3出行", "曹操出行" to "曹操出行",
        "哈啰" to "哈啰", "哈罗" to "哈啰",
        "地铁" to "地铁", "公交" to "公交", "巴士" to "公交",
        "高铁" to "高铁", "火车" to "火车",
        "飞机" to "飞机", "航旅" to "飞机",
        "加油" to "加油", "中石油" to "加油", "中石化" to "加油",
        "充电" to "充电", "特来电" to "充电",

        // 生活服务
        "话费" to "话费", "流量" to "话费", "宽带" to "话费",
        "水电" to "水电", "电费" to "水电", "水费" to "水电",
        "燃气" to "燃气", "物业" to "物业", "房租" to "房租",
        "快递" to "快递", "顺丰" to "快递", "圆通" to "快递",
        "打印" to "打印", "复印" to "打印",

        // 娱乐
        "电影" to "电影", "淘票票" to "电影", "猫眼" to "电影",
        "腾讯视频" to "腾讯视频", "爱奇艺" to "爱奇艺", "B站" to "B站",
        "网易云" to "网易云音乐", "QQ音乐" to "QQ音乐",
        "游戏" to "游戏", "Steam" to "游戏",

        // 医疗
        "医院" to "医院", "诊所" to "医院", "药店" to "药店",
        "体检" to "体检",

        // 金融
        "工资" to "工资", "薪水" to "工资", "奖金" to "工资",
        "红包" to "红包", "转账" to "转账", "退款" to "退款"
    )

    // 标准商户名 → 分类
    private val standardToCategory = mapOf(
        "美团" to "餐饮", "饿了么" to "餐饮",
        "麦当劳" to "餐饮", "肯德基" to "餐饮",
        "瑞幸" to "餐饮", "库迪" to "餐饮",
        "星巴克" to "餐饮", "蜜雪冰城" to "餐饮",

        "淘宝" to "购物", "京东" to "购物", "拼多多" to "购物",
        "抖音" to "购物", "快手" to "购物", "小红书" to "购物",
        "苏宁" to "购物", "网易严选" to "购物", "得物" to "购物",

        "滴滴" to "交通", "高德" to "交通",
        "百度地图" to "交通", "腾讯地图" to "交通",
        "T3出行" to "交通", "曹操出行" to "交通",
        "哈啰" to "交通", "地铁" to "交通",
        "公交" to "交通", "高铁" to "交通",
        "火车" to "交通", "飞机" to "交通",
        "加油" to "交通", "充电" to "交通",

        "话费" to "通讯",
        "水电" to "居住", "燃气" to "居住", "物业" to "居住", "房租" to "居住",

        "快递" to "日用", "打印" to "日用",

        "电影" to "娱乐", "淘票票" to "娱乐", "猫眼" to "娱乐",
        "腾讯视频" to "娱乐", "爱奇艺" to "娱乐", "B站" to "娱乐",
        "网易云音乐" to "娱乐", "QQ音乐" to "娱乐", "游戏" to "娱乐",

        "医院" to "医疗", "药店" to "医疗", "体检" to "医疗",

        "工资" to "工资", "红包" to "其他", "转账" to "其他", "退款" to "退款"
    )

    /** 关键词列表（按长度降序排列，长词优先匹配） */
    private val sortedKeywords = aliasToStandard.keys.sortedByDescending { it.length }

    /** 关键词→分类的直接映射（用于没有对应商户名的场景） */
    private val keywordToCategory: Map<String, String> = mapOf(
        "餐" to "餐饮", "饭" to "餐饮", "食" to "餐饮", "外卖" to "餐饮",
        "买菜" to "餐饮", "菜" to "餐饮", "奶茶" to "餐饮", "咖啡" to "餐饮",
        "打车" to "交通", "骑车" to "交通",
        "停车" to "交通", "坐车" to "交通",
        "超市" to "购物", "买东西" to "购物",
        "房租" to "居住", "交租" to "居住",
        "药" to "医疗",
        "工资" to "工资", "发工资" to "工资",
        "游戏" to "娱乐",
        "书" to "教育"
    )

    /**
     * 从文本中提取商户名（归一化后的标准名）
     */
    fun extractMerchant(text: String): Pair<String?, String?> {
        for (keyword in sortedKeywords) {
            if (text.contains(keyword)) {
                return Pair(aliasToStandard[keyword], keyword)
            }
        }
        return Pair(null, null)
    }

    /**
     * 根据商户名或文本推断分类
     */
    fun guessCategory(merchant: String?, text: String): String {
        // 1. 先查商户→分类映射
        if (merchant != null) {
            val cat = standardToCategory[merchant]
            if (cat != null) return cat
        }

        // 2. 按关键词推断分类
        val sortedKw = keywordToCategory.keys.sortedByDescending { it.length }
        for (kw in sortedKw) {
            if (text.contains(kw)) {
                return keywordToCategory[kw] ?: "其他"
            }
        }

        return "其他"
    }
}
