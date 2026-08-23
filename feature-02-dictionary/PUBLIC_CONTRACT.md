# 功能2公共契约

## Android 入口

```kotlin
enum class DictionaryPresentation {
    FULL,
    QUICK_LOOKUP
}

@Composable
fun DictionaryFeature(
    initialQuery: String? = null,
    presentation: DictionaryPresentation = DictionaryPresentation.FULL,
    onClose: () -> Unit = {}
)
```

- `FULL`：完整词典搜索与管理入口。
- `QUICK_LOOKUP`：由根 App 壳传入单词并显示临时结果；`onClose` 请求关闭临时页面。
- 功能模块不得接受功能1内部对象；根 App 壳只传入普通字符串和导航回调。
- 功能内部自行创建数据库、Repository、解析器和 ViewModel，App 壳不得访问内部 DAO 或文件实现。

修改本契约前必须向总控提交 `contract_change` 汇报并等待确认。
