package com.semiovert.app.navigation

import com.semiovert.app.core.designsystem.SoSymbol

enum class Destination(val route: String, val label: String, val symbol: SoSymbol) {
    HOME("home","首页",SoSymbol.HOME), CATEGORY("category","分类",SoSymbol.CATEGORY),
    SEARCH("search","搜索",SoSymbol.SEARCH), PROFILE("profile","我的",SoSymbol.PERSON)
}
